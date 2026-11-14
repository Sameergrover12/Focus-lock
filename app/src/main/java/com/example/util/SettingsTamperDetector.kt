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

    @Volatile
    var lastDeviceAdminLaunchTimestamp: Long = 0L

    const val DEVICE_ADMIN_LAUNCH_WINDOW_MS = 45_000L
    const val KEY_LAST_DEVICE_ADMIN_LAUNCH_TS = "last_device_admin_launch_timestamp"

    fun recordDeviceAdminLaunch(
        context: android.content.Context? = null,
        nowElapsedMs: Long = try { android.os.SystemClock.elapsedRealtime() } catch (_: Throwable) { System.currentTimeMillis() }
    ) {
        val ts = if (nowElapsedMs > 0L) nowElapsedMs else 1L
        lastDeviceAdminLaunchTimestamp = ts
        if (context != null) {
            try {
                context.getSharedPreferences("user_preferences_sync", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_LAST_DEVICE_ADMIN_LAUNCH_TS, ts)
                    .apply()
            } catch (_: Exception) {}
        }
    }

    fun clearDeviceAdminLaunchTimestamp(context: android.content.Context? = null) {
        lastDeviceAdminLaunchTimestamp = 0L
        if (context != null) {
            try {
                context.getSharedPreferences("user_preferences_sync", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_LAST_DEVICE_ADMIN_LAUNCH_TS)
                    .apply()
            } catch (_: Exception) {}
        }
    }

    fun isWithinDeviceAdminLaunchWindow(
        context: android.content.Context? = null,
        nowElapsedMs: Long = try { android.os.SystemClock.elapsedRealtime() } catch (_: Throwable) { System.currentTimeMillis() }
    ): Boolean {
        var ts = lastDeviceAdminLaunchTimestamp
        if (ts <= 0L && context != null) {
            ts = try {
                context.getSharedPreferences("user_preferences_sync", android.content.Context.MODE_PRIVATE)
                    .getLong(KEY_LAST_DEVICE_ADMIN_LAUNCH_TS, 0L)
            } catch (_: Exception) {
                0L
            }
            if (ts > 0L) {
                lastDeviceAdminLaunchTimestamp = ts
            }
        }
        if (ts <= 0L || nowElapsedMs < ts) return false
        return (nowElapsedMs - ts) < DEVICE_ADMIN_LAUNCH_WINDOW_MS
    }

    fun isDeviceAdminDeactivationClass(eventClassName: String?): Boolean {
        if (eventClassName.isNullOrBlank()) return false
        val lower = eventClassName.lowercase()
        return lower.contains("deviceadminadd") || lower.contains("deviceadminsettings")
    }

    fun isDeviceAdminScreen(
        eventClassName: String?,
        texts: List<String>
    ): Boolean {
        if (eventClassName?.contains("DeviceAdmin", ignoreCase = true) == true) {
            return true
        }
        val textsLower = texts.map { it.lowercase().trim() }
        return textsLower.any {
            it.contains("activate device admin") ||
                it.contains("activate this device admin") ||
                it.contains("device admin") ||
                it.contains("verification code") ||
                it.contains("deactivate this device admin") ||
                it.contains("remove active admin")
        }
    }

    fun isTargetScreenCDeviceAdminDeactivation(
        eventClassName: String?,
        textsLower: List<String>,
        appPackageName: String,
        appLabel: String = "Focus Lock"
    ): Boolean {
        if (isDeviceAdminDeactivationClass(eventClassName)) {
            return true
        }
        val hasFocusLock = hasAppOrFocusLockName(textsLower, appPackageName, appLabel)
        if (!hasFocusLock) return false
        return textsLower.any {
            it.contains("deactivate this device admin") ||
                it.contains("remove active admin") ||
                it.contains("deactivate")
        }
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

    fun isDownloadedServicesListFragment(eventClassName: String?): Boolean {
        if (eventClassName.isNullOrBlank()) return false
        val lower = eventClassName.lowercase()
        return lower.contains("installedservicesfragment") ||
            lower.contains("downloadedservicesfragment")
    }

    fun isFocusLockAccessibilityDetailFragment(eventClassName: String?): Boolean {
        if (eventClassName.isNullOrBlank()) return false
        val lower = eventClassName.lowercase()
        return lower.contains("toggleaccessibilityservicepreferencefragment") ||
            lower.contains("accessibilitydetailssettingsfragment")
    }

    fun isAccessibilityServiceDetailFragment(eventClassName: String?): Boolean {
        return isDownloadedServicesListFragment(eventClassName) ||
            isFocusLockAccessibilityDetailFragment(eventClassName)
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
            "turn off",
            "allow focus lock"
        )
        return textsLower.any { text ->
            specificToggleIndicators.any { ind -> text.contains(ind) }
        }
    }

    /**
     * Rule 1 — Absolute Settings Search Immunity:
     * Returns true when any Settings Search indicator is active in the event or window.
     */
    fun isSettingsSearchScreen(
        eventPackageName: String? = null,
        eventClassName: String? = null,
        texts: List<String> = emptyList(),
        hasFocusedEditText: Boolean = false,
        sourceClassName: String? = null
    ): Boolean {
        if (eventPackageName?.contains("settings.intelligence", ignoreCase = true) == true) {
            return true
        }
        if (eventClassName?.contains("Search", ignoreCase = true) == true ||
            eventClassName?.contains("EditText", ignoreCase = true) == true
        ) {
            return true
        }
        if (sourceClassName?.contains("EditText", ignoreCase = true) == true) {
            return true
        }
        if (hasFocusedEditText) {
            return true
        }
        val textsLower = texts.map { it.lowercase().trim() }
        return textsLower.any {
            it.contains("search settings") ||
                it == "search" ||
                it.startsWith("search ") ||
                it.contains("search history") ||
                it == "suggestions" ||
                it.contains("recent searches")
        }
    }

    /**
     * Rule 2 & 3 — Instant Step A check (with Rule 1 Search Immunity at the very top).
     */
    fun matchesInstantStepAEventTrigger(
        eventTexts: List<String>,
        eventClassName: String?,
        appPackageName: String,
        appLabel: String = "Focus Lock",
        isClickEvent: Boolean = true,
        eventPackageName: String? = null,
        isDeviceAdminActive: Boolean = false,
        isWithinDeviceAdminLaunchWindow: Boolean = false,
        sourceClassName: String? = null,
        isInAccessibilityContext: Boolean = true,
        isInSecurityOrDeviceAdminContext: Boolean = true,
        isInSafeNonTamperSubSettings: Boolean = false
    ): Boolean {
        // Rule 1: Absolute Settings Search Immunity at the very top (for ALL event types, including clicks!)
        if (isSettingsSearchScreen(
                eventPackageName = eventPackageName,
                eventClassName = eventClassName,
                texts = eventTexts,
                hasFocusedEditText = false,
                sourceClassName = sourceClassName
            )
        ) {
            return false
        }

        if (isNonAccessibilityRootExitClass(eventClassName)) return false

        val textsLower = eventTexts.map { it.lowercase().trim() }.filter { it.isNotEmpty() }

        // Smart Device Admin Activation Immunity when isDeviceAdminActive == false
        val onDeviceAdminScreen = isDeviceAdminScreen(eventClassName, eventTexts)
        val isAccessibilityTamperEvent = isAccessibilityServiceDetailFragment(eventClassName) ||
            textsLower.any {
                it.contains("downloaded apps") ||
                    it.contains("installed services") ||
                    it.contains("downloaded services") ||
                    it.contains("use focus lock") ||
                    it.contains("focus lock uses this service")
            }
        if (!isDeviceAdminActive) {
            if (onDeviceAdminScreen) return false
            if (isWithinDeviceAdminLaunchWindow && !isAccessibilityTamperEvent) return false
        }

        if (isSafePermissionScreen(textsLower, isDeviceAdminActive)) return false
        if (isNormalAppManagerScreen(textsLower)) return false

        if (isAccessibilityServiceDetailFragment(eventClassName)) return true

        // Rule 2: Strict TYPE_VIEW_CLICKED Check (Inspect ONLY the clicked row)
        if (isClickEvent) {
            // a) User tapped "Downloaded apps", "Installed apps", "Installed services", or "Downloaded services"
            // while on the main Accessibility screen
            val clickedDownloadedOrInstalledServices = textsLower.any {
                it.contains("downloaded apps") ||
                    it.contains("installed services") ||
                    it.contains("downloaded services") ||
                    (isInAccessibilityContext && it.contains("installed apps"))
            }
            if (clickedDownloadedOrInstalledServices) return true

            // b) User tapped "Focus Lock" accessibility service row inside Accessibility / Downloaded apps
            // (do NOT trigger if "Focus Lock" is tapped inside normal Battery, Data usage, or Notifications settings)
            val clickedFocusLock = hasAppOrFocusLockName(textsLower, appPackageName, appLabel)
            if (clickedFocusLock && !isInSafeNonTamperSubSettings && isInAccessibilityContext) {
                return true
            }

            // c) isDeviceAdminCurrentlyActive() == true AND user tapped "Device admin apps" or "Focus Lock"
            // inside Security / Device admin settings
            if (isDeviceAdminActive) {
                val clickedDeviceAdminApps = textsLower.any {
                    it.contains("device admin apps") ||
                        it.contains("deactivate this device admin") ||
                        it.contains("remove active admin")
                }
                if (clickedDeviceAdminApps) return true
                if (clickedFocusLock && isInSecurityOrDeviceAdminContext && !isInSafeNonTamperSubSettings) {
                    return true
                }
            }

            return false
        }

        // Rule 3: For non-click events (TYPE_WINDOW_STATE_CHANGED / TYPE_WINDOW_CONTENT_CHANGED / TYPE_WINDOWS_CHANGED):
        // Target Screen A or B fragment classes:
        if (isAccessibilityServiceDetailFragment(eventClassName)) return true

        // Target Screen C fragment classes or deactivation text (ONLY when isDeviceAdminActive == true):
        if (isDeviceAdminActive && isTargetScreenCDeviceAdminDeactivation(eventClassName, textsLower, appPackageName, appLabel)) {
            return true
        }

        if (textsLower.isEmpty()) return false

        return isTamperConfirmationDialog(eventTexts, appPackageName, appLabel, isDeviceAdminActive)
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
        appLabel: String = "Focus Lock",
        isDeviceAdminActive: Boolean = true
    ): Boolean {
        if (texts.isEmpty()) return false
        val textsLower = texts.map { it.lowercase().trim() }
        if (isSafePermissionScreen(textsLower, isDeviceAdminActive)) return false

        if (!isDeviceAdminActive && isDeviceAdminScreen(null, texts)) {
            return false
        }

        if (!hasAppOrFocusLockName(textsLower, appPackageName, appLabel)) return false

        val hasStopOrTurnOff = textsLower.any {
            it.contains("turn off") ||
                it.contains("stop focus lock") ||
                it == "stop" ||
                it.contains("stop?") ||
                it.contains("will stop") ||
                (isDeviceAdminActive && it.contains("deactivate")) ||
                it.contains("force stop")
        }
        val hasCancelAction = textsLower.any {
            it == "cancel" || it.contains("cancel") || it == "deny" || it.contains("keep on")
        }
        val hasDirectConfirmationPhrase = textsLower.any {
            it.contains("turn off focus lock") ||
                it.contains("stop focus lock") ||
                (isDeviceAdminActive && it.contains("deactivate this device admin app"))
        }

        return hasDirectConfirmationPhrase || (hasStopOrTurnOff && hasCancelAction)
    }

    fun isSafePermissionScreen(
        textsLower: List<String>,
        isDeviceAdminActive: Boolean = false
    ): Boolean {
        return textsLower.any { text ->
            SAFE_PERMISSION_WHITELIST.any { safe ->
                if (safe == "activate this device admin app") {
                    !isDeviceAdminActive && text.contains(safe) && !textsLower.any { it.contains("deactivate") }
                } else {
                    text.contains(safe)
                }
            }
        }
    }

    /**
     * Rule 1 + Rule 3: Evaluates collected screen text and fragment class name against exact target screens.
     */
    fun matchTamperSignature(
        texts: List<String>,
        hasClickableSwitchOrCheckbox: Boolean,
        isClockTamperProtectionEnabled: Boolean,
        appPackageName: String,
        appLabel: String = "Focus Lock",
        eventClassName: String? = null,
        eventPackageName: String? = null,
        hasFocusedEditText: Boolean = false,
        isDeviceAdminActive: Boolean = false,
        isWithinDeviceAdminLaunchWindow: Boolean = false
    ): TamperSignature {
        // Rule 1: Absolute Settings Search Immunity at the VERY TOP (before ALL anti-tamper checks!)
        if (isSettingsSearchScreen(
                eventPackageName = eventPackageName,
                eventClassName = eventClassName,
                texts = texts,
                hasFocusedEditText = hasFocusedEditText
            )
        ) {
            return TamperSignature.NONE
        }

        val textsLower = texts.map { it.lowercase().trim() }

        // Safe Whitelist: NEVER block or trigger anti-tamper on permission grant screens!
        if (isSafePermissionScreen(textsLower, isDeviceAdminActive)) {
            return TamperSignature.NONE
        }

        // Smart Device Admin Rule:
        // When Device Admin is NOT active yet, allow Device Admin activation screens & Realme verification code popup.
        val onDeviceAdminScreen = isDeviceAdminScreen(eventClassName, texts)
        val isAccessibilityTamperScreen = isAccessibilityServiceDetailFragment(eventClassName) ||
            textsLower.any {
                it.contains("downloaded apps") ||
                    it.contains("installed services") ||
                    it.contains("downloaded services") ||
                    it.contains("use focus lock") ||
                    it.contains("focus lock uses this service")
            }
        if (onDeviceAdminScreen) {
            val hasDeactivatePhrase = textsLower.any {
                it.contains("deactivate this device admin") ||
                    it.contains("remove active admin") ||
                    it.contains("deactivate")
            }
            val hasActivateOnlyPhrase = textsLower.any {
                it.contains("activate device admin") ||
                    it.contains("activate this device admin") ||
                    it.contains("verification code")
            } && !hasDeactivatePhrase

            if (!isDeviceAdminActive && (hasActivateOnlyPhrase || !hasDeactivatePhrase)) {
                return TamperSignature.NONE
            }
            if (isDeviceAdminActive && isTargetScreenCDeviceAdminDeactivation(eventClassName, textsLower, appPackageName, appLabel)) {
                return TamperSignature.SIGNATURE_C_DEVICE_ADMIN
            }
            if (hasDeactivatePhrase && hasAppOrFocusLockName(textsLower, appPackageName, appLabel)) {
                return TamperSignature.SIGNATURE_C_DEVICE_ADMIN
            }
        }
        if (!isDeviceAdminActive && isWithinDeviceAdminLaunchWindow && !isAccessibilityTamperScreen) {
            return TamperSignature.NONE
        }

        // Target Screen A or B fragment classes:
        if (isAccessibilityServiceDetailFragment(eventClassName)) {
            val isInstalledServicesList = isDownloadedServicesListFragment(eventClassName)
            if (isInstalledServicesList || texts.isEmpty() || hasAppOrFocusLockName(textsLower, appPackageName, appLabel)) {
                return TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE
            }
        }

        // Target Screen C fragment classes when isDeviceAdminActive == true:
        if (isDeviceAdminActive && isDeviceAdminDeactivationClass(eventClassName)) {
            return TamperSignature.SIGNATURE_C_DEVICE_ADMIN
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

        // Target Screen A — Accessibility Downloaded/Installed Apps List
        if (!isMainAccRoot && !isNormalAppList) {
            val hasDownloadedOrInstalledHeader = textsLower.any {
                it.contains("downloaded apps") ||
                    it.contains("downloaded services") ||
                    it.contains("installed services") ||
                    it.contains("services downloaded") ||
                    it.contains("installed apps")
            }
            val hasAccessibilityServicesListing = textsLower.any {
                it.contains("focus lock") ||
                    it.contains("guided vision") ||
                    it.contains("talkback") ||
                    it.contains("tap here to enable") ||
                    it.contains("accessibility") ||
                    it == "on" ||
                    it == "off"
            }
            if (hasDownloadedOrInstalledHeader && hasAccessibilityServicesListing) {
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

        // Target Screen C — Device Admin Deactivation Page for Focus Lock
        if (isTargetScreenCDeviceAdminDeactivation(eventClassName, textsLower, appPackageName, appLabel)) {
            return TamperSignature.SIGNATURE_C_DEVICE_ADMIN
        }

        // Target Screen B — Focus Lock Accessibility Detail / Toggle Page or Turn-Off Dialog
        if (isFocusLockServiceDetailContent(textsLower) ||
            isTamperConfirmationDialog(texts, appPackageName, appLabel, isDeviceAdminActive)
        ) {
            return TamperSignature.SIGNATURE_A_ACCESSIBILITY_TOGGLE
        }

        if (!isNormalAppList) {
            val hasAccessibilityOrShortcutWord = textsLower.any {
                it.contains("accessibility") || it.contains("shortcut") || it.contains("focus lock shortcut")
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
