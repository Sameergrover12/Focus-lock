package com.example.service

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.local.entity.BlockedKeyword
import com.example.data.local.entity.BlockedWebsite
import java.util.Locale

object ContentScanner {

    enum class TextSource(val description: String) {
        VISIBLE_TEXT("Visible Text"),
        CONTENT_DESCRIPTION_USER_AUTHORED("Content Description (User Authored)"),
        CONTENT_DESCRIPTION_LABEL("Content Description (Alt-Text Label)"),
        HINT_OR_TOOLTIP("Hint / Tooltip"),
        URL_BAR("Browser URL Bar"),
        FEED_JOINED("Joined Feed Text")
    }

    data class ScannedNodeText(
        val text: String,
        val source: TextSource,
        val isUserAuthored: Boolean,
        val viewId: String? = null
    )

    data class KeywordMatchResult(
        val rule: BlockedKeyword,
        val matchedSnippet: String,
        val sourceText: String,
        val source: TextSource
    )

    data class WebsiteMatchResult(
        val rule: BlockedWebsite,
        val matchedSnippet: String,
        val sourceText: String,
        val source: TextSource
    )

    // Known browser address bar resource IDs
    val BROWSER_URL_BAR_IDS = mapOf(
        "com.android.chrome" to listOf("com.android.chrome:id/url_bar"),
        "org.mozilla.firefox" to listOf("org.mozilla.firefox:id/toolbar_title", "org.mozilla.firefox:id/url_bar_title"),
        "com.sec.android.app.sbrowser" to listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text"),
        "com.microsoft.emmx" to listOf("com.microsoft.emmx:id/url_bar"),
        "com.brave.browser" to listOf("com.brave.browser:id/url_bar"),
        "com.opera.browser" to listOf("com.opera.browser:id/url_field")
    )

