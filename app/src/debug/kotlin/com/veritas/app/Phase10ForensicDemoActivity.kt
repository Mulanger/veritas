package com.veritas.app

import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.veritas.core.design.VeritasTheme
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

class Phase10ForensicDemoActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_VIDEO
        val mediaType =
            when (mode) {
                MODE_IMAGE -> MediaType.IMAGE
                MODE_AUDIO -> MediaType.AUDIO
                else -> MediaType.VIDEO
            }
        val media = demoMedia(mediaType)
        val reasons = demoReasons(mediaType)
        val verdict = demoVerdict(media, reasons)
        setContent {
            VeritasTheme {
                var selectedReason by remember {
                    mutableStateOf(if (mode == MODE_SHEET) reasons.first() else null)
                }
                ScanFlowScreen(
                    state =
                        ScanUiState(
                            media = media,
                            surface = ScanSurface.Forensic,
                            verdict = verdict,
                            selectedReason = selectedReason,
                        ),
                    onClose = {},
                    onPrimaryVerdictAction = {},
                    onDone = {},
                    onBackToVerdict = {},
                    onReasonSelected = { selectedReason = it },
                    onReasonDismiss = { selectedReason = null },
                    onFindOriginalDismiss = {},
                )
            }
        }
    }

    private fun demoMedia(mediaType: MediaType): ScannedMedia =
        ScannedMedia(
            id = "phase10-demo-${mediaType.name.lowercase()}",
            uri = "file:///phase10/demo",
            mediaType = mediaType,
            mimeType =
                when (mediaType) {
                    MediaType.IMAGE -> "image/jpeg"
                    MediaType.AUDIO -> "audio/mp4"
                    MediaType.VIDEO -> "video/mp4"
                },
            sizeBytes = 1024,
            durationMs = if (mediaType == MediaType.IMAGE) null else 23_000,
            widthPx = if (mediaType == MediaType.AUDIO) null else 1280,
            heightPx = if (mediaType == MediaType.AUDIO) null else 720,
            source = MediaSource.FilePicker,
            ingestedAt = Clock.System.now(),
        )

    private fun demoVerdict(
        media: ScannedMedia,
        reasons: List<Reason>,
    ): Verdict {
        val forensicEvidence =
            when (media.mediaType) {
                MediaType.IMAGE ->
                    ForensicEvidence.Image(
                        ForensicEvidenceFactory.imageHeatmap(media.mediaType, 0.84f, reasons),
                    )
                MediaType.AUDIO ->
                    ForensicEvidence.Audio(
                        waveform = ForensicEvidenceFactory.waveform(media.durationMs, 0.79f, reasons),
                        temporalConfidence = ForensicEvidenceFactory.temporalConfidence(media.durationMs, listOf(0L to 0.55f, 7_000L to 0.84f, 15_000L to 0.78f), 0.72f),
                    )
                MediaType.VIDEO ->
                    ForensicEvidence.Video(
                        heatmap = ForensicEvidenceFactory.videoHeatmap(0.86f, listOf(0L to 0.48f, 4_000L to 0.86f, 9_000L to 0.91f, 17_000L to 0.75f), reasons),
                        temporalConfidence = ForensicEvidenceFactory.temporalConfidence(media.durationMs, listOf(0L to 0.48f, 4_000L to 0.86f, 9_000L to 0.91f, 17_000L to 0.75f), 0.70f),
                    )
            }
        return Verdict(
            id = "phase10-demo-verdict",
            mediaId = media.id,
            mediaType = media.mediaType,
            outcome = VerdictOutcome.LIKELY_SYNTHETIC,
            confidence = ConfidenceRange(82, 96),
            summary = "Demo forensic evidence generated from Phase 10 detector evidence contracts.",
            reasons = reasons,
            modelVersions = mapOf(media.mediaType.name.lowercase() to "phase10-demo"),
            scannedAt = Clock.System.now(),
            inferenceHardware = InferenceHardware.GPU,
            elapsedMs = 1200,
            forensicEvidence = forensicEvidence,
        )
    }

    private fun demoReasons(mediaType: MediaType): List<Reason> =
        when (mediaType) {
            MediaType.IMAGE ->
                listOf(
                    Reason(ReasonCode.IMG_DEEPFAKE_MODEL_HIGH, 0.70f, Severity.MAJOR, ReasonEvidence.Region("face", BBox(0.28f, 0.18f, 0.44f, 0.55f))),
                    Reason(ReasonCode.IMG_EXIF_MISSING, 0.10f, Severity.MINOR, ReasonEvidence.Qualitative("No camera metadata is present.")),
                    Reason(ReasonCode.IMG_ELA_ANOMALY, 0.07f, Severity.MINOR, ReasonEvidence.Scalar(0.66f, "ela_ratio")),
                )
            MediaType.AUDIO ->
                listOf(
                    Reason(ReasonCode.AUD_SYNTHETIC_VOICE_HIGH, 0.70f, Severity.MAJOR, ReasonEvidence.Temporal(listOf(4_000L, 11_000L))),
                    Reason(ReasonCode.AUD_CODEC_MISMATCH, 0.08f, Severity.MINOR, ReasonEvidence.Scalar(0.31f, "codec_plausibility")),
                    Reason(ReasonCode.AUD_LOW_QUALITY, 0.05f, Severity.NEUTRAL, ReasonEvidence.Qualitative("Sample rate or codec quality reduced confidence.")),
                )
            MediaType.VIDEO ->
                listOf(
                    Reason(ReasonCode.VID_SPATIAL_SYNTHETIC_FRAMES, 0.50f, Severity.MAJOR, ReasonEvidence.Region("face", BBox(0.28f, 0.18f, 0.44f, 0.55f))),
                    Reason(ReasonCode.VID_TEMPORAL_DRIFT_HIGH, 0.20f, Severity.MINOR, ReasonEvidence.Temporal(listOf(4_000L, 9_000L, 17_000L))),
                    Reason(ReasonCode.VID_FACE_INCONSISTENT, 0.10f, Severity.MINOR, ReasonEvidence.Scalar(0.76f, "face_consistency")),
                )
        }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_IMAGE = "image"
        const val MODE_AUDIO = "audio"
        const val MODE_VIDEO = "video"
        const val MODE_SHEET = "sheet"
    }
}
