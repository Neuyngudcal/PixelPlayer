package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.ToggleSegmentButton
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun BottomToggleRow(
    modifier: Modifier,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    isShuffleTransitionInProgress: Boolean = false,
    showLyricsAndQueueToggles: Boolean = false,
    activeSidePanel: NowPlayingSidePanel = NowPlayingSidePanel.NONE,
    onLyricsClick: () -> Unit = {},
    onQueueClick: () -> Unit = {},
    activeColorMain: Color = MaterialTheme.colorScheme.primaryFixed,
    activeColorSecondary: Color = MaterialTheme.colorScheme.secondaryFixed,
    activeColorTertiary: Color = MaterialTheme.colorScheme.tertiaryFixed,
    onActiveColorMain: Color = MaterialTheme.colorScheme.onPrimaryFixed,
    onActiveColorSecondary: Color = MaterialTheme.colorScheme.onSecondaryFixed,
    onActiveColorTertiary: Color = MaterialTheme.colorScheme.onTertiaryFixed,
    sidePanelActiveColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    onSidePanelActiveColor: Color = MaterialTheme.colorScheme.primaryContainer,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    inactiveContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    innerPadding: Dp = 8.dp,
    itemSpacing: Dp = 8.dp
) {
    val isFavorite = isFavoriteProvider()
    val rowCorners = 60.dp
    val containerShape = remember(rowCorners) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = rowCorners,
            smoothnessAsPercentTL = 60,
            cornerRadiusTR = rowCorners,
            smoothnessAsPercentTR = 60,
            cornerRadiusBL = rowCorners,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = rowCorners,
            smoothnessAsPercentBR = 60
        )
    }

    Box(
        modifier = modifier.background(
            color = containerColor,
            shape = containerShape
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .clip(containerShape)
                .background(Color.Transparent),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val commonModifier = Modifier.weight(1f)

            ToggleSegmentButton(
                modifier = commonModifier,
                active = isShuffleEnabled,
                enabled = !isShuffleTransitionInProgress,
                activeColor = activeColorMain,
                activeCornerRadius = rowCorners,
                activeContentColor = onActiveColorMain,
                inactiveColor = inactiveColor,
                inactiveContentColor = inactiveContentColor,
                onClick = onShuffleToggle,
                iconId = R.drawable.rounded_shuffle_24,
                contentDesc = stringResource(R.string.queue_cd_toggle_shuffle_action)
            )
            val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
            val repeatIcon = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.rounded_repeat_one_24
                Player.REPEAT_MODE_ALL -> R.drawable.rounded_repeat_24
                else -> R.drawable.rounded_repeat_24
            }
            ToggleSegmentButton(
                modifier = commonModifier,
                active = repeatActive,
                activeColor = activeColorSecondary,
                activeCornerRadius = rowCorners,
                activeContentColor = onActiveColorSecondary,
                inactiveColor = inactiveColor,
                inactiveContentColor = inactiveContentColor,
                onClick = onRepeatToggle,
                iconId = repeatIcon,
                contentDesc = stringResource(R.string.queue_cd_toggle_repeat_action)
            )
            ToggleSegmentButton(
                modifier = commonModifier,
                active = isFavorite,
                activeColor = activeColorTertiary,
                activeCornerRadius = rowCorners,
                activeContentColor = onActiveColorTertiary,
                inactiveColor = inactiveColor,
                inactiveContentColor = inactiveContentColor,
                onClick = onFavoriteToggle,
                iconId = if (isFavorite) R.drawable.round_favorite_24 else R.drawable.rounded_favorite_24,
                contentDesc = stringResource(R.string.player_cd_favorite)
            )

            if (showLyricsAndQueueToggles) {
                ToggleSegmentButton(
                    modifier = commonModifier,
                    active = activeSidePanel == NowPlayingSidePanel.LYRICS,
                    activeColor = sidePanelActiveColor,
                    activeCornerRadius = rowCorners,
                    activeContentColor = onSidePanelActiveColor,
                    inactiveColor = inactiveColor,
                    inactiveContentColor = inactiveContentColor,
                    onClick = onLyricsClick,
                    iconId = R.drawable.rounded_lyrics_24,
                    contentDesc = stringResource(R.string.player_panel_lyrics)
                )
                ToggleSegmentButton(
                    modifier = commonModifier,
                    active = activeSidePanel == NowPlayingSidePanel.QUEUE,
                    activeColor = sidePanelActiveColor,
                    activeCornerRadius = rowCorners,
                    activeContentColor = onSidePanelActiveColor,
                    inactiveColor = inactiveColor,
                    inactiveContentColor = inactiveContentColor,
                    onClick = onQueueClick,
                    iconId = R.drawable.rounded_queue_music_24,
                    contentDesc = stringResource(R.string.player_panel_up_next)
                )
            }
        }
    }
}
