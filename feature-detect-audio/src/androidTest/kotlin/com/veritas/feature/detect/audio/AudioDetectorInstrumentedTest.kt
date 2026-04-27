package com.veritas.feature.detect.audio

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
import com.veritas.feature.detect.audio.domain.AudioDetectionInput
import com.veritas.feature.detect.audio.domain.AudioDetector
import com.veritas.feature.detect.audio.model.DeepfakeAudioDetectorModel
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioDetectorInstrumentedTest {
    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun phase8AudioDetector_fixtureRunsSignedModelAndReturnsSubScores() = runBlocking {
        val file = copyFixtureToCache("sample.wav")
        val detector = AudioDetector(
            model = DeepfakeAudioDetectorModel(
                runnerFactory = RunnerFactory(
                    appContext = appContext,
                    liteRtRuntime = LiteRtRuntime(appContext),
                ),
            ),
        )

        val result = detector.detect(
            AudioDetectionInput(
                media = ScannedMedia(
                    id = "phase8-audio-fixture",
                    uri = file.toURI().toString(),
                    mediaType = MediaType.AUDIO,
                    mimeType = "audio/wav",
                    sizeBytes = file.length(),
                    durationMs = 1_000,
                    widthPx = null,
                    heightPx = null,
                    source = MediaSource.FilePicker,
                    ingestedAt = Clock.System.now(),
                ),
                file = file,
            ),
        )

        Log.i(TAG, "score=${result.syntheticScore}, interval=${result.confidenceInterval}, fallback=${result.fallbackUsed}, subScores=${result.subScores}")
        assertEquals("audio_deepfake_detector_hemgg_wi8", result.detectorId)
        assertTrue(result.syntheticScore in 0.02f..0.98f)
        assertTrue(result.confidenceInterval.high <= 0.95f)
        assertTrue(result.subScores.containsKey("wav2vec2_model"))
        assertTrue(result.subScores.containsKey("codec"))
    }

    private fun copyFixtureToCache(fileName: String): File {
        val dest = File(appContext.cacheDir, "phase8_audio_detector/$fileName")
        if (dest.exists()) return dest
        dest.parentFile?.mkdirs()
        testContext.assets.open("audio-samples/$fileName").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    private companion object {
        private const val TAG = "AudioDetectorInstrumentedTest"
    }
}
