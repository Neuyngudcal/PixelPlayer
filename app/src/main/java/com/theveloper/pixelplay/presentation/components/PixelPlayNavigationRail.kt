package com.theveloper.pixelplay.presentation.components

import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateToTopLevelSafely
import com.theveloper.pixelplay.ui.theme.LocalSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val NavigationRailCollapsedWidth: Dp = 80.dp
val NavigationRailExpandedWidth: Dp = 220.dp

enum class PixelPlayRailDestination(val rootRoute: String) {
    HOME(Screen.Home.route),
    SEARCH(Screen.Search.route),
    LIBRARY(Screen.Library.route),
    EQUALIZER(Screen.Equalizer.route),
    SETTINGS(Screen.Settings.route)
}

fun resolveSelectedRailDestination(currentRoute: String?): PixelPlayRailDestination {
    if (currentRoute == null) return PixelPlayRailDestination.HOME
    val editTransitionBase = Screen.EditTransition.route.substringBefore("?")
    return when {
        currentRoute == Screen.Search.route ||
            currentRoute.startsWith(Screen.GenreDetail.route.substringBefore("{")) -> {
            PixelPlayRailDestination.SEARCH
        }
        currentRoute == Screen.Library.route ||
            currentRoute.startsWith(Screen.AlbumDetail.route.substringBefore("{")) ||
            currentRoute.startsWith(Screen.ArtistDetail.route.substringBefore("{")) ||
            currentRoute.startsWith(Screen.PlaylistDetail.route.substringBefore("{")) ||
            currentRoute == editTransitionBase ||
            currentRoute.startsWith("$editTransitionBase?") -> {
            PixelPlayRailDestination.LIBRARY
        }
        currentRoute == Screen.Equalizer.route -> {
            PixelPlayRailDestination.EQUALIZER
        }
        currentRoute == Screen.Settings.route ||
            currentRoute.startsWith(Screen.SettingsCategory.route.substringBefore("{")) ||
            currentRoute == Screen.About.route ||
            currentRoute == Screen.PaletteStyle.route ||
            currentRoute == Screen.ArtistSettings.route ||
            currentRoute == Screen.DelimiterConfig.route ||
            currentRoute == Screen.WordDelimiterConfig.route ||
            currentRoute == Screen.NavBarCrRad.route ||
            currentRoute == Screen.OpenSourceLicenses.route ||
            currentRoute == Screen.DeviceCapabilities.route ||
            currentRoute == Screen.Experimental.route ||
            currentRoute == Screen.OfflineModelHub.route ||
            currentRoute == Screen.EasterEgg.route -> {
            PixelPlayRailDestination.SETTINGS
        }
        else -> PixelPlayRailDestination.HOME
    }
}

@Immutable
private data class RailItemSpec(
    val destination: PixelPlayRailDestination,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int? = null,
    @DrawableRes val selectedIconRes: Int? = null,
    val iconVector: ImageVector? = null
)

