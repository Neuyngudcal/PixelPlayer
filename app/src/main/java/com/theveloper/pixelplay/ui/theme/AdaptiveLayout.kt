package com.theveloper.pixelplay.ui.theme

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Official Material 3 Adaptive 5-tier width breakpoints:
 * - [COMPACT]: < 600dp (phones, narrow split-screen / pop-up windows)
 * - [MEDIUM]: 600..839dp (unfolded foldables, tablet 50% split-screen)
 * - [EXPANDED]: 840..1199dp (Galaxy Tab S10 Ultra portrait ~924dp, 11" tablets)
 * - [LARGE]: 1200..1599dp (Galaxy Tab S10 Ultra landscape ~1480dp)
 * - [EXTRA_LARGE]: >= 1600dp (Samsung DeX / external desktop monitors)
 */
enum class WindowWidthTier {
    COMPACT,
    MEDIUM,
    EXPANDED,
    LARGE,
    EXTRA_LARGE
}

enum class WindowHeightTier {
    COMPACT,
    MEDIUM,
    EXPANDED
}

@Immutable
data class PixelPlayWindowInfo(
    val widthDp: Int = 411,
    val heightDp: Int = 915,
    val widthTier: WindowWidthTier = WindowWidthTier.COMPACT,
    val heightTier: WindowHeightTier = WindowHeightTier.EXPANDED,
    val hasNavigationRailOverride: Boolean? = null
) {
    val isCompactWidth: Boolean
        get() = widthTier == WindowWidthTier.COMPACT

    val isTabletLayout: Boolean
        get() = widthTier.ordinal >= WindowWidthTier.MEDIUM.ordinal

    /** Use persistent start-edge Material 3 Navigation Rail instead of Floating Bottom Bar. */
    val useNavigationRail: Boolean
        get() = hasNavigationRailOverride ?: (widthTier.ordinal >= WindowWidthTier.MEDIUM.ordinal)

    /** Allow expanding the Navigation Rail into a Wide Navigation Rail (220dp) on Large+ screens. */
    val supportsExpandableWideRail: Boolean
        get() = widthTier.ordinal >= WindowWidthTier.LARGE.ordinal

    /** Default to expanded Wide Navigation Rail on ExtraLarge (e.g. Samsung DeX external displays). */
    val startWithExpandedWideRail: Boolean
        get() = widthTier == WindowWidthTier.EXTRA_LARGE

    /** Show 2-pane Samsung x Google Play Settings List-Detail layout. */
    val useTwoPaneSettings: Boolean
        get() = widthDp >= 720

    /** Show 2-column split (sticky hero + tracklist) on Album / Artist / Playlist / Genre detail screens. */
    val useTwoPaneDetail: Boolean
        get() = widthTier.ordinal >= WindowWidthTier.EXPANDED.ordinal

    /**
     * Window-size-aware 2-column FullPlayer trigger.
     * Replaces fixed ORIENTATION_LANDSCAPE checks so Samsung Multi-Window / Pop-up View / DeX behave properly.
     */
    val useTwoColumnPlayer: Boolean
        get() = widthDp >= 720 && widthDp >= (heightDp * 0.9f)

    /** Show 2-column balanced Home dashboard (Hero Mix + Recently Played side by side). */
    val useTwoColumnHome: Boolean
        get() = widthTier.ordinal >= WindowWidthTier.EXPANDED.ordinal

    /** Show 2-column Bento grid on Listening Stats screen. */
    val useTwoColumnStats: Boolean
        get() = widthTier.ordinal >= WindowWidthTier.EXPANDED.ordinal

    /** Adaptive minimum cell width for Album / Artist / Playlist grids. */
    val mediaGridMinCellSize: Dp
        get() = when (widthTier) {
            WindowWidthTier.COMPACT -> 160.dp
            WindowWidthTier.MEDIUM -> 160.dp
            WindowWidthTier.EXPANDED -> 176.dp
            WindowWidthTier.LARGE -> 192.dp
            WindowWidthTier.EXTRA_LARGE -> 196.dp
        }

    /** Preferred list-pane width in Settings 2-pane ListDetailPaneScaffold. */
    val settingsListPanePreferredWidth: Dp
        get() = when (widthTier) {
            WindowWidthTier.COMPACT,
            WindowWidthTier.MEDIUM -> 320.dp
            WindowWidthTier.EXPANDED -> 340.dp
            WindowWidthTier.LARGE -> 360.dp
            WindowWidthTier.EXTRA_LARGE -> 380.dp
        }

    /** Fixed left hero column width on 2-pane Album / Artist / Playlist / Genre detail screens. */
    val detailHeroPaneWidth: Dp
        get() = when (widthTier) {
            WindowWidthTier.COMPACT,
            WindowWidthTier.MEDIUM -> 320.dp
            WindowWidthTier.EXPANDED -> 340.dp
            WindowWidthTier.LARGE -> 380.dp
            WindowWidthTier.EXTRA_LARGE -> 400.dp
        }

    /** Constrained floating pill width for MiniPlayer on Medium+ tablets (full width on Compact phones). */
    val miniPlayerMaxWidth: Dp
        get() = if (useNavigationRail) 560.dp else Dp.Unspecified
}

fun classifyWindowWidthTier(widthDp: Int): WindowWidthTier = when {
    widthDp < 600 -> WindowWidthTier.COMPACT
    widthDp < 840 -> WindowWidthTier.MEDIUM
    widthDp < 1200 -> WindowWidthTier.EXPANDED
    widthDp < 1600 -> WindowWidthTier.LARGE
    else -> WindowWidthTier.EXTRA_LARGE
}

fun classifyWindowHeightTier(heightDp: Int): WindowHeightTier = when {
    heightDp < 480 -> WindowHeightTier.COMPACT
    heightDp < 900 -> WindowHeightTier.MEDIUM
    else -> WindowHeightTier.EXPANDED
}

fun computePixelPlayWindowInfo(widthDp: Int, heightDp: Int): PixelPlayWindowInfo {
    val safeWidth = widthDp.coerceAtLeast(1)
    val safeHeight = heightDp.coerceAtLeast(1)
    return PixelPlayWindowInfo(
        widthDp = safeWidth,
        heightDp = safeHeight,
        widthTier = classifyWindowWidthTier(safeWidth),
        heightTier = classifyWindowHeightTier(safeHeight)
    )
}

/**
 * Computes the effective window metrics available to main navigation destinations after reserving
 * space for the persistent Navigation Rail and an expanded Side-Docked Now Playing panel, while
 * preserving the outer host's [PixelPlayWindowInfo.useNavigationRail] capability.
 */
fun computeEffectiveContentWindowInfo(
    windowInfo: PixelPlayWindowInfo,
    navigationRailWidthDp: Int,
    sideDockedReservedWidthDp: Int
): PixelPlayWindowInfo {
    val totalReserved = navigationRailWidthDp.coerceAtLeast(0) + sideDockedReservedWidthDp.coerceAtLeast(0)
    if (totalReserved == 0) return windowInfo
    val remainingWidthDp = (windowInfo.widthDp - totalReserved).coerceAtLeast(360)
    return computePixelPlayWindowInfo(
        widthDp = remainingWidthDp,
        heightDp = windowInfo.heightDp
    ).copy(hasNavigationRailOverride = windowInfo.useNavigationRail)
}

@Composable
fun rememberPixelPlayWindowInfo(): PixelPlayWindowInfo {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    // Subscribe to official Material 3 Adaptive window info so fold/posture/resize recomposes cleanly.
    val adaptiveInfo = currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)

    val widthDp = remember(containerSize.width, configuration.screenWidthDp, density) {
        if (containerSize.width > 0) {
            with(density) { containerSize.width.toDp().value.roundToInt() }
        } else {
            configuration.screenWidthDp.coerceAtLeast(1)
        }
    }
    val heightDp = remember(containerSize.height, configuration.screenHeightDp, density) {
        if (containerSize.height > 0) {
            with(density) { containerSize.height.toDp().value.roundToInt() }
        } else {
            configuration.screenHeightDp.coerceAtLeast(1)
        }
    }

    return remember(widthDp, heightDp, adaptiveInfo.windowSizeClass) {
        computePixelPlayWindowInfo(widthDp = widthDp, heightDp = heightDp)
    }
}

val LocalPixelPlayWindowInfo = staticCompositionLocalOf { PixelPlayWindowInfo() }
