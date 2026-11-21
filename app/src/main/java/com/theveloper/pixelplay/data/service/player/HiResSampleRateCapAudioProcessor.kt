package com.theveloper.pixelplay.data.service.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Caps ultra-high-rate PCM to a rate Android devices handle more reliably.
 *
 * Media3/AudioTrack can end up in unstable device-specific paths for sample rates above 192 kHz.
 * Reducing 352.8/384 kHz streams before they reach the sink avoids the "loading audio" hang while
 * preserving normal playback for standard and regular hi-res files.
 */
@UnstableApi
class HiResSampleRateCapAudioProcessor(
    private val maxOutputSampleRateHz: Int = 192_000
) : AudioProcessor {

    private companion object {
        private val NATIVE_ORDER = ByteOrder.nativeOrder()
        private val NATIVE_ORDER_IS_BIG_ENDIAN = NATIVE_ORDER == ByteOrder.BIG_ENDIAN
    }

    private var inputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputFormat: AudioFormat = AudioFormat.NOT_SET
    private var downsampleFactor: Int = 1
    private var decimationKernel: FloatArray = floatArrayOf(1f)
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var pendingBytes = ByteArray(0)
    private var pendingSize = 0
    private var floatAccumulators = FloatArray(0)
    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        val shouldDownsample = (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) &&
            inputAudioFormat.channelCount > 0 &&
            inputAudioFormat.sampleRate > maxOutputSampleRateHz

        if (!shouldDownsample) {
            inputFormat = AudioFormat.NOT_SET
            outputFormat = AudioFormat.NOT_SET
            downsampleFactor = 1
            decimationKernel = floatArrayOf(1f)
            pendingSize = 0
            return inputAudioFormat
        }

        downsampleFactor = ceil(
            inputAudioFormat.sampleRate.toDouble() / maxOutputSampleRateHz.toDouble()
        ).toInt().coerceAtLeast(2)
        decimationKernel = buildDecimationKernel(downsampleFactor)

        inputFormat = inputAudioFormat
        outputFormat = AudioFormat(
            inputAudioFormat.sampleRate / downsampleFactor,
            inputAudioFormat.channelCount,
            inputAudioFormat.encoding
        )
        pendingSize = 0
        ensurePendingCapacity(maxPendingBytes())
        ensureAccumulatorCapacity(inputAudioFormat.channelCount)
        return outputFormat
    }

    override fun isActive(): Boolean = outputFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) return

        if (inputFormat.encoding == C.ENCODING_PCM_FLOAT) {
            processFloat(inputBuffer)
        } else {
            process16Bit(inputBuffer)
        }
        inputBuffer.position(inputBuffer.limit())
    }

    private fun process16Bit(inputBuffer: ByteBuffer) {
        val bytesPerFrame = inputFormat.channelCount * Short.SIZE_BYTES
        val processableFrameCount = processableFrameCount(bytesPerFrame, inputBuffer.remaining())
        if (processableFrameCount == 0) {
            outputBuffer = AudioProcessor.EMPTY_BUFFER
            stashPendingBytes(inputBuffer)
            return
        }

        outputBuffer = ensureOutputBuffer(processableFrameCount * bytesPerFrame)
        val shortInput = inputBuffer.duplicate().order(NATIVE_ORDER)
        var pendingReadOffset = 0

        repeat(processableFrameCount) {
            java.util.Arrays.fill(floatAccumulators, 0f)
            for (tap in 0 until downsampleFactor) {
                val weight = decimationKernel[tap]
                if (pendingReadOffset < pendingSize) {
                    for (ch in 0 until inputFormat.channelCount) {
                        floatAccumulators[ch] += readShortFromPending(pendingReadOffset).toFloat() * weight
                        pendingReadOffset += Short.SIZE_BYTES
                    }
                } else {
                    for (ch in 0 until inputFormat.channelCount) {
                        floatAccumulators[ch] += shortInput.short.toFloat() * weight
                    }
                }
            }
            for (ch in 0 until inputFormat.channelCount) {
                outputBuffer.putShort(
                    floatAccumulators[ch]
                        .roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                )
            }
        }
        storePendingRemainder(pendingReadOffset, shortInput)
        outputBuffer.flip()
    }

    private fun processFloat(inputBuffer: ByteBuffer) {
        val bytesPerSample = Float.SIZE_BYTES
        val bytesPerFrame = inputFormat.channelCount * bytesPerSample
        val processableFrameCount = processableFrameCount(bytesPerFrame, inputBuffer.remaining())
        if (processableFrameCount == 0) {
            outputBuffer = AudioProcessor.EMPTY_BUFFER
            stashPendingBytes(inputBuffer)
            return
        }

        outputBuffer = ensureOutputBuffer(processableFrameCount * bytesPerFrame)
        val floatInput = inputBuffer.duplicate().order(NATIVE_ORDER)
        var pendingReadOffset = 0

        repeat(processableFrameCount) {
            java.util.Arrays.fill(floatAccumulators, 0f)
            for (tap in 0 until downsampleFactor) {
                val weight = decimationKernel[tap]
                if (pendingReadOffset < pendingSize) {
                    for (ch in 0 until inputFormat.channelCount) {
                        floatAccumulators[ch] += readFloatFromPending(pendingReadOffset) * weight
                        pendingReadOffset += Float.SIZE_BYTES
                    }
                } else {
                    for (ch in 0 until inputFormat.channelCount) {
                        floatAccumulators[ch] += floatInput.float * weight
                    }
                }
            }
            for (ch in 0 until inputFormat.channelCount) {
                outputBuffer.putFloat(floatAccumulators[ch].coerceIn(-1f, 1f))
            }
        }
        storePendingRemainder(pendingReadOffset, floatInput)
        outputBuffer.flip()
    }

    /**
     * Builds a normalized symmetric Hamming-windowed sinc low-pass FIR kernel of length [factor]
     * scaled to normalized cutoff `0.45 / factor`.
     * For `factor == 2`, symmetry gives exact `[0.5f, 0.5f]` anti-aliasing weights; for higher
     * integer decimation factors, tapered outer weights attenuate stopband aliasing better than
     * flat boxcar averaging while maintaining zero group delay.
     */
    private fun buildDecimationKernel(factor: Int): FloatArray {
        if (factor <= 1) return floatArrayOf(1f)
        val kernel = FloatArray(factor)
        val fc = 0.45 / factor.toDouble()
        val center = (factor - 1) / 2.0
        var weightSum = 0.0

        for (i in 0 until factor) {
            val offset = i - center
            val sinc = if (abs(offset) < 1e-6) {
                2.0 * fc
            } else {
                sin(2.0 * PI * fc * offset) / (PI * offset)
            }
            val phase = (i + 0.5) / factor.toDouble()
            val window = 0.54 - 0.46 * cos(2.0 * PI * phase)
            val tapWeight = sinc * window
            kernel[i] = tapWeight.toFloat()
            weightSum += tapWeight
        }

        if (weightSum > 0.0) {
            val invSum = (1.0 / weightSum).toFloat()
            for (i in kernel.indices) {
                kernel[i] *= invSum
            }
        } else {
            val uniform = 1f / factor.toFloat()
            java.util.Arrays.fill(kernel, uniform)
        }
        return kernel
    }

    private fun processableFrameCount(bytesPerFrame: Int, incomingBytes: Int): Int {
        val totalFrames = (pendingSize + incomingBytes) / bytesPerFrame
        return totalFrames / downsampleFactor
    }

    private fun maxPendingBytes(): Int {
        val sampleBytes = if (inputFormat.encoding == C.ENCODING_PCM_FLOAT) Float.SIZE_BYTES else Short.SIZE_BYTES
        return inputFormat.channelCount * sampleBytes * downsampleFactor
    }

    private fun ensurePendingCapacity(requiredCapacity: Int) {
        if (pendingBytes.size >= requiredCapacity) return
        pendingBytes = pendingBytes.copyOf(requiredCapacity.coerceAtLeast(pendingBytes.size * 2).coerceAtLeast(1))
    }

    private fun ensureAccumulatorCapacity(channelCount: Int) {
        if (floatAccumulators.size < channelCount) {
            floatAccumulators = FloatArray(channelCount)
        }
    }

    private fun ensureOutputBuffer(requiredCapacity: Int): ByteBuffer {
        return if (outputBuffer.capacity() < requiredCapacity) {
            ByteBuffer.allocateDirect(requiredCapacity).order(NATIVE_ORDER).also {
                outputBuffer = it
            }
        } else {
            outputBuffer.clear()
            outputBuffer
        }
    }

    private fun stashPendingBytes(inputBuffer: ByteBuffer) {
        ensurePendingCapacity(pendingSize + inputBuffer.remaining())
        val source = inputBuffer.duplicate()
        source.get(pendingBytes, pendingSize, source.remaining())
        pendingSize += inputBuffer.remaining()
    }

    private fun storePendingRemainder(pendingReadOffset: Int, inputBuffer: ByteBuffer) {
        val unreadPendingBytes = pendingSize - pendingReadOffset
        val remainingInputBytes = inputBuffer.remaining()
        ensurePendingCapacity(unreadPendingBytes + remainingInputBytes)
        if (unreadPendingBytes > 0 && pendingReadOffset > 0) {
            pendingBytes.copyInto(
                destination = pendingBytes,
                destinationOffset = 0,
                startIndex = pendingReadOffset,
                endIndex = pendingSize
            )
        }
        if (remainingInputBytes > 0) {
            inputBuffer.get(pendingBytes, unreadPendingBytes, remainingInputBytes)
        }
        pendingSize = unreadPendingBytes + remainingInputBytes
    }

    private fun readShortFromPending(offset: Int): Short {
        val byte0 = pendingBytes[offset].toInt() and 0xFF
        val byte1 = pendingBytes[offset + 1].toInt() and 0xFF
        val bits = if (NATIVE_ORDER_IS_BIG_ENDIAN) {
            (byte0 shl 8) or byte1
        } else {
            (byte1 shl 8) or byte0
        }
        return bits.toShort()
    }

    private fun readFloatFromPending(offset: Int): Float {
        val intBits = readIntFromPending(offset)
        return Float.fromBits(intBits)
    }

    private fun readIntFromPending(offset: Int): Int {
        val byte0 = pendingBytes[offset].toInt() and 0xFF
        val byte1 = pendingBytes[offset + 1].toInt() and 0xFF
        val byte2 = pendingBytes[offset + 2].toInt() and 0xFF
        val byte3 = pendingBytes[offset + 3].toInt() and 0xFF
        return if (NATIVE_ORDER_IS_BIG_ENDIAN) {
            (byte0 shl 24) or (byte1 shl 16) or (byte2 shl 8) or byte3
        } else {
            (byte3 shl 24) or (byte2 shl 16) or (byte1 shl 8) or byte0
        }
    }

    override fun getOutput(): ByteBuffer {
        val pendingOutput = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return pendingOutput
    }

    override fun isEnded(): Boolean = inputEnded && pendingSize == 0 && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun queueEndOfStream() {
        if (inputEnded) return
        if (isActive()) {
            flushTrailingPendingFrames()
        } else {
            pendingSize = 0
        }
        inputEnded = true
    }

    private fun flushTrailingPendingFrames() {
        val sampleBytes = if (inputFormat.encoding == C.ENCODING_PCM_FLOAT) Float.SIZE_BYTES else Short.SIZE_BYTES
        val bytesPerFrame = inputFormat.channelCount * sampleBytes
        val remainingFrames = if (bytesPerFrame > 0) pendingSize / bytesPerFrame else 0
        if (remainingFrames <= 0) {
            pendingSize = 0
            return
        }

        val existingRemaining = if (outputBuffer !== AudioProcessor.EMPTY_BUFFER && outputBuffer.hasRemaining()) {
            val copy = ByteArray(outputBuffer.remaining())
            outputBuffer.get(copy)
            copy
        } else {
            null
        }

        val existingSize = existingRemaining?.size ?: 0
        outputBuffer = ensureOutputBuffer(existingSize + bytesPerFrame)
        if (existingRemaining != null) {
            outputBuffer.put(existingRemaining)
        }

        java.util.Arrays.fill(floatAccumulators, 0f)
        var pendingReadOffset = 0
        var weightSum = 0f
        for (tap in 0 until remainingFrames) {
            val weight = decimationKernel.getOrElse(tap) { 1f }
            weightSum += weight
            if (inputFormat.encoding == C.ENCODING_PCM_FLOAT) {
                for (ch in 0 until inputFormat.channelCount) {
                    floatAccumulators[ch] += readFloatFromPending(pendingReadOffset) * weight
                    pendingReadOffset += Float.SIZE_BYTES
                }
            } else {
                for (ch in 0 until inputFormat.channelCount) {
                    floatAccumulators[ch] += readShortFromPending(pendingReadOffset).toFloat() * weight
                    pendingReadOffset += Short.SIZE_BYTES
                }
            }
        }

        val invWeightSum = if (weightSum > 0f) 1f / weightSum else 1f
        if (inputFormat.encoding == C.ENCODING_PCM_FLOAT) {
            for (ch in 0 until inputFormat.channelCount) {
                outputBuffer.putFloat((floatAccumulators[ch] * invWeightSum).coerceIn(-1f, 1f))
            }
        } else {
            for (ch in 0 until inputFormat.channelCount) {
                outputBuffer.putShort(
                    (floatAccumulators[ch] * invWeightSum)
                        .roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                )
            }
        }

        outputBuffer.flip()
        pendingSize = 0
    }

    @Deprecated("Media3 AudioProcessor now prefers flush(StreamMetadata); kept for interface compatibility")
    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        pendingSize = 0
        inputEnded = false
    }

    override fun reset() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        pendingSize = 0
        inputEnded = false
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
        downsampleFactor = 1
        decimationKernel = floatArrayOf(1f)
    }
}
