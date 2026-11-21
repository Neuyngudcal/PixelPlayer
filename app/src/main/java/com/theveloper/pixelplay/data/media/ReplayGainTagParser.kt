package com.theveloper.pixelplay.data.media

/**
 * Shared ReplayGain / Opus R128 loudness tag parser used by both
 * [ReplayGainManager] and [AudioMetadataReader].
 */
object ReplayGainTagParser {

    /**
     * RFC 7845 Section 5.2 specifies `R128_TRACK_GAIN` and `R128_ALBUM_GAIN` relative to
     * `-23 LUFS`, whereas standard ReplayGain tags target `-18 LUFS` (+5.0 dB difference).
     */
    const val OPUS_R128_TO_REPLAYGAIN_OFFSET_DB = 5.0f

    private const val Q78_SCALE = 256.0f

    val TRACK_GAIN_KEYS: List<String> = listOf(
        "REPLAYGAIN_TRACK_GAIN",
        "REPLAYGAIN_TRACK_GAIN_DB",
        "R128_TRACK_GAIN",
    )

    val ALBUM_GAIN_KEYS: List<String> = listOf(
        "REPLAYGAIN_ALBUM_GAIN",
        "REPLAYGAIN_ALBUM_GAIN_DB",
        "R128_ALBUM_GAIN",
    )

    /**
     * Resolves the first parseable gain value (in ReplayGain-aligned dB) from [propertyMap]
     * using [keys] in priority order. Unparseable values are skipped so fallback keys are tried.
     */
    fun extractGainDb(
        propertyMap: Map<String, Array<String>>,
        keys: List<String>,
    ): Float? {
        for (key in keys) {
            val values = propertyMap[key]
                ?: propertyMap.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
                ?: continue
            for (rawValue in values) {
                val parsed = parseGainDb(tagKey = key, rawValue = rawValue)
                if (parsed != null) {
                    return parsed
                }
            }
        }
        return null
    }

    /**
     * Parses a loudness tag value into ReplayGain-equivalent decibels (`-18 LUFS` reference).
     *
     * - For `R128_*` keys (case-insensitive `tagKey.startsWith("R128_")`): parses the signed
     *   RFC 7845 Q7.8 fixed-point `1/256 dB` value (`rawQ78 / 256.0f`) and adds
     *   [OPUS_R128_TO_REPLAYGAIN_OFFSET_DB] (`+5.0f` dB).
     * - For standard ReplayGain keys (`REPLAYGAIN_*`): parses the numeric float while stripping
     *   an optional `"dB"` suffix (case-insensitive) and normalizing commas to dots.
     *
     * Non-finite (`NaN`/`Infinity`) or malformed strings return `null`.
     */
    fun parseGainDb(tagKey: String, rawValue: String?): Float? {
        val normalizedInput = rawValue
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace(',', '.')
            ?: return null

        val cleanedNumber = normalizedInput
            .replace(Regex("(?i)\\s*dB$"), "")
            .trim()
        val parsed = cleanedNumber.toFloatOrNull()?.takeIf { it.isFinite() } ?: return null

        val resultDb = if (tagKey.trim().startsWith("R128_", ignoreCase = true)) {
            (parsed / Q78_SCALE) + OPUS_R128_TO_REPLAYGAIN_OFFSET_DB
        } else {
            parsed
        }
        return resultDb.takeIf { it.isFinite() }
    }
}
