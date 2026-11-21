package com.theveloper.pixelplay.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Contrast
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
import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.data.preferences.ThemeContrastLevel
import org.junit.Test

class ColorRolesTest {

    @Test
    fun selectSeedColorArgbFromPixels_matchesOfficialGoogleQuantizerAndScorePipeline() {
        val pixels = pixelsOf(
            520 to 0xFFD8893A.toInt(),
            260 to 0xFFE9B764.toInt(),
            220 to 0xFF69B84A.toInt()
        )

        val expected = Score.score(
            QuantizerCelebi.quantize(pixels, 128),
            4,
            NEUTRAL_SEED_ARGB,
            true
        ).first()

        assertThat(selectSeedColorArgbFromPixels(pixels)).isEqualTo(expected)
    }

    @Test
    fun selectSeedColorArgbFromPixels_returnsNeutralSeedForPureGrayscaleArtwork() {
        val pixels = pixelsOf(
            600 to 0xFF1A1A1A.toInt(),
            300 to 0xFF7F7F7F.toInt(),
            100 to 0xFFE8E8E8.toInt()
        )

        val seed = selectSeedColorArgbFromPixels(pixels)

        assertThat(seed).isEqualTo(NEUTRAL_SEED_ARGB)
        assertThat(Hct.fromInt(seed).chroma).isLessThan(ACHROMATIC_CHROMA_CUTOFF)
    }

    @Test
    fun generateColorSchemeFromSeed_achromaticSeedUsesSchemeMonochromeAndPreservesSemanticError() {
        val neutralSeed = Color(NEUTRAL_SEED_ARGB)
        val sourceHct = Hct.fromInt(neutralSeed.toArgb())
        val expectedLight = SchemeMonochrome(sourceHct, false, 0.0)
        val expectedDark = SchemeMonochrome(sourceHct, true, 0.0)

        AlbumArtPaletteStyle.entries.forEach { style ->
            val actual = generateColorSchemeFromSeed(neutralSeed, style, contrastLevel = 0.0)

            assertThat(actual.light.toArgbList())
                .containsExactlyElementsIn(expectedLight.toArgbList())
                .inOrder()
            assertThat(actual.dark.toArgbList())
                .containsExactlyElementsIn(expectedDark.toArgbList())
                .inOrder()

            // Semantic error color must stay chromatic red (not stripped to grayscale)
            assertThat(Hct.fromInt(actual.light.error.toArgb()).chroma).isGreaterThan(20.0)
            assertThat(Hct.fromInt(actual.dark.error.toArgb()).chroma).isGreaterThan(20.0)
            // Primary/neutral roles stay pure monochrome
            assertThat(Hct.fromInt(actual.light.primary.toArgb()).chroma).isLessThan(1.0)
            assertThat(Hct.fromInt(actual.dark.surface.toArgb()).chroma).isLessThan(1.0)
        }
    }

    @Test
    fun generateColorSchemeFromSeed_matchesGoogleDynamicSchemeForAllEightVariantsAndContrasts() {
        val seed = Color(0xFF3A7D44)
        val sourceHct = Hct.fromInt(seed.toArgb())
        val contrasts = listOf(0.0, 0.5, 1.0)

        AlbumArtPaletteStyle.entries.forEach { style ->
            contrasts.forEach { contrast ->
                val actual = generateColorSchemeFromSeed(seed, style, contrastLevel = contrast)

                assertThat(actual.light.toArgbList())
                    .containsExactlyElementsIn(
                        expectedScheme(style, sourceHct, isDark = false, contrastLevel = contrast).toArgbList()
                    )
                    .inOrder()
                assertThat(actual.dark.toArgbList())
                    .containsExactlyElementsIn(
                        expectedScheme(style, sourceHct, isDark = true, contrastLevel = contrast).toArgbList()
                    )
                    .inOrder()
            }
        }
    }

    @Test
    fun generateColorSchemeFromSeed_higherContrastIncreasesPrimaryContrastRatio() {
        val seed = Color(0xFF4F6AF5)

        val standard = generateColorSchemeFromSeed(seed, AlbumArtPaletteStyle.CONTENT, contrastLevel = 0.0)
        val medium = generateColorSchemeFromSeed(seed, AlbumArtPaletteStyle.CONTENT, contrastLevel = 0.5)
        val high = generateColorSchemeFromSeed(seed, AlbumArtPaletteStyle.CONTENT, contrastLevel = 1.0)

        val standardRatio = toneContrast(standard.dark.primary, standard.dark.onPrimary)
        val mediumRatio = toneContrast(medium.dark.primary, medium.dark.onPrimary)
        val highRatio = toneContrast(high.dark.primary, high.dark.onPrimary)

        assertThat(mediumRatio).isAtLeast(standardRatio)
        assertThat(highRatio).isGreaterThan(standardRatio)
    }

