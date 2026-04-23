@file:Suppress("CyclomaticComplexMethod", "MagicNumber", "MaxLineLength", "TooManyFunctions")

package com.veritas.app

import androidx.compose.ui.graphics.Color
import com.veritas.core.design.EvidenceChipVariant
import com.veritas.core.design.StageRowState
import com.veritas.core.design.VerdictTone
import com.veritas.core.design.VeritasColors
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.PipelineStage
import com.veritas.domain.detection.Reason
import com.veritas.domain.detection.ReasonCode
import com.veritas.domain.detection.ReasonEvidence
import com.veritas.domain.detection.ScanStage
import com.veritas.domain.detection.ScannedMedia
import com.veritas.domain.detection.Verdict
import com.veritas.domain.detection.VerdictOutcome
import java.text.DecimalFormat
import java.util.Locale
import kotlin.random.Random

data class ScanUiState(
    val media: ScannedMedia? = null,
    val surface: ScanSurface = ScanSurface.Scanning,
    val stages: List<StageUiModel> = emptyList(),
    val verdict: Verdict? = null,
    val selectedReason: Reason? = null,
    val showFindOriginalSheet: Boolean = false,
) {
    companion object {
        fun scanning(media: ScannedMedia): ScanUiState =
            ScanUiState(
                media = media,
                surface = ScanSurface.Scanning,
                stages =
                    PipelineStage.forMediaType(media.mediaType).map { stage ->
                        StageUiModel(
                            stage = stage,
                            state = StageRowState.Idle,
                            meta = "QUEUED",
                        )
                    },
            )
    }
}

enum class ScanSurface {
    Scanning,
    Verdict,
    Forensic,
}

data class StageUiModel(
    val stage: PipelineStage,
    val state: StageRowState,
    val meta: String,
    val startedAtEpochMs: Long? = null,
)

data class ReasonCodeCopy(
    val primaryName: String,
    val whatItMeans: String,
    val whyItMatters: String,
    val falsePositiveRisk: String,
)

data class TimelineSegmentUi(
    val tone: VerdictTone,
)

data class VerdictPresentation(
    val headline: String,
    val primaryAction: String,
    val tone: VerdictTone,
    val headlineColor: Color,
)

fun ScanUiState.applyStage(stage: ScanStage): ScanUiState =
    when (stage) {
        is ScanStage.Started ->
            copy(
                stages =
                    stage.stages.map { pipelineStage ->
                        StageUiModel(
                            stage = pipelineStage,
                            state = StageRowState.Idle,
                            meta = "QUEUED",
                        )
                    },
            )

        is ScanStage.StageActive ->
            copy(
                surface = ScanSurface.Scanning,
                stages =
                    stages.map { row ->
                        if (row.stage == stage.stage) {
                            row.copy(
                                state = StageRowState.Active,
                                meta = "0.0s",
                                startedAtEpochMs = stage.startedAt.toEpochMilliseconds(),
                            )
                        } else {
                            row
                        }
                    },
            )

        is ScanStage.StageDone ->
            copy(
                stages =
                    stages.map { row ->
                        if (row.stage == stage.stage) {
                            row.copy(
                                state = StageRowState.Done,
                                meta = stage.terminalState.label,
                                startedAtEpochMs = null,
                            )
                        } else {
                            row
                        }
                    },
            )

        is ScanStage.VerdictReady ->
            copy(
                surface = ScanSurface.Verdict,
                verdict = stage.verdict,
                selectedReason = null,
                showFindOriginalSheet = false,
            )

        is ScanStage.Cancelled,
        is ScanStage.Failed,
        -> this
    }

