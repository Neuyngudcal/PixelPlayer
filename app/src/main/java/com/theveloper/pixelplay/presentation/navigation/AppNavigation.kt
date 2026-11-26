package com.theveloper.pixelplay.presentation.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import DelimiterConfigScreen
import com.theveloper.pixelplay.presentation.screens.WordDelimiterConfigScreen
import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.theveloper.pixelplay.data.preferences.LaunchTab
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.presentation.screens.AlbumDetailScreen
import com.theveloper.pixelplay.presentation.screens.ArtistDetailScreen
import com.theveloper.pixelplay.presentation.screens.ArtistSettingsScreen
import com.theveloper.pixelplay.presentation.screens.DailyMixScreen
import com.theveloper.pixelplay.presentation.screens.EditTransitionScreen
import com.theveloper.pixelplay.presentation.screens.GenreDetailScreen
import com.theveloper.pixelplay.presentation.screens.HomeScreen
import com.theveloper.pixelplay.presentation.screens.LibraryScreen
import com.theveloper.pixelplay.presentation.screens.NavBarCornerRadiusScreen
import com.theveloper.pixelplay.presentation.screens.PaletteStyleSettingsScreen
import com.theveloper.pixelplay.presentation.screens.PlaylistDetailScreen
import com.theveloper.pixelplay.presentation.screens.RecentlyPlayedScreen

import com.theveloper.pixelplay.presentation.screens.AboutScreen
import com.theveloper.pixelplay.presentation.screens.OpenSourceLicensesScreen
import com.theveloper.pixelplay.presentation.screens.SearchScreen
import com.theveloper.pixelplay.presentation.screens.StatsScreen
import com.theveloper.pixelplay.presentation.screens.SettingsScreen
import com.theveloper.pixelplay.presentation.screens.SettingsCategoryScreen
import com.theveloper.pixelplay.presentation.screens.EqualizerScreen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import kotlinx.coroutines.flow.first
import com.theveloper.pixelplay.presentation.components.ScreenWrapper
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayWindowInfo

