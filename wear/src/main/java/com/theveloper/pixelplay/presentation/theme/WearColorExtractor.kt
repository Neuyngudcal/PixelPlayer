package com.theveloper.pixelplay.presentation.theme

import android.app.UiModeManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.scale
import com.google.android.material.color.utilities.Blend
import com.google.android.material.color.utilities.Contrast
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.SchemeContent
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.Score
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Google Material Color Utilities extractor and palette synthesizer for standalone Wear OS playback.
 *
 * Uses the exact same pipeline as the phone app (`112x112` area-bounded downscale ->
 * [QuantizerCelebi] -> [Score.score] -> [SchemeContent]/[SchemeMonochrome] + [Blend.harmonize])
 * so standalone watch playback matches phone-synced themes seamlessly.
 */
internal object WearColorExtractor {
    private const val MAX_EXTRACTION_DIMENSION = 112
    private const val MAX_EXTRACTION_AREA = MAX_EXTRACTION_DIMENSION * MAX_EXTRACTION_DIMENSION
    private const val QUANTIZER_MAX_COLORS = 128
    private const val SCORE_DESIRED_COUNT = 4
    private const val ACHROMATIC_CHROMA_CUTOFF = 5.0
    private const val NEUTRAL_SEED_ARGB = 0xFF808080.toInt()
    private const val BRAND_SEED_ARGB = 0xFF6C4FF5.toInt()
    private const val FAVORITE_DESIGN_COLOR_ARGB = 0xFFFF5C8A.toInt()
    private const val FALLBACK_LIGHT_CONTENT_ARGB = 0xFFF6F2FF.toInt()
    private const val FALLBACK_DARK_CONTENT_ARGB = 0xFF17141E.toInt()

    const val EXTRACTION_DECODE_DIMENSION = MAX_EXTRACTION_DIMENSION

    fun readWearSystemContrast(context: Context): Double {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return 0.0
        return runCatching {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            uiModeManager?.contrast?.toDouble()?.coerceIn(-1.0, 1.0) ?: 0.0
        }.getOrDefault(0.0)
    }

    fun extractSeedColorArgb(bitmap: Bitmap): Int? {
        if (bitmap.width <= 0 || bitmap.height <= 0 || bitmap.isRecycled) return null

        var softwareBitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null
        return try {
            val readable = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)?.also { softwareBitmap = it }
                    ?: return null
            } else {
                bitmap
            }

            val working = resizeToExtractionArea(readable).also { resized ->
                if (resized !== readable) scaledBitmap = resized
            }

            val pixels = IntArray(working.width * working.height)
            working.getPixels(pixels, 0, working.width, 0, 0, working.width, working.height)
            if (pixels.isEmpty()) return null

            val quantized = QuantizerCelebi.quantize(pixels, QUANTIZER_MAX_COLORS)
            if (quantized.isEmpty()) return null

