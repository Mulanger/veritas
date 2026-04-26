package com.veritas.feature.detect.video.temporal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veritas.data.detection.ml.inference.RunnerFactory
import com.veritas.data.detection.ml.runtime.LiteRtRuntime
import com.veritas.domain.detection.FallbackLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoViNetA0StreamingInstrumentedTest {
    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun movinetSignedModelRunsAndReturnsTemporalScore() = runBlocking {
        val frames = List(2) { index ->
            Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888).apply {
                eraseColor(if (index == 0) Color.BLACK else Color.DKGRAY)
            }
        }
        val model = MoViNetA0Streaming(
            runnerFactory = RunnerFactory(
                appContext = appContext,
                liteRtRuntime = LiteRtRuntime(appContext),
            ),
        )

        val score = model.analyze(frames)

        assertTrue(score.driftScore in 0f..1f)
        assertEquals(2, score.logits.size)
        assertTrue(score.logits.all { it.size == 600 })
        frames.forEach { it.recycle() }
    }

    @Test
    fun physicalDeviceUsesGpuDelegateForMovinet() = runBlocking {
        assumeFalse("GPU delegate verification requires a physical device", isProbablyEmulator())
        val frame = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val model = MoViNetA0Streaming(
            runnerFactory = RunnerFactory(
                appContext = appContext,
                liteRtRuntime = LiteRtRuntime(appContext),
            ),
        )

        val score = model.analyze(listOf(frame))

        assertEquals(FallbackLevel.GPU, score.fallbackLevel)
        frame.recycle()
    }

    private fun isProbablyEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.MODEL.contains("sdk", ignoreCase = true) ||
            Build.MODEL.contains("emulator", ignoreCase = true) ||
            Build.MANUFACTURER.contains("genymotion", ignoreCase = true)
}
