package com.example.util

/**
 * Surgical zero-false-positive detector for Focus Lock anti-tamper enforcement in Settings.
 *
 * Ensures normal device functions (Wi-Fi, Bluetooth, Display, Sound, Battery, Storage,
 * scrolling the All Apps list, and App Info/Accessibility for OTHER apps) are completely
 * unhindered, while instantly catching exact Focus Lock tamper pages.
 */
object SettingsTamperDetector {

    enum class TamperSignature {
        NONE,
        SIGNATURE_A_ACCESSIBILITY_TOGGLE,
        SIGNATURE_B_APP_INFO,
        SIGNATURE_C_DEVICE_ADMIN,
        SIGNATURE_D_DATE_TIME
    }

    /**
     * Evaluates collected screen text and view state against exact tamper signatures.
     *
     * @param texts All visible text and content descriptions collected from the window.
     * @param hasClickableSwitchOrCheckbox True if a clickable Switch or CheckBox is present.
     * @param isClockTamperProtectionEnabled True if clock tamper protection is currently on.
     * @param appPackageName The package name of Focus Lock (e.g. context.packageName).
     * @return TamperSignature matching one of Signatures A, B, C, D, or NONE.
     */
    fun matchTamperSignature(
        texts: List<String>,
        hasClickableSwitchOrCheckbox: Boolean,
        isClockTamperProtectionEnabled: Boolean,
        appPackageName: String
    ): TamperSignature {
        if (texts.isEmpty()) return TamperSignature.NONE

        val textsLower = texts.map { it.lowercase().trim() }
        val targetPkgLower = appPackageName.lowercase().trim()

        // -------------------------------------------------------------------------
        // Signature D — System Date & Time Settings (ONLY when Clock Tamper Protection is enabled):
        // - The screen contains "Date & time" (or "Set time") AND ("Automatic date" or "Time zone" or "Set date").
        // -------------------------------------------------------------------------
        if (isClockTamperProtectionEnabled) {
            val hasDateTimeKeyword = textsLower.any {
                it.contains("date & time") ||
                it.contains("date and time") ||
                it.contains("set time")
            }
            val hasDateTimeSettingMarker = textsLower.any {
                it.contains("automatic date") ||
                it.contains("time zone") ||
                it.contains("set date") ||
                it.contains("network-provided time")
            }
            if (hasDateTimeKeyword && hasDateTimeSettingMarker) {
                return TamperSignature.SIGNATURE_D_DATE_TIME
            }
        }

        // Check if Focus Lock is specifically referenced on this screen
        val hasFocusLockName = textsLower.any {
            it.contains("focus lock") || (targetPkgLower.isNotEmpty() && it.contains(targetPkgLower))
        }

        // Signatures A, B, and C strictly require "Focus Lock" (or our exact packageName) on the screen
        if (!hasFocusLockName) {
            return TamperSignature.NONE
        }

        // -------------------------------------------------------------------------
        // Anti-False-Positive Guard: Ignore General App Lists
        // If the user is just scrolling through the general "App Management / All Apps" list
        // (where many unrelated app rows are visible and neither "Use Focus Lock" nor
        // "Use service" nor "Stop Focus Lock" is present), do NOT trigger!
        // -------------------------------------------------------------------------
        val specificToggleIndicators = listOf(
            "use focus lock",
            "use service",
            "focus lock shortcut",
            "stop focus lock",
            "turn off focus lock",
            "allow focus lock"
        )
        val hasSpecificToggleIndicator = textsLower.any { text ->
            specificToggleIndicators.any { ind -> text.contains(ind) }
        }

        val isGeneralAppList = textsLower.any {
            it == "all apps" ||
            it == "app management" ||
            it == "manage apps" ||
            it == "installed apps" ||
            it == "see all apps" ||
            it == "default apps" ||
            it.contains("apps installed") ||
            it.contains("apps found")
        }

        // -------------------------------------------------------------------------
        // Signature A — Focus Lock's Specific Accessibility Toggle Page or Turn-Off Dialog:
        // - The screen contains "Focus Lock" (case-insensitive)
        // - AND the screen contains at least ONE specific Accessibility toggle-page indicator:
        //   * "Use Focus Lock"
        //   * "Use service"
        //   * "Focus Lock shortcut"
        //   * "Stop Focus Lock"
        //   * "Turn off Focus Lock"
        //   * "Allow Focus Lock"
        //   * OR ("Accessibility" AND ("On" or "Off" or a clickable android.widget.Switch / CheckBox on the same screen))
        // -------------------------------------------------------------------------
        if (hasSpecificToggleIndicator) {
            return TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE
        }

        if (!isGeneralAppList) {
            val hasAccessibilityWord = textsLower.any { it.contains("accessibility") }
            val hasOnOffWord = textsLower.any {
                it == "on" || it == "off" || it.contains(" turned on") || it.contains(" turned off")
            }
            if (hasAccessibilityWord && (hasOnOffWord || hasClickableSwitchOrCheckbox)) {
                return TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE
            }
        }

        // -------------------------------------------------------------------------
        // Signature B — Focus Lock's Specific "App Info" (Force Stop / Uninstall) Page:
        // - The screen contains "Focus Lock" (or our exact packageName)
        // - AND the screen contains "Force stop" (or "Force Stop") AND ("Uninstall" or "App info" or "Storage").
        // -------------------------------------------------------------------------
        val hasForceStop = textsLower.any { it.contains("force stop") }
        val hasAppInfoMarker = textsLower.any {
            it.contains("uninstall") || it.contains("app info") || it.contains("storage")
        }
        if (hasForceStop && hasAppInfoMarker) {
            return TamperSignature.SIGNATURE_B_APP_INFO
        }

        // -------------------------------------------------------------------------
        // Signature C — Device Admin Deactivation Page for Focus Lock:
        // - The screen contains "Focus Lock" AND ("Deactivate this device admin app" or "Device admin").
        // -------------------------------------------------------------------------
        val hasDeviceAdminMarker = textsLower.any {
            it.contains("deactivate this device admin app") ||
            it.contains("deactivate this device admin") ||
            it.contains("device admin")
        }
        if (hasDeviceAdminMarker) {
            return TamperSignature.SIGNATURE_C_DEVICE_ADMIN
        }

        return TamperSignature.NONE
    }
}
