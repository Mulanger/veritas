package com.veritas.feature.detect.image.fusion

import com.veritas.data.detection.ml.fusion.Calibrator
import com.veritas.data.detection.ml.fusion.HandTunedFusion
import com.veritas.feature.detect.image.forensics.ForensicSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationAndFusionTest {
    @Test
    fun fuse_clampsLowAndHighScores() {
        val low = HandTunedFusion.imageScore(vitScore = -1f, exifCompleteness = 1f, elaAnomalyScore = 0f)
        val high = HandTunedFusion.imageScore(vitScore = 2f, exifCompleteness = 0f, elaAnomalyScore = 1f)

        assertEquals(0.02f, low, EPSILON)
        assertEquals(0.98f, high, EPSILON)
    }

    @Test
    fun fuse_weightsModelSignalMoreThanForensics() {
        val signals = ForensicSignals(
            hasCameraMake = false,
            hasCameraModel = false,
            hasCaptureDateTime = false,
            hasGps = false,
            hasExposureMetadata = false,
            quantTableIsStandard = true,
            exifCompletenessScore = 0f,
            elaAnomalyScore = 1f,
        )

        val score = ImageFusion.fuse(vitScore = 0.6f, signals = signals)

        assertEquals(0.66f, score, EPSILON)
    }

    @Test
    fun interval_neverEmitsConfidenceAboveNinetyFivePercent() {
        val interval = Calibrator.scoreToInterval(score = 0.98f)

        assertEquals(0.95f, interval.high, EPSILON)
    }

    @Test
    fun uncertainInterval_straddlesDecisionThreshold() {
        val interval = Calibrator.scoreToInterval(score = 0.51f)

        assertTrue(interval.low < 0.5f)
        assertTrue(interval.high > 0.5f)
    }

    private companion object {
        private const val EPSILON = 0.001f
    }
}
