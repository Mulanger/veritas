package com.veritas.feature.detect.audio.model

import com.veritas.data.detection.ml.runtime.ModelRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class Wav2Vec2PreprocessorTest {
    @Test
    fun preprocess_packsExpectedSampleCount() {
        val output = Wav2Vec2Preprocessor().preprocess(floatArrayOf(0.25f, -0.5f))

        assertEquals(ModelRegistry.AUDIO_INPUT_SAMPLE_COUNT * FLOAT_BYTES, output.capacity())
        assertEquals(0.25f, output.float, EPSILON)
        assertEquals(-0.5f, output.float, EPSILON)
        assertEquals(0f, output.float, EPSILON)
    }

    private companion object {
        private const val FLOAT_BYTES = 4
        private const val EPSILON = 0.0001f
    }
}
