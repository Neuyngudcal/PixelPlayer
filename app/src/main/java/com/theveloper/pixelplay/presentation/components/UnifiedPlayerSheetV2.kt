package com.theveloper.pixelplay.presentation.components

import android.widget.Toast
import com.theveloper.pixelplay.presentation.components.ExpressiveOfflineDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import com.theveloper.pixelplay.presentation.components.player.TabletPlayerDisplayMode
import com.theveloper.pixelplay.presentation.components.player.resolveNowPlayingSheetBoundsSpec
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayWindowInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.theveloper.pixelplay.data.diagnostics.AdvancedPerformanceDiagnostics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.sanitizeNavBarCornerRadius
import com.theveloper.pixelplay.presentation.components.scoped.PlayerAlbumNavigationEffect
import com.theveloper.pixelplay.presentation.components.scoped.PlayerArtistNavigationEffect
import com.theveloper.pixelplay.presentation.components.scoped.PlayerSheetPredictiveBackHandler
import com.theveloper.pixelplay.presentation.components.scoped.QueueSheetRuntimeEffects
import com.theveloper.pixelplay.presentation.components.scoped.SheetMotionController
import com.theveloper.pixelplay.presentation.components.scoped.miniPlayerDismissHorizontalGesture
import com.theveloper.pixelplay.presentation.components.scoped.playerSheetVerticalDragGesture
import com.theveloper.pixelplay.presentation.components.scoped.rememberFullPlayerCompositionPolicy
import com.theveloper.pixelplay.presentation.components.scoped.rememberCastSheetState
import com.theveloper.pixelplay.presentation.components.scoped.rememberFullPlayerVisualState
import com.theveloper.pixelplay.presentation.components.scoped.rememberMiniPlayerDismissGestureHandler
import com.theveloper.pixelplay.presentation.components.scoped.rememberPrewarmFullPlayer
import com.theveloper.pixelplay.presentation.components.scoped.rememberQueueSheetState
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetActionHandlers
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetBackAndDragState
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetInteractionState
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetModalOverlayController
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetOverlayState
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetThemeState
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetVisualState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private data class PlayerUiSheetSliceV2(
    val currentQueueSourceName: String = "",
    val preparingSongId: String? = null
)

/**
 * V2 real host: no longer delegates to the legacy `UnifiedPlayerSheet`.
 *
 * This path keeps behavior parity, but now owns its own runtime wiring so we can
 * profile and optimize V2 independently while preserving the Experimental switch.
 */
