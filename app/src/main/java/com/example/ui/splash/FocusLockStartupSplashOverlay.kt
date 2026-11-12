package com.example.ui.splash

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Classy, cinematic 0ms–1600ms (0.6x paced) startup animation overlay rendered at the root Box
 * of MainActivity with zIndex(999f).
 *
 * Showcases the full original Focus Lock logo including its glowing green neon border:
 * - Stage 1 (0ms–650ms): 3D perspective vault awakening (scale 0.86f -> 1.0f, subtle 3D tilt
 *   settling to 0°), rotating precision telemetry dial behind the icon, and a luminous mint
 *   laser scan sweeping the 3D "F" from left to right.
 * - Stage 2 (360ms–1120ms): Bottom-right white padlock drops in with a damped harmonic spring
 *   bounce (-12.dp -> 0.dp). At lock-in (~700ms), an emerald shockwave ripples outward while
 *   twin neon emerald-mint light beams trace the squircle's glowing green neon border and a
 *   diagonal glass reflection sheen glides across the icon.
 * - Stage 3 (220ms–1080ms): Bottom "FOCUS LOCK" title & "ATTENTION SHIELD ENGAGED" subtitle
 *   reveal with smooth letter-spacing condensation and a center-expanding neon laser underline.
 * - Stage 4 (1300ms–1600ms): Smooth 300ms cross-dissolve exit into the underlying screen.
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
        // Wait for the window and initial frame to settle past the OS launcher open transition
        withFrameNanos { }
        withFrameNanos { }
        delay(80)

        timeMillis.animateTo(
            targetValue = 1600f,
            animationSpec = tween(durationMillis = 1600, easing = LinearEasing)
        )
        onAnimationFinished()
    }

    val t = timeMillis.value
    val density = LocalDensity.current

    // Stage 1 (0ms to 650ms): 3D Vault Awakening & "F" Laser Sweep (0.18f -> 1.0f)
    val stage1Progress = calcProgress(t, 0f, 650f, FastOutSlowInEasing)
    val fClipProgress = 0.18f + 0.82f * stage1Progress
    val introScale = 0.86f + 0.14f * stage1Progress
    val tiltX = 12f * (1f - stage1Progress)
    val tiltY = -10f * (1f - stage1Progress)

    // Stage 2 (360ms to 1120ms): Padlock Spring Drop-In & Lock-Click Pulse
    val lockAlpha = calcProgress(t, 360f, 560f, FastOutSlowInEasing)
    val lockFraction = calcProgress(t, 360f, 1020f, LinearEasing)
    val lockSpring = when {
        lockFraction <= 0f -> 0f
        lockFraction >= 1f -> 1f
        else -> {
            val durationSec = 0.56f
            val omega0 = sqrt(620f)
            val zeta = 0.50f
            val omegaD = omega0 * sqrt(1f - zeta * zeta)
            val elapsedSec = lockFraction * durationSec
            1f - exp(-zeta * omega0 * elapsedSec) * cos(omegaD * elapsedSec)
        }
    }
    val lockOffsetYPx = with(density) { (-12.dp.toPx()) * (1f - lockSpring) }

    // Lock-click impact bump around 680ms–920ms when the padlock snaps shut
    val lockSnapPulse = if (t in 660f..940f) {
        val p = (t - 660f) / 280f
        sin(p * PI.toFloat()) * 0.032f
    } else {
        0f
    }

    // Shockwave ring expanding from the squircle when padlock clicks shut (650ms to 1250ms)
    val shockwaveProgress = calcProgress(t, 650f, 1250f, FastOutSlowInEasing)
    val shockwave2Progress = calcProgress(t, 760f, 1340f, FastOutSlowInEasing)

    // Twin neon border charge trace (380ms to 1120ms) & glass reflection sheen (700ms to 1220ms)
    val borderGlowSweep = calcProgress(t, 380f, 1120f, FastOutSlowInEasing)
    val sheenProgress = calcProgress(t, 700f, 1220f, FastOutSlowInEasing)

    // Precision telemetry ring rotation & alpha
    val dialAppear = calcProgress(t, 0f, 500f, FastOutSlowInEasing)
    val dialFade = 1f - calcProgress(t, 1220f, 1550f, LinearEasing)
    val dialAlpha = (dialAppear * dialFade).coerceIn(0f, 1f)
    val dialRotationDeg = (t / 1600f) * 54f

    // Stage 3 (220ms to 1080ms): Classy Typography & Neon Underline Reveal
    val nameProgress = calcProgress(t, 220f, 880f, FastOutSlowInEasing)
    val subtitleProgress = calcProgress(t, 480f, 1080f, FastOutSlowInEasing)
    val nameOffsetPx = with(density) { (10.dp * (1f - nameProgress)).toPx() }
    val dynamicLetterSpacing = (5.2f - 2.8f * nameProgress).sp

    // Stage 4 (1300ms to 1600ms): Smooth 300ms Cross-Dissolve Exit
    val exitProgress = calcProgress(t, 1300f, 1600f, FastOutLinearInEasing)
    val overlayAlpha = (1f - exitProgress).coerceIn(0f, 1f)
    val totalSquircleScale = introScale + lockSnapPulse + (0.05f * exitProgress)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(999f)
            .alpha(overlayAlpha)
            .background(screenBgColor),
        contentAlignment = Alignment.Center
    ) {
        // Outer Ambient Halo + Precision Telemetry Ring + Dual Lock-Click Shockwaves
        Canvas(
            modifier = Modifier.size(270.dp)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f

            // 1. Soft breathing radial emerald-mint ambient aura behind the icon
            val auraAlpha = (0.20f * stage1Progress * (1f - exitProgress * 0.5f)).coerceIn(0f, 0.25f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF10B981).copy(alpha = auraAlpha),
                        Color(0xFF059669).copy(alpha = auraAlpha * 0.45f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius * (0.85f + 0.12f * sin(t / 350f))
                ),
                radius = maxRadius,
                center = center
            )

            // 2. Rotating Precision Vault Telemetry Dial (architectural degree ticks)
            if (dialAlpha > 0.01f) {
                val dialRadius = 86.dp.toPx() * (0.90f + 0.10f * dialAppear)
                val tickCount = 36
                for (i in 0 until tickCount) {
                    val angleDeg = i * (360f / tickCount) + dialRotationDeg
                    val angleRad = (angleDeg * PI / 180.0).toFloat()
                    val isMajor = i % 3 == 0
                    val innerR = dialRadius - if (isMajor) 6.dp.toPx() else 3.dp.toPx()
                    val outerR = dialRadius
                    val tickAlpha = (if (isMajor) 0.36f else 0.16f) * dialAlpha

                    drawLine(
                        color = Color(0xFF34D399).copy(alpha = tickAlpha),
                        start = Offset(
                            x = center.x + innerR * cos(angleRad),
                            y = center.y + innerR * sin(angleRad)
                        ),
                        end = Offset(
                            x = center.x + outerR * cos(angleRad),
                            y = center.y + outerR * sin(angleRad)
                        ),
                        strokeWidth = if (isMajor) 1.6.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Subtle segmented orbital arc around the telemetry ring
                val arcRadius = 94.dp.toPx()
                val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
                drawArc(
                    color = Color(0xFF6EE7B7).copy(alpha = 0.28f * dialAlpha),
                    startAngle = -45f - dialRotationDeg * 1.3f,
                    sweepAngle = 70f * stage1Progress,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFF6EE7B7).copy(alpha = 0.28f * dialAlpha),
                    startAngle = 135f - dialRotationDeg * 1.3f,
                    sweepAngle = 70f * stage1Progress,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 3. Primary & Secondary Emerald Shockwave Ripples when padlock clicks shut
            if (shockwaveProgress in 0.01f..0.99f) {
                val waveRadius = 64.dp.toPx() + 58.dp.toPx() * shockwaveProgress
                val waveAlpha = 0.55f * (1f - shockwaveProgress)
                val cornerR = (28.dp.toPx() + 18.dp.toPx() * shockwaveProgress)
                drawRoundRect(
                    color = Color(0xFF34D399).copy(alpha = waveAlpha),
                    topLeft = Offset(center.x - waveRadius, center.y - waveRadius),
                    size = Size(waveRadius * 2f, waveRadius * 2f),
                    cornerRadius = CornerRadius(cornerR, cornerR),
                    style = Stroke(width = (2.2f * (1f - shockwaveProgress * 0.5f)).dp.toPx())
                )
            }
            if (shockwave2Progress in 0.01f..0.99f) {
                val wave2Radius = 64.dp.toPx() + 44.dp.toPx() * shockwave2Progress
                val wave2Alpha = 0.35f * (1f - shockwave2Progress)
                val cornerR2 = (28.dp.toPx() + 14.dp.toPx() * shockwave2Progress)
                drawRoundRect(
                    color = Color(0xFF6EE7B7).copy(alpha = wave2Alpha),
                    topLeft = Offset(center.x - wave2Radius, center.y - wave2Radius),
                    size = Size(wave2Radius * 2f, wave2Radius * 2f),
                    cornerRadius = CornerRadius(cornerR2, cornerR2),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }

        // Centered 124.dp x 124.dp Squircle Icon Container preserving the FULL green neon border
        val squircleShape = RoundedCornerShape(28.dp)
        val shadowModifier = if (!isDarkTheme) {
            Modifier.shadow(
                elevation = 16.dp,
                shape = squircleShape,
                spotColor = Color(0x40000000),
                ambientColor = Color(0x26000000)
            )
        } else {
            Modifier.shadow(
                elevation = 20.dp,
                shape = squircleShape,
                spotColor = Color(0x6610B981),
                ambientColor = Color(0x33059669)
            )
        }

        Box(
            modifier = Modifier
                .size(124.dp)
                .scale(totalSquircleScale)
                .graphicsLayer {
                    rotationX = tiltX
                    rotationY = tiltY
                    cameraDistance = 14f * density.density
                }
                .then(shadowModifier)
                .clip(squircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0A362E),
                            Color(0xFF061E1A),
                            Color(0xFF040F0D)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val bmpW = originalIcon.width
                val bmpH = originalIcon.height

                // Preserve the full glowing green neon border of focus_lock_original_icon.png
                // (neon border sits at ~10.2% inset in the 1254x1254 source image; cropping at
                // 0.088f keeps 100% of the glowing green neon border inside the squircle)
                val padInsetRatio = 0.088f
                val srcCropX = (bmpW * padInsetRatio).roundToInt()
                val srcCropY = (bmpH * padInsetRatio).roundToInt()
                val srcCropW = (bmpW * (1f - 2 * padInsetRatio)).roundToInt()
                val srcCropH = (bmpH * (1f - 2 * padInsetRatio)).roundToInt()

                val padLeftRatio = 0.52f
                val padTopRatio = 0.46f
                val padRightRatio = 0.82f
                val padBottomRatio = 0.82f

                val sweepRightX = w * fClipProgress
                val padlockLeftX = w * padLeftRatio
                val padlockTopY = h * padTopRatio
                val padlockBottomY = h * padBottomRatio
                val padlockRightX = w * padRightRatio

                // Draw subtle base silhouette of the full neon-bordered squircle at 35% alpha
                // so the green neon border is visible right from 0ms while the laser charges it
                if (t < 1020f) {
                    drawImage(
                        image = originalIcon,
                        srcOffset = IntOffset(srcCropX, srcCropY),
                        srcSize = IntSize(srcCropW, srcCropH),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                        alpha = 0.28f * stage1Progress
                    )

                    if (sweepRightX <= padlockLeftX) {
                        clipRect(left = 0f, top = 0f, right = sweepRightX, bottom = h) {
                            drawImage(
                                image = originalIcon,
                                srcOffset = IntOffset(srcCropX, srcCropY),
                                srcSize = IntSize(srcCropW, srcCropH),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(w.roundToInt(), h.roundToInt())
                            )
                        }
                    } else {
                        // Left section (full height up to padlockLeftX)
                        clipRect(left = 0f, top = 0f, right = padlockLeftX, bottom = h) {
                            drawImage(
                                image = originalIcon,
                                srcOffset = IntOffset(srcCropX, srcCropY),
                                srcSize = IntSize(srcCropW, srcCropH),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(w.roundToInt(), h.roundToInt())
                            )
                        }
                        // Top-right section above padlock
                        clipRect(left = padlockLeftX, top = 0f, right = sweepRightX, bottom = padlockTopY) {
                            drawImage(
                                image = originalIcon,
                                srcOffset = IntOffset(srcCropX, srcCropY),
                                srcSize = IntSize(srcCropW, srcCropH),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(w.roundToInt(), h.roundToInt())
                            )
                        }
                        // Bottom-right section below padlock
                        clipRect(left = padlockLeftX, top = padlockBottomY, right = sweepRightX, bottom = h) {
                            drawImage(
                                image = originalIcon,
                                srcOffset = IntOffset(srcCropX, srcCropY),
                                srcSize = IntSize(srcCropW, srcCropH),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(w.roundToInt(), h.roundToInt())
                            )
                        }
                        // Right neon border margin beyond padlock
                        if (sweepRightX > padlockRightX) {
                            clipRect(left = padlockRightX, top = padlockTopY, right = sweepRightX, bottom = padlockBottomY) {
                                drawImage(
                                    image = originalIcon,
                                    srcOffset = IntOffset(srcCropX, srcCropY),
                                    srcSize = IntSize(srcCropW, srcCropH),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = IntSize(w.roundToInt(), h.roundToInt())
                                )
                            }
                        }
                    }
                } else {
                    // After 1020ms, both "F", neon border, and padlock are 100% locked in place
                    drawImage(
                        image = originalIcon,
                        srcOffset = IntOffset(srcCropX, srcCropY),
                        srcSize = IntSize(srcCropW, srcCropH),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(w.roundToInt(), h.roundToInt())
                    )
                }

                // Stage 1 Laser Scanner Beam + Spark Nodes along the leading edge (0ms to 650ms)
                if (stage1Progress < 1f) {
                    val beamAlpha = (0.78f * (1f - stage1Progress * 0.75f)).coerceIn(0f, 0.85f)
                    val beamHalfWidth = 10.dp.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF34D399).copy(alpha = beamAlpha * 0.55f),
                                Color(0xFFA7F3D0).copy(alpha = beamAlpha),
                                Color.Transparent
                            ),
                            startX = sweepRightX - beamHalfWidth,
                            endX = sweepRightX + beamHalfWidth
                        ),
                        topLeft = Offset(sweepRightX - beamHalfWidth, 0f),
                        size = Size(beamHalfWidth * 2f, h)
                    )

                    // Subtle glowing energy nodes riding along the scanner beam
                    for (nodeIdx in 0..3) {
                        val yRatio = ((nodeIdx * 0.24f + stage1Progress * 0.6f) % 0.84f) + 0.08f
                        drawCircle(
                            color = Color(0xFFD1FAE5).copy(alpha = beamAlpha),
                            radius = 2.2.dp.toPx(),
                            center = Offset(sweepRightX, h * yRatio)
                        )
                    }
                }

                // Stage 2: White Padlock Spring Drop-In (360ms to 1020ms)
                if (t in 360f..1020f) {
                    val padSrcX = (srcCropX + srcCropW * padLeftRatio).roundToInt()
                    val padSrcY = (srcCropY + srcCropH * padTopRatio).roundToInt()
                    val padSrcW = (srcCropW * (padRightRatio - padLeftRatio)).roundToInt()
                    val padSrcH = (srcCropH * (padBottomRatio - padTopRatio)).roundToInt()

                    val padDstX = (w * padLeftRatio).roundToInt()
                    val padDstY = (h * padTopRatio + lockOffsetYPx).roundToInt()
                    val padDstW = (w * (padRightRatio - padLeftRatio)).roundToInt()
                    val padDstH = (h * (padBottomRatio - padTopRatio)).roundToInt()

                    drawImage(
                        image = originalIcon,
                        srcOffset = IntOffset(padSrcX, padSrcY),
                        srcSize = IntSize(padSrcW, padSrcH),
                        dstOffset = IntOffset(padDstX, padDstY),
                        dstSize = IntSize(padDstW, padDstH),
                        alpha = lockAlpha
                    )
                }

                // Stage 2B: Twin Neon Emerald-Mint Border Charge around the 28.dp Squircle Perimeter
                if (borderGlowSweep > 0f) {
                    val strokeWidthPx = 2.6.dp.toPx()
                    val cornerRadiusPx = 26.dp.toPx()
                    val insetPx = 2.5.dp.toPx()

                    val borderPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(
                                    insetPx,
                                    insetPx,
                                    w - insetPx,
                                    h - insetPx
                                ),
                                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                            )
                        )
                    }

                    val pathMeasure = PathMeasure()
                    pathMeasure.setPath(borderPath, false)
                    val totalLen = pathMeasure.length
                    val segment = Path()
                    pathMeasure.getSegment(0f, totalLen * borderGlowSweep, segment, true)

                    val glowAlpha = if (t <= 1120f) {
                        0.92f
                    } else {
                        (0.92f * (1f - calcProgress(t, 1120f, 1450f, LinearEasing))).coerceIn(0f, 0.92f)
                    }

                    if (glowAlpha > 0f) {
                        drawPath(
                            path = segment,
                            color = Color(0xFF4BF5C2).copy(alpha = glowAlpha),
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                        )
                    }
                }

                // Stage 2C: Diagonal Glass Reflection Shimmer across the 3D "F" and Padlock (700ms to 1220ms)
                if (sheenProgress in 0.01f..0.99f) {
                    val diagSpan = w * 2.6f
                    val sheenCenter = -w * 0.65f + diagSpan * sheenProgress
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x24FFFFFF),
                                Color(0x3D6EE7B7),
                                Color(0x24FFFFFF),
                                Color.Transparent
                            ),
                            start = Offset(sheenCenter - w * 0.38f, 0f),
                            end = Offset(sheenCenter + w * 0.38f, h)
                        ),
                        topLeft = Offset.Zero,
                        size = size
                    )
                }
            }
        }

        // Stage 3: Classy Bottom Title, Expanding Neon Laser Underline & Status Subtitle
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
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = dynamicLetterSpacing,
                color = titleTextColor,
                modifier = Modifier.alpha(nameProgress)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Center-expanding glowing emerald laser line
            val lineFraction = calcProgress(t, 380f, 1020f, FastOutSlowInEasing)
            Box(
                modifier = Modifier
                    .width((110.dp * lineFraction).coerceAtLeast(1.dp))
                    .height(1.8.dp)
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
                letterSpacing = 2.8.sp,
                color = subtitleTextColor,
                modifier = Modifier.alpha(subtitleProgress * 0.85f)
            )
        }
    }
}

private fun calcProgress(t: Float, start: Float, end: Float, easing: Easing = FastOutSlowInEasing): Float {
    if (t <= start) return 0f
    if (t >= end) return 1f
    val fraction = ((t - start) / (end - start)).coerceIn(0f, 1f)
    return easing.transform(fraction)
}
