package com.theveloper.pixelplay.presentation.components.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import com.theveloper.pixelplay.data.model.Lyrics
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LoadingIndicator
// import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults // Removed
// import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState // Removed
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayWindowInfo
import com.theveloper.pixelplay.ui.theme.LocalSpacing
import com.theveloper.pixelplay.ui.theme.WindowWidthTier
import com.theveloper.pixelplay.data.diagnostics.AdvancedPerformanceDiagnostics
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.AlbumArtQuality
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.FullPlayerLoadingTweaks
import com.theveloper.pixelplay.presentation.components.AlbumCarouselSection
import com.theveloper.pixelplay.presentation.components.AutoScrollingTextOnDemand
import com.theveloper.pixelplay.presentation.components.LocalMaterialTheme
import com.theveloper.pixelplay.presentation.components.LyricsSheet
import com.theveloper.pixelplay.presentation.components.lyrics.LyricsProviderPrioritySheet
import com.theveloper.pixelplay.presentation.components.scoped.rememberSmoothProgress
import com.theveloper.pixelplay.presentation.components.subcomps.FetchLyricsDialog
import com.theveloper.pixelplay.presentation.viewmodel.LyricsSearchUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.utils.AudioMetaUtils.mimeTypeToFormat
import com.theveloper.pixelplay.utils.LyricsImportFailureReason
import com.theveloper.pixelplay.utils.LyricsImportSecurity
import com.theveloper.pixelplay.utils.LyricsImportValidationResult
import com.theveloper.pixelplay.utils.ValidatedLyricsImport
import com.theveloper.pixelplay.utils.formatDuration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber
import java.util.Locale
import kotlin.math.roundToLong
import com.theveloper.pixelplay.presentation.components.WavySliderExpressive
import com.theveloper.pixelplay.presentation.components.ToggleSegmentButton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

private const val PREVIOUS_TRACK_RESTART_THRESHOLD_MS = 10_000L
private const val SKIP_COMMAND_GUARD_MS = 96L

private enum class SkipDirection { PREVIOUS, NEXT }

private suspend fun validateLyricsImport(
    context: Context,
    uri: Uri
): LyricsImportValidationResult = withContext(Dispatchers.IO) {
    val contentResolver = context.contentResolver

    var fileName = ""
    var fileSize: Long? = null
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            fileName = if (nameIndex != -1) cursor.getString(nameIndex) else ""
            fileSize = if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                cursor.getLong(sizeIndex)
            } else {
                null
            }
        }
    }

    contentResolver.openInputStream(uri)?.use { inputStream ->
        LyricsImportSecurity.validateImportedLyricsFile(
            fileName = fileName,
            mimeType = contentResolver.getType(uri),
            inputStream = inputStream,
            reportedSizeBytes = fileSize
        )
    } ?: LyricsImportValidationResult.Invalid(LyricsImportFailureReason.EMPTY_CONTENT)
}

