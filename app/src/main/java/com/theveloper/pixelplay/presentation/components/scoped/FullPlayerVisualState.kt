package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.util.lerp

/**
 * Holds references needed to compute full-player visual properties lazily.
 *
 * **Key design**: [contentAlpha] and [translationY] are computed on-demand via getters
 * that read [Animatable.value]. When called inside `graphicsLayer { }`, these reads
 * happen during the draw phase and trigger **re-draw only** — not recomposition.
 * This eliminates per-frame recomposition of the parent composable during gestures.
 */
internal class FullPlayerVisualState(
    private val expansionFraction: Animatable<Float, AnimationVector1D>,
    private val initialOffsetY: Float
) {
    /** Full-player fade-in with M3 Expressive decelerate curve: invisible until 20% expanded. */
    val contentAlpha: Float
        get() {
            val f = expansionFraction.value
            val raw = ((f - 0.20f) / 0.80f).coerceIn(0f, 1f)
            // Smooth cubic ease-out: 1 - (1 - x)^3
            val inv = 1f - raw
            return 1f - (inv * inv * inv)
        }

    /** Slide-up entrance driven by [contentAlpha]. */
    val translationY: Float
        get() = lerp(initialOffsetY, 0f, contentAlpha)

    /** Subtle spatial scale entrance (0.96f -> 1.0f) for M3 Expressive depth. */
    val contentScale: Float
        get() = lerp(0.96f, 1f, contentAlpha)
}

@Composable
internal fun rememberFullPlayerVisualState(
    expansionFraction: Animatable<Float, AnimationVector1D>,
    initialOffsetY: Float
): FullPlayerVisualState {
    return remember(expansionFraction, initialOffsetY) {
        FullPlayerVisualState(expansionFraction, initialOffsetY)
    }
}
