@file:Suppress("DEPRECATION")
package com.theveloper.pixelplay.data.service.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * An [AudioProcessor] that downmixes 5.1 (6-channel) and 7.1 (8-channel) surround audio
 * to stereo (2-channel) PCM using headroom-normalized ITU-R BS.775 downmix coefficients.
 *
 * Unnormalized ITU/Dolby matrix weights:
 * ```
 *   L_raw = FL + 0.7071·FC + 0.7071·SL [+ 0.7071·SBL] + 0.5·LFE
 *   R_raw = FR + 0.7071·FC + 0.7071·SR [+ 0.7071·SBR] + 0.5·LFE
 * ```
 * Each output channel divides by its L1 gain sum (`sum(|w_i|)`) so full-scale multi-channel
 * signals stay strictly within `[-1.0, 1.0]` / `[Short.MIN_VALUE, Short.MAX_VALUE]` without clipping.
 *
 * FFmpeg output channel order assumed:
 * - 5.1: FL, FR, FC, LFE, SL, SR
 * - 7.1: FL, FR, FC, LFE, SL, SR, SBL, SBR
 *
 * This processor is only active for 6-channel or 8-channel 16-bit/float PCM input.
 * All other formats are passed through without modification.
 */
@UnstableApi
class SurroundDownmixProcessor : AudioProcessor {

    internal data class DownmixCoefficients(
        val front: Float,
        val center: Float,
        val surround: Float,
        val back: Float,
        val lfe: Float,
    )

    companion object {
        /** Standard ITU-R BS.775 center/surround weight: 1/√2 ≈ 0.7071 */
        private const val WEIGHT_CENTER = 0.70710677f
        private const val WEIGHT_SURROUND = 0.70710677f
        /** Subdued LFE weight (-6 dB) before headroom normalization */
        private const val WEIGHT_LFE = 0.5f

        private const val L1_SUM_51 = 1.0f + WEIGHT_CENTER + WEIGHT_SURROUND + WEIGHT_LFE
        private const val L1_SUM_71 = 1.0f + WEIGHT_CENTER + WEIGHT_SURROUND + WEIGHT_SURROUND + WEIGHT_LFE

        internal val COEFFS_51 = DownmixCoefficients(
            front = 1.0f / L1_SUM_51,
            center = WEIGHT_CENTER / L1_SUM_51,
            surround = WEIGHT_SURROUND / L1_SUM_51,
            back = 0f,
            lfe = WEIGHT_LFE / L1_SUM_51,
        )

        internal val COEFFS_71 = DownmixCoefficients(
            front = 1.0f / L1_SUM_71,
            center = WEIGHT_CENTER / L1_SUM_71,
            surround = WEIGHT_SURROUND / L1_SUM_71,
            back = WEIGHT_SURROUND / L1_SUM_71,
            lfe = WEIGHT_LFE / L1_SUM_71,
        )

        /** Largest supported surround layout (7.1). */
        private const val MAX_SUPPORTED_CHANNELS = 8

        // Shared FFmpeg channel indices (FL..SR match between 5.1 and 7.1)
        private const val CH_FL = 0
        private const val CH_FR = 1
        private const val CH_FC = 2
        private const val CH_LFE = 3
        private const val CH_SL = 4
        private const val CH_SR = 5
        private const val CH_SBL = 6
        private const val CH_SBR = 7
    }

    private var inputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private val frameScratch = FloatArray(MAX_SUPPORTED_CHANNELS)

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        val isSupported = (inputAudioFormat.channelCount == 6 || inputAudioFormat.channelCount == 8)
                && (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT)

        return if (isSupported) {
            inputFormat = inputAudioFormat
            outputFormat = AudioFormat(
                inputAudioFormat.sampleRate,
                /* channelCount = */ 2,
                inputAudioFormat.encoding
            )
            outputFormat
        } else {
            inputFormat = AudioFormat.NOT_SET
            outputFormat = AudioFormat.NOT_SET
            inputAudioFormat // pass-through
        }
    }

    override fun isActive(): Boolean = outputFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) return

        val channelCount = inputFormat.channelCount
        val isFloat = inputFormat.encoding == C.ENCODING_PCM_FLOAT
        val sampleBytes = if (isFloat) Float.SIZE_BYTES else Short.SIZE_BYTES
        val bytesPerFrame = channelCount * sampleBytes
        val frameCount = inputBuffer.remaining() / bytesPerFrame

        if (frameCount == 0) {
            // Leave trailing partial-frame bytes unconsumed so Media3 re-presents them next call.
            outputBuffer = AudioProcessor.EMPTY_BUFFER
            return
        }

        val matrix = if (channelCount == 6) COEFFS_51 else COEFFS_71
        val hasBackChannels = channelCount == 8
        outputBuffer = ensureOutputBuffer(frameCount * 2 * sampleBytes)

        val reader = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        repeat(frameCount) {
            for (ch in 0 until channelCount) {
                frameScratch[ch] = if (isFloat) reader.float else reader.short.toFloat()
            }
            val backLeft = if (hasBackChannels) matrix.back * frameScratch[CH_SBL] else 0f
            val backRight = if (hasBackChannels) matrix.back * frameScratch[CH_SBR] else 0f
            val left = matrix.front * frameScratch[CH_FL] +
                matrix.center * frameScratch[CH_FC] +
                matrix.surround * frameScratch[CH_SL] +
                backLeft +
                matrix.lfe * frameScratch[CH_LFE]
            val right = matrix.front * frameScratch[CH_FR] +
                matrix.center * frameScratch[CH_FC] +
                matrix.surround * frameScratch[CH_SR] +
                backRight +
                matrix.lfe * frameScratch[CH_LFE]

            if (isFloat) {
                outputBuffer.putFloat(left.coerceIn(-1f, 1f))
                outputBuffer.putFloat(right.coerceIn(-1f, 1f))
            } else {
                outputBuffer.putShort(
                    left.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                )
                outputBuffer.putShort(
                    right.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                )
            }
        }

        inputBuffer.position(inputBuffer.position() + frameCount * bytesPerFrame)
        outputBuffer.flip()
    }

    private fun ensureOutputBuffer(requiredCapacity: Int): ByteBuffer {
        return if (outputBuffer.capacity() < requiredCapacity) {
            ByteBuffer.allocateDirect(requiredCapacity).order(ByteOrder.nativeOrder()).also {
                outputBuffer = it
            }
        } else {
            outputBuffer.clear()
            outputBuffer
        }
    }

    override fun getOutput(): ByteBuffer {
        val pending = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return pending
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun queueEndOfStream() { inputEnded = true }

    @Deprecated("Media3 AudioProcessor now prefers flush(StreamMetadata); kept for interface compatibility")
    @Suppress("DEPRECATION")
    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
    }
}
