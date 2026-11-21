package com.theveloper.pixelplay.data.equalizer

import android.media.audiofx.Equalizer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EqualizerManagerTest {

    @Test
    fun `failed audio session does not disable equalizer for subsequent valid sessions`() = runTest {
        val eqSession20 = mockk<Equalizer>(relaxed = true).apply {
            every { bandLevelRange } returns shortArrayOf(-1500, 1500)
            every { numberOfBands } returns 5.toShort()
        }

        val manager = EqualizerManager().apply {
            equalizerFactory = { sessionId ->
                if (sessionId == 10) {
                    throw IllegalStateException("Offload session does not expose EQ")
                }
                eqSession20
            }
            bassBoostFactory = { throw UnsupportedOperationException("No BassBoost") }
            virtualizerFactory = { throw UnsupportedOperationException("No Virtualizer") }
            loudnessEnhancerFactory = { throw UnsupportedOperationException("No Loudness") }
        }

        manager.restoreState(
            enabled = true,
            presetName = "flat",
            customBands = List(10) { 0 },
            bassBoostEnabled = true,
            bassBoostStrength = 500,
            virtualizerEnabled = false,
            virtualizerStrength = 0,
            loudnessEnabled = false,
            loudnessStrength = 0,
        )

        // Session 10 fails (e.g., offload/BT), but user preferences remain intact
        manager.attachToAudioSession(10)
        assertFalse(manager.isAttached)
        assertTrue(manager.isEnabled.value)
        assertTrue(manager.bassBoostEnabled.value)

        // Session 20 succeeds when audio route switches back to normal PCM
        manager.attachToAudioSession(20)
        assertTrue(manager.isAttached)
        verify(atLeast = 1) { eqSession20.enabled = true }
    }

    @Test
    fun `concurrent attachToAudioSession calls serialize via Mutex without leaking previous session`() = runTest {
        val createdBySession = mutableMapOf<Int, Equalizer>()
        val manager = EqualizerManager().apply {
            equalizerFactory = { sessionId ->
                mockk<Equalizer>(relaxed = true).apply {
                    every { bandLevelRange } returns shortArrayOf(-1500, 1500)
                    every { numberOfBands } returns 5.toShort()
                    createdBySession[sessionId] = this
                }
            }
            bassBoostFactory = { mockk(relaxed = true) }
            virtualizerFactory = { mockk(relaxed = true) }
            loudnessEnhancerFactory = { mockk(relaxed = true) }
        }

        val jobs = (1..5).map { idx ->
            async { manager.attachToAudioSession(idx) }
        }
        jobs.awaitAll()

        assertTrue(manager.isAttached)
        // Every intermediate session's Equalizer (except possibly the final attached one) must be released cleanly
        val releasedCount = createdBySession.values.count { eq ->
            runCatching { verify(atLeast = 1) { eq.release() } }.isSuccess
        }
        assertTrue(releasedCount >= createdBySession.size - 1)
    }
}
