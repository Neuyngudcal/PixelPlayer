package com.theveloper.pixelplay.presentation.screens.settings

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import org.junit.jupiter.api.Test

class SettingsSearchIndexTest {

    private val mockTitles: Map<Int, String> = mapOf(
        R.string.settings_category_music_management_title to "Music Management",
        R.string.settings_category_appearance_title to "Appearance",
        R.string.settings_category_playback_title to "Playback",
        R.string.settings_category_behavior_title to "Behavior",
        R.string.settings_category_equalizer_title to "Equalizer",
        R.string.settings_category_ai_title to "AI Integration",
        R.string.settings_category_backup_title to "Backup & Restore",
        R.string.settings_category_device_capabilities_title to "Device Capabilities",
        R.string.settings_category_developer_title to "Developer",
        R.string.settings_category_about_title to "About",
        R.string.settings_palette_style_title to "Album Art Palette Style",
        R.string.settings_excluded_directories_title to "Excluded Directories",
        R.string.settings_artists_title to "Artists",
        R.string.settings_refresh_library_title to "Refresh Library",
        R.string.settings_min_song_duration to "Minimum Song Duration",
        R.string.navbar_adjust_title to "Navigation Bar Corner Radius",
        R.string.settings_pause_on_volume_zero to "Pause on volume zero",
        R.string.settings_exp_screen_title to "Experimental",
        R.string.about_open_source_licenses to "Open source licenses"
    )

    private fun runSearch(query: String): List<SettingsSearchEntry> =
        SettingsSearchIndex.search(
            query = query,
            titleProvider = { resId -> mockTitles[resId] ?: "" },
            subtitleProvider = { "" }
        )

    @Test
    fun `normalizes Vietnamese diacritics and letter d-stroke consistently`() {
        assertThat(SettingsSearchIndex.normalizeForSearch("Giao diện & Bảng màu"))
            .isEqualTo("giao dien & bang mau")
        assertThat(SettingsSearchIndex.normalizeForSearch("Âm Thanh ĐẶC BIỆT"))
            .isEqualTo("am thanh dac biet")
    }

    @Test
    fun `finds palette style and equalizer from English and Vietnamese queries`() {
        val paletteResults = runSearch("palette")
        assertThat(paletteResults.map { it.destination }).contains(SettingsDetailDestination.PaletteStyle)

        val vietnamesePaletteResults = runSearch("bảng màu")
        assertThat(vietnamesePaletteResults.map { it.destination }).contains(SettingsDetailDestination.PaletteStyle)

        val eqResults = runSearch("equalizer")
        assertThat(eqResults.first().destination).isEqualTo(SettingsDetailDestination.Equalizer)

        val backupViResults = runSearch("sao luu")
        assertThat(backupViResults.map { it.category }).contains(SettingsCategory.BACKUP_RESTORE)
    }

    @Test
    fun `returns empty list for blank or unmatched query`() {
        assertThat(runSearch("   ")).isEmpty()
        assertThat(runSearch("zzzz_nonexistent_setting_xyz")).isEmpty()
    }
}
