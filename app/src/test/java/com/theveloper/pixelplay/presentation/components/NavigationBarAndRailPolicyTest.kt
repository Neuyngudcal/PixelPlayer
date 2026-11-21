package com.theveloper.pixelplay.presentation.components

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.isMainRootRoute
import com.theveloper.pixelplay.ui.theme.WindowWidthTier
import com.theveloper.pixelplay.ui.theme.PixelPlayGroupShapes
import com.theveloper.pixelplay.ui.theme.computeEffectiveContentWindowInfo
import com.theveloper.pixelplay.ui.theme.computePixelPlayWindowInfo
import org.junit.jupiter.api.Test

class NavigationBarAndRailPolicyTest {

    @Test
    fun `resolveSelectedRailDestination maps all primary and nested destinations accurately`() {
        assertThat(resolveSelectedRailDestination(null)).isEqualTo(PixelPlayRailDestination.HOME)
        assertThat(resolveSelectedRailDestination(Screen.Home.route)).isEqualTo(PixelPlayRailDestination.HOME)
        assertThat(resolveSelectedRailDestination(Screen.DailyMixScreen.route)).isEqualTo(PixelPlayRailDestination.HOME)
        assertThat(resolveSelectedRailDestination(Screen.RecentlyPlayed.route)).isEqualTo(PixelPlayRailDestination.HOME)
        assertThat(resolveSelectedRailDestination(Screen.Stats.route)).isEqualTo(PixelPlayRailDestination.HOME)
        assertThat(resolveSelectedRailDestination(Screen.DJSpace.route)).isEqualTo(PixelPlayRailDestination.HOME)

        assertThat(resolveSelectedRailDestination(Screen.Search.route)).isEqualTo(PixelPlayRailDestination.SEARCH)
        assertThat(resolveSelectedRailDestination(Screen.GenreDetail.createRoute("indie")))
            .isEqualTo(PixelPlayRailDestination.SEARCH)

        assertThat(resolveSelectedRailDestination(Screen.Library.route)).isEqualTo(PixelPlayRailDestination.LIBRARY)
        assertThat(resolveSelectedRailDestination(Screen.AlbumDetail.createRoute(42L)))
            .isEqualTo(PixelPlayRailDestination.LIBRARY)
        assertThat(resolveSelectedRailDestination(Screen.ArtistDetail.createRoute(7L)))
            .isEqualTo(PixelPlayRailDestination.LIBRARY)
        assertThat(resolveSelectedRailDestination(Screen.PlaylistDetail.createRoute("favorites")))
            .isEqualTo(PixelPlayRailDestination.LIBRARY)
        assertThat(resolveSelectedRailDestination(Screen.EditTransition.route))
            .isEqualTo(PixelPlayRailDestination.LIBRARY)
        assertThat(resolveSelectedRailDestination(Screen.EditTransition.createRoute("p1")))
            .isEqualTo(PixelPlayRailDestination.LIBRARY)
        assertThat(resolveSelectedRailDestination(Screen.EditTransition.createRoute(null)))
            .isEqualTo(PixelPlayRailDestination.LIBRARY)

        assertThat(resolveSelectedRailDestination(Screen.Equalizer.route))
            .isEqualTo(PixelPlayRailDestination.EQUALIZER)

        listOf(
            Screen.Settings.route,
            Screen.SettingsCategory.createRoute("appearance"),
            Screen.About.route,
            Screen.PaletteStyle.route,
            Screen.ArtistSettings.route,
            Screen.DelimiterConfig.route,
            Screen.WordDelimiterConfig.route,
            Screen.NavBarCrRad.route,
            Screen.OpenSourceLicenses.route,
            Screen.DeviceCapabilities.route,
            Screen.Experimental.route,
            Screen.OfflineModelHub.route,
            Screen.EasterEgg.route
        ).forEach { settingsRoute ->
            assertThat(resolveSelectedRailDestination(settingsRoute))
                .isEqualTo(PixelPlayRailDestination.SETTINGS)
        }
    }

