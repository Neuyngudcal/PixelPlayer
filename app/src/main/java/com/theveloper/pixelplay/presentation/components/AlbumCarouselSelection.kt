package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import coil.size.Size
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.presentation.components.scoped.PrefetchAlbumNeighbors
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.first

import com.theveloper.pixelplay.data.preferences.AlbumArtQuality
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

// ====== TIPOS/STATE DEL CARRUSEL (wrapper para mantener compatibilidad) ======

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberRoundedParallaxCarouselState(
    initialPage: Int,
    pageCount: () -> Int
): CarouselState = rememberCarouselState(initialItem = initialPage, itemCount = pageCount)

// ====== TU SECCIÓN: ACOPLADA AL NUEVO API ======

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumCarouselSection(
    currentSong: Song?,
    queue: ImmutableList<Song>,
    expansionFraction: Float,
    currentMediaItemIndex: Int = -1,
    requestedScrollIndex: Int? = null,
    onSongSelected: (Song, Int) -> Unit,
    onAlbumClick: (Song) -> Unit = {},
    modifier: Modifier = Modifier,
    carouselStyle: String = CarouselStyle.NO_PEEK,
    itemSpacing: Dp = 8.dp,
    albumArtQuality: AlbumArtQuality = AlbumArtQuality.MEDIUM,
    onSwipePreviewChanged: (String?, () -> Float) -> Unit = { _, _ -> }
) {
    if (queue.isEmpty()) return

    // Mantiene compatibilidad con tu llamada actual
    val initialIndex = remember(currentSong?.id, currentMediaItemIndex, queue) {
        resolveCurrentQueueIndex(
            currentSong = currentSong,
            currentMediaItemIndex = currentMediaItemIndex,
            queue = queue
        )
    }

    val carouselState = rememberRoundedParallaxCarouselState(
        initialPage = initialIndex,
        pageCount = { queue.size }
    )

    // Use M3 Expressive slowSpatialSpec (damping = 0.8f, stiffness = 200f) for the full-width
    // album art hero transition so programmatic skips accelerate gracefully instead of snapping too abruptly.
    val motionScheme = MaterialTheme.motionScheme
    val carouselAnimationSpec = remember(motionScheme) {
        motionScheme.slowSpatialSpec<Float>()
    }

    // Calculate target size based on quality
    val targetSize = remember(albumArtQuality) {
        if (albumArtQuality.maxSize == 0) SafeOriginalAlbumArtSize
        else Size(albumArtQuality.maxSize, albumArtQuality.maxSize)
    }

    // Player -> Carousel
    val currentSongIndex = remember(currentSong?.id, currentMediaItemIndex, queue) {
        resolveCurrentQueueIndex(
            currentSong = currentSong,
            currentMediaItemIndex = currentMediaItemIndex,
            queue = queue
        )
    }
    val requestedTargetIndex = remember(requestedScrollIndex, queue) {
        requestedScrollIndex?.takeIf { it in queue.indices }
    }
    val effectiveTargetIndex = requestedTargetIndex ?: currentSongIndex
    val carouselItemKeys = remember(queue) {
        buildQueueOccurrenceKeys(queue)
    }

    val prefetchRadius = remember(albumArtQuality) {
        if (albumArtQuality == AlbumArtQuality.ORIGINAL || albumArtQuality == AlbumArtQuality.HIGH) 1 else 2
    }

    PrefetchAlbumNeighbors(
        isActive = expansionFraction > 0.08f,
        pagerState = carouselState.pagerState,
        queue = queue,
        radius = prefetchRadius,
        targetSize = targetSize,
        anchorIndex = effectiveTargetIndex
    )
    var programmaticScrollInProgress by remember { mutableStateOf(false) }
    var lastSettledSongId by remember { mutableStateOf(currentSong?.id) }

    // Track whether the *user* is actively dragging the carousel (via the pager's interaction
    // source) so we can tell a real swipe apart from our own programmatic skip/seek scrolls.
    var isUserDragging by remember { mutableStateOf(false) }
    var userDragSettlePending by remember { mutableStateOf(false) }
    var settledSwipePage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(carouselState) {
        carouselState.pagerState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    settledSwipePage = null
                    isUserDragging = true
                    userDragSettlePending = true
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> isUserDragging = false
            }
        }
    }

    LaunchedEffect(currentSongIndex, requestedTargetIndex) {
        if (requestedTargetIndex != null || settledSwipePage == currentSongIndex) {
            settledSwipePage = null
        }
    }

    LaunchedEffect(settledSwipePage) {
        val pendingPage = settledSwipePage ?: return@LaunchedEffect
        kotlinx.coroutines.delay(800)
        if (settledSwipePage == pendingPage) {
            settledSwipePage = null
        }
    }

    val latestCurrentSongIndex = rememberUpdatedState(currentSongIndex)
    val latestSettledSwipePage = rememberUpdatedState(settledSwipePage)
    val latestIsSwipeActive = rememberUpdatedState(
        isUserDragging || userDragSettlePending || settledSwipePage != null
    )
    val swipeProgressProvider = remember(carouselState) {
        {
            val settledTarget = latestSettledSwipePage.value
            val continuousPage = carouselState.pagerState.currentPage +
                carouselState.pagerState.currentPageOffsetFraction
            if (settledTarget != null && !carouselState.pagerState.isScrollInProgress) {
                1f
            } else if (latestIsSwipeActive.value) {
                val anchor = latestCurrentSongIndex.value
                val delta = continuousPage - anchor
                val absDelta = abs(delta)
                if (absDelta <= 1f) {
                    absDelta.coerceIn(0f, 1f)
                } else {
                    val target = settledTarget ?: (anchor + delta.roundToInt())
                    (1f - abs(target - continuousPage)).coerceIn(0f, 1f)
                }
            } else {
                0f
            }
        }
    }

    val latestOnSwipePreviewChanged by rememberUpdatedState(onSwipePreviewChanged)
    LaunchedEffect(carouselState, queue, currentSongIndex, requestedTargetIndex) {
        snapshotFlow {
            val swipeActive = (isUserDragging || userDragSettlePending || settledSwipePage != null) &&
                requestedTargetIndex == null
            if (!swipeActive) {
                null
            } else {
                val settledTarget = settledSwipePage
                val anchor = currentSongIndex
                val continuousPage = carouselState.pagerState.currentPage +
                    carouselState.pagerState.currentPageOffsetFraction
                val delta = continuousPage - anchor
                val targetIdx = when {
                    settledTarget != null -> settledTarget
                    abs(delta) < 0.005f -> null
                    delta > 0f -> (anchor + ceil(delta.toDouble()).toInt()).coerceIn(0, queue.lastIndex)
                    else -> (anchor + floor(delta.toDouble()).toInt()).coerceIn(0, queue.lastIndex)
                }?.takeIf { it != anchor && it in queue.indices }
                targetIdx?.let { queue[it].albumArtUriString }
            }
        }
            .distinctUntilChanged()
            .collect { targetUri ->
                latestOnSwipePreviewChanged(targetUri, swipeProgressProvider)
            }
    }

    // Player -> Carousel. Retargets continuously: each press cancels the in-flight animation and
    // animates from the current position to the new target, so a rapid skip burst scrolls smoothly
    // through the albums instead of freezing. We only ever defer to an active *user* drag — never
    // to our own (cancelled) programmatic scroll. Key on effectiveTargetIndex and queue only so that
    // clearing requestedTargetIndex (after the player confirms the track) does NOT cancel and restart
    // the in-flight spring animation mid-scroll.
    LaunchedEffect(effectiveTargetIndex, queue) {
        if (isUserDragging) {
            snapshotFlow { isUserDragging }.first { !it }
        }
        
        val isAlreadyAtTarget = carouselState.pagerState.currentPage == effectiveTargetIndex &&
                carouselState.pagerState.currentPageOffsetFraction == 0f
        if (!isAlreadyAtTarget) {
            val isShiftOnly = currentSong?.id != null && 
                              currentSong.id == lastSettledSongId && 
                              requestedTargetIndex == null
            
            if (isShiftOnly) {
                // Same song moved to a new index: scroll instantly to maintain focus
                // and avoid showing the wrong item for the duration of an animation.
                carouselState.pagerState.scrollToPage(effectiveTargetIndex)
            } else {
                programmaticScrollInProgress = true
                try {
                    carouselState.animateScrollToItem(effectiveTargetIndex, animationSpec = carouselAnimationSpec)
                } finally {
                    programmaticScrollInProgress = false
                }
            }
        }
        lastSettledSongId = queue.getOrNull(effectiveTargetIndex)?.id ?: currentSong?.id
    }

    val latestOnSongSelected by rememberUpdatedState(onSongSelected)
    val latestRequestedScrollIndex by rememberUpdatedState(requestedScrollIndex)
    val hapticFeedback = LocalHapticFeedback.current
    // Carousel -> Player (cuando se detiene el scroll)
    LaunchedEffect(carouselState, currentSongIndex, queue) {
        snapshotFlow { carouselState.pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { !it }
            .collect {
                // Only a settle that followed a real user drag changes the song. Programmatic
                // skip/seek scrolls (and their mid-burst cancellations) must not feed back into
                // the player — it already drove them — otherwise a rapid skip burst would fire
                // spurious selections and fight the optimistic carousel index.
                if (!userDragSettlePending) return@collect
                userDragSettlePending = false
                val settled = carouselState.pagerState.currentPage
                if (latestRequestedScrollIndex == settled) return@collect
                if (settled != currentSongIndex) {
                    settledSwipePage = settled
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    queue.getOrNull(settled)?.let { song ->
                        latestOnSongSelected(song, settled)
                    }
                }
            }
    }

    val corner = 18.dp//lerp(36.dp, 15.dp, expansionFraction.coerceIn(0f, 1f))

    BoxWithConstraints(modifier = modifier) {
        val availableWidth = this.maxWidth

        RoundedHorizontalMultiBrowseCarousel(
            state = carouselState,
            modifier = Modifier.fillMaxSize(), // Fill the space provided by the parent's modifier
            itemSpacing = itemSpacing,
            itemCornerRadius = corner,
            suppressNoPeekSettleCorrection = requestedTargetIndex != null || programmaticScrollInProgress,
            carouselStyle = if (carouselState.pagerState.pageCount == 1) CarouselStyle.NO_PEEK else carouselStyle, // Handle single-item case
            carouselWidth = availableWidth, // Pass the full width for layout calculations
            itemKey = { index -> carouselItemKeys.getOrNull(index) ?: "queue_item_$index" },
            content = { index ->
                val song = queue[index]
                val isTelegramArt = song.albumArtUriString?.startsWith("telegram_art") == true
                Box(
                    Modifier
                        .fillMaxSize()
                        .aspectRatio(1f)
                        .clickable(
                            enabled = song.albumId != -1L,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (carouselState.pagerState.currentPage == index) {
                                    onAlbumClick(song)
                                }
                            }
                        )
                ) { // Enforce 1:1 aspect ratio for the item itself
                    OptimizedAlbumArt(
                        uri = song.albumArtUriString,
                        title = song.title,
                        modifier = Modifier.fillMaxSize(),
                        targetSize = targetSize,
                        placeholderModel = if (isTelegramArt) {
                            "${song.albumArtUriString}?quality=thumb"
                        } else null,
                        crossfadeDurationMillis = if (isTelegramArt) 350 else 0,
                        useSubcomposition = false
                    )
                }
            }
        )
    }
}

private fun resolveCurrentQueueIndex(
    currentSong: Song?,
    currentMediaItemIndex: Int,
    queue: ImmutableList<Song>
): Int {
    val songId = currentSong?.id ?: return 0
    if (currentMediaItemIndex in queue.indices && queue[currentMediaItemIndex].id == songId) {
        return currentMediaItemIndex
    }
    return queue.indexOfFirst { it.id == songId }
        .takeIf { it >= 0 }
        ?: queue.indexOf(currentSong)
            .takeIf { it >= 0 }
        ?: 0
}

private fun buildQueueOccurrenceKeys(queue: ImmutableList<Song>): List<String> {
    val occurrencesBySongId = HashMap<String, Int>()
    return queue.mapIndexed { index, song ->
        val occurrence = occurrencesBySongId.getOrDefault(song.id, 0)
        occurrencesBySongId[song.id] = occurrence + 1
        "queue_carousel_${song.id}_${occurrence}_${song.albumArtUriString.orEmpty().hashCode()}_$index"
    }
}