            val ranked = Score.score(
                quantized,
                SCORE_DESIRED_COUNT,
                NEUTRAL_SEED_ARGB,
                /* filter = */ true
            )
            ranked.firstOrNull() ?: NEUTRAL_SEED_ARGB
        } catch (_: Exception) {
            null
        } finally {
            scaledBitmap?.let { if (!it.isRecycled) it.recycle() }
            softwareBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    fun buildDefaultBrandPalette(contrastLevel: Double = 0.0): WearPalette =
        buildWearPaletteFromSeed(BRAND_SEED_ARGB, contrastLevel)

    fun buildWearPaletteFromBitmap(bitmap: Bitmap, contrastLevel: Double = 0.0): WearPalette {
        val seed = extractSeedColorArgb(bitmap) ?: BRAND_SEED_ARGB
        return buildWearPaletteFromSeed(seed, contrastLevel)
    }

    fun buildWearPaletteFromSeed(seedArgb: Int, contrastLevel: Double = 0.0): WearPalette {
        val clampedContrast = contrastLevel.coerceIn(-1.0, 1.0)
        val sourceHct = Hct.fromInt(seedArgb)
        val scheme = if (sourceHct.chroma < ACHROMATIC_CHROMA_CUTOFF) {
            SchemeMonochrome(sourceHct, true, clampedContrast)
        } else {
            SchemeContent(sourceHct, true, clampedContrast)
        }

        val surfaceContainerLowest = scheme.getSurfaceContainerLowest()
        val surfaceContainerLow = scheme.getSurfaceContainerLow()
        val surfaceContainer = scheme.getSurfaceContainer()
        val surfaceContainerHigh = scheme.getSurfaceContainerHigh()
        val surfaceContainerHighest = scheme.getSurfaceContainerHighest()

        val primaryArgb = scheme.getPrimary()
        val playContainer = scheme.getOnPrimaryContainer()
        val playContent = scheme.getPrimaryContainer()
        val transportContainer = primaryArgb
        val transportContent = scheme.getOnPrimary()
        val chipContainer = scheme.getSecondaryContainer()

        val primaryHct = Hct.fromInt(primaryArgb)
        val surfaceHct = Hct.fromInt(surfaceContainer)
        val accentChroma = primaryHct.chroma.coerceAtMost(36.0)
        val gradientTop = Hct.from(primaryHct.hue, (accentChroma * 0.65).coerceAtMost(28.0), 20.0).toInt()
        val gradientMiddle = Hct.from(surfaceHct.hue, (accentChroma * 0.35).coerceAtMost(16.0), 10.0).toInt()
        val gradientBottom = Hct.from(surfaceHct.hue, (accentChroma * 0.20).coerceAtMost(10.0), 4.0).toInt()

        return WearPalette(
            gradientTop = Color(gradientTop),
            gradientMiddle = Color(gradientMiddle),
            gradientBottom = Color(gradientBottom),
            surfaceContainerLowest = Color(surfaceContainerLowest),
            surfaceContainerLow = Color(surfaceContainerLow),
            surfaceContainer = Color(surfaceContainer),
            surfaceContainerHigh = Color(surfaceContainerHigh),
            surfaceContainerHighest = Color(surfaceContainerHighest),
            textPrimary = Color(ensureReadable(scheme.getOnSurface(), gradientMiddle)),
            textSecondary = Color(ensureReadable(scheme.getOnSurfaceVariant(), gradientBottom)),
            textError = Color(ensureReadable(scheme.getError(), gradientBottom)),
            controlContainer = Color(playContainer),
            controlContent = Color(ensureReadable(playContent, playContainer)),
            controlDisabledContainer = Color(surfaceContainerHighest),
            controlDisabledContent = Color(ensureReadable(scheme.getOnSurfaceVariant(), surfaceContainerHighest)),
            transportContainer = Color(transportContainer),
            transportContent = Color(ensureReadable(transportContent, transportContainer)),
            chipContainer = Color(chipContainer),
            chipContent = Color(ensureReadable(scheme.getOnSecondaryContainer(), chipContainer)),
            favoriteActive = Color(harmonizeAccent(FAVORITE_DESIGN_COLOR_ARGB, primaryArgb, surfaceContainer)),
            shuffleActive = Color(ensureReadable(scheme.getTertiary(), surfaceContainer)),
            repeatActive = Color(ensureReadable(scheme.getSecondary(), surfaceContainer))
        )
    }

    /**
     * Derives a bright, hue-harmonized clock tint directly from the active [WearPalette]
     * in HCT space (zero bitmap pixel scanning on the UI thread).
     */
    fun deriveClockTintFromPalette(palette: WearPalette): Color {
        val hct = Hct.fromInt(palette.transportContainer.toArgb())
        val chroma = if (hct.chroma < ACHROMATIC_CHROMA_CUTOFF) 0.0 else hct.chroma.coerceIn(20.0, 44.0)
        return Color(Hct.from(hct.hue, chroma, 88.0).toInt()).copy(alpha = 0.98f)
    }

    private fun resizeToExtractionArea(bitmap: Bitmap): Bitmap {
        val area = bitmap.width.toLong() * bitmap.height.toLong()
        if (area <= MAX_EXTRACTION_AREA.toLong()) return bitmap
        val scaleFactor = sqrt(MAX_EXTRACTION_AREA.toDouble() / area.toDouble())
        val targetWidth = (bitmap.width * scaleFactor).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scaleFactor).roundToInt().coerceAtLeast(1)
        return bitmap.scale(width = targetWidth, height = targetHeight)
    }

    private fun harmonizeAccent(designColorArgb: Int, sourceColorArgb: Int, backgroundArgb: Int): Int {
        val harmonized = Blend.harmonize(designColorArgb, sourceColorArgb)
        val hct = Hct.fromInt(harmonized)
        val elevated = Hct.from(hct.hue, hct.chroma.coerceAtLeast(36.0), hct.tone.coerceIn(66.0, 82.0)).toInt()
        return ensureReadable(elevated, backgroundArgb)
    }

    private fun ensureReadable(preferredColor: Int, backgroundColor: Int): Int {
        val opaqueBackground = compositeOverOpaqueBlack(backgroundColor)
        val opaquePreferred = compositeOverOpaqueBlack(preferredColor)
        val bgTone = Hct.fromInt(opaqueBackground).tone
        if (Contrast.ratioOfTones(Hct.fromInt(opaquePreferred).tone, bgTone) >= Contrast.RATIO_30) {
            return preferredColor
        }
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
}
