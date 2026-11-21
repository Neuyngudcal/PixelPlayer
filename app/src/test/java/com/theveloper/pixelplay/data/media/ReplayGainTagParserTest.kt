package com.theveloper.pixelplay.data.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReplayGainTagParserTest {

    @Test
    fun `parses R128 Q78 fixed-point tags per RFC 7845 aligned to ReplayGain -18 LUFS`() {
        // -2304 / 256 = -9.0 dB (vs -23 LUFS) -> -4.0 dB (vs -18 LUFS)
        assertEquals(-4.0f, ReplayGainTagParser.parseGainDb("R128_TRACK_GAIN", "-2304")!!, 1e-4f)
        // +512 / 256 = +2.0 dB (vs -23 LUFS) -> +7.0 dB (vs -18 LUFS)
        assertEquals(7.0f, ReplayGainTagParser.parseGainDb("r128_album_gain", "512")!!, 1e-4f)
        assertEquals(5.0f, ReplayGainTagParser.parseGainDb("R128_TRACK_GAIN", "0")!!, 1e-4f)
    }

    @Test
    fun `parses standard ReplayGain tags with dB suffix and comma decimals`() {
        assertEquals(-6.54f, ReplayGainTagParser.parseGainDb("REPLAYGAIN_TRACK_GAIN", "-6.54 dB")!!, 1e-4f)
        assertEquals(-3.21f, ReplayGainTagParser.parseGainDb("REPLAYGAIN_ALBUM_GAIN", "-3,21 DB")!!, 1e-4f)
        assertNull(ReplayGainTagParser.parseGainDb("REPLAYGAIN_TRACK_GAIN", "NaN"))
        assertNull(ReplayGainTagParser.parseGainDb("REPLAYGAIN_TRACK_GAIN", "Infinity"))
        assertNull(ReplayGainTagParser.parseGainDb("REPLAYGAIN_TRACK_GAIN", ""))
    }

    @Test
    fun `extractGainDb falls through malformed entries to subsequent keys`() {
        val map = mapOf(
            "REPLAYGAIN_TRACK_GAIN" to arrayOf("NaN", "bad"),
            "REPLAYGAIN_TRACK_GAIN_DB" to arrayOf("-7.5 dB"),
        )
        assertEquals(
            -7.5f,
            ReplayGainTagParser.extractGainDb(map, ReplayGainTagParser.TRACK_GAIN_KEYS)!!,
            1e-4f,
        )
    }
}
