package com.theveloper.pixelplay.data.model

import kotlinx.serialization.Serializable

/**
 * Modelo de datos para las letras de las canciones.
 *
 * @param plain Lista de líneas de texto plano (no sincronizadas).
 * @param synced Lista de pares (milisegundos, línea) para letras sincronizadas.
 * @param areFromRemote Indica si las letras se obtuvieron de una fuente remota.
 */
@Serializable
data class Lyrics(
    val plain: List<String>? = null,
    val synced: List<SyncedLine>? = null,
    val areFromRemote: Boolean = false,
    val provider: String? = null
)

@Serializable
data class SyncedLine(
    val time: Int,
    val line: String,
    val words: List<SyncedWord>? = null, // Null if not a word-by-word synced lyric
    val translation: String? = null, // Translation text paired by identical timestamp
    val romanization: String? = null, // Romanization text paired by identical timestamp
    val endTime: Int? = null, // Optional line end timestamp in ms
    val voice: String? = null, // Optional singer role ("v1", "v2", "v3")
    val isBackground: Boolean = false // True for [bg:...] or ttm:role="x-bg" backing vocals
)

@Serializable
data class SyncedWord(
    val time: Int,
    val word: String,
    val startsNewWord: Boolean = true,
    val endTime: Int? = null // Optional word end timestamp in ms
)
