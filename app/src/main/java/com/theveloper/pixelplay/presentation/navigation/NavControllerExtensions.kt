package com.theveloper.pixelplay.presentation.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

private fun NavController.isCurrentEntryAtLeast(state: Lifecycle.State): Boolean {
    return runCatching {
        currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(state) == true
    }.getOrDefault(false)
}

/**
 * Push navigations (detail screens, settings pages) only fire once the current entry is RESUMED,
 * i.e. after its enter transition has settled. While a transition runs the outgoing screen is still
 * on screen and still receives touches, so a second tap would otherwise push another destination on
 * top of the one that is animating in (same idea as `dropUnlessResumed`).
 */
private fun NavController.isReadyForPushNavigation(): Boolean =
    isCurrentEntryAtLeast(Lifecycle.State.RESUMED)

/**
 * Top-level tab switches stay responsive during transitions: they always pop back to the start
 * destination with `launchSingleTop`, so a fast second tap cannot stack screens.
 */
private fun NavController.isReadyForTopLevelNavigation(): Boolean =
    isCurrentEntryAtLeast(Lifecycle.State.STARTED)

fun NavController.navigateSafely(route: String): Boolean {
    if (!isReadyForPushNavigation()) return false
    navigate(route) {
        launchSingleTop = true
    }
    return true
}

fun NavController.navigateSafely(
    route: String,
    builder: NavOptionsBuilder.() -> Unit
): Boolean {
    if (!isReadyForPushNavigation()) return false
    navigate(route) {
        launchSingleTop = true
        builder()
    }
    return true
}

/**
 * Navigates to [route], replacing the current entry only when it is the same kind of destination
 * ([patternToPop], e.g. AlbumDetail → AlbumDetail). Other entries in the back stack are kept, so
 * Album A → Artist B → Album C still returns to Artist B on back instead of jumping past it.
 */
fun NavController.navigateSafelyReplacing(
    route: String,
    patternToPop: String,
    builder: NavOptionsBuilder.() -> Unit = {}
): Boolean {
    if (!isReadyForPushNavigation()) return false
    val topDestination = currentBackStackEntry?.destination
    navigate(route) {
        launchSingleTop = false
        if (topDestination != null && topDestination.route == patternToPop) {
            popUpTo(topDestination.id) {
                inclusive = true
            }
        }
        builder()
    }
    return true
}

fun NavController.navigateToTopLevelSafely(route: String): Boolean {
    if (!isReadyForTopLevelNavigation()) return false
    val startDestinationId = runCatching { graph.startDestinationId }.getOrNull() ?: return false
    return runCatching {
        navigate(route) {
            popUpTo(startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }.isSuccess
}

/**
 * Back from a toolbar button. Never pops the start destination (that leaves the NavHost empty, i.e. a
 * blank screen) and is ignored while a transition is still running (a double tap would pop two screens).
 *
 * In composables, wrap the click in Google's `dropUnlessResumed { navController.popBackStackSafely() }`
 * so the tap is dropped against the screen's own entry lifecycle; the RESUMED check here also covers
 * callers outside the NavHost (player sheet, effects).
 */
fun NavController.popBackStackSafely(): Boolean {
    if (!isCurrentEntryAtLeast(Lifecycle.State.RESUMED)) return false
    if (previousBackStackEntry == null) return false
    return popBackStack()
}
