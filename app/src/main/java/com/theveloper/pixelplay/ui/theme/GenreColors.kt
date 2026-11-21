package com.theveloper.pixelplay.ui.theme

import android.util.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Contrast
import com.google.android.material.color.utilities.Hct
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import kotlin.math.abs

data class GenreThemeColor(
    val container: Color,
    val onContainer: Color
)

object GenreThemeUtils {
    private val genreColorSchemeCache = LruCache<Int, ColorScheme>(128)
    private val unknownSeedColor = Color(0xFF7C7D84)

    /**
     * Mode-independent seed colors across the full hue wheel so genre cards and
     * genre detail screens share a single consistent seed across Light and Dark themes.
     */
    private val genreSeedColors = listOf(
        Color(0xFF1A73E8), // Blue
        Color(0xFFD81B60), // Rose
        Color(0xFFE91E63), // Pink
        Color(0xFF00ACC1), // Cyan
        Color(0xFF2E7D32), // Green
        Color(0xFFEF6C00), // Gold / Orange
        Color(0xFF546E7A), // Slate
        Color(0xFF7E57C2), // Purple
        Color(0xFFD32F2F), // Red
        Color(0xFF827717), // Lime
        Color(0xFF00897B), // Teal
        Color(0xFF5C6BC0), // Indigo
        Color(0xFFAD1457), // Maroon
        Color(0xFFF9A825), // Yellow
        Color(0xFF1565C0), // Navy
        Color(0xFF3949AB), // Steel Blue
        Color(0xFFC62828), // Brick Red
        Color(0xFF616161), // Neutral Grey
        Color(0xFF8E24AA), // Violet
        Color(0xFFFF8F00)  // Amber
    )

    private fun isUnknownGenreId(genreId: String): Boolean {
        return genreId
            .trim()
            .lowercase()
            .let { normalized ->
                normalized == "unknown" ||
                    normalized == "unknown genre" ||
                    normalized == "unknown_genre"
            }
    }

    fun getGenreThemeColor(
        genreId: String,
        isDark: Boolean,
        contrastLevel: Double = 0.0
    ): GenreThemeColor {
        val forceMonochrome = isUnknownGenreId(genreId)
        val seed = if (forceMonochrome) unknownSeedColor else seedColorForGenreId(genreId)
        val scheme = getColorSchemeFromSeed(
            seedColor = seed,
            isDark = isDark,
            paletteStyle = AlbumArtPaletteStyle.TONAL_SPOT,
            forceMonochrome = forceMonochrome,
            contrastLevel = contrastLevel
        )
        return GenreThemeColor(
            container = scheme.primaryContainer,
            onContainer = scheme.onPrimaryContainer
        )
    }

    fun getGenreThemeColor(
        genre: Genre?,
        isDark: Boolean,
        fallbackGenreId: String = "unknown",
        contrastLevel: Double = 0.0
    ): GenreThemeColor {
        val effectiveGenreId = genre?.id?.takeIf { it.isNotBlank() } ?: fallbackGenreId
        if (isUnknownGenreId(effectiveGenreId)) {
            return getGenreThemeColor(effectiveGenreId, isDark, contrastLevel)
        }

        val explicitContainer = parseHexColor(
            if (isDark) genre?.darkColorHex else genre?.lightColorHex
        )
        if (explicitContainer != null) {
            val explicitOnColor = parseHexColor(
                if (isDark) genre?.onDarkColorHex else genre?.onLightColorHex
            )
            return GenreThemeColor(
                container = explicitContainer,
                onContainer = explicitOnColor ?: explicitContainer.hctContrastContentColor()
            )
        }

        return getGenreThemeColor(effectiveGenreId, isDark, contrastLevel)
    }

    /**
     * Computes a WCAG AAA (>= 7.0) content color on the same HCT hue using Google's
     * Material Color Utilities [Contrast] tone solver instead of naive RGB lerping.
     */
    internal fun Color.hctContrastContentColor(minContrastRatio: Double = Contrast.RATIO_70): Color {
        val bgHct = Hct.fromInt(toArgb())
        val targetChroma = bgHct.chroma.coerceAtMost(32.0)

        val lighterTone = Contrast.lighter(bgHct.tone, minContrastRatio)
        val darkerTone = Contrast.darker(bgHct.tone, minContrastRatio)

        val targetTone = when {
            bgHct.tone < 50.0 && lighterTone >= 0.0 -> lighterTone
            bgHct.tone >= 50.0 && darkerTone >= 0.0 -> darkerTone
            lighterTone >= 0.0 -> lighterTone
            darkerTone >= 0.0 -> darkerTone
            else -> if (bgHct.tone < 50.0) 98.0 else 10.0
        }

        return Color(Hct.from(bgHct.hue, targetChroma, targetTone).toInt())
    }

