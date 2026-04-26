package com.veritas.data.detection.ml.fusion

object HandTunedFusion {
    fun imageScore(
        vitScore: Float,
        exifCompleteness: Float,
        elaAnomalyScore: Float?,
    ): Float {
        val exifSignal = 1f - exifCompleteness.coerceIn(0f, 1f)
        val ela = elaAnomalyScore?.coerceIn(0f, 1f) ?: 0.5f
        return (
            VIT_WEIGHT * vitScore.coerceIn(0f, 1f) +
                EXIF_WEIGHT * exifSignal +
                ELA_WEIGHT * ela
        ).coerceIn(MIN_SCORE, MAX_SCORE)
    }

    private const val VIT_WEIGHT = 0.85f
    private const val EXIF_WEIGHT = 0.10f
    private const val ELA_WEIGHT = 0.05f
    private const val MIN_SCORE = 0.02f
    private const val MAX_SCORE = 0.98f
}
