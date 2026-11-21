package com.theveloper.pixelplay.data.service.wear

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Contrast
import com.google.android.material.color.utilities.Hct
import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.ui.theme.generateColorSchemeFromSeed
import org.junit.Test

class WearThemePaletteFactoryTest {

    @Test
    fun buildWearThemePalette_usesDynamicSchemeSemanticRolesAndReadableContrast() {
        val darkScheme = generateColorSchemeFromSeed(
            seedColor = Color(0xFF2E7D32),
            paletteStyle = AlbumArtPaletteStyle.CONTENT,
            contrastLevel = 0.0
        ).dark

        val palette = buildWearThemePalette(darkScheme)

        // Surface tiers map directly from the M3 dark scheme
        assertThat(palette.surfaceContainerLowestArgb).isEqualTo(darkScheme.surfaceContainerLowest.toArgb())
        assertThat(palette.surfaceContainerLowArgb).isEqualTo(darkScheme.surfaceContainerLow.toArgb())
        assertThat(palette.surfaceContainerArgb).isEqualTo(darkScheme.surfaceContainer.toArgb())
        assertThat(palette.surfaceContainerHighArgb).isEqualTo(darkScheme.surfaceContainerHigh.toArgb())
        assertThat(palette.surfaceContainerHighestArgb).isEqualTo(darkScheme.surfaceContainerHighest.toArgb())

        // Error uses the scheme's semantic error token instead of a hardcoded pink hex
        assertThat(palette.textErrorArgb).isEqualTo(darkScheme.error.toArgb())

        // OLED gradient progresses smoothly from dark tonal highlight down to near-black
        val topTone = Hct.fromInt(palette.gradientTopArgb).tone
        val midTone = Hct.fromInt(palette.gradientMiddleArgb).tone
        val bottomTone = Hct.fromInt(palette.gradientBottomArgb).tone
        assertThat(topTone).isGreaterThan(midTone)
        assertThat(midTone).isGreaterThan(bottomTone)
        assertThat(bottomTone).isLessThan(10.0)

        // Foreground/background pairs meet readable contrast ratios
        assertThat(toneContrast(palette.textPrimaryArgb, palette.gradientMiddleArgb)).isAtLeast(4.5)
        assertThat(toneContrast(palette.controlContentArgb, palette.controlContainerArgb)).isAtLeast(4.5)
        assertThat(toneContrast(palette.transportContentArgb, palette.transportContainerArgb)).isAtLeast(4.5)
        assertThat(toneContrast(palette.chipContentArgb, palette.chipContainerArgb)).isAtLeast(4.5)
        assertThat(toneContrast(palette.favoriteActiveArgb, palette.surfaceContainerArgb)).isAtLeast(3.0)
    }

    private fun toneContrast(firstArgb: Int, secondArgb: Int): Double {
        return Contrast.ratioOfTones(
            Hct.fromInt(firstArgb).tone,
            Hct.fromInt(secondArgb).tone
        )
    }
}
