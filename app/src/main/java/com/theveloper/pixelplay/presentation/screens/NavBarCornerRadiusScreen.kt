package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.navigation.popBackStackSafely
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.preferences.DEFAULT_NAV_BAR_CORNER_RADIUS
import com.theveloper.pixelplay.data.preferences.MAX_NAV_BAR_CORNER_RADIUS
import com.theveloper.pixelplay.data.preferences.MIN_NAV_BAR_CORNER_RADIUS
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.presentation.components.LocalInsideFloatingPane
import com.theveloper.pixelplay.presentation.components.NavBarDefaultMinBottomPadding
import com.theveloper.pixelplay.presentation.components.resolveNavBarBottomPadding
import com.theveloper.pixelplay.presentation.components.resolveNavBarHorizontalPadding
import com.theveloper.pixelplay.presentation.components.resolveNavBarSurfaceHeight
import com.theveloper.pixelplay.presentation.components.sanitizeNavigationBarBottomInset
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavBarCornerRadiusScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    embeddedInPane: Boolean = false,
    onBackClick: (() -> Unit)? = null
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val useSmoothCorners by settingsViewModel.useSmoothCorners.collectAsStateWithLifecycle()
    val isFullWidth = uiState.navBarStyle == NavBarStyle.FULL_WIDTH
    val backAction: () -> Unit = onBackClick ?: { navController.popBackStackSafely() }

    NavBarCornerRadiusContent(
        initialRadius = uiState.navBarCornerRadius.toFloat(),
        onRadiusChange = { settingsViewModel.setNavBarCornerRadius(it) },
        onDone = backAction,
        onBack = backAction,
        isFullWidth = isFullWidth,
        isCompactMode = uiState.navBarCompactMode,
        useSmoothCorners = useSmoothCorners,
        embeddedInPane = embeddedInPane
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavBarCornerRadiusContent(
    initialRadius: Float,
    onRadiusChange: (Int) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    isFullWidth: Boolean,
    isCompactMode: Boolean = false,
    useSmoothCorners: Boolean = true,
    embeddedInPane: Boolean = false
) {
    fun Float.safeRadius(): Float =
        coerceIn(MIN_NAV_BAR_CORNER_RADIUS.toFloat(), MAX_NAV_BAR_CORNER_RADIUS.toFloat())

    val defaultRadiusFloat = DEFAULT_NAV_BAR_CORNER_RADIUS.toFloat().safeRadius()
    var sliderValue by remember { mutableFloatStateOf(initialRadius.safeRadius()) }
    var hasBeenAdjusted by remember { mutableStateOf(sliderValue != defaultRadiusFloat) }

    val haptic = LocalHapticFeedback.current

    // Sync if initial value changes externally (though unlikely in this flow, good practice)
    LaunchedEffect(initialRadius) {
        sliderValue = initialRadius.safeRadius()
    }

    // Update hasBeenAdjusted when sliderValue changes relative to default
    LaunchedEffect(sliderValue) {
        hasBeenAdjusted = sliderValue != defaultRadiusFloat
    }

    val insideFloatingPane = LocalInsideFloatingPane.current
    val useTransparentContainer = embeddedInPane || insideFloatingPane

    Scaffold(
        containerColor = if (useTransparentContainer) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                windowInsets = if (useTransparentContainer) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
                title = { },
                navigationIcon = {
                    if (!embeddedInPane) {
                        FilledIconButton(
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                            onClick = onBack,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Icon(
                                painterResource(R.drawable.rounded_arrow_back_24),
                                contentDescription = stringResource(R.string.common_back),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            onRadiusChange(
                                sliderValue.toInt().coerceIn(MIN_NAV_BAR_CORNER_RADIUS, MAX_NAV_BAR_CORNER_RADIUS)
                            )
                            onDone()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.common_done))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (useTransparentContainer) Color.Transparent else MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (useTransparentContainer) Color.Transparent else MaterialTheme.colorScheme.background)
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.navbar_adjust_title),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.navbar_adjust_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Controls Area
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Controls
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.navbar_corner_radius_label),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (hasBeenAdjusted) {
                                FilledTonalButton(
                                    onClick = {
                                        sliderValue = defaultRadiusFloat
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = stringResource(R.string.common_reset),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text(stringResource(R.string.common_reset), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RoundedCorner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Slider
                            Box(modifier = Modifier.weight(1f)) {
                                Slider(
                                    value = sliderValue,
                                    onValueChange = {
                                        if (sliderValue != it) {
                                            if (it.toInt() != sliderValue.toInt()) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            sliderValue = it.safeRadius()
                                        }
                                    },
                                    valueRange = MIN_NAV_BAR_CORNER_RADIUS.toFloat()..MAX_NAV_BAR_CORNER_RADIUS.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    track = { sliderState ->
                                        SliderDefaults.Track(
                                            sliderState = sliderState,
                                            trackCornerSize = 14.dp,
                                            modifier = Modifier.height(36.dp)
                                        )
                                    }
                                )
                            }

                            Text(
                                modifier = Modifier.width(46.dp),
                                text = stringResource(
                                    R.string.navbar_corner_dp_format,
                                    sliderValue.toInt()
                                ),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Live Preview matching the real bottom bar geometry, corners, and colors
                val previewStyle = if (isFullWidth) NavBarStyle.FULL_WIDTH else NavBarStyle.DEFAULT
                val sanitizedInset = sanitizeNavigationBarBottomInset(paddingValues.calculateBottomPadding())
                val previewHorizontalPadding = resolveNavBarHorizontalPadding(previewStyle, sanitizedInset)
                val previewBottomPadding = if (useTransparentContainer) {
                    if (isFullWidth) 0.dp else NavBarDefaultMinBottomPadding
                } else {
                    resolveNavBarBottomPadding(previewStyle, sanitizedInset)
                }
                val previewSurfaceInset = if (useTransparentContainer && isFullWidth) 0.dp else sanitizedInset
                val previewHeight = resolveNavBarSurfaceHeight(
                    navBarStyle = previewStyle,
                    systemNavBarInset = previewSurfaceInset,
                    compactMode = isCompactMode
                )
                val previewTopCorner = if (isFullWidth) sliderValue.dp else 10.dp
                val previewBottomCorner = if (isFullWidth) 0.dp else sliderValue.dp
                val previewShape = if (useSmoothCorners) {
                    AbsoluteSmoothCornerShape(
                        cornerRadiusTL = previewTopCorner,
                        smoothnessAsPercentTL = 60,
                        cornerRadiusTR = previewTopCorner,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBL = previewBottomCorner,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusBR = previewBottomCorner,
                        smoothnessAsPercentBR = 60
                    )
                } else {
                    RoundedCornerShape(
                        topStart = previewTopCorner,
                        topEnd = previewTopCorner,
                        bottomEnd = previewBottomCorner,
                        bottomStart = previewBottomCorner
                    )
                }

                Surface(
                    modifier = Modifier
                        .padding(
                            start = previewHorizontalPadding,
                            end = previewHorizontalPadding,
                            bottom = previewBottomPadding
                        )
                        .fillMaxWidth()
                        .height(previewHeight),
                    color = NavigationBarDefaults.containerColor,
                    shadowElevation = 3.dp,
                    shape = previewShape
                ) {
                    val innerBottomPadding = if (isFullWidth) previewSurfaceInset else 0.dp
                    val innerHorizontalPadding = if (isFullWidth) 12.dp else 10.dp
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = innerHorizontalPadding,
                                end = innerHorizontalPadding,
                                bottom = innerBottomPadding
                            ),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PreviewNavBarItem(
                            iconRes = R.drawable.home_24_rounded_filled,
                            labelRes = R.string.nav_bar_home,
                            selected = true,
                            showLabel = !isCompactMode,
                            modifier = Modifier.weight(1f)
                        )
                        PreviewNavBarItem(
                            iconRes = R.drawable.rounded_search_24,
                            labelRes = R.string.nav_bar_search,
                            selected = false,
                            showLabel = !isCompactMode,
                            modifier = Modifier.weight(1f)
                        )
                        PreviewNavBarItem(
                            iconRes = R.drawable.rounded_library_music_24,
                            labelRes = R.string.nav_bar_library,
                            selected = false,
                            showLabel = !isCompactMode,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewNavBarItem(
    iconRes: Int,
    labelRes: Int,
    selected: Boolean,
    showLabel: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 32.dp)
                .background(
                    color = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )
        }
        if (showLabel) {
            Text(
                text = stringResource(id = labelRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
