@file:Suppress("MagicNumber")

package com.veritas.feature.detect.audio.model

import com.veritas.data.detection.ml.inference.ModelRunner
import com.veritas.data.detection.ml.inference.RunnerFactory
import com.veritas.data.detection.ml.runtime.ModelRegistry
import com.veritas.data.detection.ml.runtime.TensorBuffers
import com.veritas.domain.detection.FallbackLevel
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

@Singleton
class DeepfakeAudioDetectorModel @Inject constructor(
    private val runnerFactory: RunnerFactory,
) {
    private val preprocessor = Wav2Vec2Preprocessor()
    @Volatile private var handle: AudioModelHandle? = null

    suspend fun score(samples: FloatArray): AudioModelScore {
        val localHandle = handle ?: runnerFactory.createCpu(ModelRegistry.audioHemggWi8).let {
            AudioModelHandle(
                runner = it.runner,
                fallbackLevel = it.fallbackLevel,
                modelVersion = it.modelVersion,
                output = TensorBuffers.floatBuffer(OUTPUT_FLOATS),
            ).also { created -> handle = created }
        }
        val input = preprocessor.preprocess(samples)
        localHandle.runner.run(input, localHandle.output)
        val logits = readOutput(localHandle.output)
        val probabilities = softmax(logits)
        return AudioModelScore(
            syntheticScore = probabilities[AIVOICE_INDEX].coerceIn(0f, 1f),
            humanScore = probabilities[HUMANVOICE_INDEX].coerceIn(0f, 1f),
            fallbackLevel = localHandle.fallbackLevel,
            modelVersion = localHandle.modelVersion,
        )
    }

    private fun readOutput(output: ByteBuffer): FloatArray {
        output.rewind()
        return FloatArray(OUTPUT_FLOATS) {
            if (output.remaining() >= FLOAT_BYTES) output.float else 0f
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exps = FloatArray(logits.size) { exp((logits[it] - max).toDouble()).toFloat() }
        val sum = exps.sum().takeIf { it > 0f } ?: 1f
        return FloatArray(exps.size) { exps[it] / sum }
    }

    companion object {
        private const val OUTPUT_FLOATS = 2
        private const val FLOAT_BYTES = 4
        private const val AIVOICE_INDEX = 0
        private const val HUMANVOICE_INDEX = 1
    }
}

data class AudioModelScore(
    val syntheticScore: Float,
    val humanScore: Float,
    val fallbackLevel: FallbackLevel,
    val modelVersion: String,
)

private data class AudioModelHandle(
    val runner: ModelRunner,
    val fallbackLevel: FallbackLevel,
    val modelVersion: String,
    val output: ByteBuffer,
)
