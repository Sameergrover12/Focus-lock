package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

class MainActivity : ComponentActivity() {

    private val viewModel: FocusViewModel by viewModels {
        val app = application as FocusApplication
        FocusViewModelFactory(app, app.repository, app.preferencesRepository, app.backupManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableHighRefreshRate()
        enableEdgeToEdge()

        // Explicitly initialize Firebase for Google Cloud Backup & Restore
        com.example.util.FirebaseInitializer.ensureInitialized(this)

        // Start the enforcement background tracking service
        FocusForegroundService.startService(this)

        setContent {
            val baseThemeMode by viewModel.baseThemeMode.collectAsStateWithLifecycle()
            val secondaryPalette by viewModel.secondaryPalette.collectAsStateWithLifecycle()
            val isOnboardingComplete by viewModel.isOnboardingComplete.collectAsStateWithLifecycle()

            FocusLockTheme(
                baseThemeMode = baseThemeMode,
                secondaryPalette = secondaryPalette
            ) {
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
            }
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
        com.example.util.AppForegroundTracker.isFocusLockInForeground = true
    }

    override fun onStop() {
        super.onStop()
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
        bottomBar = {
            val navOutlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            val view = androidx.compose.ui.platform.LocalView.current
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        // Subtle 1dp top border using palette outline for physical separation
                        drawLine(
                            color = navOutlineColor,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    },
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, style = MaterialTheme.typography.labelSmall) },
                        alwaysShowLabel = false,
                        selected = !isViewingAnalytics && currentTabIndex == tab.routeIndex,
                        onClick = {
                            com.example.util.FocusHaptics.tick(view)
                            isViewingAnalytics = false
                            currentTabIndex = tab.routeIndex
                        },
                        modifier = Modifier
                            .testTag(tab.testTag)
                            .padding(vertical = 4.dp),
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
