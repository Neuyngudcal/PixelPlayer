package com.theveloper.pixelplay.data.preferences

enum class AlbumArtPaletteStyle(
    val storageKey: String,
    val label: String
) {
    CONTENT("content", "Content"),
    FIDELITY("fidelity", "Fidelity"),
    TONAL_SPOT("tonal_spot", "Tonal Spot"),
    VIBRANT("vibrant", "Vibrant"),
    EXPRESSIVE("expressive", "Expressive"),
    FRUIT_SALAD("fruit_salad", "Fruit Salad"),
    MONOCHROME("monochrome", "Monochrome"),
    NEUTRAL("neutral", "Neutral");

    companion object {
        val default: AlbumArtPaletteStyle = CONTENT

        fun fromStorageKey(value: String?): AlbumArtPaletteStyle {
            return entries.firstOrNull { it.storageKey == value } ?: default
        }
    }
}
