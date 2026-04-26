package com.veritas.domain.detection

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

private const val MAX_CONFIDENCE_PCT = 100

@Serializable
data class Verdict(
    val id: String,
    val mediaId: String,
    val mediaType: MediaType,
    val outcome: VerdictOutcome,
    val confidence: ConfidenceRange,
    val summary: String,
    val reasons: List<Reason>,
    val modelVersions: Map<String, String>,
    val scannedAt: Instant,
    val inferenceHardware: InferenceHardware,
    val elapsedMs: Long,
)

@Serializable
enum class VerdictOutcome {
    VERIFIED_AUTHENTIC,
    LIKELY_AUTHENTIC,
    UNCERTAIN,
    LIKELY_SYNTHETIC,
}

@Serializable
data class ConfidenceRange(
    val lowPct: Int,
    val highPct: Int,
) {
    init {
        require(lowPct in 0..MAX_CONFIDENCE_PCT) { "lowPct out of range" }
        require(highPct in lowPct..MAX_CONFIDENCE_PCT) { "highPct must be >= lowPct" }
    }
}

@Serializable
enum class InferenceHardware {
    NPU,
    GPU,
    CPU_XNNPACK,
    MIXED,
}

@Serializable
data class Reason(
    val code: ReasonCode,
    val weight: Float,
    val severity: Severity,
    val evidence: ReasonEvidence,
)

@Serializable
enum class Severity {
    POSITIVE,
    NEUTRAL,
    MINOR,
    MAJOR,
    CRITICAL,
}

@Serializable
enum class ReasonCode {
    C2PA_VERIFIED,
    C2PA_INVALID_SIGNATURE,
    C2PA_REVOKED,
    SYNTHID_DETECTED,
    DIFFUSION_SPECTRAL_SIG,
    GAN_SPECTRAL_SIG,
    TEMPORAL_INCONSISTENCY,
    LIP_SYNC_DRIFT,
    EAR_GEOMETRY_DRIFT,
    TEETH_ARTIFACTS,
    JEWELRY_FLICKER,
    EYE_REFLECTION_MISMATCH,
    RPPG_ABSENT,
    RPPG_IMPLAUSIBLE,
    RPPG_NATURAL,
    AUDIO_SPECTRAL_NEURAL,
    AUDIO_PROSODY_UNNATURAL,
    AUDIO_CODEC_MISMATCH,
    METADATA_IMPLAUSIBLE,
    ELA_INCONSISTENT,
    CODEC_CONSISTENT,
    COMPRESSION_HEAVY,
    LOW_RESOLUTION,
    DETECTOR_DISAGREEMENT,
    LOW_MEMORY_FALLBACK,
    IMG_DEEPFAKE_MODEL_HIGH,
    IMG_EXIF_MISSING,
    IMG_EXIF_SUSPICIOUS,
    IMG_ELA_ANOMALY,
    IMG_LOW_QUALITY,
    AUD_SYNTHETIC_VOICE_HIGH,
    AUD_CODEC_MISMATCH,
    AUD_TOO_SHORT,
    AUD_LOW_QUALITY,
    AUD_NATURAL_PROSODY,
    VID_TEMPORAL_DRIFT_HIGH,
    VID_SPATIAL_SYNTHETIC_FRAMES,
    VID_FACE_INCONSISTENT,
    VID_LIP_SYNC_DRIFT,
    VID_LOW_QUALITY,
    VID_DECODE_FAILED,
}

@Serializable
sealed class ReasonEvidence {
    @Serializable
    data class C2PAVerified(
        val issuerName: String,
        val deviceName: String?,
        val signedAt: Instant,
    ) : ReasonEvidence()

    @Serializable
    data class SynthIDDetected(
        val generatorName: String,
    ) : ReasonEvidence()

    @Serializable
    data class Temporal(
        val timestampsMs: List<Long>,
    ) : ReasonEvidence()

    @Serializable
    data class Region(
        val regionLabel: String,
        val bboxNormalized: BBox,
    ) : ReasonEvidence()

    @Serializable
    data class Scalar(
        val measurement: Float,
        val unit: String,
    ) : ReasonEvidence()

    @Serializable
    data class Qualitative(
        val note: String,
    ) : ReasonEvidence()

    @Serializable
    data object None : ReasonEvidence()
}

@Serializable
data class BBox(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
) {
    init {
        require(x in 0f..1f && y in 0f..1f && w in 0f..1f && h in 0f..1f)
    }
}
