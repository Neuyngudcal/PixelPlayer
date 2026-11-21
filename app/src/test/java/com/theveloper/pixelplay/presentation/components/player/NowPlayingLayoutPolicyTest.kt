package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.computePixelPlayWindowInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingLayoutPolicyTest {

    @Test
    fun `phone portrait resolves to PORTRAIT_COMPACT without side docked mode`() {
        val windowInfo = computePixelPlayWindowInfo(widthDp = 411, heightDp = 915)
        val bounds = resolveNowPlayingSheetBoundsSpec(windowInfo)
        val metrics = resolveNowPlayingLayoutMetrics(windowInfo, TabletPlayerDisplayMode.SIDE_DOCKED)

        assertFalse(bounds.supportsSideDockedMode)
        assertEquals(NowPlayingLayoutMode.PORTRAIT_COMPACT, metrics.mode)
        assertTrue(metrics.showTopUtilityBar)
        assertFalse(metrics.supportsInlineSidePanel)
        assertFalse(metrics.supportsSideDockedMode)
    }

    @Test
    fun `phone landscape resolves to COMPACT_LANDSCAPE and hides top utility bar`() {
        val windowInfo = computePixelPlayWindowInfo(widthDp = 915, heightDp = 411)
        val bounds = resolveNowPlayingSheetBoundsSpec(windowInfo)
        val metrics = resolveNowPlayingLayoutMetrics(windowInfo, TabletPlayerDisplayMode.FULLSCREEN)

        assertFalse(bounds.supportsSideDockedMode)
        assertEquals(NowPlayingLayoutMode.COMPACT_LANDSCAPE, metrics.mode)
        assertFalse(metrics.showTopUtilityBar)
        assertFalse(metrics.supportsInlineSidePanel)
    }

    @Test
    fun `split screen tablet portrait 620x924 resolves to PORTRAIT_TABLET_FULLSCREEN`() {
        val windowInfo = computePixelPlayWindowInfo(widthDp = 620, heightDp = 924)
        val bounds = resolveNowPlayingSheetBoundsSpec(windowInfo)
        val metrics = resolveNowPlayingLayoutMetrics(windowInfo, TabletPlayerDisplayMode.SIDE_DOCKED)

        assertFalse(bounds.supportsSideDockedMode)
        assertEquals(NowPlayingLayoutMode.PORTRAIT_TABLET_FULLSCREEN, metrics.mode)
        assertTrue(metrics.showTopUtilityBar)
        assertEquals(560.dp, metrics.controlsMaxWidth)
    }

    @Test
    fun `galaxy tab s10 ultra portrait 924x1480 supports SIDE_DOCKED and PORTRAIT_TABLET_FULLSCREEN`() {
        val windowInfo = computePixelPlayWindowInfo(widthDp = 924, heightDp = 1480)
        val bounds = resolveNowPlayingSheetBoundsSpec(windowInfo)
        val dockedMetrics = resolveNowPlayingLayoutMetrics(windowInfo, TabletPlayerDisplayMode.SIDE_DOCKED)
        val fullscreenMetrics = resolveNowPlayingLayoutMetrics(windowInfo, TabletPlayerDisplayMode.FULLSCREEN)

        assertTrue(bounds.supportsSideDockedMode)
        assertEquals(384.dp, bounds.sideDockedWidth)
        assertEquals(NowPlayingLayoutMode.SIDE_DOCKED_PORTRAIT, dockedMetrics.mode)
        assertTrue(dockedMetrics.showTopUtilityBar)
        assertEquals(NowPlayingLayoutMode.PORTRAIT_TABLET_FULLSCREEN, fullscreenMetrics.mode)
        assertTrue(fullscreenMetrics.showTopUtilityBar)
    }

    @Test
    fun `galaxy tab s10 ultra landscape 1480x924 supports SIDE_DOCKED 408dp and TABLET_LANDSCAPE_FULLSCREEN`() {
        val windowInfo = computePixelPlayWindowInfo(widthDp = 1480, heightDp = 924)
        val bounds = resolveNowPlayingSheetBoundsSpec(windowInfo)
        val dockedMetrics = resolveNowPlayingLayoutMetrics(windowInfo, TabletPlayerDisplayMode.SIDE_DOCKED)
        val fullscreenMetrics = resolveNowPlayingLayoutMetrics(windowInfo, TabletPlayerDisplayMode.FULLSCREEN)

        assertTrue(bounds.supportsSideDockedMode)
        assertEquals(408.dp, bounds.sideDockedWidth)

        assertEquals(NowPlayingLayoutMode.SIDE_DOCKED_PORTRAIT, dockedMetrics.mode)
        assertTrue(dockedMetrics.showTopUtilityBar)
        assertEquals(408.dp, dockedMetrics.controlsMaxWidth)

        assertEquals(NowPlayingLayoutMode.TABLET_LANDSCAPE_FULLSCREEN, fullscreenMetrics.mode)
        assertFalse(fullscreenMetrics.showTopUtilityBar)
        assertTrue(fullscreenMetrics.supportsInlineSidePanel)
        assertTrue(fullscreenMetrics.supportsSideDockedMode)
    }

    @Test
    fun `samsung dex 1920x1080 supports both SIDE_DOCKED and inline side panel in FULLSCREEN`() {
        val windowInfo = computePixelPlayWindowInfo(widthDp = 1920, heightDp = 1080)
        val dockedMetrics = resolveNowPlayingLayoutMetrics(windowInfo, TabletPlayerDisplayMode.SIDE_DOCKED)
        val fullscreenMetrics = resolveNowPlayingLayoutMetrics(windowInfo, TabletPlayerDisplayMode.FULLSCREEN)

        assertEquals(NowPlayingLayoutMode.SIDE_DOCKED_PORTRAIT, dockedMetrics.mode)
        assertEquals(NowPlayingLayoutMode.TABLET_LANDSCAPE_FULLSCREEN, fullscreenMetrics.mode)
        assertTrue(fullscreenMetrics.supportsInlineSidePanel)
    }
}
