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
