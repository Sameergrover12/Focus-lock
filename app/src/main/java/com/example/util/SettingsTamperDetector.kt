package com.example.util

/**
 * Surgical zero-false-positive detector for Focus Lock anti-tamper enforcement in Settings.
 *
 * Ensures normal device functions (Wi-Fi, Bluetooth, Display, Sound, Battery, Storage,
 * scrolling the All Apps list, and App Info/Accessibility for OTHER apps) are completely
 * unhindered, while instantly catching exact Focus Lock tamper pages across Stock Android,
 * Samsung OneUI, Realme UI, ColorOS, and other OEM flavors.
 */
object SettingsTamperDetector {

    enum class TamperSignature {
        NONE,
        SIGNATURE_A_ACCESSIBILITY_TOGGLE,
        SIGNATURE_B_APP_INFO,
        SIGNATURE_C_DEVICE_ADMIN,
        SIGNATURE_D_DATE_TIME
    }

    val SAFE_PERMISSION_WHITELIST = listOf(
        "display over other apps",
        "appear on top",
        "allow display over other apps",
        "usage access",
        "permit usage access",
        "usage data access",
        "special app access",
        "notification access",
        "alarms & reminders",
        "alarms and reminders",
        "battery optimization",
        "ignore battery optimizations",
        "background usage",
        "auto-launch permission",
        "background auto-launch",
        "activate this device admin app"
    )

    fun isSafePermissionScreen(textsLower: List<String>): Boolean {
        // If this screen contains Accessibility toggle markers, it's not a safe permission screen
        val hasAccessibilityMarker = textsLower.any {
            it.contains("accessibility") || it.contains("use service") || it.contains("downloaded apps") || it.contains("installed apps")
        }
        return textsLower.any { text ->
            SAFE_PERMISSION_WHITELIST.any { safe ->
                if (safe == "activate this device admin app") {
                    text.contains(safe) && !textsLower.any { it.contains("deactivate") }
                } else if (safe == "auto-launch permission" || safe == "background auto-launch") {
                    text.contains(safe)
                } else {
                    text.contains(safe)
                }
            }
        }
    }

