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

    private val KNOWN_SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.google.android.settings",
        "com.coloros.settings",
        "com.oplus.settings",
        "com.oppo.settings",
        "com.vivo.settings",
        "com.oneplus.settings",
        "com.xiaomi.settings",
        "com.miui.settings"
    )

    fun isSettingsAppPackage(pkgName: String?): Boolean {
        if (pkgName.isNullOrBlank()) return false
        val lower = pkgName.lowercase().trim()
        return lower.contains("settings") && !lower.contains("focuslock")
    }

    fun isAccessibilityServiceDetailFragment(eventClassName: String?): Boolean {
        if (eventClassName.isNullOrBlank()) return false
        val lower = eventClassName.lowercase()
        return lower.contains("toggleaccessibilityservicepreferencefragment") ||
            lower.contains("accessibilitydetailssettingsfragment") ||
            lower.contains("installedservicesfragment")
    }

    fun isNonAccessibilityRootExitClass(eventClassName: String?): Boolean {
        if (eventClassName.isNullOrBlank()) return false
        val lower = eventClassName.lowercase()
        return lower.contains("settingshomepageactivity") ||
            lower.contains("toplevelsettings") ||
            lower.contains("manageapplications") ||
            lower.contains("appdashboardfragment") ||
            lower.contains("applicationssettings")
    }

    fun hasAppOrFocusLockName(
        textsLower: List<String>,
        appPackageName: String,
        appLabel: String = "Focus Lock"
    ): Boolean {
        val targetPkgLower = appPackageName.lowercase().trim()
        val labelLower = appLabel.lowercase().trim()
        return textsLower.any {
            it.contains("focus lock") ||
                (labelLower.isNotEmpty() && it.contains(labelLower)) ||
                (targetPkgLower.isNotEmpty() && it.contains(targetPkgLower))
        }
    }

    fun isNormalAppManagerScreen(textsLower: List<String>): Boolean {
        return textsLower.any {
            it == "all apps" ||
            it == "app management" ||
            it == "manage apps" ||
            it == "see all apps" ||
            it == "default apps" ||
            it == "permission manager" ||
            it.contains("apps installed") ||
            it.contains("apps found") ||
            it.contains("permission manager")
        }
    }

    fun isFocusLockServiceDetailContent(textsLower: List<String>): Boolean {
        val hasServiceDescription = textsLower.any {
            it.contains("focus lock uses this service") ||
            it.contains("detect foreground apps") ||
            it.contains("monitor screen time limits")
        }
        if (hasServiceDescription) return true

        val hasOemSubPageMarker = textsLower.any {
            it.contains("about this function") ||
            it.contains("auto-launch management")
        }
        if (hasOemSubPageMarker) return true

        val hasShortcut = textsLower.any { it.contains("shortcut") }
        val hasAppInfo = textsLower.any { it.contains("app info") }
        if (hasShortcut && hasAppInfo) return true

        val specificToggleIndicators = listOf(
            "use focus lock",
            "use service",
            "focus lock shortcut",
            "stop focus lock",
            "turn off focus lock",
            "allow focus lock"
        )
        return textsLower.any { text ->
            specificToggleIndicators.any { ind -> text.contains(ind) }
        }
    }

    fun matchesInstantStepAEventTrigger(
        eventTexts: List<String>,
        eventClassName: String?,
        appPackageName: String,
        appLabel: String = "Focus Lock"
    ): Boolean {
        if (isNonAccessibilityRootExitClass(eventClassName)) return false

        val textsLower = eventTexts.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        if (isSafePermissionScreen(textsLower)) return false
        if (isNormalAppManagerScreen(textsLower)) return false

        if (isAccessibilityServiceDetailFragment(eventClassName)) return true
        if (textsLower.isEmpty()) return false

        if (hasAppOrFocusLockName(textsLower, appPackageName, appLabel)) return true

        return textsLower.any {
            it.contains("downloaded apps") ||
                it.contains("installed apps") ||
                it.contains("installed services") ||
                it.contains("downloaded services")
        }
    }

    fun isMainAccessibilityRootScreen(
        eventClassName: String?,
        texts: List<String>,
        appPackageName: String,
        appLabel: String = "Focus Lock"
    ): Boolean {
        if (isAccessibilityServiceDetailFragment(eventClassName)) return false

        val textsLower = texts.map { it.lowercase().trim() }
        if (isSafePermissionScreen(textsLower)) return false
        if (isNormalAppManagerScreen(textsLower)) return false
        if (isTamperConfirmationDialog(texts, appPackageName, appLabel)) return false

        val hasApp = hasAppOrFocusLockName(textsLower, appPackageName, appLabel)
        if (hasApp && isFocusLockServiceDetailContent(textsLower)) {
            return false
        }

        val hasMainAccessibilityMenuItem = textsLower.any {
            it.contains("press power button to end calls") ||
            it.contains("accessibility menu") ||
            it.contains("shortcut from lock screen") ||
            it.contains("display size and text") ||
            it.contains("color and motion") ||
            it.contains("visibility enhancements") ||
            it.contains("hearing enhancements") ||
            it.contains("interaction controls") ||
            it.contains("system controls") ||
            it.contains("caption preferences")
        }
        if (hasMainAccessibilityMenuItem) return true

        if (!eventClassName.isNullOrBlank() &&
            eventClassName.contains("AccessibilitySettings", ignoreCase = true)
        ) {
            return true
        }

        val primaryTitle = textsLower.firstOrNull {
            it.isNotEmpty() && it != "navigate up" && it != "back" && it != "search"
        }
        return primaryTitle == "accessibility"
    }

    fun isTamperConfirmationDialog(
        texts: List<String>,
        appPackageName: String,
        appLabel: String = "Focus Lock"
    ): Boolean {
        if (texts.isEmpty()) return false
        val textsLower = texts.map { it.lowercase().trim() }
        if (isSafePermissionScreen(textsLower)) return false

        if (!hasAppOrFocusLockName(textsLower, appPackageName, appLabel)) return false

        val hasStopOrTurnOff = textsLower.any {
            it.contains("turn off") ||
                it.contains("stop focus lock") ||
                it == "stop" ||
                it.contains("stop?") ||
                it.contains("will stop") ||
                it.contains("deactivate") ||
                it.contains("force stop")
        }
        val hasCancelAction = textsLower.any {
            it == "cancel" || it.contains("cancel") || it == "deny" || it.contains("keep on")
        }
        val hasDirectConfirmationPhrase = textsLower.any {
            it.contains("turn off focus lock") ||
                it.contains("stop focus lock") ||
                it.contains("deactivate this device admin app")
        }

        return hasDirectConfirmationPhrase || (hasStopOrTurnOff && hasCancelAction)
    }

    fun isSafePermissionScreen(textsLower: List<String>): Boolean {
        return textsLower.any { text ->
            SAFE_PERMISSION_WHITELIST.any { safe ->
                if (safe == "activate this device admin app") {
                    text.contains(safe) && !textsLower.any { it.contains("deactivate") }
                } else {
                    text.contains(safe)
                }
            }
        }
    }

    /**
     * Evaluates collected screen text and fragment class name against exact tamper signatures.
     */
    fun matchTamperSignature(
        texts: List<String>,
        hasClickableSwitchOrCheckbox: Boolean,
        isClockTamperProtectionEnabled: Boolean,
        appPackageName: String,
        appLabel: String = "Focus Lock",
        eventClassName: String? = null
    ): TamperSignature {
        val textsLower = texts.map { it.lowercase().trim() }

        // Safe Whitelist: NEVER block or trigger anti-tamper on permission grant screens!
        if (isSafePermissionScreen(textsLower)) {
            return TamperSignature.NONE
        }

        // Language-independent service detail fragments (ToggleAccessibilityServicePreferenceFragment, etc.)
        if (isAccessibilityServiceDetailFragment(eventClassName)) {
            val isInstalledServicesList = eventClassName?.contains("InstalledServicesFragment", ignoreCase = true) == true
            if (isInstalledServicesList || texts.isEmpty() || hasAppOrFocusLockName(textsLower, appPackageName, appLabel)) {
                return TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE
            }
        }

        if (texts.isEmpty()) return TamperSignature.NONE

        // Signature D — System Date & Time Settings
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

        val isNormalAppList = isNormalAppManagerScreen(textsLower) || isNonAccessibilityRootExitClass(eventClassName)
        val isMainAccRoot = isMainAccessibilityRootScreen(eventClassName, texts, appPackageName, appLabel)

        // Step B Fallback: "Downloaded apps" / "Installed services" / Accessibility "Installed apps"
        // when NOT on the main Accessibility root menu and NOT on the main Settings -> Apps manager.
        if (!isMainAccRoot && !isNormalAppList) {
            val hasDownloadedAppsOrServicesScreen = textsLower.any {
                it.contains("downloaded apps") ||
                    it.contains("downloaded services") ||
                    it.contains("installed services") ||
                    it.contains("services downloaded")
            }
            val hasInstalledAppsInAccessibility = textsLower.any {
                it.contains("installed apps")
            } && textsLower.any {
                it.contains("accessibility") ||
                    it.contains("focus lock") ||
                    it.contains("talkback") ||
                    it.contains("service") ||
                    it == "on" ||
                    it == "off"
            }
            if (hasDownloadedAppsOrServicesScreen || hasInstalledAppsInAccessibility) {
                return TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE
            }
        }

        val hasFocusLockName = hasAppOrFocusLockName(textsLower, appPackageName, appLabel)
        if (!hasFocusLockName) {
            return TamperSignature.NONE
        }

        if (isMainAccRoot) {
            return TamperSignature.NONE
        }

        // Signature B — Focus Lock App Info Page (Force Stop / Uninstall)
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

        // Signature C — Device Admin Deactivation Page for Focus Lock
        val hasDeviceAdminMarker = textsLower.any {
            it.contains("deactivate") || it.contains("device admin")
        }
        if (hasDeviceAdminMarker) {
            return TamperSignature.SIGNATURE_C_DEVICE_ADMIN
        }

        // Direct Focus Lock Accessibility detail / confirmation dialog
        if (isFocusLockServiceDetailContent(textsLower) ||
            isTamperConfirmationDialog(texts, appPackageName, appLabel)
        ) {
            return TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE
        }

        if (!isNormalAppList) {
            val hasAccessibilityOrShortcutWord = textsLower.any {
                it.contains("accessibility") || it.contains("shortcut")
            }
            val hasOnOffWord = textsLower.any {
                it == "on" || it == "off" || it.contains(" turned on") || it.contains(" turned off")
            }
            if (hasAccessibilityOrShortcutWord && (hasOnOffWord || hasClickableSwitchOrCheckbox)) {
                return TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE
            }
        }

        return TamperSignature.NONE
    }
}
