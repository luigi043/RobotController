package com.robotcontroller.bluetooth

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

enum class ConnectionState { IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR }
data class BluetoothUiState(
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val connectedDeviceName: String? = null,
    val errorMessage: String? = null
)

/** A session owns its transport. Queued writes never target a later robot. */
interface RobotConnection {
    val name: String
    val input: InputStream
    val output: OutputStream
    fun connect()
    fun close()
}

class BluetoothManager {
    private class Session(val connection: RobotConnection) {
        val writes = Channel<String>(Channel.UNLIMITED)
        var job: Job? = null
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var session: Session? = null
    private val _uiState = MutableStateFlow(BluetoothUiState())
    val uiState = _uiState.asStateFlow()
    private val _receivedLine = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val receivedLine = _receivedLine.asSharedFlow()
    val isConnected: Boolean get() = uiState.value.connectionState == ConnectionState.CONNECTED

    fun connect(connection: RobotConnection) {
        val current = synchronized(lock) {
            if (session != null) return
            Session(connection).also {
                session = it
                _uiState.value = BluetoothUiState(ConnectionState.CONNECTING, connection.name)
            }
        }
        current.job = scope.launch {
            try {
                connection.connect()
                ensureActive()
                val input = connection.input
                val output = connection.output
                synchronized(lock) {
                    if (session !== current) return@launch
                    _uiState.value = BluetoothUiState(ConnectionState.CONNECTED, connection.name)
                }
                coroutineScope {
                    launch {
                        try {
                            for (command in current.writes) {
                                output.write(command.toByteArray(Charsets.UTF_8))
                                output.flush()
                            }
                        } catch (_: IOException) {
                            fail(current, "Send failed. Check the robot and reconnect.")
                        }
                    }
                    val reader = input.reader(Charsets.UTF_8)
                    val pending = StringBuilder()
                    var dropping = false
                    while (isActive) {
                        val code = reader.read()
                        if (code < 0) throw IOException("Robot closed the connection")
                        if (code == '\n'.code) {
                            if (!dropping) pending.toString().trim().takeIf { it.isNotEmpty() }
                                ?.let { _receivedLine.tryEmit(it) }
                            pending.clear()
                            dropping = false
                        } else if (!dropping) {
                            if (pending.length >= 3000) {
                                pending.clear()
                                dropping = true
                            } else pending.append(code.toChar())
                        }
                    }
                }
            } catch (_: CancellationException) {
                // A local disconnect has already detached this session.
            } catch (_: SecurityException) {
                fail(current, "Bluetooth permission denied. Grant permission and refresh.")
            } catch (_: IOException) {
                fail(current, "Connection failed or lost. Check the robot and reconnect.")
            } finally {
                runCatching { connection.close() }
            }
        }
    }

    private fun fail(current: Session, message: String) {
        synchronized(lock) {
            if (session !== current) return
            session = null
            current.writes.close()
            _uiState.value = BluetoothUiState(ConnectionState.ERROR, errorMessage = message)
        }
        runCatching { current.connection.close() }
        current.job?.cancel()
    }

    fun sendCommand(command: String) {
        synchronized(lock) {
            if (isConnected) session?.writes?.trySend(command)
        }
    }

    fun disconnect() {
        val old = synchronized(lock) {
            val previous = session
            session = null
            _uiState.value = BluetoothUiState(ConnectionState.DISCONNECTED)
            previous
        }
        old?.writes?.close()
        old?.job?.cancel()
        // Closing interrupts blocking I/O. Cancellation alone cannot do so.
        old?.let { runCatching { it.connection.close() } }
    }

    fun cleanup() { disconnect(); scope.cancel() }
}
