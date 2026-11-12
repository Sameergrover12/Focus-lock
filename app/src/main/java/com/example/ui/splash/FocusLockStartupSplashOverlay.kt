package com.example.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val SmoothSignatureEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Artifact-free, single-window 0ms–850ms startup animation overlay rendered at the root Box
 * of MainActivity with zIndex(999f).
 *
 * Renders @drawable/focus_lock_original_icon as a single, unified, 100% intact bitmap inside
 * the centered 112.dp squircle (RoundedCornerShape(26.dp)) without any regional clipRect cuts
 * or sub-rectangle translationY shifts:
 *
 * - Layer 1 (0ms–420ms): Intact emblem spring (0.90f -> 1.0f over 0ms..380ms with
 *   dampingRatio = 0.72f, stiffness = 380f) & feathered diagonal specular light sheen
 *   (60ms..450ms) sweeping from top-left across the "F" to the bottom-right padlock.
 * - Layer 2 (280ms–580ms): Precision padlock lock-in pulse at (cx = width * 0.665f,
 *   cy = height * 0.64f) with a crisp squircle micro-scale pulse (1.0f -> 1.035f -> 1.0f)
 *   and a soft radial mint-white glow ring expanding from 12.dp to 34.dp (alpha 0.45f -> 0f).
 * - Layer 3 (0ms–650ms): Rotating segmented outer HUD dial ticks, squircle border wave &
 *   shockwave ring, and bottom "FOCUS LOCK" + "ATTENTION SHIELD" typography.
 * - Layer 4 (630ms–850ms): Buttery cross-dissolve (alpha 1f -> 0f, scale 1.0f -> 1.04f)
 *   into the already-composed underlying screen.
 */
