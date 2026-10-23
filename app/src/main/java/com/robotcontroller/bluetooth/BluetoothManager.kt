package com.robotcontroller.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
//  Data models
// ─────────────────────────────────────────────────────────────────────────────

enum class ConnectionState { IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR }

data class BluetoothUiState(
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val connectedDeviceName: String? = null,
    val errorMessage: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
//  BluetoothManager
//  Handles all BT socket I/O on a dedicated coroutine scope (Dispatchers.IO).
//  The Activity/ViewModel observes StateFlow / SharedFlow on the Main thread.
// ─────────────────────────────────────────────────────────────────────────────

class BluetoothManager {

    companion object {
        /** Standard Serial Port Profile UUID used by HC-05, HC-06, ESP32, etc. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val READ_BUFFER_SIZE = 1024
        private const val MAX_LOG_CHARS   = 3000
    }

    // Supervisor job → child failures don't cancel the whole scope
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var btSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream?  = null
    private var readJob: Job? = null

    // ── Public state ──────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(BluetoothUiState())
    val uiState: StateFlow<BluetoothUiState> = _uiState.asStateFlow()

    /** Emits each complete line received from the robot (newline-delimited). */
    private val _receivedLine = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)
    val receivedLine: SharedFlow<String> = _receivedLine.asSharedFlow()

    val isConnected: Boolean
        get() = _uiState.value.connectionState == ConnectionState.CONNECTED &&
                btSocket?.isConnected == true

    // ── Connection ────────────────────────────────────────────────────────────

    fun connect(device: BluetoothDevice) {
        managerScope.launch {
            _uiState.value = BluetoothUiState(
                connectionState   = ConnectionState.CONNECTING,
                connectedDeviceName = device.name ?: device.address
            )

            closeSocket() // clean up any leftover socket

            val socket = openSocket(device)
            if (socket == null) {
                _uiState.value = BluetoothUiState(
                    connectionState = ConnectionState.ERROR,
                    errorMessage    = "Could not open RFCOMM socket"
                )
                return@launch
            }

            try {
                socket.connect()
            } catch (e: IOException) {
                closeSocket()
                _uiState.value = BluetoothUiState(
                    connectionState = ConnectionState.ERROR,
                    errorMessage    = "Connection refused: ${e.message}"
                )
                return@launch
            }

            btSocket      = socket
            outputStream  = socket.outputStream
            inputStream   = socket.inputStream

            _uiState.value = BluetoothUiState(
                connectionState     = ConnectionState.CONNECTED,
                connectedDeviceName = device.name ?: device.address
            )

            startReading()
        }
    }

    /**
     * Try the standard RFCOMM socket first; fall back to the hidden
     * `createRfcommSocket(int)` method required by some HC-05 modules.
     */
    private fun openSocket(device: BluetoothDevice): BluetoothSocket? {
        return try {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (_: IOException) {
            try {
                device.javaClass
                    .getMethod("createRfcommSocket", Int::class.java)
                    .invoke(device, 1) as BluetoothSocket
            } catch (_: Exception) { null }
        }
    }

    // ── Disconnect ────────────────────────────────────────────────────────────

    fun disconnect() {
        managerScope.launch {
            readJob?.cancelAndJoin()
            closeSocket()
            _uiState.value = BluetoothUiState(connectionState = ConnectionState.DISCONNECTED)
        }
    }

    private fun closeSocket() {
        runCatching { outputStream?.close() }
        runCatching { inputStream?.close() }
        runCatching { btSocket?.close() }
        btSocket     = null
        outputStream = null
        inputStream  = null
    }

    // ── Read loop ─────────────────────────────────────────────────────────────

    private fun startReading() {
        readJob?.cancel()
        readJob = managerScope.launch {
            val buffer  = ByteArray(READ_BUFFER_SIZE)
            val pending = StringBuilder()

            while (isActive) {
                val bytes = try {
                    inputStream?.read(buffer) ?: break
                } catch (_: IOException) {
                    if (isActive && isConnected) {
                        _uiState.value = BluetoothUiState(
                            connectionState = ConnectionState.DISCONNECTED,
                            errorMessage    = "Connection lost"
                        )
                    }
                    break
                }

                if (bytes > 0) {
                    pending.append(String(buffer, 0, bytes, Charsets.UTF_8))

                    // Emit every complete line
                    while (true) {
                        val nl = pending.indexOf('\n')
                        if (nl == -1) break
                        val line = pending.substring(0, nl).trim()
                        pending.delete(0, nl + 1)
                        if (line.isNotEmpty()) _receivedLine.tryEmit(line)
                    }

                    // Safety: prevent runaway buffer growth on malformed data
                    if (pending.length > MAX_LOG_CHARS) pending.clear()
                }
            }
        }
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    fun sendCommand(command: String) {
        if (!isConnected) return
        managerScope.launch {
            try {
                outputStream?.write(command.toByteArray(Charsets.UTF_8))
                outputStream?.flush()
            } catch (e: IOException) {
                _uiState.value = BluetoothUiState(
                    connectionState = ConnectionState.DISCONNECTED,
                    errorMessage    = "Send failed – connection lost"
                )
                closeSocket()
            }
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /** Call from Activity.onDestroy() to release all resources. */
    fun cleanup() {
        managerScope.cancel()
        closeSocket()
    }
}
