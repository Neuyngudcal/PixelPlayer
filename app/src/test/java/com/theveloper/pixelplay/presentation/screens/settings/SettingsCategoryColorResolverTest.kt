package com.theveloper.pixelplay.presentation.screens.settings

import com.google.android.material.color.utilities.Contrast
import com.google.android.material.color.utilities.Hct
import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import org.junit.jupiter.api.Test

class SettingsCategoryColorResolverTest {

    private val testDynamicPrimarySeeds = listOf(
        0xFF6750A4.toInt(), // Material baseline purple
        0xFF1A73E8.toInt(), // Google Blue
        0xFF006D3B.toInt(), // Emerald Green
        0xFFB3261E.toInt(), // Deep Red
        0xFF7D5700.toInt(), // Warm Amber
    )

    @Test
    fun `all 10 settings categories meet WCAG AAA contrast across light and dark modes for every dynamic seed`() {
        for (primaryArgb in testDynamicPrimarySeeds) {
            for (isDark in listOf(false, true)) {
                for (category in SettingsCategory.entries) {
                    val (containerArgb, iconArgb) = SettingsCategoryColorResolver.resolveArgbPair(
                        category = category,
                        primaryArgb = primaryArgb,
                        isDark = isDark
                    )
                    val containerHct = Hct.fromInt(containerArgb)
                    val iconHct = Hct.fromInt(iconArgb)
                    val ratio = Contrast.ratioOfTones(iconHct.tone, containerHct.tone)

                    assertThat(ratio).isAtLeast(7.0)
                    if (isDark) {
                        assertThat(containerHct.tone).isLessThan(40.0)
                        assertThat(iconHct.tone).isGreaterThan(80.0)
                    } else {
                        assertThat(containerHct.tone).isGreaterThan(80.0)
                        assertThat(iconHct.tone).isLessThan(40.0)
                    }
                }
            }
        }
    }

    @Test
    fun `harmonization shifts category hue gently toward dynamic primary while preserving category distinctness`() {
        val (libBlue, _) = SettingsCategoryColorResolver.resolveArgbPair(
            category = SettingsCategory.LIBRARY,
            primaryArgb = 0xFF1A73E8.toInt(),
            isDark = false
        )
        val (libRed, _) = SettingsCategoryColorResolver.resolveArgbPair(
            category = SettingsCategory.LIBRARY,
            primaryArgb = 0xFFD32F2F.toInt(),
            isDark = false
        )
        assertThat(libBlue).isNotEqualTo(libRed)

        val (appearanceContainer, _) = SettingsCategoryColorResolver.resolveArgbPair(
            category = SettingsCategory.APPEARANCE,
            primaryArgb = 0xFF6750A4.toInt(),
            isDark = false
        )
        val (equalizerContainer, _) = SettingsCategoryColorResolver.resolveArgbPair(
            category = SettingsCategory.EQUALIZER,
            primaryArgb = 0xFF6750A4.toInt(),
            isDark = false
        )
        assertThat(appearanceContainer).isNotEqualTo(equalizerContainer)
    }
}
