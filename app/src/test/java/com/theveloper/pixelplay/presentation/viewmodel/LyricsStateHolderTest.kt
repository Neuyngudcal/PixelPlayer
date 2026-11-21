package com.theveloper.pixelplay.presentation.viewmodel

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.lyrics.LrcLibResponse
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.data.repository.MusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsStateHolderTest {

    @Test
    fun withPersistedLyrics_replacesAlbumArtUriWhenMetadataWriteRefreshesArtworkPath() {
        val originalSong = testSong(albumArtUriString = "file:///cache/song_art_1_old.jpg")

        val updatedSong = originalSong.withPersistedLyrics(
            rawLyrics = "New lyrics",
            refreshedAlbumArtUri = "file:///cache/song_art_1_new.jpg"
        )

        assertThat(updatedSong.lyrics).isEqualTo("New lyrics")
        assertThat(updatedSong.albumArtUriString).isEqualTo("file:///cache/song_art_1_new.jpg")
    }

    @Test
    fun withPersistedLyrics_keepsExistingAlbumArtUriWhenMetadataWriteDoesNotReturnOne() {
        val originalSong = testSong(albumArtUriString = "content://art/song_art_1.jpg")

        val updatedSong = originalSong.withPersistedLyrics(
            rawLyrics = "Imported lyrics",
            refreshedAlbumArtUri = null
        )

        assertThat(updatedSong.lyrics).isEqualTo("Imported lyrics")
        assertThat(updatedSong.albumArtUriString).isEqualTo("content://art/song_art_1.jpg")
    }

    @Test
    fun fetchLyricsForSong_usesStoredLyricsWithoutRemoteFetch() {
        val musicRepository = mockk<MusicRepository>(relaxed = true)
        val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        val songMetadataEditor = mockk<SongMetadataEditor>(relaxed = true)
        val holder = LyricsStateHolder(
            musicRepository = musicRepository,
            userPreferencesRepository = userPreferencesRepository,
            songMetadataEditor = songMetadataEditor
        )
        val scope = TestScope(StandardTestDispatcher())
        val callback = RecordingLyricsLoadCallback()
        val state = MutableStateFlow(StablePlayerState())
        val song = testSong(albumArtUriString = "content://art/song_art_1.jpg").copy(
            lyrics = "Stored lyrics"
        )
        val storedLyrics = Lyrics(plain = listOf("Stored lyrics"), areFromRemote = false)

        holder.initialize(scope, callback, state)
        coEvery { musicRepository.getStoredLyrics(song) } returns (storedLyrics to "Stored lyrics")

        holder.fetchLyricsForSong(
            song = song,
            forcePickResults = false,
            sourcePreference = com.theveloper.pixelplay.data.model.LyricsSourcePreference.API_FIRST
        ) { "Lyrics already available" }
        scope.advanceUntilIdle()

        assertThat(holder.searchUiState.value).isEqualTo(LyricsSearchUiState.Success(storedLyrics))
        coVerify(exactly = 1) { musicRepository.getStoredLyrics(song) }
        coVerify(exactly = 0) { musicRepository.getLyricsFromRemote(any()) }
        coVerify(exactly = 0) { musicRepository.searchRemoteLyrics(any()) }
    }

    @Test
    fun fetchLyricsForSong_forcePickResults_bypassesStoredAndLocalLyricsAndSearchesRemote() {
        val musicRepository = mockk<MusicRepository>(relaxed = true)
        val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        val songMetadataEditor = mockk<SongMetadataEditor>(relaxed = true)
        val holder = LyricsStateHolder(
            musicRepository = musicRepository,
            userPreferencesRepository = userPreferencesRepository,
            songMetadataEditor = songMetadataEditor
        )
        val scope = TestScope(StandardTestDispatcher())
        val callback = RecordingLyricsLoadCallback()
        val state = MutableStateFlow(StablePlayerState())
        val song = testSong(albumArtUriString = "content://art/song_art_1.jpg").copy(
            lyrics = "[00:01.00]Existing embedded lyrics"
        )
        val remoteResult = LyricsSearchResult(
            record = LrcLibResponse(
                id = 1,
                name = song.title,
                artistName = song.artist,
                albumName = song.album,
                duration = 295.0,
                plainLyrics = null,
                syncedLyrics = "[00:01.00]Remote line"
            ),
            lyrics = Lyrics(plain = listOf("Remote line"), areFromRemote = true, provider = "Zarz-LRC"),
            rawLyrics = "[00:01.00]Remote line"
        )

        holder.initialize(scope, callback, state)
        coEvery { musicRepository.searchRemoteLyrics(song) } returns Result.success(
            "${song.title} ${song.artist}" to listOf(remoteResult)
        )

        holder.fetchLyricsForSong(
            song = song,
            forcePickResults = true,
            sourcePreference = com.theveloper.pixelplay.data.model.LyricsSourcePreference.EMBEDDED_FIRST
        ) { "Lyrics already available" }
        scope.advanceUntilIdle()

        val uiState = holder.searchUiState.value
        assertThat(uiState).isInstanceOf(LyricsSearchUiState.PickResult::class.java)
        assertThat((uiState as LyricsSearchUiState.PickResult).results).containsExactly(remoteResult)
        coVerify(exactly = 0) { musicRepository.getStoredLyrics(any()) }
        coVerify(exactly = 1) { musicRepository.searchRemoteLyrics(song) }
    }

    private fun testSong(albumArtUriString: String?): Song {
        return Song(
            id = "1",
            title = "Indian Summer",
            artist = "Blood Cultures",
            album = "Happy Birthday",
            path = "/music/indian-summer.mp3",
            contentUriString = "content://media/external/audio/media/1",
            albumArtUriString = albumArtUriString,
            duration = 295_000L,
            mimeType = "audio/mpeg",
            bitrate = 320_000,
            sampleRate = 44_100,
            artistId = 1L,
            albumId = 1L
        )
    }

    private class RecordingLyricsLoadCallback : LyricsLoadCallback {
        override fun onLoadingStarted(songId: String) = Unit

        override fun onLyricsLoaded(songId: String, lyrics: Lyrics?) = Unit
    }
}
