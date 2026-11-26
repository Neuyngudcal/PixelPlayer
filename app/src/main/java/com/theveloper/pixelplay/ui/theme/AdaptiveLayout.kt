package com.theveloper.pixelplay.ui.theme

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
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
    val hasNavigationRailOverride: Boolean? = null,
    val hostWidthTier: WindowWidthTier = widthTier
) {
    val isCompactWidth: Boolean
        get() = widthTier == WindowWidthTier.COMPACT

    val isTabletLayout: Boolean
        get() = hasNavigationRailOverride ?: (widthTier.ordinal >= WindowWidthTier.MEDIUM.ordinal)

    /** Use persistent start-edge Material 3 Navigation Rail instead of Floating Bottom Bar. */
    val useNavigationRail: Boolean
        get() = hasNavigationRailOverride ?: (widthTier.ordinal >= WindowWidthTier.MEDIUM.ordinal)

    /** Allow expanding the Navigation Rail into a Wide Navigation Rail (220dp) on Large+ screens. */
    val supportsExpandableWideRail: Boolean
        get() = hostWidthTier.ordinal >= WindowWidthTier.LARGE.ordinal

    /** Default to expanded Wide Navigation Rail on ExtraLarge (e.g. Samsung DeX external displays). */
    val startWithExpandedWideRail: Boolean
        get() = hostWidthTier == WindowWidthTier.EXTRA_LARGE

    /** Show 2-pane Samsung x Google Play Settings List-Detail layout. */
    val useTwoPaneSettings: Boolean
        get() = if (useNavigationRail) widthDp >= 580 else widthDp >= 720

    /** Show 2-column split (sticky hero + tracklist) on Album / Artist / Playlist / Genre detail screens. */
    val useTwoPaneDetail: Boolean
        get() = if (useNavigationRail) {
            widthDp >= 580
        } else {
            widthTier.ordinal >= WindowWidthTier.EXPANDED.ordinal
        }

    /**
     * Window-size-aware 2-column FullPlayer trigger.
     * Replaces fixed ORIENTATION_LANDSCAPE checks so Samsung Multi-Window / Pop-up View / DeX behave properly.
     */
    val useTwoColumnPlayer: Boolean
        get() = widthDp >= 720 && widthDp >= (heightDp * 0.9f)

    /** Show 2-column balanced Home dashboard (Hero Mix + Recently Played side by side). */
    val useTwoColumnHome: Boolean
        get() = if (useNavigationRail) {
            widthDp >= 580
        } else {
            widthTier.ordinal >= WindowWidthTier.EXPANDED.ordinal
        }

    /** Show 2-column Bento grid on Listening Stats screen. */
    val useTwoColumnStats: Boolean
        get() = if (useNavigationRail) {
            widthDp >= 580
        } else {
            widthTier.ordinal >= WindowWidthTier.EXPANDED.ordinal
        }

    /** Adaptive minimum cell width for Album / Artist / Playlist grids on compact phones. */
    val mediaGridMinCellSize: Dp
        get() = when (hostWidthTier) {
            WindowWidthTier.COMPACT -> 160.dp
            WindowWidthTier.MEDIUM -> 160.dp
            WindowWidthTier.EXPANDED -> 176.dp
            WindowWidthTier.LARGE -> 192.dp
            WindowWidthTier.EXTRA_LARGE -> 196.dp
        }

    /**
     * Invariant column count for tablet media & genre grids keyed strictly on [hostWidthTier]
     * so smoothly narrowing/widening the left pane for the Side-Docked Card scales cards
     * in place without mid-spring integer column-count jumps.
     */
    val tabletMediaGridColumns: Int
        get() = when (hostWidthTier) {
            WindowWidthTier.COMPACT -> 2
            WindowWidthTier.MEDIUM -> 2
            WindowWidthTier.EXPANDED -> 3
            WindowWidthTier.LARGE -> 4
            WindowWidthTier.EXTRA_LARGE -> 5
        }

    /** Preferred list-pane width in Settings 2-pane ListDetailPaneScaffold (keyed on host tier so the left list pane stays stationary during side-docking). */
    val settingsListPanePreferredWidth: Dp
        get() = when (hostWidthTier) {
            WindowWidthTier.COMPACT,
            WindowWidthTier.MEDIUM -> 280.dp
            WindowWidthTier.EXPANDED -> 296.dp
            WindowWidthTier.LARGE -> 320.dp
            WindowWidthTier.EXTRA_LARGE -> 340.dp
        }

    /** Fixed left hero column width on 2-pane Album / Artist / Playlist / Genre detail screens. */
    val detailHeroPaneWidth: Dp
        get() = when (hostWidthTier) {
            WindowWidthTier.COMPACT,
            WindowWidthTier.MEDIUM -> 264.dp
            WindowWidthTier.EXPANDED -> 292.dp
            WindowWidthTier.LARGE -> 324.dp
            WindowWidthTier.EXTRA_LARGE -> 368.dp
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
    ).copy(
        hasNavigationRailOverride = windowInfo.useNavigationRail,
        hostWidthTier = windowInfo.widthTier
    )
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

val LocalPixelPlayWindowInfo = compositionLocalOf { PixelPlayWindowInfo() }
