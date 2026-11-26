package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Centralizes sheet motion updates so animation/snap logic lives in one place.
 * This keeps behavior stable while enabling an incremental V2 rewrite.
 */
internal class SheetMotionController(
    private val translationY: Animatable<Float, AnimationVector1D>,
    private val expansionFraction: Animatable<Float, AnimationVector1D>,
    private val mutex: MutatorMutex,
    private val defaultAnimationSpec: AnimationSpec<Float>,
    private val expandedY: Float = 0f
) {
    @Volatile
    private var activeTargetExpanded: Boolean? = null

    suspend fun animateTo(
        targetExpanded: Boolean,
        canExpand: Boolean,
        collapsedY: Float,
        animationSpec: AnimationSpec<Float> = defaultAnimationSpec,
        initialVelocity: Float = 0f
    ) {
        val effectiveTargetExpanded = canExpand && targetExpanded
        val targetFraction = if (effectiveTargetExpanded) 1f else 0f
        val targetY = if (targetExpanded) expandedY else collapsedY
        val velocityScale = (collapsedY - expandedY).coerceAtLeast(1f)

        // If a velocity-preserving gesture fling is already animating toward this exact target,
        // do not cancel and restart it with initialVelocity = 0f from LaunchedEffect.
        if (
            activeTargetExpanded == effectiveTargetExpanded &&
            (translationY.isRunning || expansionFraction.isRunning) &&
            initialVelocity == 0f
        ) {
            return
        }

        if (
            translationY.value == targetY &&
            expansionFraction.value == targetFraction &&
            !translationY.isRunning &&
            !expansionFraction.isRunning
        ) {
            return
        }

        mutex.mutate {
            activeTargetExpanded = effectiveTargetExpanded
            try {
                coroutineScope {
                    launch {
                        translationY.animateTo(
                            targetValue = targetY,
                            initialVelocity = initialVelocity,
                            animationSpec = animationSpec
                        )
                    }
                    launch {
                        val fractionAnimationSpec = if (animationSpec is SpringSpec<Float>) {
                            spring(
                                dampingRatio = animationSpec.dampingRatio,
                                stiffness = animationSpec.stiffness,
                                visibilityThreshold = 0.0005f
                            )
                        } else {
                            animationSpec
                        }
                        expansionFraction.animateTo(
                            targetValue = targetFraction,
                            initialVelocity = initialVelocity / velocityScale,
                            animationSpec = fractionAnimationSpec
                        )
                    }
                }
            } finally {
                if (activeTargetExpanded == effectiveTargetExpanded) {
                    activeTargetExpanded = null
                }
            }
        }
    }

    suspend fun stop() {
        activeTargetExpanded = null
        translationY.stop()
        expansionFraction.stop()
    }

    suspend fun snapTo(translationYValue: Float, expansionFractionValue: Float) {
        activeTargetExpanded = null
        mutex.mutate {
            translationY.snapTo(translationYValue)
            expansionFraction.snapTo(expansionFractionValue)
        }
    }

    suspend fun snapCollapsed(collapsedY: Float) {
        snapTo(
            translationYValue = collapsedY,
            expansionFractionValue = 0f
        )
    }

    suspend fun syncToExpansion(collapsedY: Float) {
        val adjustedY = collapsedY + (expandedY - collapsedY) * expansionFraction.value
        if (translationY.value == adjustedY && !translationY.isRunning) return
        mutex.mutate {
            translationY.snapTo(adjustedY)
        }
    }
}
