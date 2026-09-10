package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.blocked.BlockedActivity
import com.example.ui.blocked.BlockedScreen
import com.example.ui.theme.FocusLockTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun blocked_screen_screenshot() {
    composeTestRule.setContent {
      FocusLockTheme {
        BlockedScreen(
          title = "Instagram",
          reason = "You have reached your 45m daily screen time limit.",
          type = BlockedActivity.TYPE_SCREEN_TIME,
          nextWindow = null,
          onGoHome = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/blocked_screen.png")
  }
}
