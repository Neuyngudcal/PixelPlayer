package com.theveloper.pixelplay.data.service.wear

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Blend
import com.google.android.material.color.utilities.Contrast
import com.google.android.material.color.utilities.Hct
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.shared.WearThemePalette
import com.theveloper.pixelplay.ui.theme.extractSeedColor
import com.theveloper.pixelplay.ui.theme.generateColorSchemeFromSeed
import kotlin.math.roundToInt

private const val FAVORITE_DESIGN_COLOR_ARGB = 0xFFFF5C8A.toInt()
private const val FALLBACK_LIGHT_CONTENT_ARGB = 0xFFF6F2FF.toInt()
private const val FALLBACK_DARK_CONTENT_ARGB = 0xFF17141E.toInt()

/**
 * Maps a Google Material 3 dark [ColorScheme] into a compact [WearThemePalette] for OLED watch UIs.
 *
 * Uses Material Color Utilities HCT tonal stops for gradients and [Blend.harmonize] for semantic
 * accents instead of HSL hue shifting or RGB black/white lerping.
 */
internal fun buildWearThemePalette(darkScheme: ColorScheme): WearThemePalette {
    val surfaceContainer = darkScheme.surfaceContainer.toArgb()
    val surfaceContainerLowest = darkScheme.surfaceContainerLowest.toArgb()
    val surfaceContainerLow = darkScheme.surfaceContainerLow.toArgb()
    val surfaceContainerHigh = darkScheme.surfaceContainerHigh.toArgb()
    val surfaceContainerHighest = darkScheme.surfaceContainerHighest.toArgb()

    val primaryArgb = darkScheme.primary.toArgb()
    val playContainer = darkScheme.onPrimaryContainer.toArgb()
    val playContent = darkScheme.primaryContainer.toArgb()
    val transportContainer = primaryArgb
    val transportContent = darkScheme.onPrimary.toArgb()
    val chipContainer = darkScheme.secondaryContainer.toArgb()

    // HCT perceptual tonal stops preserve artwork hue depth on OLED screens without RGB muddying
    val primaryHct = Hct.fromInt(primaryArgb)
    val surfaceHct = Hct.fromInt(surfaceContainer)
    val accentChroma = primaryHct.chroma.coerceAtMost(36.0)
    val gradientTop = Hct.from(primaryHct.hue, (accentChroma * 0.65).coerceAtMost(28.0), 20.0).toInt()
    val gradientMiddle = Hct.from(surfaceHct.hue, (accentChroma * 0.35).coerceAtMost(16.0), 10.0).toInt()
    val gradientBottom = Hct.from(surfaceHct.hue, (accentChroma * 0.20).coerceAtMost(10.0), 4.0).toInt()

    val favoriteActive = harmonizeWearAccent(
        designColorArgb = FAVORITE_DESIGN_COLOR_ARGB,
        sourceColorArgb = primaryArgb,
        backgroundArgb = surfaceContainer
    )
    val shuffleActive = ensureWearReadable(
        preferredColor = darkScheme.tertiary.toArgb(),
        backgroundColor = surfaceContainer
    )
    val repeatActive = ensureWearReadable(
        preferredColor = darkScheme.secondary.toArgb(),
        backgroundColor = surfaceContainer
    )

    return WearThemePalette(
        gradientTopArgb = gradientTop,
        gradientMiddleArgb = gradientMiddle,
        gradientBottomArgb = gradientBottom,
        surfaceContainerLowestArgb = surfaceContainerLowest,
        surfaceContainerLowArgb = surfaceContainerLow,
        surfaceContainerArgb = surfaceContainer,
        surfaceContainerHighArgb = surfaceContainerHigh,
        surfaceContainerHighestArgb = surfaceContainerHighest,
        textPrimaryArgb = ensureWearReadable(
            preferredColor = darkScheme.onSurface.toArgb(),
            backgroundColor = gradientMiddle
        ),
        textSecondaryArgb = ensureWearReadable(
            preferredColor = darkScheme.onSurfaceVariant.toArgb(),
            backgroundColor = gradientBottom
        ),
        textErrorArgb = ensureWearReadable(
            preferredColor = darkScheme.error.toArgb(),
            backgroundColor = gradientBottom
        ),
        controlContainerArgb = playContainer,
        controlContentArgb = ensureWearReadable(
            preferredColor = playContent,
            backgroundColor = playContainer
        ),
        controlDisabledContainerArgb = surfaceContainerHighest,
        controlDisabledContentArgb = ensureWearReadable(
            preferredColor = darkScheme.onSurfaceVariant.toArgb(),
            backgroundColor = surfaceContainerHighest
        ),
        transportContainerArgb = transportContainer,
        transportContentArgb = ensureWearReadable(
            preferredColor = transportContent,
            backgroundColor = transportContainer
        ),
        chipContainerArgb = chipContainer,
        chipContentArgb = ensureWearReadable(
            preferredColor = darkScheme.onSecondaryContainer.toArgb(),
            backgroundColor = chipContainer
        ),
        favoriteActiveArgb = favoriteActive,
        shuffleActiveArgb = shuffleActive,
        repeatActiveArgb = repeatActive
    )
}

