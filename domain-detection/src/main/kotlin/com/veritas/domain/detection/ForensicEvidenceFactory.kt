@file:Suppress("MagicNumber", "MaxLineLength", "ReturnCount")

package com.veritas.domain.detection

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

object ForensicEvidenceFactory {
    const val HEATMAP_BINS = 64
    private const val DEFAULT_AUDIO_BINS = 96

    fun imageHeatmap(
        mediaType: MediaType,
        syntheticScore: Float,
        reasons: List<Reason>,
        timestampMs: Long = 0L,
    ): HeatmapData =
        HeatmapData(
            mediaType = mediaType,
            frames =
                listOf(
                    HeatmapFrame(
                        timestampMs = timestampMs,
                        widthBins = HEATMAP_BINS,
                        heightBins = HEATMAP_BINS,
                        intensities = heatmapBins(score = syntheticScore, reasons = reasons),
                        labeledRegions = labeledRegionsFor(reasons),
                    ),
                ),
        )

    fun videoHeatmap(
        syntheticScore: Float,
        frameScores: List<Pair<Long, Float>>,
        reasons: List<Reason>,
    ): HeatmapData {
        val frames =
            frameScores
                .ifEmpty { listOf(0L to syntheticScore) }
                .map { (timestampMs, score) ->
                    HeatmapFrame(
                        timestampMs = timestampMs,
                        widthBins = HEATMAP_BINS,
                        heightBins = HEATMAP_BINS,
                        intensities = heatmapBins(score = score, reasons = reasons),
                        labeledRegions = labeledRegionsFor(reasons),
                    )
                }
        return HeatmapData(mediaType = MediaType.VIDEO, frames = frames)
    }

    fun temporalConfidence(
        durationMs: Long?,
        scores: List<Pair<Long, Float>>,
        fallbackScore: Float,
    ): TemporalConfidence {
        val safeDuration = max(durationMs ?: 8_000L, 1_000L)
        val binCount = ceil(safeDuration / 1_000f).toInt().coerceIn(8, 16)
        val binDuration = max(safeDuration / binCount, 1L)
        val bins =
            List(binCount) { index ->
                val start = index * binDuration
                val end = if (index == binCount - 1) safeDuration else (index + 1) * binDuration
                val nearestScore =
                    scores.minByOrNull { abs(it.first - start) }?.second
                        ?: fallbackScore
                TemporalBin(
                    startMs = start,
                    endMs = end,
                    syntheticProbability = nearestScore.coerceIn(0f, 1f),
                )
            }
        return TemporalConfidence(bins)
    }

    fun waveform(
        durationMs: Long?,
        score: Float,
        reasons: List<Reason>,
        bins: Int = DEFAULT_AUDIO_BINS,
    ): WaveformData {
        val safeDuration = max(durationMs ?: 8_000L, 1_000L)
        val clampedScore = score.coerceIn(0f, 1f)
        val rms =
            List(bins.coerceAtLeast(8)) { index ->
                val phase = index / bins.toFloat()
                val carrier = triangular(phase * 6f)
                val envelope = 0.35f + 0.45f * triangular(phase * 2f + clampedScore)
                (0.08f + carrier * envelope).coerceIn(0f, 1f)
            }
        val flagged = flaggedAudioRegions(safeDuration, reasons, clampedScore)
        return WaveformData(
            durationMs = safeDuration,
            samplesPerBin = max((safeDuration / rms.size).toInt(), 1),
            rmsBins = rms,
            flaggedRegions = flagged,
        )
    }

    private fun heatmapBins(
        score: Float,
        reasons: List<Reason>,
    ): List<Float> {
        val regions =
            labeledRegionsFor(reasons).ifEmpty {
                listOf(LabeledRegion("FACE", BBox(0.31f, 0.20f, 0.38f, 0.48f), Severity.MAJOR))
            }
        val clampedScore = score.coerceIn(0f, 1f)
        return List(HEATMAP_BINS * HEATMAP_BINS) { index ->
            val x = (index % HEATMAP_BINS + 0.5f) / HEATMAP_BINS
            val y = (index / HEATMAP_BINS + 0.5f) / HEATMAP_BINS
            regions
                .fold(0f) { current, region ->
                    val centerX = region.bbox.x + region.bbox.w / 2f
                    val centerY = region.bbox.y + region.bbox.h / 2f
                    val sigmaX = max(region.bbox.w / 1.8f, 0.08f)
                    val sigmaY = max(region.bbox.h / 1.8f, 0.08f)
                    val exponent =
                        -(
                            ((x - centerX) * (x - centerX)) / (2f * sigmaX * sigmaX) +
                                ((y - centerY) * (y - centerY)) / (2f * sigmaY * sigmaY)
                        )
                    max(current, exp(exponent) * severityWeight(region.severity))
                }.let { (it * (0.25f + 0.75f * clampedScore)).coerceIn(0f, 1f) }
        }
    }

