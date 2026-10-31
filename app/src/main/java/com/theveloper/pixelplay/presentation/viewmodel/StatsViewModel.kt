package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository.PlaybackStatsSummary
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {

    data class StatsUiState(
        val selectedRange: StatsTimeRange = StatsTimeRange.WEEK,
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val summary: PlaybackStatsSummary? = null,
        val availableRanges: List<StatsTimeRange> = StatsTimeRange.entries
    )

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val _weeklyOverview = MutableStateFlow<PlaybackStatsSummary?>(null)
    val weeklyOverview: StateFlow<PlaybackStatsSummary?> = _weeklyOverview.asStateFlow()

    private val _homeOverview = MutableStateFlow<PlaybackStatsSummary?>(null)
    val homeOverview: StateFlow<PlaybackStatsSummary?> = _homeOverview.asStateFlow()

    private val songsMutex = Mutex()
    @Volatile
    private var cachedSongsById: Map<String, Song> = emptyMap()

    init {
        observeStatsRefreshFlow()
        viewModelScope.launch {
            // Defer heavy JSON history parsing & multi-range aggregation until HomeScreen finishes initial composition
            delay(400L)
            refreshRangeAndHomeOverview(
                range = StatsTimeRange.WEEK,
                showLoading = true
            )
        }
    }

    fun onRangeSelected(range: StatsTimeRange) {
        if (range == _uiState.value.selectedRange && !_uiState.value.isLoading) {
            return
        }
        refreshRange(
            range = range,
            showLoading = true,
            updateWeeklyOverview = range == StatsTimeRange.WEEK
        )
    }

    fun refreshWeeklyOverview() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val songs = loadSongs()
                    playbackStatsRepository.loadSummary(StatsTimeRange.WEEK, songs)
                }
            }.onSuccess { summary ->
                _weeklyOverview.value = summary
            }.onFailure { throwable ->
                Timber.e(throwable, "Failed to load weekly stats overview")
                _weeklyOverview.value = null
            }
        }
    }

    fun refreshHomeOverview(precomputedWeekSummary: PlaybackStatsSummary? = null) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (precomputedWeekSummary != null && precomputedWeekSummary.hasListeningActivity()) {
                        return@withContext precomputedWeekSummary
                    }
                    val songs = loadSongs()
                    for (range in HomeOverviewRanges) {
                        if (range == StatsTimeRange.WEEK && precomputedWeekSummary != null) {
                            continue
                        }
                        val summary = playbackStatsRepository.loadSummary(range, songs)
                        if (summary.hasListeningActivity()) {
                            return@withContext summary
                        }
                    }
                    null
                }
            }.onSuccess { summary ->
                _homeOverview.value = summary
            }.onFailure { throwable ->
                Timber.e(throwable, "Failed to load home stats overview")
                _homeOverview.value = null
            }
        }
    }

    private fun refreshRangeAndHomeOverview(
        range: StatsTimeRange,
        showLoading: Boolean
    ) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true, isRefreshing = false, selectedRange = range) }
            } else {
                _uiState.update { it.copy(isRefreshing = true, selectedRange = range) }
            }
            val summaryResult = runCatching {
                withContext(Dispatchers.IO) {
                    val songs = loadSongs()
                    playbackStatsRepository.loadSummary(range, songs)
                }
            }
            val loadedSummary = summaryResult.getOrNull()
            if (range == StatsTimeRange.WEEK && loadedSummary != null) {
                _weeklyOverview.value = loadedSummary
            }
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    summary = loadedSummary,
                    selectedRange = range
                )
            }
            summaryResult.exceptionOrNull()?.let { Timber.e(it, "Failed to load stats for range %s", range) }

            if (range != StatsTimeRange.WEEK) {
                refreshWeeklyOverview()
            }
            refreshHomeOverview(precomputedWeekSummary = if (range == StatsTimeRange.WEEK) loadedSummary else null)
        }
    }

    private fun refreshRange(
        range: StatsTimeRange,
        showLoading: Boolean = true,
        updateWeeklyOverview: Boolean = false
    ) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true, isRefreshing = false, selectedRange = range) }
            } else {
                _uiState.update { it.copy(isRefreshing = true, selectedRange = range) }
            }
            val summary = runCatching {
                withContext(Dispatchers.IO) {
                    val songs = loadSongs()
                    playbackStatsRepository.loadSummary(range, songs)
                }
            }
            summary.getOrNull()?.let { loaded ->
                if (updateWeeklyOverview) {
                    _weeklyOverview.value = loaded
                }
            }
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    summary = summary.getOrNull(),
                    selectedRange = range
                )
            }
            summary.exceptionOrNull()?.let { Timber.e(it, "Failed to load stats for range %s", range) }
        }
    }

    private fun observeStatsRefreshFlow() {
        viewModelScope.launch {
            playbackStatsRepository.refreshFlow
                .drop(1)
                .collectLatest {
                    refreshRangeAndHomeOverview(
                        range = _uiState.value.selectedRange,
                        showLoading = false
                    )
                }
        }
    }

    fun requestStatsRefresh() {
        playbackStatsRepository.requestRefresh()
    }

    fun forceRegenerateStats() {
        cachedSongsById = emptyMap()
        playbackStatsRepository.requestRefresh()
    }

    private suspend fun loadSongs(): List<Song> = songsMutex.withLock {
        val playedIds = playbackStatsRepository.getPlayedSongIds()
        if (playedIds.isEmpty()) return@withLock emptyList()

        val missingIds = playedIds.filterNot { cachedSongsById.containsKey(it) }
        if (missingIds.isNotEmpty()) {
            val fetchedSongs = missingIds.chunked(SONG_ID_QUERY_CHUNK_SIZE).flatMap { chunk ->
                musicRepository.getSongsByIds(chunk).first()
            }
            if (fetchedSongs.isNotEmpty()) {
                cachedSongsById = cachedSongsById + fetchedSongs.associateBy { it.id }
            }
        }
        playedIds.mapNotNull { cachedSongsById[it] }
    }

    private fun PlaybackStatsSummary.hasListeningActivity(): Boolean {
        return totalDurationMs > 0L ||
            totalPlayCount > 0 ||
            uniqueSongs > 0 ||
            activeDays > 0 ||
            totalSessions > 0
    }

    private companion object {
        const val SONG_ID_QUERY_CHUNK_SIZE = 500
        val HomeOverviewRanges = listOf(
            StatsTimeRange.WEEK,
            StatsTimeRange.MONTH,
            StatsTimeRange.YEAR,
            StatsTimeRange.ALL
        )
    }
}
