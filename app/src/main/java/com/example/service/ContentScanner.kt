package com.example.service

import android.os.Build
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
     * Checks if a single URL or text string matches any blocked website rule.
     */
    fun matchBlockedWebsite(urlOrText: String, blockedWebsites: List<BlockedWebsite>): BlockedWebsite? {
        return matchBlockedWebsiteInTexts(listOf(urlOrText), blockedWebsites)
    }

    /**
     * Scans visible text strings across the screen for any blocked website or domain substring.
     * Browser-agnostic: works across all browsers, in-app webviews, and any apps showing URLs.
     */
    fun matchBlockedWebsiteInTexts(screenTexts: List<String>, blockedWebsites: List<BlockedWebsite>): BlockedWebsite? {
        if (screenTexts.isEmpty() || blockedWebsites.isEmpty()) return null

        val normalizedRules = blockedWebsites.mapNotNull { rule ->
            val norm = normalizeUrlOrDomain(rule.domainOrUrl)
            if (norm.isNotBlank()) rule to norm else null
        }
        if (normalizedRules.isEmpty()) return null

        for (text in screenTexts) {
            val lowerText = text.lowercase(Locale.ROOT)
            for ((rule, norm) in normalizedRules) {
                if (lowerText.contains(norm)) {
                    return rule
                }
            }
        }

        // Also check joined screen text in case domain was broken into separate nodes/spans
        val joined = screenTexts.joinToString(" ").lowercase(Locale.ROOT)
        val joinedNoSpace = screenTexts.joinToString("").lowercase(Locale.ROOT)
        for ((rule, norm) in normalizedRules) {
            if (joined.contains(norm) || joinedNoSpace.contains(norm)) {
                return rule
            }
        }

        return null
    }

    /**
     * Recursively walks the accessibility tree and collects all text nodes.
     * Supports deep Compose and RecyclerView hierarchies up to maxDepth (default 35).
     */
    fun extractAllScreenText(rootNode: AccessibilityNodeInfo?, maxDepth: Int = 35): List<String> {
        if (rootNode == null) return emptyList()
        val textList = mutableListOf<String>()
        var visitedNodes = 0
        collectTextRecursive(rootNode, textList, depth = 0, maxDepth = maxDepth, countSupplier = { visitedNodes++ }, maxNodes = 1000)
        return textList
    }

    private fun collectTextRecursive(
        node: AccessibilityNodeInfo?,
        result: MutableList<String>,
        depth: Int,
        maxDepth: Int,
        countSupplier: () -> Int,
        maxNodes: Int
    ) {
        if (node == null || depth > maxDepth) return
        if (countSupplier() > maxNodes) return

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.toString()?.trim()
        } else null
        val tooltip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            node.tooltipText?.toString()?.trim()
        } else null
        val paneTitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            node.paneTitle?.toString()?.trim()
        } else null
        val stateDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            node.stateDescription?.toString()?.trim()
        } else null
        val errorText = node.error?.toString()?.trim()

        if (!text.isNullOrEmpty()) {
            result.add(text)
        }
        if (!desc.isNullOrEmpty() && desc != text) {
            result.add(desc)
        }
        if (!hint.isNullOrEmpty() && hint != text && hint != desc) {
            result.add(hint)
        }
        if (!tooltip.isNullOrEmpty() && tooltip != text && tooltip != desc) {
            result.add(tooltip)
        }
        if (!paneTitle.isNullOrEmpty() && paneTitle != text) {
            result.add(paneTitle)
        }
        if (!stateDesc.isNullOrEmpty() && stateDesc != text && stateDesc != desc) {
            result.add(stateDesc)
        }
        if (!errorText.isNullOrEmpty()) {
            result.add(errorText)
        }

        // When both text and contentDescription exist on the same node, also add their concatenation
        // to handle feed items where post semantics or titles are partitioned
        if (!text.isNullOrEmpty() && !desc.isNullOrEmpty() && text != desc) {
            result.add("$text $desc")
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectTextRecursive(child, result, depth + 1, maxDepth, countSupplier, maxNodes)
                try {
                    child.recycle()
                } catch (e: Exception) {
                    // Ignored on newer Android runtimes where recycle is safe/no-op
                }
            }
        }
    }

    /**
     * Checks screen text against list of blocked keywords.
     * Evaluates individual strings and joined feed texts to capture split spans.
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

        // Also check joined feed text to detect keywords split across adjacent inline spans or Compose text nodes
        val combinedText = screenTexts.joinToString(" ")
        for (keywordItem in blockedKeywords) {
            val targetKeyword = keywordItem.keyword.trim()
            if (targetKeyword.isEmpty()) continue

            val isMatch = if (keywordItem.caseSensitive) {
                combinedText.contains(targetKeyword)
            } else {
                combinedText.contains(targetKeyword, ignoreCase = true)
            }

            if (isMatch) {
                return keywordItem
            }
        }

        return null
    }
}
