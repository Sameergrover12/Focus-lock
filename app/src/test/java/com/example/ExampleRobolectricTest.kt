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

  @Test
  fun `verify cross version backup json parsing`() {
    val sampleFutureOrLegacyJson = """
        {
          "version": 99,
          "unknownFutureField": { "nested": [1, 2, 3] },
          "blockedPackageNames": ["com.instagram.android", "com.zhiliaoapp.musically"],
          "blockedApps": [
            { "packageName": "com.google.android.youtube", "appName": "YouTube" }
          ],
          "appLimits": [
            { "packageName": "com.instagram.android", "limitMinutes": 30, "sessionMinutes": 10, "cooldownMinutes": 15 }
          ],
          "groups": [
            {
              "id": 1,
              "name": "Deep Work",
              "scheduleEnabled": true,
              "startHour": 22,
              "startMinute": 30,
              "endHour": 6,
              "endMinute": 15,
              "daysOfWeek": [1, 2, 3, 4, 5],
              "memberPackages": ["com.instagram.android"]
            }
          ],
          "websites": ["reddit.com", { "domainOrUrl": "x.com" }],
          "keywords": ["shorts", { "keyword": "spoiler", "caseSensitive": true }],
          "theme": {
            "baseThemeMode": "AMOLED",
            "secondaryPalette": "CYBER_VIOLET"
          }
        }
    """.trimIndent()

    val summary = com.example.data.backup.BackupManager.parseCrossVersionBackupSummary(sampleFutureOrLegacyJson)
    assertEquals(
        listOf("com.instagram.android", "com.zhiliaoapp.musically", "com.google.android.youtube"),
        summary.blockedPackageNames
    )
    assertEquals(1, summary.limitsCount)
    assertEquals(1, summary.groupsCount)
    assertEquals(2, summary.websitesCount)
    assertEquals(2, summary.keywordsCount)
    assertEquals("AMOLED", summary.baseThemeMode)
    assertEquals("CYBER_VIOLET", summary.secondaryPalette)
  }
}
