package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.presentation.navigation.navigateToTopLevelSafely

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.BottomNavItem
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.presentation.components.scoped.CustomNavigationBarItem
import com.theveloper.pixelplay.presentation.navigation.Screen
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val NavBarContentHeight = 90.dp // Altura del contenido de la barra de navegación
internal val NavBarCompactContentHeight = 64.dp
internal val NavBarContentHeightFullWidth = NavBarContentHeight // Altura del contenido de la barra de navegación en modo completo
internal val NavBarDefaultMinHorizontalPadding = 14.dp
internal val NavBarDefaultMinBottomPadding = 12.dp
private val MainScreenBottomGradientExtraHeight = MiniPlayerHeight + MiniPlayerBottomSpacer + 8.dp
// Some OEM freeform/floating-window modes can report a bottom inset close to the whole window height.
internal val MaxNavigationBarBottomInset = 96.dp

internal fun sanitizeNavigationBarBottomInset(systemNavBarInset: Dp): Dp {
    if (!systemNavBarInset.value.isFinite()) return 0.dp
    return systemNavBarInset.coerceIn(0.dp, MaxNavigationBarBottomInset)
}

internal fun resolveNavBarHorizontalPadding(
    navBarStyle: String,
    systemNavBarInset: Dp
): Dp {
    if (navBarStyle != NavBarStyle.DEFAULT) return 0.dp
    val safeInset = sanitizeNavigationBarBottomInset(systemNavBarInset)
    return if (safeInset > 30.dp || safeInset < NavBarDefaultMinHorizontalPadding) {
        NavBarDefaultMinHorizontalPadding
    } else {
        safeInset
    }
}

internal fun resolveNavBarBottomPadding(
    navBarStyle: String,
    systemNavBarInset: Dp
): Dp {
    if (navBarStyle == NavBarStyle.FULL_WIDTH) return 0.dp
    val safeInset = sanitizeNavigationBarBottomInset(systemNavBarInset)
    return maxOf(safeInset, NavBarDefaultMinBottomPadding)
}

internal fun calculatePlayerSheetCollapsedTargetY(
    containerHeightPx: Float,
    collapsedContentHeightPx: Float,
    bottomMarginPx: Float,
    bottomSpacerPx: Float
): Float {
    val safeContainerHeightPx = containerHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeCollapsedContentHeightPx = collapsedContentHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeBottomMarginPx = bottomMarginPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeBottomSpacerPx = bottomSpacerPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val maxTargetY = (safeContainerHeightPx - safeCollapsedContentHeightPx).coerceAtLeast(0f)

    return (safeContainerHeightPx - safeCollapsedContentHeightPx - safeBottomMarginPx - safeBottomSpacerPx)
        .coerceIn(0f, maxTargetY)
}

internal fun resolveNavBarContentHeight(compactMode: Boolean): Dp =
    if (compactMode) NavBarCompactContentHeight else NavBarContentHeight

internal fun resolveMainScreenBottomGradientHeight(
    compactMode: Boolean,
    useNavigationRail: Boolean = false
): Dp = if (useNavigationRail) {
    MainScreenBottomGradientExtraHeight
} else {
    resolveNavBarContentHeight(compactMode) + MainScreenBottomGradientExtraHeight
}

internal fun resolveNavBarSurfaceHeight(
    navBarStyle: String,
    systemNavBarInset: Dp,
    compactMode: Boolean
): Dp {
    val contentHeight = resolveNavBarContentHeight(compactMode)
    val safeInset = sanitizeNavigationBarBottomInset(systemNavBarInset)
    return if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        contentHeight + safeInset
    } else {
        contentHeight
    }
}

internal fun resolveNavBarOccupiedHeight(
    systemNavBarInset: Dp,
    compactMode: Boolean,
    navBarStyle: String = NavBarStyle.DEFAULT
): Dp {
    val contentHeight = resolveNavBarContentHeight(compactMode)
    return if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        contentHeight + sanitizeNavigationBarBottomInset(systemNavBarInset)
    } else {
        contentHeight + resolveNavBarBottomPadding(navBarStyle, systemNavBarInset)
    }
}

