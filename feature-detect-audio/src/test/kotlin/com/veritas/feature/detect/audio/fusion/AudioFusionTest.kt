package com.veritas.feature.detect.audio.fusion

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFusionTest {
    @Test
    fun fuse_clampsLowAndHighScores() {
        assertEquals(0.02f, AudioFusion.fuse(wav2vec2Score = -1f, codecPlausibility = 1f), EPSILON)
        assertEquals(0.98f, AudioFusion.fuse(wav2vec2Score = 2f, codecPlausibility = 0f), EPSILON)
    }

    @Test
    fun fuse_usesCodecAsSmallTieBreaker() {
        val plausible = AudioFusion.fuse(wav2vec2Score = 0.50f, codecPlausibility = 1f)
        val suspicious = AudioFusion.fuse(wav2vec2Score = 0.50f, codecPlausibility = 0f)

        assertEquals(0.46f, plausible, EPSILON)
        assertEquals(0.54f, suspicious, EPSILON)
    }

    private companion object {
        private const val EPSILON = 0.001f
    }
}
