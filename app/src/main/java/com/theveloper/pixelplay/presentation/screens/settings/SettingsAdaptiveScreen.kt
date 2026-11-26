package com.theveloper.pixelplay.presentation.screens.settings

import DelimiterConfigScreen
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.LocalInsideFloatingPane
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.TabletPaneTokens
import com.theveloper.pixelplay.presentation.components.resolveTabletPaneBottomMargin
import com.theveloper.pixelplay.presentation.components.resolveTabletPaneTopMargin
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.presentation.navigation.topLevelFadeThroughEnter
import com.theveloper.pixelplay.presentation.navigation.topLevelFadeThroughExit
import com.theveloper.pixelplay.presentation.screens.AboutScreen
import com.theveloper.pixelplay.presentation.screens.ArtistSettingsScreen
import com.theveloper.pixelplay.presentation.screens.DeviceCapabilitiesScreen
import com.theveloper.pixelplay.presentation.screens.EqualizerScreen
import com.theveloper.pixelplay.presentation.screens.ExperimentalSettingsScreen
import com.theveloper.pixelplay.presentation.screens.NavBarCornerRadiusScreen
import com.theveloper.pixelplay.presentation.screens.OfflineModelHubScreen
import com.theveloper.pixelplay.presentation.screens.OpenSourceLicensesScreen
import com.theveloper.pixelplay.presentation.screens.PaletteStyleSettingsScreen
import com.theveloper.pixelplay.presentation.screens.SettingsCategoryScreen
import com.theveloper.pixelplay.presentation.screens.WordDelimiterConfigScreen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayWindowInfo
import com.theveloper.pixelplay.ui.theme.LocalSpacing
import com.theveloper.pixelplay.ui.theme.PixelPlayGroupShapes
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private data class SettingsMenuGroup(
    val id: String,
    @StringRes val labelRes: Int,
    val categories: List<SettingsCategory>
)

private val SamsungGroupedSettingsMenu = listOf(
    SettingsMenuGroup(
        id = "music_appearance",
        labelRes = R.string.settings_group_library_appearance,
        categories = listOf(
            SettingsCategory.LIBRARY,
            SettingsCategory.APPEARANCE
        )
    ),
    SettingsMenuGroup(
        id = "audio_interaction",
        labelRes = R.string.settings_group_audio_interaction,
        categories = listOf(
            SettingsCategory.PLAYBACK,
            SettingsCategory.BEHAVIOR,
            SettingsCategory.EQUALIZER
        )
    ),
    SettingsMenuGroup(
        id = "intelligence_data",
        labelRes = R.string.settings_group_intelligence_data,
        categories = listOf(
            SettingsCategory.AI_INTEGRATION,
            SettingsCategory.BACKUP_RESTORE
        )
    ),
    SettingsMenuGroup(
        id = "system_info",
        labelRes = R.string.settings_group_system_info,
        categories = listOf(
            SettingsCategory.DEVICE_CAPABILITIES,
            SettingsCategory.DEVELOPER,
            SettingsCategory.ABOUT
        )
    )
)

