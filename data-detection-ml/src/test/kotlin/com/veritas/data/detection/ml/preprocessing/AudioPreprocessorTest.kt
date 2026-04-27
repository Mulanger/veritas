package com.veritas.data.detection.ml.preprocessing

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPreprocessorTest {
    @Test
    fun normalizeLengthPadsShortInput() {
        val preprocessor = AudioPreprocessor(targetSampleCount = 5)

        val output = preprocessor.normalizeLength(floatArrayOf(0.25f, -0.5f))

        assertEquals(5, output.size)
        assertEquals(0.25f, output[0], EPSILON)
        assertEquals(-0.5f, output[1], EPSILON)
        assertEquals(0f, output[2], EPSILON)
        assertEquals(0f, output[4], EPSILON)
    }

    @Test
    fun normalizeLengthTruncatesLongInput() {
        val preprocessor = AudioPreprocessor(targetSampleCount = 3)

        val output = preprocessor.normalizeLength(floatArrayOf(0f, 0.1f, 0.2f, 0.3f))

        assertEquals(3, output.size)
        assertEquals(0.2f, output[2], EPSILON)
    }

    @Test
    fun preprocessClampsAndPacksFloats() {
        val preprocessor = AudioPreprocessor(targetSampleCount = 3)

        val output = preprocessor.preprocess(floatArrayOf(-2f, 0.5f, 2f))

        assertEquals(-1f, output.float, EPSILON)
        assertEquals(0.5f, output.float, EPSILON)
        assertEquals(1f, output.float, EPSILON)
    }

    private companion object {
        private const val EPSILON = 0.0001f
    }
}
