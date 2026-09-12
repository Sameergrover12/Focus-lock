package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Focus Lock", appName)
  }

  @Test
  fun `verify system packages are filtered`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    org.junit.Assert.assertTrue(com.example.util.ScreenTimeHelper.isIgnoredSystemPackage("com.google.android.permissioncontroller", context))
    org.junit.Assert.assertTrue(com.example.util.ScreenTimeHelper.isIgnoredSystemPackage("com.android.launcher", context))
    org.junit.Assert.assertTrue(com.example.util.ScreenTimeHelper.isIgnoredSystemPackage("com.android.launcher3", context))
    org.junit.Assert.assertTrue(com.example.util.ScreenTimeHelper.isIgnoredSystemPackage("com.android.systemui", context))
    org.junit.Assert.assertTrue(com.example.util.ScreenTimeHelper.isIgnoredSystemPackage("android", context))
    org.junit.Assert.assertTrue(com.example.util.ScreenTimeHelper.isIgnoredSystemPackage("com.google.android.inputmethod.latin", context))
    org.junit.Assert.assertTrue(com.example.util.ScreenTimeHelper.isIgnoredSystemPackage("com.example", context))
  }
}
