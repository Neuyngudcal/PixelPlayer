package com.theveloper.pixelplay.presentation.viewmodel

import androidx.compose.runtime.Immutable
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import kotlinx.collections.immutable.ImmutableList

@Immutable
sealed interface LyricsSearchUiState {
    object Idle : LyricsSearchUiState
    object Loading : LyricsSearchUiState
    @Immutable
    data class PickResult(
        val query: String,
        val results: ImmutableList<LyricsSearchResult>
    ) : LyricsSearchUiState
    @Immutable
    data class Success(val lyrics: Lyrics) : LyricsSearchUiState
    @Immutable
    data class NotFound(val message: String, val allowManualSearch: Boolean = true) : LyricsSearchUiState
    @Immutable
    data class Error(val message: String, val query: String? = null) : LyricsSearchUiState
}