@OptIn(UnstableApi::class)
@SuppressLint("UnrememberedGetBackStackEntry")
@Composable
fun AppNavigation(
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    paddingValues: PaddingValues,
    userPreferencesRepository: UserPreferencesRepository,
    onSearchBarActiveChange: (Boolean) -> Unit,
    onOpenSidebar: () -> Unit,
    initialLaunchTab: String? = null
) {
    var startDestination by remember {
        mutableStateOf(initialLaunchTab?.toRoute())
    }
    if (startDestination == null && initialLaunchTab != null) {
        startDestination = initialLaunchTab.toRoute()
    }

    LaunchedEffect(Unit) {
        if (startDestination == null) {
            startDestination = userPreferencesRepository.launchTabFlow
                .first()
                .toRoute()
        }
    }

    // Read through a State so a fold/unfold does not re-key (and rebuild) the NavHost graph; the
    // transition lambdas always see the latest value when a navigation starts.
    val useNavigationRailState = rememberUpdatedState(LocalPixelPlayWindowInfo.current.useNavigationRail)

    // M3 container transform (Library album card -> AlbumDetail): phone single-pane layout only.
    val albumHeroHandoff = remember { AlbumHeroHandoff() }
    val heroWindowInfo = LocalPixelPlayWindowInfo.current
    val heroAllowedState = rememberUpdatedState(
        EnableAlbumHeroTransition &&
            !heroWindowInfo.useNavigationRail &&
            !heroWindowInfo.useTwoPaneDetail
    )

    // Top-level destinations (bottom bar: Home/Search/Library; rail adds Equalizer/Settings) use the
    // M3 "Top level" fade through when switching between each other, and the AOSP push/pop otherwise.
    val mainRootEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        remember(useNavigationRailState) {
            {
                mainRootEnterTransition(
                    useNavigationRailState.value,
                    fallback = aospSharedAxisEnter(),
                    includeJumpFromPushedScreen = true
                )
            }
        }
    val mainRootExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        remember(useNavigationRailState) {
            {
                mainRootExitTransition(
                    useNavigationRailState.value,
                    fallback = if (isAlbumHeroTransition(albumHeroHandoff, heroAllowedState.value)) {
                        containerTransformExit()
                    } else {
                        aospSharedAxisExit()
                    }
                )
            }
        }
    val mainRootPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        remember(useNavigationRailState) {
            {
                mainRootEnterTransition(
                    useNavigationRailState.value,
                    fallback = if (isAlbumHeroTransition(albumHeroHandoff, heroAllowedState.value)) {
                        containerTransformEnter()
                    } else {
                        aospSharedAxisPopEnter()
                    }
                )
            }
        }
    val mainRootPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        remember(useNavigationRailState) {
            { mainRootExitTransition(useNavigationRailState.value, fallback = aospSharedAxisPopExit()) }
        }

    startDestination?.let { initialRoute ->
        AlbumHeroTransitionHost(albumHeroHandoff) {
        NavHost(
            navController = navController,
            startDestination = initialRoute,
            enterTransition = { aospSharedAxisEnter() },
            exitTransition = {
                if (isJumpToTopLevel(useNavigationRailState.value)) {
                    topLevelFadeThroughExit()
                } else {
                    aospSharedAxisExit()
                }
            },
            popEnterTransition = { aospSharedAxisPopEnter() },
            popExitTransition = { aospSharedAxisPopExit() }
        ) {
            composable(
                Screen.Home.route,
                enterTransition = mainRootEnter,
                exitTransition = mainRootExit,
                popEnterTransition = mainRootPopEnter,
                popExitTransition = mainRootPopExit,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                    HomeScreen(
                        navController = navController, 
                        paddingValuesParent = paddingValues, 
                        playerViewModel = playerViewModel,
                        onOpenSidebar = onOpenSidebar
                    )
                }
            }
            composable(
                Screen.Search.route,
                enterTransition = mainRootEnter,
                exitTransition = mainRootExit,
                popEnterTransition = mainRootPopEnter,
                popExitTransition = mainRootPopExit,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                    SearchScreen(
                        paddingValues = paddingValues,
                        playerViewModel = playerViewModel,
                        navController = navController,
                        onSearchBarActiveChange = onSearchBarActiveChange
                    )
                }
            }
            composable(
                Screen.Library.route,
                enterTransition = mainRootEnter,
                exitTransition = mainRootExit,
                popEnterTransition = mainRootPopEnter,
                popExitTransition = mainRootPopExit,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                    LibraryScreen(navController = navController, playerViewModel = playerViewModel)
                }
            }
            composable(
                Screen.Settings.route,
                enterTransition = mainRootEnter,
                exitTransition = mainRootExit,
                popEnterTransition = mainRootPopEnter,
                popExitTransition = mainRootPopExit,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                    SettingsScreen(
                        navController = navController,
                        playerViewModel = playerViewModel,
                        onNavigationIconClick = dropUnlessResumed { navController.popBackStackSafely() }
                    )
                }
            }
            composable(
                route = Screen.SettingsCategory.route,
                arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
            ) { backStackEntry ->
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this, wrapInTabletFloatingPane = true) {
                    val categoryId = backStackEntry.arguments?.getString("categoryId")
                    if (categoryId != null) {
                        SettingsCategoryScreen(
                            categoryId = categoryId,
                            navController = navController,
                            playerViewModel = playerViewModel,
                            onBackClick = dropUnlessResumed { navController.popBackStackSafely() }
                        )
                    }
                }
            }
            composable(
                Screen.PaletteStyle.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this, wrapInTabletFloatingPane = true) {
                    PaletteStyleSettingsScreen(
                        playerViewModel = playerViewModel,
                        onBackClick = dropUnlessResumed { navController.popBackStackSafely() }
                    )
                }
            }
            composable(
                Screen.DailyMixScreen.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this, wrapInTabletFloatingPane = true) {
                    DailyMixScreen(
                        playerViewModel = playerViewModel,
                        navController = navController
                    )
                }
            }
            composable(
                Screen.RecentlyPlayed.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this, wrapInTabletFloatingPane = true) {
                    RecentlyPlayedScreen(
                        playerViewModel = playerViewModel,
                        navController = navController
                    )
                }
            }
            composable(
                Screen.Stats.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this, wrapInTabletFloatingPane = true) {
                    StatsScreen(
                        navController = navController
                    )
                }
            }
            composable(
                route = Screen.PlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId")
                val playlistViewModel: PlaylistViewModel = hiltViewModel()
                if (playlistId != null) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        PlaylistDetailScreen(
                            playlistId = playlistId,
                            playerViewModel = playerViewModel,
                            playlistViewModel = playlistViewModel,
                            onBackClick = dropUnlessResumed { navController.popBackStackSafely() },
                            onDeletePlayListClick = { navController.popBackStackSafely() },
                            navController = navController
                        )
                    }
                }
            }

            composable(
                route = Screen.GenreDetail.route,
                arguments = listOf(navArgument("genreId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val genreId = backStackEntry.arguments?.getString("genreId")
                if (genreId != null) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        GenreDetailScreen(
                            navController = navController,
                            genreId = genreId,
                            playerViewModel = playerViewModel
                        )
                    }
                } else {
                    Text(stringResource(R.string.nav_error_genre_id_missing), modifier = Modifier)
                }
            }
            composable(
                route = Screen.AlbumDetail.route,
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
                enterTransition = {
                    if (isAlbumHeroTransition(albumHeroHandoff, heroAllowedState.value)) {
                        containerTransformEnter()
                    } else {
                        aospSharedAxisEnter()
                    }
                },
                popExitTransition = {
                    if (isAlbumHeroTransition(albumHeroHandoff, heroAllowedState.value)) {
                        containerTransformExit()
                    } else {
                        aospSharedAxisPopExit()
                    }
                },
            ) { backStackEntry ->
                val albumId = backStackEntry.arguments?.getString("albumId")
                if (albumId != null) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        AlbumDetailScreen(
                            albumId = albumId,
                            navController = navController,
                            playerViewModel = playerViewModel
                        )
                    }
                }
            }
            composable(
                route = Screen.ArtistDetail.route,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val artistId = backStackEntry.arguments?.getString("artistId")
                if (artistId != null) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        ArtistDetailScreen(
                            artistId = artistId,
                            navController = navController,
                            playerViewModel = playerViewModel
                        )
                    }
                }
            }
            composable(
                "nav_bar_corner_radius",
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this, wrapInTabletFloatingPane = true) {
                    NavBarCornerRadiusScreen(navController)
                }
            }
            composable(
                route = Screen.EditTransition.route,
                arguments = listOf(navArgument("playlistId") {
                    type = NavType.StringType
                    nullable = true
                }),
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this, wrapInTabletFloatingPane = true) {
                    EditTransitionScreen(navController = navController)
                }
            }
            composable(
                Screen.About.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this, wrapInTabletFloatingPane = true) {
                    AboutScreen(
                        navController = navController,
                        viewModel = playerViewModel,
                        onNavigationIconClick = dropUnlessResumed { navController.popBackStackSafely() }
                    )
                }
            }
            composable(
                Screen.OpenSourceLicenses.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this, wrapInTabletFloatingPane = true) {
                    OpenSourceLicensesScreen(
                        onBackClick = dropUnlessResumed { navController.popBackStackSafely() },
                    )
                }
            }
            composable(
                Screen.ArtistSettings.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this, wrapInTabletFloatingPane = true) {
                    ArtistSettingsScreen(navController = navController)
                }
            }
            composable(
                Screen.DelimiterConfig.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this, wrapInTabletFloatingPane = true) {
                    DelimiterConfigScreen(navController = navController)
                }
            }
            composable(
                Screen.WordDelimiterConfig.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this, wrapInTabletFloatingPane = true) {
                    WordDelimiterConfigScreen(navController = navController)
                }
            }
            composable(
                Screen.Equalizer.route,
                enterTransition = mainRootEnter,
                exitTransition = mainRootExit,
                popEnterTransition = mainRootPopEnter,
                popExitTransition = mainRootPopExit,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                    EqualizerScreen(
                        navController = navController,
                        playerViewModel = playerViewModel
                    )
                }
            }
            composable(
                Screen.OfflineModelHub.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this, wrapInTabletFloatingPane = true) {
                    com.theveloper.pixelplay.presentation.screens.OfflineModelHubScreen(
                        navController = navController
                    )
                }
            }
        }
        } // AlbumHeroTransitionHost
    }
}

