package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState

private const val PREDICTIVE_BACK_SWIPE_EDGE_LEFT = 0
private const val PREDICTIVE_BACK_SWIPE_EDGE_RIGHT = 1

internal data class SheetVisualState(
    val currentBottomPadding: Dp,
    val baseBottomPadding: Dp,
    /** Draw-phase provider: read this inside graphicsLayer to avoid layout relayout per frame. */
    val playerContentAreaHeightPxProvider: () -> Float,
    /** Layout-phase provider: read inside .offset { } to avoid recomposition per drag frame. */
    val visualSheetTranslationYProvider: () -> Float,
    val overallSheetTopCornerRadiusProvider: () -> Dp,
    val playerContentActualBottomRadiusProvider: () -> Dp,
    /** Draw-phase providers: read inside graphicsLayer to avoid layout relayout per frame. */
    val currentHorizontalPaddingStartPxProvider: () -> Float,
    val currentHorizontalPaddingEndPxProvider: () -> Float,
    /** Target expanded dimensions for innerLayout (408dp x dockedHeight in Side-Docked, full screen in Fullscreen). */
    val targetExpandedWidthPxProvider: () -> Float,
    val targetExpandedHeightPxProvider: () -> Float
)

@Composable
internal fun rememberSheetVisualState(
    showPlayerContentArea: Boolean,
    collapsedStateHorizontalPadding: Dp,
    collapsedStartHorizontalPadding: Dp = collapsedStateHorizontalPadding,
    collapsedEndHorizontalPadding: Dp = collapsedStateHorizontalPadding,
    predictiveBackCollapseProgress: Float,
    predictiveBackSwipeEdge: Int?,
    currentSheetContentState: PlayerSheetState,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    containerHeight: Dp,
    currentSheetTranslationY: Animatable<Float, AnimationVector1D>,
    sheetCollapsedTargetY: Float,
    navBarStyle: String,
    navBarCornerRadiusDp: Dp,
    isNavBarHidden: Boolean,
    isPlaying: Boolean,
    hasCurrentSong: Boolean,
    swipeDismissProgress: Float,
    containerWidth: Dp = Dp.Unspecified,
    supportsSideDockedMode: Boolean = false,
    sideDockedWidth: Dp = 408.dp,
    sideDockedEndMargin: Dp = 16.dp,
    sideDockedTopMargin: Dp = 12.dp,
    sideDockedBottomMargin: Dp = 12.dp,
    sideDockedCornerRadius: Dp = 28.dp,
    fullScreenFractionProvider: () -> Float = { 1f }
): SheetVisualState {
    // Compute in px to be read inside graphicsLayer (draw phase) — zero relayout per drag frame.
    val density = LocalDensity.current
    val baseBottomPadding = remember(containerHeight, sheetCollapsedTargetY, density) {
        val targetYDp = with(density) { sheetCollapsedTargetY.toDp() }
        (containerHeight - com.theveloper.pixelplay.presentation.components.MiniPlayerHeight - targetYDp)
            .coerceAtLeast(0.dp)
    }

    val currentBottomPadding = 0.dp

    val miniHeightPx = remember(density) { with(density) { com.theveloper.pixelplay.presentation.components.MiniPlayerHeight.toPx() } }
    val containerHeightPx = remember(containerHeight, density) { with(density) { containerHeight.toPx() } }
    val containerWidthPx = remember(containerWidth, density) {
        with(density) {
            if (containerWidth != Dp.Unspecified) containerWidth.toPx() else 0f
        }
    }
    val sideDockedWidthPx = remember(sideDockedWidth, density) { with(density) { sideDockedWidth.toPx() } }
    val sideDockedEndMarginPx = remember(sideDockedEndMargin, density) { with(density) { sideDockedEndMargin.toPx() } }
    val sideDockedTopMarginPx = remember(sideDockedTopMargin, density) { with(density) { sideDockedTopMargin.toPx() } }
    val sideDockedBottomMarginPx = remember(sideDockedBottomMargin, density) { with(density) { sideDockedBottomMargin.toPx() } }

    val predictiveBackCollapseProgressState = rememberUpdatedState(predictiveBackCollapseProgress)
    val visualSheetTranslationYProvider: () -> Float = remember(
        currentSheetTranslationY,
        sheetCollapsedTargetY,
        supportsSideDockedMode,
        sideDockedTopMarginPx,
        playerContentExpansionFraction,
        fullScreenFractionProvider
    ) {
        {
            val progress = predictiveBackCollapseProgressState.value
            val baseTranslationY = currentSheetTranslationY.value * (1f - progress) +
                (sheetCollapsedTargetY * progress)
            if (!supportsSideDockedMode) {
                baseTranslationY
            } else {
                val fullScreenFraction = fullScreenFractionProvider().coerceIn(0f, 1f)
                val expandedTopY = androidx.compose.ui.util.lerp(sideDockedTopMarginPx, 0f, fullScreenFraction)
                val effectiveExpansion = (playerContentExpansionFraction.value * (1f - progress)).coerceIn(0f, 1f)
                baseTranslationY + expandedTopY * effectiveExpansion
            }
        }
    }

    val targetExpandedHeightPxProvider: () -> Float = remember(
        containerHeightPx,
        miniHeightPx,
        supportsSideDockedMode,
        sideDockedTopMarginPx,
        sideDockedBottomMarginPx,
        fullScreenFractionProvider
    ) {
        {
            if (!supportsSideDockedMode) {
                containerHeightPx
            } else {
                val fullScreenFraction = fullScreenFractionProvider().coerceIn(0f, 1f)
                val dockedHeightPx = (containerHeightPx - sideDockedTopMarginPx - sideDockedBottomMarginPx)
                    .coerceAtLeast(miniHeightPx)
                androidx.compose.ui.util.lerp(dockedHeightPx, containerHeightPx, fullScreenFraction)
            }
        }
    }

    val targetExpandedWidthPxProvider: () -> Float = remember(
        containerWidthPx,
        supportsSideDockedMode,
        sideDockedWidthPx,
        sideDockedEndMarginPx,
        fullScreenFractionProvider
    ) {
        {
            if (!supportsSideDockedMode || containerWidthPx <= 0f) {
                containerWidthPx
            } else {
                val fullScreenFraction = fullScreenFractionProvider().coerceIn(0f, 1f)
                val clampedDockedWidthPx = sideDockedWidthPx.coerceAtMost(
                    (containerWidthPx - sideDockedEndMarginPx * 2f).coerceAtLeast(1f)
                )
                androidx.compose.ui.util.lerp(clampedDockedWidthPx, containerWidthPx, fullScreenFraction)
            }
        }
    }

    val playerContentAreaHeightPxProvider: () -> Float = remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        miniHeightPx,
        containerHeightPx,
        visualSheetTranslationYProvider,
        sheetCollapsedTargetY,
        supportsSideDockedMode,
        sideDockedBottomMarginPx,
        targetExpandedHeightPxProvider,
        fullScreenFractionProvider
    ) {
        {
            if (showPlayerContentArea) {
                val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
                val safeFraction = effectiveFraction.coerceIn(0f, 1f)
                val fullScreenFraction = if (supportsSideDockedMode) {
                    fullScreenFractionProvider().coerceIn(0f, 1f)
                } else {
                    1f
                }
                val expandedBottomY = androidx.compose.ui.util.lerp(
                    containerHeightPx - sideDockedBottomMarginPx,
                    containerHeightPx,
                    fullScreenFraction
                )
                val expandedHeightPx = targetExpandedHeightPxProvider()
                val translationY = visualSheetTranslationYProvider()

                if (translationY <= sheetCollapsedTargetY) {
                    val targetBottom = androidx.compose.ui.util.lerp(
                        sheetCollapsedTargetY + miniHeightPx,
                        expandedBottomY,
                        safeFraction
                    )
                    (targetBottom - translationY).coerceAtLeast(0f)
                } else {
                    androidx.compose.ui.util.lerp(miniHeightPx, expandedHeightPx, safeFraction)
                }
            } else {
                0f
            }
        }
    }

    val overallSheetTopCornerRadiusProvider: () -> Dp = remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        navBarStyle,
        navBarCornerRadiusDp,
        isNavBarHidden,
        swipeDismissProgress,
        currentSheetContentState,
        supportsSideDockedMode,
        sideDockedCornerRadius,
        fullScreenFractionProvider
    ) {
        {
            val collapsedCornerTarget = if (isNavBarHidden) {
                32.dp
            } else if (navBarStyle == NavBarStyle.DEFAULT) {
                navBarCornerRadiusDp
            } else if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                32.dp
            } else {
                navBarCornerRadiusDp
            }

            val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
            val safeFraction = effectiveFraction.coerceIn(0f, 1f)
            val fullScreenFraction = if (supportsSideDockedMode) {
                fullScreenFractionProvider().coerceIn(0f, 1f)
            } else {
                1f
            }
            val expandedTarget = lerp(sideDockedCornerRadius, 0.dp, fullScreenFraction)
            val calculatedNormally = if (showPlayerContentArea) {
                lerp(collapsedCornerTarget, expandedTarget, safeFraction)
            } else {
                if (navBarStyle == NavBarStyle.DEFAULT) {
                    navBarCornerRadiusDp
                } else if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                    0.dp
                } else if (isNavBarHidden) {
                    60.dp
                } else {
                    navBarCornerRadiusDp
                }
            }

            calculatedNormally
        }
    }

    // isPlaying and hasCurrentSong are only used in the fallback branch when
    // !showPlayerContentArea. Reading them via rememberUpdatedState keeps the
    // shape provider lambda stable across play/pause toggles — so the
    // PlayerSheetDynamicShape instance (and the modifier chain that consumes it)
    // is not recreated on every isPlaying flip.
    val isPlayingState = rememberUpdatedState(isPlaying)
    val hasCurrentSongState = rememberUpdatedState(hasCurrentSong)
    val playerContentActualBottomRadiusProvider: () -> Dp = remember(
        navBarStyle,
        showPlayerContentArea,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        swipeDismissProgress,
        isNavBarHidden,
        navBarCornerRadiusDp,
        currentSheetContentState,
        supportsSideDockedMode,
        sideDockedCornerRadius,
        fullScreenFractionProvider
    ) {
        {
            val collapsedRadius = if (isNavBarHidden) {
                32.dp
            } else if (navBarStyle == NavBarStyle.DEFAULT) {
                10.dp
            } else if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                32.dp
            } else {
                navBarCornerRadiusDp
            }

            val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
            val safeFraction = effectiveFraction.coerceIn(0f, 1f)
            val fullScreenFraction = if (supportsSideDockedMode) {
                fullScreenFractionProvider().coerceIn(0f, 1f)
            } else {
                1f
            }
            val calculatedNormally =
                if (showPlayerContentArea) {
                    val expandedTarget = lerp(sideDockedCornerRadius, 0.dp, fullScreenFraction)
                    lerp(collapsedRadius, expandedTarget, safeFraction)
                } else {
                    if (!isPlayingState.value || !hasCurrentSongState.value) {
                        if (isNavBarHidden) {
                            32.dp
                        } else if (navBarStyle == NavBarStyle.DEFAULT) {
                            10.dp
                        } else {
                            navBarCornerRadiusDp
                        }
                    } else {
                        collapsedRadius
                    }
                }

            if (isNavBarHidden) {
                calculatedNormally
            } else if (currentSheetContentState == PlayerSheetState.COLLAPSED &&
                swipeDismissProgress > 0f &&
                showPlayerContentArea &&
                playerContentExpansionFraction.value < 0.01f
            ) {
                if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                    calculatedNormally
                } else if (navBarStyle == NavBarStyle.DEFAULT) {
                    lerp(10.dp, navBarCornerRadiusDp, swipeDismissProgress)
                } else {
                    val baseCollapsedRadius = if (isNavBarHidden) 32.dp else navBarCornerRadiusDp
                    lerp(baseCollapsedRadius, navBarCornerRadiusDp, swipeDismissProgress)
                }
            } else {
                calculatedNormally
            }
        }
    }

    val hasCustomAsymmetricHorizontalPadding =
        collapsedStartHorizontalPadding != collapsedStateHorizontalPadding ||
            collapsedEndHorizontalPadding != collapsedStateHorizontalPadding
    val actualCollapsedStartPadding = if (hasCustomAsymmetricHorizontalPadding) {
        collapsedStartHorizontalPadding
    } else if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        14.dp
    } else {
        collapsedStartHorizontalPadding
    }
    val actualCollapsedEndPadding = if (hasCustomAsymmetricHorizontalPadding) {
        collapsedEndHorizontalPadding
    } else if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        14.dp
    } else {
        collapsedEndHorizontalPadding
    }
    val collapsedStartHorizontalPaddingPx = remember(actualCollapsedStartPadding, density) {
        with(density) { actualCollapsedStartPadding.toPx() }
    }
    val collapsedEndHorizontalPaddingPx = remember(actualCollapsedEndPadding, density) {
        with(density) { actualCollapsedEndPadding.toPx() }
    }

    // Draw-phase lambda providers for horizontal padding — read inside graphicsLayer/layout to avoid
    // per-frame recomposition. On tablets, start/end padding smoothly expand from the constrained
    // content-centered pill out to either the right-docked phone card (SIDE_DOCKED) or full window width (FULLSCREEN).
    val currentHorizontalPaddingStartPxProvider: () -> Float = remember(
        showPlayerContentArea,
        collapsedStartHorizontalPaddingPx,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        supportsSideDockedMode,
        containerWidthPx,
        sideDockedWidthPx,
        sideDockedEndMarginPx,
        fullScreenFractionProvider
    ) {
        {
            if (showPlayerContentArea) {
                val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
                val safeFraction = effectiveFraction.coerceIn(0f, 1f)
                val expandedStartPx = if (!supportsSideDockedMode || containerWidthPx <= 0f) {
                    0f
                } else {
                    val fullScreenFraction = fullScreenFractionProvider().coerceIn(0f, 1f)
                    val dockedStartPx = (containerWidthPx - sideDockedWidthPx - sideDockedEndMarginPx)
                        .coerceAtLeast(sideDockedEndMarginPx)
                    androidx.compose.ui.util.lerp(dockedStartPx, 0f, fullScreenFraction)
                }
                androidx.compose.ui.util.lerp(collapsedStartHorizontalPaddingPx, expandedStartPx, safeFraction)
            } else {
                collapsedStartHorizontalPaddingPx
            }
        }
    }

    val currentHorizontalPaddingEndPxProvider: () -> Float = remember(
        showPlayerContentArea,
        collapsedEndHorizontalPaddingPx,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        supportsSideDockedMode,
        sideDockedEndMarginPx,
        fullScreenFractionProvider
    ) {
        {
            if (showPlayerContentArea) {
                val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
                val safeFraction = effectiveFraction.coerceIn(0f, 1f)
                val expandedEndPx = if (!supportsSideDockedMode) {
                    0f
                } else {
                    val fullScreenFraction = fullScreenFractionProvider().coerceIn(0f, 1f)
                    androidx.compose.ui.util.lerp(sideDockedEndMarginPx, 0f, fullScreenFraction)
                }
                androidx.compose.ui.util.lerp(collapsedEndHorizontalPaddingPx, expandedEndPx, safeFraction)
            } else {
                collapsedEndHorizontalPaddingPx
            }
        }
    }

    return SheetVisualState(
        currentBottomPadding = currentBottomPadding,
        baseBottomPadding = baseBottomPadding,
        playerContentAreaHeightPxProvider = playerContentAreaHeightPxProvider,
        visualSheetTranslationYProvider = visualSheetTranslationYProvider,
        overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
        playerContentActualBottomRadiusProvider = playerContentActualBottomRadiusProvider,
        currentHorizontalPaddingStartPxProvider = currentHorizontalPaddingStartPxProvider,
        currentHorizontalPaddingEndPxProvider = currentHorizontalPaddingEndPxProvider,
        targetExpandedWidthPxProvider = targetExpandedWidthPxProvider,
        targetExpandedHeightPxProvider = targetExpandedHeightPxProvider
    )
}