    @Test
    fun defaultBrandColorSchemePair_hasDistinctContainerAndBackgroundWithFull48Roles() {
        assertThat(DarkColorScheme.primaryContainer).isNotEqualTo(DarkColorScheme.background)
        assertThat(LightColorScheme.primaryContainer).isNotEqualTo(LightColorScheme.background)
        assertThat(toneContrast(DarkColorScheme.onPrimary, DarkColorScheme.primary)).isAtLeast(4.5)
        assertThat(toneContrast(LightColorScheme.onPrimary, LightColorScheme.primary)).isAtLeast(4.5)
        assertThat(AlbumArtPaletteStyle.default).isEqualTo(AlbumArtPaletteStyle.CONTENT)
        assertThat(ThemeContrastLevel.SYSTEM.resolve(0.5)).isEqualTo(0.5)
        assertThat(ThemeContrastLevel.HIGH.resolve(0.0)).isEqualTo(1.0)
    }

    private fun expectedScheme(
        style: AlbumArtPaletteStyle,
        sourceHct: Hct,
        isDark: Boolean,
        contrastLevel: Double
    ): DynamicScheme {
        return when (style) {
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

    private fun toneContrast(first: Color, second: Color): Double {
        return Contrast.ratioOfTones(
            Hct.fromInt(first.toArgb()).tone,
            Hct.fromInt(second.toArgb()).tone
        )
    }

    private fun ColorScheme.toArgbList(): List<Int> {
        return listOf(
            primary,
            onPrimary,
            primaryContainer,
            onPrimaryContainer,
            inversePrimary,
            secondary,
            onSecondary,
            secondaryContainer,
            onSecondaryContainer,
            tertiary,
            onTertiary,
            tertiaryContainer,
            onTertiaryContainer,
            background,
            onBackground,
            surface,
            onSurface,
            surfaceVariant,
            onSurfaceVariant,
            surfaceTint,
            inverseSurface,
            inverseOnSurface,
            error,
            onError,
            errorContainer,
            onErrorContainer,
            outline,
            outlineVariant,
            scrim,
            surfaceBright,
            surfaceDim,
            surfaceContainer,
            surfaceContainerHigh,
            surfaceContainerHighest,
            surfaceContainerLow,
            surfaceContainerLowest,
            primaryFixed,
            primaryFixedDim,
            onPrimaryFixed,
            onPrimaryFixedVariant,
            secondaryFixed,
            secondaryFixedDim,
            onSecondaryFixed,
            onSecondaryFixedVariant,
            tertiaryFixed,
            tertiaryFixedDim,
            onTertiaryFixed,
            onTertiaryFixedVariant
        ).map(Color::toArgb)
    }

    private fun DynamicScheme.toArgbList(): List<Int> {
        return listOf(
            getPrimary(),
            getOnPrimary(),
            getPrimaryContainer(),
            getOnPrimaryContainer(),
            getInversePrimary(),
            getSecondary(),
            getOnSecondary(),
            getSecondaryContainer(),
            getOnSecondaryContainer(),
            getTertiary(),
            getOnTertiary(),
            getTertiaryContainer(),
            getOnTertiaryContainer(),
            getBackground(),
            getOnBackground(),
            getSurface(),
            getOnSurface(),
            getSurfaceVariant(),
            getOnSurfaceVariant(),
            getSurfaceTint(),
            getInverseSurface(),
            getInverseOnSurface(),
            getError(),
            getOnError(),
            getErrorContainer(),
            getOnErrorContainer(),
            getOutline(),
            getOutlineVariant(),
            getScrim(),
            getSurfaceBright(),
            getSurfaceDim(),
            getSurfaceContainer(),
            getSurfaceContainerHigh(),
            getSurfaceContainerHighest(),
            getSurfaceContainerLow(),
            getSurfaceContainerLowest(),
            getPrimaryFixed(),
            getPrimaryFixedDim(),
            getOnPrimaryFixed(),
            getOnPrimaryFixedVariant(),
            getSecondaryFixed(),
            getSecondaryFixedDim(),
            getOnSecondaryFixed(),
            getOnSecondaryFixedVariant(),
            getTertiaryFixed(),
            getTertiaryFixedDim(),
            getOnTertiaryFixed(),
            getOnTertiaryFixedVariant()
        )
    }

    private fun pixelsOf(vararg entries: Pair<Int, Int>): IntArray {
        return buildList {
            entries.forEach { (count, color) ->
                repeat(count) { add(color) }
            }
        }.toIntArray()
    }
}
