package com.theveloper.pixelplay.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Compact seed-color cache entry per artwork URI.
 *
 * Storing only the extracted Google Material Color Utilities seed ARGB (rather than 96 hex role
 * strings) cuts SQLite storage ~30x and makes switching palette style or contrast level instant
 * (< 0.15ms in memory) with zero bitmap re-decoding or quantization.
 */
@Entity(tableName = "album_art_themes")
data class AlbumArtThemeEntity(
    @PrimaryKey val albumArtUriString: String,
    val seedColorArgb: Int,
    val algorithmVersion: Int
)
