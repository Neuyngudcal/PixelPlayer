package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayWindowInfo
import com.theveloper.pixelplay.ui.theme.PixelPlayGroupShapes

/**
 * Indicates whether the current Composable subtree renders inside a floating Tablet Pop-up Pane.
 * Child chrome (top bars, headers, status-bar stylers) uses this to omit duplicate system
 * status-bar insets and avoid overriding the outer desk's status-bar icon styling.
 */
val LocalInsideFloatingPane = staticCompositionLocalOf { false }

/**
 * Shared outer-margin and corner-radius tokens for Tablet ("Unified Desk + Floating Pop-up Panes")
 * layouts so every screen aligns pixel-for-pixel with Settings Detail Panes and the Side-Docked
 * Now Playing card.
 */
object TabletPaneTokens {
    val StartMargin: Dp = 8.dp
    val EndMargin: Dp = 16.dp
    val VerticalMargin: Dp = 12.dp
    val PaneGap: Dp = 16.dp
    val CornerRadius: Dp = PixelPlayGroupShapes.PaneContainerRadius
}

fun resolveTabletPaneTopMargin(statusBarInset: Dp): Dp =
    (statusBarInset.coerceAtLeast(0.dp) + TabletPaneTokens.VerticalMargin)
        .coerceAtLeast(TabletPaneTokens.VerticalMargin)

fun resolveTabletPaneBottomMargin(navBarInset: Dp): Dp =
    (navBarInset.coerceAtLeast(0.dp) + TabletPaneTokens.VerticalMargin)
        .coerceAtLeast(TabletPaneTokens.VerticalMargin)

/**
 * Wraps screen content in a `28.dp` rounded floating pop-up card ([containerColor]) when
 * [enabled] is true (defaulting to Tablet / Navigation Rail mode), leaving Compact phone screens
 * edge-to-edge without wasted horizontal/vertical margins.
 */
@Composable
fun AdaptiveFloatingPane(
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalPixelPlayWindowInfo.current.useNavigationRail,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentColor: Color = contentColorFor(containerColor),
    applyTopMargin: Boolean = true,
    applyBottomMargin: Boolean = true,
    startMargin: Dp = TabletPaneTokens.StartMargin,
    endMargin: Dp = TabletPaneTokens.EndMargin,
    content: @Composable BoxScope.() -> Unit
) {
    if (!enabled) {
        Box(
            modifier = modifier.fillMaxSize(),
            content = content
        )
        return
    }

    val topPadding = if (applyTopMargin) {
        resolveTabletPaneTopMargin(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    } else {
        0.dp
    }
    val bottomPadding = if (applyBottomMargin) {
        resolveTabletPaneBottomMargin(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
    } else {
        0.dp
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = startMargin,
                top = topPadding,
                end = endMargin,
                bottom = bottomPadding
            ),
        shape = RoundedCornerShape(TabletPaneTokens.CornerRadius),
        color = containerColor,
        contentColor = contentColor
    ) {
        CompositionLocalProvider(LocalInsideFloatingPane provides true) {
            Box(
                modifier = Modifier.fillMaxSize(),
                content = content
            )
        }
    }
}
