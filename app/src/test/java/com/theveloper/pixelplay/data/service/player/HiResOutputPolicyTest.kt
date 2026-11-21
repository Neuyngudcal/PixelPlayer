package com.theveloper.pixelplay.data.service.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class HiResOutputPolicyTest {

    @Test
    fun settingOff_forcesPcm16BitForAllTracksRegardlessOfBitrateOrCodec() {
        assertThat(
            HiResOutputPolicy.shouldUseHiResFloatOutput(
                hiFiModeEnabled = false,
                sampleMimeType = MimeTypes.AUDIO_FLAC,
                effectiveBitrateBps = 1_921_000,
                pcmEncoding = C.ENCODING_PCM_24BIT,
                sampleRate = 48_000
            )
        ).isFalse()

        assertThat(
            HiResOutputPolicy.shouldUseHiResFloatOutput(
                hiFiModeEnabled = false,
                sampleMimeType = "audio/wav",
                effectiveBitrateBps = 1_411_200,
                pcmEncoding = C.ENCODING_PCM_16BIT,
                sampleRate = 44_100
            )
        ).isFalse()
    }

    @Test
    fun settingOn_disablesFloatOutputForTracksAtOrBelow1000Kbps() {
        val ineligibleCases = listOf(
            MimeTypes.AUDIO_MPEG to 320_000,
            MimeTypes.AUDIO_AAC to 256_000,
            MimeTypes.AUDIO_OPUS to 160_000,
            MimeTypes.AUDIO_FLAC to 850_000,
            MimeTypes.AUDIO_FLAC to 1_000_000
        )

        for ((mime, bitrate) in ineligibleCases) {
            assertThat(
                HiResOutputPolicy.shouldUseHiResFloatOutput(
                    hiFiModeEnabled = true,
                    sampleMimeType = mime,
                    effectiveBitrateBps = bitrate
                )
            ).isFalse()
        }
    }

    @Test
    fun settingOn_enablesFloatOutputForLosslessTracksAbove1000Kbps() {
        val eligibleCases = listOf(
            Triple(MimeTypes.AUDIO_FLAC, 1_001_000, 44_100),
            Triple(MimeTypes.AUDIO_FLAC, 1_921_000, 48_000),
            Triple("audio/wav", 1_411_200, 44_100),
            Triple(MimeTypes.AUDIO_ALAC, 1_150_000, 44_100)
        )

        for ((mime, bitrate, sampleRate) in eligibleCases) {
            assertThat(
                HiResOutputPolicy.shouldUseHiResFloatOutput(
                    hiFiModeEnabled = true,
                    sampleMimeType = mime,
                    effectiveBitrateBps = bitrate,
                    sampleRate = sampleRate
                )
            ).isTrue()
        }
    }

    @Test
    fun settingOn_rejectsLossyMimeTypesEvenAbove1000Kbps() {
        val lossyMimes = listOf(
            MimeTypes.AUDIO_MPEG,
            MimeTypes.AUDIO_AAC,
            MimeTypes.AUDIO_OPUS,
            MimeTypes.AUDIO_VORBIS
        )

        for (mime in lossyMimes) {
            assertThat(
                HiResOutputPolicy.shouldUseHiResFloatOutput(
                    hiFiModeEnabled = true,
                    sampleMimeType = mime,
                    effectiveBitrateBps = 1_500_000
                )
            ).isFalse()
        }
    }

    @Test
    fun settingOn_withUnknownBitrate_usesBitDepthOrSampleRateFallbackForLosslessOnly() {
        assertThat(
            HiResOutputPolicy.shouldUseHiResFloatOutput(
                hiFiModeEnabled = true,
                sampleMimeType = MimeTypes.AUDIO_FLAC,
                effectiveBitrateBps = null,
                pcmEncoding = C.ENCODING_PCM_24BIT,
                sampleRate = 48_000
            )
        ).isTrue()

        assertThat(
            HiResOutputPolicy.shouldUseHiResFloatOutput(
                hiFiModeEnabled = true,
                sampleMimeType = MimeTypes.AUDIO_FLAC,
                effectiveBitrateBps = null,
                pcmEncoding = C.ENCODING_PCM_16BIT,
                sampleRate = 96_000
            )
        ).isTrue()

        assertThat(
            HiResOutputPolicy.shouldUseHiResFloatOutput(
                hiFiModeEnabled = true,
                sampleMimeType = MimeTypes.AUDIO_FLAC,
                effectiveBitrateBps = null,
                pcmEncoding = C.ENCODING_PCM_16BIT,
                sampleRate = 44_100
            )
        ).isFalse()

        assertThat(
            HiResOutputPolicy.shouldUseHiResFloatOutput(
                hiFiModeEnabled = true,
                sampleMimeType = MimeTypes.AUDIO_FLAC,
                effectiveBitrateBps = null,
                pcmEncoding = C.ENCODING_PCM_16BIT,
                sampleRate = 48_000
            )
        ).isFalse()
    }

    @Test
    fun resolveEffectiveBitrateBps_respectsFallbackHierarchy() {
        assertThat(
            HiResOutputPolicy.resolveEffectiveBitrateBps(
                formatAverageBitrate = 1_921_000,
                formatPeakBitrate = 2_100_000,
                mediaItemExtraBitrateBps = 1_500_000,
                fileSizeBytes = 10_000_000L,
                durationMs = 100_000L
            )
        ).isEqualTo(1_921_000)

        assertThat(
            HiResOutputPolicy.resolveEffectiveBitrateBps(
                formatAverageBitrate = Format.NO_VALUE,
                formatPeakBitrate = 2_100_000,
                mediaItemExtraBitrateBps = 1_500_000,
                fileSizeBytes = 10_000_000L,
                durationMs = 100_000L
            )
        ).isEqualTo(2_100_000)

        assertThat(
            HiResOutputPolicy.resolveEffectiveBitrateBps(
                formatAverageBitrate = Format.NO_VALUE,
                formatPeakBitrate = Format.NO_VALUE,
                mediaItemExtraBitrateBps = 1_500_000,
                fileSizeBytes = 10_000_000L,
                durationMs = 100_000L
            )
        ).isEqualTo(1_500_000)

        assertThat(
            HiResOutputPolicy.resolveEffectiveBitrateBps(
                formatAverageBitrate = Format.NO_VALUE,
                formatPeakBitrate = Format.NO_VALUE,
                mediaItemExtraBitrateBps = null,
                fileSizeBytes = 25_000_000L,
                durationMs = 100_000L
            )
        ).isEqualTo(2_000_000)

        assertThat(
            HiResOutputPolicy.resolveEffectiveBitrateBps(
                formatAverageBitrate = Format.NO_VALUE,
                formatPeakBitrate = Format.NO_VALUE,
                mediaItemExtraBitrateBps = null,
                fileSizeBytes = null,
                durationMs = null
            )
        ).isNull()
    }

    @Test
    fun adaptiveHiResAudioSink_gatesPcmFloatSupportPerTrackWhileLeavingPcm16Untouched() {
        val delegate = mockk<AudioSink>()
        val floatFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .setSampleRate(48_000)
            .setChannelCount(2)
            .build()
        val pcm16Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setSampleRate(44_100)
            .setChannelCount(2)
            .build()

        every { delegate.getFormatSupport(floatFormat) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        every { delegate.getFormatSupport(pcm16Format) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

        val disabledSink = AdaptiveHiResAudioSink(delegate, hiFiModeEnabled = false).apply {
            allowFloatOutputForCurrentTrack = true
        }
        assertThat(disabledSink.getFormatSupport(floatFormat))
            .isEqualTo(AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING)
        assertThat(disabledSink.getFormatSupport(pcm16Format))
            .isEqualTo(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY)

        val enabledSink = AdaptiveHiResAudioSink(delegate, hiFiModeEnabled = true)
        enabledSink.allowFloatOutputForCurrentTrack = false
        assertThat(enabledSink.getFormatSupport(floatFormat))
            .isEqualTo(AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING)
        assertThat(enabledSink.getFormatSupport(pcm16Format))
            .isEqualTo(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY)

        enabledSink.allowFloatOutputForCurrentTrack = true
        assertThat(enabledSink.getFormatSupport(floatFormat))
            .isEqualTo(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY)
        assertThat(enabledSink.getFormatSupport(pcm16Format))
            .isEqualTo(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY)
    }
}