@androidx.annotation.OptIn(UnstableApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UnifiedPlayerSheetV2(
    playerViewModel: PlayerViewModel,
    sheetCollapsedTargetY: Float,
    containerHeight: Dp,
    collapsedStateHorizontalPadding: Dp = 12.dp,
    collapsedStartHorizontalPadding: Dp = collapsedStateHorizontalPadding,
    collapsedEndHorizontalPadding: Dp = collapsedStateHorizontalPadding,
    containerWidth: Dp = Dp.Unspecified,
    navController: NavHostController,
    hideMiniPlayer: Boolean = false,
    isNavBarHidden: Boolean = false,
    tabletDisplayMode: TabletPlayerDisplayMode = TabletPlayerDisplayMode.SIDE_DOCKED,
    onTabletDisplayModeChange: (TabletPlayerDisplayMode) -> Unit = {},
    fullScreenFractionProvider: () -> Float = { 1f }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestContext by rememberUpdatedState(context)
    var showNoInternetDialog by remember { mutableStateOf(false) }

    // MediaStore write-permission launcher (for metadata editing without MANAGE_EXTERNAL_STORAGE)
    val writePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        playerViewModel.onWritePermissionResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    // MediaStore delete-permission launcher (system delete confirmation dialog)
    val deletePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        playerViewModel.onDeletePermissionResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    LaunchedEffect(playerViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                playerViewModel.toastEvents.collect { message ->
                    Toast.makeText(latestContext, message, Toast.LENGTH_SHORT).show()
                }
            }
            launch {
                playerViewModel.showNoInternetDialog.collect {
                    showNoInternetDialog = true
                }
            }
            launch {
                playerViewModel.writePermissionRequest.collect { intentSender ->
                    writePermissionLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                    )
                }
            }
            launch {
                playerViewModel.deletePermissionRequest.collect { intentSender ->
                    deletePermissionLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                    )
                }
            }
        }
    }

    if (showNoInternetDialog) {
        ExpressiveOfflineDialog(
            onDismiss = { showNoInternetDialog = false },
            onRetry = {
                 playerViewModel.refreshLocalConnectionInfo()
                 showNoInternetDialog = false
            }
        )
    }

    val infrequentPlayerStateReference = playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val infrequentPlayerState = infrequentPlayerStateReference.value
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    val currentPositionState = playerViewModel.currentPlaybackPosition.collectAsStateWithLifecycle()
    val remotePositionState = playerViewModel.remotePosition.collectAsStateWithLifecycle()
    val isRemotePlaybackActive by playerViewModel.isRemotePlaybackActive.collectAsStateWithLifecycle()
    val positionToDisplayProvider = remember(isRemotePlaybackActive) {
        {
            if (isRemotePlaybackActive) remotePositionState.value
            else currentPositionState.value
        }
    }

    val isFavorite by playerViewModel.isCurrentSongFavorite.collectAsStateWithLifecycle()

    val playerUiSheetSlice by remember {
        playerViewModel.playerUiState
            .map { state ->
                PlayerUiSheetSliceV2(
                    currentQueueSourceName = state.currentQueueSourceName,
                    preparingSongId = state.preparingSongId
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = PlayerUiSheetSliceV2())
    val currentQueueSourceName = playerUiSheetSlice.currentQueueSourceName
    val preparingSongId = playerUiSheetSlice.preparingSongId

    val currentSheetContentState by playerViewModel.sheetState.collectAsStateWithLifecycle()
    val predictiveBackCollapseProgressState =
        playerViewModel.predictiveBackCollapseFraction.collectAsStateWithLifecycle()
    val predictiveBackCollapseProgressProvider = remember {
        { predictiveBackCollapseProgressState.value }
    }
    val predictiveBackSwipeEdge by playerViewModel.predictiveBackSwipeEdge.collectAsStateWithLifecycle()
    val prewarmFullPlayer = rememberPrewarmFullPlayer(
        currentSongId = infrequentPlayerState.currentSong?.id,
        isExpanded = currentSheetContentState == PlayerSheetState.EXPANDED
    )

    val playerConfig by playerViewModel.playerConfigSlice.collectAsStateWithLifecycle()
    val navBarCornerRadius = sanitizeNavBarCornerRadius(playerConfig.navBarCornerRadius)
    val navBarStyle = playerConfig.navBarStyle
    val carouselStyle = playerConfig.carouselStyle
    val fullPlayerLoadingTweaks = playerConfig.fullPlayerLoadingTweaks
    val tapBackgroundClosesPlayer = playerConfig.tapBackgroundClosesPlayer
    val useSmoothCorners = playerConfig.useSmoothCorners
    val playerThemePreference = playerConfig.playerThemePreference

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val offsetAnimatable = remember { Animatable(0f) }
    val screenWidthPx = remember(containerWidth, configuration, density) {
        with(density) {
            if (containerWidth != Dp.Unspecified) containerWidth.toPx()
            else configuration.screenWidthDp.dp.toPx()
        }
    }
    val dismissThresholdPx = remember(screenWidthPx) { screenWidthPx * 0.4f }
    val swipeDismissProgressState = remember(dismissThresholdPx) {
        derivedStateOf {
            if (dismissThresholdPx == 0f) 0f
            else (abs(offsetAnimatable.value) / dismissThresholdPx).coerceIn(0f, 1f)
        }
    }
    val swipeDismissProgressProvider = remember(swipeDismissProgressState) {
        { swipeDismissProgressState.value }
    }

    val screenHeightPx = remember(configuration, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }
    val miniPlayerContentHeightPx = remember { with(density) { MiniPlayerHeight.toPx() } }

    val isCastConnecting by playerViewModel.isCastConnecting.collectAsStateWithLifecycle()
    val showPlayerContentArea by remember(infrequentPlayerState.currentSong, isCastConnecting) {
        derivedStateOf { infrequentPlayerState.currentSong != null || isCastConnecting }
    }

    val playerContentExpansionFraction = playerViewModel.playerContentExpansionFraction
    val isPlayerFullyExpanded by remember(playerContentExpansionFraction, currentSheetContentState) {
        derivedStateOf {
            playerContentExpansionFraction.value >= 0.85f ||
                currentSheetContentState == PlayerSheetState.EXPANDED
        }
    }
    val visualOvershootScaleY = remember { Animatable(1f) }
    val initialFullPlayerOffsetY = remember(density) { with(density) { 24.dp.toPx() } }
    val motionScheme = remember { MotionScheme.expressive() }
    val sheetAnimationSpec = remember(motionScheme) { motionScheme.slowSpatialSpec<Float>() }
    val sheetAnimationMutex = remember { MutatorMutex() }
    val sheetExpandedTargetY = 0f
    val initialY =
        if (currentSheetContentState == PlayerSheetState.COLLAPSED) sheetCollapsedTargetY
        else sheetExpandedTargetY
    val currentSheetTranslationY = remember { Animatable(initialY) }
    val sheetMotionController = remember(
        currentSheetTranslationY,
        playerContentExpansionFraction,
        sheetAnimationMutex,
        sheetAnimationSpec
    ) {
        SheetMotionController(
            translationY = currentSheetTranslationY,
            expansionFraction = playerContentExpansionFraction,
            mutex = sheetAnimationMutex,
            defaultAnimationSpec = sheetAnimationSpec,
            expandedY = sheetExpandedTargetY
        )
    }

    // FullPlayerVisualState now holds lazy getters that read from the Animatable
    // inside graphicsLayer (draw-phase), avoiding per-frame recomposition.
    val fullPlayerVisualState = rememberFullPlayerVisualState(
        expansionFraction = playerContentExpansionFraction,
        initialOffsetY = initialFullPlayerOffsetY
    )
    val fullPlayerCompositionPolicy = rememberFullPlayerCompositionPolicy(
        currentSongId = infrequentPlayerState.currentSong?.id,
        currentSheetState = currentSheetContentState,
        expansionFraction = playerContentExpansionFraction
    )
    val shouldRenderFullPlayer = fullPlayerCompositionPolicy.shouldRenderFullPlayer

    suspend fun animatePlayerSheet(
        targetExpanded: Boolean,
        animationSpec: androidx.compose.animation.core.AnimationSpec<Float> = sheetAnimationSpec,
        initialVelocity: Float = 0f
    ) {
        sheetMotionController.animateTo(
            targetExpanded = targetExpanded,
            canExpand = showPlayerContentArea,
            collapsedY = sheetCollapsedTargetY,
            animationSpec = animationSpec,
            initialVelocity = initialVelocity
        )
    }

    LaunchedEffect(sheetCollapsedTargetY, sheetMotionController) {
        // Keep the mini player anchored to the latest collapsed target whenever
        // the navbar height/visibility changes under it.
        sheetMotionController.syncToExpansion(sheetCollapsedTargetY)
    }

    var previousSheetState by remember { mutableStateOf(currentSheetContentState) }
    LaunchedEffect(showPlayerContentArea, currentSheetContentState) {
        val targetExpanded = showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED
        val shouldBounceCollapse =
            showPlayerContentArea &&
                previousSheetState == PlayerSheetState.EXPANDED &&
                currentSheetContentState == PlayerSheetState.COLLAPSED
        if (previousSheetState != currentSheetContentState) {
            val fromState = previousSheetState
            val toState = currentSheetContentState
            AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                type = AdvancedPerformanceDiagnostics.EventTypes.UI,
                name = "player_sheet_state_changed"
            ) {
                mapOf(
                    "from" to fromState.name,
                    "to" to toState.name,
                    "showPlayerContentArea" to showPlayerContentArea.toString()
                )
            }
        }
        previousSheetState = currentSheetContentState
        scope.launch {
            animatePlayerSheet(targetExpanded = targetExpanded)
        }
 
        if (showPlayerContentArea) {
            scope.launch {
                if (targetExpanded) {
                    visualOvershootScaleY.snapTo(1f)
                    visualOvershootScaleY.animateTo(
                        targetValue = 1f,
                        animationSpec = keyframes {
                            durationMillis = 300
                            1.0f at 0
                            1.02f at 100
                            1.0f at 300
                        }
                    )
                } else if (shouldBounceCollapse && !visualOvershootScaleY.isRunning) {
                    visualOvershootScaleY.snapTo(0.97f)
                    visualOvershootScaleY.animateTo(
                        targetValue = 1f,
                        animationSpec = motionScheme.fastSpatialSpec()
                    )
                } else {
                    visualOvershootScaleY.snapTo(1f)
                }
            }
        } else {
            scope.launch { visualOvershootScaleY.snapTo(1f) }
        }
    }

    val windowInfo = LocalPixelPlayWindowInfo.current
    val sheetBoundsSpec = remember(windowInfo) { resolveNowPlayingSheetBoundsSpec(windowInfo) }
    val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val sideDockedTopMargin = remember(statusBarTopInset, sheetBoundsSpec.sideDockedVerticalMargin) {
        (statusBarTopInset + sheetBoundsSpec.sideDockedVerticalMargin).coerceAtLeast(12.dp)
    }
    val sideDockedBottomMargin = remember(navBarBottomInset, sheetBoundsSpec.sideDockedVerticalMargin) {
        (navBarBottomInset + sheetBoundsSpec.sideDockedVerticalMargin).coerceAtLeast(12.dp)
    }
    val resolvedContainerWidth = if (containerWidth != Dp.Unspecified) {
        containerWidth
    } else {
        configuration.screenWidthDp.dp
    }

    val sheetVisualState = rememberSheetVisualState(
        showPlayerContentArea = showPlayerContentArea,
        collapsedStateHorizontalPadding = collapsedStateHorizontalPadding,
        collapsedStartHorizontalPadding = collapsedStartHorizontalPadding,
        collapsedEndHorizontalPadding = collapsedEndHorizontalPadding,
        predictiveBackCollapseProgressProvider = predictiveBackCollapseProgressProvider,
        predictiveBackSwipeEdge = predictiveBackSwipeEdge,
        currentSheetContentState = currentSheetContentState,
        playerContentExpansionFraction = playerContentExpansionFraction,
        containerHeight = containerHeight,
        currentSheetTranslationY = currentSheetTranslationY,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        navBarStyle = navBarStyle,
        navBarCornerRadiusDp = navBarCornerRadius.dp,
        isNavBarHidden = isNavBarHidden,
        isPlaying = infrequentPlayerState.isPlaying,
        hasCurrentSong = infrequentPlayerState.currentSong != null,
        swipeDismissProgressProvider = swipeDismissProgressProvider,
        containerWidth = resolvedContainerWidth,
        supportsSideDockedMode = sheetBoundsSpec.supportsSideDockedMode,
        sideDockedWidth = sheetBoundsSpec.sideDockedWidth,
        sideDockedEndMargin = sheetBoundsSpec.sideDockedEndMargin,
        sideDockedTopMargin = sideDockedTopMargin,
        sideDockedBottomMargin = sideDockedBottomMargin,
        sideDockedCornerRadius = sheetBoundsSpec.sideDockedCornerRadius,
        fullScreenFractionProvider = fullScreenFractionProvider
    )
    val currentBottomPadding = sheetVisualState.currentBottomPadding
    val baseBottomPadding = sheetVisualState.baseBottomPadding
    val playerContentAreaHeightPxProvider = sheetVisualState.playerContentAreaHeightPxProvider
    val visualSheetTranslationYProvider = sheetVisualState.visualSheetTranslationYProvider
    val overallSheetTopCornerRadiusProvider = sheetVisualState.overallSheetTopCornerRadiusProvider
    val playerContentActualBottomRadiusProvider = sheetVisualState.playerContentActualBottomRadiusProvider
    val currentHorizontalPaddingStartPxProvider = sheetVisualState.currentHorizontalPaddingStartPxProvider
    val currentHorizontalPaddingEndPxProvider = sheetVisualState.currentHorizontalPaddingEndPxProvider

    val queueSheetState = rememberQueueSheetState(
        scope = scope,
        screenHeightPx = screenHeightPx,
        density = density,
        currentBottomPadding = currentBottomPadding,
        showPlayerContentArea = showPlayerContentArea,
        isPlayerFullyExpanded = isPlayerFullyExpanded
    )
    val showQueueSheet = queueSheetState.showQueueSheet
    val allowQueueSheetInteraction = queueSheetState.allowQueueSheetInteraction
    val queueSheetOffset = queueSheetState.queueSheetOffset
    val queueSheetHeightPx = queueSheetState.queueSheetHeightPx
    val queueHiddenOffsetPx = queueSheetState.queueHiddenOffsetPx
    val queueSheetController = queueSheetState.queueSheetController
    val onQueueSheetHeightPxChange = queueSheetState.onQueueSheetHeightPxChange

    val castSheetState = rememberCastSheetState()
    val sheetBackAndDragState = rememberSheetBackAndDragState(
        showPlayerContentArea = showPlayerContentArea,
        currentSheetContentState = currentSheetContentState
    )
    val velocityTracker = remember { VelocityTracker() }
    val sheetModalOverlayController = rememberSheetModalOverlayController(
        scope = scope,
        queueSheetController = queueSheetController,
        animationDurationMs = ANIMATION_DURATION_MS,
        onCollapsePlayerSheet = { playerViewModel.collapsePlayerSheet() }
    )
    val pendingSaveQueueOverlay = sheetModalOverlayController.pendingSaveQueueOverlay
    val selectedSongForInfo = sheetModalOverlayController.selectedSongForInfo
    var isFullPlayerInternalOverlayOpen by remember { mutableStateOf(false) }
    val hasAnyCardOverlayOpen =
        showQueueSheet ||
            queueSheetState.isCollapsing ||
            castSheetState.showCastSheet ||
            selectedSongForInfo != null ||
            pendingSaveQueueOverlay != null ||
            isFullPlayerInternalOverlayOpen

    val isTabletFullscreenBackActive =
        sheetBoundsSpec.supportsSideDockedMode &&
            currentSheetContentState == PlayerSheetState.EXPANDED &&
            tabletDisplayMode == TabletPlayerDisplayMode.FULLSCREEN &&
            !hasAnyCardOverlayOpen
    BackHandler(enabled = isTabletFullscreenBackActive) {
        onTabletDisplayModeChange(TabletPlayerDisplayMode.SIDE_DOCKED)
    }

    val hasLeftPaneNavigationBackStack = navController.previousBackStackEntry != null
    val canHandlePlayerBack by remember(
        sheetBackAndDragState.predictiveBackEnabled,
        hasAnyCardOverlayOpen,
        isTabletFullscreenBackActive,
        sheetBoundsSpec.supportsSideDockedMode,
        tabletDisplayMode,
        hasLeftPaneNavigationBackStack
    ) {
        derivedStateOf {
            sheetBackAndDragState.predictiveBackEnabled &&
                !hasAnyCardOverlayOpen &&
                !isTabletFullscreenBackActive &&
                !(sheetBoundsSpec.supportsSideDockedMode &&
                    tabletDisplayMode == TabletPlayerDisplayMode.SIDE_DOCKED &&
                    hasLeftPaneNavigationBackStack)
        }
    }
    val switchToSideDockedMode = remember(onTabletDisplayModeChange) {
        { onTabletDisplayModeChange(TabletPlayerDisplayMode.SIDE_DOCKED) }
    }
    val dismissAllCardSubOverlays = remember(
        queueSheetController,
        castSheetState,
        sheetModalOverlayController
    ) {
        {
            queueSheetController.animate(false)
            castSheetState.dismissCastSheet()
            sheetModalOverlayController.updateSelectedSongForInfo(null)
            sheetModalOverlayController.dismissSaveQueueOverlay()
        }
    }
    val sheetActionHandlers = rememberSheetActionHandlers(
        scope = scope,
        navController = navController,
        playerViewModel = playerViewModel,
        sheetMotionController = sheetMotionController,
        queueSheetController = queueSheetController,
        sheetModalOverlayController = sheetModalOverlayController,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        supportsSideDockedMode = sheetBoundsSpec.supportsSideDockedMode,
        tabletDisplayMode = tabletDisplayMode,
        onSwitchToSideDocked = switchToSideDockedMode
    )

    PlayerArtistNavigationEffect(
        navController = navController,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetMotionController = sheetMotionController,
        playerViewModel = playerViewModel,
        supportsSideDockedMode = sheetBoundsSpec.supportsSideDockedMode,
        tabletDisplayMode = tabletDisplayMode,
        onSwitchToSideDocked = switchToSideDockedMode,
        onDismissSubOverlays = dismissAllCardSubOverlays
    )
    PlayerAlbumNavigationEffect(
        navController = navController,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetMotionController = sheetMotionController,
        playerViewModel = playerViewModel,
        supportsSideDockedMode = sheetBoundsSpec.supportsSideDockedMode,
        tabletDisplayMode = tabletDisplayMode,
        onSwitchToSideDocked = switchToSideDockedMode,
        onDismissSubOverlays = dismissAllCardSubOverlays
    )

    val hapticFeedback = LocalHapticFeedback.current
    val miniDismissGestureHandler = rememberMiniPlayerDismissGestureHandler(
        scope = scope,
        density = density,
        hapticFeedback = hapticFeedback,
        offsetAnimatable = offsetAnimatable,
        screenWidthPx = screenWidthPx,
        onDismissPlaylistAndShowUndo = { playerViewModel.dismissPlaylistAndShowUndo() },
        onDismissStarted = { playerViewModel.setMiniPlayerDismissing(true) }
    )

    QueueSheetRuntimeEffects(
        queueSheetController = queueSheetController,
        queueSheetOffset = queueSheetOffset,
        queueHiddenOffsetPx = queueHiddenOffsetPx,
        showQueueSheet = showQueueSheet,
        allowQueueSheetInteraction = allowQueueSheetInteraction,
        onTopEdgeReached = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    )

    val sideDockedTopMarginPx = remember(sideDockedTopMargin, density) {
        with(density) { sideDockedTopMargin.toPx() }
    }
    PlayerSheetPredictiveBackHandler(
        enabled = canHandlePlayerBack,
        playerViewModel = playerViewModel,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetExpandedTargetY = sheetExpandedTargetY,
        sheetMotionController = sheetMotionController,
        animationDurationMs = ANIMATION_DURATION_MS,
        onSwipeEdgeChanged = { playerViewModel.updatePredictiveBackSwipeEdge(it) },
        registrationKey = currentBackStackEntry?.id,
        expandedVisualYProvider = {
            if (sheetBoundsSpec.supportsSideDockedMode) {
                androidx.compose.ui.util.lerp(
                    sideDockedTopMarginPx,
                    sheetExpandedTargetY,
                    fullScreenFractionProvider().coerceIn(0f, 1f)
                )
            } else {
                sheetExpandedTargetY
            }
        }
    )

    val queuePredictiveBackProgress = remember { Animatable(0f) }
    var queuePredictiveBackSwipeEdge by remember { mutableStateOf<Int?>(null) }

    val sheetOverlayState = rememberSheetOverlayState(
        density = density,
        showPlayerContentArea = showPlayerContentArea,
        hideMiniPlayer = hideMiniPlayer,
        showQueueSheet = showQueueSheet,
        isQueueCollapsing = queueSheetState.isCollapsing,
        queueHiddenOffsetPx = queueHiddenOffsetPx,
        screenHeightPx = screenHeightPx,
        castSheetOpenFractionProvider = castSheetState.castSheetOpenFractionProvider,
        queueSheetOffset = queueSheetOffset,
        queuePredictiveBackProgress = queuePredictiveBackProgress
    )
    val internalIsKeyboardVisible = sheetOverlayState.internalIsKeyboardVisible
    val actuallyShowSheetContent = sheetOverlayState.actuallyShowSheetContent
    val isQueueVisible = sheetOverlayState.isQueueVisible
    val isQueueScrimVisible = sheetOverlayState.isQueueScrimVisible
    val bottomSheetOpenFractionProvider = sheetOverlayState.bottomSheetOpenFractionProvider
    val queueScrimAlphaProvider = sheetOverlayState.queueScrimAlphaProvider
    val shouldRenderQueueHost by remember(internalIsKeyboardVisible, selectedSongForInfo) {
        derivedStateOf {
            !internalIsKeyboardVisible || selectedSongForInfo != null
        }
    }
    val isQueueTelemetryActive = showQueueSheet

    // Battery: only keep 250 ms ticker active while expanded player/lyrics UI is visibly
    // mounted and not fully occluded by Queue/Cast sheets. In collapsed Mini Player
    // (even when FullPlayer stays warm in composition), drop back to MINIPLAYER_TICK_MS (1 s).
    val isSliderUiVisiblyMounted by remember(
        infrequentPlayerState.currentSong?.id,
        currentSheetContentState,
        playerContentExpansionFraction,
        bottomSheetOpenFractionProvider
    ) {
        derivedStateOf {
            val hasSong = infrequentPlayerState.currentSong?.id != null
            val isPlayerVisiblyExpanded =
                currentSheetContentState == PlayerSheetState.EXPANDED ||
                    playerContentExpansionFraction.value > 0.015f
            val isOccludedByQueueOrCast = bottomSheetOpenFractionProvider() >= 0.92f
            hasSong && isPlayerVisiblyExpanded && !isOccludedByQueueOrCast
        }
    }
    DisposableEffect(isSliderUiVisiblyMounted) {
        playerViewModel.setSliderUiMounted(isSliderUiVisiblyMounted)
        onDispose { playerViewModel.setSliderUiMounted(false) }
    }

    LaunchedEffect(showQueueSheet) {
        playerViewModel.updateQueueSheetVisibility(showQueueSheet)
    }
    LaunchedEffect(castSheetState.showCastSheet) {
        playerViewModel.updateCastSheetVisibility(castSheetState.showCastSheet)
    }
    DisposableEffect(Unit) {
        onDispose {
            playerViewModel.updateQueueSheetVisibility(false)
            playerViewModel.updateCastSheetVisibility(false)
        }
    }

    val activePlayerSchemePair by playerViewModel.activePlayerColorSchemePair.collectAsStateWithLifecycle()
    val themedAlbumArtUri by playerViewModel.currentThemedAlbumArtUri.collectAsStateWithLifecycle()
    val isDarkTheme = LocalPixelPlayDarkTheme.current
    val currentSong = infrequentPlayerState.currentSong

    var swipePreviewTargetUri by remember { mutableStateOf<String?>(null) }
    var swipePreviewFractionProvider by remember { mutableStateOf<() -> Float>({ 0f }) }
    val swipePreviewSchemeFlow = remember(swipePreviewTargetUri, playerViewModel) {
        swipePreviewTargetUri?.let { uri ->
            playerViewModel.getAlbumColorSchemeFlow(uri, eager = true)
        }
    }
    val swipePreviewSchemePair = if (swipePreviewSchemeFlow != null) {
        swipePreviewSchemeFlow.collectAsStateWithLifecycle().value
    } else {
        null
    }
    val onSwipePreviewChanged = remember {
        { targetUri: String?, fractionProvider: () -> Float ->
            if (swipePreviewTargetUri != targetUri) {
                swipePreviewTargetUri = targetUri
            }
            swipePreviewFractionProvider = fractionProvider
        }
    }

    val sheetThemeState = rememberSheetThemeState(
        activePlayerSchemePair = activePlayerSchemePair,
        isDarkTheme = isDarkTheme,
        playerThemePreference = playerThemePreference,
        currentSong = currentSong,
        themedAlbumArtUri = themedAlbumArtUri,
        preparingSongId = preparingSongId,
        systemColorScheme = MaterialTheme.colorScheme,
        swipeTargetSchemePair = swipePreviewSchemePair,
        swipeProgressProvider = { swipePreviewFractionProvider() }
    )
    val albumColorScheme = sheetThemeState.albumColorScheme
    val miniPlayerScheme = sheetThemeState.miniPlayerScheme
    val isPreparingPlayback = sheetThemeState.isPreparingPlayback
    val miniReadyAlphaProvider = sheetThemeState.miniReadyAlphaProvider
    val miniAppearScaleProvider = sheetThemeState.miniAppearScaleProvider
    // Elevation is only visible in the mini/collapsed state (expansion < 0.18),
    // or on the floating Side-Docked card in expanded state.
    val isSideDockedCardOverlayMode =
        sheetBoundsSpec.supportsSideDockedMode &&
            tabletDisplayMode == TabletPlayerDisplayMode.SIDE_DOCKED
    val isDragging = sheetBackAndDragState.isDragging
    val showQueueSheetState = rememberUpdatedState(showQueueSheet)
    val isDraggingState = rememberUpdatedState(isDragging)

    val sheetInteractionState = rememberSheetInteractionState(
        scope = scope,
        velocityTracker = velocityTracker,
        sheetMotionController = sheetMotionController,
        playerContentExpansionFraction = playerContentExpansionFraction,
        currentSheetTranslationY = currentSheetTranslationY,
        visualOvershootScaleY = visualOvershootScaleY,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetExpandedTargetY = sheetExpandedTargetY,
        miniPlayerContentHeightPx = miniPlayerContentHeightPx,
        currentSheetContentState = currentSheetContentState,
        showPlayerContentArea = showPlayerContentArea,
        overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
        playerContentActualBottomRadiusProvider = playerContentActualBottomRadiusProvider,
        useSmoothCorners = useSmoothCorners,
        isDragging = sheetBackAndDragState.isDragging,
        onAnimateSheet = { targetExpanded, animationSpec, initialVelocity ->
            if (animationSpec == null) {
                animatePlayerSheet(targetExpanded = targetExpanded)
            } else {
                animatePlayerSheet(
                    targetExpanded = targetExpanded,
                    animationSpec = animationSpec,
                    initialVelocity = initialVelocity
                )
            }
        },
        onExpandSheetState = { playerViewModel.expandPlayerSheet() },
        onCollapseSheetState = { playerViewModel.collapsePlayerSheet() },
        onDraggingChange = sheetBackAndDragState.onDraggingChange,
        onDraggingPlayerAreaChange = sheetBackAndDragState.onDraggingPlayerAreaChange
    )

    if (!actuallyShowSheetContent) return

    val playerSheetSemanticsDescription = remember(
        currentSheetContentState,
        infrequentPlayerState.currentSong?.title
    ) {
        "PixelPlay player sheet ${currentSheetContentState.name.lowercase()} " +
            (infrequentPlayerState.currentSong?.title ?: "")
    }
    val onToggleTabletDisplayMode = remember(tabletDisplayMode, onTabletDisplayModeChange) {
        {
            val nextMode = if (tabletDisplayMode == TabletPlayerDisplayMode.SIDE_DOCKED) {
                TabletPlayerDisplayMode.FULLSCREEN
            } else {
                TabletPlayerDisplayMode.SIDE_DOCKED
            }
            onTabletDisplayModeChange(nextMode)
        }
    }

    val sheetOvershootPx by remember(
        visualSheetTranslationYProvider,
        currentSheetContentState,
        isDragging
    ) {
        derivedStateOf {
            val translationY = visualSheetTranslationYProvider().roundToInt()
            if (translationY < 0) -translationY else 0
        }
    }

    // Use a plain Box instead of M3 Surface here so empty space to the left of the
    // Side-Docked Phone Card on tablets does not intercept or block pointer events on MainUI.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                // Read translationY ONLY in the placement block so normal vertical dragging
                // invalidates placement only and never re-measures the sheet tree.
                val targetHeight = (constraints.maxHeight + sheetOvershootPx).coerceAtLeast(0)
                val placeable = measurable.measure(
                    constraints.copy(
                        minHeight = targetHeight,
                        maxHeight = targetHeight
                    )
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.placeRelative(0, visualSheetTranslationYProvider().roundToInt())
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = currentBottomPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (showPlayerContentArea) {
                    var isQueuePrewarmedAfterSettle by remember(infrequentPlayerState.currentSong?.id) {
                        mutableStateOf(false)
                    }
                    LaunchedEffect(
                        infrequentPlayerState.currentSong?.id,
                        currentSheetContentState,
                        playerContentExpansionFraction.isRunning,
                        currentSheetTranslationY.isRunning
                    ) {
                        if (infrequentPlayerState.currentSong?.id == null) {
                            isQueuePrewarmedAfterSettle = false
                        } else if (
                            currentSheetContentState == PlayerSheetState.EXPANDED &&
                            !playerContentExpansionFraction.isRunning &&
                            !currentSheetTranslationY.isRunning &&
                            !isQueuePrewarmedAfterSettle
                        ) {
                            delay(150L)
                            isQueuePrewarmedAfterSettle = true
                        }
                    }
                    val shouldRenderQueueLayer by remember(
                        isQueuePrewarmedAfterSettle,
                        showQueueSheet,
                        queueSheetState.isCollapsing
                    ) {
                        derivedStateOf {
                            isQueuePrewarmedAfterSettle ||
                                showQueueSheet ||
                                queueSheetState.isCollapsing
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val expansion = playerContentExpansionFraction.value.coerceIn(0f, 1f)
                                val effectiveOvershootY = 1f + (visualOvershootScaleY.value - 1f) * (1f - expansion)
                                val miniAppearScale = miniAppearScaleProvider()
                                translationX = offsetAnimatable.value
                                scaleX = miniAppearScale
                                scaleY = effectiveOvershootY * miniAppearScale
                                alpha = miniReadyAlphaProvider()
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                            // outerLayout:
                            // Measures downstream chain with innerWidth and targetHeightPx.
                            // Places child at startPaddingPx to center it horizontally (or dock it on the right).
                            // Reports full screen width to parent to satisfy fillMaxWidth() constraints.
                            .layout { measurable, constraints ->
                                val targetHeightPx = playerContentAreaHeightPxProvider()
                                    .toInt().coerceAtLeast(0)
                                val startPaddingPx = currentHorizontalPaddingStartPxProvider()
                                    .toInt().coerceAtLeast(0)
                                val endPaddingPx = currentHorizontalPaddingEndPxProvider()
                                    .toInt().coerceAtLeast(0)
                                val innerWidth = (constraints.maxWidth - startPaddingPx - endPaddingPx)
                                    .coerceAtLeast(0)

                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = innerWidth,
                                        maxWidth = innerWidth,
                                        minHeight = targetHeightPx,
                                        maxHeight = targetHeightPx
                                    )
                                )
                                layout(constraints.maxWidth, targetHeightPx) {
                                    placeable.placeRelative(startPaddingPx, 0)
                                }
                            }
                            // Single RenderNode for both shadow elevation and rounded clipping:
                            // evaluates PlayerSheetDynamicShape.createOutline ONCE per frame
                            // (instead of twice with separate .shadow + .clip) and avoids
                            // recomposing UnifiedPlayerSheetV2 when shadow elevation toggles.
                            .graphicsLayer {
                                val expansion = playerContentExpansionFraction.value.coerceIn(0f, 1f)
                                val miniShadow = 3f * miniReadyAlphaProvider()
                                val dockedCardElevation = androidx.compose.ui.util.lerp(miniShadow, 6f, expansion)
                                val fullScreenElevation = if (
                                    showQueueSheetState.value ||
                                    isDraggingState.value ||
                                    playerContentExpansionFraction.isRunning ||
                                    expansion > 0.18f
                                ) {
                                    0f
                                } else {
                                    miniShadow
                                }
                                val elevationDp = if (sheetBoundsSpec.supportsSideDockedMode) {
                                    androidx.compose.ui.util.lerp(
                                        dockedCardElevation,
                                        fullScreenElevation,
                                        fullScreenFractionProvider().coerceIn(0f, 1f)
                                    )
                                } else {
                                    fullScreenElevation
                                }
                                shadowElevation = elevationDp.dp.toPx()
                                shape = sheetInteractionState.playerShadowShape
                                clip = true
                            }
                            .drawBehind {
                                drawRect(color = albumColorScheme.primaryContainer)
                            }
                    ) {
                        // Child 1: MiniPlayer fills outerLayout directly (never snapping 560dp -> 408dp on
                        // Frame 1), while FullPlayerContent measures at its stable target expanded bounds.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .miniPlayerDismissHorizontalGesture(
                                    enabled = currentSheetContentState == PlayerSheetState.COLLAPSED,
                                    handler = miniDismissGestureHandler
                                )
                                .playerSheetVerticalDragGesture(
                                    enabled = sheetInteractionState.canDragSheet && !hasAnyCardOverlayOpen,
                                    handler = sheetInteractionState.sheetVerticalDragGestureHandler
                                )
                                .clickable(
                                    enabled = currentSheetContentState == PlayerSheetState.COLLAPSED ||
                                        (tapBackgroundClosesPlayer && !isSideDockedCardOverlayMode && !hasAnyCardOverlayOpen),
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (currentSheetContentState == PlayerSheetState.COLLAPSED) {
                                        playerViewModel.expandPlayerSheet()
                                    } else if (tapBackgroundClosesPlayer && !isSideDockedCardOverlayMode && !hasAnyCardOverlayOpen) {
                                        playerViewModel.collapsePlayerSheet()
                                    }
                                }
                                .semantics {
                                    contentDescription = playerSheetSemanticsDescription
                                }
                        ) {
                            UnifiedPlayerMiniAndFullLayers(
                                currentSong = infrequentPlayerState.currentSong,
                                miniPlayerScheme = miniPlayerScheme,
                                overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
                                infrequentPlayerState = infrequentPlayerState,
                                isCastConnecting = isCastConnecting,
                                isPreparingPlayback = isPreparingPlayback,
                                playerContentExpansionFraction = playerContentExpansionFraction,
                                albumColorScheme = albumColorScheme,
                                bottomSheetOpenFractionProvider = bottomSheetOpenFractionProvider,
                                fullPlayerVisualState = fullPlayerVisualState,
                                containerHeight = containerHeight,
                                currentQueueSourceName = currentQueueSourceName,
                                currentSheetContentState = currentSheetContentState,
                                carouselStyle = carouselStyle,
                                fullPlayerLoadingTweaks = fullPlayerLoadingTweaks,
                                isSheetDragGestureActive = sheetBackAndDragState.isDraggingPlayerArea,
                                playerViewModel = playerViewModel,
                                currentPositionProvider = positionToDisplayProvider,
                                isFavorite = isFavorite,
                                shouldRenderFullPlayer = shouldRenderFullPlayer || prewarmFullPlayer,
                                currentHorizontalPaddingStartPxProvider = currentHorizontalPaddingStartPxProvider,
                                currentHorizontalPaddingEndPxProvider = currentHorizontalPaddingEndPxProvider,
                                supportsSideDockedMode = sheetBoundsSpec.supportsSideDockedMode,
                                screenWidthPx = screenWidthPx,
                                targetExpandedWidthPxProvider = sheetVisualState.targetExpandedWidthPxProvider,
                                targetExpandedHeightPxProvider = sheetVisualState.targetExpandedHeightPxProvider,
                                onShowQueueClicked = sheetActionHandlers.openQueueSheet,
                                onShowCastClicked = castSheetState.openCastSheet,
                                onSwipePreviewChanged = onSwipePreviewChanged,
                                tabletDisplayMode = tabletDisplayMode,
                                onToggleTabletDisplayMode = onToggleTabletDisplayMode,
                                fullScreenFractionProvider = fullScreenFractionProvider,
                                onOverlayVisibilityChanged = { isFullPlayerInternalOverlayOpen = it }
                            )
                        }

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            PredictiveBackHandler(enabled = isQueueVisible && !internalIsKeyboardVisible) { progressFlow ->
                                try {
                                    progressFlow.collect { backEvent ->
                                        queuePredictiveBackSwipeEdge = backEvent.swipeEdge
                                        queuePredictiveBackProgress.snapTo(backEvent.progress)
                                    }
                                    scope.launch {
                                        launch {
                                            sheetActionHandlers.animateQueueSheet(false)
                                        }
                                        launch {
                                            queuePredictiveBackProgress.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(ANIMATION_DURATION_MS)
                                            )
                                            queuePredictiveBackSwipeEdge = null
                                        }
                                    }
                                } catch (_: kotlin.coroutines.cancellation.CancellationException) {
                                    scope.launch {
                                        queuePredictiveBackProgress.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(ANIMATION_DURATION_MS)
                                        )
                                        queuePredictiveBackSwipeEdge = null
                                    }
                                }
                            }
                        } else {
                            BackHandler(enabled = isQueueVisible && !internalIsKeyboardVisible) {
                                sheetActionHandlers.animateQueueSheet(false)
                            }
                        }

                        val queuePredictiveBackSwipeEdgeState = rememberUpdatedState(queuePredictiveBackSwipeEdge)

                        // Child 2: Queue, Song Info, Save Queue & Connect Device Host measured at stable expanded
                        // bounds. Keyed on supportsSideDockedMode (not tabletDisplayMode) so toggling
                        // SIDE_DOCKED <-> FULLSCREEN never unmounts or resets active Cast / Queue overlays.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .layout { measurable, constraints ->
                                    val targetContentHeightPx = if (sheetBoundsSpec.supportsSideDockedMode) {
                                        sheetVisualState.targetExpandedHeightPxProvider()
                                            .roundToInt()
                                            .coerceAtLeast(1)
                                    } else {
                                        containerHeight.roundToPx().coerceAtLeast(1)
                                    }
                                    val measureWidth = if (sheetBoundsSpec.supportsSideDockedMode) {
                                        sheetVisualState.targetExpandedWidthPxProvider()
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
                                        val isOverlayActive =
                                            showQueueSheet ||
                                                queueSheetState.isCollapsing ||
                                                selectedSongForInfo != null ||
                                                pendingSaveQueueOverlay != null ||
                                                (sheetBoundsSpec.supportsSideDockedMode && castSheetState.showCastSheet)
                                        if (!isOverlayActive) {
                                            placeable.placeRelative(0, 10000)
                                        } else {
                                            val xOffset = if (sheetBoundsSpec.supportsSideDockedMode) {
                                                ((constraints.maxWidth - measureWidth) / 2f).roundToInt()
                                            } else {
                                                -currentHorizontalPaddingStartPxProvider().toInt()
                                            }
                                            placeable.placeRelative(xOffset, 0)
                                        }
                                    }
                                }
                        ) {
                            UnifiedPlayerQueueAndSongInfoHost(
                                shouldRenderHost = shouldRenderQueueHost && (shouldRenderQueueLayer || selectedSongForInfo != null),
                                keepQueueSheetWarm = (isQueuePrewarmedAfterSettle || currentSheetContentState == PlayerSheetState.EXPANDED) &&
                                    !internalIsKeyboardVisible,
                                isQueueTelemetryActive = isQueueTelemetryActive,
                                albumColorScheme = if (showQueueSheet || queueSheetState.isCollapsing || selectedSongForInfo != null) {
                                    albumColorScheme
                                } else {
                                    sheetThemeState.targetAlbumColorScheme
                                },
                                isQueueScrimVisible = isQueueScrimVisible,
                                queueScrimAlphaProvider = queueScrimAlphaProvider,
                                showQueueSheet = showQueueSheet,
                                isQueueCollapsing = queueSheetState.isCollapsing,
                                queueHiddenOffsetPx = queueHiddenOffsetPx,
                                queueSheetOffset = queueSheetOffset,
                                queueSheetHeightPx = queueSheetHeightPx,
                                onQueueSheetHeightPxChange = onQueueSheetHeightPxChange,
                                configurationResetKey = configuration,
                                currentQueueSourceName = currentQueueSourceName,
                                infrequentPlayerState = infrequentPlayerState,
                                playerViewModel = playerViewModel,
                                selectedSongForInfo = selectedSongForInfo,
                                onSelectedSongForInfoChange = sheetActionHandlers.onSelectedSongForInfoChange,
                                onAnimateQueueSheet = sheetActionHandlers.animateQueueSheet,
                                onBeginQueueDrag = sheetActionHandlers.beginQueueDrag,
                                onDragQueueBy = sheetActionHandlers.dragQueueBy,
                                onEndQueueDrag = sheetActionHandlers.endQueueDrag,
                                onLaunchSaveQueueOverlay = sheetActionHandlers.onLaunchSaveQueueOverlay,
                                onNavigateToAlbum = sheetActionHandlers.onNavigateToAlbum,
                                onNavigateToArtist = sheetActionHandlers.onNavigateToArtist,
                                onNavigateToGenre = sheetActionHandlers.onNavigateToGenre,
                                queuePredictiveBackProgress = queuePredictiveBackProgress,
                                queuePredictiveBackSwipeEdge = queuePredictiveBackSwipeEdgeState,
                                shouldRenderQueueLayer = shouldRenderQueueLayer,
                                isContainedInCard = isSideDockedCardOverlayMode,
                                minMeasuredQueueHeightPx = miniPlayerContentHeightPx * 2.5f
                            )

                            if (sheetBoundsSpec.supportsSideDockedMode) {
                                UnifiedPlayerCastLayer(
                                    showCastSheet = castSheetState.showCastSheet,
                                    internalIsKeyboardVisible = internalIsKeyboardVisible,
                                    albumColorScheme = albumColorScheme,
                                    playerViewModel = playerViewModel,
                                    onDismiss = castSheetState.dismissCastSheet,
                                    onExpansionChanged = castSheetState.onCastExpansionChanged,
                                    isContainedInCard = true
                                )
                                UnifiedPlayerSaveQueueLayer(
                                    pendingOverlay = pendingSaveQueueOverlay,
                                    onDismissOverlay = { sheetModalOverlayController.dismissSaveQueueOverlay() },
                                    isContainedInCard = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (!sheetBoundsSpec.supportsSideDockedMode) {
        UnifiedPlayerCastLayer(
            showCastSheet = castSheetState.showCastSheet,
            internalIsKeyboardVisible = internalIsKeyboardVisible,
            albumColorScheme = albumColorScheme,
            playerViewModel = playerViewModel,
            onDismiss = castSheetState.dismissCastSheet,
            onExpansionChanged = castSheetState.onCastExpansionChanged,
            isContainedInCard = false
        )
        UnifiedPlayerSaveQueueLayer(
            pendingOverlay = pendingSaveQueueOverlay,
            onDismissOverlay = { sheetModalOverlayController.dismissSaveQueueOverlay() },
            isContainedInCard = false
        )
    }
}