/**
 * Persistent Google Play / Material 3 Expressive Navigation Rail for Tablet (Medium+ >= 600dp).
 * Supports smooth collapse/expand into a Wide Navigation Rail on Large+ displays (Tab S10 Ultra / DeX).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PixelPlayNavigationRail(
    navController: NavHostController,
    currentRoute: String?,
    isExpanded: Boolean,
    supportsExpandToggle: Boolean,
    onToggleExpanded: () -> Unit,
    onSearchDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
    railWidth: Dp? = null
) {
    val spacing = LocalSpacing.current
    val scope = rememberCoroutineScope()
    val selectedDestination = remember(currentRoute) { resolveSelectedRailDestination(currentRoute) }
    var lastSearchTapTimestamp by remember { mutableLongStateOf(0L) }

    val effectiveExpanded = isExpanded && supportsExpandToggle
    val resolvedWidth = railWidth ?: animateDpAsState(
        targetValue = if (effectiveExpanded) {
            NavigationRailExpandedWidth
        } else {
            NavigationRailCollapsedWidth
        },
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "NavigationRailWidth"
    ).value

    val primaryItems = remember {
        listOf(
            RailItemSpec(
                destination = PixelPlayRailDestination.HOME,
                labelRes = R.string.nav_bar_home,
                iconRes = R.drawable.rounded_home_24,
                selectedIconRes = R.drawable.home_24_rounded_filled
            ),
            RailItemSpec(
                destination = PixelPlayRailDestination.SEARCH,
                labelRes = R.string.nav_bar_search,
                iconRes = R.drawable.rounded_search_24,
                selectedIconRes = R.drawable.rounded_search_24
            ),
            RailItemSpec(
                destination = PixelPlayRailDestination.LIBRARY,
                labelRes = R.string.nav_bar_library,
                iconRes = R.drawable.rounded_library_music_24,
                selectedIconRes = R.drawable.round_library_music_24
            )
        )
    }

    val utilityItems = remember {
        listOf(
            RailItemSpec(
                destination = PixelPlayRailDestination.EQUALIZER,
                labelRes = R.string.settings_category_equalizer_title,
                iconVector = Icons.Rounded.GraphicEq
            ),
            RailItemSpec(
                destination = PixelPlayRailDestination.SETTINGS,
                labelRes = R.string.common_settings,
                iconRes = R.drawable.rounded_settings_24,
                selectedIconRes = R.drawable.rounded_settings_24
            )
        )
    }

    fun onDestinationClick(destination: PixelPlayRailDestination) {
        if (currentRoute == null) return
        val targetRoute = destination.rootRoute
        val isAtRoot = currentRoute == targetRoute

        if (destination == PixelPlayRailDestination.SEARCH) {
            val now = SystemClock.elapsedRealtime()
            val isDoubleTap = now - lastSearchTapTimestamp <= 350L
            lastSearchTapTimestamp = now

            if (!isAtRoot) {
                val popped = if (selectedDestination == PixelPlayRailDestination.SEARCH) {
                    navController.popBackStack(targetRoute, inclusive = false)
                } else {
                    false
                }
                val movedToSearch = popped || navController.navigateToTopLevelSafely(targetRoute)
                if (!movedToSearch) {
                    lastSearchTapTimestamp = 0L
                    return
                }
            }
            if (isDoubleTap) {
                lastSearchTapTimestamp = 0L
                if (isAtRoot) {
                    onSearchDoubleTap()
                } else {
                    scope.launch {
                        delay(160L)
                        onSearchDoubleTap()
                    }
                }
            }
            return
        }

        lastSearchTapTimestamp = 0L
        if (isAtRoot) return

        // If user is inside a child route of the same section (e.g. AlbumDetail inside Library),
        // pop back to that section's root cleanly when possible.
        if (selectedDestination == destination) {
            val popped = navController.popBackStack(targetRoute, inclusive = false)
            if (popped) return
        }
        navController.navigateToTopLevelSafely(targetRoute)
    }

    Surface(
        modifier = modifier
            .width(resolvedWidth)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Vertical + WindowInsetsSides.Start
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(vertical = spacing.md),
            horizontalAlignment = Alignment.Start
        ) {
            if (supportsExpandToggle) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleExpanded) {
                        Icon(
                            imageVector = if (effectiveExpanded) {
                                Icons.AutoMirrored.Rounded.MenuOpen
                            } else {
                                Icons.Rounded.Menu
                            },
                            contentDescription = stringResource(
                                if (effectiveExpanded) R.string.nav_rail_collapse else R.string.nav_rail_expand
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedVisibility(
                        visible = effectiveExpanded,
                        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                            expandHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                            shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec())
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = spacing.xs)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(spacing.md))
            } else {
                Spacer(modifier = Modifier.height(spacing.sm))
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                horizontalAlignment = Alignment.Start
            ) {
                primaryItems.forEach { item ->
                    PixelPlayRailItem(
                        spec = item,
                        selected = selectedDestination == item.destination,
                        expanded = effectiveExpanded,
                        onClick = { onDestinationClick(item.destination) }
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.lg, horizontal = spacing.lg),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                horizontalAlignment = Alignment.Start
            ) {
                utilityItems.forEach { item ->
                    PixelPlayRailItem(
                        spec = item,
                        selected = selectedDestination == item.destination,
                        expanded = effectiveExpanded,
                        onClick = { onDestinationClick(item.destination) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PixelPlayRailItem(
    spec: RailItemSpec,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val spacing = LocalSpacing.current
    val motionScheme = MaterialTheme.motionScheme
    val label = stringResource(spec.labelRes)
    val interactionSource = remember { MutableInteractionSource() }

    val indicatorColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "RailIndicatorColor"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "RailIconColor"
    )
    val labelColor by animateColorAsState(
        targetValue = when {
            expanded && selected -> MaterialTheme.colorScheme.onSecondaryContainer
            selected -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "RailLabelColor"
    )

    val iconContent: @Composable () -> Unit = {
        when {
            spec.iconVector != null -> {
                Icon(
                    imageVector = spec.iconVector,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            else -> {
                val resId = if (selected && spec.selectedIconRes != null) {
                    spec.selectedIconRes
                } else {
                    spec.iconRes!!
                }
                Icon(
                    painter = painterResource(resId),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    AnimatedContent(
        targetState = expanded,
        transitionSpec = {
            (fadeIn(motionScheme.fastEffectsSpec()) togetherWith
                fadeOut(motionScheme.fastEffectsSpec()))
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.CenterStart,
        label = "RailItemExpandedTransition"
    ) { isExpandedItem ->
        if (isExpandedItem) {
            Row(
                modifier = Modifier
                    .padding(horizontal = spacing.md)
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
                    .semantics(mergeDescendants = true) { this.selected = selected }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true),
                        role = Role.Tab,
                        onClick = onClick
                    )
                    .padding(horizontal = spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                iconContent()
                Text(
                    text = label,
                    style = if (selected) {
                        MaterialTheme.typography.labelLargeEmphasized
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                    color = labelColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(horizontal = spacing.xs)
                    .width(NavigationRailCollapsedWidth - spacing.sm)
                    .defaultMinSize(minHeight = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .semantics(mergeDescendants = true) { this.selected = selected }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true),
                        role = Role.Tab,
                        onClick = onClick
                    )
                    .padding(vertical = spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(32.dp)
                        .clip(CircleShape)
                        .background(indicatorColor),
                    contentAlignment = Alignment.Center
                ) {
                    iconContent()
                }
                Text(
                    text = label,
                    style = if (selected) {
                        MaterialTheme.typography.labelMediumEmphasized
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                    color = labelColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = spacing.xs)
                )
            }
        }
    }
}
