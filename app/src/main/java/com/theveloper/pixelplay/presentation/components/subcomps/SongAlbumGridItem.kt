package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.AutoScrollingText
import com.theveloper.pixelplay.presentation.components.ShimmerBox
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * 3:2 grid card for songs in the Library Songs "Album View" mode.
 *
 * Mirrors [AlbumGridItemRedesigned]'s artwork-tinted M3E presentation while keeping scroll
 * performance smooth via fast [AsyncImage] decoding, lazy selection/active animations,
 * and scroll-idle dynamic palette hydration.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongAlbumGridItem(
    song: Song,
    albumColorSchemePairFlow: StateFlow<ColorSchemePair?>,
    isPlaying: Boolean,
    isCurrentSong: Boolean = false,
    isLoading: Boolean = false,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    onLongPress: () -> Unit = {},
    onMoreOptionsClick: (Song) -> Unit,
    onClick: () -> Unit
) {
    if (isLoading) {
        val skeletonShape = remember { RoundedCornerShape(20.dp) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = skeletonShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 2f)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
        return
    }

    val albumColorSchemePair by albumColorSchemePairFlow.collectAsStateWithLifecycle()
    val systemIsDark = LocalPixelPlayDarkTheme.current
    val currentMaterialColorScheme = MaterialTheme.colorScheme

    val targetDesignColorScheme = remember(albumColorSchemePair, systemIsDark, currentMaterialColorScheme) {
        albumColorSchemePair?.let { pair ->
            if (systemIsDark) pair.dark else pair.light
        } ?: currentMaterialColorScheme
    }

    val targetGradientBaseColor = when {
        albumColorSchemePair != null -> targetDesignColorScheme.primaryContainer
        isCurrentSong -> currentMaterialColorScheme.primaryContainer
        else -> currentMaterialColorScheme.surfaceContainerHigh
    }
    val targetOnGradientColor = when {
        albumColorSchemePair != null -> targetDesignColorScheme.onPrimaryContainer
        isCurrentSong -> currentMaterialColorScheme.onPrimaryContainer
        else -> currentMaterialColorScheme.onSurface
    }

    var hasEverAnimatedState by remember { mutableStateOf(isCurrentSong || isSelected) }
    if (isCurrentSong || isSelected) {
        hasEverAnimatedState = true
    }

    val startedWithNullScheme = remember(song.albumArtUriString) {
        albumColorSchemePair == null
    }
    val shouldAnimateTheme = (!song.albumArtUriString.isNullOrBlank() && startedWithNullScheme) || hasEverAnimatedState

    val gradientBaseColor = if (shouldAnimateTheme) {
        animateColorAsState(
            targetValue = targetGradientBaseColor,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "songAlbumGridGradientBase"
        ).value
    } else {
        targetGradientBaseColor
    }
    val onGradientColor = if (shouldAnimateTheme) {
        animateColorAsState(
            targetValue = targetOnGradientColor,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "songAlbumGridOnGradient"
        ).value
    } else {
        targetOnGradientColor
    }

    val cardCornerRadius = if (hasEverAnimatedState) {
        animateDpAsState(
            targetValue = if (isCurrentSong) 26.dp else 20.dp,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "songAlbumGridCornerRadius"
        ).value
    } else {
        20.dp
    }
    val selectionScale = if (hasEverAnimatedState) {
        animateFloatAsState(
            targetValue = if (isSelected) 0.985f else 1f,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "songAlbumGridSelectionScale"
        ).value
    } else {
        1f
    }
    val selectionBorderWidth = if (hasEverAnimatedState) {
        animateDpAsState(
            targetValue = if (isSelected || isCurrentSong) 2.dp else 0.dp,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "songAlbumGridBorderWidth"
        ).value
    } else {
        0.dp
    }

    val cardShape = remember(cardCornerRadius) { RoundedCornerShape(cardCornerRadius) }
    val borderColor = if (isSelected) {
        currentMaterialColorScheme.primary
    } else {
        targetDesignColorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(selectionScale)
            .then(
                if (selectionBorderWidth > 0.dp) {
                    Modifier.border(
                        width = selectionBorderWidth,
                        color = borderColor,
                        shape = cardShape
                    )
                } else {
                    Modifier
                }
            )
            .clip(cardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = gradientBaseColor)
    ) {
        Box {
            Column(
                modifier = Modifier.background(
                    color = gradientBaseColor,
                    shape = cardShape
                )
            ) {
                Box(contentAlignment = Alignment.BottomStart) {
                    SmartImage(
                        model = song.albumArtUriString,
                        contentDescription = stringResource(R.string.common_album_art_for_title, song.title),
                        contentScale = ContentScale.Crop,
                        targetSize = Size(256, 256),
                        crossfadeDurationMillis = 150,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 2f)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 2f)
                            .background(
                                remember(gradientBaseColor) {
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, gradientBaseColor)
                                    )
                                }
                            )
                    )

                    if (isCurrentSong && !isSelectionMode) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp),
                            shape = CircleShape,
                            color = targetDesignColorScheme.primary
                        ) {
                            PlayingEqIcon(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .size(width = 16.dp, height = 14.dp),
                                color = targetDesignColorScheme.onPrimary,
                                isPlaying = isPlaying
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isCurrentSong && !isSelectionMode) {
                            AutoScrollingText(
                                text = song.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = onGradientColor
                                ),
                                gradientEdgeColor = gradientBaseColor
                            )
                        } else {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = onGradientColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.displayArtist,
                            style = MaterialTheme.typography.bodySmall,
                            color = onGradientColor.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!isSelectionMode) {
                        FilledIconButton(
                            onClick = { onMoreOptionsClick(song) },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = onGradientColor.copy(alpha = 0.12f),
                                contentColor = onGradientColor
                            ),
                            modifier = Modifier
                                .padding(start = 4.dp, end = 4.dp)
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.common_more_options_for, song.title),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (isSelectionMode && isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(28.dp)
                        .background(
                            color = currentMaterialColorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectionIndex?.toString() ?: "✓",
                        color = currentMaterialColorScheme.onPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
