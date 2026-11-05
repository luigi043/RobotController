package com.robotcontroller

import android.graphics.Bitmap
import androidx.core.widget.NestedScrollView
import android.view.ViewGroup
import androidx.core.view.children
import android.content.Context
import android.bluetooth.BluetoothManager
import android.os.SystemClock
import android.os.Environment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NativeWalkthroughTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private fun capture(name: String) {
        device.waitForIdle(1500)
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "verification").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun waitText(text: String) {
        assertTrue("Missing text: $text", device.findObject(UiSelector().textContains(text)).waitForExists(20000))
    }
    private fun awaitBluetooth(enabled: Boolean) {
        val adapter = (instrumentation.targetContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val deadline = SystemClock.elapsedRealtime() + 30000
        while (adapter.isEnabled != enabled && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        assertEquals("Bluetooth adapter state", enabled, adapter.isEnabled)
    }
    private fun click(id: Int) {
        onView(withId(id)).perform(scrollTo(), click())
        if (id in listOf(R.id.btnDemo, R.id.btnDisconnect, R.id.btnSimulateLoss)) {
            onView(withId(R.id.tvStatus)).perform(scrollTo())
        }
    }

    @Test fun walkthroughWithoutPhysicalHardware() {
        val qemu = device.executeShellCommand("getprop ro.kernel.qemu").trim()
        assertEquals("Native walkthrough is emulator-only", "1", qemu)
        // Clear only the system Settings activity left by an earlier test attempt.
        device.executeShellCommand("am force-stop com.android.settings")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitText("offline demo")
            capture("01-launch-no-permission")
            onView(withId(R.id.btnConnect)).check(matches(not(isEnabled())))
            click(R.id.btnRefresh)
            val deny = device.findObject(UiSelector().resourceId("com.android.permissioncontroller:id/permission_deny_button"))
            assertTrue(deny.waitForExists(20000))
            capture("02-nearby-permission-dialog")
            deny.click()
            waitText("permission denied")
            capture("03-permission-denied")
            click(R.id.btnAppSettings)
            assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")), 15000))
            capture("04-system-app-permissions")
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.pkg("com.robotcontroller.debug")), 15000))
            onView(withId(R.id.tvConnectionHelp)).perform(scrollTo())
            waitText("permission denied")
            click(R.id.btnRefresh)
            val allow = device.findObject(UiSelector().resourceId("com.android.permissioncontroller:id/permission_allow_button"))
            assertTrue(allow.waitForExists(20000))
            allow.click()
            // Explicitly exercise Bluetooth-off recovery on this emulator only.
            device.executeShellCommand("svc bluetooth disable")
            awaitBluetooth(false)
            click(R.id.btnRefresh)
            val enable = device.findObject(UiSelector().resourceId("android:id/button1"))
            assertTrue(enable.waitForExists(20000))
            capture("05-enable-bluetooth-dialog")
            enable.click()
            awaitBluetooth(true)
            assertTrue(device.wait(Until.hasObject(By.pkg("com.robotcontroller.debug")), 15000))
            onView(withId(R.id.tvConnectionHelp)).perform(scrollTo())
            click(R.id.btnRefresh)
            waitText("No paired devices")
            capture("06-no-paired-devices")
            click(R.id.btnSettings)
            assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")), 15000))
            capture("07-system-bluetooth-settings")
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.pkg("com.robotcontroller.debug")), 15000))
            onView(withId(R.id.tvConnectionHelp)).perform(scrollTo())
            waitText("offline demo")
            click(R.id.btnDemo)
            waitText("Demo connected")
            capture("08-demo-connected")
            click(R.id.btnForward)
            onView(withId(R.id.btnForward)).check(matches(isEnabled()))
            click(R.id.btnBackward)
            click(R.id.btnLeft)
            click(R.id.btnRight)
            click(R.id.btnStop)
            click(R.id.btnRed)
            click(R.id.btnBlue)
            click(R.id.btnGreen)
            click(R.id.btnAutoMode)
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
                root.children.filterIsInstance<NestedScrollView>().first().apply { scrollTo(0, getChildAt(0).height) }
            }
            onView(withId(R.id.tvReceivedData)).check(matches(isDisplayed()))
            waitText("SIM OK: AUTO")
            onView(withId(R.id.tvReceivedData)).check(matches(withText(containsString("SIM OK: F"))))
            onView(withId(R.id.tvReceivedData)).check(matches(withText(containsString("SIM OK: S"))))
            capture("09-controls-and-simulated-log")
            click(R.id.btnClearData)
            onView(withId(R.id.tvReceivedData)).check(matches(withText(R.string.no_data)))
            capture("10-cleared-log")
            click(R.id.btnSimulateLoss)
            waitText("Connection error")
            capture("11-simulated-connection-loss")
            onView(withId(R.id.btnForward)).check(matches(not(isEnabled())))
            click(R.id.btnDemo)
            waitText("Demo connected")
            click(R.id.btnDisconnect)
            waitText("Disconnected")
            capture("12-manual-disconnect")
            click(R.id.btnDemo)
            waitText("Demo connected")
            scenario.recreate()
            waitText("No paired devices")
            onView(withId(R.id.btnForward)).check(matches(not(isEnabled())))
            capture("13-recreated-disconnected")
        }
    }
}
