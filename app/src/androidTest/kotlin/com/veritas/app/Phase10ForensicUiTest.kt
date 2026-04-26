package com.veritas.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.veritas.domain.detection.BBox
import com.veritas.domain.detection.ConfidenceRange
import com.veritas.domain.detection.ForensicEvidence
import com.veritas.domain.detection.ForensicEvidenceFactory
import com.veritas.domain.detection.InferenceHardware
import com.veritas.domain.detection.MediaSource
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.Reason
import com.veritas.domain.detection.ReasonCode
import com.veritas.domain.detection.ReasonEvidence
import com.veritas.domain.detection.ScannedMedia
import com.veritas.domain.detection.Severity
import com.veritas.domain.detection.Verdict
import com.veritas.domain.detection.VerdictOutcome
import kotlinx.datetime.Clock
import org.junit.Rule
import org.junit.Test

class Phase10ForensicUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun forensicViewScrubsTimelineAndOpensReasonSheet() {
        val media = media()
        val reasons =
            listOf(
                Reason(
                    code = ReasonCode.VID_SPATIAL_SYNTHETIC_FRAMES,
                    weight = 0.5f,
                    severity = Severity.MAJOR,
                    evidence = ReasonEvidence.Region("face", BBox(0.28f, 0.18f, 0.44f, 0.55f)),
                ),
                Reason(
                    code = ReasonCode.VID_TEMPORAL_DRIFT_HIGH,
                    weight = 0.2f,
                    severity = Severity.MINOR,
                    evidence = ReasonEvidence.Temporal(listOf(1_000L, 4_000L)),
                ),
            )
        val forensicEvidence =
            ForensicEvidence.Video(
                heatmap =
                    ForensicEvidenceFactory.videoHeatmap(
                        syntheticScore = 0.82f,
                        frameScores = listOf(0L to 0.72f, 4_000L to 0.86f, 8_000L to 0.42f),
                        reasons = reasons,
                    ),
                temporalConfidence =
                    ForensicEvidenceFactory.temporalConfidence(
                        durationMs = media.durationMs,
                        scores = listOf(0L to 0.72f, 4_000L to 0.86f, 8_000L to 0.42f),
                        fallbackScore = 0.82f,
                    ),
            )
        val verdict =
            Verdict(
                id = "phase10-verdict",
                mediaId = media.id,
                mediaType = MediaType.VIDEO,
                outcome = VerdictOutcome.LIKELY_SYNTHETIC,
                confidence = ConfidenceRange(82, 96),
                summary = "Synthetic frame patterns and temporal drift were detected.",
                reasons = reasons,
                modelVersions = mapOf("video" to "video_detector_phase9"),
                scannedAt = Clock.System.now(),
                inferenceHardware = InferenceHardware.GPU,
                elapsedMs = 1200,
                forensicEvidence = forensicEvidence,
            )

        composeRule.setContent {
            var selectedReason by remember { mutableStateOf<Reason?>(null) }
            ScanFlowScreen(
                state = ScanUiState(media = media, surface = ScanSurface.Forensic, verdict = verdict, selectedReason = selectedReason),
                onClose = {},
                onPrimaryVerdictAction = {},
                onDone = {},
                onBackToVerdict = {},
                onReasonSelected = { selectedReason = it },
                onReasonDismiss = { selectedReason = null },
                onFindOriginalDismiss = {},
            )
        }

        composeRule.onNodeWithTag(FORENSIC_SCREEN_TAG).assertExists()
        composeRule.onNodeWithText("Heatmap").assertExists()
        composeRule.onNodeWithTag("${FORENSIC_TIMELINE_SEGMENT_TAG_PREFIX}1").performClick()
        composeRule.onNodeWithTag("${FORENSIC_REASON_TAG_PREFIX}1").performClick()
        composeRule.onNodeWithTag(REASON_DETAIL_SHEET_TAG).assertExists()
        composeRule.onNodeWithText("0:04").assertExists()
        composeRule.onNodeWithTag(REASON_DETAIL_CLOSE_TAG).performClick()
    }

    private fun media(): ScannedMedia =
        ScannedMedia(
            id = "phase10-media",
            uri = "file:///tmp/phase10.mp4",
            mediaType = MediaType.VIDEO,
            mimeType = "video/mp4",
            sizeBytes = 1024,
            durationMs = 8_000,
            widthPx = 1280,
            heightPx = 720,
            source = MediaSource.FilePicker,
            ingestedAt = Clock.System.now(),
        )
}
