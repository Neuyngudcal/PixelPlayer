package com.theveloper.pixelplay.presentation.components

import android.widget.Toast
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.R
import androidx.activity.compose.BackHandler
import com.theveloper.pixelplay.presentation.components.scoped.LyricsPredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.ContentScale
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.AutoScrollingText
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.util.lerp
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LowPriority
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.ui.text.font.FontStyle
import com.theveloper.pixelplay.presentation.components.lyrics.LyricsProviderPrioritySheet
import com.theveloper.pixelplay.presentation.components.lyrics.LyricsShareCardSheet
import com.theveloper.pixelplay.presentation.components.lyrics.MornyeSweepingWordSpan
import com.theveloper.pixelplay.presentation.components.lyrics.rememberInterpolatedLyricsPosition
import com.theveloper.pixelplay.presentation.components.lyrics.rememberTrailingCascadeWaveOffset
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import com.theveloper.pixelplay.data.network.lyrics.LyricsProviderType
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.consumePositionChange
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.presentation.screens.TabAnimation
import com.theveloper.pixelplay.presentation.components.subcomps.FetchLyricsDialog
import com.theveloper.pixelplay.presentation.components.subcomps.PlayerSeekBar
import com.theveloper.pixelplay.presentation.viewmodel.LyricsSearchUiState
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.utils.BubblesLine
import com.theveloper.pixelplay.utils.ProviderText
import com.theveloper.pixelplay.presentation.components.snapping.ExperimentalSnapperApi
import com.theveloper.pixelplay.presentation.components.snapping.SnapperLayoutInfo
import com.theveloper.pixelplay.presentation.components.snapping.rememberLazyListSnapperLayoutInfo
import com.theveloper.pixelplay.presentation.components.snapping.rememberSnapperFlingBehavior
import com.theveloper.pixelplay.utils.LyricsUtils
import com.theveloper.pixelplay.presentation.components.subcomps.LyricsMoreBottomSheet
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.theveloper.pixelplay.data.preferences.dataStore

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.utils.MultiLangRomanizer

internal data class LyricsSheetColors(
    val container: Color,
    val content: Color,
    val controlContainer: Color,
    val controlContent: Color,
    val accent: Color,
    val accentContent: Color,
    val lyricHighlight: Color,
    val playPauseContainer: Color,
    val playPauseContent: Color,
    val syncButtonContainer: Color,
    val syncButtonContent: Color
)

internal fun lyricsSheetColors(colorScheme: ColorScheme): LyricsSheetColors {
    val container = colorScheme.primaryContainer
    val content = colorScheme.onPrimaryContainer
    val accent = colorScheme.primary
    val accentContent = colorScheme.onPrimary

    return LyricsSheetColors(
        container = container,
        content = content,
        controlContainer = colorScheme.surfaceContainerLowest,
        controlContent = colorScheme.onSurface,
        accent = accent,
        accentContent = accentContent,
        lyricHighlight = preferredContrastColor(
            background = container,
            preferred = accent,
            fallback = content
        ),
        playPauseContainer = colorScheme.tertiaryFixedDim,
        playPauseContent = colorScheme.onTertiaryFixed,
        syncButtonContainer = colorScheme.secondaryFixedDim,
        syncButtonContent = colorScheme.onSecondaryFixed
    )
}

