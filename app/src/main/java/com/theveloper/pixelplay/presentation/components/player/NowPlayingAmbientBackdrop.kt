package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.max

/**
 * Draw-phase Material 3 Expressive Ambient Tonal Radial Glow backdrop for Now Playing.
 *
 * Reads [ColorScheme] strictly inside [drawBehind] so album palette crossfades
 * (`rememberBatchAnimatedColorScheme`) never trigger composition or layout passes.
 */
fun Modifier.nowPlayingAmbientBackdrop(
    colorSchemeProvider: () -> ColorScheme,
    isDarkTheme: Boolean,
    focalCenterXFraction: Float = 0.28f,
    focalCenterYFraction: Float = 0.42f
): Modifier = this.drawBehind {
    val scheme = colorSchemeProvider()
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return@drawBehind

    // 1. Base tonal container canvas
    drawRect(color = scheme.primaryContainer)

    val maxDim = max(w, h)

    // 2. Primary hero focal glow behind album art
    val focalRadius = (maxDim * 0.72f).coerceAtLeast(1f)
    val focalCenter = Offset(
        x = w * focalCenterXFraction.coerceIn(0.1f, 0.9f),
        y = h * focalCenterYFraction.coerceIn(0.1f, 0.9f)
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                scheme.tertiaryContainer.copy(alpha = if (isDarkTheme) 0.34f else 0.28f),
                Color.Transparent
            ),
            center = focalCenter,
            radius = focalRadius
        )
    )

    // 3. Counter-balance secondary ambient glow at bottom-end
    val secondaryRadius = (maxDim * 0.65f).coerceAtLeast(1f)
    val secondaryCenter = Offset(
        x = w * 0.84f,
        y = h * 0.82f
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                scheme.secondaryContainer.copy(alpha = if (isDarkTheme) 0.28f else 0.22f),
                Color.Transparent
            ),
            center = secondaryCenter,
            radius = secondaryRadius
        )
    )

    // 4. Subtle bottom depth vignette to anchor transport controls
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                scheme.surfaceContainerLowest.copy(alpha = if (isDarkTheme) 0.18f else 0.08f)
            ),
            startY = h * 0.45f,
            endY = h
        )
    )
}
