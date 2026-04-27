package com.veritas.data.detection.ml.preprocessing

import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioPreprocessor(
    private val targetSampleCount: Int,
) {
    fun normalizeLength(samples: FloatArray): FloatArray {
        val output = FloatArray(targetSampleCount)
        val copyCount = minOf(samples.size, targetSampleCount)
        System.arraycopy(samples, 0, output, 0, copyCount)
        return output
    }

    fun preprocess(samples: FloatArray): ByteBuffer {
        val normalized = normalizeLength(samples)
        val output = ByteBuffer
            .allocateDirect(normalized.size * FLOAT_BYTES)
            .order(ByteOrder.nativeOrder())
        for (sample in normalized) {
            output.putFloat(sample.coerceIn(MIN_AMPLITUDE, MAX_AMPLITUDE))
        }
        output.rewind()
        return output
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 16_000
        const val DEFAULT_SECONDS = 5
        const val DEFAULT_SAMPLE_COUNT = DEFAULT_SAMPLE_RATE * DEFAULT_SECONDS

        private const val FLOAT_BYTES = 4
        private const val MIN_AMPLITUDE = -1f
        private const val MAX_AMPLITUDE = 1f
    }
}