    private val KNOWN_BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.sec.android.app.sbrowser",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.opera.browser",
        "com.duckduckgo.mobile.android",
        "com.kiwibrowser.browser",
        "com.vivaldi.browser"
    )

    fun isKnownBrowser(packageName: String): Boolean {
        return KNOWN_BROWSER_PACKAGES.contains(packageName)
    }

    /**
     * Extracts text from the browser's address bar if available.
     */
    fun extractBrowserUrl(rootNode: AccessibilityNodeInfo?, packageName: String): String? {
        if (rootNode == null) return null

        val candidateIds = BROWSER_URL_BAR_IDS[packageName]
        if (candidateIds != null) {
            for (id in candidateIds) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        val text = node.text?.toString()
                        if (!text.isNullOrBlank()) {
                            return text
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Normalizes a URL or domain by stripping protocol, www, trailing slash, and converting to lowercase.
     */
    fun normalizeUrlOrDomain(input: String): String {
        return input.trim().lowercase(Locale.ROOT)
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/')
    }

    /**
     * Fix 2.1: Builds a word-boundary-aware regex for a given pattern.
     * e.g. "hard" -> \bhard\b (case-insensitive unless specified).
     * Prevents false positives like "hardware" or "diehard".
     */
    fun buildWordBoundaryRegex(pattern: String, caseSensitive: Boolean = false): Regex {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) return Regex("$^")
        val prefix = if (trimmed.first().isLetterOrDigit()) "\\b" else ""
        val suffix = if (trimmed.last().isLetterOrDigit()) "\\b" else ""
        val escaped = Regex.escape(trimmed)
        val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return Regex("$prefix$escaped$suffix", options)
    }

    /**
     * Fix 2.3: Reconsider contentDescription scanning.
     * Evaluates whether a string appears to be user-authored text (long, sentence-like)
     * rather than short auto-generated accessibility alt-text labels (e.g. "image", "upvote icon").
     */
    fun isUserAuthoredText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 20) return false
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.size >= 4
    }

    /**
     * Scans visible text strings across the screen for any blocked website or domain.
     * Uses word-boundary matching so "reddit.com" won't match "notreddit.com" or "reddit.commercial".
     */
    fun findWebsiteMatch(
        items: List<ScannedNodeText>,
        blockedWebsites: List<BlockedWebsite>
    ): WebsiteMatchResult? {
        if (items.isEmpty() || blockedWebsites.isEmpty()) return null

        val compiledRules = blockedWebsites.mapNotNull { rule ->
            val norm = normalizeUrlOrDomain(rule.domainOrUrl)
            if (norm.isNotEmpty()) {
                val regex = buildWordBoundaryRegex(norm, caseSensitive = false)
                rule to regex
            } else null
        }
        if (compiledRules.isEmpty()) return null

        // 1. Check individual items
        for (item in items) {
            for ((rule, regex) in compiledRules) {
                val match = regex.find(item.text)
                if (match != null) {
                    return WebsiteMatchResult(
                        rule = rule,
                        matchedSnippet = match.value,
                        sourceText = item.text,
                        source = item.source
                    )
                }
            }
        }

        // 2. Check joined text for domain parts split across adjacent nodes (e.g. "reddit", ".com")
        val joined = items.joinToString(" ") { it.text }
        val joinedCollapsedDots = joined.replace(Regex("\\s*\\.\\s*"), ".").replace(Regex("\\s*/\\s*"), "/")
        for ((rule, regex) in compiledRules) {
            val match = regex.find(joined) ?: regex.find(joinedCollapsedDots)
            if (match != null) {
                return WebsiteMatchResult(
                    rule = rule,
                    matchedSnippet = match.value,
                    sourceText = joined.take(150),
                    source = TextSource.FEED_JOINED
                )
            }
        }

        return null
    }

    /**
     * Scans screen text against list of blocked keywords.
     * Enforces:
     * - Word-boundary matching (\bkeyword\b)
     * - Only matches against contentDescription if user-authored
     */
    fun findKeywordMatch(
        items: List<ScannedNodeText>,
        blockedKeywords: List<BlockedKeyword>
    ): KeywordMatchResult? {
        if (items.isEmpty() || blockedKeywords.isEmpty()) return null

        val compiledRules = blockedKeywords.mapNotNull { rule ->
            val pattern = rule.keyword.trim()
            if (pattern.isNotEmpty()) {
                val regex = buildWordBoundaryRegex(pattern, rule.caseSensitive)
                rule to regex
            } else null
        }
        if (compiledRules.isEmpty()) return null

        // 1. Check individual items
        for (item in items) {
            // Fix 2.3: Skip short automated labels / alt-text descriptions
            if (item.source == TextSource.CONTENT_DESCRIPTION_LABEL) {
                continue
            }

            for ((rule, regex) in compiledRules) {
                val match = regex.find(item.text)
                if (match != null) {
                    return KeywordMatchResult(
                        rule = rule,
                        matchedSnippet = match.value,
                        sourceText = item.text,
                        source = item.source
                    )
                }
            }
        }

        // 2. Check joined feed text across adjacent visible text nodes
        val visibleItems = items.filter { it.source == TextSource.VISIBLE_TEXT }
        if (visibleItems.size > 1) {
            val combinedText = visibleItems.joinToString(" ") { it.text }
            for ((rule, regex) in compiledRules) {
                val match = regex.find(combinedText)
                if (match != null) {
                    return KeywordMatchResult(
                        rule = rule,
                        matchedSnippet = match.value,
                        sourceText = combinedText.take(150),
                        source = TextSource.FEED_JOINED
                    )
                }
            }
        }

        return null
    }

    // Backwards-compatible methods for callers & tests
    fun matchBlockedWebsite(urlOrText: String, blockedWebsites: List<BlockedWebsite>): BlockedWebsite? {
        return matchBlockedWebsiteInTexts(listOf(urlOrText), blockedWebsites)
    }

    fun matchBlockedWebsiteInTexts(screenTexts: List<String>, blockedWebsites: List<BlockedWebsite>): BlockedWebsite? {
        val items = screenTexts.map {
            ScannedNodeText(text = it, source = TextSource.VISIBLE_TEXT, isUserAuthored = true)
        }
        return findWebsiteMatch(items, blockedWebsites)?.rule
    }

    fun matchBlockedKeyword(screenTexts: List<String>, blockedKeywords: List<BlockedKeyword>): BlockedKeyword? {
        val items = screenTexts.map {
            ScannedNodeText(text = it, source = TextSource.VISIBLE_TEXT, isUserAuthored = true)
        }
        return findKeywordMatch(items, blockedKeywords)?.rule
    }

    /**
     * Fix 2.2: Deeply scans the active accessibility window:
     * - Filters out off-screen nodes using screenBounds (getBoundsInScreen)
     * - Filters out nodes where isVisibleToUser is false
     * - Classifies text sources (Visible Text vs User-Authored vs Label/Alt-Text)
     */
    fun extractScreenNodes(
        rootNode: AccessibilityNodeInfo?,
        screenBounds: Rect? = null,
        maxDepth: Int = 35
    ): List<ScannedNodeText> {
        if (rootNode == null) return emptyList()
        val result = mutableListOf<ScannedNodeText>()
        var visitedNodes = 0
        collectNodesRecursive(
            node = rootNode,
            result = result,
            screenBounds = screenBounds,
            depth = 0,
            maxDepth = maxDepth,
            countSupplier = { visitedNodes++ },
            maxNodes = 1000
        )
        return result
    }

    fun extractAllScreenText(
        rootNode: AccessibilityNodeInfo?,
        screenBounds: Rect? = null,
        maxDepth: Int = 35
    ): List<String> {
        return extractScreenNodes(rootNode, screenBounds, maxDepth).map { it.text }
    }

    private fun collectNodesRecursive(
        node: AccessibilityNodeInfo?,
        result: MutableList<ScannedNodeText>,
        screenBounds: Rect?,
        depth: Int,
        maxDepth: Int,
        countSupplier: () -> Int,
        maxNodes: Int
    ) {
        if (node == null || depth > maxDepth) return
        if (countSupplier() > maxNodes) return

        // 1. Fix 2.2: Viewport bounds check. Skip nodes that are outside the visible screen.
        if (screenBounds != null) {
            val nodeBounds = Rect()
            node.getBoundsInScreen(nodeBounds)
            if (!nodeBounds.isEmpty && !Rect.intersects(screenBounds, nodeBounds)) {
                return // Skip off-screen views and their pre-rendered children
            }
        }

        // 2. Visibility check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (!node.isVisibleToUser && node.childCount == 0) {
                return
            }
        }

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) node.hintText?.toString()?.trim() else null
        val tooltip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) node.tooltipText?.toString()?.trim() else null
        val paneTitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) node.paneTitle?.toString()?.trim() else null
        val viewId = node.viewIdResourceName

        if (!text.isNullOrEmpty()) {
            result.add(
                ScannedNodeText(
                    text = text,
                    source = TextSource.VISIBLE_TEXT,
                    isUserAuthored = true,
                    viewId = viewId
                )
            )
        }

        if (!desc.isNullOrEmpty() && desc != text) {
            val isUser = isUserAuthoredText(desc)
            result.add(
                ScannedNodeText(
                    text = desc,
                    source = if (isUser) TextSource.CONTENT_DESCRIPTION_USER_AUTHORED else TextSource.CONTENT_DESCRIPTION_LABEL,
                    isUserAuthored = isUser,
                    viewId = viewId
                )
            )
        }

        if (!hint.isNullOrEmpty() && hint != text && hint != desc) {
            result.add(
                ScannedNodeText(
                    text = hint,
                    source = TextSource.HINT_OR_TOOLTIP,
                    isUserAuthored = false,
                    viewId = viewId
                )
            )
        }

        if (!tooltip.isNullOrEmpty() && tooltip != text && tooltip != desc) {
            result.add(
                ScannedNodeText(
                    text = tooltip,
                    source = TextSource.HINT_OR_TOOLTIP,
                    isUserAuthored = false,
                    viewId = viewId
                )
            )
        }

        if (!paneTitle.isNullOrEmpty() && paneTitle != text) {
            result.add(
                ScannedNodeText(
                    text = paneTitle,
                    source = TextSource.VISIBLE_TEXT,
                    isUserAuthored = true,
                    viewId = viewId
                )
            )
        }

        // When both text and user-authored contentDescription exist on the same node, add their combination
        if (!text.isNullOrEmpty() && !desc.isNullOrEmpty() && text != desc && isUserAuthoredText(desc)) {
            result.add(
                ScannedNodeText(
                    text = "$text $desc",
                    source = TextSource.VISIBLE_TEXT,
                    isUserAuthored = true,
                    viewId = viewId
                )
            )
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectNodesRecursive(child, result, screenBounds, depth + 1, maxDepth, countSupplier, maxNodes)
                try {
                    child.recycle()
                } catch (e: Exception) {
                    // Ignored
                }
            }
        }
    }
}