@Composable
private fun PlayerInternalNavigationItemsRow(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    navBarStyle: String,
    compactMode: Boolean,
    bottomBarPadding: Dp,
    navigationEnabled: Boolean = true,
    onSearchIconDoubleTap: () -> Unit
) {
    val navBarInsetPadding = sanitizeNavigationBarBottomInset(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    )
    // Maintain invariant: bottomBarPadding + innerRowPadding = the sanitized system nav bar inset.
    // This prevents nav items from appearing behind the gesture bar during style transitions,
    // e.g. FULL_WIDTH→DEFAULT where bottomBarPadding starts at 0 and animates to systemNavBarInset.
    val innerRowPadding = (navBarInsetPadding - bottomBarPadding).coerceAtLeast(0.dp)
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestOnSearchIconDoubleTap by rememberUpdatedState(onSearchIconDoubleTap)
    val isInteractive = currentRoute != null && navigationEnabled
    val latestNavigationEnabled by rememberUpdatedState(isInteractive)

    val rowModifier = if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = innerRowPadding, start = 12.dp, end = 12.dp)
    } else {
        modifier
            .padding(start = 10.dp, end = 10.dp, bottom = innerRowPadding)
            .fillMaxWidth()
    }
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val scope = rememberCoroutineScope()
        var lastSearchTapTimestamp by remember { mutableStateOf(0L) }
        navItems.forEach { item ->
            val isSelected = currentRoute != null && currentRoute == item.screen.route
            val selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer
            val selectedTextColor = MaterialTheme.colorScheme.onSurface
            val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            val indicatorColorFromTheme = MaterialTheme.colorScheme.secondaryContainer

            val unselectedIconResId = item.iconResId
            val selectedIconResId = item.selectedIconResId?.takeIf { it != 0 } ?: item.iconResId
            val localizedLabel = stringResource(id = item.labelResId)
            val iconLambda: @Composable () -> Unit = remember(unselectedIconResId, localizedLabel) {
                {
                    Icon(
                        painter = painterResource(id = unselectedIconResId),
                        contentDescription = localizedLabel
                    )
                }
            }
            val selectedIconLambda: @Composable () -> Unit = remember(selectedIconResId, localizedLabel) {
                {
                    Icon(
                        painter = painterResource(id = selectedIconResId),
                        contentDescription = localizedLabel
                    )
                }
            }
            val labelLambda: (@Composable () -> Unit)? = if (compactMode) {
                null
            } else {
                remember(localizedLabel) {
                    {
                        Text(
                            text = localizedLabel,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            val onClickLambda: () -> Unit = remember(item.screen.route, navController, scope) {
                click@{
                    if (!latestNavigationEnabled) {
                        lastSearchTapTimestamp = 0L
                        return@click
                    }

                    val itemRoute = item.screen.route
                    val isSearchTab = itemRoute == Screen.Search.route
                    val isAlreadySelected = latestCurrentRoute == itemRoute

                    if (isSearchTab) {
                        val now = SystemClock.elapsedRealtime()
                        val isDoubleTap = now - lastSearchTapTimestamp <= 350L
                        lastSearchTapTimestamp = now

                        if (!isAlreadySelected) {
                            if (!navController.navigateToTopLevelSafely(itemRoute)) {
                                lastSearchTapTimestamp = 0L
                                return@click
                            }
                        }

                        if (isDoubleTap) {
                            lastSearchTapTimestamp = 0L
                            if (isAlreadySelected) {
                                latestOnSearchIconDoubleTap()
                            } else {
                                scope.launch {
                                    delay(160L)
                                    latestOnSearchIconDoubleTap()
                                }
                            }
                        }
                    } else if (!isAlreadySelected) {
                        lastSearchTapTimestamp = 0L
                        navController.navigateToTopLevelSafely(itemRoute)
                    } else {
                        lastSearchTapTimestamp = 0L
                    }
                }
            }
            CustomNavigationBarItem(
                modifier = Modifier.weight(1f),
                selected = isSelected,
                onClick = onClickLambda,
                enabled = isInteractive,
                icon = iconLambda,
                selectedIcon = selectedIconLambda,
                label = labelLambda,
                contentDescription = localizedLabel,
                alwaysShowLabel = true,
                selectedIconColor = selectedIconColor,
                unselectedIconColor = unselectedColor,
                selectedTextColor = selectedTextColor,
                unselectedTextColor = unselectedColor,
                indicatorColor = indicatorColorFromTheme
            )
        }
    }
}

@Composable
fun PlayerInternalNavigationBar(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    navBarStyle: String,
    compactMode: Boolean,
    bottomBarPadding: Dp = 0.dp,
    navigationEnabled: Boolean = true,
    onSearchIconDoubleTap: () -> Unit = {}
) {
    PlayerInternalNavigationItemsRow(
        navController = navController,
        navItems = navItems,
        currentRoute = currentRoute,
        navBarStyle = navBarStyle,
        compactMode = compactMode,
        bottomBarPadding = bottomBarPadding,
        navigationEnabled = navigationEnabled,
        onSearchIconDoubleTap = onSearchIconDoubleTap,
        modifier = modifier
    )
}
