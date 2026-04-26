package com.veritas.app

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.data.detection.ml.inference.RunnerFactory
import com.veritas.data.detection.ml.runtime.LiteRtRuntime
import com.veritas.domain.detection.MediaSource
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.ScannedMedia
import com.veritas.feature.detect.image.domain.ImageDetectionInput
import com.veritas.feature.detect.image.domain.ImageDetector
import com.veritas.feature.detect.image.model.DeepfakeDetectorV2Model
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase7ImageDetectorInstrumentedTest {
    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun phase7ImageDetector_realFixtureRunsSignedModelAndReturnsSubScores() = runBlocking {
        val file = copyFixtureToCache("nikon-20221019-building.jpeg")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        val detector = ImageDetector(
            model = DeepfakeDetectorV2Model(
                runnerFactory = RunnerFactory(
                    appContext = appContext,
                    liteRtRuntime = LiteRtRuntime(appContext),
                ),
            ),
        )
        val result = detector.detect(
            ImageDetectionInput(
                media = ScannedMedia(
                    id = "phase7-nikon-fixture",
                    uri = file.toURI().toString(),
                    mediaType = MediaType.IMAGE,
                    mimeType = "image/jpeg",
                    sizeBytes = file.length(),
                    durationMs = null,
                    widthPx = bounds.outWidth,
                    heightPx = bounds.outHeight,
                    source = MediaSource.FilePicker,
                    ingestedAt = Clock.System.now(),
                ),
                file = file,
            ),
        )

        Log.i(
            TAG,
            "score=${result.syntheticScore}, interval=${result.confidenceInterval}, " +
                "fallback=${result.fallbackUsed}, subScores=${result.subScores}, reasons=${result.reasons.map { it.code }}",
        )
        assertEquals("image_deepfake_detector_v2", result.detectorId)
        assertTrue(result.syntheticScore in 0.02f..0.98f)
        assertTrue(result.confidenceInterval.high <= 0.95f)
        assertTrue(result.subScores.containsKey("vit_model"))
        assertTrue(result.subScores.containsKey("exif_ela"))
        assertNotNull(result.reasons)
    }

    private fun copyFixtureToCache(fileName: String): File {
        val dest = File(appContext.cacheDir, "phase7_fixtures/$fileName")
        if (dest.exists()) return dest
        dest.parentFile?.mkdirs()
        testContext.assets.open("test_fixtures/$fileName").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    private companion object {
        private const val TAG = "Phase7ImageDetectorTest"
    }
}
