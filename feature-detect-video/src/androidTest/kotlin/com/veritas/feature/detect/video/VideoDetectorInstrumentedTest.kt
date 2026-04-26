package com.veritas.feature.detect.video

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.data.detection.ml.inference.RunnerFactory
import com.veritas.data.detection.ml.runtime.LiteRtRuntime
import com.veritas.domain.detection.MediaSource
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.ScannedMedia
import com.veritas.feature.detect.image.model.DeepfakeDetectorV2Model
import com.veritas.feature.detect.video.decode.MediaCodecFrameExtractor
import com.veritas.feature.detect.video.domain.VideoDetectionInput
import com.veritas.feature.detect.video.domain.VideoDetector
import com.veritas.feature.detect.video.face.FaceConsistencyAnalyzer
import com.veritas.feature.detect.video.face.FaceDetectorWrapper
import com.veritas.feature.detect.video.temporal.MoViNetA0Streaming
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoDetectorInstrumentedTest {
    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun phase9VideoDetector_fixtureRunsAndReturnsRequiredSubScores() = runBlocking {
        val file = copyFixtureToCache("sample_h264.mp4")
        val runnerFactory = RunnerFactory(
            appContext = appContext,
            liteRtRuntime = LiteRtRuntime(appContext),
        )
        val spatialModel = DeepfakeDetectorV2Model(runnerFactory)
        val detector = VideoDetector(
            frameExtractor = MediaCodecFrameExtractor(),
            spatialModel = spatialModel,
            temporalModel = MoViNetA0Streaming(runnerFactory),
            faceAnalyzer = FaceConsistencyAnalyzer(
                faceDetector = FaceDetectorWrapper(appContext),
                spatialModel = spatialModel,
            ),
        )

        val result = detector.detect(
            VideoDetectionInput(
                media = ScannedMedia(
                    id = "phase9-video-fixture",
                    uri = file.toURI().toString(),
                    mediaType = MediaType.VIDEO,
                    mimeType = "video/mp4",
                    sizeBytes = file.length(),
                    durationMs = 3_000L,
                    widthPx = 640,
                    heightPx = 360,
                    source = MediaSource.FilePicker,
                    ingestedAt = Clock.System.now(),
                ),
                file = file,
            ),
        )

        Log.i(TAG, "score=${result.syntheticScore}, interval=${result.confidenceInterval}, fallback=${result.fallbackUsed}, subScores=${result.subScores}")
        Log.i(TAG, "stageTiming=${VideoDetector.lastTiming}")
        assertEquals("video_detector_phase9", result.detectorId)
        assertTrue(result.syntheticScore in 0.02f..0.98f)
        assertTrue(result.confidenceInterval.high <= 0.95f)
        assertTrue(result.subScores.containsKey("spatial_vit"))
        assertTrue(result.subScores.containsKey("temporal_movinet"))
        assertTrue(result.subScores.containsKey("face_consistency"))
    }

    private fun copyFixtureToCache(fileName: String): File {
        val dest = File(appContext.cacheDir, "phase9_video_detector/$fileName")
        if (dest.exists()) return dest
        dest.parentFile?.mkdirs()
        testContext.assets.open("video_fixtures/$fileName").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    private companion object {
        private const val TAG = "VideoDetectorInstrumentedTest"
    }
}
