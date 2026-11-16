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
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class PhoneScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun walkthrough() {
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1500)
        rule.onRoot().captureRoboImage("screenshots/phone_01_boot.png")
        rule.mainClock.advanceTimeBy(4000)
        rule.onRoot().captureRoboImage("screenshots/phone_02_targets.png")
        val vm = rule.activity.let { androidx.lifecycle.ViewModelProvider(it)[TabletViewModel::class.java] }
        rule.runOnUiThread { vm.call(1); vm.call(2) }
        rule.mainClock.advanceTimeBy(2000)
        rule.runOnUiThread { vm.hangUp(2) }
        rule.mainClock.advanceTimeBy(1300)
        rule.onRoot().captureRoboImage("screenshots/phone_03_ringing.png")
        rule.onNodeWithText("FEEDS").performClick()
        rule.mainClock.advanceTimeBy(2500)
        rule.onRoot().captureRoboImage("screenshots/phone_04_feeds.png")
        rule.onNodeWithText("LOG").performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onRoot().captureRoboImage("screenshots/phone_05_log.png")
        rule.onNodeWithText("CONFIG").performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onRoot().captureRoboImage("screenshots/phone_06_config.png")
    }
}