@androidx.annotation.OptIn(UnstableApi::class)
@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullPlayerContent(
    currentSong: Song?,
    currentPlaybackQueue: ImmutableList<Song>,
    currentQueueSourceName: String,
    currentMediaItemIndex: Int = -1,
    isShuffleEnabled: Boolean,
    shuffleTransitionInProgress: Boolean,
    repeatMode: Int,
    allowRealtimeUpdates: Boolean = true,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    carouselStyle: String,
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean = false,
    playerViewModel: PlayerViewModel, // For stable state like totalDuration and lyrics
    // State Providers
    currentPositionProvider: () -> Long,
    isPlayingProvider: () -> Boolean,
    isBufferingProvider: () -> Boolean = { false },
    playWhenReadyProvider: () -> Boolean,
    isFavoriteProvider: () -> Boolean,
    repeatModeProvider: () -> Int,
    isShuffleEnabledProvider: () -> Boolean,
    totalDurationProvider: () -> Long,
    lyricsProvider: () -> Lyrics? = { null }, 
    // State
    isCastConnecting: Boolean = false,
    // Event Handlers
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCollapse: () -> Unit,
    onShowQueueClicked: () -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit,
    onShowCastClicked: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onSwipePreviewChanged: (String?, () -> Float) -> Unit = { _, _ -> },
    tabletDisplayMode: TabletPlayerDisplayMode = TabletPlayerDisplayMode.FULLSCREEN,
    onToggleTabletDisplayMode: () -> Unit = {}
) {
    var retainedSong by remember { mutableStateOf(currentSong) }
    LaunchedEffect(currentSong?.id) {
        if (currentSong != null) {
            retainedSong = currentSong
        }
    }

    val song = currentSong ?: retainedSong ?: return // Keep the player visible while transitioning
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showArtistPicker by rememberSaveable { mutableStateOf(false) }
    
    val lyricsSearchUiState by playerViewModel.lyricsSearchUiState.collectAsStateWithLifecycle()

    // Single subscription — replaces 11 independent collectAsStateWithLifecycle calls.
    // distinctUntilChanged in the ViewModel ensures this only emits when something
    // actually changed, batching multiple rapid updates into one recomposition.
    val fullPlayerSlice by playerViewModel.fullPlayerSlice.collectAsStateWithLifecycle()
    val currentSongArtists = fullPlayerSlice.currentSongArtists
    val lyricsSyncOffset = fullPlayerSlice.lyricsSyncOffset
    val albumArtQuality = fullPlayerSlice.albumArtQuality
    val playbackAudioMetadata = fullPlayerSlice.audioMetadata
    val showPlayerFileInfo = fullPlayerSlice.showPlayerFileInfo
    val immersiveLyricsEnabled = fullPlayerSlice.immersiveLyricsEnabled
    val immersiveLyricsTimeout = fullPlayerSlice.immersiveLyricsTimeout
    val isImmersiveTemporarilyDisabled = fullPlayerSlice.isImmersiveTemporarilyDisabled
    val isRemotePlaybackActive = fullPlayerSlice.isRemotePlaybackActive
    val selectedRouteName = fullPlayerSlice.selectedRouteName
    val isBluetoothEnabled = fullPlayerSlice.isBluetoothEnabled
    val bluetoothName = fullPlayerSlice.bluetoothName
    val navigationBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val queueGestureBottomExclusion = maxOf(20.dp, navigationBarBottomInset + 8.dp)
    val queueGestureBottomExclusionPx = with(LocalDensity.current) {
        queueGestureBottomExclusion.toPx()
    }

    var showFetchLyricsDialog by remember { mutableStateOf(false) }
    var showProviderPrioritySheet by remember { mutableStateOf(false) }
    var totalDrag by remember { mutableStateOf(0f) }

    val context = LocalContext.current
    val fileImportScope = rememberCoroutineScope()
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                fileImportScope.launch {
                    try {
                        val validation = validateLyricsImport(context, it)
                        val validatedImport: ValidatedLyricsImport = when (validation) {
                            is LyricsImportValidationResult.Valid -> validation.value
                            is LyricsImportValidationResult.Invalid -> {
                                playerViewModel.sendToast(
                                    LyricsImportSecurity.messageFor(validation.reason)
                                )
                                return@launch
                            }
                        }

                        val currentSongId = currentSong?.id?.toLongOrNull()
                        if (currentSongId == null) {
                            playerViewModel.sendToast("No song selected for lyrics import.")
                            return@launch
                        }

                        playerViewModel.importLyricsFromFile(currentSongId, validatedImport)
                        showFetchLyricsDialog = false
                        showLyricsSheet = true
                    } catch (e: Exception) {
                        Timber.e(e, "Error reading imported lyrics file")
                        playerViewModel.sendToast("Error reading file.")
                    }
                }
            }
        }
    )

    // totalDurationValue is derived from stablePlayerState, so it's fine.
    // OPTIMIZATION: Use passed provider instead of collecting flow
    val totalDurationValue = totalDurationProvider()

    // Window-size-aware 2-column & 3-mode layout (Mini <-> Side-Docked Phone Card <-> Fullscreen)
    val windowInfo = LocalPixelPlayWindowInfo.current
    val layoutMetrics = remember(windowInfo, tabletDisplayMode) {
        resolveNowPlayingLayoutMetrics(windowInfo, tabletDisplayMode)
    }
    val isLandscape = layoutMetrics.mode == NowPlayingLayoutMode.TABLET_LANDSCAPE_FULLSCREEN ||
        layoutMetrics.mode == NowPlayingLayoutMode.COMPACT_LANDSCAPE
    val isSideDockedCardMode = layoutMetrics.mode == NowPlayingLayoutMode.SIDE_DOCKED_PORTRAIT
    var activeSidePanel by rememberSaveable { mutableStateOf(NowPlayingSidePanel.NONE) }
    LaunchedEffect(layoutMetrics.supportsInlineSidePanel) {
        if (!layoutMetrics.supportsInlineSidePanel && activeSidePanel != NowPlayingSidePanel.NONE) {
            activeSidePanel = NowPlayingSidePanel.NONE
        }
    }
    BackHandler(enabled = activeSidePanel != NowPlayingSidePanel.NONE && !showLyricsSheet) {
        activeSidePanel = NowPlayingSidePanel.NONE
    }

    val latestLyricsProvider by rememberUpdatedState(lyricsProvider)
    val latestOnShowQueueClicked by rememberUpdatedState(onShowQueueClicked)
    val latestSupportsInlineSidePanel by rememberUpdatedState(layoutMetrics.supportsInlineSidePanel)
    val latestIsSideDockedCard by rememberUpdatedState(isSideDockedCardMode)

    val onOpenFullLyricsSheet = remember {
        {
            val lyrics = latestLyricsProvider()
            if (!latestIsSideDockedCard && lyrics?.synced.isNullOrEmpty() && lyrics?.plain.isNullOrEmpty()) {
                showFetchLyricsDialog = true
            } else {
                showLyricsSheet = true
            }
        }
    }

    // Lógica para el botón de Lyrics en el reproductor expandido
    val onLyricsClick = remember {
        {
            if (latestSupportsInlineSidePanel) {
                activeSidePanel = if (activeSidePanel == NowPlayingSidePanel.LYRICS) {
                    NowPlayingSidePanel.NONE
                } else {
                    NowPlayingSidePanel.LYRICS
                }
            } else {
                onOpenFullLyricsSheet()
            }
        }
    }

    // Observador para reaccionar al resultado de la búsqueda de letras
    LaunchedEffect(lyricsSearchUiState) {
        when (val state = lyricsSearchUiState) {
            is LyricsSearchUiState.Success -> {
                if (showFetchLyricsDialog) {
                    showFetchLyricsDialog = false
                    showLyricsSheet = true
                    playerViewModel.resetLyricsSearchState()
                }
            }
            is LyricsSearchUiState.Error -> {
            }
            else -> Unit
        }
    }

    val onAlbumSongSelected: (Song, Int) -> Unit = remember(
        playerViewModel,
        currentPlaybackQueue,
        currentQueueSourceName
    ) {
        { newSong, index ->
            playerViewModel.showAndPlaySong(
                song = newSong,
                contextSongs = currentPlaybackQueue,
                queueName = currentQueueSourceName,
                indexInQueue = index
            )
        }
    }

    val onSongMetadataQueueClick = remember {
        {
            if (latestSupportsInlineSidePanel) {
                activeSidePanel = if (activeSidePanel == NowPlayingSidePanel.QUEUE) {
                    NowPlayingSidePanel.NONE
                } else {
                    NowPlayingSidePanel.QUEUE
                }
            } else {
                latestOnShowQueueClicked()
            }
        }
    }

    val onSongMetadataArtistClick = remember(currentSongArtists, song.artistId, playerViewModel) {
        {
            val resolvedArtistId = currentSongArtists.firstOrNull { it.id != 0L && it.id != -1L }?.id ?: song.artistId
            if (currentSongArtists.size > 1) {
                showArtistPicker = true
            } else {
                playerViewModel.triggerArtistNavigationFromPlayer(resolvedArtistId)
            }
        }
    }

    var pendingCarouselIndex by remember { mutableStateOf<Int?>(null) }
    val currentQueueIndex = remember(song.id, currentMediaItemIndex, currentPlaybackQueue) {
        resolveQueueIndex(
            queue = currentPlaybackQueue,
            songId = song.id,
            currentMediaItemIndex = currentMediaItemIndex
        )
    }
    val skipRequests = remember {
        MutableSharedFlow<SkipDirection>(
            extraBufferCapacity = 16
        )
    }
    val latestQueue by rememberUpdatedState(currentPlaybackQueue)
    val latestSongId by rememberUpdatedState(song.id)
    val latestCurrentQueueIndex by rememberUpdatedState(currentQueueIndex)
    val latestRepeatMode by rememberUpdatedState(repeatMode)
    val latestIsRemotePlaybackActive by rememberUpdatedState(isRemotePlaybackActive)
    val latestCurrentPositionProvider by rememberUpdatedState(currentPositionProvider)
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnPrevious by rememberUpdatedState(onPrevious)

    LaunchedEffect(currentQueueIndex, pendingCarouselIndex) {
        val targetIndex = pendingCarouselIndex ?: return@LaunchedEffect
        if (targetIndex == currentQueueIndex) {
            kotlinx.coroutines.delay(750)
            if (pendingCarouselIndex == currentQueueIndex) {
                pendingCarouselIndex = null
            }
        } else {
            kotlinx.coroutines.delay(950)
            if (pendingCarouselIndex == targetIndex) {
                pendingCarouselIndex = null
            }
        }
    }

    LaunchedEffect(skipRequests) {
        skipRequests.collect { direction ->
            // Wait until Frame 1 commits the optimistic carousel animation before firing MediaController IPC
            androidx.compose.runtime.withFrameNanos { }
            when (direction) {
                SkipDirection.NEXT -> latestOnNext()
                SkipDirection.PREVIOUS -> latestOnPrevious()
            }

            kotlinx.coroutines.delay(SKIP_COMMAND_GUARD_MS)
        }
    }

    fun predictSkipCarouselIndex(direction: SkipDirection): Int? {
        val queueSnapshot = latestQueue
        val baseIndex = pendingCarouselIndex
            ?: latestCurrentQueueIndex
            ?: queueSnapshot.indexOfFirst { it.id == latestSongId }.takeIf { it >= 0 }

        return when (direction) {
            SkipDirection.NEXT -> predictSkipNextCarouselIndex(
                currentIndex = baseIndex,
                queue = queueSnapshot,
                repeatMode = latestRepeatMode,
                isRemotePlaybackActive = latestIsRemotePlaybackActive
            )
            SkipDirection.PREVIOUS -> predictSkipPreviousCarouselIndex(
                currentIndex = baseIndex,
                queue = queueSnapshot,
                currentPositionMs = latestCurrentPositionProvider(),
                repeatMode = latestRepeatMode,
                isRemotePlaybackActive = latestIsRemotePlaybackActive
            )
        }
    }

    fun requestSkip(direction: SkipDirection) {
        val predictedTargetIndex = predictSkipCarouselIndex(direction)
        if (skipRequests.tryEmit(direction) && predictedTargetIndex != null) {
            pendingCarouselIndex = predictedTargetIndex
        }
    }

    val onNextWithOptimisticCarousel = remember {
        {
            requestSkip(SkipDirection.NEXT)
            Unit
        }
    }

    val onPreviousWithOptimisticCarousel = remember {
        {
            requestSkip(SkipDirection.PREVIOUS)
            Unit
        }
    }

    val onAlbumCoverClick = remember(playerViewModel) {
        { albumSong: Song ->
            playerViewModel.triggerAlbumNavigationFromPlayer(albumSong.albumId)
        }
    }

    val albumCoverSection: @Composable (Modifier) -> Unit = remember(
        song,
        currentPlaybackQueue,
        currentQueueIndex,
        currentMediaItemIndex,
        carouselStyle,
        loadingTweaks,
        isSheetDragGestureActive,
        expansionFractionProvider,
        currentSheetState,
        isPlayingProvider,
        playWhenReadyProvider,
        albumArtQuality,
        pendingCarouselIndex,
        onAlbumSongSelected,
        onAlbumCoverClick,
        onSwipePreviewChanged
    ) {
        { modifier ->
            FullPlayerAlbumCoverSection(
                song = song,
                currentPlaybackQueue = currentPlaybackQueue,
                currentMediaItemIndex = currentQueueIndex ?: currentMediaItemIndex,
                carouselStyle = carouselStyle,
                loadingTweaks = loadingTweaks,
                isSheetDragGestureActive = isSheetDragGestureActive,
                expansionFractionProvider = expansionFractionProvider,
                currentSheetState = currentSheetState,
                isPlayingProvider = isPlayingProvider,
                playWhenReadyProvider = playWhenReadyProvider,
                albumArtQuality = albumArtQuality,
                requestedScrollIndex = pendingCarouselIndex,
                onSongSelected = onAlbumSongSelected,
                onAlbumClick = onAlbumCoverClick,
                onSwipePreviewChanged = onSwipePreviewChanged,
                modifier = modifier
            )
        }
    }

    val playerProgressSection: @Composable () -> Unit = remember(
        song,
        playbackAudioMetadata.mediaId,
        playbackAudioMetadata.mimeType,
        playbackAudioMetadata.bitrate,
        playbackAudioMetadata.sampleRate,
        currentPositionProvider,
        totalDurationValue,
        showPlayerFileInfo,
        onSeek,
        expansionFractionProvider,
        isPlayingProvider,
        currentSheetState,
        allowRealtimeUpdates,
        isSheetDragGestureActive,
        loadingTweaks
    ) {
        {
            FullPlayerProgressSection(
                song = song,
                playbackMetadataMediaId = playbackAudioMetadata.mediaId,
                playbackMetadataMimeType = playbackAudioMetadata.mimeType,
                playbackMetadataBitrate = playbackAudioMetadata.bitrate,
                playbackMetadataSampleRate = playbackAudioMetadata.sampleRate,
                currentPositionProvider = currentPositionProvider,
                totalDurationValue = totalDurationValue,
                showPlayerFileInfo = showPlayerFileInfo,
                onSeek = onSeek,
                expansionFractionProvider = expansionFractionProvider,
                isPlayingProvider = isPlayingProvider,
                currentSheetState = currentSheetState,
                allowRealtimeUpdates = allowRealtimeUpdates,
                isSheetDragGestureActive = isSheetDragGestureActive,
                loadingTweaks = loadingTweaks
            )
        }
    }

    val controlsSection: @Composable () -> Unit = remember(
        loadingTweaks,
        isSheetDragGestureActive,
        expansionFractionProvider,
        currentSheetState,
        isPlayingProvider,
        onPreviousWithOptimisticCarousel,
        onPlayPause,
        onNextWithOptimisticCarousel,
        isShuffleEnabledProvider,
        shuffleTransitionInProgress,
        repeatModeProvider,
        isFavoriteProvider,
        onShuffleToggle,
        onRepeatToggle,
        onFavoriteToggle,
        layoutMetrics.mode,
        activeSidePanel,
        onLyricsClick,
        onSongMetadataQueueClick
    ) {
        {
            FullPlayerControlsSection(
                loadingTweaks = loadingTweaks,
                isSheetDragGestureActive = isSheetDragGestureActive,
                expansionFractionProvider = expansionFractionProvider,
                currentSheetState = currentSheetState,
                isPlayingProvider = isPlayingProvider,
                onPrevious = onPreviousWithOptimisticCarousel,
                onPlayPause = onPlayPause,
                onNext = onNextWithOptimisticCarousel,
                isShuffleEnabledProvider = isShuffleEnabledProvider,
                shuffleTransitionInProgress = shuffleTransitionInProgress,
                repeatModeProvider = repeatModeProvider,
                isFavoriteProvider = isFavoriteProvider,
                onShuffleToggle = onShuffleToggle,
                onRepeatToggle = onRepeatToggle,
                onFavoriteToggle = onFavoriteToggle,
                showLyricsAndQueueToggles = layoutMetrics.mode == NowPlayingLayoutMode.TABLET_LANDSCAPE_FULLSCREEN,
                activeSidePanel = activeSidePanel,
                onLyricsClick = onLyricsClick,
                onQueueClick = onSongMetadataQueueClick
            )
        }
    }

    val portraitSongMetadataSection: @Composable () -> Unit = remember(
        song,
        currentSongArtists,
        loadingTweaks,
        isSheetDragGestureActive,
        expansionFractionProvider,
        currentSheetState,
        onLyricsClick,
        playerViewModel,
        onSongMetadataQueueClick,
        onSongMetadataArtistClick,
        isPlayingProvider,
        isBufferingProvider,
        layoutMetrics.mode
    ) {
        {
            FullPlayerSongMetadataSection(
                song = song,
                currentSongArtists = currentSongArtists,
                loadingTweaks = loadingTweaks,
                isSheetDragGestureActive = isSheetDragGestureActive,
                expansionFractionProvider = expansionFractionProvider,
                currentSheetState = currentSheetState,
                isLandscape = false,
                layoutMode = layoutMetrics.mode,
                onLyricsClick = onLyricsClick,
                playerViewModel = playerViewModel,
                onQueueClick = onSongMetadataQueueClick,
                onArtistClick = onSongMetadataArtistClick,
                isPlayingProvider = isPlayingProvider,
                isBufferingProvider = isBufferingProvider
            )
        }
    }

    val landscapeCastIconRes = when {
        isCastConnecting || isRemotePlaybackActive -> R.drawable.rounded_cast_24
        isBluetoothEnabled && !bluetoothName.isNullOrEmpty() -> R.drawable.rounded_bluetooth_24
        else -> R.drawable.rounded_mobile_speaker_24
    }
    val landscapeCastDescRes = when {
        isCastConnecting || isRemotePlaybackActive -> R.string.player_cd_cast
        isBluetoothEnabled && !bluetoothName.isNullOrEmpty() -> R.string.player_cd_bluetooth
        else -> R.string.player_cd_local_playback
    }

    val landscapeSongMetadataSection: @Composable () -> Unit = remember(
        song,
        currentSongArtists,
        loadingTweaks,
        isSheetDragGestureActive,
        expansionFractionProvider,
        currentSheetState,
        onLyricsClick,
        playerViewModel,
        onSongMetadataQueueClick,
        onSongMetadataArtistClick,
        onShowCastClicked,
        onCollapse,
        landscapeCastIconRes,
        landscapeCastDescRes,
        isPlayingProvider,
        isBufferingProvider,
        layoutMetrics.mode,
        layoutMetrics.supportsSideDockedMode,
        onToggleTabletDisplayMode
    ) {
        {
            FullPlayerSongMetadataSection(
                song = song,
                currentSongArtists = currentSongArtists,
                loadingTweaks = loadingTweaks,
                isSheetDragGestureActive = isSheetDragGestureActive,
                expansionFractionProvider = expansionFractionProvider,
                currentSheetState = currentSheetState,
                isLandscape = true,
                layoutMode = layoutMetrics.mode,
                supportsSideDockedMode = layoutMetrics.supportsSideDockedMode,
                onToggleTabletDisplayMode = onToggleTabletDisplayMode,
                onLyricsClick = onLyricsClick,
                playerViewModel = playerViewModel,
                onQueueClick = onSongMetadataQueueClick,
                onArtistClick = onSongMetadataArtistClick,
                onShowCastClicked = onShowCastClicked,
                onCollapse = onCollapse,
                castIconRes = landscapeCastIconRes,
                castContentDescriptionRes = landscapeCastDescRes,
                isPlayingProvider = isPlayingProvider,
                isBufferingProvider = isBufferingProvider
            )
        }
    }

    val isDarkTheme = LocalPixelPlayDarkTheme.current
    val latestAmbientSchemeState = rememberUpdatedState(LocalMaterialTheme.current)

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier
            .nowPlayingAmbientBackdrop(
                colorSchemeProvider = { latestAmbientSchemeState.value },
                isDarkTheme = isDarkTheme,
                focalCenterXFraction = if (isLandscape) 0.26f else 0.50f,
                focalCenterYFraction = if (isLandscape) 0.50f else 0.32f
            )
            .pointerInput(currentSheetState, queueGestureBottomExclusionPx) {
            val queueDragActivationThresholdPx = 4.dp.toPx()
            val quickFlickVelocityThreshold = -520f

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Check condition AFTER the down event occurs
                val isFullyExpanded = currentSheetState == PlayerSheetState.EXPANDED && expansionFractionProvider() >= 0.99f

                if (!isFullyExpanded) {
                    return@awaitEachGesture
                }

                val bottomGestureBoundaryY =
                    (size.height.toFloat() - queueGestureBottomExclusionPx).coerceAtLeast(0f)
                if (down.position.y >= bottomGestureBoundaryY) {
                    // Let the system Home/back gesture win near the bottom edge.
                    return@awaitEachGesture
                }

                // Proceed with gesture logic
                var dragConsumedByQueue = false
                val velocityTracker = VelocityTracker()
                var totalDrag = 0f
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                drag(down.id) { change ->
                    val dragAmount = change.positionChange().y
                    totalDrag += dragAmount
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    val isDraggingUp = totalDrag < -queueDragActivationThresholdPx

                    if (isDraggingUp && !dragConsumedByQueue) {
                        dragConsumedByQueue = true
                        onQueueDragStart()
                    }

                    if (dragConsumedByQueue) {
                        change.consume()
                        onQueueDrag(dragAmount)
                    }
                }

                val velocity = velocityTracker.calculateVelocity().y
                if (dragConsumedByQueue) {
                    onQueueRelease(totalDrag, velocity)
                } else if (
                    totalDrag < -(queueDragActivationThresholdPx * 2f) &&
                    velocity < quickFlickVelocityThreshold
                ) {
                    // Treat short/fast upward flick as queue-open intent.
                    onQueueRelease(totalDrag, velocity)
                }
            }
        },
        topBar = {
            AnimatedVisibility(
                visible = layoutMetrics.showTopUtilityBar,
                enter = fadeIn(animationSpec = tween(350, easing = FastOutSlowInEasing)) +
                        slideInVertically(
                            initialOffsetY = { -it / 2 },
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        ),
                exit = fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                       slideOutVertically(
                           targetOffsetY = { -it / 2 },
                           animationSpec = tween(220, easing = FastOutSlowInEasing)
                       )
            ) {
                val isSideDockedCard = layoutMetrics.mode == NowPlayingLayoutMode.SIDE_DOCKED_PORTRAIT
                TopAppBar(
                    modifier = Modifier.graphicsLayer {
                        val fraction = expansionFractionProvider()
                        val startThreshold = 0f
                        val endThreshold = 1f
                        alpha = ((fraction - startThreshold) / (endThreshold - startThreshold)).coerceIn(0f, 1f)
                    },
                    windowInsets = if (isSideDockedCard) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = LocalMaterialTheme.current.onPrimaryContainer,
                    ),
                    title = {
                        if (!isCastConnecting) {
                            AnimatedVisibility(visible = (!isRemotePlaybackActive)) {
                                val headerTitle = if (isSideDockedCard && currentQueueSourceName.isNotBlank()) {
                                    currentQueueSourceName
                                } else {
                                    stringResource(R.string.player_now_playing)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        modifier = Modifier.padding(start = if (isSideDockedCard) 10.dp else 18.dp),
                                        text = headerTitle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelLargeEmphasized,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    if (currentSong != null && (currentSong.telegramChatId != null || currentSong.contentUriString.startsWith("telegram:"))) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Rounded.Cloud,
                                            contentDescription = stringResource(R.string.player_cd_cloud_stream),
                                            tint = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.6f),
                                            modifier = Modifier.padding(start = 8.dp).size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        val scheme = LocalMaterialTheme.current
                        val buttonContainerColor = scheme.onPrimaryContainer.copy(alpha = 0.14f)
                        val buttonContentColor = scheme.onPrimaryContainer
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .height(42.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(buttonContainerColor)
                                    .clickable(onClick = onCollapse),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.rounded_keyboard_arrow_down_24),
                                    contentDescription = stringResource(R.string.player_cd_collapse),
                                    tint = buttonContentColor
                                )
                            }
                        }
                    },
                    actions = {
                        val scheme = LocalMaterialTheme.current
                        val buttonContainerColor = scheme.onPrimaryContainer.copy(alpha = 0.14f)
                        val buttonContentColor = scheme.onPrimaryContainer
                        Row(
                            modifier = Modifier
                                .padding(end = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val showCastLabel = isCastConnecting || (isRemotePlaybackActive && selectedRouteName != null)
                            val isBluetoothActive =
                                isBluetoothEnabled && !bluetoothName.isNullOrEmpty() && !isRemotePlaybackActive && !isCastConnecting
                            val castIconPainter = when {
                                isCastConnecting || isRemotePlaybackActive -> painterResource(R.drawable.rounded_cast_24)
                                isBluetoothActive -> painterResource(R.drawable.rounded_bluetooth_24)
                                else -> painterResource(R.drawable.rounded_mobile_speaker_24)
                            }
                            val castCornersExpanded = 50.dp
                            val castCornersCompact = 6.dp
                            val castTopStart = castCornersExpanded
                            val castTopEnd by animateDpAsState(
                                targetValue = if (showCastLabel) castCornersExpanded else castCornersCompact,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                            )
                            val castBottomStart = castCornersExpanded
                            val castBottomEnd by animateDpAsState(
                                targetValue = if (showCastLabel) castCornersExpanded else castCornersCompact,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                            )
                            Box(
                                modifier = Modifier
                                    .height(42.dp)
                                    .align(Alignment.CenterVertically)
                                    .animateContentSize(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                    .widthIn(
                                        min = 46.dp,
                                        max = if (showCastLabel) 170.dp else 52.dp
                                    )
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = castTopStart.coerceAtLeast(0.dp),
                                            topEnd = castTopEnd.coerceAtLeast(0.dp),
                                            bottomStart = castBottomStart.coerceAtLeast(0.dp),
                                            bottomEnd = castBottomEnd.coerceAtLeast(0.dp)
                                        )
                                    )
                                    .background(buttonContainerColor)
                                    .clickable { onShowCastClicked() },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(start = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Icon(
                                        painter = castIconPainter,
                                        contentDescription = when {
                                            isCastConnecting || isRemotePlaybackActive -> stringResource(R.string.player_cd_cast)
                                            isBluetoothActive -> stringResource(R.string.player_cd_bluetooth)
                                            else -> stringResource(R.string.player_cd_local_playback)
                                        },
                                        tint = buttonContentColor
                                    )
                                    AnimatedVisibility(visible = showCastLabel) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Spacer(Modifier.width(8.dp))
                                            AnimatedContent(
                                                targetState = when {
                                                    isCastConnecting -> stringResource(R.string.player_connecting)
                                                    isRemotePlaybackActive && selectedRouteName != null -> selectedRouteName
                                                    else -> ""
                                                },
                                                transitionSpec = {
                                                    fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(120))
                                                },
                                                label = "castButtonLabel"
                                            ) { label ->
                                                Row(
                                                    modifier = Modifier.padding(end = 14.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = buttonContentColor,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    AnimatedVisibility(visible = isCastConnecting) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier
                                                                .size(14.dp),
                                                            strokeWidth = 2.dp,
                                                            color = buttonContentColor
                                                        )
                                                    }
                                                    if (isRemotePlaybackActive && !isCastConnecting) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .clip(CircleShape)
                                                                .background(LocalMaterialTheme.current.tertiaryFixed)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Queue Button (omitted in Side-Docked Phone Card since Queue & Lyrics live in bottom card)
                            if (!isSideDockedCard) {
                                Box(
                                    modifier = Modifier
                                        .size(height = 42.dp, width = 46.dp)
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 6.dp,
                                                topEnd = if (layoutMetrics.supportsSideDockedMode) 6.dp else 50.dp,
                                                bottomStart = 6.dp,
                                                bottomEnd = if (layoutMetrics.supportsSideDockedMode) 6.dp else 50.dp
                                            )
                                        )
                                        .background(buttonContainerColor)
                                        .clickable {
                                            onShowQueueClicked()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.rounded_queue_music_24),
                                        contentDescription = stringResource(R.string.player_cd_open_queue),
                                        tint = buttonContentColor
                                    )
                                }
                            }

                            if (layoutMetrics.supportsSideDockedMode) {
                                Box(
                                    modifier = Modifier
                                        .size(height = 42.dp, width = 46.dp)
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 6.dp,
                                                topEnd = 50.dp,
                                                bottomStart = 6.dp,
                                                bottomEnd = 50.dp
                                            )
                                        )
                                        .background(buttonContainerColor)
                                        .clickable(onClick = onToggleTabletDisplayMode),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSideDockedCard) {
                                            Icons.Rounded.OpenInFull
                                        } else {
                                            Icons.Rounded.CloseFullscreen
                                        },
                                        contentDescription = stringResource(
                                            if (isSideDockedCard) {
                                                R.string.player_mode_expand_fullscreen
                                            } else {
                                                R.string.player_mode_dock_corner
                                            }
                                        ),
                                        tint = buttonContentColor,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
        var contentVisible by remember(isLandscape) { mutableStateOf(false) }
        LaunchedEffect(isLandscape) { contentVisible = true }
        val contentAlpha by animateFloatAsState(
            targetValue = if (contentVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
            label = "orientationAlpha"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha }
        ) {
            if (isLandscape) {
                FullPlayerLandscapeContent(
                    paddingValues = paddingValues,
                    layoutMetrics = layoutMetrics,
                    activeSidePanel = activeSidePanel,
                    tabletUtilityButtons = if (layoutMetrics.mode == NowPlayingLayoutMode.TABLET_LANDSCAPE_FULLSCREEN) {
                        {
                            TabletFullscreenUtilityButtons(
                                castIconRes = landscapeCastIconRes,
                                castContentDescriptionRes = landscapeCastDescRes,
                                supportsSideDockedMode = layoutMetrics.supportsSideDockedMode,
                                onShowCastClicked = onShowCastClicked,
                                onToggleTabletDisplayMode = onToggleTabletDisplayMode,
                                onCollapse = onCollapse
                            )
                        }
                    } else {
                        null
                    },
                    albumCoverSection = albumCoverSection,
                    songMetadataSection = landscapeSongMetadataSection,
                    playerProgressSection = playerProgressSection,
                    controlsSection = controlsSection,
                    sidePanelContent = { panelModifier ->
                        TabletNowPlayingSidePanel(
                            activePanel = activeSidePanel,
                            onSelectPanel = { activeSidePanel = it },
                            onExpandFullPanel = { panel ->
                                activeSidePanel = NowPlayingSidePanel.NONE
                                if (panel == NowPlayingSidePanel.LYRICS) {
                                    onOpenFullLyricsSheet()
                                } else {
                                    onShowQueueClicked()
                                }
                            },
                            onClosePanel = { activeSidePanel = NowPlayingSidePanel.NONE },
                            currentSong = song,
                            queue = currentPlaybackQueue,
                            currentQueueSourceName = currentQueueSourceName,
                            currentMediaItemIndex = currentQueueIndex ?: currentMediaItemIndex,
                            lyrics = lyricsProvider(),
                            isLoadingLyrics = stablePlayerState.isLoadingLyrics,
                            playbackPositionFlow = if (isRemotePlaybackActive) {
                                playerViewModel.remotePosition
                            } else {
                                playerViewModel.currentPlaybackPosition
                            },
                            isPlaying = isPlayingProvider(),
                            lyricsSyncOffset = lyricsSyncOffset,
                            useAnimatedLyrics = false,
                            animatedLyricsBlurEnabled = true,
                            animatedLyricsBlurStrength = 2.5f,
                            onSeekTo = onSeek,
                            onFetchLyricsClick = { showFetchLyricsDialog = true },
                            onPlayQueueSong = onAlbumSongSelected,
                            onRemoveQueueSong = { songId -> playerViewModel.removeSongFromQueue(songId) },
                            modifier = panelModifier
                        )
                    }
                )
            } else {
                val isSideDockedCard = layoutMetrics.mode == NowPlayingLayoutMode.SIDE_DOCKED_PORTRAIT
                FullPlayerPortraitContent(
                    paddingValues = paddingValues,
                    layoutMetrics = layoutMetrics,
                    albumCoverSection = albumCoverSection,
                    songMetadataSection = portraitSongMetadataSection,
                    playerProgressSection = playerProgressSection,
                    controlsSection = controlsSection,
                    bottomPreviewCard = if (isSideDockedCard) {
                        {
                            SideDockedLyricsPreviewCard(
                                lyrics = lyricsProvider(),
                                currentPositionProvider = currentPositionProvider,
                                lyricsSyncOffset = lyricsSyncOffset,
                                onLyricsCardClick = onOpenFullLyricsSheet,
                                onQueueButtonClick = onShowQueueClicked,
                                onExpandLyricsFullClick = onOpenFullLyricsSheet
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }
    AnimatedVisibility(
        visible = showLyricsSheet,
        enter = slideInVertically(
            initialOffsetY = { it / 5 },
            animationSpec = tween(
                durationMillis = 500,
                easing = com.theveloper.pixelplay.presentation.components.M3ExpressiveDecelerateEasing
            )
        ) + scaleIn(
            initialScale = 0.94f,
            animationSpec = tween(
                durationMillis = 500,
                easing = com.theveloper.pixelplay.presentation.components.M3ExpressiveDecelerateEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = 300,
                easing = com.theveloper.pixelplay.presentation.components.M3ExpressiveDecelerateEasing
            )
        ),
        exit = slideOutVertically(
            targetOffsetY = { it / 5 },
            animationSpec = tween(
                durationMillis = 250,
                easing = com.theveloper.pixelplay.presentation.components.M3ExpressiveAccelerateEasing
            )
        ) + scaleOut(
            targetScale = 0.94f,
            animationSpec = tween(
                durationMillis = 250,
                easing = com.theveloper.pixelplay.presentation.components.M3ExpressiveAccelerateEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = 200,
                easing = com.theveloper.pixelplay.presentation.components.M3ExpressiveAccelerateEasing
            )
        )
    ) {
        LyricsSheet(
            stablePlayerStateFlow = playerViewModel.stablePlayerState,
            playbackPositionFlow = if (isRemotePlaybackActive) {
                playerViewModel.remotePosition
            } else {
                playerViewModel.currentPlaybackPosition
            },
            lyricsSearchUiState = lyricsSearchUiState,
            resetLyricsForCurrentSong = {
                playerViewModel.resetLyricsForCurrentSong()
            },
            onSearchLyrics = { forcePick -> playerViewModel.fetchLyricsForCurrentSong(forcePick) },
            onPickResult = { playerViewModel.acceptLyricsSearchResultForCurrentSong(it) },
            onManualSearch = { title, artist -> playerViewModel.searchLyricsManually(title, artist) },
            onImportLyrics = { filePickerLauncher.launch(com.theveloper.pixelplay.utils.LyricsImportSecurity.pickerMimeTypes()) },
            onDismissLyricsSearch = { playerViewModel.resetLyricsSearchState() },
            lyricsSyncOffset = lyricsSyncOffset,
            onLyricsSyncOffsetChange = { currentSong?.id?.let { songId -> playerViewModel.setLyricsSyncOffset(songId, it) } },
            // Use the platform default font (fontFamily = null) for lyrics so extended
            // Unicode glyphs (e.g. Icelandic æ ð þ) render instead of tofu. The bundled
            // Google Sans Rounded variable font drops these codepoints at runtime. (#2427)
            lyricsTextStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = null),
            colorScheme = LocalMaterialTheme.current,
            onBackClick = { showLyricsSheet = false },
            onSaveLyricsToFile = playerViewModel::saveLyricsToFile,
            onTranslateViaAi = { playerViewModel.translateLyricsViaAi() },
            onSeekTo = { playerViewModel.seekTo(it) },
            onPlayPause = {
                playerViewModel.playPause()
            },
            onNext = onNext,
            onPrev = onPrevious,
            immersiveLyricsEnabled = immersiveLyricsEnabled,
            immersiveLyricsTimeout = immersiveLyricsTimeout,
            isImmersiveTemporarilyDisabled = isImmersiveTemporarilyDisabled,
            onSetImmersiveTemporarilyDisabled = { playerViewModel.setImmersiveTemporarilyDisabled(it) },
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            isFavoriteProvider = isFavoriteProvider,
            onShuffleToggle = onShuffleToggle,
            onRepeatToggle = onRepeatToggle,
            onFavoriteToggle = onFavoriteToggle,
            isContainedInCard = isSideDockedCardMode
        )
    }

    if (showFetchLyricsDialog) {
        MaterialTheme(
            colorScheme = LocalMaterialTheme.current,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            FetchLyricsDialog(
                uiState = lyricsSearchUiState,
                currentSong = song, // Use 'song' which is derived from args/retained
                onConfirm = { forcePick ->
                    playerViewModel.fetchLyricsForCurrentSong(forcePick)
                },
                onPickResult = { result ->
                    playerViewModel.acceptLyricsSearchResultForCurrentSong(result)
                },
                onManualSearch = { title, artist ->
                    playerViewModel.searchLyricsManually(title, artist)
                },
                onDismiss = {
                    showFetchLyricsDialog = false
                    playerViewModel.resetLyricsSearchState()
                },
                onImport = {
                    filePickerLauncher.launch(com.theveloper.pixelplay.utils.LyricsImportSecurity.pickerMimeTypes())
                },
                onOpenProvidersClick = {
                    showProviderPrioritySheet = true
                },
                isContainedInCard = isSideDockedCardMode
            )
        }
    }

    if (showProviderPrioritySheet) {
        MaterialTheme(
            colorScheme = LocalMaterialTheme.current,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            LyricsProviderPrioritySheet(
                onDismissRequest = { showProviderPrioritySheet = false },
                onSearchOnlineChain = {
                    showFetchLyricsDialog = true
                    playerViewModel.resetLyricsSearchState()
                    playerViewModel.fetchLyricsForCurrentSong(true)
                },
                onSearchSpecificProvider = { provider ->
                    val songTitle = song.title
                    if (songTitle.isNotBlank()) {
                        showFetchLyricsDialog = true
                        playerViewModel.resetLyricsSearchState()
                        playerViewModel.searchLyricsManually(
                            "[provider:${provider.id}] $songTitle",
                            song.displayArtist
                        )
                    }
                },
                isContainedInCard = isSideDockedCardMode
            )
        }
    }

    val artistPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showArtistPicker && currentSongArtists.isNotEmpty()) {
        PlayerArtistPickerBottomSheet(
            song = song,
            artists = currentSongArtists,
            sheetState = artistPickerSheetState,
            onDismiss = { showArtistPicker = false },
            onArtistClick = { artist ->
                playerViewModel.triggerArtistNavigationFromPlayer(artist.id)
                showArtistPicker = false
            }
        )
    }
}


@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun FullPlayerAlbumCoverSection(
    song: Song,
    currentPlaybackQueue: ImmutableList<Song>,
    currentMediaItemIndex: Int,
    carouselStyle: String,
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    isPlayingProvider: () -> Boolean,
    playWhenReadyProvider: () -> Boolean,
    albumArtQuality: AlbumArtQuality,
    requestedScrollIndex: Int?,
    onSongSelected: (Song, Int) -> Unit,
    onAlbumClick: (Song) -> Unit,
    onSwipePreviewChanged: (String?, () -> Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val shouldDelay = loadingTweaks.delayAll || loadingTweaks.delayAlbumCarousel
    val shouldApplyPausedScale = !isPlayingProvider() && !playWhenReadyProvider()
    // Use a short deterministic tween instead of spring(StiffnessLow). The original
    // spring took ~1s to settle, producing ~60 frames of graphicsLayer invalidations
    // that overlapped with any subsequent sheet-collapse gesture. A 260 ms tween
    // finishes well before the user can start the next gesture, keeping the album
    // art's "pause squish" visible but removing the long tail of frame work.
    val albumArtScale by animateFloatAsState(
        targetValue = if (shouldApplyPausedScale) 0.95f else 1f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "AlbumArtScale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        val baseBound = minOf(maxWidth, maxHeight)
        val carouselHeight = when (carouselStyle) {
            CarouselStyle.NO_PEEK -> baseBound
            CarouselStyle.ONE_PEEK -> baseBound * 0.8f
            CarouselStyle.TWO_PEEK -> baseBound * 0.6f
            else -> baseBound * 0.8f
        }

        DelayedContent(
            shouldDelay = shouldDelay,
            showPlaceholders = loadingTweaks.showPlaceholders,
            applyPlaceholderDelayOnClose = loadingTweaks.applyPlaceholdersOnClose,
            switchOnDragRelease = loadingTweaks.switchOnDragRelease,
            isSheetDragGestureActive = isSheetDragGestureActive,
            sharedBoundsModifier = Modifier.width(baseBound).height(carouselHeight),
            expansionFractionProvider = expansionFractionProvider,
            isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
            normalStartThreshold = 0.08f,
            delayAppearThreshold = loadingTweaks.contentAppearThresholdPercent / 100f,
            delayCloseThreshold = 1f - (loadingTweaks.contentCloseThresholdPercent / 100f),
            placeholder = {
                if (loadingTweaks.transparentPlaceholders) {
                    Box(
                        Modifier
                            .width(baseBound)
                            .height(carouselHeight)
                            .graphicsLayer {
                                scaleX = albumArtScale
                                scaleY = albumArtScale
                            }
                    )
                } else {
                    val placeholderColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.25f)
                    val placeholderOnColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.2f)
                    AlbumPlaceholder(
                        height = carouselHeight,
                        width = baseBound,
                        color = placeholderColor,
                        onColor = placeholderOnColor,
                        modifier = Modifier.graphicsLayer {
                            scaleX = albumArtScale
                            scaleY = albumArtScale
                        }
                    )
                }
            }
        ) {
            AlbumCarouselSection(
                currentSong = song,
                queue = currentPlaybackQueue,
                expansionFraction = 1f,
                currentMediaItemIndex = currentMediaItemIndex,
                requestedScrollIndex = requestedScrollIndex,
                onSongSelected = { newSong, index ->
                    if (newSong.id != song.id || index != currentMediaItemIndex) {
                        onSongSelected(newSong, index)
                    }
                },
                onAlbumClick = onAlbumClick,
                carouselStyle = carouselStyle,
                onSwipePreviewChanged = onSwipePreviewChanged,
                modifier = Modifier
                    .width(baseBound)
                    .height(carouselHeight)
                    .graphicsLayer {
                        scaleX = albumArtScale
                        scaleY = albumArtScale
                    },
                albumArtQuality = albumArtQuality
            )
        }
    }
}

@Composable
private fun FullPlayerControlsSection(
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    isPlayingProvider: () -> Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    isShuffleEnabledProvider: () -> Boolean,
    shuffleTransitionInProgress: Boolean,
    repeatModeProvider: () -> Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    showLyricsAndQueueToggles: Boolean = false,
    activeSidePanel: NowPlayingSidePanel = NowPlayingSidePanel.NONE,
    onLyricsClick: () -> Unit = {},
    onQueueClick: () -> Unit = {}
) {
    val motionScheme = remember { MotionScheme.expressive() }
    val controlSpatialSpec = remember(motionScheme) { motionScheme.fastSpatialSpec<Float>() }
    val shouldDelay = loadingTweaks.delayAll || loadingTweaks.delayControls

    DelayedContent(
        shouldDelay = shouldDelay,
        showPlaceholders = loadingTweaks.showPlaceholders,
        applyPlaceholderDelayOnClose = loadingTweaks.applyPlaceholdersOnClose,
        switchOnDragRelease = loadingTweaks.switchOnDragRelease,
        isSheetDragGestureActive = isSheetDragGestureActive,
        sharedBoundsModifier = Modifier.fillMaxWidth().height(176.dp),
        expansionFractionProvider = expansionFractionProvider,
        isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
        normalStartThreshold = 0.42f,
        delayAppearThreshold = loadingTweaks.contentAppearThresholdPercent / 100f,
        delayCloseThreshold = 1f - (loadingTweaks.contentCloseThresholdPercent / 100f),
        placeholder = {
            if (loadingTweaks.transparentPlaceholders) {
                Box(Modifier.fillMaxWidth().height(176.dp))
            } else {
                val playerOnBaseColor = LocalMaterialTheme.current.onPrimaryContainer
                ControlsPlaceholder(
                    color = playerOnBaseColor.copy(alpha = 0.1f),
                    onColor = playerOnBaseColor.copy(alpha = 0.2f)
                )
            }
        }
    ) {
        val colorScheme = LocalMaterialTheme.current
        val transportPlayPauseColors = expressivePlayPauseButtonColors(colorScheme)
        val transportSkipColors = TransportButtonColors(
            container = colorScheme.primary,
            content = colorScheme.onPrimary
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedPlaybackControls(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp, vertical = 6.dp),
                isPlayingProvider = isPlayingProvider,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                height = 76.dp,
                pressAnimationSpec = controlSpatialSpec,
                releaseDelay = 220L,
                colorOtherButtons = transportSkipColors.container,
                colorPlayPause = transportPlayPauseColors.container,
                tintPlayPauseIcon = transportPlayPauseColors.content,
                tintOtherIcons = transportSkipColors.content,
                colorPreviousButton = transportSkipColors.container,
                colorNextButton = transportSkipColors.container,
                tintPreviousIcon = transportSkipColors.content,
                tintNextIcon = transportSkipColors.content
            )

            Spacer(modifier = Modifier.height(12.dp))

            BottomToggleRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp, max = 80.dp)
                    .padding(horizontal = 0.dp, vertical = 0.dp)
                    .padding(bottom = 4.dp),
                isShuffleEnabled = isShuffleEnabledProvider(),
                isShuffleTransitionInProgress = shuffleTransitionInProgress,
                repeatMode = repeatModeProvider(),
                isFavoriteProvider = isFavoriteProvider,
                onShuffleToggle = onShuffleToggle,
                onRepeatToggle = onRepeatToggle,
                onFavoriteToggle = onFavoriteToggle,
                showLyricsAndQueueToggles = showLyricsAndQueueToggles,
                activeSidePanel = activeSidePanel,
                onLyricsClick = onLyricsClick,
                onQueueClick = onQueueClick
            )
        }
    }
}