    /**
     * Evaluates collected screen text and view state against exact tamper signatures.
     *
     * @param texts All visible text and content descriptions collected from the window.
     * @param hasClickableSwitchOrCheckbox True if a clickable Switch or CheckBox is present.
     * @param isClockTamperProtectionEnabled True if clock tamper protection / strict mode is active.
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
        // Safe Whitelist: NEVER block or trigger anti-tamper on permission grant screens!
        // Allows user to grant Usage Access, Display over other apps, Notification Access,
        // Battery Optimization, or Activate Device Admin without being kicked out.
        // -------------------------------------------------------------------------
        if (isSafePermissionScreen(textsLower)) {
            return TamperSignature.NONE
        }

        // -------------------------------------------------------------------------
        // Signature 4 (D) — System Date & Time Settings
        // Trigger when isClockTamperProtectionEnabled / strict mode / killswitch is active.
        // Screen contains "date & time" OR "set time" OR "automatic date & time".
        // (Guards against the parent "System" settings menu which lists "Date & time" as a sub-item)
        // -------------------------------------------------------------------------
        if (isClockTamperProtectionEnabled) {
            val hasSetTimeOrAuto = textsLower.any {
                it.contains("automatic date & time") ||
                it.contains("automatic date and time") ||
                it.contains("set time")
            }
            val hasDateTimeKeyword = textsLower.any {
                it.contains("date & time") ||
                it.contains("date and time")
            }
            val hasDateTimeSettingMarker = textsLower.any {
                it.contains("automatic date") ||
                it.contains("time zone") ||
                it.contains("set date") ||
                it.contains("network-provided time")
            }
            if (hasSetTimeOrAuto || (hasDateTimeKeyword && hasDateTimeSettingMarker)) {
                return TamperSignature.SIGNATURE_D_DATE_TIME
            }
        }

        // Check if Focus Lock is specifically referenced on this screen
        val hasFocusLockName = textsLower.any {
            it.contains("focus lock") || (targetPkgLower.isNotEmpty() && it.contains(targetPkgLower))
        }

        // Signatures 1, 2, and 3 strictly require "Focus Lock" (or our exact packageName) on the screen
        if (!hasFocusLockName) {
            return TamperSignature.NONE
        }

        // -------------------------------------------------------------------------
        // Signature 2 (B) — Focus Lock App Info Page (Force Stop / Uninstall)
        // hasFocusLock AND ("force stop" OR "uninstall")
        // -------------------------------------------------------------------------
        val hasForceStopOrUninstall = textsLower.any {
            it.contains("force stop") || it.contains("uninstall")
        }
        val isAppInfoPage = textsLower.any {
            it.contains("app info") || it.contains("storage") || it.contains("notifications") ||
            it.contains("disable") || it.contains("open")
        }
        if (hasForceStopOrUninstall && (isAppInfoPage || textsLower.any { it.contains("force stop") })) {
            return TamperSignature.SIGNATURE_B_APP_INFO
        }

        // -------------------------------------------------------------------------
        // Signature 3 (C) — Device Admin Deactivation Page for Focus Lock
        // hasFocusLock AND ("deactivate" OR "device admin")
        // -------------------------------------------------------------------------
        val hasDeviceAdminMarker = textsLower.any {
            it.contains("deactivate") || it.contains("device admin")
        }
        if (hasDeviceAdminMarker) {
            return TamperSignature.SIGNATURE_C_DEVICE_ADMIN
        }

        // -------------------------------------------------------------------------
        // Signature 1 (A) — Focus Lock Specific Accessibility Toggle Page or Dialog:
        // hasFocusLock AND none of the main Accessibility menu items
        // ("press power button to end calls", "accessibility menu", "shortcut from lock screen")
        // are present, AND ANY of:
        // 1. "focus lock uses this service" OR "detect foreground apps" OR "monitor screen time limits"
        // 2. "about this function" OR "auto-launch management" (OneUI, Realme UI, ColorOS)
        // 3. ("shortcut" AND "app info" on same screen as "focus lock")
        // 4. "use focus lock" OR "use service" OR "stop focus lock" OR "turn off focus lock" OR "allow focus lock"
        // 5. (!isGeneralAppList AND has "accessibility" AND ("on" or "off" or clickable Switch/Checkbox))
        // -------------------------------------------------------------------------
        val hasMainAccessibilityMenuItem = textsLower.any {
            it.contains("press power button to end calls") ||
            it.contains("accessibility menu") ||
            it.contains("shortcut from lock screen")
        }
        if (hasMainAccessibilityMenuItem) {
            return TamperSignature.NONE
        }

        // 1. Service description markers
        val hasServiceDescription = textsLower.any {
            it.contains("focus lock uses this service") ||
            it.contains("detect foreground apps") ||
            it.contains("monitor screen time limits")
        }
        if (hasServiceDescription) {
            return TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE
        }

        // 2. OEM sub-page markers (OneUI, Realme UI, ColorOS)
        val hasOemSubPageMarker = textsLower.any {
            it.contains("about this function") ||
            it.contains("auto-launch management")
        }
        if (hasOemSubPageMarker) {
            return TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE
        }

        // 3. "shortcut" AND "app info" on same screen as "focus lock"
        val hasShortcut = textsLower.any { it.contains("shortcut") }
        val hasAppInfo = textsLower.any { it.contains("app info") }
        if (hasShortcut && hasAppInfo) {
            return TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE
        }

        // 4. Specific toggle / dialog indicators
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
        if (hasSpecificToggleIndicator) {
            return TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE
        }

        // 5. Accessibility toggle page when NOT a general app / service list
        val isGeneralAppList = textsLower.any {
            it == "all apps" ||
            it == "app management" ||
            it == "manage apps" ||
            it == "installed apps" ||
            it == "installed services" ||
            it == "downloaded apps" ||
            it == "downloaded services" ||
            it == "see all apps" ||
            it == "default apps" ||
            it.contains("apps installed") ||
            it.contains("apps found") ||
            it.contains("downloaded apps") ||
            it.contains("installed apps")
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

        return TamperSignature.NONE
    }
}
