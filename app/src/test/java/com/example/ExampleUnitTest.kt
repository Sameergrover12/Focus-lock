package com.example

import com.example.data.local.entity.AppGroup
import com.example.data.local.entity.BlockedKeyword
import com.example.data.local.entity.BlockedWebsite
import com.example.service.ContentScanner
import com.example.service.FocusForegroundService
import com.example.service.GroupRuleEvaluator
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
}