@Composable
private fun FullPlayerProgressSection(
    song: Song,
    playbackMetadataMediaId: String?,
    playbackMetadataMimeType: String?,
    playbackMetadataBitrate: Int?,
    playbackMetadataSampleRate: Int?,
    currentPositionProvider: () -> Long,
    totalDurationValue: Long,
    showPlayerFileInfo: Boolean,
    onSeek: (Long) -> Unit,
    expansionFractionProvider: () -> Float,
    isPlayingProvider: () -> Boolean,
    currentSheetState: PlayerSheetState,
    allowRealtimeUpdates: Boolean,
    isSheetDragGestureActive: Boolean,
    loadingTweaks: FullPlayerLoadingTweaks
) {
    val playerOnBaseColor = LocalMaterialTheme.current.onPrimaryContainer
    val progressActiveColor = playerOnBaseColor
    val isMetadataForCurrentSong = playbackMetadataMediaId == song.id
    val audioMimeType = if (isMetadataForCurrentSong) {
        playbackMetadataMimeType ?: song.mimeType
    } else {
        song.mimeType
    }
    val audioBitrate = if (isMetadataForCurrentSong) {
        playbackMetadataBitrate ?: song.bitrate
    } else {
        song.bitrate
    }
    val audioSampleRate = if (isMetadataForCurrentSong) {
        playbackMetadataSampleRate ?: song.sampleRate
    } else {
        song.sampleRate
    }

    PlayerProgressBarSection(
        songId = song.id,
        currentPositionProvider = currentPositionProvider,
        totalDurationValue = totalDurationValue,
        songDurationHintMs = song.duration,
        audioMimeType = audioMimeType,
        audioBitrate = audioBitrate,
        audioSampleRate = audioSampleRate,
        showAudioFileInfo = showPlayerFileInfo,
        onSeek = onSeek,
        expansionFractionProvider = expansionFractionProvider,
        isPlayingProvider = isPlayingProvider,
        currentSheetState = currentSheetState,
        activeTrackColor = progressActiveColor,
        inactiveTrackColor = playerOnBaseColor.copy(alpha = 0.2f),
        thumbColor = progressActiveColor,
        timeTextColor = playerOnBaseColor,
        allowRealtimeUpdates = allowRealtimeUpdates,
        isSheetDragGestureActive = isSheetDragGestureActive,
        loadingTweaks = loadingTweaks
    )
}

