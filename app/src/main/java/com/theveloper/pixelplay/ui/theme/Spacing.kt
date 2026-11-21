package com.theveloper.pixelplay.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Unified 4dp Material 3 spacing grid & adaptive layout tokens for PixelPlayer.
 * Read via [LocalSpacing.current] to eliminate ad-hoc hardcoded dp spacing across screens.
 */
@Immutable
data class PixelPlaySpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
    // Adaptive tokens resolved per WindowWidthTier
    val screenHorizontal: Dp = 16.dp,
    val paneGap: Dp = 16.dp,
    val sectionGap: Dp = 20.dp,
    val groupItemGap: Dp = 2.dp,
    val listRowHorizontal: Dp = 16.dp,
    val listRowVertical: Dp = 14.dp,
    val listRowMinHeight: Dp = 64.dp,
    val iconTextGap: Dp = 16.dp,
    val titleSubtitleGap: Dp = 2.dp,
    val maxReadableContentWidth: Dp = 840.dp
)

fun spacingForWidthTier(widthTier: WindowWidthTier): PixelPlaySpacing = when (widthTier) {
    WindowWidthTier.COMPACT -> PixelPlaySpacing(
        screenHorizontal = 16.dp,
        paneGap = 12.dp,
        sectionGap = 20.dp,
        maxReadableContentWidth = 640.dp
    )
    WindowWidthTier.MEDIUM -> PixelPlaySpacing(
        screenHorizontal = 20.dp,
        paneGap = 16.dp,
        sectionGap = 20.dp,
        maxReadableContentWidth = 720.dp
    )
    WindowWidthTier.EXPANDED -> PixelPlaySpacing(
        screenHorizontal = 24.dp,
        paneGap = 16.dp,
        sectionGap = 24.dp,
        maxReadableContentWidth = 840.dp
    )
    WindowWidthTier.LARGE -> PixelPlaySpacing(
        screenHorizontal = 24.dp,
        paneGap = 20.dp,
        sectionGap = 24.dp,
        maxReadableContentWidth = 840.dp
    )
    WindowWidthTier.EXTRA_LARGE -> PixelPlaySpacing(
        screenHorizontal = 32.dp,
        paneGap = 24.dp,
        sectionGap = 24.dp,
        maxReadableContentWidth = 920.dp
    )
}

val LocalSpacing = staticCompositionLocalOf { PixelPlaySpacing() }
