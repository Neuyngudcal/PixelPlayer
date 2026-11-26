package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.presentation.components.player.TabletPlayerDisplayMode
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun PlayerAlbumNavigationEffect(
    navController: NavHostController,
    sheetCollapsedTargetY: Float,
    sheetMotionController: SheetMotionController,
    playerViewModel: PlayerViewModel,
    supportsSideDockedMode: Boolean = false,
    tabletDisplayMode: TabletPlayerDisplayMode = TabletPlayerDisplayMode.FULLSCREEN,
    onSwitchToSideDocked: () -> Unit = {},
    onDismissSubOverlays: () -> Unit = {}
) {
    val latestSheetCollapsedTargetY by rememberUpdatedState(sheetCollapsedTargetY)
    val latestSupportsSideDockedMode by rememberUpdatedState(supportsSideDockedMode)
    val latestTabletDisplayMode by rememberUpdatedState(tabletDisplayMode)
    val latestOnSwitchToSideDocked by rememberUpdatedState(onSwitchToSideDocked)
    val latestOnDismissSubOverlays by rememberUpdatedState(onDismissSubOverlays)

    LaunchedEffect(navController) {
        playerViewModel.albumNavigationRequests.collectLatest { albumId ->
            latestOnDismissSubOverlays()
            if (latestSupportsSideDockedMode) {
                if (latestTabletDisplayMode == TabletPlayerDisplayMode.FULLSCREEN) {
                    latestOnSwitchToSideDocked()
                }
            } else {
                sheetMotionController.snapCollapsed(latestSheetCollapsedTargetY)
                playerViewModel.collapsePlayerSheet()
            }

            navController.navigateSafelyReplacing(
                route = Screen.AlbumDetail.createRoute(albumId),
                patternToPop = Screen.AlbumDetail.route
            )
        }
    }
}
