package com.theveloper.pixelplay.data.service.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

class SurroundDownmixProcessorTest {

    @Test
    fun queueInput_downmixes51Pcm16BitToStereoNormalized() {
        val processor = SurroundDownmixProcessor()
        val outputFormat = processor.configure(AudioFormat(48_000, 6, C.ENCODING_PCM_16BIT))

        processor.queueInput(
            shortBufferOf(
                1_000, 2_000, 3_000, 4_000, 5_000, 6_000
            )
        )

        assertThat(processor.isActive()).isTrue()
        assertThat(outputFormat.channelCount).isEqualTo(2)

        val coeffs = SurroundDownmixProcessor.COEFFS_51
        val expectedLeft = (coeffs.front * 1_000f + coeffs.center * 3_000f + coeffs.surround * 5_000f + coeffs.lfe * 4_000f).roundToInt()
        val expectedRight = (coeffs.front * 2_000f + coeffs.center * 3_000f + coeffs.surround * 6_000f + coeffs.lfe * 4_000f).roundToInt()

        assertThat(readShorts(processor.getOutput()))
            .containsExactly(expectedLeft, expectedRight)
            .inOrder()
    }

    @Test
    fun queueInput_downmixes71FloatToStereoNormalized() {
        val processor = SurroundDownmixProcessor()
        val outputFormat = processor.configure(AudioFormat(48_000, 8, C.ENCODING_PCM_FLOAT))

        processor.queueInput(
            floatBufferOf(
                0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f
            )
        )

        assertThat(processor.isActive()).isTrue()
        assertThat(outputFormat.channelCount).isEqualTo(2)

        val coeffs = SurroundDownmixProcessor.COEFFS_71
        val expectedLeft = coeffs.front * 0.1f + coeffs.center * 0.3f + coeffs.surround * 0.5f + coeffs.back * 0.7f + coeffs.lfe * 0.4f
        val expectedRight = coeffs.front * 0.2f + coeffs.center * 0.3f + coeffs.surround * 0.6f + coeffs.back * 0.8f + coeffs.lfe * 0.4f

        val output = readFloats(processor.getOutput())
        assertThat(output).hasSize(2)
        assertThat(output[0]).isWithin(1e-6f).of(expectedLeft)
        assertThat(output[1]).isWithin(1e-6f).of(expectedRight)
    }

    @Test
    fun queueInput_leavesPartialFrameBytesUnconsumedWithoutMisaligningStream() {
        val processor = SurroundDownmixProcessor()
        processor.configure(AudioFormat(48_000, 6, C.ENCODING_PCM_16BIT))

        // 1 frame = 12 bytes. Send 16 bytes (1 full frame + 4 trailing bytes).
        val firstFrame = shortBufferOf(1_000, 2_000, 3_000, 4_000, 5_000, 6_000)
        val extraPartial = shortBufferOf(700, 800)
        val combined = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder())
        combined.put(firstFrame)
        combined.put(extraPartial)
        combined.flip()

        processor.queueInput(combined)

        // Only the 1 complete frame (12 bytes) is consumed; the 4 partial bytes remain intact.
        assertThat(combined.remaining()).isEqualTo(4)
        assertThat(readShorts(processor.getOutput())).hasSize(2)

        // Re-present remaining 4 bytes prepended to the rest of frame 2 (8 bytes -> total 12 bytes).
        val nextChunk = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder())
        nextChunk.put(combined)
        nextChunk.put(shortBufferOf(900, 1_000, 1_100, 1_200))
        nextChunk.flip()

        processor.queueInput(nextChunk)
        assertThat(nextChunk.remaining()).isEqualTo(0)

        val coeffs = SurroundDownmixProcessor.COEFFS_51
        val expectedLeft2 = (coeffs.front * 700f + coeffs.center * 900f + coeffs.surround * 1_100f + coeffs.lfe * 1_000f).roundToInt()
        val expectedRight2 = (coeffs.front * 800f + coeffs.center * 900f + coeffs.surround * 1_200f + coeffs.lfe * 1_000f).roundToInt()
        assertThat(readShorts(processor.getOutput()))
            .containsExactly(expectedLeft2, expectedRight2)
            .inOrder()
    }

    @Test
    fun queueInput_fullScale51And71PeaksDoNotClipOrSaturate() {
        val processor51 = SurroundDownmixProcessor()
        processor51.configure(AudioFormat(48_000, 6, C.ENCODING_PCM_FLOAT))
        processor51.queueInput(floatBufferOf(1f, -1f, 1f, 1f, 1f, -1f))
        val out51 = readFloats(processor51.getOutput())
        assertThat(abs(out51[0])).isAtMost(1.0f)
        assertThat(abs(out51[1])).isAtMost(1.0f)

        val processor71 = SurroundDownmixProcessor()
        processor71.configure(AudioFormat(48_000, 8, C.ENCODING_PCM_16BIT))
        val maxVal = Short.MAX_VALUE.toInt()
        processor71.queueInput(shortBufferOf(maxVal, maxVal, maxVal, maxVal, maxVal, maxVal, maxVal, maxVal))
        val out71 = readShorts(processor71.getOutput())
        assertThat(out71[0]).isAtMost(Short.MAX_VALUE.toInt())
        assertThat(out71[1]).isAtMost(Short.MAX_VALUE.toInt())
    }

    private fun shortBufferOf(vararg samples: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(samples.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        for (sample in samples) {
            buffer.putShort(sample.toShort())
        }
        buffer.flip()
        return buffer
    }

    private fun floatBufferOf(vararg samples: Float): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(samples.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        for (sample in samples) {
            buffer.putFloat(sample)
        }
        buffer.flip()
        return buffer
    }

    private fun readShorts(buffer: ByteBuffer): List<Int> {
        val duplicate = buffer.duplicate().order(ByteOrder.nativeOrder())
        val samples = mutableListOf<Int>()
        while (duplicate.remaining() >= Short.SIZE_BYTES) {
            samples += duplicate.short.toInt()
        }
        return samples
    }

    private fun readFloats(buffer: ByteBuffer): List<Float> {
        val duplicate = buffer.duplicate().order(ByteOrder.nativeOrder())
        val samples = mutableListOf<Float>()
        while (duplicate.remaining() >= Float.SIZE_BYTES) {
            samples += duplicate.float
        }
        return samples
    }
}