    @Test
    fun `bottom nav bar keeps safe floating padding when system gesture hint inset is zero`() {
        assertThat(resolveNavBarHorizontalPadding(NavBarStyle.DEFAULT, 0.dp)).isEqualTo(14.dp)
        assertThat(resolveNavBarBottomPadding(NavBarStyle.DEFAULT, 0.dp)).isEqualTo(12.dp)
        assertThat(resolveNavBarSurfaceHeight(NavBarStyle.DEFAULT, 0.dp, compactMode = false)).isEqualTo(90.dp)
        assertThat(resolveNavBarSurfaceHeight(NavBarStyle.DEFAULT, 0.dp, compactMode = true)).isEqualTo(64.dp)
        assertThat(resolveNavBarOccupiedHeight(0.dp, compactMode = false, navBarStyle = NavBarStyle.DEFAULT))
            .isEqualTo(102.dp)
        assertThat(resolveNavBarOccupiedHeight(0.dp, compactMode = true, navBarStyle = NavBarStyle.DEFAULT))
            .isEqualTo(76.dp)

        assertThat(resolveNavBarHorizontalPadding(NavBarStyle.FULL_WIDTH, 0.dp)).isEqualTo(0.dp)
        assertThat(resolveNavBarBottomPadding(NavBarStyle.FULL_WIDTH, 0.dp)).isEqualTo(0.dp)
        assertThat(resolveNavBarOccupiedHeight(0.dp, compactMode = false, navBarStyle = NavBarStyle.FULL_WIDTH))
            .isEqualTo(90.dp)
    }

    @Test
    fun `bottom nav bar handles gesture pill, 3-button bar, and OEM freeform clamped insets`() {
        // Gesture pill (~24dp)
        assertThat(resolveNavBarHorizontalPadding(NavBarStyle.DEFAULT, 24.dp)).isEqualTo(24.dp)
        assertThat(resolveNavBarBottomPadding(NavBarStyle.DEFAULT, 24.dp)).isEqualTo(24.dp)
        assertThat(resolveNavBarOccupiedHeight(24.dp, compactMode = false, navBarStyle = NavBarStyle.DEFAULT))
            .isEqualTo(114.dp)

        // 3-button navigation (48dp)
        assertThat(resolveNavBarHorizontalPadding(NavBarStyle.DEFAULT, 48.dp)).isEqualTo(14.dp)
        assertThat(resolveNavBarBottomPadding(NavBarStyle.DEFAULT, 48.dp)).isEqualTo(48.dp)
        assertThat(resolveNavBarSurfaceHeight(NavBarStyle.FULL_WIDTH, 48.dp, compactMode = true))
            .isEqualTo(112.dp)
        assertThat(resolveNavBarOccupiedHeight(48.dp, compactMode = false, navBarStyle = NavBarStyle.FULL_WIDTH))
            .isEqualTo(138.dp)

        // Malformed OEM freeform inset (200dp -> clamped to 96dp)
        assertThat(resolveNavBarHorizontalPadding(NavBarStyle.DEFAULT, 200.dp)).isEqualTo(14.dp)
        assertThat(resolveNavBarBottomPadding(NavBarStyle.DEFAULT, 200.dp)).isEqualTo(96.dp)
        assertThat(resolveNavBarOccupiedHeight(200.dp, compactMode = false, navBarStyle = NavBarStyle.FULL_WIDTH))
            .isEqualTo(186.dp)
    }

    @Test
    fun `main screen bottom gradient excludes bottom nav bar height in navigation rail mode`() {
        val phoneGradient = resolveMainScreenBottomGradientHeight(compactMode = false, useNavigationRail = false)
        val compactPhoneGradient = resolveMainScreenBottomGradientHeight(compactMode = true, useNavigationRail = false)
        val tabletGradient = resolveMainScreenBottomGradientHeight(compactMode = false, useNavigationRail = true)

        assertThat(phoneGradient).isEqualTo(NavBarContentHeight + tabletGradient)
        assertThat(compactPhoneGradient).isEqualTo(NavBarCompactContentHeight + tabletGradient)
        assertThat(tabletGradient).isLessThan(phoneGradient)
    }

