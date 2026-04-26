package com.veritas.feature.detect.audio.decode

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioDecoderInstrumentedTest {
    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun decodesCommonAudioFormatsToPcm() = runBlocking {
        val decoder = AudioDecoder()
        val converter = PcmConverter()

        for (fixture in FIXTURES) {
            val file = copyFixtureToCache(fixture)
            val decoded = decoder.decode(file)
            val mono16k = converter.toMonoFloat(decoded)

            assertTrue("$fixture sample rate", decoded.sampleRate > 0)
            assertTrue("$fixture channel count", decoded.channelCount > 0)
            assertTrue("$fixture pcm bytes", decoded.pcmBytes.isNotEmpty())
            assertTrue("$fixture duration", decoded.durationMs in MIN_DURATION_MS..MAX_DURATION_MS)
            assertTrue("$fixture mono16k samples", mono16k.size in MIN_16K_SAMPLES..MAX_16K_SAMPLES)
            assertTrue("$fixture amplitude range", mono16k.all { it in -1f..1f })
        }
    }

    @Test
    fun wavFixtureReportsRawAudio() = runBlocking {
        val decoded = AudioDecoder().decode(copyFixtureToCache("sample.wav"))

        assertEquals("audio/raw", decoded.mimeType)
        assertEquals(PcmEncoding.Pcm16Bit, decoded.pcmEncoding)
    }

    private fun copyFixtureToCache(fileName: String): File {
        val dest = File(appContext.cacheDir, "phase8_audio_fixtures/$fileName")
        if (dest.exists()) return dest
        dest.parentFile?.mkdirs()
        testContext.assets.open("audio-samples/$fileName").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    private companion object {
        private val FIXTURES = listOf(
            "sample.mp3",
            "sample.aac",
            "sample.m4a",
            "sample.wav",
            "sample.ogg",
            "sample.opus",
            "sample_8k.wav",
            "sample_48k_stereo.mp3",
            "sample_22k.m4a",
            "sample_stereo.opus",
        )
        private const val MIN_DURATION_MS = 900L
        private const val MAX_DURATION_MS = 1_500L
        private const val MIN_16K_SAMPLES = 14_000
        private const val MAX_16K_SAMPLES = 25_000
    }
}
