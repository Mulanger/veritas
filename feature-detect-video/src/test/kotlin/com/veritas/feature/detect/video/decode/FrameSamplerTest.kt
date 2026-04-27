package com.veritas.feature.detect.video.decode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameSamplerTest {
    @Test
    fun sampleTimestamps_returnsEmptyForInvalidDuration() {
        assertEquals(emptyList<Long>(), FrameSampler.sampleTimestamps(durationMs = 0L))
        assertEquals(emptyList<Long>(), FrameSampler.sampleTimestamps(durationMs = -1L))
    }

    @Test
    fun sampleTimestamps_usesMiddleFrameForVeryShortClip() {
        assertEquals(listOf(50L), FrameSampler.sampleTimestamps(durationMs = 100L))
    }

    @Test
    fun sampleTimestamps_spreadsFramesInsideClipEdges() {
        val timestamps = FrameSampler.sampleTimestamps(durationMs = 2_000L, targetFrameCount = 16)

        assertEquals(16, timestamps.size)
        assertEquals(100L, timestamps.first())
        assertEquals(1_900L, timestamps.last())
        assertTrue(timestamps.zipWithNext().all { (first, second) -> second > first })
    }

    @Test
    fun sampleTimestamps_capsDenseSamplingForShortClips() {
        val timestamps = FrameSampler.sampleTimestamps(durationMs = 700L, targetFrameCount = 16)

        assertEquals(5, timestamps.size)
    }
}