private fun resolveQueueIndex(
    queue: ImmutableList<Song>,
    songId: String,
    currentMediaItemIndex: Int
): Int? {
    if (currentMediaItemIndex in queue.indices && queue[currentMediaItemIndex].id == songId) {
        return currentMediaItemIndex
    }
    return queue.indexOfFirst { it.id == songId }.takeIf { it >= 0 }
}

private fun predictSkipNextCarouselIndex(
    currentIndex: Int?,
    queue: ImmutableList<Song>,
    repeatMode: Int,
    isRemotePlaybackActive: Boolean
): Int? {
    if (isRemotePlaybackActive || queue.size <= 1) return null
    val safeCurrentIndex = currentIndex?.takeIf { it in queue.indices } ?: return null

    return when {
        safeCurrentIndex < queue.lastIndex -> safeCurrentIndex + 1
        repeatMode == Player.REPEAT_MODE_ALL -> 0
        else -> null
    }
}

private fun predictSkipPreviousCarouselIndex(
    currentIndex: Int?,
    queue: ImmutableList<Song>,
    currentPositionMs: Long,
    repeatMode: Int,
    isRemotePlaybackActive: Boolean
): Int? {
    if (isRemotePlaybackActive || queue.size <= 1) return null
    if (currentPositionMs > PREVIOUS_TRACK_RESTART_THRESHOLD_MS) return null
    val safeCurrentIndex = currentIndex?.takeIf { it in queue.indices } ?: return null

    return when {
        safeCurrentIndex > 0 -> safeCurrentIndex - 1
        repeatMode == Player.REPEAT_MODE_ALL -> queue.lastIndex
        else -> null
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun FullPlayerSongMetadataSection(
    song: Song,
    currentSongArtists: List<Artist>,
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    isLandscape: Boolean,
    layoutMode: NowPlayingLayoutMode = NowPlayingLayoutMode.PORTRAIT_COMPACT,
    supportsSideDockedMode: Boolean = false,
    onToggleTabletDisplayMode: () -> Unit = {},
    onLyricsClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    onQueueClick: () -> Unit,
    onArtistClick: () -> Unit,
    onShowCastClicked: () -> Unit = {},
    onCollapse: () -> Unit = {},
    castIconRes: Int = R.drawable.rounded_mobile_speaker_24,
    castContentDescriptionRes: Int = R.string.player_cd_local_playback,
    isPlayingProvider: () -> Boolean = { true },
    isBufferingProvider: () -> Boolean = { false }
) {
    val shouldDelay = loadingTweaks.delayAll || loadingTweaks.delaySongMetadata

    DelayedContent(
        shouldDelay = shouldDelay,
        showPlaceholders = loadingTweaks.showPlaceholders,
        applyPlaceholderDelayOnClose = loadingTweaks.applyPlaceholdersOnClose,
        switchOnDragRelease = loadingTweaks.switchOnDragRelease,
        isSheetDragGestureActive = isSheetDragGestureActive,
        sharedBoundsModifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
        expansionFractionProvider = expansionFractionProvider,
        isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
        normalStartThreshold = 0.20f,
        delayAppearThreshold = loadingTweaks.contentAppearThresholdPercent / 100f,
        delayCloseThreshold = 1f - (loadingTweaks.contentCloseThresholdPercent / 100f),
        placeholder = {
            if (loadingTweaks.transparentPlaceholders) {
                Box(Modifier.fillMaxWidth().height(70.dp))
            } else {
                val playerOnBaseColor = LocalMaterialTheme.current.onPrimaryContainer
                MetadataPlaceholder(
                    expansionFractionProvider = expansionFractionProvider,
                    color = playerOnBaseColor.copy(alpha = 0.1f),
                    onColor = playerOnBaseColor.copy(alpha = 0.2f),
                    showQueueButtons = isLandscape
                )
            }
        }
    ) {
        val colorScheme = LocalMaterialTheme.current
        val playerOnBaseColor = colorScheme.onPrimaryContainer
        SongMetadataDisplaySection(
            modifier = Modifier
                .padding(start = 0.dp),
            onClickLyrics = onLyricsClick,
            song = song,
            currentSongArtists = currentSongArtists,
            expansionFractionProvider = expansionFractionProvider,
            textColor = playerOnBaseColor,
            artistTextColor = playerOnBaseColor.copy(alpha = 0.76f),
            playerViewModel = playerViewModel,
            gradientEdgeColor = colorScheme.primaryContainer,
            chipColor = playerOnBaseColor.copy(alpha = 0.14f),
            chipContentColor = playerOnBaseColor,
            showQueueButton = isLandscape,
            layoutMode = layoutMode,
            supportsSideDockedMode = supportsSideDockedMode,
            onToggleTabletDisplayMode = onToggleTabletDisplayMode,
            onClickQueue = onQueueClick,
            onClickArtist = onArtistClick,
            onShowCastClicked = onShowCastClicked,
            onCollapse = onCollapse,
            castIconRes = castIconRes,
            castContentDescriptionRes = castContentDescriptionRes,
            isPlayingProvider = isPlayingProvider,
            isBufferingProvider = isBufferingProvider
        )
    }
}

@Composable
private fun FullPlayerPortraitContent(
    paddingValues: PaddingValues,
    layoutMetrics: NowPlayingLayoutMetrics,
    albumCoverSection: @Composable (Modifier) -> Unit,
    songMetadataSection: @Composable () -> Unit,
    playerProgressSection: @Composable () -> Unit,
    controlsSection: @Composable () -> Unit,
    bottomPreviewCard: (@Composable () -> Unit)? = null
) {
    val isSideDockedCard = layoutMetrics.mode == NowPlayingLayoutMode.SIDE_DOCKED_PORTRAIT
    val isTabletFullscreenPortrait = layoutMetrics.mode == NowPlayingLayoutMode.PORTRAIT_TABLET_FULLSCREEN
    val horizontalPad = when {
        isSideDockedCard -> 18.dp
        isTabletFullscreenPortrait -> 32.dp
        else -> 24.dp
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = paddingValues.calculateTopPadding(),
                bottom = if (isSideDockedCard) 12.dp else paddingValues.calculateBottomPadding()
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = layoutMetrics.controlsMaxWidth)
                .padding(horizontal = horizontalPad),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = when {
                isSideDockedCard -> Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                isTabletFullscreenPortrait -> Arrangement.spacedBy(layoutMetrics.deckSpacing, Alignment.CenterVertically)
                else -> Arrangement.SpaceAround
            }
        ) {
            albumCoverSection(
                if (isSideDockedCard) {
                    Modifier.weight(1f, fill = false)
                } else {
                    Modifier
                }
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(Modifier.align(Alignment.Start)) {
                    songMetadataSection()
                }
                playerProgressSection()
            }

            controlsSection()

            if (bottomPreviewCard != null) {
                bottomPreviewCard()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerLandscapeContent(
    paddingValues: PaddingValues,
    layoutMetrics: NowPlayingLayoutMetrics,
    activeSidePanel: NowPlayingSidePanel,
    tabletUtilityButtons: (@Composable () -> Unit)? = null,
    albumCoverSection: @Composable (Modifier) -> Unit,
    songMetadataSection: @Composable () -> Unit,
    playerProgressSection: @Composable () -> Unit,
    controlsSection: @Composable () -> Unit,
    sidePanelContent: @Composable (Modifier) -> Unit
) {
    val windowInfo = LocalPixelPlayWindowInfo.current
    val spacing = LocalSpacing.current
    val motionScheme = MaterialTheme.motionScheme
    val isLargeTablet = windowInfo.widthTier.ordinal >= WindowWidthTier.LARGE.ordinal
    val horizontalPad = if (isLargeTablet) spacing.xxxl else 24.dp
    val columnGap = if (isLargeTablet) spacing.xxxl else 16.dp
    val isSplitPanelOpen =
        layoutMetrics.supportsInlineSidePanel && activeSidePanel != NowPlayingSidePanel.NONE
    val statusBarTopPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = maxOf(paddingValues.calculateTopPadding(), statusBarTopPad + 8.dp),
                bottom = maxOf(paddingValues.calculateBottomPadding(), navBarBottomPad + 8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isSplitPanelOpen,
            transitionSpec = {
                fadeIn(animationSpec = motionScheme.fastEffectsSpec()) togetherWith
                    fadeOut(animationSpec = motionScheme.fastEffectsSpec())
            },
            label = "TabletLandscapeNowPlayingSplit"
        ) { splitOpen ->
            if (!splitOpen) {
                // Giao diện 3 (Image 2): Symmetrical 2-Column Full-Screen Showcase
                Box(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxSize()
                            .widthIn(max = 1320.dp)
                            .padding(horizontal = horizontalPad),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            albumCoverSection(
                                Modifier
                                    .fillMaxHeight()
                                    .widthIn(max = layoutMetrics.albumArtMaxSize)
                            )
                        }
                        Spacer(Modifier.width(columnGap))
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = layoutMetrics.controlsMaxWidth),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(
                                    layoutMetrics.deckSpacing,
                                    Alignment.CenterVertically
                                )
                            ) {
                                songMetadataSection()
                                playerProgressSection()
                                controlsSection()
                            }
                        }
                    }

                    if (tabletUtilityButtons != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = horizontalPad, top = 4.dp)
                        ) {
                            tabletUtilityButtons()
                        }
                    }
                }
            } else {
                // Full-Screen Split-Panel Mode (Left: Top utility bar + Large 1:1 album art + Bottom-anchored controls, Right: Lyrics / Up Next)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1440.dp)
                        .padding(horizontal = horizontalPad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                            .widthIn(max = 500.dp)
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (tabletUtilityButtons != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                tabletUtilityButtons()
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            albumCoverSection(Modifier.fillMaxSize())
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            songMetadataSection()
                            playerProgressSection()
                            controlsSection()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp)
                    ) {
                        sidePanelContent(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletFullscreenUtilityButtons(
    castIconRes: Int,
    castContentDescriptionRes: Int,
    supportsSideDockedMode: Boolean,
    onShowCastClicked: () -> Unit,
    onToggleTabletDisplayMode: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = LocalMaterialTheme.current
    val chipColor = scheme.onPrimaryContainer.copy(alpha = 0.14f)
    val chipContentColor = scheme.onPrimaryContainer

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(chipColor)
                .clickable(onClick = onShowCastClicked),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(castIconRes),
                contentDescription = stringResource(castContentDescriptionRes),
                tint = chipContentColor,
                modifier = Modifier.size(19.dp)
            )
        }
        if (supportsSideDockedMode) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(chipColor)
                    .clickable(onClick = onToggleTabletDisplayMode),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloseFullscreen,
                    contentDescription = stringResource(R.string.player_mode_dock_corner),
                    tint = chipContentColor,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(chipColor)
                .clickable(onClick = onCollapse),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_keyboard_arrow_down_24),
                contentDescription = stringResource(R.string.player_cd_collapse),
                tint = chipContentColor,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SongMetadataDisplaySection(
    song: Song?,
    currentSongArtists: List<Artist>,
    expansionFractionProvider: () -> Float,
    textColor: Color,
    artistTextColor: Color,
    gradientEdgeColor: Color,
    playerViewModel: PlayerViewModel,
    chipColor: Color,
    chipContentColor: Color,
    onClickLyrics: () -> Unit,
    showQueueButton: Boolean,
    layoutMode: NowPlayingLayoutMode = NowPlayingLayoutMode.PORTRAIT_COMPACT,
    supportsSideDockedMode: Boolean = false,
    onToggleTabletDisplayMode: () -> Unit = {},
    onClickQueue: () -> Unit,
    onClickArtist: () -> Unit,
    modifier: Modifier = Modifier,
    onShowCastClicked: () -> Unit = {},
    onCollapse: () -> Unit = {},
    castIconRes: Int = R.drawable.rounded_mobile_speaker_24,
    castContentDescriptionRes: Int = R.string.player_cd_local_playback,
    isPlayingProvider: () -> Boolean = { true },
    isBufferingProvider: () -> Boolean = { false }
) {
    val isTabletFullscreen = layoutMode == NowPlayingLayoutMode.TABLET_LANDSCAPE_FULLSCREEN ||
        layoutMode == NowPlayingLayoutMode.PORTRAIT_TABLET_FULLSCREEN
    val isSideDockedCard = layoutMode == NowPlayingLayoutMode.SIDE_DOCKED_PORTRAIT

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        song?.let { currentSong ->
            PlayerSongInfo(
                title = currentSong.title,
                artist = currentSong.displayArtist,
                album = currentSong.album,
                albumId = currentSong.albumId,
                artistId = currentSong.artistId,
                artists = currentSongArtists,
                expansionFractionProvider = expansionFractionProvider,
                textColor = textColor,
                artistTextColor = artistTextColor,
                gradientEdgeColor = gradientEdgeColor,
                playerViewModel = playerViewModel,
                onClickArtist = onClickArtist,
                useLargeHeadline = isTabletFullscreen,
                showAlbumSubtitle = layoutMode == NowPlayingLayoutMode.TABLET_LANDSCAPE_FULLSCREEN,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                isPlayingProvider = isPlayingProvider
            )
        }
        
        val isBuffering = isBufferingProvider()


        AnimatedVisibility(
            visible = isBuffering,
            enter = scaleIn(
                initialScale = 0.85f,
                animationSpec = tween(
                    durationMillis = 400,
                    delayMillis = 80,
                    easing = FastOutSlowInEasing
                )
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 300,
                    delayMillis = 80
                )
            ),
            exit = scaleOut(
                targetScale = 0.85f,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = 200
                )
            )
        ) {
            Surface(
                shape = CircleShape,
                color = chipColor,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier.padding(10.dp), 
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(28.dp),
                        color = chipContentColor
                    )
                }
            }
        }

        when {
            layoutMode == NowPlayingLayoutMode.TABLET_LANDSCAPE_FULLSCREEN -> {
                // Utility controls (Cast, Dock-Corner, Collapse) are placed at the top of the player deck
                // so the song title spans the full deck width without truncation.
            }
            showQueueButton -> {
                // Compact Phone Landscape (< 560dp height)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(height = 40.dp, width = 40.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 50.dp,
                                    topEnd = 6.dp,
                                    bottomStart = 50.dp,
                                    bottomEnd = 6.dp
                                )
                            )
                            .background(chipColor)
                            .clickable { onShowCastClicked() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(castIconRes),
                            contentDescription = stringResource(castContentDescriptionRes),
                            tint = chipContentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(height = 40.dp, width = 42.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(chipColor)
                            .clickable { onClickLyrics() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_lyrics_24),
                            contentDescription = stringResource(R.string.common_lyrics),
                            tint = chipContentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(height = 40.dp, width = 42.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 6.dp,
                                    topEnd = 50.dp,
                                    bottomStart = 6.dp,
                                    bottomEnd = 50.dp
                                )
                            )
                            .background(chipColor)
                            .clickable { onClickQueue() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_queue_music_24),
                            contentDescription = stringResource(R.string.player_cd_open_queue),
                            tint = chipContentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(chipColor)
                            .clickable { onCollapse() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_keyboard_arrow_down_24),
                            contentDescription = stringResource(R.string.player_cd_collapse),
                            tint = chipContentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            !isSideDockedCard -> {
                // Portrait Mode: Tonal Lyrics button (in Side-Docked mode, Lyrics & Queue live in the bottom preview card)
                FilledIconButton(
                    modifier = Modifier
                        .size(width = 44.dp, height = 44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = chipColor,
                        contentColor = chipContentColor
                    ),
                    onClick = onClickLyrics,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_lyrics_24),
                        contentDescription = stringResource(R.string.common_lyrics)
                    )
                }
            }
        }
    }
}

