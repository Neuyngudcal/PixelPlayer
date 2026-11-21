package com.theveloper.pixelplay.presentation.screens.settings

import android.os.Parcelable
import androidx.annotation.StringRes
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import kotlinx.parcelize.Parcelize
import java.text.Normalizer
import java.util.Locale

/**
 * Typed detail-pane destinations hosted inside [SettingsAdaptiveScreen]'s [ListDetailPaneScaffold].
 */
@Parcelize
sealed interface SettingsDetailDestination : Parcelable {
    val parentCategory: SettingsCategory
    val isSubDestination: Boolean

    @Parcelize
    data class Category(val categoryId: String) : SettingsDetailDestination {
        override val parentCategory: SettingsCategory
            get() = SettingsCategory.fromId(categoryId) ?: SettingsCategory.LIBRARY
        override val isSubDestination: Boolean get() = false
    }

    @Parcelize
    data object Equalizer : SettingsDetailDestination {
        override val parentCategory: SettingsCategory get() = SettingsCategory.EQUALIZER
        override val isSubDestination: Boolean get() = false
    }

    @Parcelize
    data object DeviceCapabilities : SettingsDetailDestination {
        override val parentCategory: SettingsCategory get() = SettingsCategory.DEVICE_CAPABILITIES
        override val isSubDestination: Boolean get() = false
    }

    @Parcelize
    data object About : SettingsDetailDestination {
        override val parentCategory: SettingsCategory get() = SettingsCategory.ABOUT
        override val isSubDestination: Boolean get() = false
    }

    @Parcelize
    data object PaletteStyle : SettingsDetailDestination {
        override val parentCategory: SettingsCategory get() = SettingsCategory.APPEARANCE
        override val isSubDestination: Boolean get() = true
    }

    @Parcelize
    data object NavBarCornerRadius : SettingsDetailDestination {
        override val parentCategory: SettingsCategory get() = SettingsCategory.APPEARANCE
        override val isSubDestination: Boolean get() = true
    }

    @Parcelize
    data object ArtistSettings : SettingsDetailDestination {
        override val parentCategory: SettingsCategory get() = SettingsCategory.LIBRARY
        override val isSubDestination: Boolean get() = true
    }

    @Parcelize
    data object DelimiterConfig : SettingsDetailDestination {
        override val parentCategory: SettingsCategory get() = SettingsCategory.LIBRARY
        override val isSubDestination: Boolean get() = true
    }

    @Parcelize
    data object WordDelimiterConfig : SettingsDetailDestination {
        override val parentCategory: SettingsCategory get() = SettingsCategory.LIBRARY
        override val isSubDestination: Boolean get() = true
    }

    @Parcelize
    data object OfflineModelHub : SettingsDetailDestination {
        override val parentCategory: SettingsCategory get() = SettingsCategory.AI_INTEGRATION
        override val isSubDestination: Boolean get() = true
    }

    @Parcelize
    data object Experimental : SettingsDetailDestination {
        override val parentCategory: SettingsCategory get() = SettingsCategory.DEVELOPER
        override val isSubDestination: Boolean get() = true
    }

    @Parcelize
    data object OpenSourceLicenses : SettingsDetailDestination {
        override val parentCategory: SettingsCategory get() = SettingsCategory.ABOUT
        override val isSubDestination: Boolean get() = true
    }

    companion object {
        fun forCategory(category: SettingsCategory): SettingsDetailDestination = when (category) {
            SettingsCategory.EQUALIZER -> Equalizer
            SettingsCategory.DEVICE_CAPABILITIES -> DeviceCapabilities
            SettingsCategory.ABOUT -> About
            else -> Category(category.id)
        }
    }
}

data class SettingsSearchEntry(
    val id: String,
    val category: SettingsCategory,
    val destination: SettingsDetailDestination,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val keywords: List<String> = emptyList()
)

object SettingsSearchIndex {

    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")

