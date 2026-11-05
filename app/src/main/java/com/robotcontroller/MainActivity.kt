package com.robotcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager as SystemBtManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.robotcontroller.bluetooth.*
import com.robotcontroller.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val manager = BluetoothManager()
    private var adapter: BluetoothAdapter? = null
    private var devices: List<BluetoothDevice> = emptyList()
    private var demo: DemoRobotConnection? = null
    private var permissionDenied = false
    private val log = StringBuilder()
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionDenied = !hasPermission()
        loadDevices()
    }
    private val enableBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { loadDevices() }

    override fun attachBaseContext(base: Context) {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.ENGLISH)
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightNavigationBars = true
        adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as SystemBtManager).adapter
        binding.btnRefresh.setOnClickListener { refresh() }
        binding.btnSettings.setOnClickListener { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        binding.btnAppSettings.setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        binding.btnConnect.setOnClickListener {
            if (!hasPermission()) { refresh(); return@setOnClickListener }
            devices.getOrNull(binding.spinnerDevices.selectedItemPosition)?.let { device ->
                try {
                    getSharedPreferences("RobotPrefs", MODE_PRIVATE).edit().putString("last_device_address", device.address).apply()
                    manager.connect(AndroidRobotConnection(device))
                } catch (_: SecurityException) { showMessage("Bluetooth permission denied. Refresh to retry.") }
            }
        }
        binding.btnDisconnect.setOnClickListener { manager.disconnect() }
        binding.btnDemo.setOnClickListener {
            demo = DemoRobotConnection()
            manager.connect(requireNotNull(demo))
        }
        binding.btnSimulateLoss.setOnClickListener { demo?.close() }
        listOf(binding.btnForward to CommandHandler.FORWARD, binding.btnBackward to CommandHandler.BACKWARD,
            binding.btnLeft to CommandHandler.LEFT, binding.btnRight to CommandHandler.RIGHT).forEach { (view, command) -> setupMovement(view, command) }
        listOf(binding.btnStop to CommandHandler.STOP, binding.btnRed to CommandHandler.RED,
            binding.btnBlue to CommandHandler.BLUE, binding.btnGreen to CommandHandler.GREEN,
            binding.btnAutoMode to CommandHandler.AUTO).forEach { (view, command) ->
            view.setOnClickListener { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); manager.sendCommand(command) }
        }
        binding.btnClearData.setOnClickListener { log.clear(); binding.tvReceivedData.setText(R.string.no_data) }
        lifecycleScope.launch { manager.uiState.collect { render(it) } }
        lifecycleScope.launch { manager.receivedLine.collect { appendLog(it) } }
        loadDevices() // Ask for permission only when the user chooses Refresh.
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized && !manager.isConnected && manager.uiState.value.connectionState != ConnectionState.CONNECTING) loadDevices()
    }

    override fun onStop() {
        // Close the session when leaving the control screen; firmware must still
        // implement a watchdog because radio loss cannot guarantee a STOP delivery.
        if (manager.uiState.value.connectionState in listOf(ConnectionState.CONNECTED, ConnectionState.CONNECTING)) {
            manager.sendCommand(CommandHandler.STOP)
            manager.disconnect()
        }
        super.onStop()
    }

    override fun onDestroy() { manager.cleanup(); super.onDestroy() }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMovement(view: View, command: String) {
        view.setOnTouchListener { v, event ->
            if (!v.isEnabled) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    manager.sendCommand(command)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    v.parent.requestDisallowInterceptTouchEvent(false)
                    manager.sendCommand(CommandHandler.STOP)
                    true
                }
                else -> true
            }
        }
        // TalkBack/keyboard activation makes a bounded movement pulse.
        view.setOnClickListener { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); manager.sendCommand(command); manager.sendCommand(CommandHandler.STOP) }
    }

    private fun render(state: BluetoothUiState) {
        val connected = state.connectionState == ConnectionState.CONNECTED
        val busy = connected || state.connectionState == ConnectionState.CONNECTING
        binding.tvStatus.text = when (state.connectionState) {
            ConnectionState.CONNECTING -> "Connecting…"
            ConnectionState.CONNECTED -> if (demo != null) "Demo connected" else "Connected"
            ConnectionState.ERROR -> "Connection error"
            else -> "Disconnected"
        }
        binding.viewStatusDot.setBackgroundResource(when {
            connected -> R.drawable.ic_status_connected
            busy -> R.drawable.ic_status_connecting
            else -> R.drawable.ic_status_disconnected
        })
        binding.btnDisconnect.isEnabled = busy
        binding.btnDemo.isEnabled = !busy
        binding.btnRefresh.isEnabled = !busy
        binding.btnSettings.isEnabled = !busy
        binding.btnAppSettings.isEnabled = !busy
        binding.spinnerDevices.isEnabled = !busy && devices.isNotEmpty()
        binding.btnConnect.isEnabled = !busy && devices.isNotEmpty() && hasPermission()
        listOf(binding.btnForward, binding.btnBackward, binding.btnLeft, binding.btnRight, binding.btnStop,
            binding.btnRed, binding.btnBlue, binding.btnGreen, binding.btnAutoMode).forEach { it.isEnabled = connected }
        binding.btnSimulateLoss.visibility = if (connected && demo != null) View.VISIBLE else View.GONE
        if (connected) {
            showMessage(if (demo != null) "SIMULATION: in-memory commands and responses. No Bluetooth or hardware is used."
                else "Connected to ${state.connectedDeviceName}. Hold a direction, release to stop.")
        } else if (state.connectionState == ConnectionState.ERROR) {
            showMessage(state.errorMessage ?: "Connection failed. Refresh and reconnect.")
            appendLog(state.errorMessage ?: "Connection lost")
            demo = null
        } else if (state.connectionState == ConnectionState.DISCONNECTED) {
            demo = null
            showMessage("Disconnected. Refresh paired devices or start the offline demo.")
        }
    }

    private fun hasPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun refresh() {
        if (!hasPermission()) permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
        else loadDevices(promptEnable = true)
    }

    @SuppressLint("MissingPermission")
    private fun loadDevices(promptEnable: Boolean = false) {
        devices = emptyList()
        try {
            when {
                adapter == null -> showMessage("Bluetooth is unavailable on this device. Try the offline demo.")
                !hasPermission() -> showMessage(if (permissionDenied) "Nearby devices permission denied. Use Refresh to retry, or open App permissions." else "Refresh to allow nearby devices, or try the offline demo without permission.")
                adapter?.isEnabled != true -> {
                    showMessage("Bluetooth is off. Refresh to enable it, or try the offline demo.")
                    if (promptEnable) enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
                else -> {
                    devices = adapter?.bondedDevices.orEmpty().sortedBy { it.name ?: it.address }
                    showMessage(if (devices.isEmpty()) "No paired devices. Pair your robot in Bluetooth settings, then Refresh."
                        else "Select a paired robot, then Connect. The offline demo uses no hardware.")
                }
            }
            val labels = devices.map { "${it.name ?: "Unnamed device"} (${it.address})" }
            binding.spinnerDevices.adapter = ArrayAdapter(this, R.layout.spinner_item, labels).also {
                it.setDropDownViewResource(R.layout.spinner_dropdown_item)
            }
            val address = getSharedPreferences("RobotPrefs", MODE_PRIVATE).getString("last_device_address", null)
            devices.indexOfFirst { it.address == address }.takeIf { it >= 0 }?.let { binding.spinnerDevices.setSelection(it) }
        } catch (_: SecurityException) { showMessage("Bluetooth permission denied. Refresh to retry.") }
        binding.btnConnect.isEnabled = devices.isNotEmpty() && hasPermission()
        binding.spinnerDevices.isEnabled = devices.isNotEmpty()
    }

    private fun showMessage(message: String) { binding.tvConnectionHelp.text = message }
    private fun appendLog(line: String) {
        log.insert(0, "[${clock.format(Date())}] $line\n")
        if (log.length > 4000) log.delete(3000, log.length)
        binding.tvReceivedData.text = log.toString()
        binding.scrollViewData.scrollTo(0, 0)
    }
}
