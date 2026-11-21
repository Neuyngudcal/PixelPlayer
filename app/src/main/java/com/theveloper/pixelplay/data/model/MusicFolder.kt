package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class MusicFolder(
    val path: String,
    val name: String,
    val songs: ImmutableList<Song> = persistentListOf(),
    val subFolders: ImmutableList<MusicFolder> = persistentListOf()
) {
    val totalSongCount: Int
        get() = songs.size + subFolders.sumOf { it.totalSongCount }

    val totalSubFolderCount: Int
        get() = subFolders.size + subFolders.sumOf { it.totalSubFolderCount }
}