    fun getGenreColorScheme(
        genre: Genre?,
        isDark: Boolean,
        genreIdFallback: String = "unknown",
        paletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.EXPRESSIVE,
        contrastLevel: Double = 0.0
    ): ColorScheme {
        val effectiveGenreId = genre?.id?.takeIf { it.isNotBlank() } ?: genreIdFallback
        val forceMonochrome = isUnknownGenreId(effectiveGenreId)
        val seed = if (forceMonochrome) {
            unknownSeedColor
        } else {
            resolveSeedColor(
                genre = genre,
                isDark = isDark,
                fallbackGenreId = genreIdFallback
            )
        }
        return getColorSchemeFromSeed(
            seedColor = seed,
            isDark = isDark,
            paletteStyle = paletteStyle,
            forceMonochrome = forceMonochrome,
            contrastLevel = contrastLevel
        )
    }

    fun getGenreColorScheme(
        genreId: String,
        isDark: Boolean,
        paletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.EXPRESSIVE,
        contrastLevel: Double = 0.0
    ): ColorScheme {
        return getGenreColorScheme(
            genre = null,
            isDark = isDark,
            genreIdFallback = genreId,
            paletteStyle = paletteStyle,
            contrastLevel = contrastLevel
        )
    }

    fun getGenreDetailColorScheme(
        genre: Genre?,
        isDark: Boolean,
        fallbackGenreId: String = "unknown",
        paletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.default,
        contrastLevel: Double = 0.0
    ): ColorScheme {
        val effectiveGenreId = genre?.id?.takeIf { it.isNotBlank() } ?: fallbackGenreId
        val forceMonochrome = isUnknownGenreId(effectiveGenreId)
        val seed = if (forceMonochrome) {
            unknownSeedColor
        } else {
            resolveSeedColor(
                genre = genre,
                isDark = isDark,
                fallbackGenreId = fallbackGenreId
            )
        }
        return getColorSchemeFromSeed(
            seedColor = seed,
            isDark = isDark,
            paletteStyle = paletteStyle,
            forceMonochrome = forceMonochrome,
            contrastLevel = contrastLevel
        )
    }

    fun getColorSchemeFromSeed(
        seedColor: Color,
        isDark: Boolean,
        paletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.EXPRESSIVE,
        forceMonochrome: Boolean = false,
        contrastLevel: Double = 0.0
    ): ColorScheme {
        val normalizedSeed = if (forceMonochrome) unknownSeedColor else seedColor
        val contrastBucket = quantizeContrastLevel(contrastLevel)
        val cacheKey = buildColorSchemeCacheKey(
            seed = normalizedSeed,
            isDark = isDark,
            paletteStyle = paletteStyle,
            forceMonochrome = forceMonochrome,
            contrastBucket = contrastBucket
        )
        genreColorSchemeCache.get(cacheKey)?.let { return it }

        val pair = if (forceMonochrome) {
            generateMonochromeColorSchemeFromSeed(normalizedSeed, contrastLevel)
        } else {
            generateColorSchemeFromSeed(
                seedColor = normalizedSeed,
                paletteStyle = paletteStyle,
                contrastLevel = contrastLevel
            )
        }
        val scheme = if (isDark) pair.dark else pair.light
        genreColorSchemeCache.put(cacheKey, scheme)
        return scheme
    }

    internal fun seedColorForGenreId(genreId: String): Color {
        val hash = abs(genreId.hashCode())
        val index = hash % genreSeedColors.size
        return genreSeedColors[index]
    }

    private fun resolveSeedColor(
        genre: Genre?,
        isDark: Boolean,
        fallbackGenreId: String
    ): Color {
        val explicitSeed = parseHexColor(
            if (isDark) genre?.darkColorHex else genre?.lightColorHex
        ) ?: parseHexColor(
            if (isDark) genre?.lightColorHex else genre?.darkColorHex
        )
        if (explicitSeed != null) return explicitSeed

        val fallbackId = genre?.id?.takeIf { it.isNotBlank() } ?: fallbackGenreId
        return seedColorForGenreId(fallbackId)
    }

    private fun buildColorSchemeCacheKey(
        seed: Color,
        isDark: Boolean,
        paletteStyle: AlbumArtPaletteStyle,
        forceMonochrome: Boolean,
        contrastBucket: Int
    ): Int {
        var result = seed.toArgb()
        result = 31 * result + if (isDark) 1 else 0
        result = 31 * result + paletteStyle.ordinal
        result = 31 * result + if (forceMonochrome) 1 else 0
        result = 31 * result + contrastBucket
        return result
    }

    private fun parseHexColor(hex: String?): Color? {
        if (hex.isNullOrBlank()) return null
        val normalized = if (hex.startsWith("#")) hex else "#$hex"
        return try {
            Color(android.graphics.Color.parseColor(normalized))
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
