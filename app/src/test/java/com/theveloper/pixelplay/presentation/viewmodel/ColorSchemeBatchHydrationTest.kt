package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.database.AlbumArtThemeDao
import com.theveloper.pixelplay.data.database.AlbumArtThemeEntity
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.data.preferences.ThemeContrastLevel
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.ui.theme.SystemContrastObserver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ColorSchemeBatchHydrationTest {

    private val context: Context = mockk(relaxed = true)
    private val albumArtThemeDao: AlbumArtThemeDao = mockk(relaxed = true)

    @Test
    fun getCachedSchemeOnlyFromMemory_returnsNullWhenOnlySeedIsCached_andReturnsPairAfterBatchHydration() = runTest {
        val uri = "pixelplay_local_art://song/101"
        val seedArgb = 0xFF3A7D44.toInt()
        coEvery {
            albumArtThemeDao.getSeedByUri(uri, ColorSchemeProcessor.SEED_ALGORITHM_VERSION)
        } returns seedArgb

        val processor = ColorSchemeProcessor(context, albumArtThemeDao)
        val extractedSeed = processor.getOrExtractSeedColor(uri)
        assertThat(extractedSeed).isEqualTo(seedArgb)

        // Scheme cache has not been populated yet, so the O(1) main-thread check must stay null
        // without synthesizing 48-role schemes synchronously on the calling thread.
        assertThat(
            processor.getCachedSchemeOnlyFromMemory(uri, AlbumArtPaletteStyle.CONTENT, 0.0)
        ).isNull()

        val hydrated = mutableMapOf<String, ColorSchemePair>()
        processor.getOrGenerateColorSchemesBatch(
            albumArtUris = listOf(uri),
            paletteStyle = AlbumArtPaletteStyle.CONTENT,
            contrastLevel = 0.0
        ) { readyUri, pair ->
            hydrated[readyUri] = pair
        }

        assertThat(hydrated).containsKey(uri)
        val cachedScheme = processor.getCachedSchemeOnlyFromMemory(uri, AlbumArtPaletteStyle.CONTENT, 0.0)
        assertThat(cachedScheme).isEqualTo(hydrated[uri])
        coVerify(exactly = 0) { albumArtThemeDao.getSeedsByUris(any(), any()) }
    }

    @Test
    fun getOrGenerateColorSchemesBatch_queriesRoomOnceForMultipleUris_andReusesMemoryCacheOnSecondCall() = runTest {
        val uri1 = "pixelplay_local_art://song/1"
        val uri2 = "pixelplay_local_art://song/2"
        val uri3 = "pixelplay_local_art://song/3"
        val entities = listOf(
            AlbumArtThemeEntity(uri1, 0xFF3A7D44.toInt(), ColorSchemeProcessor.SEED_ALGORITHM_VERSION),
            AlbumArtThemeEntity(uri2, 0xFF4F6AF5.toInt(), ColorSchemeProcessor.SEED_ALGORITHM_VERSION),
            AlbumArtThemeEntity(uri3, 0xFFD8893A.toInt(), ColorSchemeProcessor.SEED_ALGORITHM_VERSION)
        )
        coEvery {
            albumArtThemeDao.getSeedsByUris(listOf(uri1, uri2, uri3), ColorSchemeProcessor.SEED_ALGORITHM_VERSION)
        } returns entities

        val processor = ColorSchemeProcessor(context, albumArtThemeDao)
        val firstPass = mutableMapOf<String, ColorSchemePair>()
        processor.getOrGenerateColorSchemesBatch(
            albumArtUris = listOf(uri1, uri2, "", uri1, uri3),
            paletteStyle = AlbumArtPaletteStyle.CONTENT,
            contrastLevel = 0.0
        ) { readyUri, pair ->
            firstPass[readyUri] = pair
        }

        assertThat(firstPass.keys).containsExactly(uri1, uri2, uri3)
        coVerify(exactly = 1) {
            albumArtThemeDao.getSeedsByUris(listOf(uri1, uri2, uri3), ColorSchemeProcessor.SEED_ALGORITHM_VERSION)
        }

        val secondPass = mutableMapOf<String, ColorSchemePair>()
        processor.getOrGenerateColorSchemesBatch(
            albumArtUris = listOf(uri1, uri2, uri3),
            paletteStyle = AlbumArtPaletteStyle.CONTENT,
            contrastLevel = 0.0
        ) { readyUri, pair ->
            secondPass[readyUri] = pair
        }

        assertThat(secondPass).isEqualTo(firstPass)
        coVerify(exactly = 1) { albumArtThemeDao.getSeedsByUris(any(), any()) }
    }

    @Test
    fun themeStateHolder_nonEagerFlowDoesNotTriggerDao_andHydrateUpdatesFlow() = runTest {
        val uri = "uri://album/1"
        val seedArgb = 0xFF69B84A.toInt()
        coEvery {
            albumArtThemeDao.getSeedsByUris(listOf(uri), ColorSchemeProcessor.SEED_ALGORITHM_VERSION)
        } returns listOf(
            AlbumArtThemeEntity(uri, seedArgb, ColorSchemeProcessor.SEED_ALGORITHM_VERSION)
        )

        val themePreferencesRepository: ThemePreferencesRepository = mockk(relaxed = true)
        every { themePreferencesRepository.playerThemePreferenceFlow } returns flowOf(ThemePreference.GLOBAL)
        every { themePreferencesRepository.albumArtPaletteStyleFlow } returns flowOf(AlbumArtPaletteStyle.CONTENT)
        every { themePreferencesRepository.themeContrastLevelFlow } returns flowOf(ThemeContrastLevel.STANDARD)

        val systemContrastObserver: SystemContrastObserver = mockk(relaxed = true)
        every { systemContrastObserver.currentContrast() } returns 0.0
        every { systemContrastObserver.systemContrastFlow } returns MutableStateFlow(0.0)

        val processor = ColorSchemeProcessor(context, albumArtThemeDao)
        val themeStateHolder = ThemeStateHolder(
            colorSchemeProcessor = processor,
            themePreferencesRepository = themePreferencesRepository,
            systemContrastObserver = systemContrastObserver
        )

        val flow = themeStateHolder.getAlbumColorSchemeFlow(uri, eager = false)
        assertThat(flow.value).isNull()
        coVerify(exactly = 0) { albumArtThemeDao.getSeedByUri(any(), any()) }
        coVerify(exactly = 0) { albumArtThemeDao.getSeedsByUris(any(), any()) }

        themeStateHolder.hydrateAlbumColorSchemes(listOf(uri))

        assertThat(flow.value).isNotNull()
        coVerify(exactly = 1) {
            albumArtThemeDao.getSeedsByUris(listOf(uri), ColorSchemeProcessor.SEED_ALGORITHM_VERSION)
        }
    }
}
