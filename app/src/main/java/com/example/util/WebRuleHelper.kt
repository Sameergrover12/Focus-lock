package com.example.util

import java.util.Locale

object WebRuleHelper {

    /**
     * Normalizes a website or domain string:
     * - Trims whitespace and converts to lowercase.
     * - Strips protocol prefixes (http://, https://), www. prefixes, and any trailing slashes (/).
     * e.g., "https://www.YouTube.com/" and "youtube.com" -> "youtube.com"
     */
    fun normalizeDomain(input: String): String {
        var s = input.trim().lowercase(Locale.ROOT)
        if (s.startsWith("https://")) {
            s = s.removePrefix("https://")
        } else if (s.startsWith("http://")) {
            s = s.removePrefix("http://")
        }
        if (s.startsWith("www.")) {
            s = s.removePrefix("www.")
        }
        s = s.trimEnd('/')
        return s.trim()
    }

    /**
     * Normalizes a keyword string:
     * - Trims leading and trailing spaces.
     * - Collapses multiple internal spaces into a single space.
     * e.g., "  shorts   video  " -> "shorts video"
     */
    fun normalizeKeyword(input: String): String {
        return input.trim().replace(Regex("\\s+"), " ")
    }
}
