package com.veritas.data.detection.ml.preprocessing

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
class ImagePreprocessorTest {
    @Test
    fun preprocessImageNetNhwc_matchesReferenceValues() {
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(128, 64, 32))

        val buffer = ImagePreprocessor(INPUT_SIZE).preprocess(bitmap)

        val red = buffer.float
        val expectedRed = ((128f / 255f) - 0.485f) / 0.229f
        val expectedGreen = ((64f / 255f) - 0.456f) / 0.224f
        val expectedBlue = ((32f / 255f) - 0.406f) / 0.225f
        val green = buffer.float
        val blue = buffer.float

        assertEquals(expectedRed, red, EPSILON)
        assertEquals(expectedGreen, green, EPSILON)
        assertEquals(expectedBlue, blue, EPSILON)
    }

    @Test
    fun preprocessImageNetNhwc_hasLowMeanAbsoluteErrorAgainstReference() {
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(10, 120, 240))

        val buffer = ImagePreprocessor(INPUT_SIZE).preprocess(bitmap)
        val expected = floatArrayOf(
            ((10f / 255f) - 0.485f) / 0.229f,
            ((120f / 255f) - 0.456f) / 0.224f,
            ((240f / 255f) - 0.406f) / 0.225f,
        )
        var absoluteError = 0f
        repeat(3) { channel ->
            absoluteError += abs(buffer.float - expected[channel])
        }

        assertEquals(0f, absoluteError / 3f, EPSILON)
    }

    private companion object {
        private const val FLOAT_BYTES = 4
        private const val INPUT_SIZE = 224
        private const val EPSILON = 0.001f
    }
}
