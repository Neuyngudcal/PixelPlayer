package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.ai.GeminiModel
import com.theveloper.pixelplay.data.worker.SyncProgress
import com.theveloper.pixelplay.presentation.viewmodel.LyricsRefreshProgress
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.graphics.Shape
import com.theveloper.pixelplay.ui.theme.LocalSpacing
import com.theveloper.pixelplay.ui.theme.PixelPlayGroupShapes
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.sp
import androidx.core.view.HapticFeedbackConstantsCompat
import com.theveloper.pixelplay.presentation.components.subcomps.TightWrapText
import com.theveloper.pixelplay.presentation.utils.LocalAppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.performAppCompatHapticFeedback

/**
 * Standardized Material 3 Expressive section header for Settings screens.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(
            start = spacing.lg,
            top = spacing.xl,
            bottom = spacing.sm
        )
    )
}

/**
 * Unified rounded group container: clips outer corners to 20dp while stacking inner items with 2dp
 * separation and 4dp inner corners (matching Samsung grouped structure with M3 Expressive tokens).
 */
@Composable
fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PixelPlayGroupShapes.GroupOuterRadius))
            .background(Color.Transparent),
        verticalArrangement = Arrangement.spacedBy(spacing.groupItemGap),
        content = content
    )
}

@Composable
fun SettingsSection(title: String, icon: @Composable () -> Unit, content: @Composable () -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.xs)
    ) {
        SettingsSectionHeader(
            title = title,
            modifier = Modifier.padding(top = spacing.md)
        )
        content()
    }
}

