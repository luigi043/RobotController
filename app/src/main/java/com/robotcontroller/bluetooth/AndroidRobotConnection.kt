package com.robotcontroller.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidRobotConnection(private val device: BluetoothDevice) : RobotConnection {
    override val name = device.name ?: device.address
    private val lock = Any()
    private var closed = false
    private var socket: BluetoothSocket? = null
    override val input get() = requireNotNull(socket).inputStream
    override val output get() = requireNotNull(socket).outputStream

    override fun connect() {
        val candidate = try {
            device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
        } catch (original: IOException) {
            // Preserve the legacy channel-1 creation fallback. Device support varies.
            try {
                device.javaClass.getMethod("createRfcommSocket", Int::class.java)
                    .invoke(device, 1) as BluetoothSocket
            } catch (_: ReflectiveOperationException) { throw original }
        }
        synchronized(lock) {
            if (closed) {
                candidate.close()
                throw IOException("Connection cancelled")
            }
            socket = candidate
        }
        candidate.connect()
    }

    override fun close() {
        val old = synchronized(lock) { closed = true; socket }
        old?.close()
    }
}
