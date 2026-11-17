package dev.dokkaebi.tablet

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the real screens on the JVM so the UI can be checked without a device. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
class ScreenshotTest {
    val rule = createAndroidComposeRule<MainActivity>()

    /** Wipe saved settings before the activity launches so each run starts fresh. */
    @get:Rule val chain: org.junit.rules.RuleChain = org.junit.rules.RuleChain
        .outerRule(object : org.junit.rules.ExternalResource() {
            override fun before() {
                val app = org.robolectric.RuntimeEnvironment.getApplication()
                listOf("tablet", "recon").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
            }
        })
        .around(rule)

    @Test fun walkthrough() {
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1500)
        rule.onRoot().captureRoboImage("screenshots/01_boot.png")
        rule.mainClock.advanceTimeBy(4000)
        rule.onRoot().captureRoboImage("screenshots/02_targets.png")
        val vm = rule.activity.let { androidx.lifecycle.ViewModelProvider(it)[TabletViewModel::class.java] }
        rule.runOnUiThread { vm.call(1); vm.call(2) }
        rule.mainClock.advanceTimeBy(2000)
        rule.runOnUiThread { vm.hangUp(2) }
        rule.mainClock.advanceTimeBy(1300)
        rule.onRoot().captureRoboImage("screenshots/03_ringing.png")
        rule.onNodeWithText("FEEDS").performClick()
        rule.mainClock.advanceTimeBy(2500)
        rule.onRoot().captureRoboImage("screenshots/04_feeds.png")
        rule.onNodeWithText("LOG").performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onRoot().captureRoboImage("screenshots/05_log.png")
        rule.onNodeWithText("CONFIG").performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onRoot().captureRoboImage("screenshots/06_config.png")

        // RECON: authorization gate, then a real port scan of a local test service.
        rule.onNodeWithText("RECON").performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onRoot().captureRoboImage("screenshots/07_recon_gate.png")
        rule.onNodeWithText("UNDERSTAND", substring = true).performClick()
        val server = java.net.ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
        kotlin.concurrent.thread(isDaemon = true) {
            while (!server.isClosed) runCatching {
                server.accept().use { it.getOutputStream().write("SSH-2.0-OpenSSH_9.6 demo\r\n".toByteArray()); Thread.sleep(50) }
            }
        }
        val rvm = androidx.lifecycle.ViewModelProvider(rule.activity)[dev.dokkaebi.tablet.recon.ReconViewModel::class.java]
        rule.runOnUiThread {
            rvm.tool = dev.dokkaebi.tablet.recon.ReconTool.PORTS
            rvm.portHost = "127.0.0.1"
            rvm.portSpec = "${server.localPort},21,23,80,443"
            rvm.startPortScan()
        }
        repeat(30) { Thread.sleep(100); rule.mainClock.advanceTimeBy(100) }
        rule.onRoot().captureRoboImage("screenshots/08_recon_ports.png")
        server.close()

        // REAL contact mode
        rule.runOnUiThread {
            vm.switchMode(ContactMode.REAL)
            vm.addContact("Mom", "+1 (555) 010-2030")
            vm.addContact("Jae-won", "+82 10-5555-0199")
        }
        rule.onNodeWithText("CONFIG").performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onRoot().captureRoboImage("screenshots/09_real_config.png")
        rule.onNodeWithText("TARGETS").performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onRoot().captureRoboImage("screenshots/10_real_targets.png")
        rule.runOnUiThread { vm.switchMode(ContactMode.FAKE) }
    }
}