fun verdictPresentation(outcome: VerdictOutcome): VerdictPresentation =
    when (outcome) {
        VerdictOutcome.VERIFIED_AUTHENTIC ->
            VerdictPresentation(
                headline = "Verified\nauthentic.",
                primaryAction = "See details",
                tone = VerdictTone.Ok,
                headlineColor = VeritasColors.ok,
            )

        VerdictOutcome.LIKELY_AUTHENTIC ->
            VerdictPresentation(
                headline = "Looks\nauthentic.",
                primaryAction = "See details",
                tone = VerdictTone.Ok,
                headlineColor = VeritasColors.ok.copy(alpha = 0.84f),
            )

        VerdictOutcome.UNCERTAIN ->
            VerdictPresentation(
                headline = "Uncertain.",
                primaryAction = "Find original",
                tone = VerdictTone.Warn,
                headlineColor = VeritasColors.warn,
            )

        VerdictOutcome.LIKELY_SYNTHETIC ->
            VerdictPresentation(
                headline = "Likely\nsynthetic.",
                primaryAction = "See heatmap",
                tone = VerdictTone.Bad,
                headlineColor = VeritasColors.bad,
            )
    }

fun evidenceVariantFor(outcome: VerdictOutcome): EvidenceChipVariant =
    when (outcome) {
        VerdictOutcome.VERIFIED_AUTHENTIC,
        VerdictOutcome.LIKELY_AUTHENTIC,
        -> EvidenceChipVariant.Plus
        VerdictOutcome.UNCERTAIN -> EvidenceChipVariant.Mixed
        VerdictOutcome.LIKELY_SYNTHETIC -> EvidenceChipVariant.Minus
    }

fun verdictTag(media: ScannedMedia): String {
    val trailing =
        media.durationMs?.let(::formatScanDuration)
            ?: formatResolution(media)
    return "VERDICT · ${media.mediaType.name} · $trailing"
}

fun forensicPillText(outcome: VerdictOutcome): String =
    when (outcome) {
        VerdictOutcome.VERIFIED_AUTHENTIC -> "VERIFIED"
        VerdictOutcome.LIKELY_AUTHENTIC -> "AUTHENTIC"
        VerdictOutcome.UNCERTAIN -> "UNCERTAIN"
        VerdictOutcome.LIKELY_SYNTHETIC -> "SYNTHETIC"
    }

fun forensicIndicator(mediaType: MediaType): String =
    when (mediaType) {
        MediaType.VIDEO -> "Frame 127 · 0:04.2"
        MediaType.AUDIO -> "Audio span · 0:04.2"
        MediaType.IMAGE -> "Region: facial"
    }

fun timelineSegmentsFor(
    verdict: Verdict,
    mediaType: MediaType,
): List<TimelineSegmentUi> {
    if (mediaType == MediaType.IMAGE) {
        return emptyList()
    }

    val random = Random(verdict.id.hashCode())
    return List(8) {
        val tone =
            when (verdict.outcome) {
                VerdictOutcome.VERIFIED_AUTHENTIC,
                VerdictOutcome.LIKELY_AUTHENTIC,
                -> if (random.nextInt(100) < 80) VerdictTone.Ok else VerdictTone.Warn
                VerdictOutcome.UNCERTAIN ->
                    when (random.nextInt(3)) {
                        0 -> VerdictTone.Ok
                        1 -> VerdictTone.Warn
                        else -> VerdictTone.Bad
                    }
                VerdictOutcome.LIKELY_SYNTHETIC ->
                    if (random.nextInt(100) < 70) {
                        VerdictTone.Bad
                    } else {
                        VerdictTone.Warn
                    }
            }
        TimelineSegmentUi(tone = tone)
    }
}

fun toneColor(tone: VerdictTone): Color =
    when (tone) {
        VerdictTone.Ok -> VeritasColors.ok
        VerdictTone.Warn -> VeritasColors.warn
        VerdictTone.Bad -> VeritasColors.bad
    }

fun reasonAccentColor(reason: Reason): Color =
    when (reason.code) {
        ReasonCode.C2PA_VERIFIED,
        ReasonCode.RPPG_NATURAL,
        ReasonCode.CODEC_CONSISTENT,
        -> VeritasColors.ok

        ReasonCode.COMPRESSION_HEAVY,
        ReasonCode.LOW_RESOLUTION,
        ReasonCode.DETECTOR_DISAGREEMENT,
        ReasonCode.C2PA_REVOKED,
        -> VeritasColors.warn

        else -> VeritasColors.bad
    }

