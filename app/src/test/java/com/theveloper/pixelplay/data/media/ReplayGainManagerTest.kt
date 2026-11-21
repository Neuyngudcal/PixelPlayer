package com.theveloper.pixelplay.data.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ReplayGainManagerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `negative cache avoids repeated propertyMapReader reads for untagged file`() {
        val audioFile = File(tempDir.toFile(), "untagged.flac").apply {
            writeText("dummy")
        }
        var readCount = 0
        val manager = ReplayGainManager().apply {
            propertyMapReader = {
                readCount++
                emptyMap()
            }
        }

        assertNull(manager.readReplayGain(audioFile.absolutePath))
        assertNull(manager.readReplayGain(audioFile.absolutePath))
        assertEquals(1, readCount)
    }

    @Test
    fun `cache invalidates when file lastModified changes or invalidateCache is called`() {
        val audioFile = File(tempDir.toFile(), "track.opus").apply {
            writeText("v1")
            setLastModified(1_000_000L)
        }
        var readCount = 0
        var returnTag = false
        val manager = ReplayGainManager().apply {
            propertyMapReader = {
                readCount++
                if (returnTag) {
                    mapOf("R128_TRACK_GAIN" to arrayOf("-2304"))
                } else {
                    emptyMap()
                }
            }
        }

        // First call caches negative hit at t = 1_000_000L
        assertNull(manager.readReplayGain(audioFile.absolutePath))
        assertEquals(1, readCount)

        // Updating timestamp invalidates negative cache entry
        returnTag = true
        assertTrue(audioFile.setLastModified(2_000_000L))
        val updated = manager.readReplayGain(audioFile.absolutePath)
        assertEquals(2, readCount)
        assertEquals(-4.0f, updated?.trackGainDb ?: error("expected gain"), 1e-4f)

        // Manual invalidation clears positive cache entry
        manager.invalidateCache(audioFile.absolutePath)
        assertNull(manager.getCachedReplayGain(audioFile.absolutePath))
    }

    @Test
    fun `extractGainDb skips unparseable first tag and converts R128 Q78 with +5dB alignment`() {
        val audioFile = File(tempDir.toFile(), "fallback.opus").apply {
            writeText("opus")
        }
        val manager = ReplayGainManager().apply {
            propertyMapReader = {
                mapOf(
                    "REPLAYGAIN_TRACK_GAIN" to arrayOf("not-a-number"),
                    // -2304 Q7.8 = -9.0 dB vs -23 LUFS -> -4.0 dB vs ReplayGain -18 LUFS
                    "R128_TRACK_GAIN" to arrayOf("-2304"),
                    // -1280 Q7.8 = -5.0 dB vs -23 LUFS -> 0.0 dB vs ReplayGain -18 LUFS
                    "R128_ALBUM_GAIN" to arrayOf("-1280"),
                )
            }
        }

        val values = manager.readReplayGain(audioFile.absolutePath)
        assertEquals(-4.0f, values?.trackGainDb ?: error("expected track gain"), 1e-4f)
        assertEquals(0.0f, values?.albumGainDb ?: error("expected album gain"), 1e-4f)
    }

    @Test
    fun `gainDbToVolume clamps positive boosts to 1_0f consistent with Player volume bounds`() {
        val manager = ReplayGainManager()
        assertEquals(1.0f, manager.gainDbToVolume(+6.0f), 1e-6f)
        assertEquals(0.5011872f, manager.gainDbToVolume(-6.0f), 1e-5f)
        assertEquals(1.0f, manager.gainDbToVolume(Float.NaN), 1e-6f)
    }
}
