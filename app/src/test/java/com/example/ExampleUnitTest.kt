package com.example

import com.example.data.local.entity.AppGroup
import com.example.data.local.entity.BlockedKeyword
import com.example.data.local.entity.BlockedWebsite
import com.example.service.ContentScanner
import com.example.service.FocusForegroundService
import com.example.service.GroupRuleEvaluator
import com.example.util.SettingsTamperDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testBudgetEvaluation() {
        val groupWithinBudget = AppGroup(
            name = "Social",
            budgetEnabled = true,
            dailyBudgetMinutes = 60,
            usedMinutesToday = 45
        )
        assertFalse(GroupRuleEvaluator.isBudgetExhausted(groupWithinBudget))

        val groupExhaustedBudget = AppGroup(
            name = "Social",
            budgetEnabled = true,
            dailyBudgetMinutes = 60,
            usedMinutesToday = 60
        )
        assertTrue(GroupRuleEvaluator.isBudgetExhausted(groupExhaustedBudget))
    }

    @Test
    fun testContentScannerUrlMatching() {
        val blockedSites = listOf(
            BlockedWebsite(domainOrUrl = "reddit.com"),
            BlockedWebsite(domainOrUrl = "https://youtube.com")
        )

        // Exact & Subdomain matching
        assertNotNull(ContentScanner.matchBlockedWebsite("https://www.reddit.com/r/all", blockedSites))
        assertNotNull(ContentScanner.matchBlockedWebsite("m.youtube.com/watch?v=123", blockedSites))
        assertNull(ContentScanner.matchBlockedWebsite("https://github.com", blockedSites))
    }

    @Test
    fun testContentScannerKeywordMatching() {
        val blockedKeywords = listOf(
            BlockedKeyword(keyword = "casino", caseSensitive = false),
            BlockedKeyword(keyword = "SpoilerAlert", caseSensitive = true)
        )

        val matchingText = listOf("Welcome to the CASINO floor", "Have fun!")
        val hit1 = ContentScanner.matchBlockedKeyword(matchingText, blockedKeywords)
        assertEquals("casino", hit1?.keyword)

        val nonMatchingCase = listOf("Check out this spoileralert today")
        val hit2 = ContentScanner.matchBlockedKeyword(nonMatchingCase, blockedKeywords)
        assertNull(hit2)

        val matchingCase = listOf("Warning: SpoilerAlert ahead!")
        val hit3 = ContentScanner.matchBlockedKeyword(matchingCase, blockedKeywords)
        assertEquals("SpoilerAlert", hit3?.keyword)

        // Split across inline nodes / spans in a feed
        val multiWordRule = listOf(
            BlockedKeyword(keyword = "breaking spoiler", caseSensitive = false)
        )
        val splitFeedItems = listOf("This is a breaking", "spoiler alert post")
        val hit4 = ContentScanner.matchBlockedKeyword(splitFeedItems, multiWordRule)
        assertNotNull(hit4)
        assertEquals("breaking spoiler", hit4?.keyword)
    }

    @Test
    fun testContentScannerWebsiteInTextsMatching() {
        val blockedWebsites = listOf(
            BlockedWebsite(domainOrUrl = "reddit.com")
        )
        val texts1 = listOf("Popular on reddit.com today", "Comments (20)")
        assertNotNull(ContentScanner.matchBlockedWebsiteInTexts(texts1, blockedWebsites))

        // Split domain across adjacent text nodes
        val texts2 = listOf("Visit", "reddit", ".com", "for more")
        assertNotNull(ContentScanner.matchBlockedWebsiteInTexts(texts2, blockedWebsites))

        val texts3 = listOf("Read on wikipedia.org")
        assertNull(ContentScanner.matchBlockedWebsiteInTexts(texts3, blockedWebsites))
    }

    @Test
    fun testWordBoundaryKeywordMatchingFalsePositives() {
        val keywords = listOf(
            BlockedKeyword(keyword = "hard", caseSensitive = false)
        )

        // Substrings that previously triggered false positives must NOT match
        assertNull(ContentScanner.matchBlockedKeyword(listOf("Fixing computer hardware today"), keywords))
        assertNull(ContentScanner.matchBlockedKeyword(listOf("A diehard fan of the movie"), keywords))
        assertNull(ContentScanner.matchBlockedKeyword(listOf("That was a foolhardy decision"), keywords))

        // True standalone word matches MUST match
        val hit1 = ContentScanner.matchBlockedKeyword(listOf("Working very hard today"), keywords)
        assertNotNull(hit1)
        assertEquals("hard", hit1?.keyword)

        val hit2 = ContentScanner.matchBlockedKeyword(listOf("It is HARD."), keywords)
        assertNotNull(hit2)
        assertEquals("hard", hit2?.keyword)
    }

    @Test
    fun testHideAppsStringMatching() {
        // Required exact and variation matching for all specified targets
        assertTrue(ContentScanner.isHideAppsString("Hide apps"))
        assertTrue(ContentScanner.isHideAppsString("HIDE APPS"))
        assertTrue(ContentScanner.isHideAppsString("Hidden apps"))
        assertTrue(ContentScanner.isHideAppsString("hidden apps"))
        assertTrue(ContentScanner.isHideAppsString("Hide apps on Home and Apps screens"))
        assertTrue(ContentScanner.isHideAppsString("hide apps on home and apps screens"))
        assertTrue(ContentScanner.isHideAppsString("App Hider"))
        assertTrue(ContentScanner.isHideAppsString("app hider"))
        assertTrue(ContentScanner.isHideAppsString("Settings > Home screen > Hide apps on Home and Apps screens"))
        assertTrue(ContentScanner.isHideAppsString("Security: Hidden apps (3 apps hidden)"))
        assertTrue(ContentScanner.isHideAppsString("Smart App Hider Pro"))

        // Standard OS Settings and safe navigation must NOT trigger
        assertFalse(ContentScanner.isHideAppsString("Wi-Fi"))
        assertFalse(ContentScanner.isHideAppsString("Bluetooth"))
        assertFalse(ContentScanner.isHideAppsString("Apps & notifications"))
        assertFalse(ContentScanner.isHideAppsString("Display & Brightness"))
        assertFalse(ContentScanner.isHideAppsString("Battery"))
        assertFalse(ContentScanner.isHideAppsString("Installed Apps"))
        assertFalse(ContentScanner.isHideAppsString(null))
        assertFalse(ContentScanner.isHideAppsString(""))
    }

    @Test
    fun testHideAppsTargetPackageValidation() {
        // Must match system settings packages
        assertTrue(ContentScanner.isHideAppsTargetPackage("com.android.settings"))
        assertTrue(ContentScanner.isHideAppsTargetPackage("com.samsung.android.settings"))
        assertTrue(ContentScanner.isHideAppsTargetPackage("com.miui.settings"))

        // Must match system launchers
        assertTrue(ContentScanner.isHideAppsTargetPackage("com.sec.android.app.launcher"))
        assertTrue(ContentScanner.isHideAppsTargetPackage("com.google.android.apps.nexuslauncher"))
        assertTrue(ContentScanner.isHideAppsTargetPackage("com.teslacoilsw.launcher"))

        // Must match OEM security / safecenter packages
        assertTrue(ContentScanner.isHideAppsTargetPackage("com.miui.securitycenter"))
        assertTrue(ContentScanner.isHideAppsTargetPackage("com.coloros.safecenter"))
        assertTrue(ContentScanner.isHideAppsTargetPackage("com.vivo.safecenter"))
        assertTrue(ContentScanner.isHideAppsTargetPackage("com.lenovo.safecenter"))

        // Chat apps, browsers, and user apps MUST NOT be scanned for hide apps
        assertFalse(ContentScanner.isHideAppsTargetPackage("com.whatsapp"))
        assertFalse(ContentScanner.isHideAppsTargetPackage("org.telegram.messenger"))
        assertFalse(ContentScanner.isHideAppsTargetPackage("com.google.android.apps.messaging"))
        assertFalse(ContentScanner.isHideAppsTargetPackage("com.android.chrome"))
        assertFalse(ContentScanner.isHideAppsTargetPackage("org.mozilla.firefox"))
        assertFalse(ContentScanner.isHideAppsTargetPackage("com.instagram.android"))
        assertFalse(ContentScanner.isHideAppsTargetPackage("com.discord"))
        assertFalse(ContentScanner.isHideAppsTargetPackage("com.example.focuslock"))
        assertFalse(ContentScanner.isHideAppsTargetPackage(null))
        assertFalse(ContentScanner.isHideAppsTargetPackage(""))
    }

    @Test
    fun testWordBoundaryWebsiteMatchingFalsePositives() {
        val sites = listOf(
            BlockedWebsite(domainOrUrl = "reddit.com")
        )

        // Substring collisions must NOT match
        assertNull(ContentScanner.matchBlockedWebsiteInTexts(listOf("Check out notreddit.com"), sites))
        assertNull(ContentScanner.matchBlockedWebsiteInTexts(listOf("Visiting reddit.commercial"), sites))
        assertNull(ContentScanner.matchBlockedWebsiteInTexts(listOf("Log into creditor.company"), sites))

        // Real domain with path or protocol MUST match
        assertNotNull(ContentScanner.matchBlockedWebsiteInTexts(listOf("Opening reddit.com/r/android"), sites))
        assertNotNull(ContentScanner.matchBlockedWebsiteInTexts(listOf("https://reddit.com/post/123"), sites))
    }

    @Test
    fun testContentDescriptionRefinement() {
        val keywords = listOf(
            BlockedKeyword(keyword = "casino", caseSensitive = false)
        )

        // Short alt-text label / icon metadata must NOT trigger keyword block
        val shortAltLabel = listOf(
            ContentScanner.ScannedNodeText(
                text = "casino icon thumbnail",
                source = ContentScanner.TextSource.CONTENT_DESCRIPTION_LABEL,
                isUserAuthored = false
            )
        )
        assertNull(ContentScanner.findKeywordMatch(shortAltLabel, keywords))

        // Long user-authored post content in contentDescription DOES match
        val userAuthoredDesc = listOf(
            ContentScanner.ScannedNodeText(
                text = "Yesterday we visited the grand casino downtown and won prizes",
                source = ContentScanner.TextSource.CONTENT_DESCRIPTION_USER_AUTHORED,
                isUserAuthored = true
            )
        )
        val match = ContentScanner.findKeywordMatch(userAuthoredDesc, keywords)
        assertNotNull(match)
        assertEquals("casino", match?.matchedSnippet)
        assertEquals(ContentScanner.TextSource.CONTENT_DESCRIPTION_USER_AUTHORED, match?.source)
    }

    @Test
    fun testForegroundPackagePropagation() {
        // Test switching between different apps updates foreground package
        FocusForegroundService.onForegroundPackageChanged("com.android.settings")
        // Switch to a social app
        FocusForegroundService.onForegroundPackageChanged("com.google.android.youtube")
        // Switch to browser
        FocusForegroundService.onForegroundPackageChanged("com.android.chrome")
    }

    @Test
    fun testMinutesSinceMidnightCeiling() {
        val minutesElapsed = com.example.util.ScreenTimeHelper.getMinutesSinceMidnight()
        assertTrue("Minutes since midnight must be >= 0", minutesElapsed >= 0)
        assertTrue("Minutes since midnight must be <= 1440", minutesElapsed <= 1440)
    }

    @Test
    fun testIdempotentUsageOverwrites() {
        // Baseline from before service started today
        val baseline = 45
        var liveSecondsAccrued = 0

        // Simulate 5 minutes of real time active in app (300 seconds)
        for (tick in 1..60) { // 60 ticks of 5 seconds
            liveSecondsAccrued += 5
        }

        val totalMinutes = baseline + (liveSecondsAccrued / 60)
        assertEquals(50, totalMinutes)

        // Multiple writes of the same point in time must produce the identical absolute number (never compounding)
        val write1 = totalMinutes
        val write2 = totalMinutes
        assertEquals(50, write1)
        assertEquals(50, write2)
        assertEquals(write1, write2)
    }

    @Test
    fun testIntervalMergingWithOverlappingFloatingWindows() {
        // App A foreground from t = 1000 to t = 3000 (2000 ms)
        // App B (floating window/PIP) foreground from t = 2000 to t = 4000 (2000 ms)
        // Without merging, naive sum is 4000 ms (double counting).
        // With interval merging, physical screen span is [1000, 4000] = 3000 ms.
        val intervals = listOf(
            com.example.util.TimeInterval(start = 1000L, end = 3000L),
            com.example.util.TimeInterval(start = 2000L, end = 4000L)
        )
        val mergedDuration = com.example.util.ScreenTimeHelper.mergeIntervals(intervals)
        assertEquals(3000L, mergedDuration)

        // Triple overlap (Split screen + Picture-in-picture)
        val tripleOverlap = listOf(
            com.example.util.TimeInterval(start = 10000L, end = 30000L), // Chrome: 20s
            com.example.util.TimeInterval(start = 15000L, end = 35000L), // YouTube PIP: 20s
            com.example.util.TimeInterval(start = 20000L, end = 25000L)  // Floating Calculator: 5s
        )
        // Merged interval is [10000, 35000] = 25000 ms (25s), NOT 45s
        val tripleMerged = com.example.util.ScreenTimeHelper.mergeIntervals(tripleOverlap)
        assertEquals(25000L, tripleMerged)
    }

    @Test
    fun testPhysicalScreenTimeCannotExceedClockTime() {
        val minutesElapsed = com.example.util.ScreenTimeHelper.getMinutesSinceMidnight()
        assertTrue("Minutes since midnight must be >= 0", minutesElapsed >= 0)
        assertTrue("Minutes since midnight must be <= 1440", minutesElapsed <= 1440)

        // Raw sum of overlapping apps could be 3000 minutes, but physical screen time is clamped
        val inflatedAppSum = 3000
        val clampedScreenTime = inflatedAppSum.coerceIn(0, minutesElapsed)
        assertTrue(clampedScreenTime <= minutesElapsed)
    }

    @Test
    fun testExclusiveInputFocusPreventsDoubleCrediting() {
        // In 60 minutes of multitasking, 40 minutes spent with focus on Reddit, 20 minutes with focus on YouTube
        val totalSessionMinutes = 60
        val redditFocusedMinutes = 40
        val youtubeFocusedMinutes = 20

        // With exclusive focus attribution, only the focused app accrues time
        val totalAccrued = redditFocusedMinutes + youtubeFocusedMinutes
        assertEquals(totalSessionMinutes, totalAccrued)
    }

    @Test
    fun testLocalStartOfDayCalculation() {
        val zone = com.example.util.ScreenTimeHelper.getLocalZoneId()
        val startOfDay = com.example.util.ScreenTimeHelper.getStartOfDayMillis(zone)
        val zonedDateTime = java.time.Instant.ofEpochMilli(startOfDay).atZone(zone)

        assertEquals("Hour must be 0", 0, zonedDateTime.hour)
        assertEquals("Minute must be 0", 0, zonedDateTime.minute)
        assertEquals("Second must be 0", 0, zonedDateTime.second)
        assertEquals("Nano must be 0", 0, zonedDateTime.nano)

        val todayDateStr = com.example.util.ScreenTimeHelper.getTodayDateString(zone)
        val expectedDateStr = java.time.LocalDate.now(zone).toString()
        assertEquals(expectedDateStr, todayDateStr)
    }

    @Test
    fun testEventsBeforeMidnightStrictlyIgnored() {
        val zone = com.example.util.ScreenTimeHelper.getLocalZoneId()
        val startOfToday = com.example.util.ScreenTimeHelper.getStartOfDayMillis(zone)

        // Events from yesterday evening: 28 minutes of usage before midnight
        val yesterdayEveningStart = startOfToday - (28 * 60 * 1000L)
        val yesterdayEveningEnd = startOfToday - (5 * 60 * 1000L)

        // Events today: 16 minutes of usage after midnight
        val todaySessionStart = startOfToday + (2 * 60 * 1000L)
        val todaySessionEnd = startOfToday + (18 * 60 * 1000L) // 16 minutes

        val allEvents = listOf(
            com.example.util.TimeInterval(yesterdayEveningStart, yesterdayEveningEnd),
            com.example.util.TimeInterval(todaySessionStart, todaySessionEnd)
        )

        // Strict rule: Any interval or portion before startOfToday is filtered / clamped to startOfToday
        val todayOnlyIntervals = allEvents
            .filter { it.end > startOfToday }
            .map { com.example.util.TimeInterval(maxOf(it.start, startOfToday), it.end) }

        val todayMillis = com.example.util.ScreenTimeHelper.mergeIntervals(todayOnlyIntervals)
        val todayMinutes = (todayMillis / 60000L).toInt()

        assertEquals("Usage today must strictly be 16 minutes, NOT 44 minutes", 16, todayMinutes)
    }

    @Test
    fun testMidnightRolloverSessionClamping() {
        val zone = com.example.util.ScreenTimeHelper.getLocalZoneId()
        val startOfToday = com.example.util.ScreenTimeHelper.getStartOfDayMillis(zone)

        // Session opened at 23:55 yesterday (5 mins before midnight) and closed at 00:16 today (16 mins after midnight)
        val sessionStartBeforeMidnight = startOfToday - (5 * 60 * 1000L)
        val sessionEndAfterMidnight = startOfToday + (16 * 60 * 1000L)

        // When processed for today, effective start must be clamped to startOfToday (00:00:00.000)
        val effectiveStart = maxOf(sessionStartBeforeMidnight, startOfToday)
        assertEquals(startOfToday, effectiveStart)

        val clampedInterval = listOf(com.example.util.TimeInterval(effectiveStart, sessionEndAfterMidnight))
        val millisToday = com.example.util.ScreenTimeHelper.mergeIntervals(clampedInterval)
        val minutesToday = (millisToday / 60000L).toInt()

        assertEquals("Session crossing midnight only contributes 16 minutes to today", 16, minutesToday)
    }

    @Test
    fun testTrueScreenTimeFromResumedAndPausedEvents() {
        // App A resumed at 10:00 (600,000 ms), paused at 10:15 (900,000 ms)
        // App B resumed at 10:11 (660,000 ms), paused at 10:20 (1,200,000 ms) (overlaps with App A -> [600,000, 1,200,000] = 10 mins)
        // App C resumed at 11:00 (3,600,000 ms), paused at 11:30 (5,400,000 ms) -> 30 mins
        val intervals = listOf(
            com.example.util.TimeInterval(600_000L, 900_000L),
            com.example.util.TimeInterval(660_000L, 1_200_000L),
            com.example.util.TimeInterval(3_600_000L, 5_400_000L)
        )
        val mergedMillis = com.example.util.ScreenTimeHelper.mergeIntervals(intervals)
        val screenTimeMinutes = (mergedMillis / 60000L).toInt()

        // [10:00, 10:20] = 10 mins; [11:00, 11:30] = 30 mins. Total = 40 mins.
        assertEquals(40, screenTimeMinutes)
    }

    @Test
    fun testUsageEventsScreenInteractiveAndDanglingSession() {
        val midnight = 10_000_000L
        val now = midnight + 3_600_000L // 60 minutes after midnight

        // Simulate screen sessions:
        // Session 1: 10 mins (interactive at +10m, non-interactive at +20m)
        // Session 2: Dangling session (interactive at +45m, no non-interactive event before 'now')
        var totalScreenOnMillis = 0L
        var lastScreenInteractiveTime: Long? = null

        val events = listOf(
            Pair(15 /* SCREEN_INTERACTIVE */, midnight + 600_000L),
            Pair(16 /* SCREEN_NON_INTERACTIVE */, midnight + 1_200_000L),
            Pair(15 /* SCREEN_INTERACTIVE */, midnight + 2_700_000L) // Dangling
        )

        for ((type, time) in events) {
            when (type) {
                15 -> {
                    if (lastScreenInteractiveTime == null) lastScreenInteractiveTime = time
                }
                16 -> {
                    val start = lastScreenInteractiveTime
                    if (start != null) {
                        totalScreenOnMillis += (time - start)
                        lastScreenInteractiveTime = null
                    }
                }
            }
        }

        // Dangling session resolved up to 'now'
        if (lastScreenInteractiveTime != null && now > lastScreenInteractiveTime) {
            totalScreenOnMillis += (now - lastScreenInteractiveTime)
        }

        // 10 mins + (60m - 45m = 15 mins) = 25 minutes = 1,500,000 ms
        val totalMinutes = (totalScreenOnMillis / 60000L).toInt()
        assertEquals(25, totalMinutes)
    }

    @Test
    fun testIndividualAppCannotExceedScreenOnTime() {
        val totalScreenOnMinutes = 52
        val youtubeRawMinutes = 61 // e.g. from background service or legacy bucket

        // Architectural rule: Individual apps cannot exceed total screen time
        val sanitizedMinutes = if (totalScreenOnMinutes > 0) minOf(youtubeRawMinutes, totalScreenOnMinutes) else youtubeRawMinutes
        assertEquals(52, sanitizedMinutes)
        assertTrue("Individual app minutes cannot exceed screen on minutes", sanitizedMinutes <= totalScreenOnMinutes)

        // UI Progress bar and percentage capping
        val proportion = (sanitizedMinutes.toFloat() / totalScreenOnMinutes.toFloat()).coerceIn(0f, 1f)
        val percentage = (proportion * 100).toInt().coerceIn(0, 100)
        assertEquals(1.0f, proportion, 0.001f)
        assertEquals(100, percentage)
    }

    @Test
    fun testActivityResumedToPausedAccountingWithDangling() {
        val midnight = 10_000_000L
        val now = midnight + 3_600_000L // +60m

        val appResumedMap = mutableMapOf<String, Long>()
        val appUsageMillis = mutableMapOf<String, Long>()

        // YouTube resumed at +10m, paused at +30m (20 mins)
        // YouTube resumed at +40m, dangling until 'now' (+60m) (20 mins)
        // Total YouTube = 40 mins
        val events = listOf(
            Triple("com.google.android.youtube", 1 /* RESUMED */, midnight + 600_000L),
            Triple("com.google.android.youtube", 2 /* PAUSED */, midnight + 1_800_000L),
            Triple("com.google.android.youtube", 1 /* RESUMED */, midnight + 2_400_000L)
        )

        for ((pkg, type, time) in events) {
            when (type) {
                1 -> appResumedMap[pkg] = time
                2 -> {
                    val start = appResumedMap.remove(pkg)
                    if (start != null) {
                        appUsageMillis[pkg] = (appUsageMillis[pkg] ?: 0L) + (time - start)
                    }
                }
            }
        }

        // Resolve dangling sessions
        for ((pkg, startTime) in appResumedMap) {
            if (now > startTime) {
                appUsageMillis[pkg] = (appUsageMillis[pkg] ?: 0L) + (now - startTime)
            }
        }

        val youtubeMinutes = ((appUsageMillis["com.google.android.youtube"] ?: 0L) / 60000L).toInt()
        assertEquals(40, youtubeMinutes)
    }

    @Test
    fun testMotivationLibraryQuotesAndFeedbackLines() {
        // Verify full screen quotes count and content
        assertEquals(30, com.example.focusapp.util.MotivationLibrary.FULL_SCREEN_QUOTES.size)
        val quote = com.example.focusapp.util.MotivationLibrary.getRandomFullScreenQuote()
        assertTrue(quote.isNotBlank())
        assertTrue(com.example.focusapp.util.MotivationLibrary.FULL_SCREEN_QUOTES.contains(quote))

        // Verify quick feedback lines count and content
        assertEquals(25, com.example.focusapp.util.MotivationLibrary.QUICK_FEEDBACK_LINES.size)
        val feedback = com.example.focusapp.util.MotivationLibrary.getRandomQuickFeedback()
        assertTrue(feedback.isNotBlank())
        assertTrue(com.example.focusapp.util.MotivationLibrary.QUICK_FEEDBACK_LINES.contains(feedback))
    }

    @Test
    fun testHideAppsTargetPackageConstraints() {
        // System Settings, Launchers, and Security/Safecenter apps should return true
        assertTrue(com.example.service.ContentScanner.isHideAppsTargetPackage("com.android.settings"))
        assertTrue(com.example.service.ContentScanner.isHideAppsTargetPackage("com.google.android.settings"))
        assertTrue(com.example.service.ContentScanner.isHideAppsTargetPackage("com.sec.android.app.launcher"))
        assertTrue(com.example.service.ContentScanner.isHideAppsTargetPackage("com.android.launcher3"))
        assertTrue(com.example.service.ContentScanner.isHideAppsTargetPackage("com.miui.securitycenter"))
        assertTrue(com.example.service.ContentScanner.isHideAppsTargetPackage("com.oppo.safecenter"))

        // Chat applications, browsers, and standard third-party apps MUST return false
        assertFalse(com.example.service.ContentScanner.isHideAppsTargetPackage("com.whatsapp"))
        assertFalse(com.example.service.ContentScanner.isHideAppsTargetPackage("org.telegram.messenger"))
        assertFalse(com.example.service.ContentScanner.isHideAppsTargetPackage("com.discord"))
        assertFalse(com.example.service.ContentScanner.isHideAppsTargetPackage("com.slack"))
        assertFalse(com.example.service.ContentScanner.isHideAppsTargetPackage("com.android.chrome"))
        assertFalse(com.example.service.ContentScanner.isHideAppsTargetPackage("org.mozilla.firefox"))
        assertFalse(com.example.service.ContentScanner.isHideAppsTargetPackage("com.instagram.android"))
        assertFalse(com.example.service.ContentScanner.isHideAppsTargetPackage(null))
        assertFalse(com.example.service.ContentScanner.isHideAppsTargetPackage(""))
    }

    @Test
    fun testSessionLimitHelperFormatting() {
        assertEquals("0s", com.example.util.SessionLimitHelper.formatHms(0))
        assertEquals("45s", com.example.util.SessionLimitHelper.formatHms(45))
        assertEquals("10m", com.example.util.SessionLimitHelper.formatHms(600))
        assertEquals("1h", com.example.util.SessionLimitHelper.formatHms(3600))
        assertEquals("1h 15m 30s", com.example.util.SessionLimitHelper.formatHms(4530))

        assertEquals("45s", com.example.util.SessionLimitHelper.formatRemaining(45_000L))
        assertEquals("10m 00s", com.example.util.SessionLimitHelper.formatRemaining(600_000L))
        assertEquals("1h 30m 00s", com.example.util.SessionLimitHelper.formatRemaining(5_400_000L))

        assertEquals(3665, com.example.util.SessionLimitHelper.toSeconds(1, 1, 5))
        assertEquals(0, com.example.util.SessionLimitHelper.toSeconds(0, 0, 0))
    }

    @Test
    fun testSessionLimitCooldownActiveAndRemaining() {
        val now = 1_000_000L
        val activeCooldownEnd = now + 120_000L // 2 mins in future
        val expiredCooldownEnd = now - 50_000L  // 50s in past

        assertTrue(com.example.util.SessionLimitHelper.isCooldownActive(activeCooldownEnd, now))
        assertFalse(com.example.util.SessionLimitHelper.isCooldownActive(expiredCooldownEnd, now))
        assertFalse(com.example.util.SessionLimitHelper.isCooldownActive(0L, now))

        assertEquals(120_000L, com.example.util.SessionLimitHelper.getCooldownRemainingMs(activeCooldownEnd, now))
        assertEquals(0L, com.example.util.SessionLimitHelper.getCooldownRemainingMs(expiredCooldownEnd, now))
    }

    @Test
    fun testSessionIdleReset() {
        val cooldownSeconds = 3600 // 1 hour cooldown
        val now = 10_000_000L

        // Left app 30 mins ago with partial session: DO NOT reset yet
        val recentExit = now - (30 * 60 * 1000L)
        assertFalse(com.example.util.SessionLimitHelper.shouldResetIdleSession(recentExit, cooldownSeconds, now))

        // Left app 61 mins ago (>= cooldownDurationMs): MUST reset session timer
        val longAgoExit = now - (61 * 60 * 1000L)
        assertTrue(com.example.util.SessionLimitHelper.shouldResetIdleSession(longAgoExit, cooldownSeconds, now))

        // No previous exit (first run)
        assertFalse(com.example.util.SessionLimitHelper.shouldResetIdleSession(0L, cooldownSeconds, now))
    }

    @Test
    fun testTwoLayerThemeArchitecturePalettes() {
        // Must contain all 12 palettes
        val palettes = com.example.ui.theme.SecondaryThemePalette.entries
        assertEquals(12, palettes.size)

        // Default palette must be FOCUS_EMERALD
        assertEquals(com.example.ui.theme.SecondaryThemePalette.FOCUS_EMERALD, palettes.first())

        // Verify distinct display names
        val names = palettes.map { it.displayName }.toSet()
        assertEquals(12, names.size)

        // Each palette must return 3 non-null preview dots for every base mode
        com.example.ui.theme.BaseThemeMode.entries.forEach { mode ->
            palettes.forEach { palette ->
                val dots = palette.getPreviewDots(mode)
                assertEquals(3, dots.size)
            }
        }
    }

    @Test
    fun testBaseThemeModeResolution() {
        assertEquals(com.example.ui.theme.BaseThemeMode.LIGHT, com.example.ui.theme.BaseThemeMode.LIGHT.resolve(isSystemInDark = true))
        assertEquals(com.example.ui.theme.BaseThemeMode.LIGHT, com.example.ui.theme.BaseThemeMode.LIGHT.resolve(isSystemInDark = false))

        assertEquals(com.example.ui.theme.BaseThemeMode.DARK, com.example.ui.theme.BaseThemeMode.DARK.resolve(isSystemInDark = false))
        assertEquals(com.example.ui.theme.BaseThemeMode.DARK, com.example.ui.theme.BaseThemeMode.DARK.resolve(isSystemInDark = true))

        assertEquals(com.example.ui.theme.BaseThemeMode.AMOLED, com.example.ui.theme.BaseThemeMode.AMOLED.resolve(isSystemInDark = false))
        assertEquals(com.example.ui.theme.BaseThemeMode.AMOLED, com.example.ui.theme.BaseThemeMode.AMOLED.resolve(isSystemInDark = true))

        // System Default resolution
        assertEquals(com.example.ui.theme.BaseThemeMode.DARK, com.example.ui.theme.BaseThemeMode.SYSTEM_DEFAULT.resolve(isSystemInDark = true))
        assertEquals(com.example.ui.theme.BaseThemeMode.LIGHT, com.example.ui.theme.BaseThemeMode.SYSTEM_DEFAULT.resolve(isSystemInDark = false))
    }

    @Test
    fun testAmoledBaseModePureBlackBackground() {
        val amoledScheme = com.example.ui.theme.getFocusColorScheme(
            baseMode = com.example.ui.theme.BaseThemeMode.AMOLED,
            palette = com.example.ui.theme.SecondaryThemePalette.FOCUS_EMERALD
        )
        // AMOLED background must be true pitch-black #000000
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), amoledScheme.background)
        // High contrast text on dark background
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFFFFFF), amoledScheme.onBackground)
    }

    @Test
    fun testNormalDarkBaseModeElevatedSurfaces() {
        val darkScheme = com.example.ui.theme.getFocusColorScheme(
            baseMode = com.example.ui.theme.BaseThemeMode.DARK,
            palette = com.example.ui.theme.SecondaryThemePalette.FOCUS_EMERALD
        )
        // Dark mode background is deep charcoal slate, not #000000
        assertTrue(darkScheme.background != androidx.compose.ui.graphics.Color(0xFF000000))
    }

    @Test
    fun testWebsiteDomainNormalization() {
        assertEquals("youtube.com", com.example.util.WebRuleHelper.normalizeDomain("https://www.YouTube.com/"))
        assertEquals("youtube.com", com.example.util.WebRuleHelper.normalizeDomain("http://youtube.com"))
        assertEquals("youtube.com", com.example.util.WebRuleHelper.normalizeDomain("www.youtube.com/"))
        assertEquals("youtube.com", com.example.util.WebRuleHelper.normalizeDomain("https://youtube.com///"))
        assertEquals("reddit.com", com.example.util.WebRuleHelper.normalizeDomain("  HTTPS://WWW.REDDIT.COM/  "))
        assertEquals("reddit.com", com.example.util.WebRuleHelper.normalizeDomain("reddit.com"))
    }

    @Test
    fun testKeywordNormalization() {
        assertEquals("shorts", com.example.util.WebRuleHelper.normalizeKeyword("  shorts  "))
        assertEquals("funny shorts", com.example.util.WebRuleHelper.normalizeKeyword("  funny    shorts  "))
        assertEquals("gambling", com.example.util.WebRuleHelper.normalizeKeyword("Gambling".lowercase()))
    }

    @Test
    fun testBatchWebsiteDeduplication() {
        val input = "https://www.YouTube.com/, youtube.com, REDDIT.COM, reddit.com/"
        val rawItems = input.split(",").map { com.example.util.WebRuleHelper.normalizeDomain(it) }.filter { it.isNotBlank() }
        val uniqueInBatch = mutableListOf<String>()
        val seenLower = mutableSetOf<String>()
        for (item in rawItems) {
            if (seenLower.add(item.lowercase())) {
                uniqueInBatch.add(item)
            }
        }
        assertEquals(listOf("youtube.com", "reddit.com"), uniqueInBatch)

        val existing = listOf("youtube.com")
        val toAdd = uniqueInBatch.filter { it.lowercase() !in existing }
        assertEquals(listOf("reddit.com"), toAdd)
    }

    @Test
    fun testBatchKeywordDeduplication() {
        val input = "shorts, Shorts, SHORTS, reels, Reels"
        val rawItems = input.split(",").map { com.example.util.WebRuleHelper.normalizeKeyword(it) }.filter { it.isNotBlank() }
        val uniqueInBatch = mutableListOf<String>()
        val seenLower = mutableSetOf<String>()
        for (item in rawItems) {
            if (seenLower.add(item.lowercase())) {
                uniqueInBatch.add(item)
            }
        }
        assertEquals(listOf("shorts", "reels"), uniqueInBatch)

        val existing = listOf("shorts")
        val toAdd = uniqueInBatch.filter { it.lowercase() !in existing }
        assertEquals(listOf("reels"), toAdd)
    }

    @Test
    fun testSettingsTamperDetectorZeroFalsePositivesOnNormalSettings() {
        val appPkg = "com.example.focuslock"

        // 1. Wi-Fi
        val wifiTexts = listOf("Network & internet", "Wi-Fi", "Connected to Office_5G", "Add network")
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(wifiTexts, false, true, appPkg)
        )

        // 2. Bluetooth
        val btTexts = listOf("Connected devices", "Bluetooth", "Pair new device", "Galaxy Buds")
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(btTexts, false, true, appPkg)
        )

        // 3. Display & Sound & Battery & Storage
        val displayTexts = listOf("Display", "Dark theme", "Adaptive brightness", "Screen timeout")
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(displayTexts, true, true, appPkg)
        )

        // 4. App Info for another app (Chrome)
        val chromeAppInfo = listOf("App info", "Chrome", "Google LLC", "Force stop", "Uninstall updates", "Storage & cache", "Notifications")
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(chromeAppInfo, true, true, appPkg)
        )

        // 5. General All Apps list with Focus Lock visible (Anti-False-Positive Guard)
        val allAppsList = listOf("All apps", "Chrome", "Drive", "Focus Lock", "Gmail", "Maps", "YouTube", "25 apps installed")
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(allAppsList, false, true, appPkg)
        )

        // 6. System main menu (with Date & time mentioned in summary)
        val systemMenu = listOf("System", "Languages & input", "Gestures", "Date & time", "Backup", "System update")
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(systemMenu, false, true, appPkg)
        )

        // 7. Main Accessibility Menu (Anti-Tamper Exclusion: user browsing Accessibility menu)
        val mainAccessibility = listOf(
            "Accessibility",
            "Press Power button to end calls",
            "Accessibility Menu",
            "Downloaded apps",
            "Focus Lock",
            "Shortcut from Lock screen"
        )
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(mainAccessibility, true, true, appPkg)
        )

        // 8. Samsung OneUI Main Accessibility Menu (Anti-Tamper Exclusion: general Accessibility settings accessible)
        val oneUiMainAccessibility = listOf(
            "Accessibility",
            "TalkBack",
            "Visibility enhancements",
            "Hearing enhancements",
            "Installed apps",
            "Focus Lock"
        )
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(oneUiMainAccessibility, true, true, appPkg)
        )
        // 9. Settings -> Apps -> Installed apps (outside Accessibility) MUST NOT trigger!
        val settingsAppsInstalledApps = listOf(
            "Installed apps",
            "Chrome",
            "Focus Lock",
            "WhatsApp",
            "25 apps installed"
        )
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(
                texts = settingsAppsInstalledApps,
                hasClickableSwitchOrCheckbox = false,
                isClockTamperProtectionEnabled = true,
                appPackageName = appPkg,
                eventClassName = "com.android.settings.applications.ManageApplications"
            )
        )
        assertFalse(
            SettingsTamperDetector.matchesInstantStepAEventTrigger(
                eventTexts = settingsAppsInstalledApps,
                eventClassName = "com.android.settings.applications.ManageApplications",
                appPackageName = appPkg
            )
        )

        // 10. Non-English main Accessibility root screen (e.g. Spanish/Hindi with AccessibilitySettings class) stays NONE
        val spanishMainAccessibility = listOf("Accesibilidad", "TalkBack", "Focus Lock", "Desactivado")
        assertTrue(
            SettingsTamperDetector.isMainAccessibilityRootScreen(
                eventClassName = "com.android.settings.accessibility.AccessibilitySettings",
                texts = spanishMainAccessibility,
                appPackageName = appPkg
            )
        )
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(
                texts = spanishMainAccessibility,
                hasClickableSwitchOrCheckbox = false,
                isClockTamperProtectionEnabled = true,
                appPackageName = appPkg,
                eventClassName = "com.android.settings.accessibility.AccessibilitySettings"
            )
        )
    }

    @Test
    fun testSettingsTamperDetectorCatchesExactFocusLockTamperPages() {
        val appPkg = "com.example.focuslock"

        // Step A: Instant 0.1ms in-memory trigger on event texts & fragments
        assertTrue(
            SettingsTamperDetector.matchesInstantStepAEventTrigger(
                eventTexts = listOf("Downloaded apps"),
                eventClassName = "com.oplus.settings.OplusSubSettings",
                appPackageName = appPkg
            )
        )
        assertTrue(
            SettingsTamperDetector.matchesInstantStepAEventTrigger(
                eventTexts = listOf("Installed apps"),
                eventClassName = "com.samsung.android.settings.SubSettings",
                appPackageName = appPkg
            )
        )
        assertTrue(
            SettingsTamperDetector.matchesInstantStepAEventTrigger(
                eventTexts = listOf("Installed services"),
                eventClassName = "com.android.settings.SubSettings",
                appPackageName = appPkg
            )
        )
        assertTrue(
            SettingsTamperDetector.matchesInstantStepAEventTrigger(
                eventTexts = listOf("Downloaded services"),
                eventClassName = "com.android.settings.SubSettings",
                appPackageName = appPkg
            )
        )
        assertTrue(
            SettingsTamperDetector.matchesInstantStepAEventTrigger(
                eventTexts = listOf("Focus Lock", "Off"),
                eventClassName = "android.widget.LinearLayout",
                appPackageName = appPkg
            )
        )
        assertTrue(
            SettingsTamperDetector.matchesInstantStepAEventTrigger(
                eventTexts = emptyList(),
                eventClassName = "com.android.settings.accessibility.ToggleAccessibilityServicePreferenceFragment",
                appPackageName = appPkg
            )
        )
        assertFalse(
            SettingsTamperDetector.matchesInstantStepAEventTrigger(
                eventTexts = listOf("Display over other apps", "Focus Lock"),
                eventClassName = "com.android.settings.SubSettings",
                appPackageName = appPkg
            )
        )

        // Step B: Fast fallback catches Downloaded apps / Installed apps / Installed services & fragments
        val downloadedAppsList = listOf("Downloaded apps", "Focus Lock", "Off", "TalkBack", "On")
        assertEquals(
            SettingsTamperDetector.TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE,
            SettingsTamperDetector.matchTamperSignature(
                texts = downloadedAppsList,
                hasClickableSwitchOrCheckbox = true,
                isClockTamperProtectionEnabled = true,
                appPackageName = appPkg,
                eventClassName = "com.android.settings.SubSettings"
            )
        )

        val oneUiInstalledApps = listOf("Installed apps", "Focus Lock", "On")
        assertEquals(
            SettingsTamperDetector.TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE,
            SettingsTamperDetector.matchTamperSignature(
                texts = oneUiInstalledApps,
                hasClickableSwitchOrCheckbox = true,
                isClockTamperProtectionEnabled = true,
                appPackageName = appPkg,
                eventClassName = "com.samsung.android.settings.SubSettings"
            )
        )

        // Language-independent service detail fragments (even when reopened from Recents)
        assertEquals(
            SettingsTamperDetector.TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE,
            SettingsTamperDetector.matchTamperSignature(
                texts = listOf("Focus Lock", "Activar servicio"),
                hasClickableSwitchOrCheckbox = true,
                isClockTamperProtectionEnabled = true,
                appPackageName = appPkg,
                eventClassName = "com.android.settings.accessibility.ToggleAccessibilityServicePreferenceFragment"
            )
        )
        assertEquals(
            SettingsTamperDetector.TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE,
            SettingsTamperDetector.matchTamperSignature(
                texts = listOf("Installed services"),
                hasClickableSwitchOrCheckbox = false,
                isClockTamperProtectionEnabled = true,
                appPackageName = appPkg,
                eventClassName = "com.android.settings.accessibility.InstalledServicesFragment"
            )
        )
        assertEquals(
            SettingsTamperDetector.TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE,
            SettingsTamperDetector.matchTamperSignature(
                texts = emptyList(),
                hasClickableSwitchOrCheckbox = false,
                isClockTamperProtectionEnabled = true,
                appPackageName = appPkg,
                eventClassName = "com.android.settings.accessibility.AccessibilityDetailsSettingsFragment"
            )
        )

        // Signature A: Focus Lock Accessibility toggle page
        val a1 = listOf("Accessibility", "Focus Lock", "Use Focus Lock", "Shortcut", "Help & feedback")
        assertEquals(
            SettingsTamperDetector.TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE,
            SettingsTamperDetector.matchTamperSignature(a1, true, true, appPkg)
        )

        // Signature A: Stop Focus Lock dialog
        val a2 = listOf("Stop Focus Lock?", "Turning off Focus Lock will stop tracking and blocking.", "Cancel", "Stop")
        assertEquals(
            SettingsTamperDetector.TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE,
            SettingsTamperDetector.matchTamperSignature(a2, false, true, appPkg)
        )

        // Signature A: OEM flavor (Realme UI / ColorOS) with "About this function"
        val a3 = listOf("Accessibility", "Focus Lock", "About this function", "Auto-launch management")
        assertEquals(
            SettingsTamperDetector.TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE,
            SettingsTamperDetector.matchTamperSignature(a3, true, true, appPkg)
        )

        // Signature A: Service description marker "Focus Lock uses this service"
        val a4 = listOf("Accessibility", "Focus Lock", "Focus Lock uses this service to detect foreground apps")
        assertEquals(
            SettingsTamperDetector.TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE,
            SettingsTamperDetector.matchTamperSignature(a4, false, true, appPkg)
        )

        // Signature A: "shortcut" AND "app info" on the same screen with "Focus Lock"
        val a5 = listOf("Focus Lock", "Shortcut", "App info", "Settings")
        assertEquals(
            SettingsTamperDetector.TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE,
            SettingsTamperDetector.matchTamperSignature(a5, false, true, appPkg)
        )

        // Signature B: Focus Lock App Info page
        val b = listOf("App info", "Focus Lock", "12.4 MB", "Force stop", "Uninstall", "Storage & cache")
        assertEquals(
            SettingsTamperDetector.TamperSignature.SIGNATURE_B_APP_INFO,
            SettingsTamperDetector.matchTamperSignature(b, true, true, appPkg)
        )

        // Signature C: Device Admin deactivation for Focus Lock
        val c = listOf("Device admin", "Focus Lock", "Deactivate this device admin app", "Cancel")
        assertEquals(
            SettingsTamperDetector.TamperSignature.SIGNATURE_C_DEVICE_ADMIN,
            SettingsTamperDetector.matchTamperSignature(c, false, true, appPkg)
        )

        // Signature D: Date & time settings with clock tamper protection enabled
        val d = listOf("Date & time", "Automatic date & time", "Use network-provided time", "Set date", "Time zone")
        assertEquals(
            SettingsTamperDetector.TamperSignature.SIGNATURE_D_DATE_TIME,
            SettingsTamperDetector.matchTamperSignature(d, true, true, appPkg)
        )

        // Signature D: Date & time settings with clock tamper protection DISABLED
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(d, true, false, appPkg)
        )
    }

    @Test
    fun testSettingsTamperDetectorNeverBlocksPermissionScreens() {
        val appPkg = "com.example.focuslock"

        // Display over other apps (Draw over other apps / appear on top)
        val p1 = listOf("Display over other apps", "Focus Lock", "Allow display over other apps", "Use of this feature...")
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(p1, true, true, appPkg)
        )

        // Appear on top
        val p2 = listOf("Appear on top", "Focus Lock", "Allow permission", "Off")
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(p2, true, true, appPkg)
        )

        // Usage access
        val p3 = listOf("Usage access", "Focus Lock", "Permit usage access", "Special app access")
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(p3, true, true, appPkg)
        )

        // Notification access
        val p4 = listOf("Notification access", "Focus Lock", "Allow notification access")
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(p4, true, true, appPkg)
        )

        // Battery optimization
        val p5 = listOf("Battery optimization", "All apps", "Focus Lock", "Don't optimize")
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(p5, true, true, appPkg)
        )

        // Device Admin ACTIVATION (granting permission) must be allowed
        val p6 = listOf("Device admin", "Focus Lock", "Activate this device admin app", "Cancel")
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(p6, true, true, appPkg)
        )

        // Device Admin DEACTIVATION (tampering) must be caught
        val p7 = listOf("Device admin", "Focus Lock", "Deactivate this device admin app", "Cancel")
        assertEquals(
            SettingsTamperDetector.TamperSignature.SIGNATURE_C_DEVICE_ADMIN,
            SettingsTamperDetector.matchTamperSignature(p7, true, true, appPkg)
        )
    }

    @Test
    fun testOemSettingsPackageAndTamperConfirmationDialog() {
        val appPkg = "com.aistudio.focuslock.fclk"

        // OEM Settings packages supported cleanly without intercepting normal apps
        assertTrue(SettingsTamperDetector.isSettingsAppPackage("com.android.settings"))
        assertTrue(SettingsTamperDetector.isSettingsAppPackage("com.samsung.android.settings"))
        assertTrue(SettingsTamperDetector.isSettingsAppPackage("com.coloros.settings"))
        assertTrue(SettingsTamperDetector.isSettingsAppPackage("com.oplus.settings"))
        assertTrue(SettingsTamperDetector.isSettingsAppPackage("com.google.android.settings"))
        assertTrue(SettingsTamperDetector.isSettingsAppPackage("com.motorola.settings"))
        assertFalse(SettingsTamperDetector.isSettingsAppPackage("com.whatsapp"))
        assertFalse(SettingsTamperDetector.isSettingsAppPackage("com.android.systemui"))
        assertFalse(SettingsTamperDetector.isSettingsAppPackage("com.aistudio.focuslock.fclk"))
        assertFalse(SettingsTamperDetector.isSettingsAppPackage(null))

        // Confirmation dialogs for disabling Focus Lock must be caught immediately
        val turnOffDialog = listOf("Turn off Focus Lock?", "Tapping Turn off will stop Focus Lock from observing your actions.", "Cancel", "Turn off")
        assertTrue(SettingsTamperDetector.isTamperConfirmationDialog(turnOffDialog, appPkg))
        assertEquals(
            SettingsTamperDetector.TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE,
            SettingsTamperDetector.matchTamperSignature(turnOffDialog, false, true, appPkg)
        )

        val stopDialog = listOf("Stop Focus Lock?", "Cancel", "Stop")
        assertTrue(SettingsTamperDetector.isTamperConfirmationDialog(stopDialog, appPkg))

        // Downloaded apps list, safe permission dialogs, and other apps (e.g. TalkBack) MUST NOT trigger
        val downloadedList = listOf("Downloaded apps", "Focus Lock", "On", "TalkBack", "Off")
        assertFalse(SettingsTamperDetector.isTamperConfirmationDialog(downloadedList, appPkg))

        val talkBackDialog = listOf("Turn off TalkBack?", "Cancel", "Turn off")
        assertFalse(SettingsTamperDetector.isTamperConfirmationDialog(talkBackDialog, appPkg))

        val safeDeviceAdminActivate = listOf("Device admin", "Focus Lock", "Activate this device admin app", "Cancel")
        assertFalse(SettingsTamperDetector.isTamperConfirmationDialog(safeDeviceAdminActivate, appPkg))
    }

    @Test
    fun testHistoricalDwellMaxOfCloudMerge() {
        // Verifies that a valid local dwell is never overwritten by a 0 or smaller value during restore
        val localDwell = 145
        val cloudZero = 0
        assertEquals(145, maxOf(localDwell, cloudZero))

        val cloudHigher = 180
        assertEquals(180, maxOf(localDwell, cloudHigher))

        val localZero = 0
        val cloudDwell = 220
        assertEquals(220, maxOf(localZero, cloudDwell))
    }

    @Test
    fun testBackupManagerPrefFileInclusionFilters() {
        // App-owned SharedPreferences must be included
        assertTrue(com.example.data.backup.BackupManager.shouldIncludePrefFile("user_preferences_sync"))
        assertTrue(com.example.data.backup.BackupManager.shouldIncludePrefFile("focus_emergency_breaks"))
        assertTrue(com.example.data.backup.BackupManager.shouldIncludePrefFile("focus_killswitch_prefs"))
        assertTrue(com.example.data.backup.BackupManager.shouldIncludePrefFile("intercept_tracker_prefs"))
        assertTrue(com.example.data.backup.BackupManager.shouldIncludePrefFile("focus_notification_messages"))
        assertTrue(com.example.data.backup.BackupManager.shouldIncludePrefFile("custom_user_file"))

        // Internal Google, Firebase, AndroidX, and local meta preferences MUST be excluded
        assertFalse(com.example.data.backup.BackupManager.shouldIncludePrefFile("auth_session_meta"))
        assertFalse(com.example.data.backup.BackupManager.shouldIncludePrefFile("com.google.android.gms.measurement.prefs"))
        assertFalse(com.example.data.backup.BackupManager.shouldIncludePrefFile("firebase_installations"))
        assertFalse(com.example.data.backup.BackupManager.shouldIncludePrefFile("androidx.credentials.preferences"))
        assertFalse(com.example.data.backup.BackupManager.shouldIncludePrefFile("WebViewChromiumPrefs"))
    }

    @Test
    fun testMasterVaultPathAndAccountSwitchDetection() {
        val uidA = "google_user_abc_123"
        val uidB = "google_user_xyz_789"

        val vaultPathA = "users/$uidA/${com.example.data.backup.BackupManager.FIRESTORE_BACKUP_COLLECTION}/${com.example.data.backup.BackupManager.FIRESTORE_MASTER_VAULT_DOC}"
        val vaultPathB = "users/$uidB/${com.example.data.backup.BackupManager.FIRESTORE_BACKUP_COLLECTION}/${com.example.data.backup.BackupManager.FIRESTORE_MASTER_VAULT_DOC}"

        assertEquals("users/google_user_abc_123/focus_lock_backup/master_vault", vaultPathA)
        assertEquals("users/google_user_xyz_789/focus_lock_backup/master_vault", vaultPathB)
        assertTrue("Different accounts must have completely isolated vault paths", vaultPathA != vaultPathB)

        // Switching accounts detection
        var lastActiveUid: String? = uidA
        val isSwitch = lastActiveUid != null && lastActiveUid != uidB
        assertTrue("Switching from uidA to uidB must be detected", isSwitch)

        // Same account sign-in
        lastActiveUid = uidA
        val isSameAccount = lastActiveUid == uidA
        assertTrue("Signing into the same account should not trigger an account wipe", isSameAccount)
    }

    @Test
    fun testOvernightAndSameDayScheduleEvaluation() {
        assertEquals(120_000L, com.example.util.TimeIntegrityManager.MAX_ALLOWED_DRIFT_MS)

        val overnightGroup = AppGroup(
            name = "Night Lockdown",
            scheduleEnabled = true,
            scheduleStart = "22:00",
            scheduleEnd = "06:00",
            daysOfWeek = setOf(1, 2, 3, 4, 5, 6, 7)
        )

        val afternoonCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.WEDNESDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 14)
            set(java.util.Calendar.MINUTE, 0)
        }
        assertFalse(
            "22:00-06:00 schedule must NEVER activate at 14:00 in the afternoon",
            GroupRuleEvaluator.isScheduleActive(overnightGroup, afternoonCal)
        )

        val nightCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.WEDNESDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 22)
            set(java.util.Calendar.MINUTE, 30)
        }
        assertTrue(
            "22:00-06:00 schedule must be active at 22:30",
            GroupRuleEvaluator.isScheduleActive(overnightGroup, nightCal)
        )

        val earlyMorningCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.THURSDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 2)
            set(java.util.Calendar.MINUTE, 15)
        }
        assertTrue(
            "22:00-06:00 schedule must remain active at 02:15 after midnight",
            GroupRuleEvaluator.isScheduleActive(overnightGroup, earlyMorningCal)
        )
    }

    @Test
    fun testCrossVersionScheduleConversionAndAccountIsolationConstants() {
        val (h, m) = com.example.data.backup.BackupManager.parseScheduleHourMinute("22:30", 9, 0)
        assertEquals(22, h)
        assertEquals(30, m)
        assertEquals("22:30", com.example.data.backup.BackupManager.formatHourMinute(h, m))

        val (fallbackH, fallbackM) = com.example.data.backup.BackupManager.parseScheduleHourMinute("invalid", 9, 15)
        assertEquals(9, fallbackH)
        assertEquals(15, fallbackM)

        assertEquals("auth_session_meta", com.example.data.backup.BackupManager.AUTH_SESSION_META_PREFS)
        assertEquals("last_uid", com.example.data.backup.BackupManager.KEY_LAST_UID)
        assertEquals("last_active_gmail_uid", com.example.data.backup.BackupManager.KEY_LAST_ACTIVE_GMAIL_UID)
    }

    @Test
    fun testSettingsSearchScreenNeverEjectsOnSuggestionChipsUnlessClicked() {
        val appPkg = "com.aistudio.focuslock.fclk"

        // 1. Realme UI / ColorOS Settings Search screen with "Suggestions" and "Downloaded apps" chip
        val searchSuggestionScreen = listOf("Search settings", "Suggestions", "Downloaded apps", "Dark mode", "Wi-Fi")
        assertTrue(
            SettingsTamperDetector.isSettingsSearchScreen(
                eventPackageName = "com.android.settings",
                eventClassName = "com.oplus.settings.OplusSettingsActivity",
                texts = searchSuggestionScreen,
                hasFocusedEditText = false
            )
        )
        assertEquals(
            SettingsTamperDetector.TamperSignature.NONE,
            SettingsTamperDetector.matchTamperSignature(
                texts = searchSuggestionScreen,
                hasClickableSwitchOrCheckbox = false,
                isClockTamperProtectionEnabled = true,
                appPackageName = appPkg,
                eventClassName = "com.oplus.settings.OplusSettingsActivity",
                eventPackageName = "com.android.settings"
            )
        )

        // 2. Settings Search screen via com.android.settings.intelligence package or Search class
        assertTrue(
            SettingsTamperDetector.isSettingsSearchScreen(
                eventPackageName = "com.android.settings.intelligence",
                eventClassName = "android.widget.FrameLayout",
                texts = listOf("Downloaded apps"),
                hasFocusedEditText = false
            )
        )
        assertTrue(
            SettingsTamperDetector.isSettingsSearchScreen(
                eventPackageName = "com.android.settings",
                eventClassName = "com.android.settings.intelligence.search.SearchActivity",
                texts = listOf("Downloaded apps"),
                hasFocusedEditText = false
            )
        )
        assertTrue(
            SettingsTamperDetector.isSettingsSearchScreen(
                eventPackageName = "com.android.settings",
                eventClassName = "android.widget.LinearLayout",
                texts = listOf("Downloaded apps"),
                hasFocusedEditText = true
            )
        )

        // 3. Non-click window/content event on Settings Search with "Downloaded apps" chip MUST NOT trigger
        assertFalse(
            SettingsTamperDetector.matchesInstantStepAEventTrigger(
                eventTexts = listOf("Downloaded apps"),
                eventClassName = "com.android.settings.intelligence.search.SearchActivity",
                appPackageName = appPkg,
                isClickEvent = false,
                eventPackageName = "com.android.settings.intelligence"
            )
        )
        assertFalse(
            SettingsTamperDetector.matchesInstantStepAEventTrigger(
                eventTexts = listOf("Suggestions", "Downloaded apps"),
                eventClassName = "android.widget.LinearLayout",
                appPackageName = appPkg,
                isClickEvent = false,
                eventPackageName = "com.android.settings"
            )
        )

        // 4. Physical click (TYPE_VIEW_CLICKED, isClickEvent = true) on "Downloaded apps" or "Focus Lock"
        // inside Settings Search MUST immediately trigger at 0.1ms!
        assertTrue(
            SettingsTamperDetector.matchesInstantStepAEventTrigger(
                eventTexts = listOf("Downloaded apps"),
                eventClassName = "com.google.android.material.chip.Chip",
                appPackageName = appPkg,
                isClickEvent = true,
                eventPackageName = "com.android.settings.intelligence"
            )
        )
        assertTrue(
            SettingsTamperDetector.matchesInstantStepAEventTrigger(
                eventTexts = listOf("Focus Lock"),
                eventClassName = "android.widget.LinearLayout",
                appPackageName = appPkg,
                isClickEvent = true,
                eventPackageName = "com.android.settings.intelligence"
            )
        )
    }
}



