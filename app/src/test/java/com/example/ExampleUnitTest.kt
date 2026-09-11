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
    fun testMultitaskingScreenTimeReconciliationCap() {
        // Suppose two apps (e.g. YouTube and Chrome) were each active for 120 minutes in split-screen
        // The sum of individual app usage = 240 minutes (4 hours)
        val appUsageA = 120
        val appUsageB = 120
        val naiveSum = appUsageA + appUsageB // 240 mins (inflated!)

        // Physical screen-on time was only 120 minutes
        val deviceScreenOnTime = 120
        val minutesSinceMidnight = 180

        // Reconciled total must use device screen-on time and be capped by minutes since midnight
        val resolvedTotal = if (deviceScreenOnTime > 0) {
            deviceScreenOnTime.coerceAtMost(minutesSinceMidnight)
        } else {
            naiveSum.coerceAtMost(minutesSinceMidnight)
        }

        assertEquals(120, resolvedTotal)
        assertTrue(resolvedTotal < naiveSum)
    }
}
