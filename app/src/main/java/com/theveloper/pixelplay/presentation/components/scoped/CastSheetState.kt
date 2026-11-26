package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal data class CastSheetState(
    val showCastSheet: Boolean,
    /** Draw/overlay-phase provider so cast sheet animations never recompose parent sheet layers. */
    val castSheetOpenFractionProvider: () -> Float,
    val openCastSheet: () -> Unit,
    val dismissCastSheet: () -> Unit,
    val onCastExpansionChanged: (Float) -> Unit
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberCastSheetState(): CastSheetState {
    var showCastSheet by remember { mutableStateOf(false) }
    val motionScheme = MaterialTheme.motionScheme
    val castSheetOpenFractionState = animateFloatAsState(
        targetValue = if (showCastSheet) 1f else 0f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "castSheetOpenFraction"
    )
    val castSheetOpenFractionProvider = remember {
        { castSheetOpenFractionState.value }
    }

    val openCastSheet = remember {
        { showCastSheet = true }
    }
    val dismissCastSheet = remember {
        { showCastSheet = false }
    }
    val onCastExpansionChanged = remember {
        { _: Float -> Unit }
    }

    return CastSheetState(
        showCastSheet = showCastSheet,
        castSheetOpenFractionProvider = castSheetOpenFractionProvider,
        openCastSheet = openCastSheet,
        dismissCastSheet = dismissCastSheet,
        onCastExpansionChanged = onCastExpansionChanged
    )
}
