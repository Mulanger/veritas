package com.veritas.feature.detect.video.temporal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.veritas.data.detection.ml.runtime.ModelRegistry
import java.nio.ByteBuffer

class MoViNetPreprocessor {
    fun preprocess(bitmap: Bitmap, buffer: ByteBuffer) {
        val modelInput = centerCropAndResize(bitmap)
        buffer.rewind()
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = modelInput.getPixel(x, y)
                buffer.putFloat(Color.red(pixel) / CHANNEL_SCALE)
                buffer.putFloat(Color.green(pixel) / CHANNEL_SCALE)
                buffer.putFloat(Color.blue(pixel) / CHANNEL_SCALE)
            }
        }
        buffer.rewind()
        modelInput.recycle()
    }

    private fun centerCropAndResize(bitmap: Bitmap): Bitmap {
        val sourceSize = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - sourceSize) / 2
        val top = (bitmap.height - sourceSize) / 2
        val output = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(
            bitmap,
            Rect(left, top, left + sourceSize, top + sourceSize),
            RectF(0f, 0f, INPUT_SIZE.toFloat(), INPUT_SIZE.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return output
    }

    private companion object {
        private const val INPUT_SIZE = ModelRegistry.VIDEO_MOVINET_INPUT_SIZE
        private const val CHANNEL_SCALE = 255f
    }
}
