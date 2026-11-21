package com.theveloper.pixelplay.data.service.player

import android.content.Context
import android.media.AudioFormat
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.MediaSource
import com.theveloper.pixelplay.utils.MediaItemBuilder
import timber.log.Timber
import java.io.File
import java.util.Locale

@UnstableApi
internal object HiResOutputPolicy {
    /** Minimum bitrate threshold (1000 kbps = 1,000,000 bps) required to activate 32-bit float Hi-Res output. */
    const val HI_RES_MIN_BITRATE_BPS: Int = 1_000_000

    private const val STANDARD_MAX_SAMPLE_RATE_HZ: Int = 48_000

    private val LOSSY_MIME_TYPES = setOf(
        MimeTypes.AUDIO_MPEG,
        MimeTypes.AUDIO_MPEG_L1,
        MimeTypes.AUDIO_MPEG_L2,
        MimeTypes.AUDIO_AAC,
        MimeTypes.AUDIO_OPUS,
        MimeTypes.AUDIO_VORBIS,
        MimeTypes.AUDIO_OGG,
        MimeTypes.AUDIO_AC3,
        MimeTypes.AUDIO_E_AC3,
        MimeTypes.AUDIO_E_AC3_JOC,
        MimeTypes.AUDIO_AMR_NB,
        MimeTypes.AUDIO_AMR_WB
    )

    private val LOSSLESS_MIME_TYPES = setOf(
        MimeTypes.AUDIO_FLAC,
        MimeTypes.AUDIO_ALAC,
        MimeTypes.AUDIO_RAW,
        "audio/wav",
        "audio/x-wav",
        "audio/wave",
        "audio/aiff",
        "audio/x-aiff",
        "audio/dsd",
        "audio/x-dsf"
    )

    fun isLossyMimeType(mimeType: String?): Boolean {
        val normalized = normalizeMime(mimeType) ?: return false
        return normalized in LOSSY_MIME_TYPES
    }

    fun isLosslessMimeType(mimeType: String?): Boolean {
        val normalized = normalizeMime(mimeType) ?: return false
        return normalized in LOSSLESS_MIME_TYPES
    }

    fun isHighBitDepthPcmEncoding(pcmEncoding: Int): Boolean {
        return when (pcmEncoding) {
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_24BIT_BIG_ENDIAN,
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_32BIT_BIG_ENDIAN,
            C.ENCODING_PCM_FLOAT -> true
            else -> false
        }
    }

    fun resolveEffectiveBitrateBps(
        formatAverageBitrate: Int,
        formatPeakBitrate: Int,
        mediaItemExtraBitrateBps: Int?,
        fileSizeBytes: Long?,
        durationMs: Long?
    ): Int? {
        formatAverageBitrate.takeIf { it > 0 && it != Format.NO_VALUE }?.let { return it }
        formatPeakBitrate.takeIf { it > 0 && it != Format.NO_VALUE }?.let { return it }
        mediaItemExtraBitrateBps?.takeIf { it > 0 }?.let { return it }

        if (fileSizeBytes != null && fileSizeBytes > 0L && durationMs != null && durationMs > 0L) {
            val estimatedBps = (fileSizeBytes * 8_000L) / durationMs
            return estimatedBps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().takeIf { it > 0 }
        }
        return null
    }

    fun shouldUseHiResFloatOutput(
        hiFiModeEnabled: Boolean,
        sampleMimeType: String?,
        effectiveBitrateBps: Int?,
        pcmEncoding: Int = C.ENCODING_INVALID,
        sampleRate: Int = Format.NO_VALUE
    ): Boolean {
        if (!hiFiModeEnabled) return false
        if (isLossyMimeType(sampleMimeType)) return false

        if (effectiveBitrateBps != null && effectiveBitrateBps > 0) {
            return effectiveBitrateBps > HI_RES_MIN_BITRATE_BPS
        }

        // Fallback when container/extractor does not advertise bitrate (e.g., some FLAC streams
        // played without pre-populated DB bitrate): allow float only for confirmed high-res lossless streams.
        return isLosslessMimeType(sampleMimeType) &&
            (isHighBitDepthPcmEncoding(pcmEncoding) || sampleRate > STANDARD_MAX_SAMPLE_RATE_HZ)
    }

    private fun normalizeMime(mimeType: String?): String? {
        return mimeType?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() }
    }
}

internal data class TrackHiResExtras(
    val mimeType: String? = null,
    val extraBitrateBps: Int? = null,
    val sampleRate: Int? = null,
    val fileSizeBytes: Long? = null,
    val durationMs: Long? = null
)

