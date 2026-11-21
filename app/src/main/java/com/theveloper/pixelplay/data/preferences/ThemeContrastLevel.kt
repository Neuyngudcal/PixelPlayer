package com.theveloper.pixelplay.data.preferences

/**
 * Material 3 dynamic color contrast level matching Google's Material Color Utilities
 * and Android 14+ (API 34) system color contrast settings.
 */
enum class ThemeContrastLevel(
    val storageKey: String,
    val fixedContrast: Double?
) {
    SYSTEM("system", null),
    STANDARD("standard", 0.0),
    MEDIUM("medium", 0.5),
    HIGH("high", 1.0);

    fun resolve(systemContrast: Double): Double =
        (fixedContrast ?: systemContrast).coerceIn(-1.0, 1.0)

    companion object {
        val default: ThemeContrastLevel = SYSTEM

        fun fromStorageKey(value: String?): ThemeContrastLevel {
            return entries.firstOrNull { it.storageKey == value } ?: default
        }
    }
}
