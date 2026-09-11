package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.FocusForegroundService
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
    val isMasterEnabled by viewModel.isMasterEnabled.collectAsStateWithLifecycle()

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Focus Lock",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isMasterEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isMasterEnabled) "Active" else "Paused",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, style = MaterialTheme.typography.labelSmall) },
                        selected = currentTabIndex == tab.routeIndex,
                        onClick = { currentTabIndex = tab.routeIndex },
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTabIndex) {
                0 -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToTab = { currentTabIndex = it }
                )
                1 -> AppsScreen(viewModel = viewModel)
                2 -> GroupsScreen(viewModel = viewModel)
                3 -> WebKeywordsScreen(viewModel = viewModel)
                4 -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
