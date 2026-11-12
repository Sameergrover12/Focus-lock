package com.example.ui.splash

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
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
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * High-framerate 0ms to 800ms startup animation overlay that animates
 * exclusively the original Focus Lock icon image (R.drawable.focus_lock_original_icon):
 *
 * - Stage 1 (0ms–350ms): Left-to-right sweep of the 3D "F" with glowing mint light bar
 * - Stage 2 (220ms–560ms): Padlock drop-in with crisp spring overshoot and snap
 * - Stage 3 (300ms–620ms): 360° emerald-mint squircle border glow sweep & diagonal sheen
 * - Stage 4 (100ms–450ms): Bottom "Focus Lock" title reveal
 * - Stage 5 (620ms–800ms): Smooth cross-dissolve exit into the Home screen
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
        // Wait for the window and initial frame to be physically visible on screen
        withFrameNanos { }
        withFrameNanos { }
        delay(16)

        timeMillis.animateTo(
            targetValue = 800f,
            animationSpec = tween(durationMillis = 800, easing = LinearEasing)
        )
        onAnimationFinished()
    }

    val t = timeMillis.value
    val density = LocalDensity.current

    // Stage 1 (0ms–350ms): Left-to-right sweep of "F"
    val fRevealProgress = calcProgress(t, 0f, 350f, FastOutSlowInEasing)

    // Stage 2 (220ms–560ms): White padlock drop-in & click
    val lockAlpha = calcProgress(t, 220f, 360f, FastOutSlowInEasing)
    val lockFraction = calcProgress(t, 220f, 520f, LinearEasing)
    val lockSpring = when {
        lockFraction <= 0f -> 0f
        lockFraction >= 1f -> 1f
        else -> {
            // Damped spring (dampingRatio ~0.52, stiffness ~650)
            val p = lockFraction
            1f - kotlin.math.exp(-6.5f * p) * kotlin.math.cos(12f * p)
        }
    }
    val lockOffsetYPx = with(density) { (-10.dp.toPx()) * (1f - lockSpring) }

    // Stage 3 (300ms–620ms): 360° Squircle border glow & diagonal light sheen
    val borderGlowSweep = calcProgress(t, 300f, 620f, FastOutSlowInEasing)
    val sheenProgress = calcProgress(t, 460f, 620f, LinearEasing)

    // Stage 4 (100ms–450ms): Bottom "Focus Lock" title reveal
    val nameProgress = calcProgress(t, 100f, 450f, FastOutSlowInEasing)
    val nameAlpha = nameProgress
    val nameOffsetPx = with(density) { (8.dp * (1f - nameProgress)).toPx() }

    // Stage 5 (620ms–800ms): Smooth cross-dissolve exit
    val exitProgress = calcProgress(t, 620f, 800f, FastOutLinearInEasing)
    val overlayAlpha = (1f - exitProgress).coerceIn(0f, 1f)
    val squircleScale = 1.0f + 0.04f * exitProgress

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(999f)
            .alpha(overlayAlpha)
            .background(screenBgColor),
        contentAlignment = Alignment.Center
    ) {
        // Centered 116.dp x 116.dp Squircle Icon Container
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
                .size(116.dp)
                .scale(squircleScale)
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

                // Inset to crop outer dark padding of original 1254x1254 image so squircle fills container
                val padInsetRatio = 0.098f
                val srcCropX = (bmpW * padInsetRatio).roundToInt()
                val srcCropY = (bmpH * padInsetRatio).roundToInt()
                val srcCropW = (bmpW * (1f - 2 * padInsetRatio)).roundToInt()
                val srcCropH = (bmpH * (1f - 2 * padInsetRatio)).roundToInt()

                val padLeftRatio = 0.53f
                val padTopRatio = 0.47f
                val padRightRatio = 0.82f
                val padBottomRatio = 0.82f

                // Stage 1: Sweep the "F" part of the original icon from left to right
                if (fRevealProgress > 0f) {
                    val sweepRightX = w * fRevealProgress
                    val padlockLeftX = w * padLeftRatio
                    val padlockTopY = h * padTopRatio
                    val padlockBottomY = h * padBottomRatio

                    if (t < 220f) {
                        // Padlock has not appeared yet: clip around its box
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
                            // Left section (up to padlockLeftX)
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
                        }
                    } else {
                        // After 220ms, sweep covers the full canvas as padlock is rendered in Stage 2
                        clipRect(left = 0f, top = 0f, right = sweepRightX, bottom = h) {
                            drawImage(
                                image = originalIcon,
                                srcOffset = IntOffset(srcCropX, srcCropY),
                                srcSize = IntSize(srcCropW, srcCropH),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(w.roundToInt(), h.roundToInt())
                            )
                        }
                    }

                    // Soft vertical glowing mint-teal light bar on the leading edge of the sweep
                    if (fRevealProgress in 0.02f..0.98f) {
                        val barAlpha = 0.55f * (1f - fRevealProgress)
                        val barHalfWidth = 6.dp.toPx()
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
                }

                // Stage 2: Bottom-Right White Padlock Drop-In & Snap (220ms to 560ms)
                if (t in 220f..560f) {
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

                // Stage 3A: 360° Squircle Border Glow Sweep (300ms to 620ms)
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

                    val glowAlpha = (0.85f * (1f - (borderGlowSweep - 0.75f).coerceAtLeast(0f) / 0.25f)).coerceIn(0f, 0.85f)
                    drawPath(
                        path = segment,
                        color = Color(0xFF34D399).copy(alpha = glowAlpha),
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }

                // Stage 3B: Subtle diagonal white/mint shimmer sheen once padlock clicks shut (460ms to 620ms)
                if (sheenProgress in 0.01f..0.99f) {
                    val diagSpan = w * 2.5f
                    val sheenCenter = -w * 0.6f + diagSpan * sheenProgress
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x28FFFFFF),
                                Color(0x306EE7B7),
                                Color.Transparent
                            ),
                            start = Offset(sheenCenter - w * 0.35f, 0f),
                            end = Offset(sheenCenter + w * 0.35f, h)
                        ),
                        topLeft = Offset.Zero,
                        size = size
                    )
                }
            }
        }

        // Stage 4: Bottom "Focus Lock" Title Reveal (100ms to 450ms)
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
