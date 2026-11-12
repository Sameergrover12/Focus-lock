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
private val ShackleSnapEasing = CubicBezierEasing(0.34f, 1.28f, 0.64f, 1f)

/**
 * Calm, smooth, 1750ms (~0.4x speed) startup splash animation overlay rendered at the root Box
 * of MainActivity with zIndex(999f):
 *
 * 1. Paced at 1.75 seconds total duration so every stage breathes and is clearly visible:
 *    - 0ms–780ms: Calm emblem scale-in (0.88f -> 1.0f with gentle spring).
 *    - 100ms–980ms: Feathered diagonal specular light sweep across the intact teal "F".
 *    - 0ms–1450ms: Slow rotating segmented outer HUD dial rings and perimeter border wave.
 *    - 140ms–1120ms: Unhurried bottom "FOCUS LOCK" + "ATTENTION SHIELD" text & underline reveal.
 *
 * 2. Smooth Opening-and-Closing Padlock Shackle Animation (without touching the teal "F"):
 *    - 0ms–520ms: The white padlock starts in an "unlocked/open" state with its top U-shaped
 *      shackle raised up (right leg visibly unlatched above the padlock body, while the teal "F"
 *      and padlock body remain 100% intact).
 *    - 520ms–980ms: The raised U-shaped shackle smoothly drops down and clicks shut into the
 *      locked position.
 *    - 960ms–1420ms: Right as the padlock clicks shut, a lock-confirmation pulse & radial
 *      mint-white glow illuminates the padlock and ripples outward before the splash screen
 *      smoothly cross-dissolves (1380ms–1750ms) into the app.
 */
