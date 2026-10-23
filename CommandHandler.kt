package com.robotcontroller.bluetooth

// ─────────────────────────────────────────────────────────────────────────────
//  CommandHandler
//
//  Central catalogue of every string command the robot firmware understands.
//  Using a single object keeps command strings in one place, making firmware
//  updates trivial (change here → works everywhere).
// ─────────────────────────────────────────────────────────────────────────────

object CommandHandler {

    // Movement
    const val FORWARD  = "F\n"
    const val BACKWARD = "B\n"
    const val LEFT     = "L\n"
    const val RIGHT    = "R\n"
    const val STOP     = "S\n"

    // LED colour
    const val RED   = "RED\n"
    const val BLUE  = "BLUE\n"
    const val GREEN = "GREEN\n"

    // Modes
    const val AUTO = "AUTO\n"

    // ── Convenience helpers ───────────────────────────────────────────────────

    /**
     * Sends [command] only when the manager reports an active connection.
     * Silently no-ops otherwise (the UI should already disable controls).
     */
    fun send(manager: BluetoothManager, command: String) {
        if (manager.isConnected) {
            manager.sendCommand(command)
        }
    }

    /**
     * Build a custom raw command string (newline appended automatically).
     */
    fun custom(raw: String): String = "${raw.trimEnd()}\n"
}
