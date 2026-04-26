package com.veritas.data.detection.ml.fusion

import com.veritas.domain.detection.ConfidenceInterval

object Calibrator {
    fun scoreToInterval(score: Float): ConfidenceInterval {
        val fused = score.coerceIn(MIN_SCORE, MAX_SCORE)
        val width = when {
            fused < 0.25f -> 0.18f
            fused < 0.40f -> 0.28f
            fused < 0.60f -> 0.30f
            fused < 0.75f -> 0.28f
            else -> 0.18f
        }
        return ConfidenceInterval(
            low = (fused - width / 2f).coerceIn(MIN_SCORE, MAX_CONFIDENCE),
            high = (fused + width / 2f).coerceIn(MIN_SCORE, MAX_CONFIDENCE),
        )
    }

    private const val MIN_SCORE = 0.02f
    private const val MAX_SCORE = 0.98f
    private const val MAX_CONFIDENCE = 0.95f
}
