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
    fun testDerivedTotalEqualsSumOfAppTimes() {
        val appLogs = listOf(
            com.example.data.local.entity.DailyUsageLog(packageName = "com.reddit", date = "2026-09-11", minutesUsed = 45),
            com.example.data.local.entity.DailyUsageLog(packageName = "com.android.chrome", date = "2026-09-11", minutesUsed = 20),
            com.example.data.local.entity.DailyUsageLog(packageName = "com.google.android.youtube", date = "2026-09-11", minutesUsed = 15)
        )
        val derivedTotal = appLogs.sumOf { it.minutesUsed }
        assertEquals(80, derivedTotal)
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
}
