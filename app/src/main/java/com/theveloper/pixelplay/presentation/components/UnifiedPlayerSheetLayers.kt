package com.theveloper.pixelplay.presentation.components

import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.FullPlayerLoadingTweaks
import com.theveloper.pixelplay.presentation.components.player.FullPlayerContent
import com.theveloper.pixelplay.presentation.components.player.TabletPlayerDisplayMode
import com.theveloper.pixelplay.presentation.components.scoped.FullPlayerVisualState
import com.theveloper.pixelplay.presentation.components.scoped.rememberFullPlayerRuntimePolicy
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
internal fun BoxScope.UnifiedPlayerMiniAndFullLayers(
    currentSong: Song?,
    miniPlayerScheme: ColorScheme?,
    overallSheetTopCornerRadiusProvider: () -> Dp,
    infrequentPlayerState: StablePlayerState,
    isCastConnecting: Boolean,
    isPreparingPlayback: Boolean,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    albumColorScheme: ColorScheme,
    bottomSheetOpenFractionProvider: () -> Float,
    fullPlayerVisualState: FullPlayerVisualState,
    containerHeight: Dp,
    currentQueueSourceName: String,
    currentSheetContentState: PlayerSheetState,
    carouselStyle: String,
    fullPlayerLoadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean = false,
    playerViewModel: PlayerViewModel,
    currentPositionProvider: () -> Long,
    isFavorite: Boolean,
    shouldRenderFullPlayer: Boolean = true,
    currentHorizontalPaddingStartPxProvider: () -> Float,
    currentHorizontalPaddingEndPxProvider: () -> Float,
    supportsSideDockedMode: Boolean = false,
    screenWidthPx: Float = 0f,
    targetExpandedWidthPxProvider: () -> Float = { screenWidthPx },
    targetExpandedHeightPxProvider: () -> Float = { 0f },
    onShowQueueClicked: () -> Unit,
    onShowCastClicked: () -> Unit,
    onSwipePreviewChanged: (String?, () -> Float) -> Unit = { _, _ -> },
    tabletDisplayMode: TabletPlayerDisplayMode = TabletPlayerDisplayMode.FULLSCREEN,
    onToggleTabletDisplayMode: () -> Unit = {},
    fullScreenFractionProvider: () -> Float = { 1f },
    onOverlayVisibilityChanged: (Boolean) -> Unit = {}
) {
    val shouldRenderMiniPlayer by remember {
        derivedStateOf { playerContentExpansionFraction.value < 0.28f }
    }

    currentSong?.let { currentSongNonNull ->
        if (shouldRenderMiniPlayer) {
            miniPlayerScheme?.let { readyScheme ->
                CompositionLocalProvider(
                    LocalMaterialTheme provides readyScheme
                ) {
                    val miniPlayerZIndex by remember {
                        derivedStateOf {
                            if (playerContentExpansionFraction.value < 0.5f) 1f else 0f
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(MiniPlayerHeight)
                            .graphicsLayer {
                                val f = playerContentExpansionFraction.value.coerceIn(0f, 1f)
                                alpha = (1f - (f / 0.22f)).coerceIn(0f, 1f)
                                translationY = -f * 24f
                            }
                            .zIndex(miniPlayerZIndex)
                    ) {
                        val isMiniPlayerVisible by remember {
                            derivedStateOf { playerContentExpansionFraction.value < 0.01f }
                        }
                        MiniPlayerContentInternal(
                            song = currentSongNonNull,
                            isPlaying = infrequentPlayerState.isPlaying,
                            isCastConnecting = isCastConnecting,
                            isPreparingPlayback = isPreparingPlayback,
                            onPlayPause = { playerViewModel.playPause() },
                            onPrevious = { playerViewModel.previousSong() },
                            onNext = { playerViewModel.nextSong() },
                            canScroll = isMiniPlayerVisible && infrequentPlayerState.isPlaying,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        if (shouldRenderFullPlayer) {
            CompositionLocalProvider(
                LocalMaterialTheme provides albumColorScheme
            ) {
                val fullPlayerScaleProvider = remember(bottomSheetOpenFractionProvider) {
                    { lerp(1f, 0.95f, bottomSheetOpenFractionProvider().coerceIn(0f, 1f)) }
                }

                val fullPlayerZIndex by remember {
                    derivedStateOf {
                        if (playerContentExpansionFraction.value >= 0.5f) 1f else 0f
                    }
                }
                val fullPlayerOffset by remember {
                    derivedStateOf {
                        if (playerContentExpansionFraction.value <= 0.01f) IntOffset(0, 10000)
                        else IntOffset.Zero
                    }
                }
                val fullPlayerRuntimePolicy = rememberFullPlayerRuntimePolicy(
                    currentSheetState = currentSheetContentState,
                    expansionFraction = playerContentExpansionFraction,
                    bottomSheetOpenFractionProvider = bottomSheetOpenFractionProvider
                )

                // Scoped queue collection: only the FullPlayer subtree observes
                // the queue. Sibling MiniPlayer composable and the whole
                // UnifiedPlayerSheetV2 caller are insulated from queue churn.
                val currentPlaybackQueue by playerViewModel.queueFlow
                    .collectAsStateWithLifecycle()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Always measure FullPlayerContent at its stable expanded target dimensions
                        // (even while pre-warmed in COLLAPSED state) so Frame 1 of sheet expansion
                        // performs zero re-measurement of FullPlayerContent.
                        .layout { measurable, constraints ->
                            val targetContentHeightPx = if (supportsSideDockedMode) {
                                targetExpandedHeightPxProvider()
                                    .roundToInt()
                                    .coerceAtLeast(1)
                            } else {
                                containerHeight.roundToPx().coerceAtLeast(1)
                            }
                            val measureWidth = if (supportsSideDockedMode) {
                                targetExpandedWidthPxProvider()
                                    .roundToInt()
                                    .takeIf { it > 0 } ?: screenWidthPx.roundToInt().coerceAtLeast(1)
                            } else {
                                screenWidthPx.roundToInt().takeIf { it > 0 } ?: constraints.maxWidth
                            }
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = measureWidth,
                                    maxWidth = measureWidth,
                                    minHeight = targetContentHeightPx,
                                    maxHeight = targetContentHeightPx
                                )
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                val xOffset = if (supportsSideDockedMode) {
                                    ((constraints.maxWidth - measureWidth) / 2f).roundToInt()
                                } else {
                                    -currentHorizontalPaddingStartPxProvider().toInt()
                                }
                                placeable.placeRelative(xOffset, 0)
                            }
                        }
                        .graphicsLayer {
                            val combinedScale = fullPlayerScaleProvider() * fullPlayerVisualState.contentScale
                            alpha = fullPlayerVisualState.contentAlpha
                            translationY = fullPlayerVisualState.translationY
                            scaleX = combinedScale
                            scaleY = combinedScale
                        }
                        .zIndex(fullPlayerZIndex)
                        .offset { fullPlayerOffset }
                ) {
                    val latestInfrequentPlayerState = rememberUpdatedState(infrequentPlayerState)
                    val latestIsFavorite = rememberUpdatedState(isFavorite)
                    val expansionFractionProvider = remember(playerContentExpansionFraction) {
                        { playerContentExpansionFraction.value }
                    }
                    val isPlayingProvider = remember {
                        { latestInfrequentPlayerState.value.isPlaying }
                    }
                    val isBufferingProvider = remember {
                        { latestInfrequentPlayerState.value.isBuffering }
                    }
                    val playWhenReadyProvider = remember {
                        { latestInfrequentPlayerState.value.playWhenReady }
                    }
                    val repeatModeProvider = remember {
                        { latestInfrequentPlayerState.value.repeatMode }
                    }
                    val isShuffleEnabledProvider = remember {
                        { latestInfrequentPlayerState.value.isShuffleEnabled }
                    }
                    val totalDurationProvider = remember {
                        { latestInfrequentPlayerState.value.totalDuration }
                    }
                    val lyricsProvider = remember {
                        { latestInfrequentPlayerState.value.lyrics }
                    }
                    val isFavoriteProvider = remember {
                        { latestIsFavorite.value }
                    }
                    val onPlayPause = remember(playerViewModel) { playerViewModel::playPause }
                    val onSeek = remember(playerViewModel) { playerViewModel::seekTo }
                    val onNext = remember(playerViewModel) { playerViewModel::nextSong }
                    val onPrevious = remember(playerViewModel) { playerViewModel::previousSong }
                    val onCollapse = remember(playerViewModel) {
                        { playerViewModel.collapsePlayerSheet() }
                    }
                    val onShuffleToggle = remember(playerViewModel) {
                        { playerViewModel.toggleShuffle() }
                    }
                    val onRepeatToggle = remember(playerViewModel) { playerViewModel::cycleRepeatMode }
                    val onFavoriteToggle = remember(playerViewModel) { playerViewModel::toggleFavorite }

                    FullPlayerContent(
                        currentSong = currentSongNonNull,
                        currentPlaybackQueue = currentPlaybackQueue,
                        currentQueueSourceName = currentQueueSourceName,
                        currentMediaItemIndex = infrequentPlayerState.currentMediaItemIndex,
                        isShuffleEnabled = infrequentPlayerState.isShuffleEnabled,
                        shuffleTransitionInProgress = infrequentPlayerState.isShuffleTransitionInProgress,
                        repeatMode = infrequentPlayerState.repeatMode,
                        allowRealtimeUpdates = fullPlayerRuntimePolicy.allowRealtimeUpdates,
                        expansionFractionProvider = expansionFractionProvider,
                        currentSheetState = currentSheetContentState,
                        carouselStyle = carouselStyle,
                        loadingTweaks = fullPlayerLoadingTweaks,
                        isSheetDragGestureActive = isSheetDragGestureActive,
                        playerViewModel = playerViewModel,
                        currentPositionProvider = currentPositionProvider,
                        isPlayingProvider = isPlayingProvider,
                        isBufferingProvider = isBufferingProvider,
                        playWhenReadyProvider = playWhenReadyProvider,
                        repeatModeProvider = repeatModeProvider,
                        isShuffleEnabledProvider = isShuffleEnabledProvider,
                        totalDurationProvider = totalDurationProvider,
                        lyricsProvider = lyricsProvider,
                        isCastConnecting = isCastConnecting,
                        isFavoriteProvider = isFavoriteProvider,
                        onPlayPause = onPlayPause,
                        onSeek = onSeek,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onCollapse = onCollapse,
                        onShowQueueClicked = onShowQueueClicked,
                        onShowCastClicked = onShowCastClicked,
                        onShuffleToggle = onShuffleToggle,
                        onRepeatToggle = onRepeatToggle,
                        onFavoriteToggle = onFavoriteToggle,
                        onSwipePreviewChanged = onSwipePreviewChanged,
                        tabletDisplayMode = tabletDisplayMode,
                        onToggleTabletDisplayMode = onToggleTabletDisplayMode,
                        fullScreenFractionProvider = fullScreenFractionProvider,
                        onOverlayVisibilityChanged = onOverlayVisibilityChanged
                    )
                }
            }
        }
    }
}
