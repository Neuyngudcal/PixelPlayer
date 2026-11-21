package com.theveloper.pixelplay.data.service.http

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MediaFileHttpServerAdtsAndResampleTest {

    @Test
    fun resolveAdtsSampleRateAndDecimation_capsHiResAbove96kHzToExactAdtsRates() {
        assertThat(resolveAdtsSampleRateAndDecimation(192_000))
            .isEqualTo(AdtsResamplePlan(targetSampleRate = 96_000, decimationFactor = 2))
        assertThat(resolveAdtsSampleRateAndDecimation(176_400))
            .isEqualTo(AdtsResamplePlan(targetSampleRate = 88_200, decimationFactor = 2))
        assertThat(resolveAdtsSampleRateAndDecimation(384_000))
            .isEqualTo(AdtsResamplePlan(targetSampleRate = 96_000, decimationFactor = 4))
        assertThat(resolveAdtsSampleRateAndDecimation(48_000))
            .isEqualTo(AdtsResamplePlan(targetSampleRate = 48_000, decimationFactor = 1))
        assertThat(resolveAdtsSampleRateAndDecimation(44_100))
            .isEqualTo(AdtsResamplePlan(targetSampleRate = 44_100, decimationFactor = 1))
    }

    @Test
    fun buildAdtsHeader_encodesFrequencyIndexChannelConfigAndFrameLength() {
        val header = buildAdtsHeader(sampleRate = 96_000, channels = 2, aacFrameSize = 313)
        assertThat(header).hasLength(7)

        // Sync word 0xFFF, MPEG-4, no CRC
        assertThat(header[0]).isEqualTo(0xFF.toByte())
        assertThat(header[1]).isEqualTo(0xF1.toByte())

        val byte2 = header[2].toInt() and 0xFF
        val byte3 = header[3].toInt() and 0xFF
        val byte4 = header[4].toInt() and 0xFF
        val byte5 = header[5].toInt() and 0xFF

        val profileObjMinusOne = (byte2 ushr 6) and 0x3
        val freqIdx = (byte2 ushr 2) and 0xF
        val channelCfg = ((byte2 and 0x1) shl 2) or ((byte3 ushr 6) and 0x3)
        val frameLength = ((byte3 and 0x3) shl 11) or (byte4 shl 3) or ((byte5 ushr 5) and 0x7)

        assertThat(profileObjMinusOne).isEqualTo(1) // AAC-LC
        assertThat(freqIdx).isEqualTo(0)            // 96000 Hz
        assertThat(channelCfg).isEqualTo(2)         // Stereo (even when source was 5.1)
        assertThat(frameLength).isEqualTo(313 + 7)
    }

    @Test
    fun downmixAndDecimatePcm16_downmixes6ChannelsAndDecimates2xCleanly() {
        // 2 frames of 6-channel PCM: c0=1000, c1=-1000, c2=0, lfe=5000, c4=0, c5=0
        val inputFrames = 2
        val inChannels = 6
        val pcmBytes = ByteArray(inputFrames * inChannels * 2)
        val inShorts = ByteBuffer.wrap(pcmBytes).order(ByteOrder.nativeOrder()).asShortBuffer()
        repeat(inputFrames) {
            inShorts.put(1000)
            inShorts.put(-1000)
            inShorts.put(0)
            inShorts.put(5000)
            inShorts.put(0)
            inShorts.put(0)
        }

        val outBytes = ByteArray(2 * 2) // 1 stereo frame after 2x decimation
        val written = downmixAndDecimatePcm16(
            pcmBytes = pcmBytes,
            numInputBytes = pcmBytes.size,
            inChannels = 6,
            outChannels = 2,
            decimationFactor = 2,
            outBytes = outBytes
        )

        assertThat(written).isEqualTo(4)
        val outShorts = ByteBuffer.wrap(outBytes, 0, written).order(ByteOrder.nativeOrder()).asShortBuffer()
        assertThat(outShorts.get()).isEqualTo(500.toShort())   // 1000 * 0.5f
        assertThat(outShorts.get()).isEqualTo((-500).toShort()) // -1000 * 0.5f
    }
}
