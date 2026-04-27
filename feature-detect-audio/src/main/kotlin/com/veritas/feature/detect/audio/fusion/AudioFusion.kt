package com.veritas.feature.detect.audio.fusion

object AudioFusion {
    fun fuse(
        wav2vec2Score: Float,
        codecPlausibility: Float,
    ): Float {
        val codecSignal = 1f - codecPlausibility.coerceIn(0f, 1f)
        return (
            MODEL_WEIGHT * wav2vec2Score.coerceIn(0f, 1f) +
                CODEC_WEIGHT * codecSignal
            ).coerceIn(MIN_SCORE, MAX_SCORE)
    }

    private const val MODEL_WEIGHT = 0.92f
    private const val CODEC_WEIGHT = 0.08f
    private const val MIN_SCORE = 0.02f
    private const val MAX_SCORE = 0.98f
}