@Composable
fun SettingsItem(
        title: String,
        subtitle: String,
        leadingIcon: @Composable () -> Unit,
        trailingIcon: @Composable () -> Unit = {},
        shape: Shape = RoundedCornerShape(PixelPlayGroupShapes.GroupInnerRadius),
        onClick: () -> Unit
) {
    val spacing = LocalSpacing.current
    Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(onClick = onClick)
    ) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.listRowMinHeight)
                    .padding(horizontal = spacing.listRowHorizontal, vertical = spacing.listRowVertical)
        ) {
            Box(
                    modifier = Modifier.padding(end = spacing.iconTextGap).size(24.dp),
                    contentAlignment = Alignment.Center
            ) { leadingIcon() }

            Column(
                    modifier = Modifier.weight(1f).padding(end = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.titleSubtitleGap)
            ) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                trailingIcon()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SwitchSettingItem(
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        leadingIcon: @Composable (() -> Unit)? = null,
        enabled: Boolean = true,
        shape: Shape = RoundedCornerShape(PixelPlayGroupShapes.GroupInnerRadius)
) {
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    val spacing = LocalSpacing.current
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = shape,
            modifier = Modifier.fillMaxWidth().clip(shape)
    ) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.listRowMinHeight)
                    .padding(horizontal = spacing.listRowHorizontal, vertical = spacing.listRowVertical),
                horizontalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            if (leadingIcon != null) {
                Box(
                        modifier = Modifier.padding(end = spacing.xs).size(24.dp),
                        contentAlignment = Alignment.Center
                ) { leadingIcon() }
            }

            Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.titleSubtitleGap)
            ) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color =
                                if (enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                                if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = { newValue ->
                    if (enabled) {
                        performAppCompatHapticFeedback(
                            view,
                            appHapticsConfig,
                            HapticFeedbackConstantsCompat.GESTURE_START
                        )
                        onCheckedChange(newValue)
                    }
                },
                enabled = enabled,
                thumbContent = {
                    AnimatedContent(
                        targetState = checked,
                        transitionSpec = {
                            fadeIn(effectsSpec) togetherWith fadeOut(effectsSpec)
                        },
                        label = "switch_thumb_icon"
                    ) { isChecked ->
                        Icon(
                            imageVector = if (isChecked) Icons.Rounded.Check else Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedIconColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemeSelectorItem(
        label: String,
        description: String,
        options: Map<String, String>,
        selectedKey: String,
        onSelectionChanged: (String) -> Unit,
        leadingIcon: @Composable () -> Unit,
        shape: Shape = RoundedCornerShape(PixelPlayGroupShapes.GroupInnerRadius)
) {
    var showSheet by remember { mutableStateOf(false) }
    val selectedOption = options[selectedKey] ?: selectedKey
    val spacing = LocalSpacing.current

    Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = shape,
            modifier =
                    Modifier.fillMaxWidth().clip(shape).clickable {
                        showSheet = true
                    }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.listRowMinHeight)
                .padding(horizontal = spacing.listRowHorizontal, vertical = spacing.listRowVertical)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.padding(end = spacing.iconTextGap).size(24.dp),
                        contentAlignment = Alignment.Center
                ) { leadingIcon() }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.titleSubtitleGap)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(spacing.sm))

                    // Selected Value Badge
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text(
                             text = selectedOption,
                             style = MaterialTheme.typography.labelMediumEmphasized,
                             color = MaterialTheme.colorScheme.primary,
                             modifier = Modifier.padding(horizontal = spacing.md, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.padding(bottom = spacing.xxl)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    modifier = Modifier.padding(horizontal = spacing.xxl, vertical = spacing.lg)
                )

                LazyColumn(
                    modifier = Modifier
                        .padding(horizontal = spacing.lg)
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    items(options.entries.toList()) { (key, optionLabel) ->
                        val isSelected = key == selectedKey
                        val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

                        Surface(
                            onClick = {
                                onSelectionChanged(key)
                                showSheet = false
                            },
                            shape = RoundedCornerShape(PixelPlayGroupShapes.GroupOuterRadius),
                            color = containerColor,
                            modifier = Modifier.fillMaxWidth().heightIn(min = spacing.listRowMinHeight)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = spacing.xl, vertical = spacing.lg),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = optionLabel,
                                    style = if (isSelected) {
                                        MaterialTheme.typography.titleMediumEmphasized
                                    } else {
                                        MaterialTheme.typography.titleMedium
                                    },
                                    color = contentColor,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = stringResource(R.string.common_selected),
                                        tint = contentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveSettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(PixelPlayGroupShapes.GroupOuterRadius))
            .background(Color.Transparent),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableModelSelector(
    label: String,
    description: String,
    models: List<GeminiModel>,
    selectedModelName: String,
    onModelSelected: (String) -> Unit,
    leadingIcon: @Composable () -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedDisplayName = models.find { it.name == selectedModelName }?.displayName ?: selectedModelName

    val spacing = LocalSpacing.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(PixelPlayGroupShapes.GroupInnerRadius),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PixelPlayGroupShapes.GroupInnerRadius))
            .clickable { showSheet = true }
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = spacing.listRowHorizontal,
                vertical = spacing.listRowVertical
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = spacing.iconTextGap)
                        .size(24.dp),
                    contentAlignment = Alignment.Center
                ) { leadingIcon() }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.titleSubtitleGap)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(spacing.sm))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text(
                            text = selectedDisplayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = spacing.md, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                searchQuery = ""
            },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search models...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                val filteredModels = remember(models, searchQuery) {
                    if (searchQuery.isBlank()) models
                    else models.filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                            it.displayName.contains(searchQuery, ignoreCase = true)
                    }
                }

                Text(
                    text = "${filteredModels.size} model${if (filteredModels.size != 1) "s" else ""} available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .heightIn(max = 400.dp)
                ) {
                    items(filteredModels, key = { it.name }) { model ->
                        val isSelected = model.name == selectedModelName
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onModelSelected(model.name)
                                    showSheet = false
                                    searchQuery = ""
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = model.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SliderSettingsItem(
        label: String,
        value: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int,
        onValueChange: (Float) -> Unit,
        onValueChangeFinished: (() -> Unit)? = null,
        valueText: (Float) -> String
) {
    val spacing = LocalSpacing.current
    Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(PixelPlayGroupShapes.GroupInnerRadius),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PixelPlayGroupShapes.GroupInnerRadius))
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = spacing.listRowHorizontal,
                vertical = spacing.listRowVertical
            )
        ) {
            Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(spacing.sm))
                Text(
                        text = valueText(value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps
            )
        }
    }
}

