package com.example.service

import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.local.entity.BlockedKeyword
import com.example.data.local.entity.BlockedWebsite
import java.util.Locale

object ContentScanner {

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
     * Checks if a URL or text string matches any blocked website rule.
     */
    fun matchBlockedWebsite(urlOrText: String, blockedWebsites: List<BlockedWebsite>): BlockedWebsite? {
        if (urlOrText.isBlank()) return null
        val cleanInput = urlOrText.lowercase(Locale.ROOT)
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")

        return blockedWebsites.firstOrNull { blocked ->
            val cleanRule = blocked.domainOrUrl.lowercase(Locale.ROOT)
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("www.")
                .trim()
            if (cleanRule.isNotEmpty()) {
                cleanInput.contains(cleanRule) || cleanRule.contains(cleanInput)
            } else {
                false
            }
        }
    }

    /**
     * Recursively walks the accessibility tree and collects all text nodes.
     * Limits recursion depth to prevent performance overhead.
     */
    fun extractAllScreenText(rootNode: AccessibilityNodeInfo?, maxDepth: Int = 12): List<String> {
        if (rootNode == null) return emptyList()
        val textList = mutableListOf<String>()
        collectTextRecursive(rootNode, textList, depth = 0, maxDepth = maxDepth)
        return textList
    }

    private fun collectTextRecursive(
        node: AccessibilityNodeInfo?,
        result: MutableList<String>,
        depth: Int,
        maxDepth: Int
    ) {
        if (node == null || depth > maxDepth) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            result.add(text)
        }
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty() && desc != text) {
            result.add(desc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectTextRecursive(child, result, depth + 1, maxDepth)
                child.recycle()
            }
        }
    }

    /**
     * Checks screen text against list of blocked keywords.
     */
    fun matchBlockedKeyword(screenTexts: List<String>, blockedKeywords: List<BlockedKeyword>): BlockedKeyword? {
        if (screenTexts.isEmpty() || blockedKeywords.isEmpty()) return null

        for (text in screenTexts) {
            for (keywordItem in blockedKeywords) {
                val targetKeyword = keywordItem.keyword.trim()
                if (targetKeyword.isEmpty()) continue

                val isMatch = if (keywordItem.caseSensitive) {
                    text.contains(targetKeyword)
                } else {
                    text.contains(targetKeyword, ignoreCase = true)
                }

                if (isMatch) {
                    return keywordItem
                }
            }
        }
        return null
    }
}
