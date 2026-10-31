package com.theveloper.pixelplay.presentation.components.lyrics

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Port of Mornye's 60/120fps monotonic clock position interpolator + 25% proportional drift damping
 * (`reconcileSyncedLyricsPosition` from `lib/utils/synced_lyrics_scroll.dart`) and
 * per-word horizontal gradient sweep, Hermite smoothstep vertical lift, held-note glow,
 * and trailing cascade wave (`lib/widgets/player_lyrics_word_sweep.dart` & `lib/widgets/lyric_scroll_motion.dart`).
 */
@Composable
fun rememberInterpolatedLyricsPosition(
    playbackPositionFlow: StateFlow<Long>,
    isPlaying: Boolean,
    lyricsSyncOffsetMs: Int = 0,
    positionOverrideMs: Long? = null,
    seekThresholdMs: Long = 750L,
    correctionFactor: Float = 0.25f
): State<Long> {
    val displayedPositionState = remember {
        mutableLongStateOf((positionOverrideMs ?: playbackPositionFlow.value) + lyricsSyncOffsetMs)
    }

    var anchorPositionMs by remember { mutableLongStateOf(playbackPositionFlow.value) }
    var anchorElapsedMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var smoothedBaseMs by remember { mutableLongStateOf(playbackPositionFlow.value) }

    // Reconcile incoming coarse playback position ticks (e.g. 250ms polling) using Mornye's 25% drift damping
    LaunchedEffect(playbackPositionFlow, isPlaying, positionOverrideMs) {
        if (positionOverrideMs != null) {
            val now = SystemClock.elapsedRealtime()
            anchorPositionMs = positionOverrideMs
            anchorElapsedMs = now
            smoothedBaseMs = positionOverrideMs
            displayedPositionState.longValue = (positionOverrideMs + lyricsSyncOffsetMs).coerceAtLeast(0L)
            return@LaunchedEffect
        }

        playbackPositionFlow.collect { rawPos ->
            val now = SystemClock.elapsedRealtime()
            if (!isPlaying) {
                anchorPositionMs = rawPos
                anchorElapsedMs = now
                smoothedBaseMs = rawPos
                displayedPositionState.longValue = (rawPos + lyricsSyncOffsetMs).coerceAtLeast(0L)
            } else {
                val projected = anchorPositionMs + (now - anchorElapsedMs).coerceAtLeast(0L)
                val drift = rawPos - projected
                val nextBase = if (abs(drift) >= seekThresholdMs) {
                    rawPos
                } else {
                    val corrected = projected + (drift * correctionFactor).roundToLong()
                    max(smoothedBaseMs, corrected)
                }.coerceAtLeast(0L)

                anchorPositionMs = nextBase
                anchorElapsedMs = now
                smoothedBaseMs = nextBase
                displayedPositionState.longValue = (nextBase + lyricsSyncOffsetMs).coerceAtLeast(0L)
            }
        }
    }

    // Update when sync offset or play state changes
    LaunchedEffect(isPlaying, lyricsSyncOffsetMs, positionOverrideMs) {
        val now = SystemClock.elapsedRealtime()
        val base = positionOverrideMs ?: playbackPositionFlow.value
        anchorPositionMs = base
        anchorElapsedMs = now
        smoothedBaseMs = base
        displayedPositionState.longValue = (base + lyricsSyncOffsetMs).coerceAtLeast(0L)
    }

    // 60/120fps frame ticker while playing
    LaunchedEffect(isPlaying, lyricsSyncOffsetMs, positionOverrideMs) {
        if (!isPlaying || positionOverrideMs != null) return@LaunchedEffect
        while (true) {
            withFrameNanos {
                val now = SystemClock.elapsedRealtime()
                val elapsed = (now - anchorElapsedMs).coerceAtLeast(0L)
                val interpolatedBase = max(smoothedBaseMs, anchorPositionMs + elapsed)
                smoothedBaseMs = interpolatedBase
                displayedPositionState.longValue = (interpolatedBase + lyricsSyncOffsetMs).coerceAtLeast(0L)
            }
        }
    }

    return displayedPositionState
}