@Composable
fun FocusLockStartupSplashOverlay(
    isDarkTheme: Boolean,
    onAnimationFinished: () -> Unit
) {
    val screenBgColor = if (isDarkTheme) Color(0xFF0E1414) else Color(0xFFF4F7F6)
    val titleTextColor = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF0F172A)
    val subtitleTextColor = if (isDarkTheme) Color(0xFF6EE7B7) else Color(0xFF059669)

    val originalIcon = ImageBitmap.imageResource(id = R.drawable.focus_lock_original_icon)
    val timeMillis = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Start immediately at 0ms without any blank pause
        timeMillis.animateTo(
            targetValue = 850f,
            animationSpec = tween(durationMillis = 850, easing = LinearEasing)
        )
        onAnimationFinished()
    }

    val t = timeMillis.value
    val density = LocalDensity.current

    // Layer 1A (0ms to 380ms): Unified 112.dp squircle gentle spring scale (0.90f -> 1.0f)
    // using dampingRatio = 0.72f, stiffness = 380f
    val springFraction = calcProgress(t, 0f, 380f, LinearEasing)
    val springValue = when {
        springFraction <= 0f -> 0f
        springFraction >= 1f -> 1f
        else -> {
            val durationSec = 0.38f
            val omega0 = sqrt(380f) // ~19.49 rad/s
            val zeta = 0.72f
            val omegaD = omega0 * sqrt(1f - zeta * zeta)
            val elapsedSec = springFraction * durationSec
            1f - exp(-zeta * omega0 * elapsedSec) * cos(omegaD * elapsedSec)
        }
    }
    val baseEmblemScale = 0.90f + 0.10f * springValue

    // Layer 1B (60ms to 450ms): Feathered diagonal specular light sheen across the intact icon
    val sheenProgress = calcProgress(t, 60f, 450f, SmoothSignatureEasing)

    // Layer 2 (280ms to 580ms): Precision Padlock Lock-In Pulse (1.0f -> 1.035f -> 1.0f)
    // and radial mint-white glow ring expanding from 12.dp to 34.dp (alpha 0.45f -> 0f)
    val lockPulseProgress = calcProgress(t, 280f, 580f, FastOutSlowInEasing)
    val lockScaleBump = if (t in 280f..580f) {
        val raw = ((t - 280f) / 300f).coerceIn(0f, 1f)
        sin(raw * PI.toFloat()) * 0.035f
    } else {
        0f
    }

    // Layer 3 (0ms to 650ms): Outer HUD dial, squircle border wave & shockwave ring, typography
    val hudAppear = calcProgress(t, 0f, 360f, FastOutSlowInEasing)
    val hudFade = 1f - calcProgress(t, 600f, 820f, LinearEasing)
    val hudAlpha = (hudAppear * hudFade).coerceIn(0f, 1f)
    val hudRotationDeg = SmoothSignatureEasing.transform((t / 850f).coerceIn(0f, 1f)) * 42f

    val shockwaveProgress = calcProgress(t, 280f, 650f, FastOutSlowInEasing)
    val borderWaveSweep = calcProgress(t, 80f, 560f, FastOutSlowInEasing)

    val nameProgress = calcProgress(t, 60f, 480f, FastOutSlowInEasing)
    val subtitleProgress = calcProgress(t, 160f, 580f, FastOutSlowInEasing)
    val nameOffsetPx = with(density) { (8.dp * (1f - nameProgress)).toPx() }
    val dynamicLetterSpacing = (4.4f - 2.0f * nameProgress).sp

    // Layer 4 (630ms to 850ms): Buttery cross-dissolve into the app
    // (alpha 1f -> 0f, scale 1.0f -> 1.04f)
    val exitProgress = calcProgress(t, 630f, 850f, FastOutLinearInEasing)
    val overlayAlpha = (1f - exitProgress).coerceIn(0f, 1f)
    val totalEmblemScale = (baseEmblemScale + lockScaleBump) * (1.0f + 0.04f * exitProgress)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(999f)
            .alpha(overlayAlpha)
            .background(screenBgColor),
        contentAlignment = Alignment.Center
    ) {
        // Layer 3A: Outer Ambient Halo + Rotating Segmented HUD Dial + Squircle Shockwave Ring
        Canvas(
            modifier = Modifier.size(256.dp)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f

            // Soft radial emerald-mint ambient halo adapted to Dark / Light Mode
            val haloPeakAlpha = if (isDarkTheme) 0.18f else 0.12f
            val haloAlpha = (haloPeakAlpha * hudAppear * (1f - exitProgress * 0.5f)).coerceIn(0f, haloPeakAlpha)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF10B981).copy(alpha = haloAlpha),
                        Color(0xFF059669).copy(alpha = haloAlpha * 0.4f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius * 0.88f
                ),
                radius = maxRadius,
                center = center
            )

            // Rotating segmented outer HUD dial ticks
            if (hudAlpha > 0.01f) {
                val dialRadius = 78.dp.toPx() * (0.92f + 0.08f * hudAppear)
                val tickCount = 36
                val dialBaseColor = if (isDarkTheme) Color(0xFF34D399) else Color(0xFF059669)

                for (i in 0 until tickCount) {
                    val angleDeg = i * (360f / tickCount) + hudRotationDeg
                    val angleRad = (angleDeg * PI / 180.0).toFloat()
                    val isMajor = i % 3 == 0
                    val innerR = dialRadius - if (isMajor) 5.5.dp.toPx() else 2.8.dp.toPx()
                    val outerR = dialRadius
                    val tickAlpha = (if (isMajor) 0.34f else 0.15f) * hudAlpha

                    drawLine(
                        color = dialBaseColor.copy(alpha = tickAlpha),
                        start = Offset(
                            x = center.x + innerR * cos(angleRad),
                            y = center.y + innerR * sin(angleRad)
                        ),
                        end = Offset(
                            x = center.x + outerR * cos(angleRad),
                            y = center.y + outerR * sin(angleRad)
                        ),
                        strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Segmented HUD orbital arcs
                val arcRadius = 85.dp.toPx()
                val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
                val arcColor = if (isDarkTheme) Color(0xFF6EE7B7) else Color(0xFF10B981)
                drawArc(
                    color = arcColor.copy(alpha = 0.26f * hudAlpha),
                    startAngle = -40f - hudRotationDeg * 1.2f,
                    sweepAngle = 64f * hudAppear,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = arcColor.copy(alpha = 0.26f * hudAlpha),
                    startAngle = 140f - hudRotationDeg * 1.2f,
                    sweepAngle = 64f * hudAppear,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Expanding rounded-squircle perimeter shockwave ring triggered at padlock lock-in (280ms..650ms)
            if (shockwaveProgress in 0.01f..0.99f) {
                val waveHalfSize = 56.dp.toPx() + 42.dp.toPx() * shockwaveProgress
                val waveAlpha = (if (isDarkTheme) 0.48f else 0.36f) * (1f - shockwaveProgress)
                val cornerR = 26.dp.toPx() + 14.dp.toPx() * shockwaveProgress
                drawRoundRect(
                    color = Color(0xFF34D399).copy(alpha = waveAlpha),
                    topLeft = Offset(center.x - waveHalfSize, center.y - waveHalfSize),
                    size = Size(waveHalfSize * 2f, waveHalfSize * 2f),
                    cornerRadius = CornerRadius(cornerR, cornerR),
                    style = Stroke(width = (2.0f * (1f - shockwaveProgress * 0.5f)).dp.toPx())
                )
            }
        }

        // Centered 112.dp x 112.dp Unified Squircle Emblem (RoundedCornerShape(26.dp))
        val squircleShape = RoundedCornerShape(26.dp)
        val shadowModifier = if (!isDarkTheme) {
            Modifier.shadow(
                elevation = 14.dp,
                shape = squircleShape,
                spotColor = Color(0x38000000),
                ambientColor = Color(0x20000000)
            )
        } else {
            Modifier.shadow(
                elevation = 18.dp,
                shape = squircleShape,
                spotColor = Color(0x5510B981),
                ambientColor = Color(0x28059669)
            )
        }

        Box(
            modifier = Modifier
                .size(112.dp)
                .scale(totalEmblemScale)
                .then(shadowModifier)
                .clip(squircleShape)
                .background(Color(0xFF061614)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val bmpW = originalIcon.width
                val bmpH = originalIcon.height

                // Crop outer dark margin so the full neon-bordered squircle fills the 112.dp container cleanly
                val padInsetRatio = 0.092f
                val srcCropX = (bmpW * padInsetRatio).roundToInt()
                val srcCropY = (bmpH * padInsetRatio).roundToInt()
                val srcCropW = (bmpW * (1f - 2 * padInsetRatio)).roundToInt()
                val srcCropH = (bmpH * (1f - 2 * padInsetRatio)).roundToInt()

                // 1. Render @drawable/focus_lock_original_icon as a SINGLE, UNIFIED, 100% INTACT image
                // (No regional clipRect cuts and no sub-rectangle translationY shifts)
                drawImage(
                    image = originalIcon,
                    srcOffset = IntOffset(srcCropX, srcCropY),
                    srcSize = IntSize(srcCropW, srcCropH),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(w.roundToInt(), h.roundToInt())
                )

                // 2. Layer 1B: Soft, wide diagonal specular light sheen sweeping from top-left (0, 0)
                // across the "F" down to the bottom-right padlock (width, height) between 60ms and 450ms
                if (sheenProgress in 0.01f..0.99f) {
                    val bandHalfSpan = 0.42f
                    val travel = -bandHalfSpan + (1f + 2f * bandHalfSpan) * sheenProgress
                    val startFactor = travel - bandHalfSpan
                    val endFactor = travel + bandHalfSpan

                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF6EE7B7).copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.28f),
                                Color.Transparent
                            ),
                            start = Offset(w * startFactor, h * startFactor),
                            end = Offset(w * endFactor, h * endFactor)
                        ),
                        topLeft = Offset.Zero,
                        size = size
                    )
                }

                // 3. Layer 2: Precision Padlock Lock-In Radial Glow Ring (280ms to 580ms)
                // Centered at (cx = size.width * 0.665f, cy = size.height * 0.64f), expanding
                // subtly from radius = 12.dp to 34.dp while fading out (alpha = 0.45f -> 0f)
                if (t in 280f..580f) {
                    val padlockCenter = Offset(w * 0.665f, h * 0.64f)
                    val minRadiusPx = 12.dp.toPx()
                    val maxRadiusPx = 34.dp.toPx()
                    val currentRadiusPx = minRadiusPx + (maxRadiusPx - minRadiusPx) * lockPulseProgress
                    val ringAlpha = (0.45f * (1f - lockPulseProgress)).coerceIn(0f, 0.45f)

                    // Soft radial mint-white aura + crisp expanding ring around the padlock
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = ringAlpha * 0.55f),
                                Color(0xFF6EE7B7).copy(alpha = ringAlpha * 0.85f),
                                Color.Transparent
                            ),
                            center = padlockCenter,
                            radius = currentRadiusPx
                        ),
                        radius = currentRadiusPx,
                        center = padlockCenter
                    )
                    drawCircle(
                        color = Color(0xFFA7F3D0).copy(alpha = ringAlpha),
                        radius = currentRadiusPx * 0.82f,
                        center = padlockCenter,
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                }

                // 4. Layer 3B: Subtle Emerald-Mint Squircle Perimeter Border Wave (80ms to 560ms)
                if (borderWaveSweep > 0f) {
                    val strokeWidthPx = 2.2.dp.toPx()
                    val cornerRadiusPx = 26.dp.toPx()
                    val insetPx = strokeWidthPx / 2f

                    val borderPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(insetPx, insetPx, w - insetPx, h - insetPx),
                                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                            )
                        )
                    }

                    val pathMeasure = PathMeasure()
                    pathMeasure.setPath(borderPath, false)
                    val totalLen = pathMeasure.length
                    val segment = Path()
                    pathMeasure.getSegment(0f, totalLen * borderWaveSweep, segment, true)

                    val borderAlpha = if (t <= 480f) {
                        0.85f
                    } else {
                        (0.85f * (1f - calcProgress(t, 480f, 680f, LinearEasing))).coerceIn(0f, 0.85f)
                    }
                    if (borderAlpha > 0f) {
                        drawPath(
                            path = segment,
                            color = Color(0xFF4BF5C2).copy(alpha = borderAlpha),
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }

        // Layer 3C: Bottom "FOCUS LOCK" + "ATTENTION SHIELD" Typography (0ms to 650ms)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .offset { IntOffset(0, nameOffsetPx.roundToInt()) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "FOCUS LOCK",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = dynamicLetterSpacing,
                color = titleTextColor,
                modifier = Modifier.alpha(nameProgress)
            )

            Spacer(modifier = Modifier.height(6.dp))

            val lineFraction = calcProgress(t, 120f, 540f, FastOutSlowInEasing)
            Box(
                modifier = Modifier
                    .width((104.dp * lineFraction).coerceAtLeast(1.dp))
                    .height(1.6.dp)
                    .alpha(nameProgress)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF34D399),
                                Color(0xFF6EE7B7),
                                Color(0xFF34D399),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(1.dp)
                    )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "ATTENTION SHIELD",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.6.sp,
                color = subtitleTextColor,
                modifier = Modifier.alpha(subtitleProgress * 0.85f)
            )
        }
    }
}

private fun calcProgress(
    t: Float,
    start: Float,
    end: Float,
    easing: Easing = FastOutSlowInEasing
): Float {
    if (t <= start) return 0f
    if (t >= end) return 1f
    val fraction = ((t - start) / (end - start)).coerceIn(0f, 1f)
    return easing.transform(fraction)
}
