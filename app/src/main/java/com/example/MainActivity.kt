package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.FocusForegroundService
import com.example.ui.analytics.AnalyticsScreen
import com.example.ui.apps.AppsScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.groups.GroupsScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.FocusLockTheme
import com.example.ui.viewmodel.FocusViewModel
import com.example.ui.viewmodel.FocusViewModelFactory
import com.example.ui.webwords.WebKeywordsScreen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import com.example.receiver.FocusNotificationScheduler
import com.example.ui.splash.FocusLockStartupSplashOverlay
import com.example.ui.theme.BaseThemeMode

class MainActivity : ComponentActivity() {

    private var launcherTapReplayTrigger by mutableIntStateOf(0)
    private var wasStoppedBeforeNextIntent = false

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("MainActivity", "POST_NOTIFICATIONS granted: $isGranted")
    }

    private val viewModel: FocusViewModel by viewModels {
        val app = application as FocusApplication
        FocusViewModelFactory(app, app.repository, app.preferencesRepository, app.backupManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }
        super.onCreate(savedInstanceState)
        enableHighRefreshRate()
        enableEdgeToEdge()

        // Explicitly initialize Firebase for Google Cloud Backup & Restore
        com.example.util.FirebaseInitializer.ensureInitialized(this)

        // Start the enforcement background tracking service
        FocusForegroundService.startService(this)

        // Request notification permission on Android 13+ if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Schedule daily 9:00 PM notification engine
        FocusNotificationScheduler.scheduleNext9PmAlarm(this)

        setContent {
            val baseThemeMode by viewModel.baseThemeMode.collectAsStateWithLifecycle()
            val secondaryPalette by viewModel.secondaryPalette.collectAsStateWithLifecycle()
            val isOnboardingComplete by viewModel.isOnboardingComplete.collectAsStateWithLifecycle()

            val isSystemInDark = isSystemInDarkTheme()
            val isEffectiveDarkTheme = baseThemeMode.resolve(isSystemInDark) != BaseThemeMode.LIGHT

            var hasPlayedLaunchAnimation by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(launcherTapReplayTrigger) {
                if (launcherTapReplayTrigger > 0) {
                    hasPlayedLaunchAnimation = false
                }
            }

            FocusLockTheme(
                baseThemeMode = baseThemeMode,
                secondaryPalette = secondaryPalette
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. Existing Onboarding or Main App Navigation Content
                    if (!isOnboardingComplete) {
                        OnboardingScreen(
                            viewModel = viewModel,
                            onComplete = {
                                viewModel.setOnboardingComplete(true)
                            }
                        )
                    } else {
                        MainFocusApp(viewModel = viewModel)
                    }

                    // 2. Single-window startup animation overlay tied to Activity instance
                    if (!hasPlayedLaunchAnimation) {
                        FocusLockStartupSplashOverlay(
                            isDarkTheme = isEffectiveDarkTheme,
                            onAnimationFinished = {
                                hasPlayedLaunchAnimation = true
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (wasStoppedBeforeNextIntent &&
            intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        ) {
            wasStoppedBeforeNextIntent = false
            launcherTapReplayTrigger++
        }
    }

    private fun enableHighRefreshRate() {
        try {
            val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                this.display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            } ?: return

            val supportedModes = display.supportedModes
            val currentMode = display.mode
            val highestMode = supportedModes
                .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
                .maxByOrNull { it.refreshRate }
                ?: supportedModes.maxByOrNull { it.refreshRate }

            if (highestMode != null) {
                val layoutParams = window.attributes
                layoutParams.preferredDisplayModeId = highestMode.modeId
                layoutParams.preferredRefreshRate = highestMode.refreshRate
                window.attributes = layoutParams
            }
        } catch (_: Exception) {}
    }

    override fun onStart() {
        super.onStart()
        com.example.util.AppForegroundTracker.isFocusLockInForeground = true
    }

    override fun onResume() {
        super.onResume()
        wasStoppedBeforeNextIntent = false
        com.example.util.AppForegroundTracker.isFocusLockInForeground = true
        com.example.util.TimeIntegrityManager.getTrustedCurrentTimeMillis(this)
        if (com.example.util.PermissionHelper.isUsageStatsPermissionGranted(this)) {
            viewModel.refreshDailyUsageStats()
            viewModel.refresh30DayHistory()
        }
    }

    override fun onStop() {
        super.onStop()
        wasStoppedBeforeNextIntent = true
        com.example.util.AppForegroundTracker.isFocusLockInForeground = false
    }
}

sealed class NavigationTab(
    val routeIndex: Int,
    val title: String,
    val icon: ImageVector,
    val testTag: String
) {
    object Dashboard : NavigationTab(0, "Home", Icons.Default.Shield, "nav_dashboard")
    object Apps : NavigationTab(1, "Apps", Icons.Default.Apps, "nav_apps")
    object Groups : NavigationTab(2, "Groups", Icons.Default.Group, "nav_groups")
    object WebWords : NavigationTab(3, "Web/Words", Icons.Default.Language, "nav_webwords")
    object SettingsTab : NavigationTab(4, "Settings", Icons.Default.Settings, "nav_settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFocusApp(viewModel: FocusViewModel) {
    var currentTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var isViewingAnalytics by rememberSaveable { mutableStateOf(false) }

    // System Back Button Routing:
    // If viewing analytics, back returns to the previous screen.
    // If on a secondary tab (Apps, Groups, WebWords, Settings), back returns to Home tab (Dashboard, index 0).
    // If already on Home tab and not viewing analytics, BackHandler is disabled so system handles app exit normally.
    BackHandler(enabled = isViewingAnalytics || currentTabIndex != 0) {
        if (isViewingAnalytics) {
            isViewingAnalytics = false
        } else if (currentTabIndex != 0) {
            currentTabIndex = 0
        }
    }

    val tabs = remember {
        listOf(
            NavigationTab.Dashboard,
            NavigationTab.Apps,
            NavigationTab.Groups,
            NavigationTab.WebWords,
            NavigationTab.SettingsTab
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        topBar = {
            if (isViewingAnalytics) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Analytics & Dwell",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { isViewingAnalytics = false },
                            modifier = Modifier.testTag("top_bar_analytics_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Return to Dashboard",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val view = androidx.compose.ui.platform.LocalView.current
            val outerCapsuleBg = MaterialTheme.colorScheme.primary
                .copy(alpha = 0.06f)
                .compositeOver(Color(0xFF0E0E14))
            val outerCapsuleBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .testTag("bottom_navigation_bar"),
                    shape = RoundedCornerShape(percent = 50),
                    color = outerCapsuleBg,
                    border = BorderStroke(width = 1.2.dp, color = outerCapsuleBorder),
                    shadowElevation = 12.dp,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEach { tab ->
                            val isSelected = !isViewingAnalytics && currentTabIndex == tab.routeIndex
                            val selectedInnerPillBg = MaterialTheme.colorScheme.primary
                                .copy(alpha = 0.20f)
                                .compositeOver(Color(0xFF1C1D26))

                            val pillBgColor by animateColorAsState(
                                targetValue = if (isSelected) selectedInnerPillBg else Color.Transparent,
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                label = "tab_pill_bg_${tab.routeIndex}"
                            )
                            val pillBorderColor by animateColorAsState(
                                targetValue = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                } else {
                                    Color.Transparent
                                },
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                label = "tab_pill_border_${tab.routeIndex}"
                            )
                            val iconTint by animateColorAsState(
                                targetValue = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White.copy(alpha = 0.50f)
                                },
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                label = "tab_icon_tint_${tab.routeIndex}"
                            )
                            val labelColor by animateColorAsState(
                                targetValue = if (isSelected) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.50f)
                                },
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                label = "tab_label_color_${tab.routeIndex}"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag(tab.testTag)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(
                                        color = pillBgColor,
                                        shape = RoundedCornerShape(percent = 50)
                                    )
                                    .border(
                                        border = BorderStroke(width = 1.dp, color = pillBorderColor),
                                        shape = RoundedCornerShape(percent = 50)
                                    )
                                    .clickable {
                                        com.example.util.FocusHaptics.tick(view)
                                        isViewingAnalytics = false
                                        currentTabIndex = tab.routeIndex
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = iconTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = tab.title,
                                        color = labelColor,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isViewingAnalytics) {
                AnalyticsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { isViewingAnalytics = false },
                    onNavigateToApps = {
                        isViewingAnalytics = false
                        currentTabIndex = 1
                    }
                )
            } else {
                when (currentTabIndex) {
                    0 -> DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToTab = { targetTab ->
                            isViewingAnalytics = false
                            currentTabIndex = targetTab
                        },
                        onNavigateToAnalytics = {
                            isViewingAnalytics = true
                        }
                    )
                    1 -> AppsScreen(viewModel = viewModel)
                    2 -> GroupsScreen(viewModel = viewModel)
                    3 -> WebKeywordsScreen(viewModel = viewModel)
                    4 -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
