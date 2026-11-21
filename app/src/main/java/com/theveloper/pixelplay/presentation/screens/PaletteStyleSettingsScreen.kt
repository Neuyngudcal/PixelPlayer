package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.data.preferences.ThemeContrastLevel
import com.theveloper.pixelplay.presentation.components.LocalInsideFloatingPane
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import com.theveloper.pixelplay.ui.theme.LocalSpacing
import com.theveloper.pixelplay.ui.theme.PixelPlayGroupShapes
import com.theveloper.pixelplay.ui.theme.generateColorSchemeFromSeed

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PaletteStyleSettingsScreen(
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    embeddedInPane: Boolean = false
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isDarkTheme = LocalPixelPlayDarkTheme.current
    val albumSchemePair by playerViewModel.currentAlbumArtColorSchemePair.collectAsStateWithLifecycle()

    val baseScheme = MaterialTheme.colorScheme
    val albumScheme = remember(albumSchemePair, isDarkTheme, baseScheme) {
        albumSchemePair?.let { pair -> if (isDarkTheme) pair.dark else pair.light } ?: baseScheme
    }

    val currentSong = stablePlayerState.currentSong
    val artUriString = currentSong?.albumArtUriString?.takeIf { it.isNotBlank() }
    val isMiniPlayerVisible = currentSong != null

    // Extract the true artwork seed color (never double-processed through albumScheme.primary)
    var extractedSeedColor by remember(artUriString) { mutableStateOf<Color?>(null) }
    LaunchedEffect(artUriString) {
        extractedSeedColor = if (artUriString != null) {
            settingsViewModel.getAlbumArtSeedColor(artUriString)
        } else {
            null
        }
    }

    val resolvedSeed = extractedSeedColor ?: baseScheme.primary

    var pendingStyle by remember { mutableStateOf(uiState.albumArtPaletteStyle) }
    var pendingContrastLevel by remember { mutableStateOf(uiState.themeContrastLevel) }
    LaunchedEffect(uiState.albumArtPaletteStyle) {
        pendingStyle = uiState.albumArtPaletteStyle
    }
    LaunchedEffect(uiState.themeContrastLevel) {
        pendingContrastLevel = uiState.themeContrastLevel
    }

    val effectivePreviewContrast = remember(pendingContrastLevel, uiState.systemContrastLevel) {
        pendingContrastLevel.resolve(uiState.systemContrastLevel)
    }

    // All 8 Google palette variants synthesize synchronously (< 0.15ms each) from the true seed.
    val styleSchemes = remember(resolvedSeed, isDarkTheme, effectivePreviewContrast) {
        AlbumArtPaletteStyle.entries.associateWith { style ->
            val pair = generateColorSchemeFromSeed(
                seedColor = resolvedSeed,
                paletteStyle = style,
                contrastLevel = effectivePreviewContrast
            )
            if (isDarkTheme) pair.dark else pair.light
        }
    }

    val hasPendingChanges =
        pendingStyle != uiState.albumArtPaletteStyle ||
            pendingContrastLevel != uiState.themeContrastLevel
    val previewScheme = styleSchemes[pendingStyle] ?: albumScheme
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottomPadding = bottomInset + if (isMiniPlayerVisible) MiniPlayerHeight + 12.dp else 16.dp

    val spacing = LocalSpacing.current
    val previewSurfaceColor = if (embeddedInPane) Color.Transparent else previewScheme.background

    val controlsCard: @Composable (Modifier) -> Unit = { cardModifier ->
        Surface(
            color = previewScheme.surfaceContainer,
            shape = RoundedCornerShape(PixelPlayGroupShapes.PaneContainerRadius),
            modifier = cardModifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.settings_palette_style_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = previewScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (embeddedInPane) {
                        FilledTonalButton(
                            onClick = {
                                settingsViewModel.setAlbumArtPaletteSettings(
                                    style = pendingStyle,
                                    contrastLevel = pendingContrastLevel
                                )
                            },
                            enabled = hasPendingChanges,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = previewScheme.secondaryContainer,
                                contentColor = previewScheme.onSecondaryContainer,
                                disabledContainerColor = previewScheme.surfaceContainerHigh,
                                disabledContentColor = previewScheme.onSurfaceVariant
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.common_apply),
                                style = MaterialTheme.typography.labelLargeEmphasized
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_palette_style_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = previewScheme.onSurfaceVariant
                )

                // 4x2 grid covering all 8 official Google Material Color Utilities variants
                val styleRows = remember { AlbumArtPaletteStyle.entries.chunked(4) }
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    styleRows.forEach { rowStyles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowStyles.forEach { style ->
                                PaletteSwatchSquare(
                                    scheme = styleSchemes[style] ?: previewScheme,
                                    selected = style == pendingStyle,
                                    onClick = { pendingStyle = style },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = albumArtPaletteStyleLabel(pendingStyle),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = previewScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = previewScheme.tertiaryContainer,
                            shape = RoundedCornerShape(PixelPlayGroupShapes.GroupOuterRadius)
                        )
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        text = albumArtPaletteStyleDescription(pendingStyle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = previewScheme.onTertiaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                PaletteContrastSelector(
                    scheme = previewScheme,
                    selectedLevel = pendingContrastLevel,
                    systemContrastLevel = uiState.systemContrastLevel,
                    hapticsEnabled = uiState.hapticsEnabled,
                    onSelect = { pendingContrastLevel = it }
                )
            }
        }
    }

    Scaffold(
        containerColor = previewSurfaceColor,
        contentWindowInsets = if (embeddedInPane) WindowInsets(0, 0, 0, 0) else WindowInsets.navigationBars,
        topBar = {
            if (!embeddedInPane) {
                PaletteStyleHeader(
                    scheme = previewScheme,
                    onBackClick = onBackClick,
                    onApplyClick = {
                        settingsViewModel.setAlbumArtPaletteSettings(
                            style = pendingStyle,
                            contrastLevel = pendingContrastLevel
                        )
                    },
                    applyEnabled = hasPendingChanges
                )
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val useTwoColumn = maxWidth >= 680.dp
            if (useTwoColumn) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xl),
                    verticalAlignment = Alignment.Top
                ) {
                    MiniFullPlayerSkeletonPreview(
                        scheme = previewScheme,
                        modifier = Modifier
                            .weight(0.42f)
                            .padding(bottom = contentBottomPadding)
                    )
                    Column(
                        modifier = Modifier
                            .weight(0.58f)
                            .padding(bottom = contentBottomPadding)
                    ) {
                        controlsCard(Modifier.fillMaxWidth())
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = spacing.screenHorizontal)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (embeddedInPane) {
                        Spacer(modifier = Modifier.height(spacing.xs))
                    }
                    MiniFullPlayerSkeletonPreview(
                        scheme = previewScheme,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                    controlsCard(Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(contentBottomPadding))
                }
            }
        }
    }
}

@Composable
private fun PaletteStyleHeader(
    scheme: ColorScheme,
    onBackClick: () -> Unit,
    onApplyClick: () -> Unit,
    applyEnabled: Boolean
) {
    val insideFloatingPane = LocalInsideFloatingPane.current
    Surface(color = if (insideFloatingPane) Color.Transparent else scheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!insideFloatingPane) Modifier.statusBarsPadding() else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onBackClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = scheme.surfaceContainerLow,
                    contentColor = scheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.common_close)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.settings_palette_screen_title),
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            FilledTonalButton(
                onClick = onApplyClick,
                enabled = applyEnabled,
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                    disabledContainerColor = scheme.surfaceContainerHigh,
                    disabledContentColor = scheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = stringResource(R.string.common_apply),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun MiniFullPlayerSkeletonPreview(
    scheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    val sizeFactor = 0.7f
    fun scaled(dp: Dp): Dp = (dp.value * sizeFactor).dp
    val cardWidthFraction = 0.72f * sizeFactor

    val placeholderColor = scheme.onPrimaryContainer.copy(alpha = 0.12f)
    val placeholderOnColor = scheme.onPrimaryContainer.copy(alpha = 0.24f)
    val chipColor = scheme.onPrimary.copy(alpha = 0.8f)
    val topBarButtonColor = scheme.onPrimary.copy(alpha = 0.7f)
    val inactiveTrackColor = scheme.onPrimaryContainer.copy(alpha = 0.2f)
    val toggleRowColor = scheme.surfaceContainerLowest.copy(alpha = 0.7f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            color = scheme.primaryContainer,
            shape = RoundedCornerShape(scaled(34.dp)),
            modifier = Modifier.fillMaxWidth(cardWidthFraction)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(scaled(14.dp)),
                verticalArrangement = Arrangement.spacedBy(scaled(10.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Absolute.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(scaled(42.dp))
                            .clip(CircleShape)
                            .background(topBarButtonColor)
                    )
                    Box(
                        modifier = Modifier
                            .width(scaled(92.dp))
                            .height(scaled(42.dp))
                            .clip(RoundedCornerShape(scaled(50.dp)))
                            .background(topBarButtonColor)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scaled(190.dp))
                        .clip(RoundedCornerShape(scaled(22.dp)))
                        .background(placeholderColor)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(scaled(8.dp)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.78f)
                                .height(scaled(18.dp))
                                .clip(RoundedCornerShape(scaled(6.dp)))
                                .background(placeholderColor)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.46f)
                                .height(scaled(12.dp))
                                .clip(RoundedCornerShape(scaled(6.dp)))
                                .background(placeholderOnColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(scaled(12.dp)))
                    Box(
                        modifier = Modifier
                            .size(scaled(40.dp))
                            .clip(CircleShape)
                            .background(chipColor)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scaled(6.dp))
                        .clip(RoundedCornerShape(scaled(99.dp)))
                        .background(inactiveTrackColor)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.12f)
                            .height(scaled(6.dp))
                            .clip(RoundedCornerShape(scaled(99.dp)))
                            .background(scheme.primary.copy(alpha = 0.35f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.12f)
                            .height(scaled(6.dp))
                            .clip(RoundedCornerShape(scaled(99.dp)))
                            .background(scheme.primary.copy(alpha = 0.35f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(scaled(50.dp))
                            .width(scaled(58.dp))
                            .clip(CircleShape)
                            .background(scheme.primaryFixed)
                    )
                    Box(
                        modifier = Modifier
                            .height(scaled(50.dp))
                            .width(scaled(58.dp))
                            .clip(RoundedCornerShape(scaled(20.dp)))
                            .background(scheme.primary)
                    )
                    Box(
                        modifier = Modifier
                            .height(scaled(50.dp))
                            .width(scaled(58.dp))
                            .clip(CircleShape)
                            .background(scheme.primaryFixed)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scaled(48.dp))
                        .padding(horizontal = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(scaled(48.dp))
                            .clip(RoundedCornerShape(scaled(50.dp)))
                            .background(toggleRowColor)
                            .padding(
                                horizontal = scaled(6.dp),
                                vertical = scaled(6.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(scaled(6.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(scaled(40.dp))
                                    .clip(RoundedCornerShape(scaled(18.dp)))
                                    .background(scheme.primaryFixedDim)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(scaled(40.dp))
                                    .clip(RoundedCornerShape(scaled(18.dp)))
                                    .background(scheme.secondaryFixedDim)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(scaled(40.dp))
                                    .clip(RoundedCornerShape(scaled(18.dp)))
                                    .background(scheme.tertiaryFixedDim)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PaletteSwatchSquare(
    scheme: ColorScheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spatialSpec = remember { MotionScheme.expressive().fastSpatialSpec<Dp>() }
    BoxWithConstraints(
        modifier = modifier.aspectRatio(1f)
    ) {
        val circleRadius = maxWidth / 2
        val innerCorner by animateDpAsState(
            targetValue = if (selected) 12.dp else circleRadius,
            animationSpec = spatialSpec,
            label = "paletteInnerCorner"
        )
        val outerCorner by animateDpAsState(
            targetValue = if (selected) 16.dp else circleRadius,
            animationSpec = spatialSpec,
            label = "paletteOuterCorner"
        )
        val outlinePadding by animateDpAsState(
            targetValue = if (selected) 4.dp else 0.dp,
            animationSpec = spatialSpec,
            label = "paletteOutlinePadding"
        )
        val borderWidth by animateDpAsState(
            targetValue = if (selected) 2.dp else 0.dp,
            animationSpec = spatialSpec,
            label = "paletteBorderWidth"
        )

        Surface(
            onClick = onClick,
            color = scheme.surfaceContainerHighest,
            shape = RoundedCornerShape(outerCorner.coerceAtLeast(0.dp)),
            border = if (borderWidth > 0.dp) BorderStroke(borderWidth, scheme.primary) else null,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(outlinePadding.coerceAtLeast(0.dp))
            ) {
                Surface(
                    color = scheme.surface,
                    shape = RoundedCornerShape(innerCorner.coerceAtLeast(0.dp)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(scheme.primary)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(scheme.secondary)
                            )
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(scheme.tertiary)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(scheme.surfaceContainerHighest)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PaletteContrastSelector(
    scheme: ColorScheme,
    selectedLevel: ThemeContrastLevel,
    systemContrastLevel: Double,
    hapticsEnabled: Boolean,
    onSelect: (ThemeContrastLevel) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val spatialSpec = remember { MotionScheme.expressive().fastSpatialSpec<Dp>() }
    val effectsSpec = remember { MotionScheme.expressive().defaultEffectsSpec<Color>() }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.settings_palette_contrast_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.settings_palette_contrast_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .background(
                        color = scheme.primaryContainer,
                        shape = CircleShape
                    )
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    text = contrastSummaryText(selectedLevel, systemContrastLevel),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeContrastLevel.entries.forEach { level ->
                val isSelected = level == selectedLevel
                val cornerRadius by animateDpAsState(
                    targetValue = if (isSelected) 50.dp else 14.dp,
                    animationSpec = spatialSpec,
                    label = "contrastSegmentCorner"
                )
                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) scheme.primary else scheme.surfaceContainerHigh,
                    animationSpec = effectsSpec,
                    label = "contrastSegmentBg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                    animationSpec = effectsSpec,
                    label = "contrastSegmentFg"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(containerColor)
                        .clickable {
                            if (!isSelected) {
                                if (hapticsEnabled) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onSelect(level)
                            }
                        }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contrastLevelLabel(level),
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun albumArtPaletteStyleDescription(style: AlbumArtPaletteStyle): String =
    stringResource(
        when (style) {
            AlbumArtPaletteStyle.CONTENT -> R.string.settings_palette_style_content_desc
            AlbumArtPaletteStyle.FIDELITY -> R.string.settings_palette_style_fidelity_desc
            AlbumArtPaletteStyle.TONAL_SPOT -> R.string.settings_palette_style_tonal_spot_desc
            AlbumArtPaletteStyle.VIBRANT -> R.string.settings_palette_style_vibrant_desc
            AlbumArtPaletteStyle.EXPRESSIVE -> R.string.settings_palette_style_expressive_desc
            AlbumArtPaletteStyle.FRUIT_SALAD -> R.string.settings_palette_style_fruit_salad_desc
            AlbumArtPaletteStyle.MONOCHROME -> R.string.settings_palette_style_monochrome_desc
            AlbumArtPaletteStyle.NEUTRAL -> R.string.settings_palette_style_neutral_desc
        }
    )

@Composable
internal fun albumArtPaletteStyleLabel(style: AlbumArtPaletteStyle): String =
    stringResource(
        when (style) {
            AlbumArtPaletteStyle.CONTENT -> R.string.settings_palette_style_content
            AlbumArtPaletteStyle.FIDELITY -> R.string.settings_palette_style_fidelity
            AlbumArtPaletteStyle.TONAL_SPOT -> R.string.settings_palette_style_tonal_spot
            AlbumArtPaletteStyle.VIBRANT -> R.string.settings_palette_style_vibrant
            AlbumArtPaletteStyle.EXPRESSIVE -> R.string.settings_palette_style_expressive
            AlbumArtPaletteStyle.FRUIT_SALAD -> R.string.settings_palette_style_fruit_salad
            AlbumArtPaletteStyle.MONOCHROME -> R.string.settings_palette_style_monochrome
            AlbumArtPaletteStyle.NEUTRAL -> R.string.settings_palette_style_neutral
        }
    )

@Composable
private fun contrastLevelLabel(level: ThemeContrastLevel): String =
    stringResource(
        when (level) {
            ThemeContrastLevel.SYSTEM -> R.string.settings_palette_contrast_system
            ThemeContrastLevel.STANDARD -> R.string.settings_palette_contrast_standard
            ThemeContrastLevel.MEDIUM -> R.string.settings_palette_contrast_medium
            ThemeContrastLevel.HIGH -> R.string.settings_palette_contrast_high
        }
    )

@Composable
private fun contrastSummaryText(
    selectedLevel: ThemeContrastLevel,
    systemContrastLevel: Double
): String {
    if (selectedLevel != ThemeContrastLevel.SYSTEM) {
        return contrastLevelLabel(selectedLevel)
    }
    val detectedTier = when {
        systemContrastLevel >= 0.75 -> stringResource(R.string.settings_palette_contrast_high)
        systemContrastLevel >= 0.25 -> stringResource(R.string.settings_palette_contrast_medium)
        else -> stringResource(R.string.settings_palette_contrast_standard)
    }
    return stringResource(R.string.settings_palette_contrast_system_summary, detectedTier)
}