@UnstableApi
internal fun extractTrackMetadataForHiRes(mediaItem: MediaItem?): TrackHiResExtras {
    if (mediaItem == null) return TrackHiResExtras()
    val extras = mediaItem.mediaMetadata.extras
    val extraBitrateBps = extras?.getInt(MediaItemBuilder.EXTERNAL_EXTRA_BITRATE, 0)?.takeIf { it > 0 }
    val durationMs = extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION, 0L)?.takeIf { it > 0L }
        ?: mediaItem.mediaMetadata.durationMs?.takeIf { it > 0L }
    val extraMime = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_MIME_TYPE)?.takeIf { it.isNotBlank() }
    val extraSampleRate = extras?.getInt(MediaItemBuilder.EXTERNAL_EXTRA_SAMPLE_RATE, 0)?.takeIf { it > 0 }

    val fileSizeBytes = if (extraBitrateBps == null && durationMs != null && durationMs > 0L) {
        val candidatePath = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH)
            ?.takeIf { it.isNotBlank() }
            ?: mediaItem.localConfiguration?.uri?.takeIf { it.scheme == "file" }?.path
        if (!candidatePath.isNullOrBlank()) {
            runCatching { File(candidatePath).takeIf { it.isFile }?.length() }
                .getOrNull()
                ?.takeIf { it > 0L }
        } else {
            null
        }
    } else {
        null
    }

    return TrackHiResExtras(
        mimeType = extraMime,
        extraBitrateBps = extraBitrateBps,
        sampleRate = extraSampleRate,
        fileSizeBytes = fileSizeBytes,
        durationMs = durationMs
    )
}

/**
 * Per-track gate in front of [DefaultAudioSink] that exposes [AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY]
 * for 32-bit float PCM only when [hiFiModeEnabled] is true AND [allowFloatOutputForCurrentTrack] is true.
 *
 * When a track is ineligible (e.g., lossy or <= 1000 kbps), returning
 * [AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING] instructs both `MediaCodecAudioRenderer` and
 * `FfmpegAudioRenderer` to decode to clean `PCM_16BIT` without rebuilding the ExoPlayer instance.
 */
@UnstableApi
internal class AdaptiveHiResAudioSink(
    delegate: AudioSink,
    private val hiFiModeEnabled: Boolean
) : ForwardingAudioSink(delegate) {

    @Volatile
    var allowFloatOutputForCurrentTrack: Boolean = false

    override fun getFormatSupport(format: Format): Int {
        val support = super.getFormatSupport(format)
        if ((!hiFiModeEnabled || !allowFloatOutputForCurrentTrack) &&
            MimeTypes.AUDIO_RAW == format.sampleMimeType &&
            format.pcmEncoding == C.ENCODING_PCM_FLOAT &&
            support == AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        ) {
            return AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING
        }
        return support
    }

    fun updateForMediaItemFallback(mediaItem: MediaItem?) {
        if (!hiFiModeEnabled || mediaItem == null) {
            allowFloatOutputForCurrentTrack = false
            return
        }
        val extras = extractTrackMetadataForHiRes(mediaItem)
        val effectiveBitrate = HiResOutputPolicy.resolveEffectiveBitrateBps(
            formatAverageBitrate = Format.NO_VALUE,
            formatPeakBitrate = Format.NO_VALUE,
            mediaItemExtraBitrateBps = extras.extraBitrateBps,
            fileSizeBytes = extras.fileSizeBytes,
            durationMs = extras.durationMs
        )
        allowFloatOutputForCurrentTrack = HiResOutputPolicy.shouldUseHiResFloatOutput(
            hiFiModeEnabled = true,
            sampleMimeType = extras.mimeType ?: mediaItem.localConfiguration?.mimeType,
            effectiveBitrateBps = effectiveBitrate,
            sampleRate = extras.sampleRate ?: Format.NO_VALUE
        )
    }
}

/**
 * Custom [MediaCodecAudioRenderer] that resolves per-track eligibility in [onStreamChanged],
 * prevents reusing a float codec for a 16-bit track (or vice versa) in [canReuseCodec],
 * and explicitly requests `PCM_16BIT` in [getMediaFormat] for tracks below 1000 kbps or lossy codecs.
 */
