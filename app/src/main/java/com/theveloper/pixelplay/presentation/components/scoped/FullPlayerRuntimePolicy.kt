package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState

internal data class FullPlayerRuntimePolicy(
    val allowRealtimeUpdates: Boolean
)

/**
 * Gates high-frequency UI updates (progress bar sampling, animations) behind
 * conditions that only flip at expansion thresholds — not on every frame.
 *
 * [expansionFraction] and [bottomSheetOpenFractionProvider] are read inside [derivedStateOf],
 * producing a Boolean that changes only when crossing the 0.985 / 0.95 / 0.08 thresholds.
 * This avoids per-frame recomposition of the caller during gestures.
 */
@Composable
internal fun rememberFullPlayerRuntimePolicy(
    currentSheetState: PlayerSheetState,
    expansionFraction: Animatable<Float, AnimationVector1D>,
    bottomSheetOpenFractionProvider: () -> Float
): FullPlayerRuntimePolicy {
    val allowRealtimeUpdates by remember(currentSheetState, expansionFraction, bottomSheetOpenFractionProvider) {
        derivedStateOf {
            val ef = expansionFraction.value
            // Compute content alpha inline (same formula as FullPlayerVisualState).
            val alpha = (ef - 0.25f).coerceIn(0f, 0.75f) / 0.75f
            val isOccluded = bottomSheetOpenFractionProvider() >= 0.08f

            currentSheetState == PlayerSheetState.EXPANDED &&
                ef >= 0.985f &&
                alpha >= 0.95f &&
                !isOccluded
        }
    }

    return FullPlayerRuntimePolicy(
        allowRealtimeUpdates = allowRealtimeUpdates
    )
}