    private fun labeledRegionsFor(reasons: List<Reason>): List<LabeledRegion> =
        reasons
            .mapNotNull { reason ->
                when (val evidence = reason.evidence) {
                    is ReasonEvidence.Region ->
                        LabeledRegion(
                            label = evidence.regionLabel.uppercase(),
                            bbox = evidence.bboxNormalized,
                            severity = reason.severity,
                        )
                    else -> defaultRegionFor(reason)
                }
            }.distinctBy { it.label }

    private fun defaultRegionFor(reason: Reason): LabeledRegion? =
        when (reason.code) {
            ReasonCode.EAR_GEOMETRY_DRIFT -> LabeledRegion("EAR", BBox(0.23f, 0.24f, 0.20f, 0.20f), reason.severity)
            ReasonCode.EYE_REFLECTION_MISMATCH -> LabeledRegion("EYES", BBox(0.22f, 0.25f, 0.56f, 0.18f), reason.severity)
            ReasonCode.TEETH_ARTIFACTS,
            ReasonCode.VID_LIP_SYNC_DRIFT,
            ReasonCode.LIP_SYNC_DRIFT,
            -> LabeledRegion("MOUTH", BBox(0.36f, 0.54f, 0.30f, 0.16f), reason.severity)
            ReasonCode.IMG_DEEPFAKE_MODEL_HIGH,
            ReasonCode.VID_SPATIAL_SYNTHETIC_FRAMES,
            ReasonCode.DIFFUSION_SPECTRAL_SIG,
            ReasonCode.GAN_SPECTRAL_SIG,
            -> LabeledRegion("FACE", BBox(0.28f, 0.18f, 0.44f, 0.55f), reason.severity)
            ReasonCode.IMG_ELA_ANOMALY,
            ReasonCode.ELA_INCONSISTENT,
            -> LabeledRegion("ELA", BBox(0.16f, 0.18f, 0.68f, 0.62f), reason.severity)
            else -> null
        }

    private fun flaggedAudioRegions(
        durationMs: Long,
        reasons: List<Reason>,
        score: Float,
    ): List<FlaggedAudioRegion> {
        val temporal =
            reasons.firstNotNullOfOrNull { reason ->
                (reason.evidence as? ReasonEvidence.Temporal)?.timestampsMs?.map { timestamp ->
                    FlaggedAudioRegion(
                        startMs = (timestamp - 350L).coerceAtLeast(0L),
                        endMs = min(timestamp + 650L, durationMs),
                        severity = reason.severity,
                        reasonCode = reason.code,
                    )
                }
            }
        if (!temporal.isNullOrEmpty()) return temporal

        val reason =
            reasons.firstOrNull { it.severity == Severity.MAJOR || it.severity == Severity.CRITICAL }
                ?: reasons.firstOrNull()
                ?: return emptyList()
        val center = (durationMs * score.coerceIn(0.2f, 0.8f)).toLong()
        val span = (durationMs / 5L).coerceIn(700L, 3_000L)
        return listOf(
            FlaggedAudioRegion(
                startMs = (center - span / 2L).coerceAtLeast(0L),
                endMs = min(center + span / 2L, durationMs),
                severity = reason.severity,
                reasonCode = reason.code,
            ),
        )
    }

    private fun severityWeight(severity: Severity): Float =
        when (severity) {
            Severity.POSITIVE -> 0.18f
            Severity.NEUTRAL -> 0.35f
            Severity.MINOR -> 0.55f
            Severity.MAJOR -> 0.80f
            Severity.CRITICAL -> 1.0f
        }

    private fun triangular(value: Float): Float {
        val cycle = value - value.toInt()
        return 1f - abs(cycle * 2f - 1f)
    }
}
