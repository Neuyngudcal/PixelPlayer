package com.theveloper.pixelplay.presentation.navigation

internal fun isMainRootRoute(
    route: String?,
    useNavigationRail: Boolean = false
): Boolean = when (route) {
    Screen.Home.route,
    Screen.Search.route,
    Screen.Library.route -> true
    Screen.Equalizer.route,
    Screen.Settings.route -> useNavigationRail
    else -> false
}

/**
 * Position of a top-level destination in the visible navigation order, used to pick the slide
 * direction of tab switches. On the navigation rail (tablet) Equalizer and Settings are top-level
 * items below Library, so they join the order there; on the bottom bar they are pushed screens.
 */
internal fun mainRootRouteIndex(
    route: String?,
    useNavigationRail: Boolean = false
): Int? = when (route) {
    Screen.Home.route -> 0
    Screen.Search.route -> 1
    Screen.Library.route -> 2
    Screen.Equalizer.route -> if (useNavigationRail) 3 else null
    Screen.Settings.route -> if (useNavigationRail) 4 else null
    else -> null
}
