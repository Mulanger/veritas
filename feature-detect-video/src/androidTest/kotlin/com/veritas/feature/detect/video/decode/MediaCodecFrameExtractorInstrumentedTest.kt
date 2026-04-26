package com.veritas.feature.detect.video.decode

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaCodecFrameExtractorInstrumentedTest {
    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun extractsFramesFromCommonVideoFixtures() {
        val extractor = MediaCodecFrameExtractor()

        for (fixture in FIXTURES) {
            val extracted = extractor.extract(copyFixtureToCache(fixture), targetFrameCount = 8)

            assertTrue("$fixture duration", extracted.metadata.durationMs >= 2_500L)
            assertTrue("$fixture width", extracted.metadata.width > 0)
            assertTrue("$fixture height", extracted.metadata.height > 0)
            assertTrue("$fixture frames", extracted.frames.size >= 4)
            assertTrue("$fixture bitmap width", extracted.frames.all { it.bitmap.width > 0 })
            assertTrue("$fixture bitmap height", extracted.frames.all { it.bitmap.height > 0 })
            extracted.frames.forEach { it.bitmap.recycle() }
        }
    }

    private fun copyFixtureToCache(fileName: String): File {
        val dest = File(appContext.cacheDir, "phase9_video_fixtures/$fileName")
        if (dest.exists()) return dest
        dest.parentFile?.mkdirs()
        testContext.assets.open("$FIXTURE_ROOT/$fileName").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    private companion object {
        private const val FIXTURE_ROOT = "video_fixtures"
        private val FIXTURES = listOf(
            "sample_h264.mp4",
            "sample_vp9.webm",
            "sample_mov.mov",
            "sample_low_fps.mp4",
        )
    }
}
