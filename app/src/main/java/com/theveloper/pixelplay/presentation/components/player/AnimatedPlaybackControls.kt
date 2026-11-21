package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.components.LocalMaterialTheme
import kotlinx.coroutines.delay
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

private enum class PlaybackButtonType { NONE, PREVIOUS, PLAY_PAUSE, NEXT }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedPlaybackControls(
    isPlayingProvider: () -> Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 90.dp,
    baseWeight: Float = 1f,
    expansionWeight: Float = 1.1f,
    compressionWeight: Float = 0.65f,
    pressAnimationSpec: AnimationSpec<Float>,
    releaseDelay: Long = 220L,
    playPauseCornerPlaying: Dp = 60.dp,
    playPauseCornerPaused: Dp = 26.dp,
    colorOtherButtons: Color = LocalMaterialTheme.current.secondaryContainer,
    colorPlayPause: Color = LocalMaterialTheme.current.primary,
    tintPlayPauseIcon: Color = LocalMaterialTheme.current.onPrimary,
    tintOtherIcons: Color = LocalMaterialTheme.current.onSecondaryContainer,
    colorPreviousButton: Color = colorOtherButtons,
    colorNextButton: Color = colorOtherButtons,
    tintPreviousIcon: Color = tintOtherIcons,
    tintNextIcon: Color = tintOtherIcons,
    playPauseIconSize: Dp = 36.dp,
    iconSize: Dp = 32.dp,
) {
    val isPlaying = isPlayingProvider()
    var lastClicked by remember { mutableStateOf<PlaybackButtonType?>(null) }
    var clickTrigger by remember { mutableStateOf(0) }
    val latestIsPlayingProvider by rememberUpdatedState(newValue = isPlayingProvider)
    val latestLastClicked by rememberUpdatedState(newValue = lastClicked)
    val isPlayPauseLocked =
        lastClicked == PlaybackButtonType.NEXT || lastClicked == PlaybackButtonType.PREVIOUS
    var playPauseVisualState by remember { mutableStateOf(isPlaying) }
    var pendingPlayPauseState by remember { mutableStateOf<Boolean?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

    val motionScheme = remember { MotionScheme.expressive() }
    val defaultSpatialDpSpec = remember(motionScheme) {
        motionScheme.fastSpatialSpec<Dp>()
    }

    LaunchedEffect(lastClicked, clickTrigger) {
        if (lastClicked != null) {
            val delayTime = when (lastClicked) {
                PlaybackButtonType.NEXT, PlaybackButtonType.PREVIOUS -> 300L
                else -> releaseDelay
            }
            delay(delayTime)
            lastClicked = null
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            pendingPlayPauseState = true
            return@LaunchedEffect
        }

        val shouldDelay = latestLastClicked != PlaybackButtonType.PLAY_PAUSE
        if (shouldDelay) {
            delay(releaseDelay)
        }
        if (!latestIsPlayingProvider()) {
            pendingPlayPauseState = false
        }
    }

    LaunchedEffect(isPlayPauseLocked, pendingPlayPauseState) {
        if (!isPlayPauseLocked) {
            pendingPlayPauseState?.let {
                playPauseVisualState = it
                pendingPlayPauseState = null
            }
        }
    }

    fun weightFor(button: PlaybackButtonType): Float = when (lastClicked) {
        button -> expansionWeight
        null -> baseWeight
        else -> compressionWeight
    }

    // Isolate weight & corner state reads to Layout/Draw phases so button press springs
    // and squircle morphs never trigger composition re-execution on every animation frame.
    val prevWeightState = animateFloatAsState(
        targetValue = weightFor(PlaybackButtonType.PREVIOUS),
        animationSpec = pressAnimationSpec,
        label = "prevWeight"
    )
    val playWeightState = animateFloatAsState(
        targetValue = weightFor(PlaybackButtonType.PLAY_PAUSE),
        animationSpec = pressAnimationSpec,
        label = "playWeight"
    )
    val nextWeightState = animateFloatAsState(
        targetValue = weightFor(PlaybackButtonType.NEXT),
        animationSpec = pressAnimationSpec,
        label = "nextWeight"
    )
    val playCornerState = animateDpAsState(
        targetValue = if (!playPauseVisualState) playPauseCornerPlaying else playPauseCornerPaused,
        animationSpec = defaultSpatialDpSpec,
        label = "playCorner"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(colorPreviousButton)
                        .clickable {
                            lastClicked = PlaybackButtonType.PREVIOUS
                            clickTrigger++
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPrevious()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = tintPreviousIcon,
                        modifier = Modifier.size(iconSize)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            clip = true
                            val corner = playCornerState.value
                            shape = AbsoluteSmoothCornerShape(
                                cornerRadiusTL = corner,
                                smoothnessAsPercentTL = 60,
                                cornerRadiusTR = corner,
                                smoothnessAsPercentTR = 60,
                                cornerRadiusBL = corner,
                                smoothnessAsPercentBL = 60,
                                cornerRadiusBR = corner,
                                smoothnessAsPercentBR = 60
                            )
                        }
                        .background(colorPlayPause)
                        .clickable {
                            lastClicked = PlaybackButtonType.PLAY_PAUSE
                            clickTrigger++
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    MorphingPlayPauseIcon(
                        isPlaying = playPauseVisualState,
                        tint = tintPlayPauseIcon,
                        size = playPauseIconSize,
                        motionScheme = motionScheme
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(colorNextButton)
                        .clickable {
                            lastClicked = PlaybackButtonType.NEXT
                            clickTrigger++
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNext()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Siguiente",
                        tint = tintNextIcon,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        ) { measurables, constraints ->
            val gapPx = 6.dp.roundToPx()
            val totalGapPx = gapPx * 2
            val availableWidth = (constraints.maxWidth - totalGapPx).coerceAtLeast(0)
            val heightPx = constraints.maxHeight

            val w0 = prevWeightState.value.coerceAtLeast(0.01f)
            val w1 = playWeightState.value.coerceAtLeast(0.01f)
            val w2 = nextWeightState.value.coerceAtLeast(0.01f)
            val weightSum = w0 + w1 + w2

            val width0 = (availableWidth * (w0 / weightSum)).toInt().coerceAtLeast(0)
            val width1 = (availableWidth * (w1 / weightSum)).toInt().coerceAtLeast(0)
            val width2 = (availableWidth - width0 - width1).coerceAtLeast(0)

            val placeable0 = measurables[0].measure(Constraints.fixed(width0, heightPx))
            val placeable1 = measurables[1].measure(Constraints.fixed(width1, heightPx))
            val placeable2 = measurables[2].measure(Constraints.fixed(width2, heightPx))

            layout(constraints.maxWidth, heightPx) {
                var x = 0
                placeable0.placeRelative(x, 0)
                x += width0 + gapPx
                placeable1.placeRelative(x, 0)
                x += width1 + gapPx
                placeable2.placeRelative(x, 0)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphingPlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    size: Dp,
    motionScheme: MotionScheme
) {
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            (scaleIn(
                initialScale = 0.68f,
                animationSpec = motionScheme.fastSpatialSpec()
            ) + fadeIn(
                animationSpec = motionScheme.fastEffectsSpec()
            )).togetherWith(
                scaleOut(
                    targetScale = 0.68f,
                    animationSpec = motionScheme.fastSpatialSpec()
                ) + fadeOut(
                    animationSpec = motionScheme.fastEffectsSpec()
                )
            )
        },
        label = "playPauseMorph"
    ) { playing ->
        Icon(
            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (playing) "Pausar" else "Reproducir",
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}
