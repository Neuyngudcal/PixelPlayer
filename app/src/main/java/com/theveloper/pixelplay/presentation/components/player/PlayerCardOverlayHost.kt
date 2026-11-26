package com.theveloper.pixelplay.presentation.components.player

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

/**
 * M3E in-card modal bottom sheet container that constrains presentation strictly inside the
 * parent Music Player card bounds (used in [NowPlayingLayoutMode.SIDE_DOCKED_PORTRAIT]) instead of
 * creating a full-screen window.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun PlayerCardOverlaySheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 8.dp,
    maxHeightFraction: Float = 0.92f,
    showDragHandle: Boolean = true,
    zIndex: Float = 30f,
    content: @Composable ColumnScope.() -> Unit
) {
    val motionScheme = remember { MotionScheme.expressive() }
    val spatialSpec = remember(motionScheme) { motionScheme.defaultSpatialSpec<Float>() }
    val fastSpatialSpec = remember(motionScheme) { motionScheme.fastSpatialSpec<Float>() }
    val effectsSpec = remember(motionScheme) { motionScheme.defaultEffectsSpec<Float>() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissDragThresholdPx = remember(density) { with(density) { 96.dp.toPx() } }

    // 1f = slid below card bounds, 0f = settled open
    val revealProgress = remember { Animatable(1f) }
    val scrimProgress = remember { Animatable(0f) }
    val dragOffsetPx = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    val latestOnDismiss by rememberUpdatedState(onDismissRequest)

    LaunchedEffect(Unit) {
        launch { scrimProgress.animateTo(1f, animationSpec = effectsSpec) }
        revealProgress.animateTo(0f, animationSpec = spatialSpec)
    }

    val dismissSheet: () -> Unit = {
        if (!isDismissing) {
            isDismissing = true
            scope.launch {
                val scrimJob = launch { scrimProgress.animateTo(0f, animationSpec = effectsSpec) }
                revealProgress.animateTo(1f, animationSpec = spatialSpec)
                scrimJob.join()
                latestOnDismiss()
            }
        }
    }

    BackHandler(enabled = true) {
        dismissSheet()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(zIndex)
    ) {
        val maxSheetHeight = (maxHeight * maxHeightFraction).coerceAtLeast(180.dp)
        val maxSheetHeightPx = with(density) { maxSheetHeight.toPx() }.coerceAtLeast(1f)
        val scrimColor = MaterialTheme.colorScheme.scrim

        // Scrim isolated inside the card
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val scrimAlpha = scrimProgress.value.coerceIn(0f, 1f)
                    val dragFraction = (dragOffsetPx.value / maxSheetHeightPx).coerceIn(0f, 1f)
                    alpha = (scrimAlpha * (1f - dragFraction * 0.7f) * 0.48f).coerceIn(0f, 0.48f)
                }
                .background(scrimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = dismissSheet
                )
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .graphicsLayer {
                    translationY = revealProgress.value * size.height + dragOffsetPx.value.coerceAtLeast(0f)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (showDragHandle) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(dismissDragThresholdPx) {
                                val velocityTracker = VelocityTracker()
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        velocityTracker.resetTracking()
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        scope.launch {
                                            dragOffsetPx.snapTo((dragOffsetPx.value + dragAmount).coerceAtLeast(0f))
                                        }
                                    },
                                    onDragEnd = {
                                        val velocityY = velocityTracker.calculateVelocity().y
                                        if (dragOffsetPx.value > dismissDragThresholdPx || velocityY > 520f) {
                                            dismissSheet()
                                        } else {
                                            scope.launch {
                                                dragOffsetPx.animateTo(0f, animationSpec = fastSpatialSpec)
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch {
                                            dragOffsetPx.animateTo(0f, animationSpec = fastSpatialSpec)
                                        }
                                    }
                                )
                            }
                            .padding(top = 10.dp, bottom = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(contentColor.copy(alpha = 0.28f))
                        )
                    }
                }
                content()
            }
        }
    }
}

/**
 * M3E in-card modal dialog container constrained strictly inside the parent Music Player card bounds
 * (used in [NowPlayingLayoutMode.SIDE_DOCKED_PORTRAIT]) so confirmations and search dialogs never
 * cover or block the left main app pane.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun PlayerCardOverlayDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val motionScheme = remember { MotionScheme.expressive() }
    val spatialSpec = remember(motionScheme) { motionScheme.defaultSpatialSpec<Float>() }
    val fastSpatialSpec = remember(motionScheme) { motionScheme.fastSpatialSpec<Float>() }
    val effectsSpec = remember(motionScheme) { motionScheme.defaultEffectsSpec<Float>() }
    val fastEffectsSpec = remember(motionScheme) { motionScheme.fastEffectsSpec<Float>() }
    val scope = rememberCoroutineScope()
    val reveal = remember { Animatable(0f) }
    val alphaProgress = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    val latestOnDismiss by rememberUpdatedState(onDismissRequest)

    LaunchedEffect(Unit) {
        launch { alphaProgress.animateTo(1f, animationSpec = effectsSpec) }
        reveal.animateTo(1f, animationSpec = spatialSpec)
    }

    val dismissDialog: () -> Unit = {
        if (!isDismissing) {
            isDismissing = true
            scope.launch {
                val alphaJob = launch { alphaProgress.animateTo(0f, animationSpec = fastEffectsSpec) }
                reveal.animateTo(0f, animationSpec = fastSpatialSpec)
                alphaJob.join()
                latestOnDismiss()
            }
        }
    }

    BackHandler(enabled = true) {
        dismissDialog()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(40f),
        contentAlignment = Alignment.Center
    ) {
        val maxDialogHeight = (maxHeight * 0.86f).coerceAtLeast(180.dp)
        val scrimColor = MaterialTheme.colorScheme.scrim

        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = (alphaProgress.value * 0.52f).coerceIn(0f, 0.52f)
                }
                .background(scrimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = dismissDialog
                )
        )

        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .fillMaxWidth()
                .heightIn(max = maxDialogHeight)
                .graphicsLayer {
                    val s = lerp(0.94f, 1f, reveal.value)
                    scaleX = s
                    scaleY = s
                    alpha = alphaProgress.value.coerceIn(0f, 1f)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(28.dp),
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                content = content
            )
        }
    }
}
