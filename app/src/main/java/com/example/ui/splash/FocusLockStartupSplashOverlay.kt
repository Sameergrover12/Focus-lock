package com.example.ui.splash

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BaseThemeMode
import kotlin.math.roundToInt

/**
 * 0ms to 800ms high-framerate vector startup splash animation overlay.
 *
 * Runs once on cold start:
 * - 0ms–150ms: Squircle container scale-in (0.90f -> 1.0f)
 * - 50ms–360ms: Sequential left-to-right drawing of the "F" ribbons
 * - 240ms–560ms: Padlock appear & mechanical shackle lock-snap with overshoot
 * - 480ms–600ms: Squircle border glow alpha pulse (0.25f -> 0.60f -> 0.30f)
 * - 100ms–450ms: Bottom "Focus Lock" title reveal
 * - 620ms–800ms: Smooth cross-dissolve (1.0f -> 0.0f) into the already-composed Home screen
 */
@Composable
fun FocusLockStartupSplashOverlay(
    baseThemeMode: BaseThemeMode,
    onAnimationComplete: () -> Unit
) {
    val isSystemInDark = isSystemInDarkTheme()
    val isDark = baseThemeMode.resolve(isSystemInDark) != BaseThemeMode.LIGHT

    val screenBgColor = if (isDark) Color(0xFF0E1414) else Color(0xFFF4F7F6)
    val titleTextColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF0F172A)

    val timeMillis = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        timeMillis.animateTo(
            targetValue = 800f,
            animationSpec = tween(durationMillis = 800, easing = LinearEasing)
        )
        onAnimationComplete()
    }

    val t = timeMillis.value
    val density = LocalDensity.current

    // Phase 1 (0ms–150ms): Squircle container scale-in
    val squircleScaleIn = 0.90f + 0.10f * calcProgress(t, 0f, 150f, FastOutSlowInEasing)

    // Phase 2 (50ms–360ms): Sequential sweep of "F" ribbons
    val topRibbonProgress = calcProgress(t, 50f, 300f, FastOutSlowInEasing)
    val middleRibbonProgress = calcProgress(t, 130f, 360f, FastOutSlowInEasing)

    // Phase 3 (240ms–560ms): Padlock scale, alpha & mechanical shackle drop
    val padlockProgress = calcProgress(t, 240f, 380f, FastOutSlowInEasing)
    val padlockScale = 0.75f + 0.25f * padlockProgress
    val padlockAlpha = padlockProgress

    // Shackle drop with mechanical snap overshoot (380ms–540ms)
    val shackleFraction = calcProgress(t, 380f, 540f, LinearEasing)
    val shackleSnap = when {
        shackleFraction <= 0f -> 0f
        shackleFraction >= 1f -> 1f
        else -> {
            val x = shackleFraction - 1f
            x * x * ((2.2f + 1f) * x + 2.2f) + 1f
        }
    }
    val maxShackleOffsetPx = with(density) { 7.dp.toPx() }
    val shackleOffsetYPx = -maxShackleOffsetPx * (1f - shackleSnap)

    // Lock confirmation glow (480ms–600ms): 0.25f -> 0.60f -> 0.30f
    val borderGlowAlpha = when {
        t < 480f -> 0.25f
        t < 540f -> 0.25f + 0.35f * calcProgress(t, 480f, 540f, FastOutSlowInEasing)
        t < 600f -> 0.60f - 0.30f * calcProgress(t, 540f, 600f, FastOutSlowInEasing)
        else -> 0.30f
    }

    // Phase 4 (100ms–450ms): Bottom "Focus Lock" reveal
    val nameProgress = calcProgress(t, 100f, 450f, FastOutSlowInEasing)
    val nameAlpha = nameProgress
    val nameOffsetPx = with(density) { (8.dp * (1f - nameProgress)).toPx() }

    // Phase 5 (620ms–800ms): Cross-dissolve into the Home screen
    val fadeOutProgress = calcProgress(t, 620f, 800f, FastOutLinearInEasing)
    val overlayAlpha = (1f - fadeOutProgress).coerceIn(0f, 1f)
    val squircleScaleOut = 1.0f + 0.03f * fadeOutProgress
    val totalSquircleScale = squircleScaleIn * squircleScaleOut

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(overlayAlpha)
            .background(screenBgColor),
        contentAlignment = Alignment.Center
    ) {
        // Centered 108.dp x 108.dp Squircle Icon
        val squircleShape = RoundedCornerShape(26.dp)
        val shadowModifier = if (!isDark) {
            Modifier.shadow(
                elevation = 16.dp,
                shape = squircleShape,
                spotColor = Color(0x33000000),
                ambientColor = Color(0x1F000000)
            )
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .size(108.dp)
                .scale(totalSquircleScale)
                .then(shadowModifier)
                .clip(squircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF0A221E), Color(0xFF051311))
                    )
                )
                .border(
                    width = 1.5.dp,
                    color = Color(0xFF14B8A6).copy(alpha = borderGlowAlpha),
                    shape = squircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val r = w * 0.06f

                val fRibbonBrush = Brush.linearGradient(
                    colors = listOf(Color(0xFF6EE7B7), Color(0xFF0D9488)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )

                // 1. Upper "F" Stem & Top Curved Bar (with sweep clipRect)
                if (topRibbonProgress > 0f) {
                    val topPath = Path().apply {
                        moveTo(w * 0.22f + r, h * 0.22f)
                        lineTo(w * 0.72f - r, h * 0.22f)
                        arcTo(
                            rect = Rect(w * 0.72f - 2 * r, h * 0.22f, w * 0.72f, h * 0.22f + 2 * r),
                            startAngleDegrees = -90f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false
                        )
                        lineTo(w * 0.72f, h * 0.35f - r)
                        arcTo(
                            rect = Rect(w * 0.72f - 2 * r, h * 0.35f - 2 * r, w * 0.72f, h * 0.35f),
                            startAngleDegrees = 0f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false
                        )
                        lineTo(w * 0.35f + r, h * 0.35f)
                        arcTo(
                            rect = Rect(w * 0.35f, h * 0.35f, w * 0.35f + 2 * r, h * 0.35f + 2 * r),
                            startAngleDegrees = -90f,
                            sweepAngleDegrees = -90f,
                            forceMoveTo = false
                        )
                        lineTo(w * 0.35f, h * 0.78f - r)
                        arcTo(
                            rect = Rect(w * 0.35f - 2 * r, h * 0.78f - 2 * r, w * 0.35f, h * 0.78f),
                            startAngleDegrees = 0f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false
                        )
                        lineTo(w * 0.22f + r, h * 0.78f)
                        arcTo(
                            rect = Rect(w * 0.22f, h * 0.78f - 2 * r, w * 0.22f + 2 * r, h * 0.78f),
                            startAngleDegrees = 90f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false
                        )
                        lineTo(w * 0.22f, h * 0.22f + r)
                        arcTo(
                            rect = Rect(w * 0.22f, h * 0.22f, w * 0.22f + 2 * r, h * 0.22f + 2 * r),
                            startAngleDegrees = 180f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false
                        )
                        close()
                    }

                    clipRect(
                        left = 0f,
                        top = 0f,
                        right = w * topRibbonProgress,
                        bottom = h
                    ) {
                        drawPath(path = topPath, brush = fRibbonBrush)
                    }
                }

                // 2. Middle "F" Curved Bar (sweeps right after top bar)
                if (middleRibbonProgress > 0f) {
                    val middlePath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = w * 0.22f,
                                top = h * 0.46f,
                                right = w * 0.58f,
                                bottom = h * 0.58f,
                                cornerRadius = CornerRadius(r, r)
                            )
                        )
                    }

                    clipRect(
                        left = 0f,
                        top = 0f,
                        right = w * middleRibbonProgress,
                        bottom = h
                    ) {
                        drawPath(path = middlePath, brush = fRibbonBrush)
                    }
                }

                // 3. Padlock in the bottom-right corner pocket
                if (padlockProgress > 0f) {
                    val padCenterX = w * 0.69f
                    val padCenterY = h * 0.66f
                    val bodyW = w * 0.25f
                    val bodyH = h * 0.20f
                    val bodyR = w * 0.04f

                    withTransform({
                        scale(
                            scaleX = padlockScale,
                            scaleY = padlockScale,
                            pivot = Offset(padCenterX, padCenterY)
                        )
                    }) {
                        // A. U-Shackle Arch (placed behind padlock body)
                        val shackleW = bodyW * 0.56f
                        val shackleH = bodyH * 0.62f
                        val shackleStrokePx = 3.2.dp.toPx()
                        val shackleLeft = padCenterX - shackleW / 2
                        val shackleTopBase = (padCenterY - bodyH / 2) - shackleH + (shackleStrokePx / 2)
                        val shackleTopEffective = shackleTopBase + shackleOffsetYPx

                        val shacklePath = Path().apply {
                            // Left leg
                            moveTo(shackleLeft + (shackleStrokePx / 2), padCenterY - bodyH / 2 + 2f)
                            lineTo(shackleLeft + (shackleStrokePx / 2), shackleTopEffective + shackleW / 2)
                            // Top semicircle arch
                            arcTo(
                                rect = Rect(
                                    left = shackleLeft + (shackleStrokePx / 2),
                                    top = shackleTopEffective,
                                    right = shackleLeft + shackleW - (shackleStrokePx / 2),
                                    bottom = shackleTopEffective + shackleW - shackleStrokePx
                                ),
                                startAngleDegrees = 180f,
                                sweepAngleDegrees = 180f,
                                forceMoveTo = false
                            )
                            // Right leg
                            lineTo(
                                shackleLeft + shackleW - (shackleStrokePx / 2),
                                padCenterY - bodyH / 2 + 2f
                            )
                        }

                        drawPath(
                            path = shacklePath,
                            color = Color(0xFFF8FAFC).copy(alpha = padlockAlpha),
                            style = Stroke(width = shackleStrokePx, cap = StrokeCap.Round)
                        )

                        // B. Padlock Body (white gradient rounded rectangle)
                        val bodyPath = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = padCenterX - bodyW / 2,
                                    top = padCenterY - bodyH / 2,
                                    right = padCenterX + bodyW / 2,
                                    bottom = padCenterY + bodyH / 2,
                                    cornerRadius = CornerRadius(bodyR, bodyR)
                                )
                            )
                        }

                        drawPath(
                            path = bodyPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFF8FAFC), Color(0xFFCBD5E1)),
                                startY = padCenterY - bodyH / 2,
                                endY = padCenterY + bodyH / 2
                            ),
                            alpha = padlockAlpha
                        )

                        // C. Centered Keyhole cutout
                        val keyholeCenterY = padCenterY - bodyH * 0.05f
                        val keyholeCircleRadius = bodyW * 0.10f

                        drawCircle(
                            color = Color(0xFF071A17).copy(alpha = padlockAlpha),
                            radius = keyholeCircleRadius,
                            center = Offset(padCenterX, keyholeCenterY)
                        )

                        val slotW = bodyW * 0.08f
                        val slotH = bodyH * 0.22f
                        drawRoundRect(
                            color = Color(0xFF071A17).copy(alpha = padlockAlpha),
                            topLeft = Offset(padCenterX - slotW / 2, keyholeCenterY),
                            size = androidx.compose.ui.geometry.Size(slotW, slotH),
                            cornerRadius = CornerRadius(slotW / 2, slotW / 2)
                        )
                    }
                }
            }
        }

        // Phase 4: Bottom App Name ("Focus Lock")
        Text(
            text = "Focus Lock",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            color = titleTextColor,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 52.dp)
                .offset { IntOffset(0, nameOffsetPx.roundToInt()) }
                .alpha(nameAlpha)
        )
    }
}

private fun calcProgress(t: Float, start: Float, end: Float, easing: Easing = FastOutSlowInEasing): Float {
    if (t <= start) return 0f
    if (t >= end) return 1f
    val fraction = ((t - start) / (end - start)).coerceIn(0f, 1f)
    return easing.transform(fraction)
}