private fun formatAudioMetaLabel(mimeType: String?, bitrate: Int?, sampleRate: Int?): String? {
    val formatLabel = mimeTypeToFormat(mimeType)
        .takeIf { it != "-" }
        ?.uppercase(Locale.getDefault())

    val parts = buildList {
        sampleRate?.takeIf { it > 0 }?.let { add(String.format(Locale.US, "%.1f kHz", it / 1000.0)) }
        bitrate?.takeIf { it > 0 }?.let { bitrateValue ->
            val kbpsLabel = "${bitrateValue / 1000} kbps"
            if (formatLabel != null) {
                add("$kbpsLabel \u2022 $formatLabel")
            } else {
                add(kbpsLabel)
            }
        } ?: formatLabel?.let { add(it) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" \u2022 ")
}

@Composable
private fun PlayerProgressBarSection(
    songId: String,
    currentPositionProvider: () -> Long,
    totalDurationValue: Long,
    songDurationHintMs: Long,
    audioMimeType: String?,
    audioBitrate: Int?,
    audioSampleRate: Int?,
    showAudioFileInfo: Boolean,
    onSeek: (Long) -> Unit,
    expansionFractionProvider: () -> Float,
    isPlayingProvider: () -> Boolean,
    currentSheetState: PlayerSheetState,
    activeTrackColor: Color,
    inactiveTrackColor: Color,
    thumbColor: Color,
    timeTextColor: Color,
    allowRealtimeUpdates: Boolean = true,
    isSheetDragGestureActive: Boolean = false,
    loadingTweaks: FullPlayerLoadingTweaks? = null,
    modifier: Modifier = Modifier
) {
    val progressSectionHorizontalInset = 0.dp
    val isVisible by remember(expansionFractionProvider) {
        derivedStateOf { expansionFractionProvider() > 0.01f }
    }
    val isExpanded by remember(currentSheetState, expansionFractionProvider) {
        derivedStateOf {
            currentSheetState == PlayerSheetState.EXPANDED && expansionFractionProvider() >= 0.995f
        }
    }
    val shouldRunRealtimeUpdates = allowRealtimeUpdates && isVisible
    val shouldSampleProgress = isVisible

    val reportedDuration = totalDurationValue.coerceAtLeast(0L)
    val hintDuration = songDurationHintMs.coerceAtLeast(0L)
    val displayDurationValue = when {
        reportedDuration > 0L -> reportedDuration
        hintDuration > 0L -> hintDuration
        else -> 0L
    }
    val audioMetaLabel = remember(showAudioFileInfo, audioMimeType, audioBitrate, audioSampleRate) {
        if (showAudioFileInfo) {
            formatAudioMetaLabel(
                mimeType = audioMimeType,
                bitrate = audioBitrate,
                sampleRate = audioSampleRate
            )
        } else {
            null
        }
    }
    var displayAudioMetaLabel by remember(songId) { mutableStateOf<String?>(null) }
    LaunchedEffect(songId, audioMetaLabel, showAudioFileInfo) {
        if (!showAudioFileInfo) {
            displayAudioMetaLabel = null
        } else if (!audioMetaLabel.isNullOrBlank()) {
            displayAudioMetaLabel = audioMetaLabel
        } else {
            kotlinx.coroutines.delay(500)
            displayAudioMetaLabel = null
        }
    }
    val durationForCalc = displayDurationValue.coerceAtLeast(1L)
    
    // Pass isVisible to rememberSmoothProgress
    val (smoothProgressState, _) = rememberSmoothProgress(
        isPlayingProvider = isPlayingProvider,
        currentPositionProvider = currentPositionProvider,
        totalDuration = displayDurationValue,
        sampleWhilePlayingMs = if (shouldRunRealtimeUpdates && isExpanded) 180L else 500L,
        sampleWhilePausedMs = 800L,
        isVisible = shouldSampleProgress
    )

    var sliderDragValue by remember { mutableStateOf<Float?>(null) }
    // Held seek target (fraction) — mirrors PlayerSeekBar so the slider stays where the user
    // dropped it until real playback catches up. Fraction-based so it survives duration drift.
    var targetSeekFraction by remember { mutableFloatStateOf(-1f) }
    var lastSeekFinishedTime by remember { mutableLongStateOf(0L) }

    // Reset seek state on song change to avoid stale position from previous song.
    LaunchedEffect(songId) {
        sliderDragValue = null
        targetSeekFraction = -1f
        lastSeekFinishedTime = 0L
    }

    // Release the held target once smooth progress catches up (within 4%) or after a 5 s
    // safety net — same thresholds as the LyricsSheet PlayerSeekBar. Re-keying on songId
    // restarts the snapshotFlow so the new song's progress drives the catch-up cleanly.
    LaunchedEffect(songId) {
        snapshotFlow { smoothProgressState.value }.collect { progress ->
            if (sliderDragValue != null) return@collect
            val target = targetSeekFraction
            if (target < 0f) return@collect
            val timeSinceSeek = System.currentTimeMillis() - lastSeekFinishedTime
            val diff = kotlin.math.abs(progress - target)
            if (timeSinceSeek > 5000L || diff < 0.04f) {
                targetSeekFraction = -1f
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val shouldAnimateWavyProgress by remember(shouldRunRealtimeUpdates, isPlayingProvider) {
        derivedStateOf { shouldRunRealtimeUpdates && isPlayingProvider() }
    }

    // Always drive the thumb from smoothed progress to avoid visual jumps from 500ms raw ticks.
    val animatedProgressState = remember(smoothProgressState) {
        derivedStateOf {
            when {
                sliderDragValue != null -> sliderDragValue!!
                targetSeekFraction >= 0f -> targetSeekFraction
                else -> smoothProgressState.value
            }
        }
    }

    // No LaunchedEffect/snapshotFlow needed anymore. 
    // smoothProgressState is already 60fps animated.

    val effectivePositionState = remember(durationForCalc, animatedProgressState, isVisible, displayDurationValue) {
        derivedStateOf {
             val progress = animatedProgressState.value
             (progress * durationForCalc).roundToLong().coerceIn(0L, displayDurationValue)
        }
    }

    val shouldDelay = loadingTweaks?.let { it.delayAll || it.delayProgressBar } ?: false

    val placeholderColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.25f)
    val placeholderOnColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.2f)

    DelayedContent(
        shouldDelay = shouldDelay,
        showPlaceholders = loadingTweaks?.showPlaceholders ?: false,
        applyPlaceholderDelayOnClose = loadingTweaks?.applyPlaceholdersOnClose ?: true,
        switchOnDragRelease = loadingTweaks?.switchOnDragRelease ?: false,
        isSheetDragGestureActive = isSheetDragGestureActive,
        sharedBoundsModifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
        expansionFractionProvider = expansionFractionProvider,
        isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
        normalStartThreshold = 0.08f,
        delayAppearThreshold = (loadingTweaks?.contentAppearThresholdPercent ?: 0) / 100f,
        delayCloseThreshold = 1f - ((loadingTweaks?.contentCloseThresholdPercent ?: 0) / 100f),
        placeholder = {
             if (loadingTweaks?.transparentPlaceholders == true) {
                 Box(Modifier.fillMaxWidth().heightIn(min = 70.dp))
             } else {
                 ProgressPlaceholder(
                     color = placeholderColor,
                     onColor = placeholderOnColor,
                     showAudioMetaChip = showAudioFileInfo && !displayAudioMetaLabel.isNullOrBlank()
                 )
             }
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 70.dp)
        ) {
            // Isolated Slider Component
            // Wrapped in a Box with detectVerticalDragGestures to prevent the outer
            // playerSheetVerticalDragGesture from intercepting slider touches. If the
            // user's drag has a vertical component, the inner handler absorbs it (consuming
            // the events) so the sheet-collapse gesture never activates in this area.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(onVerticalDrag = { _, _ -> })
                    }
            ) {
                EfficientSlider(
                    valueState = animatedProgressState,
                    onValueChange = { sliderDragValue = it },
                    onValueCommit = { finalValue ->
                        val targetMs = (finalValue * durationForCalc).roundToLong()
                        targetSeekFraction = finalValue
                        lastSeekFinishedTime = System.currentTimeMillis()
                        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                            type = AdvancedPerformanceDiagnostics.EventTypes.UI,
                            name = "player_seek_commit"
                        ) {
                            mapOf(
                                "targetMs" to targetMs.toString(),
                                "durationMs" to displayDurationValue.toString()
                            )
                        }
                        onSeek(targetMs)
                        sliderDragValue = null
                    },
                    thumbColor = thumbColor,
                    activeTrackColor = activeTrackColor,
                    inactiveTrackColor = inactiveTrackColor,
                    interactionSource = interactionSource,
                    isPlaying = shouldAnimateWavyProgress,
                    isVisible = isVisible,
                    trackEdgePadding = progressSectionHorizontalInset
                )
            }

            // Isolated Time Labels
            EfficientTimeLabels(
                positionState = effectivePositionState,
                duration = displayDurationValue,
                isVisible = isVisible,
                textColor = timeTextColor,
                audioMetaLabel = displayAudioMetaLabel,
                horizontalTrackInset = progressSectionHorizontalInset
            )
        }
    }
}

