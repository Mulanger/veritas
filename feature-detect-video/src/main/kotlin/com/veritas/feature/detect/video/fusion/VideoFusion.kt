package com.veritas.feature.detect.video.fusion

object VideoFusion {
    fun fuse(
        spatialMean: Float,
        spatialMax: Float,
        temporalDrift: Float,
        faceConsistencyScore: Float?,
    ): Float {
        val faceSignal = faceConsistencyScore ?: spatialMean
        return (
            SPATIAL_MEAN_WEIGHT * spatialMean.coerceIn(0f, 1f) +
                SPATIAL_MAX_WEIGHT * spatialMax.coerceIn(0f, 1f) +
                TEMPORAL_WEIGHT * temporalDrift.coerceIn(0f, 1f) +
                FACE_WEIGHT * faceSignal.coerceIn(0f, 1f) -
                FALSE_POSITIVE_GUARD_BAND
            ).coerceIn(MIN_SCORE, MAX_SCORE)
    }

    const val SPATIAL_MEAN_WEIGHT = 0.50f
    const val SPATIAL_MAX_WEIGHT = 0.20f
    const val TEMPORAL_WEIGHT = 0.20f
    const val FACE_WEIGHT = 0.10f
    const val FALSE_POSITIVE_GUARD_BAND = 0.03f
    private const val MIN_SCORE = 0.02f
    private const val MAX_SCORE = 0.98f
}
