package com.theveloper.pixelplay.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AdaptiveLayoutTest {

    @Test
    fun `phone portrait 411x915 resolves to Compact single-pane bottom-bar layout`() {
        val info = computePixelPlayWindowInfo(widthDp = 411, heightDp = 915)
        val spacing = spacingForWidthTier(info.widthTier)

        assertThat(info.widthTier).isEqualTo(WindowWidthTier.COMPACT)
        assertThat(info.heightTier).isEqualTo(WindowHeightTier.EXPANDED)
        assertThat(info.isCompactWidth).isTrue()
        assertThat(info.isTabletLayout).isFalse()
        assertThat(info.useNavigationRail).isFalse()
        assertThat(info.supportsExpandableWideRail).isFalse()
        assertThat(info.useTwoPaneSettings).isFalse()
        assertThat(info.useTwoPaneDetail).isFalse()
        assertThat(info.useTwoColumnPlayer).isFalse()
        assertThat(info.miniPlayerMaxWidth).isEqualTo(Dp.Unspecified)
        assertThat(info.mediaGridMinCellSize).isEqualTo(160.dp)
        assertThat(spacing.screenHorizontal).isEqualTo(16.dp)
    }

    @Test
    fun `samsung split-screen 620x924 resolves to Medium rail single-pane settings`() {
        val info = computePixelPlayWindowInfo(widthDp = 620, heightDp = 924)
        val spacing = spacingForWidthTier(info.widthTier)

        assertThat(info.widthTier).isEqualTo(WindowWidthTier.MEDIUM)
        assertThat(info.useNavigationRail).isTrue()
        assertThat(info.supportsExpandableWideRail).isFalse()
        assertThat(info.useTwoPaneSettings).isFalse()
        assertThat(info.useTwoPaneDetail).isFalse()
        assertThat(info.useTwoColumnPlayer).isFalse()
        assertThat(info.miniPlayerMaxWidth).isEqualTo(560.dp)
        assertThat(spacing.screenHorizontal).isEqualTo(20.dp)
    }

    @Test
    fun `galaxy tab s10 ultra portrait 924x1480 resolves to Expanded rail and two-pane settings and detail`() {
        val info = computePixelPlayWindowInfo(widthDp = 924, heightDp = 1480)
        val spacing = spacingForWidthTier(info.widthTier)

        assertThat(info.widthTier).isEqualTo(WindowWidthTier.EXPANDED)
        assertThat(info.useNavigationRail).isTrue()
        assertThat(info.supportsExpandableWideRail).isFalse()
        assertThat(info.useTwoPaneSettings).isTrue()
        assertThat(info.useTwoPaneDetail).isTrue()
        assertThat(info.useTwoColumnHome).isTrue()
        assertThat(info.useTwoColumnStats).isTrue()
        // Portrait aspect (924 / 1480 < 0.9) keeps vertical full-player layout
        assertThat(info.useTwoColumnPlayer).isFalse()
        assertThat(info.settingsListPanePreferredWidth).isEqualTo(340.dp)
        assertThat(info.mediaGridMinCellSize).isEqualTo(176.dp)
        assertThat(spacing.screenHorizontal).isEqualTo(24.dp)
        assertThat(spacing.maxReadableContentWidth).isEqualTo(840.dp)
    }

    @Test
    fun `galaxy tab s10 ultra landscape 1480x924 resolves to Large expandable rail and full two-column suite`() {
        val info = computePixelPlayWindowInfo(widthDp = 1480, heightDp = 924)
        val spacing = spacingForWidthTier(info.widthTier)

        assertThat(info.widthTier).isEqualTo(WindowWidthTier.LARGE)
        assertThat(info.useNavigationRail).isTrue()
        assertThat(info.supportsExpandableWideRail).isTrue()
        assertThat(info.startWithExpandedWideRail).isFalse()
        assertThat(info.useTwoPaneSettings).isTrue()
        assertThat(info.useTwoPaneDetail).isTrue()
        assertThat(info.useTwoColumnPlayer).isTrue()
        assertThat(info.useTwoColumnHome).isTrue()
        assertThat(info.useTwoColumnStats).isTrue()
        assertThat(info.settingsListPanePreferredWidth).isEqualTo(360.dp)
        assertThat(info.detailHeroPaneWidth).isEqualTo(380.dp)
        assertThat(info.mediaGridMinCellSize).isEqualTo(192.dp)
        assertThat(info.miniPlayerMaxWidth).isEqualTo(560.dp)
        assertThat(spacing.screenHorizontal).isEqualTo(24.dp)
        assertThat(spacing.paneGap).isEqualTo(20.dp)
    }

    @Test
    fun `samsung dex external monitor 1920x1080 resolves to ExtraLarge with expanded wide rail`() {
        val info = computePixelPlayWindowInfo(widthDp = 1920, heightDp = 1080)
        val spacing = spacingForWidthTier(info.widthTier)

        assertThat(info.widthTier).isEqualTo(WindowWidthTier.EXTRA_LARGE)
        assertThat(info.useNavigationRail).isTrue()
        assertThat(info.supportsExpandableWideRail).isTrue()
        assertThat(info.startWithExpandedWideRail).isTrue()
        assertThat(info.useTwoPaneSettings).isTrue()
        assertThat(info.useTwoPaneDetail).isTrue()
        assertThat(info.useTwoColumnPlayer).isTrue()
        assertThat(info.settingsListPanePreferredWidth).isEqualTo(380.dp)
        assertThat(spacing.screenHorizontal).isEqualTo(32.dp)
        assertThat(spacing.maxReadableContentWidth).isEqualTo(920.dp)
    }

    @Test
    fun `segmented group shape rounds only outer corners for multi-item groups`() {
        val single = PixelPlayGroupShapes.segmentedItemShape(index = 0, count = 1)
        val first = PixelPlayGroupShapes.segmentedItemShape(index = 0, count = 3)
        val middle = PixelPlayGroupShapes.segmentedItemShape(index = 1, count = 3)
        val last = PixelPlayGroupShapes.segmentedItemShape(index = 2, count = 3)

        assertThat(single.topStart).isEqualTo(first.topStart)
        assertThat(single.bottomEnd).isEqualTo(last.bottomEnd)
        assertThat(first.bottomStart).isEqualTo(middle.topStart)
        assertThat(last.topEnd).isEqualTo(middle.bottomEnd)
    }
}
