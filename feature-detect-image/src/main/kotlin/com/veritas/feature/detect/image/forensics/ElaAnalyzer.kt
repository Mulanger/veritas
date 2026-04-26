package com.veritas.feature.detect.image.forensics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs

class ElaAnalyzer {
    fun analyze(file: File): Float? {
        if (!file.extension.equals("jpg", ignoreCase = true) && !file.extension.equals("jpeg", ignoreCase = true)) {
            return null
        }
        val original = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val recompressedBytes = ByteArrayOutputStream().use { stream ->
            original.compress(Bitmap.CompressFormat.JPEG, JPEG_RECOMPRESS_QUALITY, stream)
            stream.toByteArray()
        }
        val recompressed = BitmapFactory.decodeByteArray(recompressedBytes, 0, recompressedBytes.size) ?: return null
        val width = minOf(original.width, recompressed.width)
        val height = minOf(original.height, recompressed.height)
        var highEnergy = 0
        val total = width * height

        for (y in 0 until height step PIXEL_SAMPLE_STEP) {
            for (x in 0 until width step PIXEL_SAMPLE_STEP) {
                val delta = colorDelta(original.getPixel(x, y), recompressed.getPixel(x, y))
                if (delta > HIGH_ENERGY_THRESHOLD) highEnergy++
            }
        }

        val sampledTotal = (width / PIXEL_SAMPLE_STEP).coerceAtLeast(1) * (height / PIXEL_SAMPLE_STEP).coerceAtLeast(1)
        if (recompressed !== original) recompressed.recycle()
        original.recycle()
        return (highEnergy / sampledTotal.toFloat()).coerceIn(0f, 1f)
    }

    private fun colorDelta(
        original: Int,
        recompressed: Int,
    ): Int {
        val red = abs(((original shr 16) and BYTE_MASK) - ((recompressed shr 16) and BYTE_MASK))
        val green = abs(((original shr 8) and BYTE_MASK) - ((recompressed shr 8) and BYTE_MASK))
        val blue = abs((original and BYTE_MASK) - (recompressed and BYTE_MASK))
        return (red + green + blue) / CHANNELS
    }

    companion object {
        private const val JPEG_RECOMPRESS_QUALITY = 90
        private const val HIGH_ENERGY_THRESHOLD = 22
        private const val PIXEL_SAMPLE_STEP = 2
        private const val BYTE_MASK = 0xFF
        private const val CHANNELS = 3
    }
}
