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
        FocusViewModelFactory(app, app.repository, app.preferencesRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start the enforcement background tracking service
        FocusForegroundService.startService(this)

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val isOnboardingComplete by viewModel.isOnboardingComplete.collectAsStateWithLifecycle()

            FocusLockTheme(themeMode = themeMode) {
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

    override fun onResume() {
        super.onResume()
        viewModel.loadInstalledApps()
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
    val isMasterEnabled by viewModel.isMasterEnabled.collectAsStateWithLifecycle()

    BackHandler(enabled = isViewingAnalytics) {
        isViewingAnalytics = false
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
            TopAppBar(
                title = {
                    if (isViewingAnalytics) {
                        Text(
                            text = "Analytics & Dwell",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Focus Lock",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (isMasterEnabled) Color(0xFF10B981) else Color(0xFFEF5350),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isMasterEnabled) "Active" else "Paused",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF9E9E9E),
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isViewingAnalytics) {
                        IconButton(
                            onClick = { isViewingAnalytics = false },
                            modifier = Modifier.testTag("top_bar_analytics_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Return to Dashboard",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF000000)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        // Subtle 1dp top border using semi-transparent emerald for physical separation
                        drawLine(
                            color = Color(0x3322C55E),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    },
                containerColor = Color(0xCC0D120F),
                tonalElevation = 0.dp
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, style = MaterialTheme.typography.labelSmall) },
                        alwaysShowLabel = false,
                        selected = !isViewingAnalytics && currentTabIndex == tab.routeIndex,
                        onClick = {
                            isViewingAnalytics = false
                            currentTabIndex = tab.routeIndex
                        },
                        modifier = Modifier
                            .testTag(tab.testTag)
                            .padding(vertical = 4.dp),
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color(0xFF064E3B).copy(alpha = 0.85f),
                            selectedIconColor = Color(0xFF22C55E),
                            selectedTextColor = Color(0xFF22C55E),
                            unselectedIconColor = Color(0xFF757575),
                            unselectedTextColor = Color(0xFF757575)
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
                .background(Color(0xFF000000))
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
