package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.LocalMaterialTheme
import com.theveloper.pixelplay.presentation.components.QueuePlaylistSongItem
import com.theveloper.pixelplay.presentation.components.SyncedLyricsList
import com.theveloper.pixelplay.ui.theme.PixelPlayGroupShapes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TabletNowPlayingSidePanel(
    activePanel: NowPlayingSidePanel,
    onSelectPanel: (NowPlayingSidePanel) -> Unit,
    onExpandFullPanel: (NowPlayingSidePanel) -> Unit,
    onClosePanel: () -> Unit,
    currentSong: Song,
    queue: ImmutableList<Song>,
    currentQueueSourceName: String,
    currentMediaItemIndex: Int,
    lyrics: Lyrics?,
    isLoadingLyrics: Boolean,
    playbackPositionFlow: StateFlow<Long>,
    isPlaying: Boolean,
    lyricsSyncOffset: Int,
    useAnimatedLyrics: Boolean,
    animatedLyricsBlurEnabled: Boolean,
    animatedLyricsBlurStrength: Float,
    onSeekTo: (Long) -> Unit,
    onFetchLyricsClick: () -> Unit,
    onPlayQueueSong: (Song, Int) -> Unit,
    onRemoveQueueSong: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = LocalMaterialTheme.current
    val motionScheme = MaterialTheme.motionScheme

    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        Surface(
            modifier = modifier
                .pointerInput(Unit) {
                    // Consume vertical drags inside the panel so the outer queue-open flick
                    // does not hijack scrolling inside inline Lyrics or Queue.
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, _ -> change.consume() },
                        onDragEnd = {},
                        onDragCancel = {}
                    )
                },
            shape = RoundedCornerShape(PixelPlayGroupShapes.PaneContainerRadius),
            color = scheme.onPrimaryContainer.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, scheme.onPrimaryContainer.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header: Morphing Lyrics / Up Next segmented tabs + Expand & Close buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(scheme.onPrimaryContainer.copy(alpha = 0.10f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SidePanelTabPill(
                            selected = activePanel == NowPlayingSidePanel.LYRICS,
                            label = stringResource(R.string.player_panel_lyrics),
                            onClick = { onSelectPanel(NowPlayingSidePanel.LYRICS) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Lyrics,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        SidePanelTabPill(
                            selected = activePanel == NowPlayingSidePanel.QUEUE,
                            label = stringResource(R.string.player_panel_up_next),
                            onClick = { onSelectPanel(NowPlayingSidePanel.QUEUE) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.rounded_queue_music_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(scheme.onPrimaryContainer.copy(alpha = 0.12f))
                                .clickable { onExpandFullPanel(activePanel) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInFull,
                                contentDescription = stringResource(R.string.player_panel_expand_fullscreen),
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(scheme.onPrimaryContainer.copy(alpha = 0.12f))
                                .clickable { onClosePanel() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.player_panel_close),
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                AnimatedContent(
                    targetState = activePanel,
                    transitionSpec = {
                        fadeIn(animationSpec = motionScheme.fastEffectsSpec()) togetherWith
                            fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    label = "SidePanelBodyContent"
                ) { panel ->
                    when (panel) {
                        NowPlayingSidePanel.LYRICS -> {
                            InlineLyricsPanelContent(
                                lyrics = lyrics,
                                isLoadingLyrics = isLoadingLyrics,
                                playbackPositionFlow = playbackPositionFlow,
                                isPlaying = isPlaying,
                                lyricsSyncOffset = lyricsSyncOffset,
                                useAnimatedLyrics = useAnimatedLyrics,
                                animatedLyricsBlurEnabled = animatedLyricsBlurEnabled,
                                animatedLyricsBlurStrength = animatedLyricsBlurStrength,
                                onSeekTo = onSeekTo,
                                onFetchLyricsClick = onFetchLyricsClick
                            )
                        }
                        NowPlayingSidePanel.QUEUE, NowPlayingSidePanel.NONE -> {
                            InlineQueuePanelContent(
                                currentSong = currentSong,
                                queue = queue,
                                currentQueueSourceName = currentQueueSourceName,
                                currentMediaItemIndex = currentMediaItemIndex,
                                isPlaying = isPlaying,
                                onPlayQueueSong = onPlayQueueSong,
                                onRemoveQueueSong = onRemoveQueueSong,
                                onExpandQueueSheet = { onExpandFullPanel(NowPlayingSidePanel.QUEUE) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SidePanelTabPill(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    leadingIcon: @Composable () -> Unit
) {
    val scheme = LocalMaterialTheme.current
    val motionScheme = MaterialTheme.motionScheme
    val cornerRadius by animateDpAsState(
        targetValue = if (selected) 50.dp else 14.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "SidePanelTabCorner"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            scheme.onPrimaryContainer
        } else {
            scheme.onPrimaryContainer.copy(alpha = 0f)
        },
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "SidePanelTabContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            scheme.primaryContainer
        } else {
            scheme.onPrimaryContainer.copy(alpha = 0.82f)
        },
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "SidePanelTabContent"
    )

    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        androidx.compose.material3.LocalContentColor.provides(contentColor).let {
            androidx.compose.runtime.CompositionLocalProvider(it) {
                leadingIcon()
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InlineLyricsPanelContent(
    lyrics: Lyrics?,
    isLoadingLyrics: Boolean,
    playbackPositionFlow: StateFlow<Long>,
    isPlaying: Boolean,
    lyricsSyncOffset: Int,
    useAnimatedLyrics: Boolean,
    animatedLyricsBlurEnabled: Boolean,
    animatedLyricsBlurStrength: Float,
    onSeekTo: (Long) -> Unit,
    onFetchLyricsClick: () -> Unit
) {
    val scheme = LocalMaterialTheme.current
    val motionScheme = MaterialTheme.motionScheme
    val syncedLines = lyrics?.synced
    val plainLines = lyrics?.plain

    when {
        !syncedLines.isNullOrEmpty() -> {
            val listState = rememberLazyListState()
            SyncedLyricsList(
                lines = syncedLines,
                listState = listState,
                playbackPositionFlow = playbackPositionFlow,
                isPlaying = isPlaying,
                lyricsSyncOffset = lyricsSyncOffset,
                accentColor = scheme.onPrimaryContainer,
                textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = null),
                onLineClick = { line -> onSeekTo(line.time.toLong()) },
                highlightZoneFraction = 0.24f,
                highlightOffsetDp = 16.dp,
                autoscrollAnimationSpec = motionScheme.defaultSpatialSpec(),
                useAnimatedLyrics = useAnimatedLyrics,
                animatedLyricsBlurEnabled = animatedLyricsBlurEnabled,
                animatedLyricsBlurStrength = animatedLyricsBlurStrength,
                immersiveMode = false,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 24.dp, horizontal = 8.dp)
            )
        }
        !plainLines.isNullOrEmpty() -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(plainLines) { _, line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = null),
                        color = scheme.onPrimaryContainer.copy(alpha = 0.88f)
                    )
                }
            }
        }
        isLoadingLyrics -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LoadingIndicator(color = scheme.onPrimaryContainer)
                    Text(
                        text = stringResource(R.string.lyrics_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.80f)
                    )
                }
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = stringResource(R.string.player_lyrics_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                    FilledTonalButton(
                        onClick = onFetchLyricsClick,
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = scheme.onPrimaryContainer,
                            contentColor = scheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.player_lyrics_fetch_action),
                            style = MaterialTheme.typography.labelLargeEmphasized
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InlineQueuePanelContent(
    currentSong: Song,
    queue: ImmutableList<Song>,
    currentQueueSourceName: String,
    currentMediaItemIndex: Int,
    isPlaying: Boolean,
    onPlayQueueSong: (Song, Int) -> Unit,
    onRemoveQueueSong: (String) -> Unit,
    onExpandQueueSheet: () -> Unit
) {
    val scheme = LocalMaterialTheme.current
    if (queue.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.player_queue_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onPrimaryContainer.copy(alpha = 0.80f),
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val resolvedCurrentIndex = remember(queue, currentSong.id, currentMediaItemIndex) {
        if (currentMediaItemIndex in queue.indices && queue[currentMediaItemIndex].id == currentSong.id) {
            currentMediaItemIndex
        } else {
            queue.indexOfFirst { it.id == currentSong.id }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(resolvedCurrentIndex) {
        if (resolvedCurrentIndex >= 0) {
            listState.scrollToItem((resolvedCurrentIndex - 1).coerceAtLeast(0))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentQueueSourceName.ifBlank { stringResource(R.string.queue_next_up_label) },
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = scheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = pluralStringResource(R.plurals.queue_tracks_lined_up, queue.size, queue.size),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onPrimaryContainer.copy(alpha = 0.72f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = queue,
                key = { index, song -> "${song.id}_$index" }
            ) { index, song ->
                val isCurrent = index == resolvedCurrentIndex
                QueuePlaylistSongItem(
                    onClick = { onPlayQueueSong(song, index) },
                    song = song,
                    isCurrentSong = isCurrent,
                    isPlaying = isPlaying,
                    isDragging = false,
                    onRemoveClick = { onRemoveQueueSong(song.id) },
                    dragHandle = {},
                    isReorderModeEnabled = false,
                    onMoreOptionsClick = { onExpandQueueSheet() },
                    isDragHandleVisible = false,
                    isRemoveButtonVisible = !isCurrent,
                    enableSwipeToDismiss = !isCurrent,
                    swipeStateIdentity = index.toLong(),
                    onDismissSong = { onRemoveQueueSong(song.id) },
                    isFromPlaylist = true,
                    containerColorOverride = if (isCurrent) {
                        scheme.onPrimaryContainer
                    } else {
                        scheme.onPrimaryContainer.copy(alpha = 0.10f)
                    },
                    titleColorOverride = if (isCurrent) {
                        scheme.primaryContainer
                    } else {
                        scheme.onPrimaryContainer
                    },
                    artistColorOverride = if (isCurrent) {
                        scheme.primaryContainer.copy(alpha = 0.78f)
                    } else {
                        scheme.onPrimaryContainer.copy(alpha = 0.72f)
                    },
                    playingEqColorOverride = scheme.primaryContainer,
                    moreButtonContainerColorOverride = if (isCurrent) {
                        scheme.primaryContainer.copy(alpha = 0.16f)
                    } else {
                        scheme.onPrimaryContainer.copy(alpha = 0.14f)
                    },
                    moreButtonContentColorOverride = if (isCurrent) {
                        scheme.primaryContainer
                    } else {
                        scheme.onPrimaryContainer
                    },
                    flatElevation = true
                )
            }
        }
    }
}

/**
 * Compact Lyrics & Queue preview card docked at the bottom of the Side-Docked Phone Card
 * (`SIDE_DOCKED_PORTRAIT`, matching the bottom Lyrics preview card in Spotify Tablet - Image 1).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SideDockedLyricsPreviewCard(
    lyrics: Lyrics?,
    currentPositionProvider: () -> Long,
    lyricsSyncOffset: Int,
    onLyricsCardClick: () -> Unit,
    onQueueButtonClick: () -> Unit,
    onExpandLyricsFullClick: () -> Unit = onLyricsCardClick,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val scheme = LocalMaterialTheme.current
    val motionScheme = MaterialTheme.motionScheme
    val latestPositionProvider by rememberUpdatedState(currentPositionProvider)
    val previewLine by remember(lyrics, lyricsSyncOffset) {
        derivedStateOf {
            val synced = lyrics?.synced
            val plain = lyrics?.plain
            when {
                !synced.isNullOrEmpty() -> {
                    val posMs = (latestPositionProvider() + lyricsSyncOffset.toLong()).coerceAtLeast(0L)
                    var low = 0
                    var high = synced.lastIndex
                    var rightmostIdx = -1
                    while (low <= high) {
                        val mid = (low + high) ushr 1
                        if (synced[mid].time <= posMs) {
                            rightmostIdx = mid
                            low = mid + 1
                        } else {
                            high = mid - 1
                        }
                    }
                    var matchedLine: String? = null
                    var idx = rightmostIdx
                    while (idx >= 0) {
                        val candidate = synced[idx]
                        if (candidate.line.isNotBlank()) {
                            matchedLine = candidate.line
                            break
                        }
                        idx--
                    }
                    matchedLine ?: synced.firstOrNull { it.line.isNotBlank() }?.line ?: ""
                }
                !plain.isNullOrEmpty() -> {
                    plain.firstOrNull { it.isNotBlank() } ?: ""
                }
                else -> ""
            }
        }
    }
    val emptyLyricsLabel = stringResource(R.string.player_lyrics_empty_title)
    val displayLine = previewLine.ifBlank { emptyLyricsLabel }
    val buttonSize = if (compact) 30.dp else 34.dp
    val cornerRadius = if (compact) 18.dp else 22.dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(onClick = onLyricsCardClick),
        shape = RoundedCornerShape(cornerRadius),
        color = scheme.onPrimaryContainer.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, scheme.onPrimaryContainer.copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.player_panel_lyrics),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(buttonSize)
                            .clip(CircleShape)
                            .background(scheme.onPrimaryContainer.copy(alpha = 0.14f))
                            .clickable(onClick = onQueueButtonClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_queue_music_24),
                            contentDescription = stringResource(R.string.player_panel_up_next),
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(if (compact) 15.dp else 17.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(buttonSize)
                            .clip(CircleShape)
                            .background(scheme.onPrimaryContainer.copy(alpha = 0.14f))
                            .clickable(onClick = onExpandLyricsFullClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.player_panel_lyrics),
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = displayLine,
                transitionSpec = {
                    fadeIn(animationSpec = motionScheme.fastEffectsSpec()) togetherWith
                        fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                },
                label = "SideDockedPreviewLyricLine"
            ) { lineText ->
                Text(
                    text = lineText,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = null),
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onPrimaryContainer.copy(alpha = if (previewLine.isNotBlank()) 0.92f else 0.65f),
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
