package com.veritas.domain.detection

import kotlinx.datetime.Instant
import java.util.Locale

private const val STAGE_DURATION_DIVISOR_MS = 1000f

sealed class ScanStage {
    data class Started(
        val stages: List<PipelineStage>,
    ) : ScanStage()

    data class StageActive(
        val stage: PipelineStage,
        val stageIndex: Int,
        val totalStages: Int,
        val startedAt: Instant,
    ) : ScanStage()

    data class StageDone(
        val stage: PipelineStage,
        val stageIndex: Int,
        val totalStages: Int,
        val terminalState: StageTerminalState,
        val elapsedMs: Long,
    ) : ScanStage()

    data class VerdictReady(
        val verdict: Verdict,
    ) : ScanStage()

    data class Failed(
        val error: ScanError,
    ) : ScanStage()

    data object Cancelled : ScanStage()
}

sealed class PipelineStage(
    val key: String,
    val label: String,
) {
    data object C2paManifestCheck : PipelineStage("c2pa_manifest_check", "C2PA manifest check")

    data object WatermarkScan : PipelineStage("watermark_scan", "Watermark scan")

    data object TemporalConsistency : PipelineStage("temporal_consistency", "Temporal consistency")

    data object SpatialArtifactModel : PipelineStage("spatial_artifact_model", "Spatial artifact model")

    data object FacialPhysiologicalCheck :
        PipelineStage("facial_physiological_check", "Facial physiological check")

    data object SpectralAnalysis : PipelineStage("spectral_analysis", "Spectral analysis")

    data object ProsodyPacingCheck : PipelineStage("prosody_pacing_check", "Prosody / pacing")

    data object CodecFingerprintCheck : PipelineStage("codec_fingerprint_check", "Codec fingerprint")

    data object FrequencyDomainAnalysis :
        PipelineStage("frequency_domain_analysis", "Frequency-domain analysis")

    data object MetadataForensics : PipelineStage("metadata_forensics", "Metadata forensics")

    companion object {
        fun forMediaType(mediaType: MediaType): List<PipelineStage> =
            when (mediaType) {
                MediaType.VIDEO ->
                    listOf(
                        C2paManifestCheck,
                        WatermarkScan,
                        TemporalConsistency,
                        SpatialArtifactModel,
                        FacialPhysiologicalCheck,
                    )

                MediaType.AUDIO ->
                    listOf(
                        C2paManifestCheck,
                        WatermarkScan,
                        SpectralAnalysis,
                        ProsodyPacingCheck,
                        CodecFingerprintCheck,
                    )

                MediaType.IMAGE ->
                    listOf(
                        C2paManifestCheck,
                        WatermarkScan,
                        FrequencyDomainAnalysis,
                        SpatialArtifactModel,
                        MetadataForensics,
                    )
            }
    }
}

sealed class StageTerminalState(
    val label: String,
) {
    data object None : StageTerminalState("NONE")

    data object Verified : StageTerminalState("✓")

    data object Flagged : StageTerminalState("FLAG")

    data object Skipped : StageTerminalState("SKIP")

    data class Duration(
        val valueMs: Long,
    ) : StageTerminalState(valueMs.toStageDurationLabel())
}

sealed class ScanError {
    data object DecoderFailed : ScanError()

    data object OutOfMemory : ScanError()

    data object ModelNotLoaded : ScanError()

    data class Unknown(
        val throwable: Throwable,
    ) : ScanError()
}

private fun Long.toStageDurationLabel(): String = String.format(Locale.US, "%.1fs", this / STAGE_DURATION_DIVISOR_MS)