@Composable
private fun EfficientSlider(
    valueState: androidx.compose.runtime.State<Float>,
    onValueChange: (Float) -> Unit,
    onValueCommit: (Float) -> Unit,
    thumbColor: Color,
    activeTrackColor: Color,
    inactiveTrackColor: Color,
    interactionSource: MutableInteractionSource,
    isPlaying: Boolean,
    isVisible: Boolean,
    trackEdgePadding: Dp
) {
    val haptics = LocalHapticFeedback.current
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val currentHaptics = rememberUpdatedState(haptics)
    val lastHapticStep = remember { intArrayOf(-1) }
    val onValueChangeWithHaptics = remember {
        { newValue: Float ->
            val quantized = (newValue.coerceIn(0f, 1f) * 20f).toInt()
            if (quantized != lastHapticStep[0]) {
                lastHapticStep[0] = quantized
                currentHaptics.value.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            currentOnValueChange.value(newValue)
        }
    }

    WavySliderExpressive(
        value = { valueState.value },
        onValueChange = onValueChangeWithHaptics,
        onValueCommit = onValueCommit,
        interactionSource = interactionSource,
        activeTrackColor = activeTrackColor,
        inactiveTrackColor = inactiveTrackColor,
        thumbColor = thumbColor,
        isPlaying = isPlaying,
        isVisible = isVisible,
        trackEdgePadding = trackEdgePadding,
        semanticsLabel = "Playback position",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 0.dp)
    )
}

