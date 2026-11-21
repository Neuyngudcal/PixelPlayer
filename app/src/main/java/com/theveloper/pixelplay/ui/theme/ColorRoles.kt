package com.theveloper.pixelplay.ui.theme

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.scale
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.SchemeContent
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFidelity
import com.google.android.material.color.utilities.SchemeFruitSalad
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.SchemeNeutral
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant
import com.google.android.material.color.utilities.Score
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Google Material Color Utilities (Android Monet / SystemUI Media) color extraction
 * and 48-role Material 3 [ColorScheme] generation pipeline.
 *
 * Pipeline matches Android Framework (`WallpaperColors`) and Material Components (`DynamicColors`):
 * 1. Area-bounded aspect-preserving downscale (<= 112x112 = 12,544 px).
 * 2. [QuantizerCelebi.quantize] with max 128 clusters.
 * 3. [Score.score] CAM16/HCT ranking with neutral fallback for achromatic artwork.
 * 4. [DynamicScheme] generation supporting all 8 Google variants and system contrast [-1.0, 1.0].
 */
internal const val GOOGLE_EXTRACTION_MAX_DIMENSION = 112
private const val MAX_EXTRACTION_AREA = GOOGLE_EXTRACTION_MAX_DIMENSION * GOOGLE_EXTRACTION_MAX_DIMENSION
private const val QUANTIZER_MAX_COLORS = 128
private const val SCORE_DESIRED_COUNT = 4
internal const val ACHROMATIC_CHROMA_CUTOFF = 5.0
internal const val NEUTRAL_SEED_ARGB = 0xFF808080.toInt()

/**
 * Quantizes a contrast level in `[-1.0, 1.0]` to an integer hundredths bucket for stable cache keys.
 */
fun quantizeContrastLevel(contrastLevel: Double): Int =
    (contrastLevel.coerceIn(-1.0, 1.0) * 100.0).roundToInt()

/**
 * Extracts the primary Google Material You seed color ARGB from [bitmap] using
 * [QuantizerCelebi] and [Score.score].
 */
fun extractSeedColorArgb(bitmap: Bitmap): Int {
    if (bitmap.width <= 0 || bitmap.height <= 0 || bitmap.isRecycled) {
        return NEUTRAL_SEED_ARGB
    }

    var softwareBitmap: Bitmap? = null
    var scaledBitmap: Bitmap? = null
    return try {
        val readableBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)?.also { softwareBitmap = it }
                ?: return NEUTRAL_SEED_ARGB
        } else {
            bitmap
        }

        val workingBitmap = resizeToExtractionArea(readableBitmap).also { resized ->
            if (resized !== readableBitmap) {
                scaledBitmap = resized
            }
        }

        val pixels = IntArray(workingBitmap.width * workingBitmap.height)
        workingBitmap.getPixels(
            pixels,
            0,
            workingBitmap.width,
            0,
            0,
            workingBitmap.width,
            workingBitmap.height
        )
        selectSeedColorArgbFromPixels(pixels)
    } catch (_: Exception) {
        NEUTRAL_SEED_ARGB
    } finally {
        scaledBitmap?.let { if (!it.isRecycled) it.recycle() }
        softwareBitmap?.let { if (!it.isRecycled) it.recycle() }
    }
}

fun extractSeedColor(bitmap: Bitmap): Color = Color(extractSeedColorArgb(bitmap))

/**
 * Runs Google's official [QuantizerCelebi] + [Score.score] pipeline directly on ARGB pixels.
 *
 * Unlike the 1-arg `Score.score(quantized)` overload (which falls back to Google Blue `#4285F4`),
 * this passes [NEUTRAL_SEED_ARGB] as the fallback so pure black-and-white / grayscale artwork
 * naturally resolves to an achromatic seed and renders via [SchemeMonochrome].
 */
internal fun selectSeedColorArgbFromPixels(pixels: IntArray): Int {
    if (pixels.isEmpty()) return NEUTRAL_SEED_ARGB
    val quantized = QuantizerCelebi.quantize(pixels, QUANTIZER_MAX_COLORS)
    if (quantized.isEmpty()) return NEUTRAL_SEED_ARGB
    val ranked = Score.score(
        quantized,
        SCORE_DESIRED_COUNT,
        NEUTRAL_SEED_ARGB,
        /* filter = */ true
    )
    return ranked.firstOrNull() ?: NEUTRAL_SEED_ARGB
}

fun generateColorSchemeFromSeed(
    seedColor: Color,
    paletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.default,
    contrastLevel: Double = 0.0
): ColorSchemePair {
    return runCatching {
        val clampedContrast = contrastLevel.coerceIn(-1.0, 1.0)
        val sourceHct = Hct.fromInt(seedColor.toArgb())
        val effectiveStyle = if (sourceHct.chroma < ACHROMATIC_CHROMA_CUTOFF) {
            AlbumArtPaletteStyle.MONOCHROME
        } else {
            paletteStyle
        }

        val lightScheme = createDynamicScheme(
            sourceHct = sourceHct,
            paletteStyle = effectiveStyle,
            isDark = false,
            contrastLevel = clampedContrast
        ).toComposeColorScheme()
        val darkScheme = createDynamicScheme(
            sourceHct = sourceHct,
            paletteStyle = effectiveStyle,
            isDark = true,
            contrastLevel = clampedContrast
        ).toComposeColorScheme()

        ColorSchemePair(light = lightScheme, dark = darkScheme)
    }.getOrElse {
        ColorSchemePair(light = lightColorScheme(), dark = darkColorScheme())
    }
}

