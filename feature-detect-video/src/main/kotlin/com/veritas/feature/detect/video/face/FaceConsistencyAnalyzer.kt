package com.veritas.feature.detect.video.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.veritas.feature.detect.image.model.DeepfakeDetectorV2Model
import com.veritas.feature.detect.video.decode.ExtractedVideoFrame
import javax.inject.Inject

class FaceConsistencyAnalyzer @Inject constructor(
    private val faceDetector: FaceDetectorWrapper,
    private val spatialModel: DeepfakeDetectorV2Model,
) {
    suspend fun analyze(frames: List<ExtractedVideoFrame>): FaceConsistencyScore {
        val scores = mutableListOf<Float>()
        var faceCount = 0
        for (frame in frames) {
            if (scores.size >= MAX_FACE_CROP_INFERENCES) break
            val boxes = faceDetector.detect(frame.bitmap)
                .sortedByDescending { it.area }
                .take(MAX_FACES_PER_FRAME)
            faceCount += boxes.size
            for (box in boxes) {
                if (scores.size >= MAX_FACE_CROP_INFERENCES) break
                val crop = cropFace(frame.bitmap, box) ?: continue
                scores += spatialModel.score(crop).score
                crop.recycle()
            }
        }
        val mean = scores.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        return FaceConsistencyScore(
            score = mean,
            detectedFaces = faceCount,
            analyzedCrops = scores.size,
            fallbackLevel = faceDetector.fallbackLevel,
        )
    }

    private fun cropFace(source: Bitmap, box: FaceBox): Bitmap? {
        val rect = box.toPixelRect(source.width, source.height)
        if (rect.width() < MIN_FACE_SIZE || rect.height() < MIN_FACE_SIZE) return null
        val left = rect.left.toInt().coerceIn(0, source.width - 1)
        val top = rect.top.toInt().coerceIn(0, source.height - 1)
        val right = rect.right.toInt().coerceIn(left + 1, source.width)
        val bottom = rect.bottom.toInt().coerceIn(top + 1, source.height)
        val crop = Bitmap.createBitmap(FACE_CROP_SIZE, FACE_CROP_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(crop).apply {
            drawColor(Color.BLACK)
            drawBitmap(
                source,
                Rect(left, top, right, bottom),
                RectF(0f, 0f, FACE_CROP_SIZE.toFloat(), FACE_CROP_SIZE.toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        }
        return crop
    }

    private companion object {
        private const val MAX_FACES_PER_FRAME = 3
        private const val MAX_FACE_CROP_INFERENCES = 1
        private const val MIN_FACE_SIZE = 64f
        private const val FACE_CROP_SIZE = 224
    }
}

data class FaceConsistencyScore(
    val score: Float?,
    val detectedFaces: Int,
    val analyzedCrops: Int,
    val fallbackLevel: com.veritas.domain.detection.FallbackLevel,
)
