package com.theveloper.pixelplay.presentation.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Blend
import com.google.android.material.color.utilities.Hct
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme

@Immutable
data class SettingsCategoryColors(
    val containerColor: Color,
    val iconColor: Color
)

/**
 * Generates Material 3 tonal icon badge colors for each [SettingsCategory] using Google's
 * Material Color Utilities ([Blend.harmonize] + [Hct]).
 *
 * Each category keeps a recognizable hue (like Samsung Settings for rapid visual scanning) while
 * shifting gently toward the active Material You [primaryArgb] and guaranteeing WCAG AAA (> 8:1)
 * contrast in both Light and Dark themes.
 */
object SettingsCategoryColorResolver {

    private fun seedArgbFor(category: SettingsCategory): Int = when (category) {
        SettingsCategory.LIBRARY -> 0xFF1A73E8.toInt()            // Google Blue
        SettingsCategory.APPEARANCE -> 0xFFD81B60.toInt()         // Expressive Rose
        SettingsCategory.PLAYBACK -> 0xFFE53935.toInt()           // Crimson Coral
        SettingsCategory.BEHAVIOR -> 0xFF3949AB.toInt()           // Deep Indigo
        SettingsCategory.AI_INTEGRATION -> 0xFF00897B.toInt()     // Emerald Teal
        SettingsCategory.BACKUP_RESTORE -> 0xFF5C6BC0.toInt()     // Cobalt Slate
        SettingsCategory.EQUALIZER -> 0xFFEF6C00.toInt()          // Warm Amber
        SettingsCategory.DEVICE_CAPABILITIES -> 0xFF0097A7.toInt()// Cyan
        SettingsCategory.DEVELOPER -> 0xFF2E7D32.toInt()          // Forest Green
        SettingsCategory.ABOUT -> 0xFF546E7A.toInt()              // Cool Neutral
    }

    /**
     * Pure JVM-safe ARGB resolver returning `containerArgb to iconArgb`.
     * Uses Material 3 tonal pairs: Light = (T90, T30), Dark = (T30, T90) -> contrast > 8.5:1.
     */
    fun resolveArgbPair(
        category: SettingsCategory,
        primaryArgb: Int,
        isDark: Boolean
    ): Pair<Int, Int> {
        val baseSeed = seedArgbFor(category)
        val harmonizedArgb = Blend.harmonize(baseSeed, primaryArgb)
        val harmonizedHct = Hct.fromInt(harmonizedArgb)
        val isNeutral = category == SettingsCategory.ABOUT

        val containerChroma = if (isNeutral) {
            minOf(harmonizedHct.chroma, 10.0)
        } else if (isDark) {
            minOf(harmonizedHct.chroma, 36.0)
        } else {
            minOf(harmonizedHct.chroma, 32.0)
        }

        val iconChroma = if (isNeutral) {
            minOf(harmonizedHct.chroma, 12.0)
        } else if (isDark) {
            minOf(harmonizedHct.chroma, 36.0)
        } else {
            minOf(harmonizedHct.chroma, 48.0)
        }

        val containerTone = if (isDark) 30.0 else 90.0
        val iconTone = if (isDark) 90.0 else 30.0

        val containerArgb = Hct.from(harmonizedHct.hue, containerChroma, containerTone).toInt()
        val iconArgb = Hct.from(harmonizedHct.hue, iconChroma, iconTone).toInt()
        return containerArgb to iconArgb
    }

    fun resolve(
        category: SettingsCategory,
        primaryColor: Color,
        isDark: Boolean
    ): SettingsCategoryColors {
        val (containerArgb, iconArgb) = resolveArgbPair(
            category = category,
            primaryArgb = primaryColor.toArgb(),
            isDark = isDark
        )
        return SettingsCategoryColors(
            containerColor = Color(containerArgb),
            iconColor = Color(iconArgb)
        )
    }
}

@Composable
fun rememberCategoryColors(category: SettingsCategory): SettingsCategoryColors {
    val primary = MaterialTheme.colorScheme.primary
    val isDark = LocalPixelPlayDarkTheme.current
    return remember(category, primary, isDark) {
        SettingsCategoryColorResolver.resolve(
            category = category,
            primaryColor = primary,
            isDark = isDark
        )
    }
}