@Composable
private fun EfficientTimeLabels(
    positionState: androidx.compose.runtime.State<Long>,
    duration: Long,
    isVisible: Boolean,
    textColor: Color,
    audioMetaLabel: String?,
    horizontalTrackInset: Dp
) {
    val coarsePositionMs by remember(isVisible, positionState) {
        derivedStateOf {
            if (!isVisible) 0L
            else (positionState.value.coerceAtLeast(0L) / 1000L) * 1000L
        }
    }
    val posStr by remember(isVisible, coarsePositionMs) {
        derivedStateOf { if (isVisible) formatDuration(coarsePositionMs) else "--:--" }
    }
    val durStr = remember(isVisible, duration) {
        if (isVisible) formatDuration(duration.coerceAtLeast(0L)) else "--:--"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalTrackInset)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                posStr,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Text(
                durStr,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }

        if (!audioMetaLabel.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 58.dp),
                shape = RoundedCornerShape(999.dp),
                color = textColor.copy(alpha = 0.14f),
                contentColor = textColor.copy(alpha = 0.96f)
            ) {
                Text(
                    text = audioMetaLabel,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun DelayedContent(
    shouldDelay: Boolean,
    showPlaceholders: Boolean,
    applyPlaceholderDelayOnClose: Boolean,
    switchOnDragRelease: Boolean,
    isSheetDragGestureActive: Boolean,
    sharedBoundsModifier: Modifier = Modifier,
    expansionFractionProvider: () -> Float,
    isExpandedOverride: Boolean = false,
    normalStartThreshold: Float,
    delayAppearThreshold: Float,
    delayCloseThreshold: Float,
    placeholder: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val appearThreshold = delayAppearThreshold.coerceIn(0f, 1f)
    val closeThreshold = delayCloseThreshold.coerceIn(0f, 1f)
    var isDelayGateOpen by remember(shouldDelay) { mutableStateOf(!shouldDelay) }

    LaunchedEffect(
        shouldDelay,
        appearThreshold,
        closeThreshold,
        applyPlaceholderDelayOnClose,
        switchOnDragRelease,
        isSheetDragGestureActive,
        isExpandedOverride,
        expansionFractionProvider
    ) {
        if (!shouldDelay) {
            isDelayGateOpen = true
            return@LaunchedEffect
        }

        if (switchOnDragRelease) {
            if (isSheetDragGestureActive) {
                return@LaunchedEffect
            }

            if (isExpandedOverride) {
                isDelayGateOpen = true
            } else {
                snapshotFlow { expansionFractionProvider().coerceIn(0f, 1f) }
                    .first { fraction -> fraction <= 0.001f }
                isDelayGateOpen = false
            }
            return@LaunchedEffect
        }

        var previousExpansionFraction = expansionFractionProvider().coerceIn(0f, 1f)
        var previousExpandedOverride = isExpandedOverride

        snapshotFlow {
            val rawExpansionFraction = expansionFractionProvider().coerceIn(0f, 1f)
            val effectiveExpansionFraction =
                if (isExpandedOverride && rawExpansionFraction >= 0.985f) 1f else rawExpansionFraction
            DelayedContentFrame(
                rawExpansionFraction = rawExpansionFraction,
                effectiveExpansionFraction = effectiveExpansionFraction,
                isExpandedOverride = isExpandedOverride
            )
        }.collect { frame ->
            val isCollapsingByFraction =
                frame.rawExpansionFraction < previousExpansionFraction - 0.001f
            val isExpandingByFraction =
                frame.rawExpansionFraction > previousExpansionFraction + 0.001f
            val justStartedCollapsing =
                previousExpandedOverride && !frame.isExpandedOverride
            val justStartedExpanding =
                !previousExpandedOverride && frame.isExpandedOverride
            val isCollapsing = isCollapsingByFraction || justStartedCollapsing
            val isExpanding = isExpandingByFraction || justStartedExpanding
            val isFullyExpanded =
                frame.isExpandedOverride && frame.effectiveExpansionFraction >= 0.985f

            if (frame.effectiveExpansionFraction <= 0.001f && !frame.isExpandedOverride) {
                isDelayGateOpen = false
            } else if (isFullyExpanded) {
                isDelayGateOpen = true
            } else if (isDelayGateOpen) {
                if (applyPlaceholderDelayOnClose &&
                    isCollapsing &&
                    frame.effectiveExpansionFraction <= closeThreshold
                ) {
                    isDelayGateOpen = false
                }
            } else if (
                frame.effectiveExpansionFraction >= appearThreshold &&
                    (!applyPlaceholderDelayOnClose || isExpanding || frame.isExpandedOverride)
            ) {
                isDelayGateOpen = true
            }

            previousExpansionFraction = frame.rawExpansionFraction
            previousExpandedOverride = frame.isExpandedOverride
        }
    }

    val baseAlphaProvider = remember(normalStartThreshold, expansionFractionProvider) {
        {
            ((expansionFractionProvider().coerceIn(0f, 1f) - normalStartThreshold) /
                (1f - normalStartThreshold).coerceAtLeast(0.001f))
                .coerceIn(0f, 1f)
        }
    }
    val contentBlendAlpha by animateFloatAsState(
        targetValue = if (isDelayGateOpen) 1f else 0f,
        animationSpec = if (isDelayGateOpen) {
            tween(durationMillis = 260, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 140, easing = FastOutSlowInEasing)
        },
        label = "DelayedContentBlendAlpha"
    )
    val placeholderBlendAlpha by animateFloatAsState(
        targetValue = if (isDelayGateOpen) 0f else 1f,
        animationSpec = if (isDelayGateOpen) {
            tween(durationMillis = 360, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 140, easing = FastOutSlowInEasing)
        },
        label = "DelayedPlaceholderBlendAlpha"
    )

    if (shouldDelay) {
        Box(modifier = sharedBoundsModifier) {
            val shouldComposeContent = isDelayGateOpen

            if (shouldComposeContent) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = contentBlendAlpha * baseAlphaProvider()
                    }
                ) {
                    content()
                }
            }
            if (showPlaceholders && placeholderBlendAlpha > 0.001f) {
                Box(
                    modifier = Modifier.graphicsLayer { alpha = placeholderBlendAlpha }
                ) {
                    placeholder()
                }
            }
        }
    } else {
        Box(
            modifier = sharedBoundsModifier.graphicsLayer { alpha = baseAlphaProvider() }
        ) {
            content()
        }
    }
}

