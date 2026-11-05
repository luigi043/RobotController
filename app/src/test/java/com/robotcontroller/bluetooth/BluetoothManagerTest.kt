package com.robotcontroller.bluetooth

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BluetoothManagerTest {
    private suspend fun BluetoothManager.await(state: ConnectionState) = withTimeout(3000) { uiState.first { it.connectionState == state } }

    @Test fun orderedCommandsAndDisconnectUnblocksRead() = runBlocking<Unit> {
        val manager = BluetoothManager()
        val connection = Fixture()
        try {
            manager.connect(connection)
            manager.await(ConnectionState.CONNECTED)
            repeat(40) { manager.sendCommand("F\n"); manager.sendCommand("S\n") }
            assertTrue(connection.allWritten.await(3, TimeUnit.SECONDS))
            assertEquals("F\nS\n".repeat(40), connection.sent.toString("UTF-8"))
            manager.disconnect()
            manager.await(ConnectionState.DISCONNECTED)
            assertTrue(connection.closed.await(1, TimeUnit.SECONDS))
        } finally { manager.cleanup() }
    }

    @Test fun remoteEndOfStreamLeavesConnectedState() = runBlocking<Unit> {
        val manager = BluetoothManager()
        val connection = Fixture()
        try {
            manager.connect(connection)
            manager.await(ConnectionState.CONNECTED)
            connection.close()
            manager.await(ConnectionState.ERROR)
            assertFalse(manager.isConnected)
        } finally { manager.cleanup() }
    }

    @Test fun refusedConnectionClosesTransportAndAllowsRetry() = runBlocking<Unit> {
        val manager = BluetoothManager()
        val bad = Fixture(refused = true)
        try {
            manager.connect(bad)
            manager.await(ConnectionState.ERROR)
            assertTrue(bad.closed.await(1, TimeUnit.SECONDS))
            manager.connect(DemoRobotConnection())
            manager.await(ConnectionState.CONNECTED)
        } finally { manager.cleanup() }
    }

    @Test fun disconnectCancelsPendingConnectWithoutStaleSuccess() = runBlocking<Unit> {
        val manager = BluetoothManager()
        val waiting = Fixture(blockConnect = true)
        try {
            manager.connect(waiting)
            assertTrue(waiting.started.await(1, TimeUnit.SECONDS))
            manager.disconnect()
            manager.connect(DemoRobotConnection())
            manager.await(ConnectionState.CONNECTED)
            delay(100)
            assertEquals("Demo robot (simulation)", manager.uiState.value.connectedDeviceName)
        } finally { manager.cleanup() }
    }

    @Test fun utf8AndOversizedLinesAreFramedWithoutTruncatedCommands() = runBlocking<Unit> {
        val manager = BluetoothManager()
        val data = "x".repeat(3100) + "\nTemperature: 23°C\r\n"
        val connection = Fixture(data = data)
        val received = mutableListOf<String>()
        val collector = launch(Dispatchers.Unconfined) { manager.receivedLine.collect { received.add(it) } }
        try {
            manager.connect(connection)
            withTimeout(3000) { while (received.isEmpty()) delay(10) }
            assertEquals(listOf("Temperature: 23°C"), received)
        } finally { manager.cleanup(); collector.cancel() }
    }

    @Test fun simulationAcknowledgesCommandsWithoutHardware() = runBlocking<Unit> {
        val manager = BluetoothManager()
        val result = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(3000) { manager.receivedLine.first { it == "SIM OK: RED" } } }
        try {
            manager.connect(DemoRobotConnection())
            manager.await(ConnectionState.CONNECTED)
            manager.sendCommand(CommandHandler.RED)
            assertEquals("SIM OK: RED", result.await())
        } finally { manager.cleanup() }
    }

    @Test fun writesFromOldSessionCannotReachNewRobot() = runBlocking<Unit> {
        val manager = BluetoothManager()
        val old = Fixture(blockWrites = true)
        val next = Fixture()
        try {
            manager.connect(old)
            manager.await(ConnectionState.CONNECTED)
            manager.sendCommand("F\n")
            assertTrue(old.writeStarted.await(1, TimeUnit.SECONDS))
            repeat(30) { manager.sendCommand("L\n") }
            manager.disconnect()
            manager.connect(next)
            manager.await(ConnectionState.CONNECTED)
            manager.sendCommand("S\n")
            withTimeout(3000) { while (next.sent.size() < 2) delay(10) }
            assertEquals("S\n", next.sent.toString("UTF-8"))
        } finally { manager.cleanup() }
    }

    private class Fixture(val refused: Boolean = false, val blockConnect: Boolean = false,
                          val blockWrites: Boolean = false, data: String = "") : RobotConnection {
        override val name = "Synthetic robot"
        val closed = CountDownLatch(1)
        val started = CountDownLatch(1)
        val writeStarted = CountDownLatch(1)
        val allWritten = CountDownLatch(1)
        val sent = ByteArrayOutputStream()
        private val content = ByteArrayInputStream(data.toByteArray(Charsets.UTF_8))
        override val input = object : InputStream() {
            override fun read(): Int {
                val next = content.read()
                if (next >= 0) return next
                closed.await()
                return -1
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                val next = read()
                if (next < 0) return -1
                b[off] = next.toByte()
                return 1 // force multibyte UTF-8 characters across read boundaries
            }
        }
        override val output = object : OutputStream() {
            override fun write(value: Int) {
                writeStarted.countDown()
                if (blockWrites) { closed.await(); throw IOException("Closed") }
                sent.write(value)
                if (sent.size() >= 160) allWritten.countDown()
            }
        }
        override fun connect() {
            started.countDown()
            if (refused) throw IOException("Refused")
            if (blockConnect) { closed.await(); throw IOException("Cancelled") }
        }
        override fun close() { closed.countDown() }
    }
}
