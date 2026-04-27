package com.veritas.feature.detect.video.temporal

import android.graphics.Bitmap
import com.veritas.data.detection.ml.inference.MultiInputModelRunner
import com.veritas.data.detection.ml.inference.RunnerFactory
import com.veritas.data.detection.ml.runtime.ModelRegistry
import com.veritas.domain.detection.FallbackLevel
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoViNetA0Streaming @Inject constructor(
    private val runnerFactory: RunnerFactory,
) {
    private val preprocessor = MoViNetPreprocessor()
    @Volatile private var handle: MoViNetHandle? = null

    suspend fun analyze(frames: List<Bitmap>): TemporalScore {
        if (frames.isEmpty()) {
            return TemporalScore(driftScore = 0f, logits = emptyList(), fallbackLevel = FallbackLevel.NONE)
        }
        val localHandle = handle ?: runnerFactory.createMulti(ModelRegistry.videoMovinetA0StreamInt8).let {
            MoViNetHandle(
                runner = it.runner,
                fallbackLevel = it.fallbackLevel,
                modelVersion = it.modelVersion,
                imageInputIndex = it.runner.inputSpecs.indexOfFirst { spec -> spec.name.contains("image") },
                logitsOutputIndex = it.runner.outputSpecs.indexOfFirst { spec -> spec.shape.contentEquals(intArrayOf(1, LOGIT_COUNT)) },
            ).also { created -> handle = created }
        }
        require(localHandle.imageInputIndex >= 0) { "MoViNet image input tensor not found" }
        require(localHandle.logitsOutputIndex >= 0) { "MoViNet logits output tensor not found" }

        val stateBuffers = localHandle.runner.inputSpecs.map { it.newBuffer() }.toMutableList()
        val logits = frames.map { frame ->
            val imageBuffer = localHandle.runner.inputSpecs[localHandle.imageInputIndex].newBuffer()
            preprocessor.preprocess(frame, imageBuffer)
            stateBuffers[localHandle.imageInputIndex] = imageBuffer
            val outputs = localHandle.runner.outputSpecs.indices.associateWith {
                localHandle.runner.outputSpecs[it].newBuffer()
            }.mapValues { it.value as Any }.toMutableMap()
            localHandle.runner.run(stateBuffers.toTypedArray(), outputs)
            val frameLogits = readLogits(outputs.getValue(localHandle.logitsOutputIndex) as ByteBuffer)
            copyStateOutputsToInputs(outputs, stateBuffers, localHandle.imageInputIndex, localHandle.logitsOutputIndex)
            frameLogits
        }
        return TemporalScore(
            driftScore = EmbeddingDriftAnalyzer.drift(logits),
            logits = logits,
            fallbackLevel = localHandle.fallbackLevel,
            modelVersion = localHandle.modelVersion,
        )
    }

    private fun copyStateOutputsToInputs(
        outputs: Map<Int, Any>,
        stateBuffers: MutableList<ByteBuffer>,
        imageInputIndex: Int,
        logitsOutputIndex: Int,
    ) {
        var outputCursor = 0
        for (inputIndex in stateBuffers.indices) {
            if (inputIndex == imageInputIndex) {
                continue
            }
            while (outputCursor == logitsOutputIndex) {
                outputCursor++
            }
            stateBuffers[inputIndex] = outputs.getValue(outputCursor) as ByteBuffer
            outputCursor++
        }
    }

    private fun readLogits(buffer: ByteBuffer): FloatArray {
        buffer.rewind()
        return FloatArray(LOGIT_COUNT) { if (buffer.remaining() >= FLOAT_BYTES) buffer.float else 0f }
    }

    private companion object {
        private const val LOGIT_COUNT = 600
        private const val FLOAT_BYTES = 4
    }
}

data class TemporalScore(
    val driftScore: Float,
    val logits: List<FloatArray>,
    val fallbackLevel: FallbackLevel,
    val modelVersion: String = ModelRegistry.VIDEO_TEMPORAL_DETECTOR_VERSION,
)

private data class MoViNetHandle(
    val runner: MultiInputModelRunner,
    val fallbackLevel: FallbackLevel,
    val modelVersion: String,
    val imageInputIndex: Int,
    val logitsOutputIndex: Int,
)
