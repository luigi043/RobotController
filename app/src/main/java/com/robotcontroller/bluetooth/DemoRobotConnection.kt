package com.robotcontroller.bluetooth

import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue

/** Entirely in memory. Never opens Bluetooth, network, or hardware. */
class DemoRobotConnection : RobotConnection {
    override val name = "Demo robot (simulation)"
    private val bytes = LinkedBlockingQueue<Int>()
    @Volatile private var closed = false
    override val input = object : InputStream() {
        override fun read(): Int = bytes.take()
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            val first = read()
            if (first < 0) return -1
            buffer[offset] = first.toByte()
            var count = 1
            while (count < length && bytes.isNotEmpty()) {
                val next = bytes.poll() ?: break
                if (next < 0) { bytes.offer(-1); break }
                buffer[offset + count++] = next.toByte()
            }
            return count
        }
    }
    override val output = object : OutputStream() {
        private val pending = StringBuilder()
        override fun write(value: Int) {
            if (closed) throw IOException("Demo disconnected")
            if (value == '\n'.code) {
                emit("SIM OK: $pending\n")
                pending.clear()
            } else pending.append(value.toChar())
        }
    }
    override fun connect() {
        if (closed) throw IOException("Demo disconnected")
        emit("SIM: In-memory robot connected. No hardware in use.\n")
    }
    private fun emit(text: String) = text.toByteArray(Charsets.UTF_8).forEach { bytes.offer(it.toInt() and 255) }
    override fun close() { closed = true; bytes.clear(); bytes.offer(-1) }
}