/**
 * Samsung Settings grouped 2-pane structure combined with Google Play Store's Material 3 Adaptive
 * design language (`NavigableListDetailPaneScaffold` + `VerticalDragHandle` + rounded detail pane
 * canvas + fullscreen expand toggle + floating settings search bar).
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3AdaptiveApi::class
)
@Composable
fun SettingsAdaptiveScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    onNavigationIconClick: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val windowInfo = LocalPixelPlayWindowInfo.current
    val spacing = LocalSpacing.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val isMiniPlayerVisible by remember(playerViewModel) {
        playerViewModel.stablePlayerState
            .map { it.currentSong != null }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val adaptiveInfo = currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)
    val baseDirective = remember(adaptiveInfo) {
        calculatePaneScaffoldDirective(adaptiveInfo)
    }
    val scaffoldDirective = remember(baseDirective, windowInfo.useTwoPaneSettings, spacing.paneGap) {
        baseDirective.copy(
            maxHorizontalPartitions = if (windowInfo.useTwoPaneSettings) 2 else 1,
            horizontalPartitionSpacerSize = if (windowInfo.useTwoPaneSettings) spacing.paneGap else 0.dp
        )
    }

    val navigator = rememberListDetailPaneScaffoldNavigator<SettingsDetailDestination>(
        scaffoldDirective = scaffoldDirective
    )

    val isTwoPane = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded &&
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded

    // Ensure the detail pane always displays a sensible default category when entering 2-pane tablet mode.
    LaunchedEffect(isTwoPane) {
        if (isTwoPane && navigator.currentDestination?.contentKey == null) {
            navigator.navigateTo(
                pane = ListDetailPaneScaffoldRole.Detail,
                contentKey = SettingsDetailDestination.Category(SettingsCategory.LIBRARY.id)
            )
        }
    }

    val listPreferredWidth = windowInfo.settingsListPanePreferredWidth
    val defaultAnchor = remember(listPreferredWidth) {
        PaneExpansionAnchor.Offset.fromStart(listPreferredWidth)
    }
    val balancedAnchor = remember { PaneExpansionAnchor.Proportion(0.45f) }
    val fullscreenDetailAnchor = remember { PaneExpansionAnchor.Offset.fromStart(0.dp) }
    val paneAnchors = remember(defaultAnchor, balancedAnchor, fullscreenDetailAnchor) {
        listOf(defaultAnchor, balancedAnchor, fullscreenDetailAnchor)
    }
    val paneExpansionState = rememberPaneExpansionState(
        keyProvider = navigator.scaffoldValue,
        anchors = paneAnchors,
        initialAnchoredIndex = 0
    )
    val isDetailExpanded = isTwoPane && paneExpansionState.currentAnchor == fullscreenDetailAnchor

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchResults = remember(searchQuery, context) {
        SettingsSearchIndex.search(
            query = searchQuery,
            titleProvider = { resId -> context.getString(resId) },
            subtitleProvider = { resId -> context.getString(resId) }
        )
    }

    val currentDestination: SettingsDetailDestination =
        navigator.currentDestination?.contentKey
            ?: SettingsDetailDestination.Category(SettingsCategory.LIBRARY.id)

    fun openDestination(destination: SettingsDetailDestination) {
        focusManager.clearFocus()
        coroutineScope.launch {
            navigator.navigateTo(
                pane = ListDetailPaneScaffoldRole.Detail,
                contentKey = destination
            )
        }
    }

    val outerBackgroundColor = if (isTwoPane || windowInfo.useNavigationRail) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.background
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(outerBackgroundColor)
    ) {
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            defaultBackBehavior = BackNavigationBehavior.PopUntilContentChange,
            paneExpansionState = paneExpansionState,
            paneExpansionDragHandle = { state ->
                val interactionSource = remember { MutableInteractionSource() }
                val handleDescription = stringResource(R.string.settings_pane_resize_handle)
                VerticalDragHandle(
                    modifier = Modifier.paneExpansionDraggable(
                        state = state,
                        minTouchTargetSize = 48.dp,
                        interactionSource = interactionSource,
                        semanticsProperties = {
                            contentDescription = handleDescription
                        }
                    ),
                    interactionSource = interactionSource
                )
            },
            listPane = {
                val listContent: @Composable () -> Unit = {
                    SettingsListPane(
                        isTwoPane = isTwoPane,
                        showBackButton = !windowInfo.useNavigationRail,
                        activeCategory = if (isTwoPane) currentDestination.parentCategory else null,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        searchResults = searchResults,
                        isMiniPlayerVisible = isMiniPlayerVisible,
                        onBackClick = onNavigationIconClick,
                        onCategoryClick = { category ->
                            openDestination(SettingsDetailDestination.forCategory(category))
                        },
                        onSearchResultClick = { entry ->
                            openDestination(entry.destination)
                        }
                    )
                }
                if (isTwoPane) {
                    // In 2-pane tablet mode, avoid AnimatedPane's internal LookaheadScope
                    // animateBounds spring so it never fights the outer Side-Docked Card resize.
                    Box(
                        modifier = Modifier
                            .preferredWidth(listPreferredWidth)
                            .fillMaxSize()
                    ) {
                        listContent()
                    }
                } else {
                    AnimatedPane(
                        modifier = Modifier.preferredWidth(listPreferredWidth)
                    ) {
                        listContent()
                    }
                }
            },
            detailPane = {
                val detailContent: @Composable () -> Unit = {
                    SettingsDetailPane(
                        destination = currentDestination,
                        isTwoPane = isTwoPane,
                        isDetailExpanded = isDetailExpanded,
                        navController = navController,
                        playerViewModel = playerViewModel,
                        settingsViewModel = settingsViewModel,
                        onBackClick = {
                            coroutineScope.launch {
                                when {
                                    isDetailExpanded && !currentDestination.isSubDestination -> {
                                        paneExpansionState.animateTo(defaultAnchor)
                                    }
                                    navigator.canNavigateBack(BackNavigationBehavior.PopUntilContentChange) -> {
                                        navigator.navigateBack(BackNavigationBehavior.PopUntilContentChange)
                                    }
                                    !isTwoPane -> {
                                        navigator.navigateBack()
                                    }
                                    else -> {
                                        onNavigationIconClick()
                                    }
                                }
                            }
                        },
                        onToggleExpand = {
                            coroutineScope.launch {
                                paneExpansionState.animateTo(
                                    if (isDetailExpanded) defaultAnchor else fullscreenDetailAnchor
                                )
                            }
                        },
                        onOpenSubDestination = { subDest ->
                            openDestination(subDest)
                        }
                    )
                }
                if (isTwoPane) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        detailContent()
                    }
                } else {
                    AnimatedPane {
                        detailContent()
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsListPane(
    isTwoPane: Boolean,
    showBackButton: Boolean,
    activeCategory: SettingsCategory?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<SettingsSearchEntry>,
    isMiniPlayerVisible: Boolean,
    onBackClick: () -> Unit,
    onCategoryClick: (SettingsCategory) -> Unit,
    onSearchResultClick: (SettingsSearchEntry) -> Unit
) {
    val spacing = LocalSpacing.current
    val bottomInsets = WindowInsets.navigationBars.union(WindowInsets.ime).asPaddingValues().calculateBottomPadding()
    val miniPlayerClearance = if (isMiniPlayerVisible) MiniPlayerHeight + spacing.md else 0.dp
    val floatingSearchBottomOffset = bottomInsets + miniPlayerClearance + spacing.md
    val searchBarHeight = 52.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (showBackButton) spacing.sm else spacing.xl,
                        end = spacing.lg,
                        top = spacing.md,
                        bottom = spacing.md
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBackButton) {
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
                    Spacer(modifier = Modifier.width(spacing.md))
                }
                Text(
                    text = stringResource(R.string.common_settings),
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (searchQuery.isNotBlank()) {
                if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = spacing.xxl,
                                end = spacing.xxl,
                                bottom = floatingSearchBottomOffset + searchBarHeight + spacing.lg
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(spacing.sm)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = stringResource(R.string.settings_search_no_results),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.settings_search_no_results_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = spacing.md,
                            end = spacing.md,
                            top = spacing.xs,
                            bottom = floatingSearchBottomOffset + searchBarHeight + spacing.lg
                        ),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        items(searchResults, key = { it.id }) { entry ->
                            SettingsSearchResultRow(
                                entry = entry,
                                onClick = { onSearchResultClick(entry) }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = spacing.md,
                        end = spacing.md,
                        top = spacing.xs,
                        bottom = floatingSearchBottomOffset + searchBarHeight + spacing.lg
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    SamsungGroupedSettingsMenu.forEachIndexed { groupIndex, group ->
                        item(key = "group_${group.id}") {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(spacing.xxs)
                            ) {
                                group.categories.forEachIndexed { itemIndex, category ->
                                    val isSelected = isTwoPane && activeCategory == category
                                    val shape = if (isTwoPane) {
                                        RoundedCornerShape(PixelPlayGroupShapes.ActiveNavigationPillRadius)
                                    } else {
                                        PixelPlayGroupShapes.segmentedItemShape(
                                            index = itemIndex,
                                            count = group.categories.size
                                        )
                                    }
                                    SettingsCategoryMenuRow(
                                        category = category,
                                        selected = isSelected,
                                        isTwoPane = isTwoPane,
                                        shape = shape,
                                        onClick = { onCategoryClick(category) }
                                    )
                                }
                            }
                            if (groupIndex < SamsungGroupedSettingsMenu.lastIndex) {
                                if (isTwoPane) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(
                                            horizontal = spacing.lg,
                                            vertical = spacing.sm
                                        ),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(spacing.md))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Samsung-style Floating Search Pill pinned cleanly above system insets & MiniPlayer
        FloatingSettingsSearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .padding(bottom = floatingSearchBottomOffset)
                .height(searchBarHeight)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsCategoryMenuRow(
    category: SettingsCategory,
    selected: Boolean,
    isTwoPane: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit
) {
    val spacing = LocalSpacing.current
    val badgeColors = rememberCategoryColors(category)

    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.secondaryContainer
            isTwoPane -> Color.Transparent
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "SettingsRowContainerColor"
    )
    val titleColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "SettingsRowTitleColor"
    )
    val subtitleColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "SettingsRowSubtitleColor"
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = spacing.lg,
                    vertical = if (isTwoPane) spacing.md else spacing.listRowVertical
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (isTwoPane) 40.dp else 44.dp)
                    .clip(CircleShape)
                    .background(badgeColors.containerColor),
                contentAlignment = Alignment.Center
            ) {
                if (category.icon != null) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = badgeColors.iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                } else if (category.iconRes != null) {
                    Icon(
                        painter = painterResource(id = category.iconRes),
                        contentDescription = null,
                        tint = badgeColors.iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(spacing.lg))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.titleSubtitleGap)
            ) {
                Text(
                    text = stringResource(category.titleRes),
                    style = if (selected) {
                        MaterialTheme.typography.titleMediumEmphasized
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(category.subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = if (isTwoPane) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsSearchResultRow(
    entry: SettingsSearchEntry,
    onClick: () -> Unit
) {
    val spacing = LocalSpacing.current
    val badgeColors = rememberCategoryColors(entry.category)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(PixelPlayGroupShapes.ActiveNavigationPillRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(badgeColors.containerColor),
                contentAlignment = Alignment.Center
            ) {
                if (entry.category.icon != null) {
                    Icon(
                        imageVector = entry.category.icon,
                        contentDescription = null,
                        tint = badgeColors.iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                } else if (entry.category.iconRes != null) {
                    Icon(
                        painter = painterResource(id = entry.category.iconRes),
                        contentDescription = null,
                        tint = badgeColors.iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(spacing.md))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs)
            ) {
                Text(
                    text = stringResource(entry.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(entry.category.titleRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FloatingSettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.common_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(spacing.md))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_search_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.common_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsDetailPane(
    destination: SettingsDetailDestination,
    isTwoPane: Boolean,
    isDetailExpanded: Boolean,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onToggleExpand: () -> Unit,
    onOpenSubDestination: (SettingsDetailDestination) -> Unit
) {
    val spacing = LocalSpacing.current

    val detailTitle = when (destination) {
        is SettingsDetailDestination.Category -> {
            stringResource(destination.parentCategory.titleRes)
        }
        SettingsDetailDestination.Equalizer -> {
            stringResource(R.string.settings_category_equalizer_title)
        }
        SettingsDetailDestination.DeviceCapabilities -> {
            stringResource(R.string.settings_category_device_capabilities_title)
        }
        SettingsDetailDestination.About -> {
            stringResource(R.string.about_screen_title)
        }
        SettingsDetailDestination.PaletteStyle -> {
            stringResource(R.string.settings_palette_screen_title)
        }
        SettingsDetailDestination.NavBarCornerRadius -> {
            stringResource(R.string.navbar_adjust_title)
        }
        SettingsDetailDestination.ArtistSettings -> {
            stringResource(R.string.settings_artists_title)
        }
        SettingsDetailDestination.DelimiterConfig -> {
            stringResource(R.string.delimiters_screen_title)
        }
        SettingsDetailDestination.WordDelimiterConfig -> {
            stringResource(R.string.word_delimiters_screen_title)
        }
        SettingsDetailDestination.OfflineModelHub -> {
            stringResource(R.string.settings_category_ai_title)
        }
        SettingsDetailDestination.Experimental -> {
            stringResource(R.string.settings_exp_screen_title)
        }
        SettingsDetailDestination.OpenSourceLicenses -> {
            stringResource(R.string.open_source_licenses_screen_title)
        }
    }

    val showBackButton = !isTwoPane || destination.isSubDestination || isDetailExpanded

    val containerModifier = if (isTwoPane) {
        val topMargin = resolveTabletPaneTopMargin(
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        )
        val bottomMargin = resolveTabletPaneBottomMargin(
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        )
        Modifier
            .fillMaxSize()
            .padding(
                top = topMargin,
                end = TabletPaneTokens.EndMargin,
                bottom = bottomMargin
            )
    } else {
        Modifier.fillMaxSize()
    }

    val containerShape = if (isTwoPane) {
        RoundedCornerShape(TabletPaneTokens.CornerRadius)
    } else {
        RoundedCornerShape(0.dp)
    }

    val containerColor = if (isTwoPane) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        MaterialTheme.colorScheme.background
    }

    Surface(
        modifier = containerModifier,
        shape = containerShape,
        color = containerColor
    ) {
        CompositionLocalProvider(LocalInsideFloatingPane provides isTwoPane) {
            Column(modifier = Modifier.fillMaxSize()) {
            // Unified Google Play-inspired Detail Pane Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (!isTwoPane) Modifier.statusBarsPadding() else Modifier)
                    .height(64.dp)
                    .padding(horizontal = spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBackButton) {
                    FilledIconButton(
                        onClick = onBackClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                    Spacer(modifier = Modifier.width(spacing.md))
                }

                Text(
                    text = detailTitle,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isTwoPane) {
                    Spacer(modifier = Modifier.width(spacing.sm))
                    FilledIconButton(
                        onClick = onToggleExpand,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = if (isDetailExpanded) {
                                Icons.Rounded.CloseFullscreen
                            } else {
                                Icons.Rounded.OpenInFull
                            },
                            contentDescription = stringResource(
                                if (isDetailExpanded) {
                                    R.string.settings_pane_restore_split
                                } else {
                                    R.string.settings_pane_expand_fullscreen
                                }
                            )
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        topLevelFadeThroughEnter() togetherWith topLevelFadeThroughExit()
                    },
                    label = "SettingsDetailPaneContent"
                ) { targetDestination ->
                    val contentWidthModifier = if (targetDestination is SettingsDetailDestination.PaletteStyle) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxSize()
                            .widthIn(max = spacing.maxReadableContentWidth)
                    }

                    Box(modifier = contentWidthModifier) {
                        when (targetDestination) {
                            is SettingsDetailDestination.Category -> {
                                SettingsCategoryScreen(
                                    categoryId = targetDestination.categoryId,
                                    navController = navController,
                                    playerViewModel = playerViewModel,
                                    settingsViewModel = settingsViewModel,
                                    onBackClick = onBackClick,
                                    embeddedInPane = true,
                                    onOpenSubDestination = onOpenSubDestination
                                )
                            }
                            SettingsDetailDestination.Equalizer -> {
                                EqualizerScreen(
                                    navController = navController,
                                    playerViewModel = playerViewModel,
                                    embeddedInPane = true
                                )
                            }
                            SettingsDetailDestination.DeviceCapabilities -> {
                                DeviceCapabilitiesScreen(
                                    navController = navController,
                                    playerViewModel = playerViewModel,
                                    embeddedInPane = true
                                )
                            }
                            SettingsDetailDestination.About -> {
                                AboutScreen(
                                    navController = navController,
                                    viewModel = playerViewModel,
                                    onNavigationIconClick = onBackClick,
                                    embeddedInPane = true,
                                    onOpenLicenses = {
                                        onOpenSubDestination(SettingsDetailDestination.OpenSourceLicenses)
                                    }
                                )
                            }
                            SettingsDetailDestination.PaletteStyle -> {
                                PaletteStyleSettingsScreen(
                                    playerViewModel = playerViewModel,
                                    settingsViewModel = settingsViewModel,
                                    onBackClick = onBackClick,
                                    embeddedInPane = true
                                )
                            }
                            SettingsDetailDestination.NavBarCornerRadius -> {
                                NavBarCornerRadiusScreen(
                                    navController = navController,
                                    settingsViewModel = settingsViewModel,
                                    embeddedInPane = true,
                                    onBackClick = onBackClick
                                )
                            }
                            SettingsDetailDestination.ArtistSettings -> {
                                ArtistSettingsScreen(
                                    navController = navController,
                                    embeddedInPane = true,
                                    onOpenDelimiterConfig = {
                                        onOpenSubDestination(SettingsDetailDestination.DelimiterConfig)
                                    },
                                    onOpenWordDelimiterConfig = {
                                        onOpenSubDestination(SettingsDetailDestination.WordDelimiterConfig)
                                    }
                                )
                            }
                            SettingsDetailDestination.DelimiterConfig -> {
                                DelimiterConfigScreen(
                                    navController = navController,
                                    embeddedInPane = true
                                )
                            }
                            SettingsDetailDestination.WordDelimiterConfig -> {
                                WordDelimiterConfigScreen(
                                    navController = navController,
                                    embeddedInPane = true
                                )
                            }
                            SettingsDetailDestination.OfflineModelHub -> {
                                OfflineModelHubScreen(
                                    navController = navController,
                                    embeddedInPane = true
                                )
                            }
                            SettingsDetailDestination.Experimental -> {
                                ExperimentalSettingsScreen(
                                    navController = navController,
                                    playerViewModel = playerViewModel,
                                    onNavigationIconClick = onBackClick,
                                    settingsViewModel = settingsViewModel,
                                    embeddedInPane = true
                                )
                            }
                            SettingsDetailDestination.OpenSourceLicenses -> {
                                OpenSourceLicensesScreen(
                                    onBackClick = onBackClick,
                                    embeddedInPane = true
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
