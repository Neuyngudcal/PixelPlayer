package com.theveloper.pixelplay.presentation.theme

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import com.theveloper.pixelplay.shared.WearThemePalette

@Immutable
data class WearPalette(
    val gradientTop: Color,
    val gradientMiddle: Color,
    val gradientBottom: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textError: Color,
    val controlContainer: Color,
    val controlContent: Color,
    val controlDisabledContainer: Color,
    val controlDisabledContent: Color,
    val transportContainer: Color,
    val transportContent: Color,
    val chipContainer: Color,
    val chipContent: Color,
    val favoriteActive: Color,
    val shuffleActive: Color,
    val repeatActive: Color,
)

private val DefaultWearPalette: WearPalette by lazy {
    WearColorExtractor.buildDefaultBrandPalette(contrastLevel = 0.0)
}

val LocalWearPalette = staticCompositionLocalOf { DefaultWearPalette }

fun WearPalette.radialBackgroundBrush(): Brush = Brush.radialGradient(
    colorStops = arrayOf(
        0.0f to gradientTop,
        0.40f to gradientMiddle,
        0.74f to gradientBottom,
        0.92f to Color.Black,
        1.0f to Color.Black,
    ),
)

fun WearPalette.screenBackgroundColor(): Color = Color.Black
fun WearPalette.surfaceContainerLowestColor(): Color = surfaceContainerLowest
fun WearPalette.surfaceContainerLowColor(): Color = surfaceContainerLow
fun WearPalette.surfaceContainerColor(): Color = surfaceContainer
fun WearPalette.surfaceContainerHighColor(): Color = surfaceContainerHigh
fun WearPalette.surfaceContainerHighestColor(): Color = surfaceContainerHighest

@Composable
fun WearPixelPlayTheme(
    albumArt: Bitmap? = null,
    seedColorArgb: Int? = null,
    themePalette: WearThemePalette? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemContrast = remember(context) {
        WearColorExtractor.readWearSystemContrast(context)
    }
    val palette = remember(themePalette, albumArt, seedColorArgb, systemContrast) {
        when {
            themePalette != null -> themePalette.toWearPalette()
            seedColorArgb != null -> WearColorExtractor.buildWearPaletteFromSeed(seedColorArgb, systemContrast)
            albumArt != null -> WearColorExtractor.buildWearPaletteFromBitmap(albumArt, systemContrast)
            systemContrast != 0.0 -> WearColorExtractor.buildDefaultBrandPalette(systemContrast)
            else -> DefaultWearPalette
        }
    }
    val wearColors = remember(palette) {
        Colors(
            primary = palette.controlContainer,
            primaryVariant = lerp(palette.controlContainer, Color.Black, 0.18f),
            secondary = palette.chipContainer,
            secondaryVariant = lerp(palette.chipContainer, Color.Black, 0.24f),
            error = palette.textError,
            onPrimary = palette.controlContent,
            onSecondary = palette.textPrimary,
            onError = Color.Black,
        )
    }

    CompositionLocalProvider(LocalWearPalette provides palette) {
        MaterialTheme(
            colors = wearColors,
            content = content,
        )
    }
}

private fun WearThemePalette.toWearPalette(): WearPalette {
    val surface = colorOrDefault(surfaceContainerArgb, Color(chipContainerArgb))
    val surfaceLowest = colorOrDefault(surfaceContainerLowestArgb, lerp(surface, Color.Black, 0.16f))
    val surfaceLow = colorOrDefault(surfaceContainerLowArgb, lerp(surface, Color.Black, 0.06f))
    val surfaceHigh = colorOrDefault(surfaceContainerHighArgb, lerp(surface, Color.White, 0.08f))
    val surfaceHighest = colorOrDefault(surfaceContainerHighestArgb, lerp(surface, Color.White, 0.14f))
    val disabledContainer = colorOrDefault(controlDisabledContainerArgb, surfaceHighest)
    val transportContainer = colorOrDefault(transportContainerArgb, Color(controlContainerArgb))

    return WearPalette(
        gradientTop = Color(gradientTopArgb),
        gradientMiddle = Color(gradientMiddleArgb),
        gradientBottom = Color(gradientBottomArgb),
        surfaceContainerLowest = surfaceLowest,
        surfaceContainerLow = surfaceLow,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHighest,
        textPrimary = Color(textPrimaryArgb),
        textSecondary = Color(textSecondaryArgb),
        textError = Color(textErrorArgb),
        controlContainer = Color(controlContainerArgb),
        controlContent = Color(controlContentArgb),
        controlDisabledContainer = disabledContainer,
        controlDisabledContent = colorOrDefault(controlDisabledContentArgb, bestContrastContent(disabledContainer)),
        transportContainer = transportContainer,
        transportContent = colorOrDefault(
            transportContentArgb,
            colorOrDefault(controlContentArgb, bestContrastContent(transportContainer)),
        ),
        chipContainer = colorOrDefault(chipContainerArgb, surface),
        chipContent = Color(chipContentArgb),
        favoriteActive = Color(favoriteActiveArgb),
        shuffleActive = Color(shuffleActiveArgb),
        repeatActive = Color(repeatActiveArgb),
    )
}

private fun colorOrDefault(argb: Int, fallback: Color): Color {
    return if (argb != 0) Color(argb) else fallback
}

private fun bestContrastContent(background: Color): Color {
    val light = Color(0xFFF6F2FF)
    val dark = Color(0xFF17141E)
    val opaqueBackgroundArgb = if (background.alpha >= 0.999f) {
        background.toArgb()
    } else {
        ColorUtils.compositeColors(
            background.toArgb(),
            Color.Black.toArgb(),
        )
    }
    val lightContrast = ColorUtils.calculateContrast(light.toArgb(), opaqueBackgroundArgb)
    val darkContrast = ColorUtils.calculateContrast(dark.toArgb(), opaqueBackgroundArgb)
    return if (lightContrast >= darkContrast) light else dark
}
