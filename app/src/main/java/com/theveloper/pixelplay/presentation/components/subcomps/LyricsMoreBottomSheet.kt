package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import com.theveloper.pixelplay.presentation.components.player.PlayerCardOverlayDialog
import com.theveloper.pixelplay.presentation.components.player.PlayerCardOverlaySheet
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.Abc
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LowPriority
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.presentation.components.ToggleSegmentButton
import com.theveloper.pixelplay.presentation.components.player.BottomToggleRow
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsMoreBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    lyrics: Lyrics?,
    showSyncedLyrics: Boolean,
    isSyncControlsVisible: Boolean,
    onSaveLyricsAsLrc: () -> Unit,
    onResetImportedLyrics: () -> Unit,
    onTranslateViaAi: () -> Unit,
    onToggleSyncControls: () -> Unit,
    onOpenShareCardClick: (() -> Unit)? = null,
    onOpenProvidersChainClick: (() -> Unit)? = null,
    isImmersiveTemporarilyDisabled: Boolean,
    onSetImmersiveTemporarilyDisabled: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    lyricsAlignment: String,
    onLyricsAlignmentChange: (String) -> Unit,
    hasTranslatedLyrics: Boolean,
    hasRomanizedLyrics: Boolean,
    showTranslation: Boolean,
    showRomanization: Boolean,
    onShowTranslationChange: (Boolean) -> Unit,
    onShowRomanizationChange: (Boolean) -> Unit,
    immersiveLyricsEnabled: Boolean,
    // BottomToggleRow params
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    // Colors
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onAccentColor: Color = MaterialTheme.colorScheme.onPrimary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
    onTertiaryColor: Color = MaterialTheme.colorScheme.onTertiary,
    isContainedInCard: Boolean = false
) {
    val navigationBarsPadding = if (isContainedInCard) {
        0.dp
    } else {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }
    var showResetDialog by remember { mutableStateOf(false) }

    val sheetContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = if (isContainedInCard) 16.dp else 24.dp + navigationBarsPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // No Title - "Expressive" relies on visual grouping

            val itemBackgroundColor = contentColor.copy(alpha = 0.08f)

            // Lyrics Actions Group
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier
                        .padding(start = 6.dp, bottom = 6.dp),
                    text = stringResource(R.string.lyrics_title),
                    color = accentColor,
                    style = MaterialTheme.typography.bodyLargeEmphasized
                )
                 // Save lyrics to .lrc
                if (lyrics != null) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.lyrics_save_title)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.outline_save_24),
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                            .background(itemBackgroundColor)
                            .clickable {
                                onDismissRequest()
                                onSaveLyricsAsLrc()
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = contentColor,
                            leadingIconColor = contentColor
                        )
                    )
                }

                // Translate via AI
                if (lyrics != null) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.lyrics_translate_via_ai)) },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Translate,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(itemBackgroundColor)
                            .clickable {
                                onDismissRequest()
                                onTranslateViaAi()
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = contentColor,
                            leadingIconColor = contentColor
                        )
                    )
                }

                // Share up to 5 lines as a card
                if (lyrics != null && onOpenShareCardClick != null) {
                    ListItem(
                        headlineContent = { Text("Chia sẻ thẻ lời bài hát (Tối đa 5 câu)") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(itemBackgroundColor)
                            .clickable {
                                onDismissRequest()
                                onOpenShareCardClick()
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = contentColor,
                            leadingIconColor = contentColor
                        )
                    )
                }

                // Reorderable chain of lyrics providers (Zarz-LRC, Apple Music, etc.)
                if (onOpenProvidersChainClick != null) {
                    ListItem(
                        headlineContent = { Text("Nguồn lời bài hát & Thứ tự ưu tiên (Zarz-LRC...)") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.LowPriority,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                if (lyrics != null) RoundedCornerShape(8.dp)
                                else RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                            )
                            .background(itemBackgroundColor)
                            .clickable {
                                onDismissRequest()
                                onOpenProvidersChainClick()
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = contentColor,
                            leadingIconColor = contentColor
                        )
                    )
                }

                // Reset imported lyrics
                val resetShape = if (lyrics != null || onOpenProvidersChainClick != null) {
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                } else {
                    RoundedCornerShape(24.dp)
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.lyrics_reset_imported)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.outline_restart_alt_24),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(resetShape)
                        .background(itemBackgroundColor)
                        .clickable {
                            showResetDialog = true
                        },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = contentColor,
                        leadingIconColor = contentColor
                    )
                )
            }

            if (showResetDialog && !isContainedInCard) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text(stringResource(R.string.lyrics_reset_dialog_title)) },
                    text = { Text(stringResource(R.string.lyrics_reset_dialog_message)) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showResetDialog = false
                                onDismissRequest()
                                onResetImportedLyrics()
                            }
                        ) {
                            Text(stringResource(R.string.common_reset), color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { showResetDialog = false }
                        ) {
                            Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            }

            // Appearance Group
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier
                        .padding(start = 6.dp, bottom = 6.dp),
                    text = stringResource(R.string.lyrics_appearance_section),
                    color = accentColor,
                    style = MaterialTheme.typography.bodyLargeEmphasized
                 )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(itemBackgroundColor)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.lyrics_appearance_alignment),
                        color = contentColor,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToggleSegmentButton(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            active = lyricsAlignment == "left",
                            activeColor = accentColor,
                            inactiveColor = containerColor,
                            activeContentColor = onAccentColor,
                            inactiveContentColor = contentColor.copy(alpha = 0.78f),
                            activeCornerRadius = 50.dp,
                            onClick = { onLyricsAlignmentChange("left") },
                            imageVector = Icons.AutoMirrored.Rounded.FormatAlignLeft,
                            contentDesc = stringResource(R.string.lyrics_appearance_align_left)
                        )

                        ToggleSegmentButton(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            active = lyricsAlignment == "center",
                            activeColor = accentColor,
                            inactiveColor = containerColor,
                            activeContentColor = onAccentColor,
                            inactiveContentColor = contentColor.copy(alpha = 0.78f),
                            activeCornerRadius = 50.dp,
                            onClick = { onLyricsAlignmentChange("center") },
                            imageVector = Icons.Rounded.FormatAlignCenter,
                            contentDesc = stringResource(R.string.lyrics_appearance_align_center)
                        )

                        ToggleSegmentButton(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            active = lyricsAlignment == "right",
                            activeColor = accentColor,
                            inactiveColor = containerColor,
                            activeContentColor = onAccentColor,
                            inactiveContentColor = contentColor.copy(alpha = 0.78f),
                            activeCornerRadius = 50.dp,
                            onClick = { onLyricsAlignmentChange("right") },
                            imageVector = Icons.AutoMirrored.Rounded.FormatAlignRight,
                            contentDesc = stringResource(R.string.lyrics_appearance_align_right)
                        )
                    }
                }
            }

            // Control Settings Group
            val isSyncVisible = showSyncedLyrics
            val isRomanizationVisible = hasRomanizedLyrics
            val isTranslationVisible = hasTranslatedLyrics
            val isImmersiveVisible = showSyncedLyrics && immersiveLyricsEnabled
            val isKeepScreenOnVisible = true

            if (isSyncVisible || isRomanizationVisible || isTranslationVisible || isKeepScreenOnVisible) {
                val visibleControlKeys = buildList {
                    if (isSyncVisible) add("sync")
                    if (isRomanizationVisible) add("romanization")
                    if (isTranslationVisible) add("translation")
                    if (isImmersiveVisible) add("immersive")
                    if (isKeepScreenOnVisible) add("keep_screen_on")
                }
                fun controlItemShape(key: String): RoundedCornerShape {
                    val isFirst = visibleControlKeys.firstOrNull() == key
                    val isLast = visibleControlKeys.lastOrNull() == key
                    return RoundedCornerShape(
                        topStart = if (isFirst) 18.dp else 8.dp,
                        topEnd = if (isFirst) 18.dp else 8.dp,
                        bottomStart = if (isLast) 24.dp else 8.dp,
                        bottomEnd = if (isLast) 24.dp else 8.dp
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        modifier = Modifier
                            .padding(start = 6.dp, bottom = 6.dp),
                        text = stringResource(R.string.lyrics_controls_section),
                        color = accentColor,
                        style = MaterialTheme.typography.bodyLargeEmphasized
                    )

                    if (isSyncVisible) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    if (isSyncControlsVisible) {
                                        stringResource(R.string.lyrics_controls_hide_sync)
                                    } else {
                                        stringResource(R.string.lyrics_controls_adjust_sync)
                                    }
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(controlItemShape("sync"))
                                .background(itemBackgroundColor)
                                .clickable {
                                    onDismissRequest()
                                    onToggleSyncControls()
                                },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    if (isRomanizationVisible) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lyrics_controls_show_romanization)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Abc,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = showRomanization,
                                    onCheckedChange = onShowRomanizationChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(controlItemShape("romanization"))
                                .background(itemBackgroundColor)
                                .clickable { onShowRomanizationChange(!showRomanization) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    if (isTranslationVisible) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lyrics_controls_show_translations)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Translate,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = showTranslation,
                                    onCheckedChange = onShowTranslationChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(controlItemShape("translation"))
                                .background(itemBackgroundColor)
                                .clickable { onShowTranslationChange(!showTranslation) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    // Immersive Mode Toggle
                    if (isImmersiveVisible) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lyrics_controls_disable_immersive_once)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.VisibilityOff,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    modifier = Modifier,
                                    checked = isImmersiveTemporarilyDisabled,
                                    onCheckedChange = {
                                        onSetImmersiveTemporarilyDisabled(it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(controlItemShape("immersive"))
                                .background(itemBackgroundColor)
                                .clickable {
                                    onSetImmersiveTemporarilyDisabled(!isImmersiveTemporarilyDisabled)
                                },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    // Keep Screen On Toggle
                    if (isKeepScreenOnVisible) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lyrics_controls_keep_screen_on)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.BrightnessHigh,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = keepScreenOn,
                                    onCheckedChange = onKeepScreenOnChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(controlItemShape("keep_screen_on"))
                                .background(itemBackgroundColor)
                                .clickable { onKeepScreenOnChange(!keepScreenOn) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Playback Options
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    //.background(contentColor.copy(alpha = 0.08f))
                    .padding(vertical = 0.dp, horizontal = 0.dp)
            ) {
                BottomToggleRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isContainedInCard) 64.dp else 74.dp)
                        .padding(horizontal = if (isContainedInCard) 8.dp else 20.dp),
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    isFavoriteProvider = isFavoriteProvider,
                    onShuffleToggle = onShuffleToggle,
                    onRepeatToggle = onRepeatToggle,
                    onFavoriteToggle = onFavoriteToggle
                )
            }
        }
    }

    if (isContainedInCard) {
        Box(modifier = Modifier.fillMaxSize()) {
            PlayerCardOverlaySheet(
                onDismissRequest = onDismissRequest,
                containerColor = containerColor,
                contentColor = contentColor
            ) {
                sheetContent()
            }

            if (showResetDialog) {
                PlayerCardOverlayDialog(
                    onDismissRequest = { showResetDialog = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = stringResource(R.string.lyrics_reset_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.lyrics_reset_dialog_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        androidx.compose.material3.TextButton(
                            onClick = { showResetDialog = false }
                        ) {
                            Text(
                                stringResource(R.string.common_cancel),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showResetDialog = false
                                onDismissRequest()
                                onResetImportedLyrics()
                            }
                        ) {
                            Text(
                                stringResource(R.string.common_reset),
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = containerColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            contentWindowInsets = { WindowInsets(top = 0, bottom = 0) }
        ) {
            sheetContent()
        }
    }
}
