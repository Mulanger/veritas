package com.veritas.data.detection.ml.preprocessing

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImagePreprocessor(
    private val inputSize: Int,
) {
    fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val output = ByteBuffer
            .allocateDirect(BATCH_SIZE * CHANNELS * inputSize * inputSize * FLOAT_BYTES)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            for (channel in 0 until CHANNELS) {
                val raw = when (channel) {
                    CHANNEL_RED -> (pixel shr 16) and BYTE_MASK
                    CHANNEL_GREEN -> (pixel shr 8) and BYTE_MASK
                    else -> pixel and BYTE_MASK
                }
                val normalized = ((raw / PIXEL_MAX) - IMAGENET_MEAN[channel]) / IMAGENET_STD[channel]
                output.putFloat(normalized)
            }
        }
        output.rewind()
        if (resized !== bitmap) {
            resized.recycle()
        }
        return output
    }

    companion object {
        private const val BATCH_SIZE = 1
        private const val CHANNELS = 3
        private const val CHANNEL_RED = 0
        private const val CHANNEL_GREEN = 1
        private const val BYTE_MASK = 0xFF
        private const val FLOAT_BYTES = 4
        private const val PIXEL_MAX = 255f
        private val IMAGENET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val IMAGENET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }
}
