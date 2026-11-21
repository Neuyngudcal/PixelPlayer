package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AlbumArtThemeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeed(theme: AlbumArtThemeEntity)

    @Query(
        "SELECT seedColorArgb FROM album_art_themes WHERE albumArtUriString = :uriString AND algorithmVersion = :algorithmVersion"
    )
    suspend fun getSeedByUri(uriString: String, algorithmVersion: Int): Int?

    @Query(
        "SELECT * FROM album_art_themes WHERE albumArtUriString IN (:uriStrings) AND algorithmVersion = :algorithmVersion"
    )
    suspend fun getSeedsByUris(uriStrings: List<String>, algorithmVersion: Int): List<AlbumArtThemeEntity>

    @Query("DELETE FROM album_art_themes WHERE albumArtUriString IN (:uriStrings)")
    suspend fun deleteThemesByUris(uriStrings: List<String>)

    @Query("DELETE FROM album_art_themes")
    suspend fun clearAllThemes()
}
