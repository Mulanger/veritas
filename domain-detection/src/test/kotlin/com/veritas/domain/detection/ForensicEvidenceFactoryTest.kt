package com.veritas.domain.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForensicEvidenceFactoryTest {
    @Test
    fun imageHeatmap_isDeterministicAndReducedResolution() {
        val reasons =
            listOf(
                Reason(
                    code = ReasonCode.IMG_DEEPFAKE_MODEL_HIGH,
                    weight = 0.7f,
                    severity = Severity.MAJOR,
                    evidence = ReasonEvidence.Region("face", BBox(0.25f, 0.20f, 0.50f, 0.55f)),
                ),
            )

        val first = ForensicEvidenceFactory.imageHeatmap(MediaType.IMAGE, 0.82f, reasons)
        val second = ForensicEvidenceFactory.imageHeatmap(MediaType.IMAGE, 0.82f, reasons)

        assertEquals(first, second)
        val frame = first.frames.single()
        assertEquals(64, frame.widthBins)
        assertEquals(64, frame.heightBins)
        assertEquals(64 * 64, frame.intensities.size)
        assertTrue(frame.intensities.all { it in 0f..1f })
        assertTrue(frame.labeledRegions.any { it.label == "FACE" })
    }

    @Test
    fun temporalConfidence_clampsToEightToSixteenBins() {
        val confidence =
            ForensicEvidenceFactory.temporalConfidence(
                durationMs = 60_000L,
                scores = listOf(0L to 0.2f, 30_000L to 0.8f),
                fallbackScore = 0.5f,
            )

        assertEquals(16, confidence.bins.size)
        assertEquals(0L, confidence.bins.first().startMs)
        assertEquals(60_000L, confidence.bins.last().endMs)
        assertTrue(confidence.bins.all { it.syntheticProbability in 0f..1f })
    }
}
