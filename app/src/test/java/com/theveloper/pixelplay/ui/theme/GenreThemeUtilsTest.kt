package com.theveloper.pixelplay.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Contrast
import com.google.android.material.color.utilities.Hct
import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import org.junit.Test

class GenreThemeUtilsTest {

    @Test
    fun getGenreDetailColorScheme_usesConsistentModeIndependentSeedInLightAndDark() {
        val genre = Genre(id = "rock", name = "Rock")
        val paletteStyle = AlbumArtPaletteStyle.VIBRANT
        val expectedSeed = GenreThemeUtils.seedColorForGenreId(genre.id)

        val actualLight = GenreThemeUtils.getGenreDetailColorScheme(
            genre = genre,
            isDark = false,
            fallbackGenreId = genre.id,
            paletteStyle = paletteStyle,
            contrastLevel = 0.5
        )
        val actualDark = GenreThemeUtils.getGenreDetailColorScheme(
            genre = genre,
            isDark = true,
            fallbackGenreId = genre.id,
            paletteStyle = paletteStyle,
            contrastLevel = 0.5
        )
        val expectedPair = generateColorSchemeFromSeed(
            seedColor = expectedSeed,
            paletteStyle = paletteStyle,
            contrastLevel = 0.5
        )

        assertThat(actualLight.toArgbList()).containsExactlyElementsIn(expectedPair.light.toArgbList()).inOrder()
        assertThat(actualDark.toArgbList()).containsExactlyElementsIn(expectedPair.dark.toArgbList()).inOrder()
    }

    @Test
    fun getGenreThemeColor_explicitCustomContainerAchievesHighHctContrast() {
        val customGenre = Genre(
            id = "custom_synth",
            name = "Synthwave",
            lightColorHex = "#E91E63",
            darkColorHex = "#2A1548"
        )

        listOf(false, true).forEach { isDark ->
            val cardColor = GenreThemeUtils.getGenreThemeColor(
                genre = customGenre,
                isDark = isDark,
                fallbackGenreId = customGenre.id
            )
            val ratio = Contrast.ratioOfTones(
                Hct.fromInt(cardColor.container.toArgb()).tone,
                Hct.fromInt(cardColor.onContainer.toArgb()).tone
            )
            assertThat(ratio).isAtLeast(4.5)
        }
    }

    @Test
    fun getGenreDetailColorScheme_forUnknownGenre_usesMonochromeScheme() {
        val actual = GenreThemeUtils.getGenreDetailColorScheme(
            genre = null,
            isDark = false,
            fallbackGenreId = "unknown",
            paletteStyle = AlbumArtPaletteStyle.FRUIT_SALAD,
            contrastLevel = 0.0
        )
        val expected = generateMonochromeColorSchemeFromSeed(Color(0xFF7C7D84), contrastLevel = 0.0).light

        assertThat(actual.toArgbList()).containsExactlyElementsIn(expected.toArgbList()).inOrder()
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
}