    val entries: List<SettingsSearchEntry> = listOf(
        // Library
        SettingsSearchEntry(
            id = "cat_library",
            category = SettingsCategory.LIBRARY,
            destination = SettingsDetailDestination.Category(SettingsCategory.LIBRARY.id),
            titleRes = R.string.settings_category_music_management_title,
            subtitleRes = R.string.settings_category_music_management_subtitle,
            keywords = listOf("library", "music", "folder", "scan", "sync", "thu vien", "nhac", "quet")
        ),
        SettingsSearchEntry(
            id = "excluded_dirs",
            category = SettingsCategory.LIBRARY,
            destination = SettingsDetailDestination.Category(SettingsCategory.LIBRARY.id),
            titleRes = R.string.settings_excluded_directories_title,
            subtitleRes = R.string.settings_excluded_directories_subtitle,
            keywords = listOf("folder", "directory", "exclude", "hide", "blacklist", "thu muc", "loai tru")
        ),
        SettingsSearchEntry(
            id = "artists",
            category = SettingsCategory.LIBRARY,
            destination = SettingsDetailDestination.ArtistSettings,
            titleRes = R.string.settings_artists_title,
            subtitleRes = R.string.settings_artists_subtitle,
            keywords = listOf("artist", "delimiter", "split", "multi artist", "nghe si", "tach")
        ),
        SettingsSearchEntry(
            id = "refresh_library",
            category = SettingsCategory.LIBRARY,
            destination = SettingsDetailDestination.Category(SettingsCategory.LIBRARY.id),
            titleRes = R.string.settings_refresh_library_title,
            subtitleRes = R.string.settings_refresh_library_subtitle,
            keywords = listOf("rescan", "rebuild", "database", "refresh", "lam moi", "quet lai")
        ),
        SettingsSearchEntry(
            id = "min_duration",
            category = SettingsCategory.LIBRARY,
            destination = SettingsDetailDestination.Category(SettingsCategory.LIBRARY.id),
            titleRes = R.string.settings_min_song_duration,
            subtitleRes = R.string.settings_filtering_section,
            keywords = listOf("duration", "filter", "short", "ringtone", "thoi luong", "loc")
        ),

        // Appearance
        SettingsSearchEntry(
            id = "cat_appearance",
            category = SettingsCategory.APPEARANCE,
            destination = SettingsDetailDestination.Category(SettingsCategory.APPEARANCE.id),
            titleRes = R.string.settings_category_appearance_title,
            subtitleRes = R.string.settings_category_appearance_subtitle,
            keywords = listOf("appearance", "theme", "dark", "light", "color", "giao dien", "mau sac", "toi", "sang")
        ),
        SettingsSearchEntry(
            id = "palette_style",
            category = SettingsCategory.APPEARANCE,
            destination = SettingsDetailDestination.PaletteStyle,
            titleRes = R.string.settings_palette_style_title,
            subtitleRes = R.string.settings_palette_style_subtitle,
            keywords = listOf(
                "palette", "color", "dynamic", "material you", "contrast", "expressive",
                "vibrant", "tonal", "monochrome", "bang mau", "tuong phan", "mau dong"
            )
        ),
        SettingsSearchEntry(
            id = "nav_bar_radius",
            category = SettingsCategory.APPEARANCE,
            destination = SettingsDetailDestination.NavBarCornerRadius,
            titleRes = R.string.navbar_adjust_title,
            subtitleRes = R.string.settings_category_appearance_subtitle,
            keywords = listOf("navbar", "corner", "radius", "smooth", "bo goc", "thanh dieu huong")
        ),

        // Playback
        SettingsSearchEntry(
            id = "cat_playback",
            category = SettingsCategory.PLAYBACK,
            destination = SettingsDetailDestination.Category(SettingsCategory.PLAYBACK.id),
            titleRes = R.string.settings_category_playback_title,
            subtitleRes = R.string.settings_category_playback_subtitle,
            keywords = listOf(
                "playback", "crossfade", "gapless", "replaygain", "volume", "fade", "queue", "lyrics",
                "phat nhac", "am luong", "chuyen bai", "loi bai hat"
            )
        ),
        SettingsSearchEntry(
            id = "volume_pause_zero",
            category = SettingsCategory.PLAYBACK,
            destination = SettingsDetailDestination.Category(SettingsCategory.PLAYBACK.id),
            titleRes = R.string.settings_pause_on_volume_zero,
            subtitleRes = R.string.settings_pause_on_volume_zero_desc,
            keywords = listOf("volume", "zero", "mute", "pause", "am luong", "tam dung")
        ),
        SettingsSearchEntry(
            id = "playback_hifi_mode",
            category = SettingsCategory.PLAYBACK,
            destination = SettingsDetailDestination.Category(SettingsCategory.PLAYBACK.id),
            titleRes = R.string.settings_hifi_mode_title,
            subtitleRes = R.string.settings_hifi_mode_supported_subtitle,
            keywords = listOf("hifi", "hi-fi", "hires", "hi-res", "float", "pcm", "16bit", "32bit", "1000", "lossless", "flac", "audio")
        ),

        // Behavior
        SettingsSearchEntry(
            id = "cat_behavior",
            category = SettingsCategory.BEHAVIOR,
            destination = SettingsDetailDestination.Category(SettingsCategory.BEHAVIOR.id),
            titleRes = R.string.settings_category_behavior_title,
            subtitleRes = R.string.settings_category_behavior_subtitle,
            keywords = listOf(
                "behavior", "haptic", "vibration", "language", "locale", "gesture", "battery", "scrollbar",
                "hanh vi", "rung", "ngon ngu", "cu chi"
            )
        ),

        // Equalizer
        SettingsSearchEntry(
            id = "cat_equalizer",
            category = SettingsCategory.EQUALIZER,
            destination = SettingsDetailDestination.Equalizer,
            titleRes = R.string.settings_category_equalizer_title,
            subtitleRes = R.string.settings_category_equalizer_subtitle,
            keywords = listOf("equalizer", "eq", "bass", "treble", "virtualizer", "loudness", "preset", "am thanh", "bo chinh am")
        ),

        // AI Integration
        SettingsSearchEntry(
            id = "cat_ai",
            category = SettingsCategory.AI_INTEGRATION,
            destination = SettingsDetailDestination.Category(SettingsCategory.AI_INTEGRATION.id),
            titleRes = R.string.settings_category_ai_title,
            subtitleRes = R.string.settings_category_ai_subtitle,
            keywords = listOf("ai", "gemini", "api key", "prompt", "playlist", "offline", "llm", "tri tue nhan tao")
        ),
        SettingsSearchEntry(
            id = "offline_model_hub",
            category = SettingsCategory.AI_INTEGRATION,
            destination = SettingsDetailDestination.OfflineModelHub,
            titleRes = R.string.settings_category_ai_title,
            subtitleRes = R.string.settings_category_ai_subtitle,
            keywords = listOf("offline", "gguf", "local model", "aicore", "mo hinh offline")
        ),

        // Backup & Restore
        SettingsSearchEntry(
            id = "cat_backup",
            category = SettingsCategory.BACKUP_RESTORE,
            destination = SettingsDetailDestination.Category(SettingsCategory.BACKUP_RESTORE.id),
            titleRes = R.string.settings_category_backup_title,
            subtitleRes = R.string.settings_category_backup_subtitle,
            keywords = listOf("backup", "restore", "export", "import", "sao luu", "khoi phuc", "xuat", "nhap")
        ),

        // Device Capabilities
        SettingsSearchEntry(
            id = "cat_devcaps",
            category = SettingsCategory.DEVICE_CAPABILITIES,
            destination = SettingsDetailDestination.DeviceCapabilities,
            titleRes = R.string.settings_category_device_capabilities_title,
            subtitleRes = R.string.settings_category_device_capabilities_subtitle,
            keywords = listOf("device", "capabilities", "audio", "decoder", "flac", "hifi", "sample rate", "storage", "thiet bi")
        ),

        // Developer & Experimental
        SettingsSearchEntry(
            id = "cat_developer",
            category = SettingsCategory.DEVELOPER,
            destination = SettingsDetailDestination.Category(SettingsCategory.DEVELOPER.id),
            titleRes = R.string.settings_category_developer_title,
            subtitleRes = R.string.settings_category_developer_subtitle,
            keywords = listOf("developer", "debug", "diagnostics", "crash", "cache", "palette regenerate", "nha phat trien")
        ),
        SettingsSearchEntry(
            id = "experimental",
            category = SettingsCategory.DEVELOPER,
            destination = SettingsDetailDestination.Experimental,
            titleRes = R.string.settings_exp_screen_title,
            subtitleRes = R.string.settings_exp_player_ui_tweaks_section,
            keywords = listOf("experimental", "blur", "lyrics", "animation", "album art resolution", "thu nghiem")
        ),

        // About & Licenses
        SettingsSearchEntry(
            id = "cat_about",
            category = SettingsCategory.ABOUT,
            destination = SettingsDetailDestination.About,
            titleRes = R.string.settings_category_about_title,
            subtitleRes = R.string.settings_category_about_subtitle,
            keywords = listOf("about", "version", "github", "telegram", "contributors", "gioi thieu", "phien ban")
        ),
        SettingsSearchEntry(
            id = "open_source_licenses",
            category = SettingsCategory.ABOUT,
            destination = SettingsDetailDestination.OpenSourceLicenses,
            titleRes = R.string.about_open_source_licenses,
            subtitleRes = R.string.about_open_source_licenses_subtitle,
            keywords = listOf("license", "open source", "notice", "mit", "giay phep", "ma nguon mo")
        )
    )