    @Test
    fun `computeEffectiveContentWindowInfo accounts for rail and side-docked player while preserving rail state`() {
        val phoneInfo = computePixelPlayWindowInfo(widthDp = 411, heightDp = 915)
        assertThat(computeEffectiveContentWindowInfo(phoneInfo, navigationRailWidthDp = 0, sideDockedReservedWidthDp = 0))
            .isEqualTo(phoneInfo)

        // 1000dp tablet with 80dp rail + 436dp side-docked card -> 484dp remaining content width
        val tablet1000 = computePixelPlayWindowInfo(widthDp = 1000, heightDp = 900)
        val effective1000 = computeEffectiveContentWindowInfo(
            windowInfo = tablet1000,
            navigationRailWidthDp = 80,
            sideDockedReservedWidthDp = 436
        )
        assertThat(effective1000.widthDp).isEqualTo(484)
        assertThat(effective1000.widthTier).isEqualTo(WindowWidthTier.COMPACT)
        assertThat(effective1000.useTwoPaneSettings).isFalse()
        assertThat(effective1000.useNavigationRail).isTrue()

        // Tab S10 Ultra landscape 1480x924 with 220dp wide rail + 436dp side-docked -> 824dp remaining
        val tabUltra = computePixelPlayWindowInfo(widthDp = 1480, heightDp = 924)
        val effectiveTabUltra = computeEffectiveContentWindowInfo(
            windowInfo = tabUltra,
            navigationRailWidthDp = 220,
            sideDockedReservedWidthDp = 436
        )
        assertThat(effectiveTabUltra.widthDp).isEqualTo(824)
        assertThat(effectiveTabUltra.widthTier).isEqualTo(WindowWidthTier.MEDIUM)
        assertThat(effectiveTabUltra.useTwoPaneSettings).isTrue()
        assertThat(effectiveTabUltra.useNavigationRail).isTrue()
    }

    @Test
    fun `isMainRootRoute promotes Equalizer and Settings to root routes only in Navigation Rail mode`() {
        assertThat(isMainRootRoute(Screen.Home.route, useNavigationRail = false)).isTrue()
        assertThat(isMainRootRoute(Screen.Search.route, useNavigationRail = false)).isTrue()
        assertThat(isMainRootRoute(Screen.Library.route, useNavigationRail = false)).isTrue()
        assertThat(isMainRootRoute(Screen.Equalizer.route, useNavigationRail = false)).isFalse()
        assertThat(isMainRootRoute(Screen.Settings.route, useNavigationRail = false)).isFalse()
        assertThat(isMainRootRoute(Screen.AlbumDetail.route, useNavigationRail = false)).isFalse()

        assertThat(isMainRootRoute(Screen.Equalizer.route, useNavigationRail = true)).isTrue()
        assertThat(isMainRootRoute(Screen.Settings.route, useNavigationRail = true)).isTrue()
        assertThat(isMainRootRoute(Screen.SettingsCategory.route, useNavigationRail = true)).isFalse()
        assertThat(isMainRootRoute(Screen.AlbumDetail.route, useNavigationRail = true)).isFalse()
    }

    @Test
    fun `tablet pane margins and corner radius match Side-Docked Now Playing alignment`() {
        assertThat(resolveTabletPaneTopMargin(0.dp)).isEqualTo(12.dp)
        assertThat(resolveTabletPaneTopMargin(28.dp)).isEqualTo(40.dp)
        assertThat(resolveTabletPaneTopMargin((-4).dp)).isEqualTo(12.dp)

        assertThat(resolveTabletPaneBottomMargin(0.dp)).isEqualTo(12.dp)
        assertThat(resolveTabletPaneBottomMargin(24.dp)).isEqualTo(36.dp)
        assertThat(resolveTabletPaneBottomMargin((-8).dp)).isEqualTo(12.dp)

        assertThat(TabletPaneTokens.CornerRadius).isEqualTo(PixelPlayGroupShapes.PaneContainerRadius)
        assertThat(TabletPaneTokens.EndMargin).isEqualTo(16.dp)
        assertThat(TabletPaneTokens.PaneGap).isEqualTo(16.dp)
    }
}
