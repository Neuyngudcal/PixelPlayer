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

internal fun mainRootRouteIndex(route: String?): Int? = when (route) {
    Screen.Home.route -> 0
    Screen.Search.route -> 1
    Screen.Library.route -> 2
    else -> null
}