private fun String.toRoute(): String = when (this) {
    LaunchTab.SEARCH -> Screen.Search.route
    LaunchTab.LIBRARY -> Screen.Library.route
    else -> Screen.Home.route
}

/**
 * M3 "Top level" transition pattern: switching between navigation bar / rail destinations is a
 * fade through, never a lateral slide (a slide implies the destinations can be swiped, which
 * conflicts with carousels and swipeable list items). Applies to both navigate and pop, so a
 * predictive back gesture between two top-level destinations scrubs the same fade through.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTopLevelSwitch(
    useNavigationRail: Boolean
): Boolean {
    val fromRoute = initialState.destination.route
    val toRoute = targetState.destination.route
    return fromRoute != toRoute &&
        isMainRootRoute(fromRoute, useNavigationRail) &&
        isMainRootRoute(toRoute, useNavigationRail)
}

/**
 * A non-pop navigation from a pushed screen straight to a top-level destination (a navigation bar
 * or rail tap while a detail screen is open). Only used by non-pop transitions: a real back
 * navigation to a top-level destination keeps the backward slide.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isJumpToTopLevel(
    useNavigationRail: Boolean
): Boolean =
    !isMainRootRoute(initialState.destination.route, useNavigationRail) &&
        isMainRootRoute(targetState.destination.route, useNavigationRail)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.mainRootEnterTransition(
    useNavigationRail: Boolean,
    fallback: EnterTransition,
    includeJumpFromPushedScreen: Boolean = false
): EnterTransition =
    if (
        isTopLevelSwitch(useNavigationRail) ||
        (includeJumpFromPushedScreen && isJumpToTopLevel(useNavigationRail))
    ) {
        topLevelFadeThroughEnter()
    } else {
        fallback
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.mainRootExitTransition(
    useNavigationRail: Boolean,
    fallback: ExitTransition
): ExitTransition =
    if (isTopLevelSwitch(useNavigationRail)) topLevelFadeThroughExit() else fallback
