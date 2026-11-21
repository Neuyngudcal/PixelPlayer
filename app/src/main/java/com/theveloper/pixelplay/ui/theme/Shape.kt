package com.theveloper.pixelplay.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    largeIncreased = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(40.dp)
)

/**
 * Standardized Material 3 Expressive segmented group shapes.
 * Replaces scattered per-screen corner radii across Settings and grouped cards.
 */
object PixelPlayGroupShapes {
    val GroupOuterRadius: Dp = 20.dp
    val GroupInnerRadius: Dp = 4.dp
    val PaneContainerRadius: Dp = 28.dp
    val ActiveNavigationPillRadius: Dp = 16.dp

    fun segmentedItemShape(
        index: Int,
        count: Int,
        outerRadius: Dp = GroupOuterRadius,
        innerRadius: Dp = GroupInnerRadius
    ): RoundedCornerShape {
        if (count <= 1) return RoundedCornerShape(outerRadius)
        return when (index) {
            0 -> RoundedCornerShape(
                topStart = outerRadius,
                topEnd = outerRadius,
                bottomStart = innerRadius,
                bottomEnd = innerRadius
            )
            count - 1 -> RoundedCornerShape(
                topStart = innerRadius,
                topEnd = innerRadius,
                bottomStart = outerRadius,
                bottomEnd = outerRadius
            )
            else -> RoundedCornerShape(innerRadius)
        }
    }
}
