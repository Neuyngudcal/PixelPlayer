package com.theveloper.pixelplay.presentation.viewmodel

import android.content.ComponentCallbacks2
import android.net.Uri
import android.os.Trace
import androidx.compose.ui.graphics.Color
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.data.preferences.ThemeContrastLevel
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.ui.theme.DarkColorScheme
import com.theveloper.pixelplay.ui.theme.SystemContrastObserver
import com.theveloper.pixelplay.ui.theme.quantizeContrastLevel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeStateHolder @Inject constructor(
    private val colorSchemeProcessor: ColorSchemeProcessor,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val systemContrastObserver: SystemContrastObserver
) {

    private var scope: CoroutineScope? = null
    @Volatile
    private var currentPaletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.default
    @Volatile
    private var currentContrastLevel: Double = ThemeContrastLevel.default.resolve(
        systemContrastObserver.currentContrast()
    )

    private val _effectiveContrastLevel = MutableStateFlow(currentContrastLevel)
    val effectiveContrastLevel: StateFlow<Double> = _effectiveContrastLevel.asStateFlow()

    private val _currentAlbumArtColorSchemePair = MutableStateFlow<ColorSchemePair?>(null)
    val currentAlbumArtColorSchemePair: StateFlow<ColorSchemePair?> = _currentAlbumArtColorSchemePair.asStateFlow()
    private val _currentAlbumArtUri = MutableStateFlow<String?>(null)
    val currentAlbumArtUri: StateFlow<String?> = _currentAlbumArtUri.asStateFlow()

    private val _lavaLampColors = MutableStateFlow<ImmutableList<Color>>(persistentListOf())
    val lavaLampColors: StateFlow<ImmutableList<Color>> = _lavaLampColors.asStateFlow()

    private val playerThemePreference = themePreferencesRepository.playerThemePreferenceFlow

    private val _activePlayerColorSchemePair = MutableStateFlow<ColorSchemePair?>(null)
    val activePlayerColorSchemePair: StateFlow<ColorSchemePair?> = _activePlayerColorSchemePair.asStateFlow()

    fun initialize(scope: CoroutineScope) {
        this.scope = scope

        scope.launch {
            combine(playerThemePreference, _currentAlbumArtColorSchemePair) { playerPref, albumScheme ->
                when (playerPref) {
                    ThemePreference.ALBUM_ART -> albumScheme
                    else -> null
                }
            }.collect { _activePlayerColorSchemePair.value = it }
        }

        scope.launch {
            combine(
                themePreferencesRepository.albumArtPaletteStyleFlow,
                themePreferencesRepository.themeContrastLevelFlow,
                systemContrastObserver.systemContrastFlow
            ) { style, contrastPref, systemContrast ->
                style to contrastPref.resolve(systemContrast)
            }.collect { (style, resolvedContrast) ->
                val paletteChanged =
                    currentPaletteStyle != style ||
                        quantizeContrastLevel(currentContrastLevel) != quantizeContrastLevel(resolvedContrast)
                currentPaletteStyle = style
                currentContrastLevel = resolvedContrast
                _effectiveContrastLevel.value = resolvedContrast

                if (!paletteChanged) return@collect

                val currentUri = _currentAlbumArtUri.value
                if (currentUri != null) {
                    val refreshedScheme = colorSchemeProcessor.getOrGenerateColorScheme(
                        albumArtUri = currentUri,
                        paletteStyle = style,
                        contrastLevel = resolvedContrast
                    )
                    _currentAlbumArtColorSchemePair.value = refreshedScheme
                    synchronized(individualAlbumColorSchemes) {
                        individualAlbumColorSchemes[currentUri]
                    }?.value = refreshedScheme
                }

                // Refresh all active individual album flows directly from cached seeds (< 0.15ms each).
                val activeEntries = synchronized(individualAlbumColorSchemes) {
                    individualAlbumColorSchemes.entries
                        .filter { it.key != currentUri && it.value.value != null }
                        .map { it.key to it.value }
                }
                activeEntries.forEach { (uri, targetFlow) ->
                    val refreshed = colorSchemeProcessor.getOrGenerateColorScheme(
                        albumArtUri = uri,
                        paletteStyle = style,
                        contrastLevel = resolvedContrast
                    )
                    if (refreshed != null) {
                        targetFlow.value = refreshed
                    }
                }
            }
        }

        scope.launch {
            activePlayerColorSchemePair.collect { schemePair ->
                updateLavaLampColors(schemePair)
            }
        }
    }

    suspend fun extractAndGenerateColorScheme(
        albumArtUriAsUri: Uri?,
        currentSongUriString: String?,
        isPreload: Boolean = false
    ) {
        Trace.beginSection("ThemeStateHolder.extractAndGenerateColorScheme")
        try {
            if (albumArtUriAsUri == null) {
                if (!isPreload && currentSongUriString == null) {
                    _currentAlbumArtColorSchemePair.value = null
                    _currentAlbumArtUri.value = null
                }
                return
            }

            val uriString = albumArtUriAsUri.toString()
            val style = currentPaletteStyle
            val contrast = currentContrastLevel

            // Fast path: return immediately on RAM scheme or seed hit with zero IO hop.
            val schemePair = colorSchemeProcessor.getCachedColorSchemeFromMemory(
                albumArtUri = uriString,
                paletteStyle = style,
                contrastLevel = contrast
            ) ?: withContext(Dispatchers.IO) {
                colorSchemeProcessor.getOrGenerateColorScheme(
                    albumArtUri = uriString,
                    paletteStyle = style,
                    contrastLevel = contrast
                )
            }

            if (!isPreload && currentSongUriString == uriString) {
                _currentAlbumArtColorSchemePair.value = schemePair
                _currentAlbumArtUri.value = uriString
            }
        } catch (_: Exception) {
            if (!isPreload && albumArtUriAsUri != null && currentSongUriString == albumArtUriAsUri.toString()) {
                _currentAlbumArtColorSchemePair.value = null
                _currentAlbumArtUri.value = null
            }
        } finally {
            Trace.endSection()
        }
    }

    private fun updateLavaLampColors(schemePair: ColorSchemePair?) {
        val schemeForLava = schemePair?.dark ?: DarkColorScheme
        _lavaLampColors.update {
            listOf(schemeForLava.primary, schemeForLava.secondary, schemeForLava.tertiary).distinct().toImmutableList()
        }
    }

    // LRU Cache for individual album schemes
    private val individualAlbumColorSchemes = object : LinkedHashMap<String, MutableStateFlow<ColorSchemePair?>>(
        64, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableStateFlow<ColorSchemePair?>>?): Boolean {
            return size > 256
        }
    }

    private val emptyAlbumColorScheme = MutableStateFlow<ColorSchemePair?>(null).asStateFlow()
    val emptyAlbumColorSchemeFlow: StateFlow<ColorSchemePair?> = emptyAlbumColorScheme
    private val pendingAlbumColorSchemeLock = Any()
    private val pendingAlbumColorSchemeTargets = mutableMapOf<String, MutableSet<MutableStateFlow<ColorSchemePair?>>>()

    private fun requestAlbumColorSchemeGeneration(
        uriString: String,
        targetFlow: MutableStateFlow<ColorSchemePair?>
    ) {
        if (uriString.isBlank()) return

        val shouldStartRequest = synchronized(pendingAlbumColorSchemeLock) {
            val existingTargets = pendingAlbumColorSchemeTargets[uriString]
            if (existingTargets != null) {
                existingTargets.add(targetFlow)
                false
            } else {
                pendingAlbumColorSchemeTargets[uriString] = mutableSetOf(targetFlow)
                true
            }
        }

        if (!shouldStartRequest) return

        val requestScope = scope
        if (requestScope == null) {
            synchronized(pendingAlbumColorSchemeLock) {
                pendingAlbumColorSchemeTargets.remove(uriString)
            }
            return
        }

        requestScope.launch(Dispatchers.IO) {
            var scheme: ColorSchemePair? = null
            try {
                scheme = colorSchemeProcessor.getOrGenerateColorScheme(
                    albumArtUri = uriString,
                    paletteStyle = currentPaletteStyle,
                    contrastLevel = currentContrastLevel
                )
            } catch (_: Exception) {
                // Ignore
            } finally {
                val targets = synchronized(pendingAlbumColorSchemeLock) {
                    pendingAlbumColorSchemeTargets.remove(uriString)?.toList().orEmpty()
                }
                targets.forEach { it.value = scheme }
            }
        }
    }

    fun getAlbumColorSchemeFlow(
        uriString: String,
        eager: Boolean = true
    ): StateFlow<ColorSchemePair?> {
        if (uriString.isBlank()) return emptyAlbumColorScheme

        val memoryCached = if (eager) {
            colorSchemeProcessor.getCachedColorSchemeFromMemory(
                albumArtUri = uriString,
                paletteStyle = currentPaletteStyle,
                contrastLevel = currentContrastLevel
            )
        } else {
            colorSchemeProcessor.getCachedSchemeOnlyFromMemory(
                albumArtUri = uriString,
                paletteStyle = currentPaletteStyle,
                contrastLevel = currentContrastLevel
            )
        }

        val (flow, isNew) = synchronized(individualAlbumColorSchemes) {
            val existing = individualAlbumColorSchemes[uriString]
            if (existing != null) {
                if (existing.value == null && memoryCached != null) {
                    existing.value = memoryCached
                }
                existing to false
            } else {
                val created = MutableStateFlow(memoryCached)
                individualAlbumColorSchemes[uriString] = created
                created to true
            }
        }

        if (eager && (isNew || flow.value == null) && flow.value == null) {
            requestAlbumColorSchemeGeneration(uriString, flow)
        }

        return flow
    }

    suspend fun hydrateAlbumColorSchemes(uriStrings: List<String>) {
        if (uriStrings.isEmpty()) return

        val pendingUris = synchronized(individualAlbumColorSchemes) {
            uriStrings.asSequence()
                .filter { it.isNotBlank() }
                .distinct()
                .filter { uri -> individualAlbumColorSchemes[uri]?.value == null }
                .toList()
        }
        if (pendingUris.isEmpty()) return

        val style = currentPaletteStyle
        val contrast = currentContrastLevel
        val contrastBucket = quantizeContrastLevel(contrast)

        colorSchemeProcessor.getOrGenerateColorSchemesBatch(
            albumArtUris = pendingUris,
            paletteStyle = style,
            contrastLevel = contrast
        ) { uri, schemePair ->
            if (currentPaletteStyle != style || quantizeContrastLevel(currentContrastLevel) != contrastBucket) {
                return@getOrGenerateColorSchemesBatch
            }
            val targetFlow = synchronized(individualAlbumColorSchemes) {
                individualAlbumColorSchemes[uri]
                    ?: MutableStateFlow<ColorSchemePair?>(schemePair).also {
                        individualAlbumColorSchemes[uri] = it
                    }
            }
            if (targetFlow.value != schemePair) {
                targetFlow.value = schemePair
            }
        }
    }

    fun ensureAlbumColorScheme(uriString: String) {
        if (uriString.isBlank()) return

        val memoryCached = colorSchemeProcessor.getCachedColorSchemeFromMemory(
            albumArtUri = uriString,
            paletteStyle = currentPaletteStyle,
            contrastLevel = currentContrastLevel
        )

        val targetFlow = synchronized(individualAlbumColorSchemes) {
            individualAlbumColorSchemes[uriString]
                ?: MutableStateFlow(memoryCached).also { individualAlbumColorSchemes[uriString] = it }
        }

        if (targetFlow.value == null && memoryCached != null) {
            targetFlow.value = memoryCached
        }
        if (targetFlow.value != null) return
        requestAlbumColorSchemeGeneration(uriString, targetFlow)
    }

    suspend fun getOrGenerateColorScheme(uriString: String): ColorSchemePair? {
        return colorSchemeProcessor.getOrGenerateColorScheme(
            albumArtUri = uriString,
            paletteStyle = currentPaletteStyle,
            contrastLevel = currentContrastLevel
        )
    }

    suspend fun forceRegenerateColorScheme(
        uriString: String?,
        @Suppress("UNUSED_PARAMETER") regenerateAllStyles: Boolean = false
    ) {
        if (uriString == null) {
            _currentAlbumArtColorSchemePair.value = null
            _currentAlbumArtUri.value = null
            return
        }

        // Seed cache is style- and contrast-independent, so regenerating the seed once
        // invalidates and refreshes every palette style automatically.
        colorSchemeProcessor.invalidateScheme(uriString)

        val newScheme = colorSchemeProcessor.getOrGenerateColorScheme(
            albumArtUri = uriString,
            paletteStyle = currentPaletteStyle,
            contrastLevel = currentContrastLevel,
            forceRefresh = true
        )

        val activeFlow = synchronized(individualAlbumColorSchemes) {
            individualAlbumColorSchemes[uriString]
        }
        if (activeFlow != null) {
            activeFlow.value = newScheme
        }

        if (_currentAlbumArtUri.value == uriString) {
            _currentAlbumArtColorSchemePair.value = newScheme
        }
    }

    @Suppress("DEPRECATION")
    fun trimMemory(level: Int) {
        colorSchemeProcessor.clearMemoryCache()

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            synchronized(individualAlbumColorSchemes) {
                individualAlbumColorSchemes.clear()
            }
        }

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            synchronized(pendingAlbumColorSchemeLock) {
                pendingAlbumColorSchemeTargets.clear()
            }
        }
    }

    fun onCleared() {
        scope = null
    }
}