private fun preferredContrastColor(
    background: Color,
    preferred: Color,
    fallback: Color,
    minContrastRatio: Double = 4.5
): Color {
    if (contrastRatio(preferred, background) >= minContrastRatio) return preferred
    if (contrastRatio(fallback, background) >= minContrastRatio) return fallback

    val blackContrast = contrastRatio(Color.Black, background)
    val whiteContrast = contrastRatio(Color.White, background)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

private fun contrastRatio(foreground: Color, background: Color): Double {
    val foregroundLuminance = foreground.relativeLuminance()
    val backgroundLuminance = background.relativeLuminance()
    val lighter = maxOf(foregroundLuminance, backgroundLuminance)
    val darker = minOf(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun Color.relativeLuminance(): Double {
    val argb = encodedSrgbArgb()
    val red = linearizedChannel((argb shr 16) and 0xFF)
    val green = linearizedChannel((argb shr 8) and 0xFF)
    val blue = linearizedChannel(argb and 0xFF)
    return (0.2126 * red) + (0.7152 * green) + (0.0722 * blue)
}

private fun Color.encodedSrgbArgb(): Int = (value shr 32).toInt()

private fun linearizedChannel(channel: Int): Double {
    val value = channel / 255.0
    return if (value <= 0.03928) {
        value / 12.92
    } else {
        ((value + 0.055) / 1.055).pow(2.4)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsSheet(
    stablePlayerStateFlow: StateFlow<StablePlayerState>,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSearchUiState: LyricsSearchUiState,
    resetLyricsForCurrentSong: () -> Unit,
    onSearchLyrics: (Boolean) -> Unit,
    onPickResult: (LyricsSearchResult) -> Unit,
    onManualSearch: (String, String?) -> Unit,
    onImportLyrics: () -> Unit,
    onDismissLyricsSearch: () -> Unit,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    lyricsTextStyle: TextStyle,
    colorScheme: ColorScheme,
    onBackClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    immersiveLyricsEnabled: Boolean,
    immersiveLyricsTimeout: Long,
    isImmersiveTemporarilyDisabled: Boolean,
    onSetImmersiveTemporarilyDisabled: (Boolean) -> Unit,
    onSaveLyricsToFile: (Song, Lyrics, Boolean) -> Unit,
    onTranslateViaAi: () -> Unit,
    // BottomToggleRow Params
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    swipeThreshold: Dp = 100.dp,
    highlightZoneFraction: Float = 0.08f, // Reduced from 0.22 for less padding
    highlightOffsetDp: Dp = 32.dp,
    autoscrollAnimationSpec: AnimationSpec<Float>? = null // null = auto-detect from preference
) {
    // ─── Enter / Exit animation state ────────────────────────────────────────
    // Mirrors the player-sheet pattern: a plain Float in state drives graphicsLayer
    // at draw-phase (no recomposition per frame). 0f = fully visible, 1f = dismissed.
    var backProgress by remember { mutableFloatStateOf(1f) }

    // Draw-phase lambda provider — read only inside graphicsLayer so layout is never
    // re-triggered during the gesture (same technique as SheetVisualState).
    val backProgressProvider = rememberUpdatedState(backProgress)

    // Enter animation: slide up from +6 % height + fade in.
    LaunchedEffect(Unit) {
        val anim = Animatable(1f)
        anim.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioLowBouncy
            )
        ) { backProgress = value }
    }

    // Predictive-back (Android 13+) or plain back on older devices.
    LyricsPredictiveBackHandler(
        enabled = true,
        onProgressChanged = { backProgress = it },
        onBack = onBackClick
    )

    val stablePlayerState by stablePlayerStateFlow.collectAsStateWithLifecycle()
    val sheetColors = remember(colorScheme) { lyricsSheetColors(colorScheme) }
    val backgroundColor = sheetColors.controlContainer
    val onBackgroundColor = sheetColors.controlContent
    val containerColor = sheetColors.container
    val contentColor = sheetColors.content
    val accentColor = sheetColors.accent
    val onAccentColor = sheetColors.accentContent
    val lyricHighlightColor = sheetColors.lyricHighlight
    val playPauseColor = sheetColors.playPauseContainer
    val onPlayPauseColor = sheetColors.playPauseContent

    val isLoadingLyrics by remember(stablePlayerState) { derivedStateOf { stablePlayerState.isLoadingLyrics } }
    val lyrics by remember(stablePlayerState) { derivedStateOf { stablePlayerState.lyrics } }
    val isPlaying by remember(stablePlayerState) { derivedStateOf { stablePlayerState.isPlaying } }
    val currentSong by remember(stablePlayerState) { derivedStateOf { stablePlayerState.currentSong } }

    // Plain lyrics carry their romanization / translation as extra "\n" lines inside each entry.
    val plainLineParts = remember(lyrics) { lyrics?.plain?.map(::splitPlainLyricLine).orEmpty() }

    val hasTranslatedLyrics = remember(lyrics, plainLineParts) {
        val hasSynced = lyrics?.synced?.any { !it.translation.isNullOrBlank() } == true
        val hasPlain = plainLineParts.any { it.translation.isNotBlank() }
        hasSynced || hasPlain
    }

    val hasRomanizedLyrics = remember(lyrics, plainLineParts) {
        val hasSynced = lyrics?.synced?.any { !it.romanization.isNullOrBlank() } == true
        val hasPlain = plainLineParts.any { it.romanization.isNotBlank() } ||
            lyrics?.plain?.any { line -> MultiLangRomanizer.isScriptThatNeedsRomanization(line) } == true
        hasSynced || hasPlain
    }

    val context = LocalContext.current

    // Read lyrics alignment preference internally from DataStore
    val lyricsAlignmentFlow = remember(context) {
        context.dataStore.data
            .map { it[stringPreferencesKey("lyrics_alignment")] ?: "left" }
            .distinctUntilChanged()
    }
    val lyricsAlignment by lyricsAlignmentFlow.collectAsStateWithLifecycle(initialValue = "left")

    // Read lyrics translation preference internally from DataStore
    val showLyricsTranslationFlow = remember(context) {
        context.dataStore.data
            .map { it[booleanPreferencesKey("show_lyrics_translation")] ?: true }
            .distinctUntilChanged()
    }
    val showLyricsTranslation by showLyricsTranslationFlow.collectAsStateWithLifecycle(initialValue = true)

    // Read lyrics romanization preference internally from DataStore
    val showLyricsRomanizationFlow = remember(context) {
        context.dataStore.data
            .map { it[booleanPreferencesKey("show_lyrics_romanization")] ?: true }
            .distinctUntilChanged()
    }
    val showLyricsRomanization by showLyricsRomanizationFlow.collectAsStateWithLifecycle(initialValue = true)

    // Read animated lyrics preference internally from DataStore
    val useAnimatedLyricsFlow = remember(context) {
        context.dataStore.data
            .map { it[booleanPreferencesKey("use_animated_lyrics")] ?: false }
            .distinctUntilChanged()
    }
    val useAnimatedLyrics by useAnimatedLyricsFlow.collectAsStateWithLifecycle(initialValue = false)

    val animatedLyricsBlurEnabledFlow = remember(context) {
        context.dataStore.data
            .map { it[booleanPreferencesKey("animated_lyrics_blur_enabled")] ?: true }
            .distinctUntilChanged()
    }
    val animatedLyricsBlurEnabled by animatedLyricsBlurEnabledFlow.collectAsStateWithLifecycle(initialValue = true)

    val disableBlurAllOverFlow = remember(context) {
        context.dataStore.data
            .map { it[booleanPreferencesKey("disable_blur_all_over")] ?: false }
            .distinctUntilChanged()
    }
    val disableBlurAllOver by disableBlurAllOverFlow.collectAsStateWithLifecycle(initialValue = false)

    val animatedLyricsBlurStrengthFlow = remember(context) {
        context.dataStore.data
            .map { it[androidx.datastore.preferences.core.floatPreferencesKey("animated_lyrics_blur_strength")] ?: 2.5f }
            .distinctUntilChanged()
    }
    val animatedLyricsBlurStrength by animatedLyricsBlurStrengthFlow.collectAsStateWithLifecycle(initialValue = 2.5f)

    // Read keep-screen-on preference from DataStore
    val keepScreenOnFlow = remember(context) {
        context.dataStore.data
            .map { it[booleanPreferencesKey("keep_screen_on_lyrics")] ?: false }
            .distinctUntilChanged()
    }
    var keepScreenOn by remember { mutableStateOf(false) }
    // Sync DataStore → local state
    LaunchedEffect(Unit) {
        keepScreenOnFlow.collect { keepScreenOn = it }
    }
    val coroutineScope = rememberCoroutineScope()

    // Apply FLAG_KEEP_SCREEN_ON via the window when enabled
    val view = LocalView.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(keepScreenOn, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && keepScreenOn) {
                keepScreenOn = false
                coroutineScope.launch {
                    context.dataStore.edit { prefs ->
                        prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = false
                    }
                }
            }
        }

        if (keepScreenOn) {
            view.keepScreenOn = true
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            view.keepScreenOn = false
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val resolvedAutoscrollSpec = autoscrollAnimationSpec ?: if (useAnimatedLyrics) {
        spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        )
    } else {
        tween(durationMillis = 450, easing = FastOutSlowInEasing)
    }

    var showFetchLyricsDialog by remember { mutableStateOf(false) }
    // Flag to prevent dialog from showing briefly after reset
    var wasResetTriggered by remember { mutableStateOf(false) }
    // Save lyrics dialog state
    var showSaveLyricsDialog by remember { mutableStateOf(false) }
    var showSyncControls by remember { mutableStateOf(false) }
    var previewSeekPositionMs by remember(currentSong?.id) { mutableStateOf<Long?>(null) }
    var showShareCardSheet by remember { mutableStateOf(false) }
    var shareCardInitialLineIndex by remember { mutableStateOf<Int?>(null) }
    var showProviderPrioritySheet by remember { mutableStateOf(false) }

    var showSyncedLyrics by remember(lyrics) {
        mutableStateOf(
            when {
                !lyrics?.synced.isNullOrEmpty() -> true
                !lyrics?.plain.isNullOrEmpty() -> false
                else -> null
            }
        )
    }

    val hasSyncedLyrics = remember(lyrics) {
        !lyrics?.synced.isNullOrEmpty()
    }

    // Immersive Mode State
    var immersiveMode by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showMoreSheet by remember { mutableStateOf(false) }
    val moreSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // Swipe Gesture State
    val hapticFeedback = LocalHapticFeedback.current
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // Direction of the last swipe, kept until the feedback overlay has fully faded out so the
    // icon doesn't flip to the opposite edge when dragOffset is reset.
    var swipeTowardsNext by remember { mutableStateOf(true) }
    var isSwipeActive by remember { mutableStateOf(false) }
    var hasTriggeredAction by remember { mutableStateOf(false) }
    val swipeThresholdPx = with(LocalDensity.current) { swipeThreshold.toPx() }
    val overlayTranslation = remember { Animatable(0f) }
    val swipeProgress = remember { Animatable(0f) }

    // Reset keep-screen-on when the physical screen goes off (power button / OEM sleep gesture).
    // ACTION_SCREEN_OFF is a guaranteed platform broadcast; no OEM can suppress it.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    keepScreenOn = false
                    coroutineScope.launch {
                        context.dataStore.edit { prefs ->
                            prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = false
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Auto-hide controls logic
    LaunchedEffect(immersiveLyricsEnabled, lastInteractionTime, showSyncedLyrics, isImmersiveTemporarilyDisabled) {
        if (immersiveLyricsEnabled && showSyncedLyrics == true && !isImmersiveTemporarilyDisabled) {
            delay(immersiveLyricsTimeout)
            immersiveMode = true
        } else {
            immersiveMode = false
        }
    }

    fun resetImmersiveTimer() {
        lastInteractionTime = System.currentTimeMillis()
        immersiveMode = false
    }

    LaunchedEffect(currentSong, lyrics, isLoadingLyrics) {
        if (currentSong != null && lyrics == null && !isLoadingLyrics) {
            // Only show dialog if reset was not just triggered
            if (!wasResetTriggered) {
                showFetchLyricsDialog = true
            }
        } else if (lyrics != null || isLoadingLyrics) {
            showFetchLyricsDialog = false
            wasResetTriggered = false // Reset the flag when lyrics are loaded
        }
    }

    if (showFetchLyricsDialog) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            FetchLyricsDialog(
                uiState = lyricsSearchUiState,
                currentSong = currentSong,
                onConfirm = onSearchLyrics,
                onPickResult = onPickResult,
                onManualSearch = onManualSearch,
                onDismiss = {
                    showFetchLyricsDialog = false
                    onDismissLyricsSearch()
                },
                onImport = onImportLyrics
            )
        }
    }

    // Save Lyrics Dialog
    if (showSaveLyricsDialog && lyrics != null && currentSong != null) {
        val hasSynced = !lyrics?.synced.isNullOrEmpty()
        val hasPlain = !lyrics?.plain.isNullOrEmpty()
        
        AlertDialog(
            onDismissRequest = { showSaveLyricsDialog = false },
            title = { Text(stringResource(R.string.lyrics_save_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.lyrics_save_dialog_message))
                    Spacer(modifier = Modifier.height(16.dp))
                    if (hasSynced) {
                        FilledTonalButton(
                            onClick = {
                                showSaveLyricsDialog = false
                                onSaveLyricsToFile(
                                    currentSong!!,
                                    lyrics!!,
                                    true
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.lyrics_save_synced))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (hasPlain) {
                        OutlinedButton(
                            onClick = {
                                showSaveLyricsDialog = false
                                onSaveLyricsToFile(
                                    currentSong!!,
                                    lyrics!!,
                                    false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.lyrics_save_plain))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSaveLyricsDialog = false }) {
                    Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            // ─── Enter / Predictive-back exit transformation ──────────────────
            // Read backProgressProvider inside graphicsLayer (draw-phase) — no layout
            // pass is triggered per gesture frame, same pattern as SheetVisualState.
            // 0f = fully visible, 1f = fully dismissed.
            // Effect: scale down to 92 % + slide down 8 % of height + fade to 72 % alpha.
            // Matches Android predictive back spec for full-screen destinations and
            // mirrors the scale+alpha treatment used across the rest of the app.
            .graphicsLayer {
                val p = backProgressProvider.value
                val scale = lerp(1f, 0.92f, p)
                scaleX = scale
                scaleY = scale
                translationY = lerp(0f, size.height * 0.08f, p)
                alpha = lerp(1f, 0.72f, p)
                // Round corners only while entering / predictive-back so the fully open
                // screen doesn't expose the player behind its four corners.
                shape = RoundedCornerShape(lerp(0f, 32f, (p * 4f).coerceIn(0f, 1f)).dp)
                clip = p > 0f
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isSwipeActive = true
                        hasTriggeredAction = false
                        dragOffset = 0f
                        resetImmersiveTimer()
                        coroutineScope.launch {
                            swipeProgress.snapTo(0f)
                        }
                    },
                    onDragEnd = {
                        isSwipeActive = false
                        val committed = abs(dragOffset) > swipeThresholdPx && !hasTriggeredAction 
                        
                        if (committed) {
                            if (dragOffset > 0) onPrev() else onNext()
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        coroutineScope.launch {
                             swipeProgress.animateTo(0f, tween(200))
                             dragOffset = 0f
                        }
                    },
                    onDragCancel = {
                        isSwipeActive = false
                        dragOffset = 0f
                        coroutineScope.launch {
                            swipeProgress.animateTo(0f, tween(200))
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        resetImmersiveTimer()
                        
                        if (!hasTriggeredAction) {
                            dragOffset += dragAmount.x
                            if (dragOffset != 0f) swipeTowardsNext = dragOffset < 0
                            val progress = (abs(dragOffset) / swipeThresholdPx).coerceIn(0f, 1f)
                            
                            coroutineScope.launch {
                                swipeProgress.snapTo(progress)
                            }
                        }
                    }
                )
            },
        containerColor = containerColor,
        contentColor = contentColor,
        // Removed TopBar and FAB
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    resetImmersiveTimer()
                }
        ) {
            val initialSyncedLineIndex = remember(lyrics, playbackPositionFlow, lyricsSyncOffset) {
                resolveCurrentLineIndex(
                    lines = lyrics?.synced.orEmpty(),
                    position = (playbackPositionFlow.value + lyricsSyncOffset).coerceAtLeast(0L)
                ).coerceAtLeast(0)
            }
            val syncedListState = rememberLazyListState(
                initialFirstVisibleItemIndex = initialSyncedLineIndex
            )
            val staticListState = rememberLazyListState()

            // Lyrics Content (Weight 1)
            Box(
                modifier = Modifier
                    .align(Alignment.Start)
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Track Info Header (Fixed at top)
                AnimatedContent(
                    targetState = currentSong,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(300)) + 
                         scaleIn(initialScale = 0.9f, animationSpec = tween(300)))
                        .togetherWith(fadeOut(animationSpec = tween(300)))
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .zIndex(2f)
                        .wrapContentWidth(),
                    label = "headerAnimation"
                ) { song ->
                    LyricsTrackInfo(
                        song = song,
                        modifier = Modifier
                            .padding(
                                top = 4.dp, bottom = 24.dp, start = 18.dp, end = 18.dp
                            )
                            .background(
                                color = backgroundColor,
                                shape = CircleShape
                            )
                            .wrapContentWidth()
                            .animateContentSize(), // Animate width changes
                        contentColor = onBackgroundColor,
                        isPlaying = isPlaying
                    )
                }

                when (showSyncedLyrics) {
                    null -> {
                        // Centered within the area below the header; scrolls instead of being
                        // clipped when the card is taller than the viewport (landscape / split screen).
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 110.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (isLoadingLyrics) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = stringResource(R.string.lyrics_loading),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LinearWavyProgressIndicator(
                                                trackColor = accentColor.copy(alpha = 0.4f),
                                                color = accentColor,
                                                modifier = Modifier.width(100.dp)
                                            )
                                        }
                                    } else {
                                        // Inline Online Search Card directly inside the player for files without lyrics
                                        Surface(
                                            shape = RoundedCornerShape(28.dp),
                                            color = backgroundColor.copy(alpha = 0.92f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(22.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.MusicNote,
                                                    contentDescription = null,
                                                    tint = accentColor,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                                Text(
                                                    text = stringResource(R.string.lyrics_empty_title),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontFamily = GoogleSansRounded,
                                                    fontWeight = FontWeight.Bold,
                                                    color = onBackgroundColor,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = stringResource(R.string.lyrics_empty_description),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = onBackgroundColor.copy(alpha = 0.75f),
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Button(
                                                    onClick = {
                                                        wasResetTriggered = false
                                                        onSearchLyrics(true)
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(16.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = accentColor,
                                                        contentColor = onAccentColor
                                                    )
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.CloudSync,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = stringResource(R.string.lyrics_empty_search_auto),
                                                        fontFamily = GoogleSansRounded,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                FilledTonalButton(
                                                    onClick = {
                                                        wasResetTriggered = false
                                                        showFetchLyricsDialog = true
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(16.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Search,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = stringResource(R.string.lyrics_empty_search_manual),
                                                        fontFamily = GoogleSansRounded
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = { showProviderPrioritySheet = true },
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(14.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.LowPriority,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = stringResource(R.string.lyrics_empty_sources),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    OutlinedButton(
                                                        onClick = onImportLyrics,
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(14.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.UploadFile,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = stringResource(R.string.lyrics_empty_import),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                        }
                    }

                    true -> {
                        lyrics?.synced?.let { synced ->
                            SyncedLyricsList(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                contentPadding = PaddingValues(top = 130.dp, bottom = 100.dp),
                                lines = synced,
                                listState = syncedListState,
                                playbackPositionFlow = playbackPositionFlow,
                                isPlaying = isPlaying,
                                lyricsSyncOffset = lyricsSyncOffset,
                                positionOverrideMs = previewSeekPositionMs,
                                accentColor = lyricHighlightColor,
                                textStyle = lyricsTextStyle,
                                onLineClick = { syncedLine -> 
                                    onSeekTo(
                                        resolveSeekPositionMs(
                                            lineTimeMs = syncedLine.time.toLong(),
                                            lyricsSyncOffsetMs = lyricsSyncOffset
                                        )
                                    )
                                    resetImmersiveTimer()
                                },
                                onLineLongClick = { lineIdx, _ ->
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    shareCardInitialLineIndex = lineIdx
                                    showShareCardSheet = true
                                },
                                highlightZoneFraction = highlightZoneFraction,
                                highlightOffsetDp = highlightOffsetDp,
                                autoscrollAnimationSpec = resolvedAutoscrollSpec,
                                useAnimatedLyrics = useAnimatedLyrics,
                                animatedLyricsBlurEnabled = animatedLyricsBlurEnabled && !disableBlurAllOver,
                                animatedLyricsBlurStrength = animatedLyricsBlurStrength,
                                immersiveMode = immersiveMode,
                                lyricsAlignment = lyricsAlignment,
                                showTranslation = showLyricsTranslation,
                                showRomanization = showLyricsRomanization,
                                footer = {
                                    if (lyrics?.areFromRemote == true || !lyrics?.provider.isNullOrBlank()) {
                                        val providerName = lyrics?.provider ?: "LRCLIB"
                                        item(key = "provider_text") {
                                            ProviderText(
                                                providerText = stringResource(R.string.lyrics_provided_by),
                                                uri = LyricsProviderType.resolveWebsiteUri(providerName),
                                                providerName = providerName,
                                                textAlign = TextAlign.Center,
                                                accentColor = lyricHighlightColor,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }

                    false -> {
                        lyrics?.plain?.let { plain ->
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = staticListState,
                                contentPadding = PaddingValues(
                                    start = 24.dp,
                                    end = 24.dp,
                                    top = 130.dp,
                                    // Clear the 80dp bottom fade so the last lines stay readable.
                                    bottom = 100.dp
                                )
                            ) {
                                itemsIndexed(
                                    items = plain,
                                    key = { index, line -> "$index-$line" }
                                ) { index, line ->
                                    PlainLyricsLine(
                                        line = line,
                                        style = lyricsTextStyle,
                                        lyricsAlignment = lyricsAlignment,
                                        showTranslation = showLyricsTranslation,
                                        showRomanization = showLyricsRomanization,
                                        onLongClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            shareCardInitialLineIndex = index
                                            showShareCardSheet = true
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                if (lyrics?.areFromRemote == true || !lyrics?.provider.isNullOrBlank()) {
                                    val providerName = lyrics?.provider ?: "LRCLIB"
                                    item(key = "plain_provider_text") {
                                        ProviderText(
                                            providerText = stringResource(R.string.lyrics_provided_by),
                                            uri = LyricsProviderType.resolveWebsiteUri(providerName),
                                            providerName = providerName,
                                            textAlign = TextAlign.Center,
                                            accentColor = lyricHighlightColor,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Top Gradient for fade
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(containerColor, Color.Transparent)
                            )
                        )
                )

                // Bottom Gradient for fade
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, containerColor)
                            )
                        )
                )
            }

            // Controls Section (Auto-hide in immersive mode)
            AnimatedVisibility(
                visible = !immersiveMode,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(containerColor)
                        .padding(bottom = paddingValues.calculateBottomPadding() + 10.dp, end = 16.dp, start = 16.dp)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    // Reset timer on any touch down or move in this area
                                    if (event.changes.any { it.pressed }) {
                                         resetImmersiveTimer()
                                    }
                                }
                            }
                        }
                ) {
                                AnimatedVisibility(
                    visible = showSyncedLyrics == true && lyrics?.synced != null && showSyncControls,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    LyricsSyncControls(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        offsetMillis = lyricsSyncOffset,
                        onOffsetChange = onLyricsSyncOffsetChange,
                        backgroundColor = backgroundColor,
                        accentColor = sheetColors.syncButtonContainer,
                        onAccentColor = sheetColors.syncButtonContent,
                        onBackgroundColor = onBackgroundColor
                    )
                }

                // Playback Controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Play/Pause Button (Smaller)
                    val playPauseCornerRadius by animateDpAsState(
                        targetValue = if (isPlaying) 18.dp else 50.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "playPauseShape"
                    )

                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(RoundedCornerShape(playPauseCornerRadius))
                            .background(playPauseColor)
                            .clickable {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPlayPause()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = isPlaying,
                            label = "playPauseIconAnimation"
                        ) { playing ->
                            if (playing) {
                                Icon(
                                    modifier = Modifier.size(32.dp),
                                    imageVector = Icons.Rounded.Pause,
                                    contentDescription = stringResource(R.string.common_pause),
                                    tint = onPlayPauseColor
                                )
                            } else {
                                Icon(
                                    modifier = Modifier.size(32.dp),
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.common_play),
                                    tint = onPlayPauseColor
                                )
                            }
                        }
                    }

                    // Progress Bar
                    LyricsPlaybackSeekBar(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        playbackPositionFlow = playbackPositionFlow,
                        backgroundColor = backgroundColor,
                        onBackgroundColor = onBackgroundColor,
                        accentColor = accentColor,
                        totalDuration = stablePlayerState.totalDuration,
                        onSeekTo = onSeekTo,
                        onSeekPreviewChange = { previewSeekPositionMs = it },
                        isPlaying = isPlaying
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Floating Toolbar
                LyricsFloatingToolbar(
                    modifier = Modifier.padding(horizontal = 0.dp),
                    showSyncedLyrics = showSyncedLyrics,
                    hasSyncedLyrics = hasSyncedLyrics,
                    onShowSyncedLyricsChange = { showSyncedLyrics = it },
                    onNavigateBack = {
                        onBackClick()
                    },
                    onMoreClick = { showMoreSheet = true },
                    backgroundColor = backgroundColor,
                    onBackgroundColor = onBackgroundColor,
                    accentColor = accentColor,
                    onAccentColor = onAccentColor,
                    // Pass progress so the back button animates with the gesture (draw-phase).
                    backProgressProvider = { backProgressProvider.value },
                )
             }
            }
        }

        if (showMoreSheet) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = MaterialTheme.typography,
                shapes = MaterialTheme.shapes
            ) {
                LyricsMoreBottomSheet(
                    onDismissRequest = { showMoreSheet = false },
                    sheetState = moreSheetState,
                    lyrics = lyrics,
                    showSyncedLyrics = showSyncedLyrics == true,
                    isSyncControlsVisible = showSyncControls,
                    onSaveLyricsAsLrc = { showSaveLyricsDialog = true },
                    onResetImportedLyrics = {
                        wasResetTriggered = true
                        resetLyricsForCurrentSong()
                    },
                    onTranslateViaAi = onTranslateViaAi,
                    onToggleSyncControls = {
                        resetImmersiveTimer()
                        showSyncControls = !showSyncControls
                    },
                    onOpenShareCardClick = {
                        shareCardInitialLineIndex = null
                        showShareCardSheet = true
                    },
                    onOpenProvidersChainClick = {
                        showProviderPrioritySheet = true
                    },
                    isImmersiveTemporarilyDisabled = isImmersiveTemporarilyDisabled,
                    onSetImmersiveTemporarilyDisabled = {
                        resetImmersiveTimer()
                        onSetImmersiveTemporarilyDisabled(it)
                    },
                    keepScreenOn = keepScreenOn,
                    onKeepScreenOnChange = { enabled ->
                        keepScreenOn = enabled
                        coroutineScope.launch {
                            context.dataStore.edit { prefs ->
                                prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = enabled
                            }
                        }
                    },
                    lyricsAlignment = lyricsAlignment,
                    onLyricsAlignmentChange = { newAlignment ->
                        coroutineScope.launch {
                            context.dataStore.edit { preferences ->
                                preferences[stringPreferencesKey("lyrics_alignment")] = newAlignment
                            }
                        }
                    },
                    hasTranslatedLyrics = hasTranslatedLyrics,
                    hasRomanizedLyrics = hasRomanizedLyrics,
                    showTranslation = showLyricsTranslation,
                    showRomanization = showLyricsRomanization,
                    onShowTranslationChange = { enabled ->
                        resetImmersiveTimer()
                        coroutineScope.launch {
                            context.dataStore.edit { preferences ->
                                preferences[booleanPreferencesKey("show_lyrics_translation")] = enabled
                            }
                        }
                    },
                    onShowRomanizationChange = { enabled ->
                        resetImmersiveTimer()
                        coroutineScope.launch {
                            context.dataStore.edit { preferences ->
                                preferences[booleanPreferencesKey("show_lyrics_romanization")] = enabled
                            }
                        }
                    },
                    immersiveLyricsEnabled = immersiveLyricsEnabled,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    isFavoriteProvider = isFavoriteProvider,
                    onShuffleToggle = {
                        resetImmersiveTimer()
                        onShuffleToggle()
                    },
                    onRepeatToggle = {
                        resetImmersiveTimer()
                        onRepeatToggle()
                    },
                    onFavoriteToggle = {
                        resetImmersiveTimer()
                        onFavoriteToggle()
                    },
                )
            }
        }

        if (showShareCardSheet && lyrics != null) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = MaterialTheme.typography,
                shapes = MaterialTheme.shapes
            ) {
                LyricsShareCardSheet(
                    lyrics = lyrics,
                    songTitle = currentSong?.title.orEmpty(),
                    artistName = currentSong?.displayArtist.orEmpty(),
                    artworkUri = currentSong?.albumArtUriString,
                    accentColor = lyricHighlightColor,
                    initialSelectedLineIndex = shareCardInitialLineIndex,
                    onDismissRequest = {
                        showShareCardSheet = false
                        shareCardInitialLineIndex = null
                    }
                )
            }
        }

        if (showProviderPrioritySheet) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = MaterialTheme.typography,
                shapes = MaterialTheme.shapes
            ) {
                LyricsProviderPrioritySheet(
                    onDismissRequest = { showProviderPrioritySheet = false },
                    onSearchOnlineChain = {
                        wasResetTriggered = false
                        onSearchLyrics(true)
                    },
                    onSearchSpecificProvider = { provider ->
                        val songTitle = currentSong?.title.orEmpty()
                        val artistName = currentSong?.displayArtist
                        if (songTitle.isNotBlank()) {
                            wasResetTriggered = false
                            showFetchLyricsDialog = true
                            onManualSearch("[provider:${provider.id}] $songTitle", artistName)
                        }
                    }
                )
            }
        }

       // Show Controls Button (Overlay)
       AnimatedVisibility(
            visible = immersiveMode,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Respect the navigation bar (edge-to-edge) so the button is never covered.
                .padding(bottom = paddingValues.calculateBottomPadding() + 32.dp)
        ) {
            FilledIconButton(
                onClick = { resetImmersiveTimer() },
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = accentColor,
                    contentColor = onAccentColor
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.lyrics_show_controls)
                )
            }
        }
       
       // Swipe Feedback Overlay
       if (isSwipeActive || swipeProgress.value > 0f) {
           val isNext = swipeTowardsNext
           val overlayAlignment = if (isNext) Alignment.CenterEnd else Alignment.CenterStart
           val icon = if (isNext) Icons.Rounded.SkipNext else Icons.Rounded.SkipPrevious
           
           Box(
               modifier = Modifier
                   .align(overlayAlignment)
                   .size(100.dp) // Base size
                   .padding(
                       start = if(isNext) 0.dp else 6.dp,
                       end = if(isNext) 6.dp else 0.dp
                   )
                   .graphicsLayer {
                        val widthPx = size.width
                        val initialOffset = if(isNext) widthPx else -widthPx
                        translationX = initialOffset * (1f - swipeProgress.value)

                        scaleX = 0.8f + (swipeProgress.value * 0.2f)
                        scaleY = 0.8f + (swipeProgress.value * 0.2f)
                   }
                   .background(
                        color = accentColor, // No alpha modulation
                        shape = RoundedCornerShape(
                            topStart = if(isNext) 360.dp else 8.dp,
                            bottomStart = if(isNext) 360.dp else 8.dp,
                            topEnd = if(isNext) 8.dp else 360.dp,
                            bottomEnd = if(isNext) 8.dp else 360.dp
                        )
                   ),
               contentAlignment = Alignment.Center
           ) {
               Icon(
                   imageVector = icon,
                   contentDescription = null,
                   modifier = Modifier.size(48.dp),
                   tint = onAccentColor
               )
           }
       }

      }
    }
}

@Composable
private fun LyricsPlaybackSeekBar(
    playbackPositionFlow: StateFlow<Long>,
    backgroundColor: Color,
    onBackgroundColor: Color,
    accentColor: Color,
    totalDuration: Long,
    onSeekTo: (Long) -> Unit,
    onSeekPreviewChange: (Long?) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val playbackPosition by playbackPositionFlow.collectAsStateWithLifecycle()

    PlayerSeekBar(
        backgroundColor = backgroundColor,
        onBackgroundColor = onBackgroundColor,
        primaryColor = accentColor,
        currentPosition = playbackPosition,
        totalDuration = totalDuration,
        onSeek = onSeekTo,
        onSeekPreview = onSeekPreviewChange,
        isPlaying = isPlaying,
        modifier = modifier
    )
}

@OptIn(ExperimentalSnapperApi::class)
@Composable
fun SyncedLyricsList(
    lines: List<SyncedLine>,
    listState: LazyListState,
    playbackPositionFlow: StateFlow<Long>,
    isPlaying: Boolean = true,
    lyricsSyncOffset: Int,
    positionOverrideMs: Long? = null,
    accentColor: Color,
    textStyle: TextStyle,
    onLineClick: (SyncedLine) -> Unit,
    onLineLongClick: ((Int, SyncedLine) -> Unit)? = null,
    highlightZoneFraction: Float,
    highlightOffsetDp: Dp,
    autoscrollAnimationSpec: AnimationSpec<Float>,
    useAnimatedLyrics: Boolean = false,
    animatedLyricsBlurEnabled: Boolean = true,
    animatedLyricsBlurStrength: Float = 2.5f,
    immersiveMode: Boolean = false,
    lyricsAlignment: String = "left",
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    footer: LazyListScope.() -> Unit = {}
) {
    val density = LocalDensity.current
    // 60/120fps monotonic clock interpolation + 25% proportional drift damping (Mornye port)
    val positionState = rememberInterpolatedLyricsPosition(
        playbackPositionFlow = playbackPositionFlow,
        isPlaying = isPlaying,
        lyricsSyncOffsetMs = lyricsSyncOffset,
        positionOverrideMs = positionOverrideMs
    )
    val adjustedPositionFlow = remember(playbackPositionFlow, lyricsSyncOffset, positionOverrideMs) {
        playbackPositionFlow.map { rawPos ->
            ((positionOverrideMs ?: rawPos) + lyricsSyncOffset.toLong()).coerceAtLeast(0L)
        }
    }
    val isPreviewSeeking = positionOverrideMs != null
    // Precomputed once per lyrics set: the per-frame lookup below is then a single O(n) pass
    // instead of the previous O(n²) scan on every interpolated position tick.
    val nextLineStartTimes = remember(lines) { resolveNextLineStartTimes(lines) }
    val lineEndTimes = remember(lines, nextLineStartTimes) {
        LongArray(lines.size) { i -> resolveLineEndTimeMs(lines[i], nextLineStartTimes[i]) }
    }
    val currentLineIndex by remember(lines, lineEndTimes, positionState) {
        derivedStateOf {
            resolveCurrentLineIndex(
                lines = lines,
                position = positionState.value,
                lineEndTimes = lineEndTimes
            )
        }
    }
    var hasAlignedInitialLine by remember(lines) { mutableStateOf(false) }
    var lastAutoScrolledLineIndex by remember(lines) { mutableIntStateOf(-1) }

    BoxWithConstraints(modifier = modifier) {
        val highlightOffsetPx = remember(highlightOffsetDp, density) { with(density) { highlightOffsetDp.toPx() } }

        val snapperLayoutInfo = rememberLazyListSnapperLayoutInfo(
            lazyListState = listState,
            snapOffsetForItem = { layoutInfo, item ->
                val viewportHeight = layoutInfo.endScrollOffset - layoutInfo.startScrollOffset
                highlightSnapOffsetPx(viewportHeight, item.size, highlightOffsetPx)
            }
        )
        val flingBehavior = rememberSnapperFlingBehavior(layoutInfo = snapperLayoutInfo)

        val isUserDragging by listState.interactionSource.collectIsDraggedAsState()

        LaunchedEffect(currentLineIndex, lines.size, isPreviewSeeking) {
            if (lines.isEmpty()) return@LaunchedEffect
            if (currentLineIndex !in lines.indices) return@LaunchedEffect
            // Wait for the first layout pass instead of bailing out: otherwise the initial
            // alignment is skipped and (while paused) the active line stays under the top fade.
            if (listState.layoutInfo.totalItemsCount == 0) {
                snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            }

            if (!hasAlignedInitialLine) {
                listState.scrollToItem(currentLineIndex)
                snapToSnapIndex(
                    listState = listState,
                    layoutInfo = snapperLayoutInfo,
                    targetIndex = currentLineIndex
                )
                hasAlignedInitialLine = true
                lastAutoScrolledLineIndex = currentLineIndex
                return@LaunchedEffect
            }

            if (isUserDragging && !isPreviewSeeking) return@LaunchedEffect

            val lineJumpDistance = if (lastAutoScrolledLineIndex >= 0) {
                abs(currentLineIndex - lastAutoScrolledLineIndex)
            } else {
                0
            }

            if (isPreviewSeeking) {
                if (lineJumpDistance > 2) {
                    listState.scrollToItem(currentLineIndex)
                    snapToSnapIndex(
                        listState = listState,
                        layoutInfo = snapperLayoutInfo,
                        targetIndex = currentLineIndex
                    )
                } else {
                    animateToSnapIndex(
                        listState = listState,
                        layoutInfo = snapperLayoutInfo,
                        targetIndex = currentLineIndex,
                        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing)
                    )
                }
                lastAutoScrolledLineIndex = currentLineIndex
                return@LaunchedEffect
            }

            // Critically damped spring / dynamic scroll velocity inspired by Mornye
            val dynamicAnimationSpec = if (useAnimatedLyrics) {
                spring<Float>(
                    dampingRatio = 0.88f,
                    stiffness = 220f
                )
            } else {
                autoscrollAnimationSpec
            }

            animateToSnapIndex(
                listState = listState,
                layoutInfo = snapperLayoutInfo,
                targetIndex = currentLineIndex,
                animationSpec = dynamicAnimationSpec
            )
            lastAutoScrolledLineIndex = currentLineIndex
        }

        // Re-align the active line when the viewport height changes (e.g. the controls
        // collapsing / expanding in immersive mode), so it stays in the highlight zone.
        LaunchedEffect(listState, snapperLayoutInfo) {
            snapshotFlow { listState.layoutInfo.viewportSize.height }
                .filter { it > 0 }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest {
                    if (!hasAlignedInitialLine || isUserDragging || listState.isScrollInProgress) return@collectLatest
                    val target = currentLineIndex
                    if (target !in lines.indices) return@collectLatest
                    if (listState.layoutInfo.visibleItemsInfo.none { it.index == target }) return@collectLatest
                    snapToSnapIndex(
                        listState = listState,
                        layoutInfo = snapperLayoutInfo,
                        targetIndex = target
                    )
                }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = contentPadding
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, item -> "${item.time}_$index" }
                ) { index, line ->
                    val nextTime = nextLineStartTimes.getOrElse(index) { Int.MAX_VALUE }
                    val distanceFromCurrent = if (currentLineIndex != -1) abs(currentLineIndex - index) else 100
                    
                    val parallaxModifier = if (useAnimatedLyrics) {
                        Modifier.graphicsLayer {
                            // Calculate translation dynamically inside graphicsLayer to avoid recomposing the row during scroll
                            val currentLayoutInfo = listState.layoutInfo
                            val lineItemInfo = currentLayoutInfo.visibleItemsInfo.find { it.index == index }
                            val itemCenter = lineItemInfo?.let { it.offset + (it.size / 2f) }
                            val viewportCenter = currentLayoutInfo.viewportEndOffset / 2f
                            
                            val distanceFromCenter = itemCenter?.let { it - viewportCenter } ?: 0f
                            
                            val maxTranslation = 40f 
                            val distanceRatio = (distanceFromCenter / viewportCenter).coerceIn(-1f, 1f)
                            translationY = distanceRatio * distanceRatio * distanceRatio * maxTranslation 
                        }
                    } else Modifier

                    if (line.line.isNotBlank()) {
                        LyricLineRow(
                            line = line,
                            lineIndex = index,
                            currentLineIndex = currentLineIndex,
                            nextTime = nextTime,
                            position = 0L,
                            positionState = positionState,
                            distanceFromCurrent = distanceFromCurrent,
                            useAnimatedLyrics = useAnimatedLyrics,
                            animatedLyricsBlurEnabled = animatedLyricsBlurEnabled,
                            animatedLyricsBlurStrength = animatedLyricsBlurStrength,
                            immersiveMode = immersiveMode,
                            lyricsAlignment = lyricsAlignment,
                            showTranslation = showTranslation,
                            showRomanization = showRomanization,
                            accentColor = accentColor,
                            style = textStyle,
                            modifier = parallaxModifier
                                .fillMaxWidth()
                                .testTag("synced_line_${line.time}"),
                            onClick = { onLineClick(line) },
                            onLongClick = if (onLineLongClick != null) {
                                { onLineLongClick(index, line) }
                            } else null
                        )
                    } else {
                        BubblesLine(
                            positionFlow = adjustedPositionFlow,
                            time = line.time,
                            color = LocalContentColor.current.copy(alpha = 0.6f),
                            nextTime = nextTime,
                            modifier = parallaxModifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
// 16 dp Spacer removed to allow dynamic padding in LyricLineRow
                }
                footer()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LyricLineRow(
    line: SyncedLine,
    lineIndex: Int = 0,
    currentLineIndex: Int = -1,
    nextTime: Int,
    position: Long,
    positionState: State<Long>? = null,
    distanceFromCurrent: Int = 100,
    useAnimatedLyrics: Boolean = false,
    animatedLyricsBlurEnabled: Boolean = true,
    animatedLyricsBlurStrength: Float = 2.5f,
    immersiveMode: Boolean = false,
    lyricsAlignment: String = "left",
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    accentColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val sanitizedLine = remember(line.line) { sanitizeLyricLineText(line.line) }
    val sanitizedWords = remember(line.words) {
        line.words?.let(::sanitizeSyncedWords)
    }
    val sanitizedWordClusters = remember(sanitizedWords) {
        sanitizedWords?.takeIf { it.isNotEmpty() }?.let(::clusterSyncedWords)
    }
    val lineEndTime = remember(line, nextTime) {
        resolveLineEndTimeMs(line, nextTime)
    }
    val fallbackPositionState = rememberUpdatedState(position)
    val effectivePositionState = positionState ?: fallbackPositionState
    val isCurrentLine by remember(line.time, lineEndTime, effectivePositionState) {
        derivedStateOf {
            effectivePositionState.value in line.time.toLong()..<lineEndTime
        }
    }
    val isPastLine by remember(lineEndTime, effectivePositionState) {
        derivedStateOf {
            effectivePositionState.value >= lineEndTime
        }
    }
    // Trailing cascade wave (Mornye port) for lines below the active line when currentLineIndex advances
    val cascadeWaveOffsetPx = rememberTrailingCascadeWaveOffset(
        lineIndex = lineIndex,
        currentLineIndex = currentLineIndex
    )

    // Multi-voice alignment (v2 -> right, v3 -> center) & [bg:...] backing vocal styling
    val effectiveAlignment = remember(lyricsAlignment, line.voice) {
        when (line.voice?.lowercase()) {
            "v2" -> "right"
            "v3" -> "center"
            else -> lyricsAlignment
        }
    }
    val effectiveStyle = remember(style, line.isBackground) {
        if (line.isBackground) {
            style.copy(
                fontSize = (style.fontSize.value * 0.84f).sp,
                lineHeight = (style.lineHeight.value * 0.86f).sp,
                fontStyle = FontStyle.Italic
            )
        } else {
            style
        }
    }
    val unhighlightedAlpha = if (line.isBackground) 0.34f else 0.45f
    val unhighlightedColor = LocalContentColor.current.copy(alpha = unhighlightedAlpha)
    val lineColor by animateColorAsState(
        targetValue = if (isCurrentLine) accentColor else unhighlightedColor,
        animationSpec = if (useAnimatedLyrics) spring(
            stiffness = Spring.StiffnessVeryLow,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ) else tween(durationMillis = 250),
        label = "lineColor"
    )

    // Animated mode & Immersive mode: balanced scale curve capped so scaled lines never overflow the viewport
    val targetScale = when {
        useAnimatedLyrics && immersiveMode -> when (distanceFromCurrent) {
            0 -> 1.15f
            1 -> 1.0f
            else -> 0.90f
        }
        immersiveMode -> when (distanceFromCurrent) {
            0 -> 1.12f
            1 -> 0.98f
            else -> 0.92f
        }
        useAnimatedLyrics -> when (distanceFromCurrent) {
            0 -> 1.08f
            1 -> 0.96f
            else -> 0.88f
        }
        else -> 1f
    }
    val verticalPadding = if (line.isBackground) 4.dp else 12.dp
    val targetAlpha = if (useAnimatedLyrics) when (distanceFromCurrent) {
        0 -> 1.0f; 1 -> 0.65f; else -> 0.34f
    } else 1f

    val scaleState = animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (useAnimatedLyrics) spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ) else tween(durationMillis = 200),
        label = "lineScale"
    )
    val alphaState = animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = if (useAnimatedLyrics) spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ) else tween(durationMillis = 200),
        label = "lineAlpha"
    )

    // Blur Effect: only softly blur distant lines (>1 away) with Unbounded edge treatment so glyphs are never clipped
    val targetBlur = if (useAnimatedLyrics && animatedLyricsBlurEnabled && distanceFromCurrent > 1) {
        ((distanceFromCurrent - 1) * animatedLyricsBlurStrength).coerceAtMost(4f).dp
    } else 0.dp

    // Consistent horizontal reserve space on the side(s) opposite the scale pivot so:
    // 1. Scaled text (up to 1.15x) never overflows the screen width or clips characters in either mode.
    // 2. Toggling immersiveMode does not alter text layout width or trigger expensive mid-zoom text reflow.
    val baseModifier = when (effectiveAlignment) {
        "center" -> modifier.padding(horizontal = 24.dp)
        "right" -> modifier.padding(start = 42.dp)
        else -> modifier.padding(end = 42.dp)
    }
    val animatedModifier = baseModifier
        .graphicsLayer {
            val currentScale = scaleState.value
            scaleX = currentScale
            scaleY = currentScale
            if (useAnimatedLyrics) {
                this.alpha = alphaState.value
            }
            this.translationY += cascadeWaveOffsetPx
            transformOrigin = TransformOrigin(
                pivotFractionX = when (effectiveAlignment) {
                    "center" -> 0.5f
                    "right" -> 1f
                    else -> 0f
                },
                pivotFractionY = 0.5f
            )
        }
        .then(
            if (useAnimatedLyrics && targetBlur > 0.dp) {
                Modifier.blur(targetBlur, BlurredEdgeTreatment.Unbounded)
            } else {
                Modifier
            }
        )

    // Roman or Translate Logic
    val translationText = line.translation
    val romanizationText = line.romanization

    val secondaryStyle = remember(effectiveStyle) {
        effectiveStyle.copy(
            fontSize = (effectiveStyle.fontSize.value * 0.75f).sp,
            fontWeight = FontWeight.Normal
        )
    }

    val romanizationColor = lineColor.copy(alpha = lineColor.alpha * 0.85f)
    val translationColor = lineColor.copy(alpha = lineColor.alpha * 0.55f)

    val horizontalAlignment = when (effectiveAlignment) {
        "center" -> Alignment.CenterHorizontally
        "right" -> Alignment.End
        else -> Alignment.Start
    }

    val textAlign = when (effectiveAlignment) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        else -> TextAlign.Left
    }

    val boxAlignment = when (effectiveAlignment) {
        "center" -> Alignment.TopCenter
        "right" -> Alignment.TopEnd
        else -> Alignment.TopStart
    }

    if (sanitizedWordClusters.isNullOrEmpty()) {
        Column(
            modifier = animatedModifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(vertical = verticalPadding, horizontal = 4.dp),
            horizontalAlignment = horizontalAlignment
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = boxAlignment) {
                Text(
                    text = sanitizedLine,
                    style = effectiveStyle,
                    color = lineColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = textAlign
                )
            }

            if (showRomanization && !romanizationText.isNullOrBlank()) {
                Text(
                    text = romanizationText,
                    style = secondaryStyle,
                    color = romanizationColor,
                    textAlign = textAlign,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (showTranslation && !translationText.isNullOrBlank()) {
                Text(
                    text = translationText,
                    style = secondaryStyle,
                    color = translationColor,
                    textAlign = textAlign,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    } else {
        val allWords = requireNotNull(sanitizedWords)
        // Only subscribe to per-frame position when this word-synced line is actively being sung
        val activeWordPositionMs = if (isCurrentLine) {
            effectivePositionState.value
        } else if (isPastLine) {
            lineEndTime
        } else {
            0L
        }

        Column(
            modifier = animatedModifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(vertical = verticalPadding, horizontal = 4.dp),
            horizontalAlignment = horizontalAlignment
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = when (effectiveAlignment) {
                    "center" -> Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                    "right" -> Arrangement.spacedBy(4.dp, Alignment.End)
                    else -> Arrangement.spacedBy(4.dp, Alignment.Start)
                },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sanitizedWordClusters.forEach { cluster ->
                    Row(
                        modifier = Modifier.wrapContentSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        cluster.words.forEachIndexed { clusterOffset, word ->
                            val wordIndex = cluster.startIndex + clusterOffset
                            val nextWordStartMs = allWords.getOrNull(wordIndex + 1)?.time
                            key("${line.time}_${word.time}_${word.word}_$wordIndex") {
                                MornyeSweepingWordSpan(
                                    wordText = word.word,
                                    startMs = word.time,
                                    endMs = word.endTime ?: nextWordStartMs ?: lineEndTime.toInt(),
                                    nextWordStartMs = nextWordStartMs,
                                    lineEndMs = lineEndTime.toInt(),
                                    currentPositionMs = activeWordPositionMs,
                                    isCurrentLine = isCurrentLine,
                                    isPastLine = isPastLine,
                                    activeColor = accentColor,
                                    inactiveAlpha = unhighlightedAlpha,
                                    fontSize = effectiveStyle.fontSize,
                                    fontWeight = FontWeight.Bold,
                                    style = effectiveStyle,
                                    onClick = onClick
                                )
                            }
                        }
                    }
                }
            }

            if (showRomanization && !romanizationText.isNullOrBlank()) {
                Text(
                    text = romanizationText,
                    style = secondaryStyle,
                    color = romanizationColor,
                    textAlign = textAlign,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (showTranslation && !translationText.isNullOrBlank()) {
                Text(
                    text = translationText,
                    style = secondaryStyle,
                    color = translationColor,
                    textAlign = textAlign,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlainLyricsLine(
    line: String,
    style: TextStyle,
    lyricsAlignment: String = "left",
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val parts = remember(line) { splitPlainLyricLine(line) }
    val primaryText = parts.primary
    val romanizationText = parts.romanization
    val translationText = parts.translation
    val textAlign = when (lyricsAlignment) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        else -> TextAlign.Left
    }

    val horizontalAlignment = when (lyricsAlignment) {
        "center" -> Alignment.CenterHorizontally
        "right" -> Alignment.End
        else -> Alignment.Start
    }

    val translationStyle = remember(style) {
        style.copy(
            fontSize = (style.fontSize.value * 0.75f).sp,
            fontWeight = FontWeight.Normal
        )
    }
    val translationColor = LocalContentColor.current.copy(alpha = 0.45f)

    Column(
        modifier = if (onLongClick != null) {
            modifier
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                    // Plain lines have no tap action; avoid a ripple that suggests one.
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = onLongClick
                )
        } else {
            modifier
        },
        horizontalAlignment = horizontalAlignment
    ) {
        if (primaryText.isNotBlank()) {
            Text(text = primaryText, style = style, color = LocalContentColor.current.copy(alpha = 0.7f), textAlign = textAlign)

            if (showRomanization && romanizationText.isNotBlank()) {
                Text(
                    text = romanizationText,
                    style = translationStyle,
                    color = translationColor,
                    textAlign = textAlign,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (showTranslation && translationText.isNotBlank()) {
                Text(
                    text = translationText,
                    style = translationStyle,
                    color = translationColor,
                    textAlign = textAlign,
                    modifier = Modifier.padding(top = if (showRomanization && romanizationText.isNotBlank()) 2.dp else 4.dp)
                )
            }
        }
    }
}

internal data class PlainLyricLineParts(
    val primary: String,
    val romanization: String,
    val translation: String
)

/**
 * Splits a plain lyrics entry ("original\n[romanization\n]translation…") into its parts.
 * The first extra line is treated as romanization only when the original uses a script that
 * needs romanizing AND the extra line actually looks like a romanization (Latin letters only,
 * no Vietnamese letters). Previously any line containing a space counted as "Latin",
 * so e.g. Vietnamese translations of Japanese/Korean/Chinese songs were shown as romanization.
 */
internal fun splitPlainLyricLine(line: String): PlainLyricLineParts {
    val rawLines = line.split("\n")
    val primary = sanitizeLyricLineText(rawLines.firstOrNull().orEmpty())
    if (rawLines.size <= 1) return PlainLyricLineParts(primary, "", "")

    val extras = rawLines.drop(1).map(::sanitizeLyricLineText).filter { it.isNotBlank() }
    if (extras.isEmpty()) return PlainLyricLineParts(primary, "", "")

    val firstIsRomanization = MultiLangRomanizer.isScriptThatNeedsRomanization(primary) &&
        looksLikeRomanization(extras.first())

    return if (firstIsRomanization) {
        PlainLyricLineParts(primary, extras.first(), extras.drop(1).joinToString("\n"))
    } else {
        PlainLyricLineParts(primary, "", extras.joinToString("\n"))
    }
}

internal fun looksLikeRomanization(text: String): Boolean {
    val letters = text.filter { it.isLetter() }
    if (letters.isEmpty()) return false
    if (letters.any { Character.UnicodeScript.of(it.code) != Character.UnicodeScript.LATIN }) return false
    // Decomposed diacritics or Vietnamese letters mean this is a (Vietnamese) translation.
    if (text.any { it in '\u0300'..'\u036F' }) return false
    return letters.none { it in VietnameseLetters || it in '\u1EA0'..'\u1EF9' }
}

/**
 * Letters common in Vietnamese but practically absent from the romanizations we show
 * (Hepburn uses macrons, Pinyin tone marks, Revised Romanization none). Circumflex vowels
 * are included because Vietnamese uses them constantly ("yêu", "tôi", "cần").
 */
private const val VietnameseLetters = "đĐăĂơƠưƯâÂêÊôÔãÃõÕýÝ"

private val LeadingTagRegex = Regex("^v\\d+:\\s*", RegexOption.IGNORE_CASE)

internal fun sanitizeLyricLineText(raw: String): String =
    LyricsUtils.stripLrcTimestamps(raw).replace(LeadingTagRegex, "").trimStart()

internal fun sanitizeSyncedWords(words: List<SyncedWord>): List<SyncedWord> =
    buildList {
        words.forEachIndexed { index, word ->
            val sanitized = if (index == 0) LeadingTagRegex.replace(word.word, "") else word.word
            val normalized = sanitized.trim()
            if (normalized.isEmpty()) return@forEachIndexed

            add(
                word.copy(
                    word = normalized,
                    startsNewWord = if (isEmpty()) true else word.startsNewWord
                )
            )
        }
    }

internal data class SyncedWordCluster(
    val startIndex: Int,
    val words: List<SyncedWord>
)

internal fun clusterSyncedWords(words: List<SyncedWord>): List<SyncedWordCluster> {
    if (words.isEmpty()) return emptyList()

    val clusters = mutableListOf<SyncedWordCluster>()
    var currentWords = mutableListOf<SyncedWord>()
    var currentStartIndex = 0

    words.forEachIndexed { index, word ->
        if (word.startsNewWord && currentWords.isNotEmpty()) {
            clusters += SyncedWordCluster(startIndex = currentStartIndex, words = currentWords.toList())
            currentWords = mutableListOf()
            currentStartIndex = index
        } else if (currentWords.isEmpty()) {
            currentStartIndex = index
        }

        currentWords += word
    }

    if (currentWords.isNotEmpty()) {
        clusters += SyncedWordCluster(startIndex = currentStartIndex, words = currentWords.toList())
    }

    return clusters
}

internal fun normalizeWordEndTime(
    currentWordTimeMs: Long,
    nextWordTimeMs: Long,
    lineEndTimeMs: Long
): Long {
    val minEnd = currentWordTimeMs + 1L
    val boundedLineEnd = lineEndTimeMs.coerceAtLeast(minEnd)
    return nextWordTimeMs.coerceIn(minEnd, boundedLineEnd)
}

internal fun resolveLineEndTimeMs(line: SyncedLine, nextLineStartMs: Int): Long {
    val explicitEnd = line.endTime?.toLong()?.takeIf { it > line.time }
    val nextStart = nextLineStartMs.toLong()
    val baseEnd = if (explicitEnd != null) {
        if (nextStart > explicitEnd + 1_200L) {
            explicitEnd
        } else {
            maxOf(explicitEnd, nextStart)
        }
    } else {
        nextStart
    }
    val lastWordEnd = line.words?.maxOfOrNull { (it.endTime ?: it.time).toLong() } ?: line.time.toLong()
    return maxOf(baseEnd, lastWordEnd + 1L)
}

internal fun resolveHighlightedWordIndex(
    words: List<SyncedWord>,
    positionMs: Long,
    lineStartTimeMs: Long,
    lineEndTimeMs: Long
): Int {
    if (positionMs < lineStartTimeMs || positionMs >= lineEndTimeMs) return -1
    return words.indexOfLast { it.time.toLong() <= positionMs }
}

internal fun resolveSeekPositionMs(
    lineTimeMs: Long,
    lyricsSyncOffsetMs: Int
): Long = (lineTimeMs - lyricsSyncOffsetMs.toLong()).coerceAtLeast(0L)

internal data class HighlightZoneMetrics(
    val topPadding: Dp,
    val bottomPadding: Dp,
    val zoneHeight: Dp,
    val centerFromTop: Dp
)

internal fun calculateHighlightMetrics(
    containerHeight: Dp,
    highlightZoneFraction: Float,
    highlightOffset: Dp
): HighlightZoneMetrics {
    val container = containerHeight.value
    val zoneHeight = (containerHeight * highlightZoneFraction).value.coerceAtLeast(0f)
    val offset = highlightOffset.value
    val minCenter = zoneHeight / 2f
    val maxCenter = (container - zoneHeight / 2f).coerceAtLeast(minCenter)
    val unclampedCenter = container / 2f - offset
    val center = unclampedCenter.coerceIn(minCenter, maxCenter)
    val topPadding = (center - zoneHeight / 2f).coerceAtLeast(0f)
    val bottomPadding = (container - center - zoneHeight / 2f).coerceAtLeast(0f)

    return HighlightZoneMetrics(
        topPadding = topPadding.dp,
        bottomPadding = bottomPadding.dp,
        zoneHeight = zoneHeight.dp,
        centerFromTop = center.dp
    )
}

internal fun highlightSnapOffsetPx(
    viewportHeight: Int,
    itemSize: Int,
    highlightOffsetPx: Float
): Int {
    if (viewportHeight <= 0 || itemSize <= 0) return 0
    if (itemSize >= viewportHeight) return 0
    val viewport = viewportHeight.toFloat()
    val halfItem = itemSize / 2f
    val targetCenter = (viewport / 2f) - highlightOffsetPx
    val clampedCenter = targetCenter.coerceIn(halfItem, viewport - halfItem)
    return (clampedCenter - halfItem).roundToInt()
}

internal suspend fun animateToSnapIndex(
    listState: LazyListState,
    layoutInfo: SnapperLayoutInfo,
    targetIndex: Int,
    animationSpec: AnimationSpec<Float>
) {
    val distance = layoutInfo.distanceToIndexSnap(targetIndex)
    if (distance == 0) return

    listState.scroll {
        var previous = 0f
        AnimationState(initialValue = 0f).animateTo(
            targetValue = distance.toFloat(),
            animationSpec = animationSpec
        ) {
            val delta = value - previous
            val consumed = scrollBy(delta)
            previous = value
            if (abs(delta) > 1f && abs(delta - consumed) > 0.5f) cancelAnimation()
        }
    }
}

internal suspend fun snapToSnapIndex(
    listState: LazyListState,
    layoutInfo: SnapperLayoutInfo,
    targetIndex: Int
) {
    val distance = layoutInfo.distanceToIndexSnap(targetIndex)
    if (distance == 0) return

    listState.scroll {
        scrollBy(distance.toFloat())
    }
}

/**
 * Start time of the line that ends each line: for a lead line that's the next lead (non-background)
 * line, falling back to the immediately following line; for a background line it's simply the
 * following line. Computed in a single backwards pass (O(n)).
 */
internal fun resolveNextLineStartTimes(lines: List<SyncedLine>): IntArray {
    val result = IntArray(lines.size)
    var nextLeadStart: Int? = null
    for (index in lines.indices.reversed()) {
        val line = lines[index]
        val immediateNext = lines.getOrNull(index + 1)?.time
        result[index] = if (line.isBackground) {
            immediateNext ?: Int.MAX_VALUE
        } else {
            nextLeadStart ?: immediateNext ?: Int.MAX_VALUE
        }
        if (!line.isBackground) nextLeadStart = line.time
    }
    return result
}

internal fun resolveLineEndTimes(lines: List<SyncedLine>): LongArray {
    val nextStarts = resolveNextLineStartTimes(lines)
    return LongArray(lines.size) { i -> resolveLineEndTimeMs(lines[i], nextStarts[i]) }
}

internal fun resolveCurrentLineIndex(
    lines: List<SyncedLine>,
    position: Long,
    lineEndTimes: LongArray = resolveLineEndTimes(lines)
): Int {
    if (lines.isEmpty()) return -1

    var lastActive = -1
    var lastActiveLead = -1
    for (index in lines.indices) {
        val line = lines[index]
        if (position >= line.time.toLong() && position < lineEndTimes[index]) {
            lastActive = index
            if (!line.isBackground) lastActiveLead = index
        }
    }
    return if (lastActiveLead >= 0) lastActiveLead else lastActive
}

@Composable
private fun LyricsTrackInfo(
    song: Song?,
    modifier: Modifier = Modifier,
    contentColor: Color,
    isPlaying: Boolean
) {
    if (song == null) return

    val albumShape = CircleShape

    // Helper state to stop rotation when paused, but we want it to pause in place?
    // Using infiniteTransition.animateFloat will reset on recomposition if spec changes or stops.
    // For a realistic vinyl pause, we need a manual Animatable that loops.
    // But for simplicity requested: "Animate the cover art to rotate... when music is playing".
    // If we just use conditional Modifier.graphicsLayer rotation, it might jump.
    // Let's use a simpler approach: if isPlaying, rotate.
    
    // Better approach for pausing rotation in place is non-trivial without a dedicated running time state.
    // Given the constraints, I will use a simple AnimatedVisibility or just let it reset, OR
    // use a monotonic clock if possible.
    // Let's stick to infinite transition for running, and maybe 0f for static?
    // Actually, user said "simulate a vinyl record". This implies continuous storage of rotation?
    // I'll try to implement continuous rotation.
    
    val currentRotation = remember { Animatable(0f) }
    
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            // Spin forever. 8s per revolution halves the effective per-second animation work
            // vs the original 4s cadence — visually still clearly a rotating "vinyl", but
            // drives fewer Compose invalidations during long listening sessions.
            while (true) {
                currentRotation.animateTo(
                    targetValue = currentRotation.value + 360f,
                    animationSpec = tween(8000, easing = LinearEasing)
                )
            }
        } else {
             currentRotation.stop()
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SmartImage(
            model = song.albumArtUriString ?: R.drawable.rounded_album_24,
            shape = albumShape,
            contentDescription = stringResource(R.string.common_album_art),
            modifier = Modifier
                .size(66.dp)
                .padding(6.dp)
                .graphicsLayer {
                    rotationZ = currentRotation.value % 360f
                }
                .clip(albumShape),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .weight(1f, fill = false) // Allow shrinking if content is small
                .padding(vertical = 6.dp)
                .padding(end = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    //textGeometricTransform = TextGeometricTransform(scaleX = (0.9f)),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.displayArtist,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = contentColor.copy(alpha = 0.7f),
                    //textGeometricTransform = TextGeometricTransform(scaleX = (0.9f)),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        PlayingEqIcon(
            modifier = Modifier
                .padding(start = 8.dp, end = 18.dp)
                .size(width = 18.dp, height = 16.dp),
            color = contentColor,
            isPlaying = isPlaying
        )
    }
}