@Composable
fun RefreshLibraryItem(
        isSyncing: Boolean,
        syncProgress: SyncProgress,
        activeOperationLabel: String? = null,
        onFullSync: () -> Unit,
        onRebuild: () -> Unit
) {
    val spacing = LocalSpacing.current
    val buttonLineHeight = MaterialTheme.typography.labelLarge.lineHeight
    Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(PixelPlayGroupShapes.GroupInnerRadius),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PixelPlayGroupShapes.GroupInnerRadius))
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = spacing.listRowHorizontal,
                vertical = spacing.listRowVertical
            )
        ) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                        modifier = Modifier.padding(end = spacing.iconTextGap).size(24.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Column(
                        modifier = Modifier.weight(1f).padding(end = spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.titleSubtitleGap)
                ) {
                    Text(
                            text = stringResource(R.string.settings_refresh_library_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                            text = stringResource(R.string.settings_refresh_library_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.md))

            // Full Rescan button
            FilledTonalButton(
                    onClick = onFullSync,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.sm))
                    TightWrapText(
                        text = stringResource(R.string.settings_action_full_rescan),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2,
                        lineHeight = buttonLineHeight,
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.sm))

            // Rebuild Database button - full width, destructive action
            OutlinedButton(
                    onClick = onRebuild,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.sm))
                    TightWrapText(
                        text = stringResource(R.string.settings_action_rebuild_database),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2,
                        lineHeight = buttonLineHeight,
                    )
                }
            }

            if (isSyncing) {
                Spacer(modifier = Modifier.height(12.dp))
                val phaseLabel = activeOperationLabel ?: syncPhaseLabel(syncProgress.phase)
                if (syncProgress.hasProgress) {
                    LinearProgressIndicator(
                            progress = { syncProgress.progress },
                            modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            text = stringResource(
                                R.string.settings_sync_progress_detailed,
                                phaseLabel,
                                (syncProgress.progress * 100).toInt(),
                                syncProgress.currentCount,
                                syncProgress.totalCount
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            text = stringResource(
                                R.string.settings_sync_progres_indeterminate,
                                phaseLabel
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun syncPhaseLabel(phase: SyncProgress.SyncPhase): String =
        stringResource(
                when (phase) {
                    SyncProgress.SyncPhase.IDLE -> R.string.settings_sync_phase_preparing
                    SyncProgress.SyncPhase.FETCHING_MEDIASTORE ->
                            R.string.settings_sync_phase_reading_mediastore
                    SyncProgress.SyncPhase.PROCESSING_FILES ->
                            R.string.settings_sync_phase_reading_processing_tracks
                    SyncProgress.SyncPhase.SAVING_TO_DATABASE ->
                            R.string.settings_sync_phase_saving_db
                    SyncProgress.SyncPhase.SCANNING_LRC -> R.string.settings_sync_phase_scanning_lrc
                    SyncProgress.SyncPhase.CLEANING_CACHE ->
                            R.string.settings_sync_phase_cleaning_cache
                    SyncProgress.SyncPhase.SYNCING_CLOUD ->
                            R.string.settings_sync_phase_syncing_cloud
                    SyncProgress.SyncPhase.COMPLETING -> R.string.settings_sync_phase_completing
                }
        )

@Composable
fun ActionSettingsItem(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val spacing = LocalSpacing.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(PixelPlayGroupShapes.GroupInnerRadius),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PixelPlayGroupShapes.GroupInnerRadius))
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = spacing.listRowHorizontal,
                vertical = spacing.listRowVertical
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.padding(end = spacing.iconTextGap).size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }

                Column(
                    modifier = Modifier.weight(1f).padding(end = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.titleSubtitleGap)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Action
            FilledTonalButton(
                onClick = onPrimaryAction,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(primaryActionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // Secondary Action (Optional)
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSecondaryAction,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(secondaryActionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun AiApiKeyItem(
    apiKey: String,
    onApiKeySave: (String) -> Unit,
    title: String,
    subtitle: String
) {
    var localApiKey by remember(apiKey) { mutableStateOf(apiKey) }
    val hasChanges = localApiKey != apiKey
    var showSaved by remember { mutableStateOf(false) }

    LaunchedEffect(showSaved) {
        if (showSaved) {
            kotlinx.coroutines.delay(2000)
            showSaved = false
        }
    }

    val spacing = LocalSpacing.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(PixelPlayGroupShapes.GroupInnerRadius),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PixelPlayGroupShapes.GroupInnerRadius))
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = spacing.listRowHorizontal,
                vertical = spacing.listRowVertical
            )
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = localApiKey,
                onValueChange = { localApiKey = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.settings_enter_api_key_placeholder)) },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        onApiKeySave(localApiKey)
                        showSaved = true
                    },
                    enabled = hasChanges
                ) {
                    Text(stringResource(R.string.common_save), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (showSaved) {
                    Text(
                        text = stringResource(R.string.common_saved),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AiSystemPromptItem(
    systemPrompt: String,
    defaultPrompt: String,
    onSystemPromptSave: (String) -> Unit,
    onReset: () -> Unit,
    title: String,
    subtitle: String
) {
    var localPrompt by remember(systemPrompt) { mutableStateOf(systemPrompt) }
    val hasChanges = localPrompt != systemPrompt
    val isDefault = systemPrompt == defaultPrompt
    var showSaved by remember { mutableStateOf(false) }
    val presets = listOf(
        stringResource(R.string.settings_preset_professional_curator_name) to
            stringResource(R.string.settings_preset_professional_curator_prompt),
        stringResource(R.string.settings_preset_creative_maverick_name) to
            stringResource(R.string.settings_preset_creative_maverick_prompt),
        stringResource(R.string.settings_preset_strict_librarian_name) to
            stringResource(R.string.settings_preset_strict_librarian_prompt),
        stringResource(R.string.settings_preset_atmospheric_guide_name) to
            stringResource(R.string.settings_preset_atmospheric_guide_prompt),
        stringResource(R.string.settings_preset_sonic_enthusiast_name) to
            stringResource(R.string.settings_preset_sonic_enthusiast_prompt),
        stringResource(R.string.settings_preset_energy_catalyst_name) to
            stringResource(R.string.settings_preset_energy_catalyst_prompt)
    )

    LaunchedEffect(showSaved) {
        if (showSaved) {
            kotlinx.coroutines.delay(2000)
            showSaved = false
        }
    }

    val spacing = LocalSpacing.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(PixelPlayGroupShapes.GroupInnerRadius),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PixelPlayGroupShapes.GroupInnerRadius))
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = spacing.listRowHorizontal,
                vertical = spacing.listRowVertical
            )
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_preset_prompts),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    OutlinedButton(
                        onClick = { 
                            localPrompt = preset.second
                        },
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Text(text = preset.first, maxLines = 1)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = localPrompt,
                onValueChange = { localPrompt = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp),
                placeholder = { Text(stringResource(R.string.settings_system_prompt_placeholder)) },
                minLines = 3,
                maxLines = 6
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        onSystemPromptSave(localPrompt)
                        showSaved = true
                    },
                    enabled = hasChanges
                ) {
                    Text(stringResource(R.string.common_save), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!isDefault) {
                    OutlinedButton(onClick = {
                        onReset()
                    }) {
                        Text(stringResource(R.string.common_reset), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (showSaved) {
                    Text(
                        text = stringResource(R.string.common_saved),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