fun generateMonochromeColorSchemeFromSeed(
    seedColor: Color,
    contrastLevel: Double = 0.0
): ColorSchemePair {
    return runCatching {
        val clampedContrast = contrastLevel.coerceIn(-1.0, 1.0)
        val sourceHct = Hct.fromInt(seedColor.toArgb())
        ColorSchemePair(
            light = SchemeMonochrome(sourceHct, false, clampedContrast).toComposeColorScheme(),
            dark = SchemeMonochrome(sourceHct, true, clampedContrast).toComposeColorScheme()
        )
    }.getOrElse {
        ColorSchemePair(light = lightColorScheme(), dark = darkColorScheme())
    }
}

private fun resizeToExtractionArea(bitmap: Bitmap): Bitmap {
    val area = bitmap.width.toLong() * bitmap.height.toLong()
    if (area <= MAX_EXTRACTION_AREA.toLong()) return bitmap
    val scaleFactor = sqrt(MAX_EXTRACTION_AREA.toDouble() / area.toDouble())
    val targetWidth = (bitmap.width * scaleFactor).roundToInt().coerceAtLeast(1)
    val targetHeight = (bitmap.height * scaleFactor).roundToInt().coerceAtLeast(1)
    return bitmap.scale(width = targetWidth, height = targetHeight)
}

internal fun createDynamicScheme(
    sourceHct: Hct,
    paletteStyle: AlbumArtPaletteStyle,
    isDark: Boolean,
    contrastLevel: Double
): DynamicScheme {
    return when (paletteStyle) {
        AlbumArtPaletteStyle.CONTENT -> SchemeContent(sourceHct, isDark, contrastLevel)
        AlbumArtPaletteStyle.FIDELITY -> SchemeFidelity(sourceHct, isDark, contrastLevel)
        AlbumArtPaletteStyle.TONAL_SPOT -> SchemeTonalSpot(sourceHct, isDark, contrastLevel)
        AlbumArtPaletteStyle.VIBRANT -> SchemeVibrant(sourceHct, isDark, contrastLevel)
        AlbumArtPaletteStyle.EXPRESSIVE -> SchemeExpressive(sourceHct, isDark, contrastLevel)
        AlbumArtPaletteStyle.FRUIT_SALAD -> SchemeFruitSalad(sourceHct, isDark, contrastLevel)
        AlbumArtPaletteStyle.MONOCHROME -> SchemeMonochrome(sourceHct, isDark, contrastLevel)
        AlbumArtPaletteStyle.NEUTRAL -> SchemeNeutral(sourceHct, isDark, contrastLevel)
    }
}

internal fun DynamicScheme.toComposeColorScheme(): ColorScheme {
    return ColorScheme(
        primary = Color(getPrimary()),
        onPrimary = Color(getOnPrimary()),
        primaryContainer = Color(getPrimaryContainer()),
        onPrimaryContainer = Color(getOnPrimaryContainer()),
        inversePrimary = Color(getInversePrimary()),
        secondary = Color(getSecondary()),
        onSecondary = Color(getOnSecondary()),
        secondaryContainer = Color(getSecondaryContainer()),
        onSecondaryContainer = Color(getOnSecondaryContainer()),
        tertiary = Color(getTertiary()),
        onTertiary = Color(getOnTertiary()),
        tertiaryContainer = Color(getTertiaryContainer()),
        onTertiaryContainer = Color(getOnTertiaryContainer()),
        background = Color(getBackground()),
        onBackground = Color(getOnBackground()),
        surface = Color(getSurface()),
        onSurface = Color(getOnSurface()),
        surfaceVariant = Color(getSurfaceVariant()),
        onSurfaceVariant = Color(getOnSurfaceVariant()),
        surfaceTint = Color(getSurfaceTint()),
        inverseSurface = Color(getInverseSurface()),
        inverseOnSurface = Color(getInverseOnSurface()),
        error = Color(getError()),
        onError = Color(getOnError()),
        errorContainer = Color(getErrorContainer()),
        onErrorContainer = Color(getOnErrorContainer()),
        outline = Color(getOutline()),
        outlineVariant = Color(getOutlineVariant()),
        scrim = Color(getScrim()),
        surfaceBright = Color(getSurfaceBright()),
        surfaceDim = Color(getSurfaceDim()),
        surfaceContainer = Color(getSurfaceContainer()),
        surfaceContainerHigh = Color(getSurfaceContainerHigh()),
        surfaceContainerHighest = Color(getSurfaceContainerHighest()),
        surfaceContainerLow = Color(getSurfaceContainerLow()),
        surfaceContainerLowest = Color(getSurfaceContainerLowest()),
        primaryFixed = Color(getPrimaryFixed()),
        primaryFixedDim = Color(getPrimaryFixedDim()),
        onPrimaryFixed = Color(getOnPrimaryFixed()),
        onPrimaryFixedVariant = Color(getOnPrimaryFixedVariant()),
        secondaryFixed = Color(getSecondaryFixed()),
        secondaryFixedDim = Color(getSecondaryFixedDim()),
        onSecondaryFixed = Color(getOnSecondaryFixed()),
        onSecondaryFixedVariant = Color(getOnSecondaryFixedVariant()),
        tertiaryFixed = Color(getTertiaryFixed()),
        tertiaryFixedDim = Color(getTertiaryFixedDim()),
        onTertiaryFixed = Color(getOnTertiaryFixed()),
        onTertiaryFixedVariant = Color(getOnTertiaryFixedVariant())
    )
}
