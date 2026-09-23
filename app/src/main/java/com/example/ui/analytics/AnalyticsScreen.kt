package com.example.ui.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Android
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.DailyScreenTime
import com.example.data.local.entity.DailyUsageLog
import com.example.ui.viewmodel.FocusViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Analytics Dashboard: Charts screen time and app usage data in Jetpack Compose,
 * visually demonstrating which applications are draining user focus.
 * Designed with deep AMOLED blacks, stark white typography, and mental discipline aesthetics.
 */
@Composable
fun AnalyticsScreen(
    viewModel: FocusViewModel,
    onNavigateToApps: () -> Unit = {}
) {
    val totalMinutesToday by viewModel.totalMinutesUsedToday.collectAsStateWithLifecycle()
    val todayUsageLogs by viewModel.todayUsageLogs.collectAsStateWithLifecycle()
    val recentScreenTimes by viewModel.recentDailyScreenTimes.collectAsStateWithLifecycle()
    val reclaimedCommitment by viewModel.reclaimedCommitment.collectAsStateWithLifecycle()
    val blockedApps by viewModel.blockedApps.collectAsStateWithLifecycle()

    val totalMins = (totalMinutesToday ?: 0).coerceAtLeast(0)
    val blockedPackageSet = remember(blockedApps) { blockedApps.map { it.packageName }.toSet() }

    // Sort usage logs descending by minutesUsed
    val sortedLogs = remember(todayUsageLogs) {
        todayUsageLogs.sortedByDescending { it.minutesUsed }
    }

    val topDrainApps = remember(sortedLogs) {
        sortedLogs.take(5)
    }

    val totalLoggedAppMinutes = remember(sortedLogs) {
        sortedLogs.sumOf { it.minutesUsed }.coerceAtLeast(1)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .testTag("analytics_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Focus Drain Header
        item {
            AnalyticsHeroCard(
                totalMinutes = totalMins,
                reclaimedCommitment = reclaimedCommitment
            )
        }

        // 2. 7-Day Screen Time Bar Chart
        item {
            ScreenTimeBarChartCard(
                recentScreenTimes = recentScreenTimes,
                todayMinutes = totalMins
            )
        }

        // 3. Proportional Focus Drain Breakdown Bar
        if (sortedLogs.isNotEmpty()) {
            item {
                FocusDrainSpectrumCard(
                    topApps = topDrainApps,
                    totalMinutes = totalLoggedAppMinutes
                )
            }
        }

        // 4. Ranked Focus Drain Apps
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Top Attention Drainers",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                if (sortedLogs.isNotEmpty()) {
                    Text(
                        text = "${sortedLogs.size} tracked apps",
                        color = Color(0xFF888888),
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (sortedLogs.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F0F0F),
                    border = BorderStroke(1.dp, Color(0xFF262626)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = null,
                            tint = Color(0xFF555555),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Usage Logged Yet Today",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "As you use apps throughout the day, the engine will measure exact foreground dwell time.",
                            color = Color(0xFF888888),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            itemsIndexed(sortedLogs) { index, log ->
                val isBlocked = blockedPackageSet.contains(log.packageName)
                val percentOfTotal = ((log.minutesUsed.toFloat() / totalLoggedAppMinutes) * 100f).toInt()

                AppDrainItemCard(
                    rank = index + 1,
                    log = log,
                    percentOfTotal = percentOfTotal,
                    isBlocked = isBlocked,
                    onRestrictClick = {
                        if (!isBlocked) {
                            viewModel.blockApp(log.packageName, log.appName)
                        } else {
                            viewModel.unblockApp(log.packageName)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AnalyticsHeroCard(
    totalMinutes: Int,
    reclaimedCommitment: String
) {
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60
    val timeDisplay = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

    // Assume 16 waking hours (960 min)
    val percentOfWakingDay = ((totalMinutes.toFloat() / 960f) * 100f).coerceIn(0f, 100f).toInt()

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0F0F0F),
        border = BorderStroke(1.dp, Color(0xFF262626)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analytics_hero_card")
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TOTAL SCREEN DWELL",
                        color = Color(0xFFAAAAAA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Text(
                        text = "Reclaiming: $reclaimedCommitment",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = timeDisplay,
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )

                Text(
                    text = "$percentOfWakingDay% of daylight hours",
                    color = if (percentOfWakingDay > 25) Color(0xFFEF9A9A) else Color(0xFF888888),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { (percentOfWakingDay / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (percentOfWakingDay > 30) Color(0xFFEF5350) else Color.White,
                trackColor = Color(0xFF222222),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

/**
 * 7-Day Screen Time Bar Chart rendered directly using Compose Canvas
 */
@Composable
private fun ScreenTimeBarChartCard(
    recentScreenTimes: List<DailyScreenTime>,
    todayMinutes: Int
) {
    // Generate data points for the past 7 days
    val calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dayLabelFormat = SimpleDateFormat("EEE", Locale.getDefault())

    val past7Days = remember(recentScreenTimes, todayMinutes) {
        val list = mutableListOf<DayBarData>()
        val screenTimeMap = recentScreenTimes.associate { it.date to it.screenOnMinutes }

        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = dateFormat.format(cal.time)
            val dayLabel = dayLabelFormat.format(cal.time).uppercase()
            val mins = if (i == 0) todayMinutes else (screenTimeMap[dateStr] ?: 0)
            list.add(DayBarData(label = dayLabel, date = dateStr, minutes = mins, isToday = (i == 0)))
        }
        list
    }

    val maxMinutes = remember(past7Days) {
        past7Days.maxOfOrNull { it.minutes }?.coerceAtLeast(60) ?: 60
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0F0F0F),
        border = BorderStroke(1.dp, Color(0xFF262626)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("screen_time_chart_card")
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "7-Day Usage History",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                val avgMinutes = past7Days.map { it.minutes }.average().toInt()
                val avgHours = avgMinutes / 60
                val avgM = avgMinutes % 60
                Text(
                    text = "Avg: ${if (avgHours > 0) "${avgHours}h " else ""}${avgM}m/day",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bar Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val bottomPadding = 24.dp.toPx()
                    val chartHeight = canvasHeight - bottomPadding

                    val barCount = past7Days.size
                    val slotWidth = canvasWidth / barCount
                    val barWidth = slotWidth * 0.42f

                    // Draw baseline
                    drawLine(
                        color = Color(0xFF222222),
                        start = Offset(0f, chartHeight),
                        end = Offset(canvasWidth, chartHeight),
                        strokeWidth = 1.dp.toPx()
                    )

                    past7Days.forEachIndexed { index, day ->
                        val ratio = (day.minutes.toFloat() / maxMinutes.toFloat()).coerceIn(0.04f, 1f)
                        val barHeight = chartHeight * ratio
                        val xOffset = index * slotWidth + (slotWidth - barWidth) / 2f
                        val yOffset = chartHeight - barHeight

                        val barColor = if (day.isToday) Color.White else Color(0xFF444444)

                        // Draw bar pill
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(xOffset, yOffset),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }
                }

                // Row of labels below canvas
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    past7Days.forEach { day ->
                        Text(
                            text = day.label,
                            color = if (day.isToday) Color.White else Color(0xFF666666),
                            fontSize = 10.sp,
                            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private data class DayBarData(
    val label: String,
    val date: String,
    val minutes: Int,
    val isToday: Boolean
)

/**
 * Focus Drain Spectrum: Segmented proportion of top focus drainers
 */
@Composable
private fun FocusDrainSpectrumCard(
    topApps: List<DailyUsageLog>,
    totalMinutes: Int
) {
    val colors = listOf(
        Color(0xFFE57373), // Red / Highest drain
        Color(0xFFFFB74D), // Orange
        Color(0xFF81C784), // Green
        Color(0xFF64B5F6), // Blue
        Color(0xFFBA68C8)  // Purple
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0F0F0F),
        border = BorderStroke(1.dp, Color(0xFF262626)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Focus Drain Distribution",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Share of screen time absorbed by primary applications:",
                color = Color(0xFF888888),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Multi-colored proportional bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(5.dp))
            ) {
                topApps.forEachIndexed { idx, app ->
                    val weight = (app.minutesUsed.toFloat() / totalMinutes).coerceIn(0.01f, 1f)
                    val color = colors.getOrElse(idx) { Color.Gray }
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .height(10.dp)
                            .background(color)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                topApps.take(3).forEachIndexed { idx, app ->
                    val color = colors.getOrElse(idx) { Color.Gray }
                    val percent = ((app.minutesUsed.toFloat() / totalMinutes) * 100f).toInt()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(color, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "${app.appName} ($percent%)",
                            color = Color(0xFFCCCCCC),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ranked App Drain Item Card
 */
@Composable
private fun AppDrainItemCard(
    rank: Int,
    log: DailyUsageLog,
    percentOfTotal: Int,
    isBlocked: Boolean,
    onRestrictClick: () -> Unit
) {
    val hours = log.minutesUsed / 60
    val mins = log.minutesUsed % 60
    val durationText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

    val severityColor = when {
        percentOfTotal >= 35 -> Color(0xFFEF5350)
        percentOfTotal >= 20 -> Color(0xFFFFB74D)
        else -> Color(0xFF888888)
    }

    val severityLabel = when {
        percentOfTotal >= 35 -> "Severe Drain"
        percentOfTotal >= 20 -> "Moderate Drain"
        else -> "Minor Drain"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F0F0F),
        border = BorderStroke(1.dp, if (isBlocked) Color(0xFF442222) else Color(0xFF222222)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_drain_card_${log.packageName}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rank number
                Text(
                    text = "#$rank",
                    color = Color(0xFF666666),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(28.dp)
                )

                // App Icon
                val cachedBitmap = remember(log.iconBase64) {
                    decodeBase64ToImageBitmap(log.iconBase64)
                }
                if (cachedBitmap != null) {
                    Image(
                        bitmap = cachedBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E1E1E),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.appName.ifBlank { log.packageName },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = severityColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = severityLabel,
                                color = severityColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$percentOfTotal% of daily focus",
                            color = Color(0xFF888888),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = durationText,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        onClick = onRestrictClick,
                        shape = RoundedCornerShape(12.dp),
                        color = if (isBlocked) Color(0xFF3B1818) else Color(0xFF1E1E1E),
                        border = BorderStroke(1.dp, if (isBlocked) Color(0xFFEF5350) else Color(0xFF333333)),
                        modifier = Modifier.testTag("restrict_app_${log.packageName}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isBlocked) Icons.Default.Shield else Icons.Default.Block,
                                contentDescription = null,
                                tint = if (isBlocked) Color(0xFFEF5350) else Color(0xFFCCCCCC),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isBlocked) "Blocked" else "Restrict",
                                color = if (isBlocked) Color(0xFFEF5350) else Color(0xFFCCCCCC),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dwell proportion bar
            LinearProgressIndicator(
                progress = { (percentOfTotal / 100f).coerceIn(0.02f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = severityColor,
                trackColor = Color(0xFF1C1C1C),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

private val analyticsBase64Cache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(100)

private fun decodeBase64ToImageBitmap(base64: String?): androidx.compose.ui.graphics.ImageBitmap? {
    if (base64.isNullOrBlank()) return null
    val cached = analyticsBase64Cache.get(base64)
    if (cached != null) return cached
    return try {
        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val imageBitmap = bitmap?.asImageBitmap()
        if (imageBitmap != null) {
            analyticsBase64Cache.put(base64, imageBitmap)
        }
        imageBitmap
    } catch (e: Exception) {
        null
    }
}