@Composable
fun FocusLockStartupSplashOverlay(
    isDarkTheme: Boolean,
    onAnimationFinished: () -> Unit
) {
    val screenBgColor = if (isDarkTheme) Color(0xFF0E1414) else Color(0xFFF4F7F6)
    val titleTextColor = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF0F172A)
    val subtitleTextColor = if (isDarkTheme) Color(0xFF6EE7B7) else Color(0xFF059669)

    val originalLockedIcon = ImageBitmap.imageResource(id = R.drawable.focus_lock_original_icon)
    val openPadlockBaseIcon = ImageBitmap.imageResource(id = R.drawable.focus_lock_base_open_padlock)
    val shacklePieceIcon = ImageBitmap.imageResource(id = R.drawable.focus_lock_shackle_piece)

    val timeMillis = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Sync with first rendered frame so the animation starts cleanly at 0ms
        withFrameNanos { }
        timeMillis.animateTo(
            targetValue = 1750f,
            animationSpec = tween(durationMillis = 1750, easing = LinearEasing)
        )
        onAnimationFinished()
    }

    val t = timeMillis.value
    val density = LocalDensity.current

    // 1. Slowed Emblem Scale-In (0ms to 780ms, 0.88f -> 1.0f with gentle spring)
    val springFraction = calcProgress(t, 0f, 780f, LinearEasing)
    val springValue = when {
        springFraction <= 0f -> 0f
        springFraction >= 1f -> 1f
        else -> {
            val durationSec = 0.78f
            val omega0 = sqrt(180f)
            val zeta = 0.74f
            val omegaD = omega0 * sqrt(1f - zeta * zeta)
            val elapsedSec = springFraction * durationSec
            1f - exp(-zeta * omega0 * elapsedSec) * cos(omegaD * elapsedSec)
        }
    }
    val baseEmblemScale = 0.88f + 0.12f * springValue

    // 2. Feathered Diagonal Light Sweep across the "F" (100ms to 980ms)
    val sheenProgress = calcProgress(t, 100f, 980f, SmoothSignatureEasing)

    // 3. Padlock Shackle Opening-and-Closing Choreography:
    // - 0ms to 360ms: Shackle starts open (36px raised) and lifts slightly to full open peak (42px at 480ms)
    // - 520ms to 980ms: Shackle drops smoothly down (42px -> 0px) and clicks shut into the padlock body
    val shackleLiftSrcPx = when {
        t < 480f -> {
            val openRise = calcProgress(t, 0f, 480f, FastOutSlowInEasing)
            36f + 6f * openRise
        }
        t < 980f -> {
            val closeProgress = calcProgress(t, 520f, 980f, ShackleSnapEasing)
            (42f * (1f - closeProgress)).coerceAtLeast(0f)
        }
        else -> 0f
    }
    val isPadlockFullyLocked = t >= 980f

    // 4. Lock-Confirmation Pulse & Glow right as the padlock clicks shut (960ms to 1420ms)
    val lockConfirmProgress = calcProgress(t, 960f, 1420f, FastOutSlowInEasing)
    val lockScaleBump = if (t in 950f..1320f) {
        val raw = ((t - 950f) / 370f).coerceIn(0f, 1f)
        sin(raw * PI.toFloat()) * 0.038f
    } else {
        0f
    }

    // 5. Slowed Outer Rotating HUD Rings, Squircle Border Wave & Lock Shockwave (0ms to 1500ms)
    val hudAppear = calcProgress(t, 0f, 680f, FastOutSlowInEasing)
    val hudFade = 1f - calcProgress(t, 1340f, 1700f, LinearEasing)
    val hudAlpha = (hudAppear * hudFade).coerceIn(0f, 1f)
    val hudRotationDeg = SmoothSignatureEasing.transform((t / 1750f).coerceIn(0f, 1f)) * 52f

    val borderWaveSweep = calcProgress(t, 160f, 1180f, FastOutSlowInEasing)
    val shockwaveProgress = calcProgress(t, 960f, 1480f, FastOutSlowInEasing)

    // 6. Slowed Bottom "FOCUS LOCK" + "ATTENTION SHIELD" Typography Reveal (140ms to 1120ms)
    val nameProgress = calcProgress(t, 140f, 920f, FastOutSlowInEasing)
    val subtitleProgress = calcProgress(t, 340f, 1120f, FastOutSlowInEasing)
    val nameOffsetPx = with(density) { (10.dp * (1f - nameProgress)).toPx() }
    val dynamicLetterSpacing = (4.8f - 2.4f * nameProgress).sp

    // 7. Calm, Smooth Final Fade-Out into the App (1380ms to 1750ms)
    val exitProgress = calcProgress(t, 1380f, 1750f, FastOutLinearInEasing)
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
        // Outer Ambient Halo + Slow Rotating Segmented HUD Dial + Lock-Click Shockwave Ring
        Canvas(
            modifier = Modifier.size(260.dp)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f

            // Soft radial emerald-mint ambient halo adapted to Dark / Light Mode
            val haloPeakAlpha = if (isDarkTheme) 0.19f else 0.12f
            val haloAlpha = (haloPeakAlpha * hudAppear * (1f - exitProgress * 0.5f)).coerceIn(0f, haloPeakAlpha)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF10B981).copy(alpha = haloAlpha),
                        Color(0xFF059669).copy(alpha = haloAlpha * 0.42f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius * 0.90f
                ),
                radius = maxRadius,
                center = center
            )

            // Rotating segmented outer HUD dial ticks
            if (hudAlpha > 0.01f) {
                val dialRadius = 80.dp.toPx() * (0.91f + 0.09f * hudAppear)
                val tickCount = 36
                val dialBaseColor = if (isDarkTheme) Color(0xFF34D399) else Color(0xFF059669)

                for (i in 0 until tickCount) {
                    val angleDeg = i * (360f / tickCount) + hudRotationDeg
                    val angleRad = (angleDeg * PI / 180.0).toFloat()
                    val isMajor = i % 3 == 0
                    val innerR = dialRadius - if (isMajor) 5.8.dp.toPx() else 3.0.dp.toPx()
                    val outerR = dialRadius
                    val tickAlpha = (if (isMajor) 0.35f else 0.16f) * hudAlpha

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
                val arcRadius = 87.dp.toPx()
                val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
                val arcColor = if (isDarkTheme) Color(0xFF6EE7B7) else Color(0xFF10B981)
                drawArc(
                    color = arcColor.copy(alpha = 0.28f * hudAlpha),
                    startAngle = -42f - hudRotationDeg * 1.15f,
                    sweepAngle = 68f * hudAppear,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = arcColor.copy(alpha = 0.28f * hudAlpha),
                    startAngle = 138f - hudRotationDeg * 1.15f,
                    sweepAngle = 68f * hudAppear,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Expanding rounded-squircle perimeter shockwave ring triggered right as padlock clicks shut (960ms..1480ms)
            if (shockwaveProgress in 0.01f..0.99f) {
                val waveHalfSize = 56.dp.toPx() + 44.dp.toPx() * shockwaveProgress
                val waveAlpha = (if (isDarkTheme) 0.50f else 0.38f) * (1f - shockwaveProgress)
                val cornerR = 26.dp.toPx() + 15.dp.toPx() * shockwaveProgress
                drawRoundRect(
                    color = Color(0xFF34D399).copy(alpha = waveAlpha),
                    topLeft = Offset(center.x - waveHalfSize, center.y - waveHalfSize),
                    size = Size(waveHalfSize * 2f, waveHalfSize * 2f),
                    cornerRadius = CornerRadius(cornerR, cornerR),
                    style = Stroke(width = (2.2f * (1f - shockwaveProgress * 0.5f)).dp.toPx())
                )
            }
        }

        // Centered 112.dp x 112.dp Squircle Emblem (RoundedCornerShape(26.dp))
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

                val bmpW = originalLockedIcon.width
                val bmpH = originalLockedIcon.height

                // Crop outer dark margin so the full neon-bordered squircle fills the 112.dp container cleanly
                val padInsetRatio = 0.092f
                val srcCropX = (bmpW * padInsetRatio).roundToInt()
                val srcCropY = (bmpH * padInsetRatio).roundToInt()
                val srcCropW = (bmpW * (1f - 2 * padInsetRatio)).roundToInt()
                val srcCropH = (bmpH * (1f - 2 * padInsetRatio)).roundToInt()

                if (!isPadlockFullyLocked) {
                    // 1A. While padlock is open or closing (0ms..980ms), draw the 100% intact base icon
                    // (with intact teal "F" and padlock body) and the animated U-shaped shackle on top
                    drawImage(
                        image = openPadlockBaseIcon,
                        srcOffset = IntOffset(srcCropX, srcCropY),
                        srcSize = IntSize(srcCropW, srcCropH),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(w.roundToInt(), h.roundToInt())
                    )

                    // Map the isolated shackle piece (214x193 at srcX=732, srcY=608 - shackleLiftSrcPx)
                    // and clip its bottom at the padlock body's top edge (srcY=755) so the left leg stays
                    // seamlessly inside the padlock body while the right leg lifts open and drops shut
                    val shackleDstX = ((732f - srcCropX) / srcCropW) * w
                    val shackleDstY = ((608f - shackleLiftSrcPx - srcCropY) / srcCropH) * h
                    val shackleDstW = (shacklePieceIcon.width.toFloat() / srcCropW) * w
                    val shackleDstH = (shacklePieceIcon.height.toFloat() / srcCropH) * h
                    val padlockBodyTopY = ((755f - srcCropY) / srcCropH) * h

                    clipRect(
                        left = 0f,
                        top = 0f,
                        right = w,
                        bottom = padlockBodyTopY
                    ) {
                        drawImage(
                            image = shacklePieceIcon,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(shacklePieceIcon.width, shacklePieceIcon.height),
                            dstOffset = IntOffset(shackleDstX.roundToInt(), shackleDstY.roundToInt()),
                            dstSize = IntSize(shackleDstW.roundToInt(), shackleDstH.roundToInt())
                        )
                    }
                } else {
                    // 1B. Once the shackle clicks shut (t >= 980ms), render the 100% original locked icon
                    drawImage(
                        image = originalLockedIcon,
                        srcOffset = IntOffset(srcCropX, srcCropY),
                        srcSize = IntSize(srcCropW, srcCropH),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(w.roundToInt(), h.roundToInt())
                    )
                }

                // 2. Feathered Diagonal Specular Light Sheen sweeping across the "F" (100ms to 980ms)
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

                // 3. Padlock Lock-Confirmation Pulse & Glow right as the shackle clicks shut (960ms to 1420ms)
                if (t in 960f..1420f) {
                    val padlockCenter = Offset(w * 0.665f, h * 0.64f)
                    val minRadiusPx = 10.dp.toPx()
                    val maxRadiusPx = 36.dp.toPx()
                    val currentRadiusPx = minRadiusPx + (maxRadiusPx - minRadiusPx) * lockConfirmProgress
                    val ringAlpha = (0.52f * (1f - lockConfirmProgress)).coerceIn(0f, 0.52f)

                    // Soft radial mint-white confirmation glow + crisp expanding ring on the padlock
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = ringAlpha * 0.65f),
                                Color(0xFF6EE7B7).copy(alpha = ringAlpha * 0.90f),
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
                        radius = currentRadiusPx * 0.84f,
                        center = padlockCenter,
                        style = Stroke(width = 1.9.dp.toPx())
                    )
                }

                // 4. Slowed Emerald-Mint Squircle Perimeter Border Wave (160ms to 1180ms)
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

                    val borderAlpha = if (t <= 1120f) {
                        0.85f
                    } else {
                        (0.85f * (1f - calcProgress(t, 1120f, 1480f, LinearEasing))).coerceIn(0f, 0.85f)
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

        // Bottom "FOCUS LOCK" + "ATTENTION SHIELD" Typography (140ms to 1120ms)
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

            val lineFraction = calcProgress(t, 240f, 1080f, FastOutSlowInEasing)
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
