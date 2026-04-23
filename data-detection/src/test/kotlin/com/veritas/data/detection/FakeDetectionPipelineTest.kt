package com.veritas.data.detection

import com.veritas.domain.detection.MediaSource
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.ScanStage
import com.veritas.domain.detection.ScannedMedia
import com.veritas.domain.detection.VerdictOutcome
import java.io.File
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeDetectionPipelineTest {
    @Test
    fun authenticFilenameRoutesToLooksAuthentic() =
        runBlocking {
            val pipeline = FakeDetectionPipeline(stageDelayRangeMs = 1..1, delayStepMs = 1)
            val media = createMedia("phase5_authentic_sample.mp4")

            val verdict = pipeline.scan(media).filterIsInstance<ScanStage.VerdictReady>().first().verdict

            assertEquals(VerdictOutcome.LIKELY_AUTHENTIC, verdict.outcome)
            assertTrue(verdict.confidence.lowPct >= 65)
            assertTrue(verdict.confidence.highPct <= 94)
        }

    @Test
    fun authenticC2paFilenameRoutesToVerifiedAuthentic() =
        runBlocking {
            val pipeline = FakeDetectionPipeline(stageDelayRangeMs = 1..1, delayStepMs = 1)
            val media = createMedia("phase5_authentic_c2pa_sample.mp4")

            val verdict = pipeline.scan(media).filterIsInstance<ScanStage.VerdictReady>().first().verdict

            assertEquals(VerdictOutcome.VERIFIED_AUTHENTIC, verdict.outcome)
            assertTrue(verdict.summary.contains("Content Credentials"))
        }

    @Test
    fun uncertainFilenameRoutesToThresholdStraddlingRange() =
        runBlocking {
            val pipeline = FakeDetectionPipeline(stageDelayRangeMs = 1..1, delayStepMs = 1)
            val media = createMedia("phase5_uncertain_sample.mp4")

            val verdict = pipeline.scan(media).filterIsInstance<ScanStage.VerdictReady>().first().verdict

            assertEquals(VerdictOutcome.UNCERTAIN, verdict.outcome)
            assertTrue(verdict.confidence.lowPct < 50)
            assertTrue(verdict.confidence.highPct > 50)
        }

    @Test
    fun syntheticFilenameRoutesToSyntheticVerdict() =
        runBlocking {
            val pipeline = FakeDetectionPipeline(stageDelayRangeMs = 1..1, delayStepMs = 1)
            val media = createMedia("phase5_synthetic_sample.mp4")

            val verdict = pipeline.scan(media).filterIsInstance<ScanStage.VerdictReady>().first().verdict

            assertEquals(VerdictOutcome.LIKELY_SYNTHETIC, verdict.outcome)
            assertTrue(verdict.confidence.lowPct >= 70)
            assertTrue(verdict.confidence.highPct <= 96)
        }

    @Test
    fun cancelEmitsCancelledTerminalStage() =
        runBlocking {
            val pipeline = FakeDetectionPipeline(stageDelayRangeMs = 100..100, delayStepMs = 10)
            val media = createMedia("phase5_cancel_sample.mp4")
            val events = mutableListOf<ScanStage>()
            val stageStarted = CompletableDeferred<Unit>()

            val job =
                launch {
                    pipeline.scan(media).collect { stage ->
                        events += stage
                        if (stage is ScanStage.StageActive && !stageStarted.isCompleted) {
                            stageStarted.complete(Unit)
                        }
                    }
                }

            stageStarted.await()
            pipeline.cancel()
            job.join()

            assertTrue(events.last() is ScanStage.Cancelled)
        }

    @Test
    fun scanStartsWithTypedStageList() =
        runBlocking {
            val pipeline = FakeDetectionPipeline(stageDelayRangeMs = 1..1, delayStepMs = 1)
            val media = createMedia("phase5_synthetic_video.mp4")

            val events = pipeline.scan(media).toList()
            val started = events.first() as ScanStage.Started

            assertEquals(5, started.stages.size)
            assertEquals("Temporal consistency", started.stages[2].label)
        }

    private fun createMedia(fileName: String): ScannedMedia {
        val directory = createTempDirectory(prefix = "fake-pipeline").toFile()
        val file =
            File(directory, fileName).apply {
                writeBytes(UUID.randomUUID().toString().toByteArray())
            }

        return ScannedMedia(
            id = UUID.randomUUID().toString(),
            uri = file.toURI().toString(),
            mediaType = MediaType.VIDEO,
            mimeType = "video/mp4",
            sizeBytes = file.length(),
            durationMs = 23_000,
            widthPx = 1920,
            heightPx = 1080,
            source = MediaSource.FilePicker,
            ingestedAt = Clock.System.now(),
        )
    }
}
