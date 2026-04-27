package com.veritas.feature.detect.audio.model

import com.veritas.data.detection.ml.preprocessing.AudioPreprocessor
import com.veritas.data.detection.ml.runtime.ModelRegistry
import java.nio.ByteBuffer
import javax.inject.Inject

class Wav2Vec2Preprocessor @Inject constructor() {
    private val preprocessor = AudioPreprocessor(ModelRegistry.AUDIO_INPUT_SAMPLE_COUNT)

    fun preprocess(samples: FloatArray): ByteBuffer = preprocessor.preprocess(samples)
}
