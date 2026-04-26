package com.veritas.feature.detect.video.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector.FaceDetectorOptions
import com.veritas.data.detection.ml.runtime.ModelAssetVerifier
import com.veritas.data.detection.ml.runtime.ModelRegistry
import com.veritas.domain.detection.FallbackLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FaceDetectorWrapper @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) {
    private val verifier = ModelAssetVerifier()
    @Volatile private var detectorHandle: FaceDetectorHandle? = null

    val fallbackLevel: FallbackLevel
        get() = detectorHandle?.fallbackLevel ?: FallbackLevel.NONE

    fun detect(bitmap: Bitmap): List<FaceBox> {
        val localHandle = detectorHandle ?: createDetector().also { detectorHandle = it }
        val result = localHandle.detector.detect(BitmapImageBuilder(bitmap).build())
        return result.detections()
            .map { detection ->
                val box = detection.boundingBox()
                FaceBox(
                    left = (box.left / bitmap.width).coerceIn(0f, 1f),
                    top = (box.top / bitmap.height).coerceIn(0f, 1f),
                    right = (box.right / bitmap.width).coerceIn(0f, 1f),
                    bottom = (box.bottom / bitmap.height).coerceIn(0f, 1f),
                    score = detection.categories().firstOrNull()?.score() ?: 0f,
                )
            }
    }

    private fun createDetector(): FaceDetectorHandle {
        verifier.loadVerified(appContext, ModelRegistry.videoBlazeFaceShortRange)
        return runCatching {
            FaceDetectorHandle(
                detector = FaceDetector.createFromOptions(appContext, optionsFor(Delegate.GPU)),
                fallbackLevel = FallbackLevel.GPU,
            )
        }.getOrElse { error ->
            Log.w(TAG, "Failed to create MediaPipe face detector with GPU; retrying CPU", error)
            FaceDetectorHandle(
                detector = FaceDetector.createFromOptions(appContext, optionsFor(Delegate.CPU)),
                fallbackLevel = FallbackLevel.CPU_XNNPACK,
            )
        }
    }

    private fun optionsFor(delegate: Delegate): FaceDetectorOptions =
        FaceDetectorOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(ModelRegistry.videoBlazeFaceShortRange.assetPath)
                    .setDelegate(delegate)
                    .build(),
            )
            .setRunningMode(RunningMode.IMAGE)
            .setMinDetectionConfidence(MIN_FACE_CONFIDENCE)
            .build()

    private companion object {
        private const val TAG = "FaceDetectorWrapper"
        private const val MIN_FACE_CONFIDENCE = 0.75f
    }
}

private data class FaceDetectorHandle(
    val detector: FaceDetector,
    val fallbackLevel: FallbackLevel,
)

data class FaceBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val score: Float,
) {
    val area: Float
        get() = ((right - left).coerceAtLeast(0f)) * ((bottom - top).coerceAtLeast(0f))

    fun toPixelRect(width: Int, height: Int): RectF =
        RectF(left * width, top * height, right * width, bottom * height)
}