/**
 * Port of Mornye's `syncedLyricSegmentProgress` (`lib/utils/synced_lyrics_scroll.dart`).
 * Computes continuous 0.0 -> 1.0 horizontal wipe progress for a timed word.
 */
fun syncedLyricSegmentProgress(
    positionMs: Long,
    startMs: Int,
    endMs: Int
): Float {
    val safeEnd = max(endMs, startMs + 120)
    if (positionMs <= startMs) return 0f
    if (positionMs >= safeEnd) return 1f
    val duration = (safeEnd - startMs).coerceAtLeast(1)
    return ((positionMs - startMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
}

private fun smoothStep01(value: Float): Float {
    val u = value.coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

/**
 * Port of Mornye's `syncedLyricSegmentLift` (`lib/utils/synced_lyrics_scroll.dart`).
 * Starts rising `180ms` before `startMs` over `720ms` using a Hermite smoothstep curve,
 * and boosts vertical lift up to `2.3x` for long held notes (`> 1000ms`).
 */
fun syncedLyricSegmentLift(
    positionMs: Long,
    startMs: Int,
    endMs: Int,
    wordRiseLeadMs: Int = 180,
    wordRiseDurationMs: Int = 720,
    longWordDurationMs: Int = 1000,
    longWordLiftScale: Float = 2.3f,
    longWordHoldCurvePower: Float = 1.65f,
    longWordExtraRiseDurationScale: Float = 1.55f,
    longWordFallDurationMs: Int = 180
): Float {
    val safeEnd = max(endMs, startMs + 120)
    val riseStart = startMs - wordRiseLeadMs
    if (positionMs <= riseStart) return 0f

    val baseProgress = ((positionMs - riseStart).toFloat() / wordRiseDurationMs.toFloat()).coerceIn(0f, 1f)
    val baseLift = smoothStep01(baseProgress)
    val duration = safeEnd - startMs

    if (duration <= longWordDurationMs) {
        return baseLift
    }

    val extraTarget = max(0f, longWordLiftScale - 1f)
    if (extraTarget <= 0f) return baseLift

    if (positionMs <= safeEnd) {
        if (positionMs <= startMs) return baseLift
        val extraRiseDurationMs = max(
            1f,
            min(duration.toFloat(), wordRiseDurationMs * longWordExtraRiseDurationScale)
        )
        val holdProgress = ((positionMs - startMs).toFloat() / extraRiseDurationMs).coerceIn(0f, 1f)
        val curvedHold = holdProgress.pow(longWordHoldCurvePower).coerceIn(0f, 1f)
        return baseLift + extraTarget * smoothStep01(curvedHold)
    }

    val fallProgress = ((positionMs - safeEnd).toFloat() / longWordFallDurationMs.toFloat()).coerceIn(0f, 1f)
    if (fallProgress >= 1f) return 1f
    return 1f + extraTarget * (1f - smoothStep01(fallProgress))
}

/**
 * Port of Mornye's `syncedLyricSegmentGlow` (`lib/utils/synced_lyrics_scroll.dart`).
 * Emits a smooth glow envelope for held notes (`> 900ms`).
 */
fun syncedLyricSegmentGlow(
    positionMs: Long,
    startMs: Int,
    endMs: Int,
    glowDurationThresholdMs: Int = 900,
    glowMaxDurationMs: Int = 2000,
    glowEaseInRatio: Float = 0.2f,
    glowEaseOutRatio: Float = 0.28f,
    glowReleaseMs: Int = 180
): Float {
    val safeEnd = max(endMs, startMs + 120)
    val duration = safeEnd - startMs
    if (duration <= glowDurationThresholdMs || positionMs <= startMs) return 0f

    val durationScale = ((duration - glowDurationThresholdMs).toFloat() /
        max(1, glowMaxDurationMs - glowDurationThresholdMs).toFloat()).coerceIn(0f, 1f)
    val peakIntensity = 0.35f + 0.65f * smoothStep01(durationScale)

    if (positionMs < safeEnd) {
        val progress = ((positionMs - startMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        val attackEnd = glowEaseInRatio.coerceIn(0.05f, 0.45f)
        val decayStart = (1f - glowEaseOutRatio).coerceIn(attackEnd + 0.05f, 0.95f)
        val envelope = when {
            progress < attackEnd -> smoothStep01(progress / attackEnd)
            progress > decayStart -> 1f - 0.55f * smoothStep01((progress - decayStart) / (1f - decayStart))
            else -> 1f
        }
        return peakIntensity * envelope
    }

    val releaseProgress = ((positionMs - safeEnd).toFloat() / glowReleaseMs.toFloat()).coerceIn(0f, 1f)
    if (releaseProgress >= 1f) return 0f
    val endHoldLevel = peakIntensity * 0.45f
    return endHoldLevel * (1f - smoothStep01(releaseProgress))
}

/**
 * Port of Mornye's `LyricScrollMotion` (`lib/widgets/lyric_scroll_motion.dart`).
 * Produces a subtle phase-delayed trailing cascade wave for rows below the active lyric line
 * whenever `currentLineIndex` advances forward.
 */
@Composable
fun rememberTrailingCascadeWaveOffset(
    lineIndex: Int,
    currentLineIndex: Int,
    maxWaveDisplacementDp: Dp = 2.5.dp
): Float {
    val waveAnim = remember { Animatable(0f) }
    var previousFocusIndex by remember { mutableStateOf(currentLineIndex) }
    val density = LocalDensity.current
    val maxDisplacementPx = with(density) { maxWaveDisplacementDp.toPx() }

    LaunchedEffect(currentLineIndex) {
        val prev = previousFocusIndex
        previousFocusIndex = currentLineIndex
        val delta = currentLineIndex - prev
        if (prev < 0 || currentLineIndex < 0 || delta !in 1..4) {
            return@LaunchedEffect
        }
        val rowsAfterFocus = lineIndex - currentLineIndex
        if (rowsAfterFocus !in 0..6) {
            return@LaunchedEffect
        }
        // Phase delay of 32ms per row below the active line
        val delayMs = rowsAfterFocus * 32L
        val amplitudeScale = (1f - rowsAfterFocus * 0.12f).coerceIn(0.25f, 1f)
        if (delayMs > 0L) {
            delay(delayMs)
        }
        waveAnim.snapTo(maxDisplacementPx * amplitudeScale)
        waveAnim.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = 0.78f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    return waveAnim.value
}

/**
 * Port of Mornye's `_SweepingWordSpan` (`lib/widgets/player_lyrics_word_sweep.dart`).
 * Renders a word with:
 * - Soft-edge horizontal gradient wipe from left to right (`feather = clamp(width * 0.3, 4dp, 16dp)`)
 * - Already-sung words in the active line remain 100% lit
 * - Hermite smoothstep vertical lift (`-2.2dp` base lift, up to `2.3x` for held notes) in `graphicsLayer`
 * - Subtle shadow/glow for held notes (`> 900ms`)
 */
@Composable
fun MornyeSweepingWordSpan(
    wordText: String,
    startMs: Int,
    endMs: Int,
    nextWordStartMs: Int?,
    lineEndMs: Int?,
    currentPositionMs: Long,
    isCurrentLine: Boolean,
    isPastLine: Boolean,
    activeColor: Color,
    inactiveAlpha: Float,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    style: TextStyle,
    baseLiftDp: Dp = 2.2.dp,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val baseLiftPx = with(density) { baseLiftDp.toPx() }
    val minFeatherPx = with(density) { 4.dp.toPx() }
    val maxFeatherPx = with(density) { 16.dp.toPx() }
    val glowBlurPx = with(density) { 14.dp.toPx() }

    val effectiveEndMs = remember(startMs, endMs, nextWordStartMs, lineEndMs) {
        val candidate = when {
            endMs > startMs -> endMs
            nextWordStartMs != null && nextWordStartMs > startMs -> nextWordStartMs
            lineEndMs != null && lineEndMs > startMs -> lineEndMs
            else -> startMs + 420
        }
        max(candidate, startMs + 120)
    }

    val progress = when {
        !isCurrentLine -> if (isPastLine) 0f else 0f
        else -> syncedLyricSegmentProgress(currentPositionMs, startMs, effectiveEndMs)
    }

    val liftAmount = if (isCurrentLine) {
        syncedLyricSegmentLift(currentPositionMs, startMs, effectiveEndMs)
    } else {
        0f
    }

    val glowAmount = if (isCurrentLine) {
        syncedLyricSegmentGlow(currentPositionMs, startMs, effectiveEndMs)
    } else {
        0f
    }

    val translationY = -liftAmount * baseLiftPx
    val wordScale = if (isCurrentLine && progress in 0.01f..0.99f) {
        1f + 0.025f * min(1f, liftAmount)
    } else {
        1f
    }

    val baseTextColor = if (isCurrentLine) {
        activeColor.copy(alpha = inactiveAlpha.coerceIn(0.28f, 0.52f))
    } else {
        activeColor.copy(alpha = inactiveAlpha)
    }

    val activeTextStyle = if (glowAmount > 0.01f) {
        style.copy(
            shadow = Shadow(
                color = activeColor.copy(alpha = (0.65f * glowAmount).coerceIn(0f, 0.85f)),
                offset = Offset.Zero,
                blurRadius = glowBlurPx * (0.5f + 0.5f * glowAmount)
            )
        )
    } else {
        style
    }

    Box(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp, horizontal = 1.dp)
            .graphicsLayer {
                this.translationY = translationY
                this.scaleX = wordScale
                this.scaleY = wordScale
            }
    ) {
        if (!isCurrentLine || progress <= 0.001f) {
            Text(
                text = wordText,
                style = style,
                color = baseTextColor,
                fontSize = fontSize,
                fontWeight = fontWeight
            )
        } else if (progress >= 0.999f) {
            // Already-sung word in the active line stays 100% lit (with any held-note release glow)
            Text(
                text = wordText,
                style = activeTextStyle,
                color = activeColor,
                fontSize = fontSize,
                fontWeight = fontWeight
            )
        } else {
            // Unsung dim base word
            Text(
                text = wordText,
                style = style,
                color = baseTextColor,
                fontSize = fontSize,
                fontWeight = fontWeight
            )
            // Soft-feathered horizontal gradient sweep overlay
            Text(
                text = wordText,
                style = activeTextStyle,
                color = activeColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                modifier = Modifier
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val w = size.width
                        if (w > 0f) {
                            val feather = (w * 0.30f).coerceIn(minFeatherPx, maxFeatherPx)
                            val totalTravel = w + feather
                            val center = progress * totalTravel - (feather * 0.5f)
                            val leftStop = ((center - feather * 0.5f) / w).coerceIn(0f, 1f)
                            val rightStop = ((center + feather * 0.5f) / w).coerceIn(0f, 1f)

                            if (rightStop <= 0.001f) {
                                drawRect(
                                    color = Color.Transparent,
                                    size = size,
                                    blendMode = BlendMode.DstIn
                                )
                            } else if (leftStop >= 0.999f) {
                                // Fully visible
                            } else {
                                val safeRight = max(rightStop, leftStop + 0.002f).coerceAtMost(1f)
                                val maskBrush = Brush.horizontalGradient(
                                    0f to Color.White,
                                    leftStop to Color.White,
                                    safeRight to Color.Transparent,
                                    1f to Color.Transparent,
                                    startX = 0f,
                                    endX = w
                                )
                                drawRect(
                                    brush = maskBrush,
                                    topLeft = Offset.Zero,
                                    size = Size(w, size.height),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                    }
            )
        }
    }
}
