package com.example.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Single-window 0ms–800ms startup animation overlay rendered at the root Box of MainActivity
 * with zIndex(999f):
 *
 * - 0ms to 320ms: Dark-teal 112.dp x 112.dp squircle container (RoundedCornerShape(26.dp)) is
 *   visible immediately at 0ms (scale = 0.94f -> 1.0f with FastOutSlowInEasing), while the
 *   mint-teal "F" region of @drawable/focus_lock_original_icon sweeps in smoothly from left
 *   to right (clipRect progress 0.15f -> 1.0f) with a soft glowing mint leading edge.
 * - 180ms to 520ms: White padlock in the bottom-right corner drops down from
 *   translationY = -8.dp to 0.dp with a crisp spring bounce (dampingRatio = 0.52f,
 *   stiffness = 650f) and snaps into place while a subtle emerald-mint border glow traces
 *   around the 26.dp squircle perimeter.
 * - 100ms to 450ms: Bottom "Focus Lock" title (52.dp from bottom, #E2E8F0 in Dark Mode /
 *   #0F172A in Light Mode) fades in smoothly.
 * - 600ms to 800ms: Smoothly cross-dissolve the overlay (alpha = 1f -> 0f over 200ms)
 *   directly into the underlying screen and invoke onAnimationFinished().
 */
@Composable
fun FocusLockStartupSplashOverlay(
    isDarkTheme: Boolean,
    onAnimationFinished: () -> Unit
) {
    val screenBgColor = if (isDarkTheme) Color(0xFF0E1414) else Color(0xFFF4F7F6)
    val titleTextColor = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF0F172A)

    val originalIcon = ImageBitmap.imageResource(id = R.drawable.focus_lock_original_icon)
    val timeMillis = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Wait for the first composed frame to be ready on screen
        withFrameNanos { }

        timeMillis.animateTo(
            targetValue = 800f,
            animationSpec = tween(durationMillis = 800, easing = LinearEasing)
        )
        onAnimationFinished()
    }

    val t = timeMillis.value
    val density = LocalDensity.current

    // 0ms to 320ms: Squircle container scale 0.94f -> 1.0f & "F" sweep 0.15f -> 1.0f
    val stage1Progress = calcProgress(t, 0f, 320f, FastOutSlowInEasing)
    val fClipProgress = 0.15f + 0.85f * stage1Progress
    val squircleIntroScale = 0.94f + 0.06f * stage1Progress

    // 180ms to 520ms: White padlock drop-in (-8.dp to 0.dp) with spring bounce
    // (dampingRatio = 0.52f, stiffness = 650f) & emerald-mint border glow trace
    val lockAlpha = calcProgress(t, 180f, 300f, FastOutSlowInEasing)
    val lockFraction = calcProgress(t, 180f, 520f, LinearEasing)
    val lockSpring = when {
        lockFraction <= 0f -> 0f
        lockFraction >= 1f -> 1f
        else -> {
            // Damped harmonic spring matching dampingRatio = 0.52f, stiffness = 650f over 340ms
            val durationSec = 0.34f
            val omega0 = kotlin.math.sqrt(650f) // ~25.5 rad/s
            val zeta = 0.52f
            val omegaD = omega0 * kotlin.math.sqrt(1f - zeta * zeta)
            val elapsedSec = lockFraction * durationSec
            1f - exp(-zeta * omega0 * elapsedSec) * cos(omegaD * elapsedSec)
        }
    }
    val lockOffsetYPx = with(density) { (-8.dp.toPx()) * (1f - lockSpring) }
    val borderGlowSweep = calcProgress(t, 180f, 520f, FastOutSlowInEasing)

    // 100ms to 450ms: Bottom "Focus Lock" title fades in smoothly
    val nameProgress = calcProgress(t, 100f, 450f, FastOutSlowInEasing)
    val nameAlpha = nameProgress
    val nameOffsetPx = with(density) { (6.dp * (1f - nameProgress)).toPx() }

    // 600ms to 800ms: Cross-dissolve the overlay (alpha = 1f -> 0f over 200ms)
    val exitProgress = calcProgress(t, 600f, 800f, FastOutLinearInEasing)
    val overlayAlpha = (1f - exitProgress).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(999f)
            .alpha(overlayAlpha)
            .background(screenBgColor),
        contentAlignment = Alignment.Center
    ) {
        // Centered 112.dp x 112.dp Squircle Icon Container
        val squircleShape = RoundedCornerShape(26.dp)
        val shadowModifier = if (!isDarkTheme) {
            Modifier.shadow(
                elevation = 12.dp,
                shape = squircleShape,
                spotColor = Color(0x33000000),
                ambientColor = Color(0x1F000000)
            )
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .size(112.dp)
                .scale(squircleIntroScale)
                .then(shadowModifier)
                .clip(squircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0A362E),
                            Color(0xFF061E1A),
                            Color(0xFF061614)
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

                // Crop outer dark padding of 1254x1254 image so squircle fills the 112.dp container cleanly
                val padInsetRatio = 0.098f
                val srcCropX = (bmpW * padInsetRatio).roundToInt()
                val srcCropY = (bmpH * padInsetRatio).roundToInt()
                val srcCropW = (bmpW * (1f - 2 * padInsetRatio)).roundToInt()
                val srcCropH = (bmpH * (1f - 2 * padInsetRatio)).roundToInt()

                val padLeftRatio = 0.53f
                val padTopRatio = 0.47f
                val padRightRatio = 0.82f
                val padBottomRatio = 0.82f

                // 0ms to 320ms: Sweep the mint-teal "F" region from left to right (0.15f -> 1.0f)
                val sweepRightX = w * fClipProgress
                val padlockLeftX = w * padLeftRatio
                val padlockTopY = h * padTopRatio
                val padlockBottomY = h * padBottomRatio

                if (t < 520f) {
                    // Draw the icon while excluding the padlock rectangle (which animates separately)
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
                        // Right margin beyond padlock
                        val padlockRightX = w * padRightRatio
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
                    // After 520ms, both "F" and padlock have settled at their exact final positions
                    drawImage(
                        image = originalIcon,
                        srcOffset = IntOffset(srcCropX, srcCropY),
                        srcSize = IntSize(srcCropW, srcCropH),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(w.roundToInt(), h.roundToInt())
                    )
                }

                // Soft glowing mint leading edge during the 0ms to 320ms "F" sweep
                if (stage1Progress < 1f) {
                    val barAlpha = 0.6f * (1f - stage1Progress)
                    val barHalfWidth = 7.dp.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF6EE7B7).copy(alpha = barAlpha),
                                Color.Transparent
                            ),
                            startX = sweepRightX - barHalfWidth,
                            endX = sweepRightX + barHalfWidth
                        ),
                        topLeft = Offset(sweepRightX - barHalfWidth, 0f),
                        size = androidx.compose.ui.geometry.Size(barHalfWidth * 2, h)
                    )
                }

                // 180ms to 520ms: White padlock in the bottom-right corner drops down from -8.dp to 0.dp
                if (t in 180f..520f) {
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

                // 180ms to 520ms: Subtle emerald-mint border glow traces around the 26.dp squircle perimeter
                if (borderGlowSweep > 0f) {
                    val strokeWidthPx = 2.dp.toPx()
                    val cornerRadiusPx = 26.dp.toPx()

                    val borderPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(
                                    strokeWidthPx / 2,
                                    strokeWidthPx / 2,
                                    w - strokeWidthPx / 2,
                                    h - strokeWidthPx / 2
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

                    val glowAlpha = if (t <= 520f) {
                        0.85f
                    } else {
                        (0.85f * (1f - calcProgress(t, 520f, 650f, LinearEasing))).coerceIn(0f, 0.85f)
                    }
                    if (glowAlpha > 0f) {
                        drawPath(
                            path = segment,
                            color = Color(0xFF34D399).copy(alpha = glowAlpha),
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }

        // 100ms to 450ms: Bottom "Focus Lock" Title Reveal (52.dp from bottom)
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