fun reasonChipCode(code: ReasonCode): String =
    when (code) {
        ReasonCode.C2PA_VERIFIED -> "C2PA OK"
        ReasonCode.RPPG_NATURAL -> "RPPG OK"
        ReasonCode.CODEC_CONSISTENT -> "CODEC OK"
        ReasonCode.COMPRESSION_HEAVY -> "QUALITY"
        ReasonCode.DETECTOR_DISAGREEMENT -> "DISAGREE"
        ReasonCode.LOW_RESOLUTION -> "LOW RES"
        ReasonCode.TEMPORAL_INCONSISTENCY -> "TEMPORAL"
        ReasonCode.RPPG_ABSENT -> "RPPG"
        ReasonCode.DIFFUSION_SPECTRAL_SIG -> "SPECTRAL"
        ReasonCode.SYNTHID_DETECTED -> "SYNTHID"
        ReasonCode.AUDIO_SPECTRAL_NEURAL -> "SPECTRAL"
        ReasonCode.AUDIO_PROSODY_UNNATURAL -> "PROSODY"
        ReasonCode.AUDIO_CODEC_MISMATCH -> "CODEC"
        ReasonCode.ELA_INCONSISTENT -> "ELA"
        ReasonCode.METADATA_IMPLAUSIBLE -> "METADATA"
        ReasonCode.EAR_GEOMETRY_DRIFT -> "EAR"
        else -> code.name.take(10)
    }

fun reasonDescription(reason: Reason): String =
    when (val evidence = reason.evidence) {
        is ReasonEvidence.C2PAVerified ->
            "Signature chain is valid. Issued by ${evidence.issuerName}${evidence.deviceName?.let { " for $it" } ?: ""}."
        is ReasonEvidence.Temporal ->
            "Flagged at ${evidence.timestampsMs.joinToString(separator = ", ") { formatScanDuration(it) }}."
        is ReasonEvidence.Region ->
            "Localized around the ${evidence.regionLabel.lowercase(Locale.US)} region."
        is ReasonEvidence.Scalar ->
            "${reason.code.prettyTitle()} measured ${DecimalFormat("0.##").format(evidence.measurement)} ${evidence.unit}."
        is ReasonEvidence.Qualitative -> evidence.note
        ReasonEvidence.None -> "${reason.code.prettyTitle()} contributed to this verdict in the Phase 5 stub pipeline."
    }

fun reasonCopy(code: ReasonCode): ReasonCodeCopy =
    ReasonCode.entries
        .associateWith { reasonCode ->
            val title = reasonCode.prettyTitle()
            ReasonCodeCopy(
                primaryName = title,
                whatItMeans = "Placeholder copy for $title. In the Phase 5 stub pipeline this explanation exists to validate the UI structure, field layout, and translation-ready dictionary flow.",
                whyItMatters = "$title can be a useful signal when it appears alongside other evidence. Later phases will replace this placeholder with calibrated copy tied to real detector outputs and real measurements.",
                falsePositiveRisk = "$title should not be treated as proof on its own. Capture conditions, edits, compression, or unusual source material can surface similar patterns even in authentic media.",
            )
        }.getValue(code)

fun reasonTimestamps(reason: Reason): List<Long>? =
    when (val evidence = reason.evidence) {
        is ReasonEvidence.Temporal -> evidence.timestampsMs
        else -> null
    }

fun ReasonCode.prettyTitle(): String =
    name
        .lowercase(Locale.US)
        .split('_')
        .joinToString(" ") { token -> token.replaceFirstChar(Char::titlecase) }

fun formatScanDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(Locale.US, minutes, seconds)
}

fun formatResolution(media: ScannedMedia): String =
    if (media.widthPx != null && media.heightPx != null) {
        "${media.widthPx}x${media.heightPx}"
    } else {
        media.mimeType.uppercase(Locale.US)
    }