@UnstableApi
internal class AdaptiveHiResMediaCodecAudioRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    private val adaptiveAudioSink: AdaptiveHiResAudioSink,
    private val hiFiModeEnabled: Boolean
) : MediaCodecAudioRenderer(
    context,
    codecAdapterFactory,
    mediaCodecSelector,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    adaptiveAudioSink
) {
    private val reusablePeriod = Timeline.Period()
    private val reusableWindow = Timeline.Window()
    private var currentStreamMediaItem: MediaItem? = null
    private var configuredCodecUsesFloat: Boolean = false

    private fun resolveMediaItem(mediaPeriodId: MediaSource.MediaPeriodId?): MediaItem? {
        val periodId = mediaPeriodId ?: return null
        val currentTimeline = timeline
        if (currentTimeline.isEmpty) return null
        return runCatching {
            val periodIndex = currentTimeline.getIndexOfPeriod(periodId.periodUid)
            if (periodIndex == C.INDEX_UNSET) return null
            val windowIndex = currentTimeline.getPeriod(periodIndex, reusablePeriod).windowIndex
            if (windowIndex !in 0 until currentTimeline.windowCount) return null
            currentTimeline.getWindow(windowIndex, reusableWindow).mediaItem
        }.getOrNull()
    }

    private fun evaluateEligibility(format: Format, mediaItem: MediaItem?): Boolean {
        if (!hiFiModeEnabled) return false
        val extras = extractTrackMetadataForHiRes(mediaItem)
        val effectiveBitrate = HiResOutputPolicy.resolveEffectiveBitrateBps(
            formatAverageBitrate = format.averageBitrate,
            formatPeakBitrate = format.peakBitrate,
            mediaItemExtraBitrateBps = extras.extraBitrateBps,
            fileSizeBytes = extras.fileSizeBytes,
            durationMs = extras.durationMs
        )
        val mimeType = format.sampleMimeType ?: extras.mimeType ?: mediaItem?.localConfiguration?.mimeType
        val sampleRate = format.sampleRate.takeIf { it > 0 && it != Format.NO_VALUE }
            ?: extras.sampleRate
            ?: Format.NO_VALUE
        return HiResOutputPolicy.shouldUseHiResFloatOutput(
            hiFiModeEnabled = hiFiModeEnabled,
            sampleMimeType = mimeType,
            effectiveBitrateBps = effectiveBitrate,
            pcmEncoding = format.pcmEncoding,
            sampleRate = sampleRate
        )
    }

    override fun onStreamChanged(
        formats: Array<Format>,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId
    ) {
        currentStreamMediaItem = resolveMediaItem(mediaPeriodId)
        adaptiveAudioSink.allowFloatOutputForCurrentTrack =
            formats.firstOrNull()?.let { evaluateEligibility(it, currentStreamMediaItem) } ?: false
        super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId)
    }

    override fun canReuseCodec(
        codecInfo: MediaCodecInfo,
        oldFormat: Format,
        newFormat: Format,
        isNewFormatForSameTrack: Boolean
    ): DecoderReuseEvaluation {
        val evaluation = super.canReuseCodec(codecInfo, oldFormat, newFormat, isNewFormatForSameTrack)
        val nextUsesFloat = evaluateEligibility(newFormat, currentStreamMediaItem)
        adaptiveAudioSink.allowFloatOutputForCurrentTrack = nextUsesFloat
        if (nextUsesFloat != configuredCodecUsesFloat) {
            return DecoderReuseEvaluation(
                codecInfo.name,
                oldFormat,
                newFormat,
                DecoderReuseEvaluation.REUSE_RESULT_NO,
                evaluation.discardReasons or DecoderReuseEvaluation.DISCARD_REASON_AUDIO_ENCODING_CHANGED
            )
        }
        return evaluation
    }

    override fun getMediaFormat(
        format: Format,
        codecMimeType: String,
        codecMaxInputSize: Int,
        codecOperatingRate: Float
    ): MediaFormat {
        val useFloat = evaluateEligibility(format, currentStreamMediaItem)
        adaptiveAudioSink.allowFloatOutputForCurrentTrack = useFloat
        configuredCodecUsesFloat = useFloat

        val mediaFormat = super.getMediaFormat(format, codecMimeType, codecMaxInputSize, codecOperatingRate)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !useFloat) {
            mediaFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        }
        Timber.tag("HiResOutput").d(
            "getMediaFormat mime=%s rate=%dHz bitrate=%d/%d -> useFloat=%b",
            format.sampleMimeType,
            format.sampleRate,
            format.averageBitrate,
            format.peakBitrate,
            useFloat
        )
        return mediaFormat
    }
}
