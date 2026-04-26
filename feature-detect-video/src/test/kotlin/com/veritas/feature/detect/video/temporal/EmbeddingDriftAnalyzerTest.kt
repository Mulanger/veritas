package com.veritas.feature.detect.video.temporal

import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddingDriftAnalyzerTest {
    @Test
    fun drift_returnsZeroForInsufficientFrames() {
        assertEquals(0f, EmbeddingDriftAnalyzer.drift(emptyList()), EPSILON)
        assertEquals(0f, EmbeddingDriftAnalyzer.drift(listOf(floatArrayOf(1f, 0f))), EPSILON)
    }

    @Test
    fun drift_returnsZeroForIdenticalLogits() {
        val drift = EmbeddingDriftAnalyzer.drift(
            listOf(
                floatArrayOf(1f, 0f, 0f),
                floatArrayOf(1f, 0f, 0f),
                floatArrayOf(1f, 0f, 0f),
            ),
        )

        assertEquals(0f, drift, EPSILON)
    }

    @Test
    fun drift_averagesCosineDistanceBetweenConsecutiveFrames() {
        val drift = EmbeddingDriftAnalyzer.drift(
            listOf(
                floatArrayOf(1f, 0f),
                floatArrayOf(0f, 1f),
                floatArrayOf(-1f, 0f),
            ),
        )

        assertEquals(1f, drift, EPSILON)
    }

    private companion object {
        private const val EPSILON = 0.001f
    }
}
