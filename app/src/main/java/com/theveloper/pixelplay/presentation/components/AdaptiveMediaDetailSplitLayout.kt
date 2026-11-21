package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayWindowInfo
import com.theveloper.pixelplay.ui.theme.LocalSpacing

/**
 * Reusable 2-column Tablet Detail Split layout for Album, Artist, Playlist, and Genre detail screens
 * on Expanded / Large / ExtraLarge screens (e.g., Galaxy Tab S10 Ultra 14.6").
 *
 * Left pane: sticky `28.dp` rounded Material 3 hero pop-up card with aspect-locked artwork.
 * Right pane: independently scrollable `28.dp` rounded tracklist pop-up card.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AdaptiveMediaDetailSplitLayout(
    title: String,
    subtitle: String,
    onBackClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
    onShuffleClick: (() -> Unit)? = null,
    hasPlayableItems: Boolean = true,
    heroContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    heroContentColor: Color = MaterialTheme.colorScheme.onSurface,
    listContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    topActions: @Composable RowScope.() -> Unit = {},
    artwork: @Composable BoxScope.(artSize: Dp) -> Unit,
    extraHeroContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    listPane: @Composable BoxScope.() -> Unit
) {
    val windowInfo = LocalPixelPlayWindowInfo.current
    val spacing = LocalSpacing.current
    val topMargin = resolveTabletPaneTopMargin(
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    )
    val bottomMargin = resolveTabletPaneBottomMargin(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val heroWidth = minOf(windowInfo.detailHeroPaneWidth, maxWidth * 0.42f)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = TabletPaneTokens.StartMargin,
                    top = topMargin,
                    end = TabletPaneTokens.EndMargin,
                    bottom = bottomMargin
                ),
            horizontalArrangement = Arrangement.spacedBy(TabletPaneTokens.PaneGap)
        ) {
            Surface(
                modifier = Modifier
                    .width(heroWidth)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(TabletPaneTokens.CornerRadius),
                color = heroContainerColor,
                contentColor = heroContentColor
            ) {
                CompositionLocalProvider(LocalInsideFloatingPane provides true) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            FilledIconButton(
                                onClick = onBackClick,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.common_back)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                content = topActions
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.xl))

                        // Strictly bound square artwork with minOf(maxWidth, maxHeight) per AGENTS.md Step 1
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val artSide = minOf(maxWidth, maxHeight, 240.dp)
                            Box(
                                modifier = Modifier.size(artSide),
                                contentAlignment = Alignment.Center
                            ) {
                                artwork(artSide)
                            }
                        }

                        Spacer(modifier = Modifier.height(spacing.xl))

                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = heroContentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )

                        if (subtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(spacing.xs))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = heroContentColor.copy(alpha = 0.78f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }

                        if (onPlayClick != null || onShuffleClick != null) {
                            Spacer(modifier = Modifier.height(spacing.xl))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                            ) {
                                if (onPlayClick != null) {
                                    Button(
                                        onClick = onPlayClick,
                                        enabled = hasPlayableItems,
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = stringResource(R.string.common_play),
                                            modifier = Modifier.size(ButtonDefaults.IconSize)
                                        )
                                        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                                        Text(
                                            text = stringResource(R.string.common_play),
                                            style = MaterialTheme.typography.labelLargeEmphasized,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (onShuffleClick != null) {
                                    FilledTonalButton(
                                        onClick = onShuffleClick,
                                        enabled = hasPlayableItems,
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Shuffle,
                                            contentDescription = stringResource(R.string.common_shuffle),
                                            modifier = Modifier.size(ButtonDefaults.IconSize)
                                        )
                                        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                                        Text(
                                            text = stringResource(R.string.common_shuffle),
                                            style = MaterialTheme.typography.labelLargeEmphasized,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        extraHeroContent()

                        Spacer(modifier = Modifier.height(spacing.lg))
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(TabletPaneTokens.CornerRadius),
                color = listContainerColor,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                CompositionLocalProvider(LocalInsideFloatingPane provides true) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.md, vertical = spacing.sm),
                        content = listPane
                    )
                }
            }
        }
    }
}
