package com.veritas.feature.detect.video.fusion

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFusionTest {
    @Test
    fun fuse_clampsLowAndHighScores() {
        assertEquals(0.02f, VideoFusion.fuse(spatialMean = -1f, spatialMax = -1f, temporalDrift = -1f, faceConsistencyScore = -1f), EPSILON)
        assertEquals(0.97f, VideoFusion.fuse(spatialMean = 2f, spatialMax = 2f, temporalDrift = 2f, faceConsistencyScore = 2f), EPSILON)
    }

    @Test
    fun fuse_weightsSpatialMeanAsPrimarySignal() {
        val score = VideoFusion.fuse(
            spatialMean = 0.60f,
            spatialMax = 0.80f,
            temporalDrift = 0.25f,
            faceConsistencyScore = 0.40f,
        )

        assertEquals(0.52f, score, EPSILON)
    }

    @Test
    fun fuse_usesSpatialMeanWhenFaceSignalIsUnavailable() {
        val score = VideoFusion.fuse(
            spatialMean = 0.50f,
            spatialMax = 0.50f,
            temporalDrift = 0.50f,
            faceConsistencyScore = null,
        )

        assertEquals(0.47f, score, EPSILON)
    }

    private companion object {
        private const val EPSILON = 0.001f
    }
}
