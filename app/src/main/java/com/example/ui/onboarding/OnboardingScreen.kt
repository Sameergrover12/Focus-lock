package com.example.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.FocusViewModel
import com.example.util.PermissionHelper
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    viewModel: FocusViewModel? = null,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 4 })

    var hasAccessibility by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var hasUsageStats by remember { mutableStateOf(PermissionHelper.isUsageStatsPermissionGranted(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccessibility = PermissionHelper.isAccessibilityServiceEnabled(context)
                hasUsageStats = PermissionHelper.isUsageStatsPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val selectedCommitment = viewModel?.reclaimedCommitment?.collectAsStateWithLifecycle()?.value ?: "My Focus"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .systemBarsPadding()
            .testTag("onboarding_root")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar with Page Indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier.testTag("onboarding_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // Minimalist dot indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .width(if (isSelected) 24.dp else 8.dp)
                                .background(
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                if (pagerState.currentPage < 3) {
                    Text(
                        text = "Skip",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                scope.launch {
                                    pagerState.animateScrollToPage(3)
                                }
                            }
                            .padding(8.dp)
                            .testTag("onboarding_skip_button")
                    )
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            // 4 Pager Pages
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("onboarding_pager")
            ) { page ->
                when (page) {
                    0 -> RealityCheckScreen(isActive = pagerState.currentPage == 0)
                    1 -> TheArsenalScreen(isActive = pagerState.currentPage == 1)
                    2 -> InteractiveCommitmentScreen(
                        isActive = pagerState.currentPage == 2,
                        currentSelection = selectedCommitment,
                        onSelectCommitment = { choice ->
                            viewModel?.setReclaimedCommitment(choice)
                        }
                    )
                    3 -> TransparencyPactScreen(
                        isActive = pagerState.currentPage == 3,
                        hasAccessibility = hasAccessibility,
                        hasUsageStats = hasUsageStats,
                        onOpenAccessibility = { PermissionHelper.openAccessibilitySettings(context) },
                        onOpenUsageStats = { PermissionHelper.openUsageAccessSettings(context) }
                    )
                }
            }

            // Bottom Navigation Action
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (pagerState.currentPage < 3) {
                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("onboarding_next_button"),
                        shape = RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = if (pagerState.currentPage == 2) "Review Transparency Pact" else "Continue",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("onboarding_complete_button"),
                        shape = RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = "Enter Focus Lock",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Screen 1: The Reality Check
 * Headline: "You are being engineered."
 * Body: "Algorithms are designed to harvest your attention. This tool is your counter-measure to take your mind back."
 * Visual: A minimal geometric shape with a slow, breathing pulse animation.
 */
@Composable
private fun RealityCheckScreen(isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Breathing Geometric Shape Canvas
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = (size.minDimension / 2f) * 0.72f

                // Outer pulsing breathing ring
                drawCircle(
                    color = Color.White.copy(alpha = pulseAlpha * 0.45f),
                    radius = baseRadius * pulseScale,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Middle sharp geometric diamond/square
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = baseRadius * 0.55f,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Core solid white focus point
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = center
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { 40 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "You are being engineered.",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.testTag("reality_check_headline")
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Algorithms are designed to harvest your attention. This tool is your counter-measure to take your mind back.",
                    color = Color(0xFFCCCCCC),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 25.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("reality_check_body")
                )
            }
        }
    }
}

/**
 * Screen 2: The Arsenal
 * Headline: "Friction is Freedom."
 * Body: "We intercept your doomscroll, detect restricted keywords, and force you to confront your own impulses. No easy bypasses. No negotiating with weakness."
 */
@Composable
private fun TheArsenalScreen(isActive: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Minimalist geometric shield symbol
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color(0xFF111111), CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { 40 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Friction is Freedom.",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.testTag("the_arsenal_headline")
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "We intercept your doomscroll, detect restricted keywords, and force you to confront your own impulses. No easy bypasses. No negotiating with weakness.",
                    color = Color(0xFFCCCCCC),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 25.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("the_arsenal_body")
                )
            }
        }
    }
}

/**
 * Screen 3: The Interactive Commitment
 * Headline: "What are you reclaiming today?"
 * Action: Three large selectable buttons: My Time, My Focus, or My Discipline.
 * Logic: Save this selection locally via Android DataStore.
 */
@Composable
private fun InteractiveCommitmentScreen(
    isActive: Boolean,
    currentSelection: String,
    onSelectCommitment: (String) -> Unit
) {
    val options = listOf("My Time", "My Focus", "My Discipline")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { 30 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "What are you reclaiming today?",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.testTag("commitment_headline")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Declare your primary intention. It will anchor every friction screen and intervention.",
                    color = Color(0xFFAAAAAA),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(36.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    options.forEach { option ->
                        val isSelected = option == currentSelection
                        Surface(
                            onClick = { onSelectCommitment(option) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color(0xFF1E1E1E) else Color(0xFF0F0F0F),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color.White else Color(0xFF262626)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("commitment_option_${option.lowercase().replace(" ", "_")}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp, horizontal = 22.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = option,
                                    color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                                    fontSize = 18.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    letterSpacing = 0.5.sp
                                )

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 4: The Transparency Pact (Google Play Prominent Disclosure)
 * Headline: "We need teeth to fight back."
 * Body:
 * - Usage Access: To detect the exact millisecond a restricted app is launched.
 * - Accessibility Service: To read screen text for blocked keywords and draw the full-screen intervention overlay.
 * Privacy Promise: "Everything operates strictly on your local device. We do not track or export your data."
 */
@Composable
private fun TransparencyPactScreen(
    isActive: Boolean,
    hasAccessibility: Boolean,
    hasUsageStats: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenUsageStats: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { 30 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "We need teeth to fight back.",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.testTag("transparency_headline")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "To dismantle habit loops and block compulsive bypasses, Focus Lock requires specific Android system permissions.",
                    color = Color(0xFFAAAAAA),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Permission 1: Usage Access
                TransparencyDisclosureCard(
                    title = "Usage Access",
                    disclosure = "To detect the exact millisecond a restricted app is launched.",
                    icon = Icons.Default.DataUsage,
                    isGranted = hasUsageStats,
                    actionLabel = if (hasUsageStats) "Enabled" else "Grant Usage Access",
                    onAction = onOpenUsageStats,
                    testTag = "disclosure_usage_access"
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Permission 2: Accessibility Service
                TransparencyDisclosureCard(
                    title = "Accessibility Service",
                    disclosure = "To read screen text for blocked keywords and draw the full-screen intervention overlay.",
                    icon = Icons.Default.Visibility,
                    isGranted = hasAccessibility,
                    actionLabel = if (hasAccessibility) "Enabled" else "Grant Accessibility",
                    onAction = onOpenAccessibility,
                    testTag = "disclosure_accessibility"
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Privacy Promise Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF141414),
                    border = BorderStroke(1.dp, Color(0xFF262626)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("privacy_promise_card")
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Privacy Promise",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Everything operates strictly on your local device. We do not track or export your data.",
                                color = Color(0xFFCCCCCC),
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransparencyDisclosureCard(
    title: String,
    disclosure: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F0F0F),
        border = BorderStroke(
            1.dp,
            if (isGranted) Color(0xFF2E7D32) else Color(0xFF333333)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) Color(0xFF81C784) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = Color(0xFF81C784),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = disclosure,
                color = Color(0xFFB0B0B0),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (!isGranted) {
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF1A1A1A),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF81C784),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Permission Configured",
                        color = Color(0xFF81C784),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
