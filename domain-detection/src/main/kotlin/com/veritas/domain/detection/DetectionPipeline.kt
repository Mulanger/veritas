package com.veritas.domain.detection

import kotlinx.coroutines.flow.Flow

interface DetectionPipeline {
    fun scan(media: ScannedMedia): Flow<ScanStage>

    fun cancel()
}

interface DetectorInput {
    val media: ScannedMedia
}

data class VideoInput(
    override val media: ScannedMedia,
) : DetectorInput

data class AudioInput(
    override val media: ScannedMedia,
) : DetectorInput

data class ImageInput(
    override val media: ScannedMedia,
) : DetectorInput

interface Detector<in TInput : DetectorInput, out TResult : DetectorResult> {
    suspend fun detect(input: TInput): TResult
}

sealed interface DetectorResult {
    val detectorId: String
    val syntheticScore: Float
    val confidence: Float
    val reasons: List<Reason>
    val elapsedMs: Long
    val confidenceInterval: ConfidenceInterval
        get() =
            ConfidenceInterval(
                low = (syntheticScore - 0.15f).coerceIn(MIN_DETECTOR_SCORE, MAX_CONFIDENCE),
                high = (syntheticScore + 0.15f).coerceIn(MIN_DETECTOR_SCORE, MAX_CONFIDENCE),
            )
    val subScores: Map<String, Float>
        get() = emptyMap()
    val uncertainReasons: List<UncertainReason>
        get() = emptyList()
    val fallbackUsed: FallbackLevel
        get() = FallbackLevel.NONE
    val forensicEvidence: ForensicEvidence
        get() = ForensicEvidence.None
}

data class BasicDetectorResult(
    override val detectorId: String,
    override val syntheticScore: Float,
    override val confidence: Float,
    override val reasons: List<Reason>,
    override val elapsedMs: Long,
    override val confidenceInterval: ConfidenceInterval = ConfidenceInterval.around(syntheticScore),
    override val subScores: Map<String, Float> = emptyMap(),
    override val uncertainReasons: List<UncertainReason> = emptyList(),
    override val fallbackUsed: FallbackLevel = FallbackLevel.NONE,
    override val forensicEvidence: ForensicEvidence = ForensicEvidence.None,
) : DetectorResult

private const val MIN_DETECTOR_SCORE = 0.02f
private const val MAX_DETECTOR_SCORE = 0.98f
private const val MAX_CONFIDENCE = 0.95f

data class ConfidenceInterval(
    val low: Float,
    val high: Float,
) {
    init {
        require(low in MIN_DETECTOR_SCORE..MAX_CONFIDENCE) { "low out of detector confidence range" }
        require(high in low..MAX_CONFIDENCE) { "high must be >= low and <= 0.95" }
    }

    companion object {
        fun around(
            score: Float,
            width: Float = 0.18f,
        ): ConfidenceInterval {
            val clamped = score.coerceIn(MIN_DETECTOR_SCORE, MAX_DETECTOR_SCORE)
            return ConfidenceInterval(
                low = (clamped - width / 2f).coerceIn(MIN_DETECTOR_SCORE, MAX_CONFIDENCE),
                high = (clamped + width / 2f).coerceIn(MIN_DETECTOR_SCORE, MAX_CONFIDENCE),
            )
        }
    }
}

enum class FallbackLevel {
    NONE,
    GPU,
    CPU_XNNPACK,
}

enum class UncertainReason {
    TOO_SMALL,
    TOO_SHORT,
    TOO_LONG_PROCESSED_TRUNCATED,
    LOW_SAMPLE_RATE,
    VID_TOO_SHORT,
    VID_LOW_RESOLUTION,
    VID_HEAVY_COMPRESSION,
    VID_INSUFFICIENT_FRAMES,
    VID_DECODE_FAILED,
    VID_NO_FACES_DETECTED,
    HEAVY_COMPRESSION,
    LOW_CONFIDENCE_RANGE,
    CPU_FALLBACK,
}
