package com.veritas.feature.detect.image.model

import android.graphics.Bitmap
import com.veritas.data.detection.ml.inference.RunnerFactory
import com.veritas.data.detection.ml.preprocessing.ImagePreprocessor
import com.veritas.data.detection.ml.runtime.ModelRegistry
import com.veritas.data.detection.ml.runtime.TensorBuffers
import com.veritas.domain.detection.FallbackLevel
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

@Singleton
class DeepfakeDetectorV2Model @Inject constructor(
    private val runnerFactory: RunnerFactory,
) {
    private val preprocessor = ImagePreprocessor(ModelRegistry.IMAGE_INPUT_SIZE)
    @Volatile private var handle: DeepfakeModelHandle? = null

    suspend fun score(bitmap: Bitmap): ModelScore {
        val localHandle = handle ?: runnerFactory.create(ModelRegistry.imageInt8).let {
            DeepfakeModelHandle(
                runner = it.runner,
                fallbackLevel = it.fallbackLevel,
                modelVersion = it.modelVersion,
                output = TensorBuffers.floatBuffer(OUTPUT_FLOATS),
            ).also { created -> handle = created }
        }
        val input = preprocessor.preprocess(bitmap)
        localHandle.runner.run(input, localHandle.output)
        val logit = readOutput(localHandle.output)
        return ModelScore(
            score = sigmoid(logit).coerceIn(0f, 1f),
            fallbackLevel = localHandle.fallbackLevel,
            modelVersion = localHandle.modelVersion,
        )
    }

    private fun readOutput(output: ByteBuffer): Float {
        output.rewind()
        val first = output.float
        return if (output.remaining() >= FLOAT_BYTES) {
            val second = output.float
            first - second
        } else {
            first
        }
    }

    private fun sigmoid(value: Float): Float = (1.0 / (1.0 + exp(-value.toDouble()))).toFloat()

    companion object {
        private const val OUTPUT_FLOATS = 2
        private const val FLOAT_BYTES = 4
    }
}

data class ModelScore(
    val score: Float,
    val fallbackLevel: FallbackLevel,
    val modelVersion: String,
)

private data class DeepfakeModelHandle(
    val runner: com.veritas.data.detection.ml.inference.ModelRunner,
    val fallbackLevel: FallbackLevel,
    val modelVersion: String,
    val output: ByteBuffer,
)
