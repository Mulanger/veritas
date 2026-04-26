package com.veritas.feature.detect.video.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veritas.domain.detection.FallbackLevel
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaceDetectorWrapperInstrumentedTest {
    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun noFaceFrameReturnsEmptyList() {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)

        val boxes = FaceDetectorWrapper(appContext).detect(bitmap)

        assertTrue(boxes.isEmpty())
        bitmap.recycle()
    }

    @Test
    fun physicalDeviceUsesGpuDelegateForFaceDetector() {
        assumeFalse("GPU delegate verification requires a physical device", isProbablyEmulator())
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        val detector = FaceDetectorWrapper(appContext)

        detector.detect(bitmap)

        assertEquals(FallbackLevel.GPU, detector.fallbackLevel)
        bitmap.recycle()
    }

    private fun isProbablyEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.MODEL.contains("sdk", ignoreCase = true) ||
            Build.MODEL.contains("emulator", ignoreCase = true) ||
            Build.MANUFACTURER.contains("genymotion", ignoreCase = true)
}