/**
 * Extracts a Google MCU seed color from [bitmap] (`QuantizerCelebi` + `Score.score`),
 * synthesizes the corresponding Material 3 dark scheme, and maps it to [WearThemePalette].
 */
internal fun buildWearThemePalette(
    bitmap: Bitmap,
    paletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.default,
    contrastLevel: Double = 0.0
): WearThemePalette {
    val seed: Color = extractSeedColor(bitmap)
    val schemePair = generateColorSchemeFromSeed(
        seedColor = seed,
        paletteStyle = paletteStyle,
        contrastLevel = contrastLevel
    )
    return buildWearThemePalette(schemePair.dark)
}

internal fun harmonizeWearAccent(
    designColorArgb: Int,
    sourceColorArgb: Int,
    backgroundArgb: Int
): Int {
    val harmonized = Blend.harmonize(designColorArgb, sourceColorArgb)
    val hct = Hct.fromInt(harmonized)
    val elevated = Hct.from(hct.hue, hct.chroma.coerceAtLeast(36.0), hct.tone.coerceIn(66.0, 82.0)).toInt()
    return ensureWearReadable(preferredColor = elevated, backgroundColor = backgroundArgb)
}

internal fun ensureWearReadable(preferredColor: Int, backgroundColor: Int): Int {
    val opaqueBackground = compositeOverOpaqueBlack(backgroundColor)
    val opaquePreferred = compositeOverOpaqueBlack(preferredColor)
    val bgTone = Hct.fromInt(opaqueBackground).tone
    val preferredContrast = Contrast.ratioOfTones(Hct.fromInt(opaquePreferred).tone, bgTone)
    if (preferredContrast >= Contrast.RATIO_30) return preferredColor

    val lightContrast = Contrast.ratioOfTones(Hct.fromInt(FALLBACK_LIGHT_CONTENT_ARGB).tone, bgTone)
    val darkContrast = Contrast.ratioOfTones(Hct.fromInt(FALLBACK_DARK_CONTENT_ARGB).tone, bgTone)
    return if (lightContrast >= darkContrast) FALLBACK_LIGHT_CONTENT_ARGB else FALLBACK_DARK_CONTENT_ARGB
}

private fun compositeOverOpaqueBlack(argb: Int): Int {
    val alpha = (argb ushr 24) and 0xFF
    if (alpha >= 255) return argb
    val fraction = alpha / 255.0
    val red = (((argb ushr 16) and 0xFF) * fraction).roundToInt().coerceIn(0, 255)
    val green = (((argb ushr 8) and 0xFF) * fraction).roundToInt().coerceIn(0, 255)
    val blue = ((argb and 0xFF) * fraction).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}
