package com.robotcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager as SystemBtManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.robotcontroller.bluetooth.BluetoothManager
import com.robotcontroller.bluetooth.CommandHandler
import com.robotcontroller.bluetooth.ConnectionState
import com.robotcontroller.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
//  MainActivity
//  Owns the UI; delegates all BT I/O to BluetoothManager.
// ─────────────────────────────────────────────────────────────────────────────

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Bluetooth
    private val btManager       = BluetoothManager()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var pairedDevices: List<BluetoothDevice> = emptyList()

    // UI state
    private var isAutoMode  = false
    private val dataLog     = StringBuilder()
    private val timeFmt     = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Prefs (save last device, auto-reconnect preference)
    private lateinit var prefs: SharedPreferences

    // ── Permission launcher ───────────────────────────────────────────────────

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            loadPairedDevices()
        } else {
            toast("Bluetooth permissions are required to use this app")
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        prefs = getSharedPreferences("RobotPrefs", Context.MODE_PRIVATE)

        initBluetooth()
        setupButtons()
        observeState()
        checkPermissionsAndLoad()
    }

    override fun onDestroy() {
        super.onDestroy()
        btManager.cleanup()
    }

    // ── Bluetooth initialisation ──────────────────────────────────────────────

    private fun initBluetooth() {
        val sysBtManager = getSystemService(Context.BLUETOOTH_SERVICE) as SystemBtManager
        bluetoothAdapter = sysBtManager.adapter

        if (bluetoothAdapter == null) {
            toast("Bluetooth not supported on this device")
            finish()
            return
        }

        if (bluetoothAdapter?.isEnabled == false) {
            toast("Please enable Bluetooth then restart the app")
        }
    }

    // ── Button / touch setup ──────────────────────────────────────────────────

    private fun setupButtons() {
        // ── Connection ────────────────────────────────────────────────────────
        binding.btnRefresh.setOnClickListener { checkPermissionsAndLoad() }

        binding.btnConnect.setOnClickListener {
            val idx = binding.spinnerDevices.selectedItemPosition
            if (idx in pairedDevices.indices) {
                val device = pairedDevices[idx]
                prefs.edit().putString(PREF_LAST_ADDR, device.address).apply()
                btManager.connect(device)
            } else {
                toast("Select a paired device first")
            }
        }

        binding.btnDisconnect.setOnClickListener {
            btManager.disconnect()
        }

        // ── Movement (hold-to-move → release sends STOP) ──────────────────────
        setupHoldButton(binding.btnForward,  CommandHandler.FORWARD)
        setupHoldButton(binding.btnBackward, CommandHandler.BACKWARD)
        setupHoldButton(binding.btnLeft,     CommandHandler.LEFT)
        setupHoldButton(binding.btnRight,    CommandHandler.RIGHT)

        // Tap-stop centre button
        binding.btnStop.setOnClickListener {
            vibrate()
            CommandHandler.send(btManager, CommandHandler.STOP)
        }

        // ── Colour ────────────────────────────────────────────────────────────
        binding.btnRed.setOnClickListener   { vibrate(); CommandHandler.send(btManager, CommandHandler.RED)   }
        binding.btnBlue.setOnClickListener  { vibrate(); CommandHandler.send(btManager, CommandHandler.BLUE)  }
        binding.btnGreen.setOnClickListener { vibrate(); CommandHandler.send(btManager, CommandHandler.GREEN) }

        // ── Auto mode toggle ──────────────────────────────────────────────────
        binding.btnAutoMode.setOnClickListener {
            vibrate()
            isAutoMode = !isAutoMode
            binding.btnAutoMode.text = if (isAutoMode) "AUTO MODE: ON ✓" else "AUTO MODE: OFF"
            CommandHandler.send(btManager, CommandHandler.AUTO)
        }

        // ── Data log ──────────────────────────────────────────────────────────
        binding.btnClearData.setOnClickListener {
            dataLog.clear()
            binding.tvReceivedData.text = getString(R.string.no_data)
        }

        // Initially disable all controls until connected
        setControlsEnabled(false)
    }

    /**
     * Sends [command] on finger-down and STOP on finger-up/cancel.
     * Also fires a short vibration on press.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupHoldButton(view: View, command: String) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    vibrate()
                    v.isPressed = true
                    CommandHandler.send(btManager, command)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    CommandHandler.send(btManager, CommandHandler.STOP)
                    true
                }
                else -> false
            }
        }
    }

    // ── State observers ───────────────────────────────────────────────────────

    private fun observeState() {
        // Connection state → UI
        btManager.uiState
            .onEach { state -> updateConnectionUi(state.connectionState, state.connectedDeviceName, state.errorMessage) }
            .launchIn(lifecycleScope)

        // Incoming data lines → log
        lifecycleScope.launch {
            btManager.receivedLine.collect { line -> appendLog(line) }
        }
    }

    // ── UI update helpers ─────────────────────────────────────────────────────

    private fun updateConnectionUi(state: ConnectionState, name: String?, error: String?) {
        when (state) {
            ConnectionState.IDLE, ConnectionState.DISCONNECTED -> {
                setIndicator(IndicatorColor.RED)
                binding.tvStatus.text = getString(R.string.status_disconnected)
                binding.btnConnect.isEnabled    = true
                binding.btnDisconnect.isEnabled = false
                setControlsEnabled(false)
                if (state == ConnectionState.DISCONNECTED) appendLog("── Disconnected ──")
            }

            ConnectionState.CONNECTING -> {
                setIndicator(IndicatorColor.YELLOW)
                binding.tvStatus.text = getString(R.string.status_connecting, name)
                binding.btnConnect.isEnabled    = false
                binding.btnDisconnect.isEnabled = false
                setControlsEnabled(false)
            }

            ConnectionState.CONNECTED -> {
                setIndicator(IndicatorColor.GREEN)
                binding.tvStatus.text = getString(R.string.status_connected, name)
                binding.btnConnect.isEnabled    = false
                binding.btnDisconnect.isEnabled = true
                setControlsEnabled(true)
                appendLog("── Connected to $name ──")
                toast("✓ Connected to $name")
            }

            ConnectionState.ERROR -> {
                setIndicator(IndicatorColor.RED)
                binding.tvStatus.text = error ?: getString(R.string.status_error)
                binding.btnConnect.isEnabled    = true
                binding.btnDisconnect.isEnabled = false
                setControlsEnabled(false)
                appendLog("── Error: $error ──")
                toast(error ?: "Connection failed")
            }
        }
    }

    private enum class IndicatorColor { RED, YELLOW, GREEN }

    private fun setIndicator(color: IndicatorColor) {
        val drawableRes = when (color) {
            IndicatorColor.RED    -> R.drawable.ic_status_disconnected
            IndicatorColor.YELLOW -> R.drawable.ic_status_connecting
            IndicatorColor.GREEN  -> R.drawable.ic_status_connected
        }
        binding.viewStatusDot.setBackgroundResource(drawableRes)
    }

    private fun setControlsEnabled(enabled: Boolean) {
        listOf(
            binding.btnForward, binding.btnBackward,
            binding.btnLeft,    binding.btnRight,
            binding.btnStop,    binding.btnRed,
            binding.btnBlue,    binding.btnGreen,
            binding.btnAutoMode
        ).forEach { it.isEnabled = enabled }
    }

    private fun appendLog(line: String) {
        val timestamp = timeFmt.format(Date())
        dataLog.insert(0, "[$timestamp] $line\n")
        if (dataLog.length > 4000) dataLog.delete(3000, dataLog.length)
        binding.tvReceivedData.text = dataLog.toString()
        // Auto-scroll to top (newest data)
        binding.scrollViewData.scrollTo(0, 0)
    }

    // ── Permissions & paired device list ─────────────────────────────────────

    private fun checkPermissionsAndLoad() {
        val required = requiredPermissions()
        val missing  = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) loadPairedDevices() else permLauncher.launch(missing.toTypedArray())
    }

    private fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            buildList {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

    @SuppressLint("MissingPermission")
    private fun loadPairedDevices() {
        try {
            val bonded = bluetoothAdapter?.bondedDevices ?: emptySet()
            pairedDevices = bonded.sortedBy { it.name ?: it.address }

            if (pairedDevices.isEmpty()) {
                toast("No paired devices found — pair your robot in Settings first")
                binding.spinnerDevices.adapter = null
                return
            }

            val labels = pairedDevices.map {
                "${it.name ?: "Unknown"}  (${it.address})"
            }
            val adapter = ArrayAdapter(this, R.layout.spinner_item, labels)
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            binding.spinnerDevices.adapter = adapter

            // Restore last-used device selection
            val lastAddr = prefs.getString(PREF_LAST_ADDR, null)
            lastAddr?.let { addr ->
                val idx = pairedDevices.indexOfFirst { it.address == addr }
                if (idx >= 0) binding.spinnerDevices.setSelection(idx)
            }
        } catch (e: SecurityException) {
            toast("Permission denied: ${e.message}")
        }
    }

    // ── Vibration ─────────────────────────────────────────────────────────────

    private fun vibrate(ms: Long = 28) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(ms)
                }
            }
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val PREF_LAST_ADDR = "last_device_address"
    }
}
