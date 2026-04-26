package com.veritas.feature.detect.image.fusion

import com.veritas.data.detection.ml.fusion.HandTunedFusion
import com.veritas.feature.detect.image.forensics.ForensicSignals

object ImageFusion {
    fun fuse(
        vitScore: Float,
        signals: ForensicSignals,
    ): Float =
        HandTunedFusion.imageScore(
            vitScore = vitScore,
            exifCompleteness = signals.exifCompletenessScore,
            elaAnomalyScore = signals.elaAnomalyScore,
        )
}
