@file:Suppress("MagicNumber", "LongParameterList")

package com.veritas.feature.detect.video.domain

import android.util.Log
import com.veritas.domain.detection.BasicDetectorResult
import com.veritas.domain.detection.ConfidenceInterval
import com.veritas.domain.detection.Detector
import com.veritas.domain.detection.FallbackLevel
import com.veritas.domain.detection.Reason
import com.veritas.domain.detection.ReasonCode
import com.veritas.domain.detection.ReasonEvidence
import com.veritas.domain.detection.Severity
import com.veritas.domain.detection.UncertainReason
import com.veritas.feature.detect.image.model.DeepfakeDetectorV2Model
import com.veritas.feature.detect.video.decode.ExtractedVideoFrames
import com.veritas.feature.detect.video.decode.FrameSampler
import com.veritas.feature.detect.video.decode.MediaCodecFrameExtractor
import com.veritas.feature.detect.video.decode.VideoMetadata
import com.veritas.feature.detect.video.face.FaceConsistencyAnalyzer
import com.veritas.feature.detect.video.face.FaceConsistencyScore
import com.veritas.feature.detect.video.fusion.VideoFusion
import com.veritas.feature.detect.video.temporal.MoViNetA0Streaming
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class VideoDetector @Inject constructor(
    private val frameExtractor: MediaCodecFrameExtractor,
    private val spatialModel: DeepfakeDetectorV2Model,
    private val temporalModel: MoViNetA0Streaming,
    private val faceAnalyzer: FaceConsistencyAnalyzer,
) : Detector<VideoDetectionInput, BasicDetectorResult> {
    override suspend fun detect(input: VideoDetectionInput): BasicDetectorResult {
        val startedAt = Clock.System.now()
        val extractionStartedMs = startedAt.toEpochMilliseconds()
        val extracted = runCatching {
            frameExtractor.extract(input.file, FrameSampler.DEFAULT_TARGET_FRAMES)
        }.getOrElse {
            return decodeFailedResult(startedAt)
        }
        return try {
            val extractionMs = Clock.System.now().toEpochMilliseconds() - extractionStartedMs
            val spatialStartedMs = Clock.System.now().toEpochMilliseconds()
            val spatialScores = analyzeSpatialFrames(extracted)
            val spatialMs = Clock.System.now().toEpochMilliseconds() - spatialStartedMs
            val spatialMean = spatialScores.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0.5f
            val spatialMax = spatialScores.maxOrNull() ?: spatialMean
            val temporalStartedMs = Clock.System.now().toEpochMilliseconds()
            val temporalScore = temporalModel.analyze(extracted.frames.map { it.bitmap })
            val temporalMs = Clock.System.now().toEpochMilliseconds() - temporalStartedMs
            val faceStartedMs = Clock.System.now().toEpochMilliseconds()
            val faceScore = faceAnalyzer.analyze(extracted.frames)
            val faceMs = Clock.System.now().toEpochMilliseconds() - faceStartedMs
            val fusedScore = VideoFusion.fuse(spatialMean, spatialMax, temporalScore.driftScore, faceScore.score)
            val confidenceInterval = videoScoreToInterval(fusedScore)
            val fallback = maxFallback(temporalScore.fallbackLevel)
            val uncertainReasons = uncertaintyReasons(extracted, fusedScore, fallback, faceScore)
            val elapsedMs = Clock.System.now().toEpochMilliseconds() - startedAt.toEpochMilliseconds()
            lastTiming = VideoStageTiming(
                extractionMs = extractionMs,
                spatialMs = spatialMs,
                temporalMs = temporalMs,
                faceMs = faceMs,
                totalMs = elapsedMs,
                temporalFallback = temporalScore.fallbackLevel,
                faceFallback = faceScore.fallbackLevel,
            )
            Log.i(TAG, "phase9_stage_timing=$lastTiming")

            BasicDetectorResult(
                detectorId = DETECTOR_ID,
                syntheticScore = fusedScore,
                confidence = (confidenceInterval.high - confidenceInterval.low).let { 1f - it }.coerceIn(0f, 0.95f),
                reasons = reasonsFor(spatialScores, temporalScore.driftScore, faceScore, uncertainReasons),
                elapsedMs = elapsedMs,
                confidenceInterval = confidenceInterval,
                subScores = mapOf(
                    "spatial_vit" to spatialMean,
                    "temporal_movinet" to temporalScore.driftScore,
                    "face_consistency" to (faceScore.score ?: spatialMean),
                ),
                uncertainReasons = uncertainReasons,
                fallbackUsed = fallback,
            )
        } finally {
            extracted.frames.forEach { it.bitmap.recycle() }
        }
    }

    private fun decodeFailedResult(startedAt: kotlinx.datetime.Instant): BasicDetectorResult {
        val elapsedMs = Clock.System.now().toEpochMilliseconds() - startedAt.toEpochMilliseconds()
        val interval = ConfidenceInterval(low = 0.34f, high = 0.66f)
        return BasicDetectorResult(
            detectorId = DETECTOR_ID,
            syntheticScore = 0.50f,
            confidence = 1f - (interval.high - interval.low),
            reasons = listOf(
                Reason(
                    ReasonCode.VID_DECODE_FAILED,
                    0.80f,
                    Severity.NEUTRAL,
                    ReasonEvidence.Qualitative("Video decoding failed before detector inference could run."),
                ),
            ),
            elapsedMs = elapsedMs,
            confidenceInterval = interval,
            subScores = mapOf(
                "spatial_vit" to 0.50f,
                "temporal_movinet" to 0f,
                "face_consistency" to 0.50f,
            ),
            uncertainReasons = listOf(UncertainReason.VID_DECODE_FAILED),
            fallbackUsed = FallbackLevel.NONE,
        )
    }

    private suspend fun analyzeSpatialFrames(extracted: ExtractedVideoFrames): List<Float> =
        extracted.frames.filterIndexed { index, _ -> index % SPATIAL_FRAME_STRIDE == 0 }
            .map { spatialModel.score(it.bitmap).score }

    private fun videoScoreToInterval(fusedScore: Float): ConfidenceInterval {
        val fused = fusedScore.coerceIn(0.02f, 0.98f)
        val width = when {
            fused < 0.25f -> 0.20f
            fused < 0.40f -> 0.30f
            fused < 0.60f -> 0.32f
            fused < 0.75f -> 0.30f
            else -> 0.20f
        }
        return ConfidenceInterval(
            low = (fused - width / 2f).coerceIn(0.02f, 0.95f),
            high = (fused + width / 2f).coerceIn(0.02f, 0.95f),
        )
    }

    private fun uncertaintyReasons(
        extracted: ExtractedVideoFrames,
        fusedScore: Float,
        fallbackLevel: FallbackLevel,
        faceScore: FaceConsistencyScore,
    ): List<UncertainReason> = buildList {
        val metadata = extracted.metadata
        if (metadata.durationMs < MIN_DURATION_MS) add(UncertainReason.VID_TOO_SHORT)
        if (metadata.width < MIN_WIDTH || metadata.height < MIN_HEIGHT) add(UncertainReason.VID_LOW_RESOLUTION)
        if ((metadata.bitrate ?: Int.MAX_VALUE) < MIN_BITRATE) add(UncertainReason.VID_HEAVY_COMPRESSION)
        if (extracted.frames.size < MIN_RELIABLE_FRAMES) add(UncertainReason.VID_INSUFFICIENT_FRAMES)
        if (faceScore.detectedFaces == 0) add(UncertainReason.VID_NO_FACES_DETECTED)
        if (fusedScore in LOW_CONFIDENCE_MIN..LOW_CONFIDENCE_MAX) add(UncertainReason.LOW_CONFIDENCE_RANGE)
        if (fallbackLevel == FallbackLevel.CPU_XNNPACK) add(UncertainReason.CPU_FALLBACK)
    }

    private fun reasonsFor(
        spatialScores: List<Float>,
        temporalDrift: Float,
        faceScore: FaceConsistencyScore,
        uncertainReasons: List<UncertainReason>,
    ): List<Reason> = buildList {
        val flagged = spatialScores.count { it > SPATIAL_FRAME_THRESHOLD }
        if (flagged > 0) {
            add(Reason(ReasonCode.VID_SPATIAL_SYNTHETIC_FRAMES, 0.50f, Severity.MAJOR, ReasonEvidence.Scalar(flagged.toFloat(), "flagged_frames")))
        }
        if (temporalDrift > TEMPORAL_DRIFT_THRESHOLD) {
            add(Reason(ReasonCode.VID_TEMPORAL_DRIFT_HIGH, 0.20f, Severity.MINOR, ReasonEvidence.Scalar(temporalDrift, "movinet_drift")))
        }
        if ((faceScore.score ?: 0f) > FACE_INCONSISTENT_THRESHOLD && faceScore.analyzedCrops > 0) {
            add(Reason(ReasonCode.VID_FACE_INCONSISTENT, 0.10f, Severity.MINOR, ReasonEvidence.Scalar(faceScore.score ?: 0f, "face_consistency")))
        }
        if (uncertainReasons.any { it.name.startsWith("VID_") }) {
            add(Reason(ReasonCode.VID_LOW_QUALITY, 0.08f, Severity.NEUTRAL, ReasonEvidence.Qualitative("Video quality or frame availability reduced detector confidence.")))
        }
    }.ifEmpty {
        listOf(Reason(ReasonCode.CODEC_CONSISTENT, 0.30f, Severity.POSITIVE, ReasonEvidence.Qualitative("Video spatial, temporal, and face signals did not show strong synthetic patterns.")))
    }.sortedByDescending { it.weight }

    private fun maxFallback(temporalFallback: FallbackLevel): FallbackLevel =
        when {
            temporalFallback == FallbackLevel.GPU -> FallbackLevel.GPU
            else -> FallbackLevel.CPU_XNNPACK
        }

    companion object {
        private const val TAG = "VideoDetector"
        const val DETECTOR_ID = "video_detector_phase9"
        @Volatile var lastTiming: VideoStageTiming? = null
            private set
        private const val SPATIAL_FRAME_STRIDE = 4
        private const val SPATIAL_FRAME_THRESHOLD = 0.70f
        private const val TEMPORAL_DRIFT_THRESHOLD = 0.35f
        private const val FACE_INCONSISTENT_THRESHOLD = 0.70f
        private const val MIN_DURATION_MS = 2_000L
        private const val MIN_WIDTH = 320
        private const val MIN_HEIGHT = 240
        private const val MIN_BITRATE = 50_000
        private const val MIN_RELIABLE_FRAMES = 4
        private const val LOW_CONFIDENCE_MIN = 0.35f
        private const val LOW_CONFIDENCE_MAX = 0.65f
    }
}

data class VideoStageTiming(
    val extractionMs: Long,
    val spatialMs: Long,
    val temporalMs: Long,
    val faceMs: Long,
    val totalMs: Long,
    val temporalFallback: FallbackLevel,
    val faceFallback: FallbackLevel,
)