private data class DelayedContentFrame(
    val rawExpansionFraction: Float,
    val effectiveExpansionFraction: Float,
    val isExpandedOverride: Boolean
)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerSongInfo(
    title: String,
    artist: String,
    artistId: Long,
    artists: List<Artist>,
    expansionFractionProvider: () -> Float,
    textColor: Color,
    artistTextColor: Color,
    gradientEdgeColor: Color,
    playerViewModel: PlayerViewModel,
    onClickArtist: () -> Unit,
    modifier: Modifier = Modifier,
    album: String = "",
    albumId: Long = -1L,
    useLargeHeadline: Boolean = false,
    showAlbumSubtitle: Boolean = false,
    isPlayingProvider: () -> Boolean = { true },
    isBufferingProvider: () -> Boolean = { false }
) {
    val coroutineScope = rememberCoroutineScope()
    var isNavigatingToArtist by remember { mutableStateOf(false) }
    val resolvedArtistId by remember(artists, artistId) {
        derivedStateOf { artists.firstOrNull { it.id != 0L && it.id != -1L }?.id ?: artistId }
    }
    val baseTitleStyle = if (useLargeHeadline) {
        MaterialTheme.typography.headlineMediumEmphasized
    } else {
        MaterialTheme.typography.headlineSmall
    }
    val titleStyle = baseTitleStyle.copy(
        fontWeight = FontWeight.Bold,
        fontFamily = GoogleSansRounded,
        color = textColor
    )

    val artistStyle = MaterialTheme.typography.titleMedium.copy(
        letterSpacing = 0.sp,
        color = artistTextColor
    )

    Column(
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .graphicsLayer {
                val fraction = expansionFractionProvider()
                alpha = fraction
                translationY = (1f - fraction) * 24f
            }
    ) {
        AutoScrollingTextOnDemand(
            text = title,
            style = titleStyle,
            gradientEdgeColor = gradientEdgeColor,
            expansionFractionProvider = expansionFractionProvider,
            modifier = Modifier.fillMaxWidth(),
            canScroll = isPlayingProvider()
        )
        Spacer(modifier = Modifier.height(2.dp))

        AutoScrollingTextOnDemand(
            text = artist,
            style = artistStyle,
            gradientEdgeColor = gradientEdgeColor,
            expansionFractionProvider = expansionFractionProvider,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (isNavigatingToArtist) return@combinedClickable
                        coroutineScope.launch {
                            isNavigatingToArtist = true
                            try {
                                onClickArtist()
                            } finally {
                                isNavigatingToArtist = false
                            }
                        }
                    },
                    onLongClick = {
                        if (isNavigatingToArtist) return@combinedClickable
                        coroutineScope.launch {
                            isNavigatingToArtist = true
                            try {
                                playerViewModel.triggerArtistNavigationFromPlayer(resolvedArtistId)
                            } finally {
                                isNavigatingToArtist = false
                            }
                        }
                    }
                ),
            canScroll = isPlayingProvider()
        )

        if (showAlbumSubtitle && album.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = album,
                style = MaterialTheme.typography.bodyMedium,
                color = artistTextColor.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = albumId != -1L) {
                        playerViewModel.triggerAlbumNavigationFromPlayer(albumId)
                    }
            )
        }
    }
}

@Composable
private fun PlaceholderBox(
    modifier: Modifier,
    cornerRadius: Dp = 12.dp,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = color,
        tonalElevation = 0.dp
    ) {}
}

@Composable
private fun AlbumPlaceholder(
    height: Dp,
    color: Color,
    onColor: Color,
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified
) {
    Surface(
        modifier = modifier
            .then(if (width != Dp.Unspecified) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height),
        shape = RoundedCornerShape(18.dp),
        color = color,
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                modifier = Modifier.size(86.dp),
                painter = painterResource(R.drawable.pixelplay_base_monochrome),
                contentDescription = null,
                tint = onColor
            )
        }
    }
}

@Composable
private fun MetadataPlaceholder(
    expansionFractionProvider: () -> Float,
    color: Color,
    onColor: Color,
    showQueueButtons: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .graphicsLayer {
                val expansionFraction = expansionFractionProvider().coerceIn(0f, 1f)
                alpha = expansionFraction.coerceIn(0f, 1f)
                translationY = (1f - expansionFraction.coerceIn(0f, 1f)) * 24f
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            verticalArrangement = Arrangement.spacedBy(6.dp) //2.dp
        ) {
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(27.dp), //30.dp
                cornerRadius = 8.dp,
                color = color
            )
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth(0.46f)
                    .height(17.dp), //20.dp
                cornerRadius = 8.dp,
                color = onColor
            )
        }

        if (showQueueButtons) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(height = 42.dp, width = 50.dp),
                    shape = RoundedCornerShape(
                        topStart = 50.dp,
                        topEnd = 6.dp,
                        bottomStart = 50.dp,
                        bottomEnd = 6.dp
                    ),
                    color = onColor,
                    tonalElevation = 0.dp
                ) {}
                Surface(
                    modifier = Modifier.size(height = 42.dp, width = 50.dp),
                    shape = RoundedCornerShape(
                        topStart = 6.dp,
                        topEnd = 50.dp,
                        bottomStart = 6.dp,
                        bottomEnd = 50.dp
                    ),
                    color = onColor,
                    tonalElevation = 0.dp
                ) {}
            }
        } else {
            PlaceholderBox(
                modifier = Modifier.size(width = 48.dp, height = 48.dp),
                cornerRadius = 24.dp,
                color = onColor
            )
        }
    }
}

@Composable
private fun ProgressPlaceholder(
    color: Color,
    onColor: Color,
    showAudioMetaChip: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                cornerRadius = 3.dp,
                color = onColor.copy(alpha = 0.15f)
            )
            // Keep active segment in the layout tree but invisible to avoid visual noise.
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth(0.34f)
                    .height(6.dp)
                    .graphicsLayer { alpha = 0f },
                cornerRadius = 3.dp,
                color = color
            )
            // Keep thumb slot aligned but fully transparent.
            PlaceholderBox(
                modifier = Modifier
                    .padding(start = 92.dp)
                    .size(14.dp)
                    .graphicsLayer { alpha = 0f },
                cornerRadius = 7.dp,
                color = onColor
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaceholderBox(
                    modifier = Modifier
                        .width(34.dp)
                        .height(12.dp),
                    cornerRadius = 2.dp,
                    color = onColor
                )
                PlaceholderBox(
                    modifier = Modifier
                        .width(34.dp)
                        .height(12.dp),
                    cornerRadius = 2.dp,
                    color = onColor
                )
            }

            if (showAudioMetaChip) {
                PlaceholderBox(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(min = 96.dp, max = 180.dp)
                        .height(18.dp),
                    cornerRadius = 999.dp,
                    color = onColor.copy(alpha = 0.15f)
                )
            }
        }
    }
}

@Composable
private fun ControlsPlaceholder(color: Color, onColor: Color) {
    val rowCorners = 60.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 0.dp, vertical = 6.dp)
                .fillMaxWidth()
                .height(76.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaceholderBox(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    cornerRadius = 60.dp,
                    color = onColor
                )
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTL = rowCorners,
                        smoothnessAsPercentTL = 60,
                        cornerRadiusTR = rowCorners,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBL = rowCorners,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusBR = rowCorners,
                        smoothnessAsPercentBR = 60
                    ),
                    color = color,
                    tonalElevation = 0.dp
                ) {}
                PlaceholderBox(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    cornerRadius = 60.dp,
                    color = onColor
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp, max = 80.dp)
                .padding(horizontal = 0.dp)
                .padding(bottom = 4.dp)
                .background(
                    color = onColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(rowCorners)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    PlaceholderBox(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        cornerRadius = rowCorners,
                        color = onColor.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}

private data class TransportButtonColors(
    val container: Color,
    val content: Color
)

private fun expressivePlayPauseButtonColors(colorScheme: ColorScheme): TransportButtonColors {
    return TransportButtonColors(
        container = colorScheme.tertiaryFixedDim,
        content = colorScheme.onTertiaryFixed
    )
}

private fun expressiveSkipButtonColors(colorScheme: ColorScheme): TransportButtonColors {
    return TransportButtonColors(
        container = colorScheme.secondaryFixedDim,
        content = colorScheme.onSecondaryFixed
    )
}

@Composable
private fun BottomToggleRow(
    modifier: Modifier,
    isShuffleEnabled: Boolean,
    isShuffleTransitionInProgress: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    showLyricsAndQueueToggles: Boolean = false,
    activeSidePanel: NowPlayingSidePanel = NowPlayingSidePanel.NONE,
    onLyricsClick: () -> Unit = {},
    onQueueClick: () -> Unit = {}
) {
    val isFavorite = isFavoriteProvider()
    val rowCorners = 60.dp
    val scheme = LocalMaterialTheme.current
    val containerBg = scheme.onPrimaryContainer.copy(alpha = 0.12f)
    val inactiveBg = scheme.onPrimaryContainer.copy(alpha = 0.08f)
    val inactiveContentColor = scheme.onPrimaryContainer.copy(alpha = 0.90f)

    Box(
        modifier = modifier.background(
            color = containerBg,
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = rowCorners,
                smoothnessAsPercentTL = 60,
                cornerRadiusTR = rowCorners,
                smoothnessAsPercentTR = 60,
                cornerRadiusBL = rowCorners,
                smoothnessAsPercentBL = 60,
                cornerRadiusBR = rowCorners,
                smoothnessAsPercentBR = 60
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .clip(
                    AbsoluteSmoothCornerShape(
                        cornerRadiusTL = rowCorners,
                        smoothnessAsPercentTL = 60,
                        cornerRadiusTR = rowCorners,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBL = rowCorners,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusBR = rowCorners,
                        smoothnessAsPercentBR = 60
                    )
                )
                .background(Color.Transparent),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val commonModifier = Modifier.weight(1f)

            ToggleSegmentButton(
                modifier = commonModifier,
                active = isShuffleEnabled,
                enabled = !isShuffleTransitionInProgress,
                activeColor = scheme.primaryFixed,
                activeCornerRadius = rowCorners,
                activeContentColor = scheme.onPrimaryFixed,
                inactiveColor = inactiveBg,
                inactiveContentColor = inactiveContentColor,
                onClick = onShuffleToggle,
                iconId = R.drawable.rounded_shuffle_24,
                contentDesc = stringResource(R.string.queue_cd_toggle_shuffle_action)
            )
            val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
            val repeatIcon = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.rounded_repeat_one_24
                Player.REPEAT_MODE_ALL -> R.drawable.rounded_repeat_24
                else -> R.drawable.rounded_repeat_24
            }
            ToggleSegmentButton(
                modifier = commonModifier,
                active = repeatActive,
                activeColor = scheme.secondaryFixed,
                activeCornerRadius = rowCorners,
                activeContentColor = scheme.onSecondaryFixed,
                inactiveColor = inactiveBg,
                inactiveContentColor = inactiveContentColor,
                onClick = onRepeatToggle,
                iconId = repeatIcon,
                contentDesc = stringResource(R.string.queue_cd_toggle_repeat_action)
            )
            ToggleSegmentButton(
                modifier = commonModifier,
                active = isFavorite,
                activeColor = scheme.tertiaryFixed,
                activeCornerRadius = rowCorners,
                activeContentColor = scheme.onTertiaryFixed,
                inactiveColor = inactiveBg,
                inactiveContentColor = inactiveContentColor,
                onClick = onFavoriteToggle,
                iconId = if (isFavorite) R.drawable.round_favorite_24 else R.drawable.rounded_favorite_24,
                contentDesc = stringResource(R.string.player_cd_favorite)
            )

            if (showLyricsAndQueueToggles) {
                ToggleSegmentButton(
                    modifier = commonModifier,
                    active = activeSidePanel == NowPlayingSidePanel.LYRICS,
                    activeColor = scheme.onPrimaryContainer,
                    activeCornerRadius = rowCorners,
                    activeContentColor = scheme.primaryContainer,
                    inactiveColor = inactiveBg,
                    inactiveContentColor = inactiveContentColor,
                    onClick = onLyricsClick,
                    iconId = R.drawable.rounded_lyrics_24,
                    contentDesc = stringResource(R.string.player_panel_lyrics)
                )
                ToggleSegmentButton(
                    modifier = commonModifier,
                    active = activeSidePanel == NowPlayingSidePanel.QUEUE,
                    activeColor = scheme.onPrimaryContainer,
                    activeCornerRadius = rowCorners,
                    activeContentColor = scheme.primaryContainer,
                    inactiveColor = inactiveBg,
                    inactiveContentColor = inactiveContentColor,
                    onClick = onQueueClick,
                    iconId = R.drawable.rounded_queue_music_24,
                    contentDesc = stringResource(R.string.player_panel_up_next)
                )
            }
        }
    }
}
