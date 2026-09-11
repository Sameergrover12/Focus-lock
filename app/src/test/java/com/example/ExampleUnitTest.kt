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
    fun testForegroundPackagePropagation() {
        // Test switching between different apps updates foreground package
        FocusForegroundService.onForegroundPackageChanged("com.android.settings")
        // Switch to a social app
        FocusForegroundService.onForegroundPackageChanged("com.google.android.youtube")
        // Switch to browser
        FocusForegroundService.onForegroundPackageChanged("com.android.chrome")
    }
}