    fun normalizeForSearch(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
        return diacriticsRegex.replace(decomposed, "")
            .replace('đ', 'd')
            .replace('Đ', 'd')
            .lowercase(Locale.ROOT)
    }

    fun search(
        query: String,
        sourceEntries: List<SettingsSearchEntry> = entries,
        titleProvider: (Int) -> String,
        subtitleProvider: (Int) -> String
    ): List<SettingsSearchEntry> {
        val normalizedQuery = normalizeForSearch(query)
        if (normalizedQuery.isEmpty()) return emptyList()
        val queryTokens = normalizedQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (queryTokens.isEmpty()) return emptyList()

        return sourceEntries.mapNotNull { entry ->
            val titleNorm = normalizeForSearch(titleProvider(entry.titleRes))
            val subtitleNorm = normalizeForSearch(subtitleProvider(entry.subtitleRes))
            val keywordsNorm = entry.keywords.map { normalizeForSearch(it) }
            val searchableBlob = buildString {
                append(titleNorm)
                append(' ')
                append(subtitleNorm)
                for (kw in keywordsNorm) {
                    append(' ')
                    append(kw)
                }
            }
            if (queryTokens.all { token -> searchableBlob.contains(token) }) {
                val score = when {
                    titleNorm.startsWith(normalizedQuery) -> 0
                    titleNorm.contains(normalizedQuery) -> 1
                    keywordsNorm.any { it.startsWith(normalizedQuery) } -> 2
                    else -> 3
                }
                entry to score
            } else {
                null
            }
        }
            .sortedBy { it.second }
            .map { it.first }
    }
}
