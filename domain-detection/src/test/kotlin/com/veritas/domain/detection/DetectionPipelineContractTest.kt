package com.veritas.domain.detection

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionPipelineContractTest {
    @Test
    fun scanContractEmitsTypedStagesWithoutAndroidRuntime() {
        val media =
            ScannedMedia(
                id = "scan-id",
                uri = "file:///scan-id_test.mp4",
                mediaType = MediaType.VIDEO,
                mimeType = "video/mp4",
                sizeBytes = 1024,
                durationMs = 23_000,
                widthPx = 1920,
                heightPx = 1080,
                source = MediaSource.FilePicker,
                ingestedAt = Clock.System.now(),
            )
        val pipeline =
            object : DetectionPipeline {
                override fun scan(media: ScannedMedia) =
                    flowOf(
                        ScanStage.Started(PipelineStage.forMediaType(media.mediaType)),
                        ScanStage.Cancelled,
                    )

                override fun cancel() = Unit
            }

        val stages = runBlocking { pipeline.scan(media).toList() }

        assertEquals(2, stages.size)
        assertTrue(stages.first() is ScanStage.Started)
        assertTrue(stages.last() is ScanStage.Cancelled)
    }

    @Test
    fun detectorContractSupportsTypedInputs() {
        val result =
            runBlocking {
                object : Detector<VideoInput, BasicDetectorResult> {
                    override suspend fun detect(input: VideoInput): BasicDetectorResult =
                        BasicDetectorResult(
                            detectorId = "video.stub",
                            syntheticScore = 0.2f,
                            confidence = 0.8f,
                            reasons = emptyList(),
                            elapsedMs = 120,
                        )
                }.detect(
                    VideoInput(
                        media =
                            ScannedMedia(
                                id = "video-id",
                                uri = "file:///video-id_input.mp4",
                                mediaType = MediaType.VIDEO,
                                mimeType = "video/mp4",
                                sizeBytes = 2048,
                                durationMs = 12_000,
                                widthPx = 1280,
                                heightPx = 720,
                                source = MediaSource.FilePicker,
                                ingestedAt = Clock.System.now(),
                            ),
                    ),
                )
            }

        assertEquals("video.stub", result.detectorId)
    }

    @Test
    fun mediaTypeStageOrderMatchesPhaseFivePlan() {
        assertEquals(
            listOf(
                "C2PA manifest check",
                "Watermark scan",
                "Temporal consistency",
                "Spatial artifact model",
                "Facial physiological check",
            ),
            PipelineStage
                .forMediaType(MediaType.VIDEO)
                .map(PipelineStage::label),
        )
    }
}
