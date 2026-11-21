package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.PixelPlayGroupShapes
import com.theveloper.pixelplay.ui.theme.PixelPlayWindowInfo

/**
 * Controls how the expanded Now Playing sheet is presented on tablet-class windows:
 * - [SIDE_DOCKED]: Phone-proportioned vertical Now Playing card docked on the right side
 *   while the main app navigation remains clear and interactive on the left (Image 1).
 * - [FULLSCREEN]: Full-screen 2-column immersive Now Playing experience (Image 2).
 */
enum class TabletPlayerDisplayMode {
    SIDE_DOCKED,
    FULLSCREEN
}

/**
 * Canonical Now Playing layout mode resolved from [PixelPlayWindowInfo] and [TabletPlayerDisplayMode].
 */
enum class NowPlayingLayoutMode {
    PORTRAIT_COMPACT,
    SIDE_DOCKED_PORTRAIT,
    PORTRAIT_TABLET_FULLSCREEN,
    COMPACT_LANDSCAPE,
    TABLET_LANDSCAPE_FULLSCREEN
}

/**
 * Optional inline side panel inside Fullscreen Tablet Landscape Now Playing.
 */
enum class NowPlayingSidePanel {
    NONE,
    LYRICS,
    QUEUE
}

@Immutable
data class NowPlayingSheetBoundsSpec(
    val supportsSideDockedMode: Boolean,
    val sideDockedWidth: Dp,
    val sideDockedEndMargin: Dp,
    val sideDockedVerticalMargin: Dp,
    val sideDockedCornerRadius: Dp
)

@Immutable
data class NowPlayingLayoutMetrics(
    val mode: NowPlayingLayoutMode,
    val showTopUtilityBar: Boolean,
    val supportsInlineSidePanel: Boolean,
    val supportsSideDockedMode: Boolean,
    val albumArtMaxSize: Dp,
    val controlsMaxWidth: Dp,
    val deckSpacing: Dp
)

fun resolveNowPlayingSheetBoundsSpec(windowInfo: PixelPlayWindowInfo): NowPlayingSheetBoundsSpec {
    val supportsSideDocked = windowInfo.isTabletLayout &&
        windowInfo.widthDp >= 720 &&
        windowInfo.heightDp >= 560

    val dockedWidth = when {
        windowInfo.widthDp >= 1100 -> 408.dp
        windowInfo.widthDp >= 840 -> 384.dp
        else -> 356.dp
    }

    return NowPlayingSheetBoundsSpec(
        supportsSideDockedMode = supportsSideDocked,
        sideDockedWidth = dockedWidth,
        sideDockedEndMargin = 16.dp,
        sideDockedVerticalMargin = 12.dp,
        sideDockedCornerRadius = PixelPlayGroupShapes.PaneContainerRadius
    )
}

fun resolveNowPlayingLayoutMetrics(
    windowInfo: PixelPlayWindowInfo,
    displayMode: TabletPlayerDisplayMode = TabletPlayerDisplayMode.FULLSCREEN
): NowPlayingLayoutMetrics {
    val boundsSpec = resolveNowPlayingSheetBoundsSpec(windowInfo)

    if (boundsSpec.supportsSideDockedMode && displayMode == TabletPlayerDisplayMode.SIDE_DOCKED) {
        return NowPlayingLayoutMetrics(
            mode = NowPlayingLayoutMode.SIDE_DOCKED_PORTRAIT,
            showTopUtilityBar = true,
            supportsInlineSidePanel = false,
            supportsSideDockedMode = true,
            albumArtMaxSize = 340.dp,
            controlsMaxWidth = boundsSpec.sideDockedWidth,
            deckSpacing = 14.dp
        )
    }

    if (windowInfo.useTwoColumnPlayer && windowInfo.heightDp >= 560) {
        return NowPlayingLayoutMetrics(
            mode = NowPlayingLayoutMode.TABLET_LANDSCAPE_FULLSCREEN,
            showTopUtilityBar = false,
            supportsInlineSidePanel = windowInfo.widthDp >= 900,
            supportsSideDockedMode = boundsSpec.supportsSideDockedMode,
            albumArtMaxSize = 540.dp,
            controlsMaxWidth = 500.dp,
            deckSpacing = 24.dp
        )
    }

    if (windowInfo.useTwoColumnPlayer) {
        return NowPlayingLayoutMetrics(
            mode = NowPlayingLayoutMode.COMPACT_LANDSCAPE,
            showTopUtilityBar = false,
            supportsInlineSidePanel = false,
            supportsSideDockedMode = false,
            albumArtMaxSize = 280.dp,
            controlsMaxWidth = 460.dp,
            deckSpacing = 12.dp
        )
    }

    if (windowInfo.isTabletLayout) {
        return NowPlayingLayoutMetrics(
            mode = NowPlayingLayoutMode.PORTRAIT_TABLET_FULLSCREEN,
            showTopUtilityBar = true,
            supportsInlineSidePanel = false,
            supportsSideDockedMode = boundsSpec.supportsSideDockedMode,
            albumArtMaxSize = 480.dp,
            controlsMaxWidth = 560.dp,
            deckSpacing = 24.dp
        )
    }

    return NowPlayingLayoutMetrics(
        mode = NowPlayingLayoutMode.PORTRAIT_COMPACT,
        showTopUtilityBar = true,
        supportsInlineSidePanel = false,
        supportsSideDockedMode = false,
        albumArtMaxSize = 400.dp,
        controlsMaxWidth = 480.dp,
        deckSpacing = 16.dp
    )
}
