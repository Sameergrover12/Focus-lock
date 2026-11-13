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

  @Test
  fun `verify modular user_backups subcollection reconstruction`() {
    val uid = "test_firebase_uid_123"
    val modularDocs = mapOf(
        com.example.data.backup.BackupManager.DOC_BLOCKED_APPS to mapOf(
            "userId" to uid,
            "blockedApps" to listOf(
                mapOf("packageName" to "com.instagram.android", "appName" to "Instagram", "dateAdded" to 1000L)
            ),
            "blockedPackageNames" to listOf("com.instagram.android")
        ),
        com.example.data.backup.BackupManager.DOC_BLOCKED_SITES to mapOf(
            "userId" to uid,
            "blockedWebsites" to listOf(
                mapOf("id" to 1L, "domainOrUrl" to "reddit.com", "dateAdded" to 1000L)
            ),
            "blockedKeywords" to listOf(
                mapOf("id" to 1L, "keyword" to "shorts", "caseSensitive" to false, "dateAdded" to 1000L)
            )
        ),
        com.example.data.backup.BackupManager.DOC_APP_GROUPS to mapOf(
            "userId" to uid,
            "appGroups" to listOf(
                mapOf(
                    "id" to 1L,
                    "name" to "Focus Work",
                    "scheduleEnabled" to true,
                    "scheduleStart" to "09:00",
                    "scheduleEnd" to "17:00",
                    "daysOfWeek" to listOf(1, 2, 3, 4, 5),
                    "memberPackages" to listOf("com.instagram.android")
                )
            )
        ),
        com.example.data.backup.BackupManager.DOC_SCREEN_TIME_LIMITS to mapOf(
            "userId" to uid,
            "screenTimeLimits" to listOf(
                mapOf(
                    "packageName" to "com.instagram.android",
                    "dailyLimitMinutes" to 45,
                    "sessionLimitSeconds" to 600,
                    "cooldownSeconds" to 900
                )
            )
        )
    )

    val reconstructed = com.example.data.backup.BackupManager.reconstructStateMapFromModularDocs(modularDocs)
    @Suppress("UNCHECKED_CAST")
    val focusData = reconstructed["focus_lock_data"] as Map<String, Any?>
    assertEquals(1, (focusData["blockedApps"] as List<*>).size)
    assertEquals(1, (focusData["blockedWebsites"] as List<*>).size)
    assertEquals(1, (focusData["blockedKeywords"] as List<*>).size)
    assertEquals(1, (focusData["appGroups"] as List<*>).size)
    assertEquals(1, (focusData["screenTimeLimits"] as List<*>).size)
  }

  @Test
  fun `verify firebase initialization and default web client id`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val initialized = com.example.util.FirebaseInitializer.ensureInitialized(context)
    org.junit.Assert.assertTrue(initialized)
    val app = com.google.firebase.FirebaseApp.getInstance()
    assertEquals("focus-lock-a0d19", app.options.projectId)
    assertEquals("1:582909401822:android:f5c224c7be71cc455b804f", app.options.applicationId)
    assertEquals("582909401822", app.options.gcmSenderId)
    val webClientId = context.getString(R.string.default_web_client_id)
    org.junit.Assert.assertTrue(webClientId.startsWith("582909401822-"))
  }

  @Test
  fun `verify runtime app signing sha1 helper`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sha1 = com.example.data.backup.BackupManager.getAppSigningSha1(context)
    org.junit.Assert.assertTrue(sha1.isNotBlank())
  }
}
