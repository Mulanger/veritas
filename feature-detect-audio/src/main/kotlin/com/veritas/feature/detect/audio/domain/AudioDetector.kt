@file:Suppress("MagicNumber")

package com.veritas.feature.detect.audio.domain

import com.veritas.data.detection.ml.fusion.Calibrator
import com.veritas.domain.detection.BasicDetectorResult
import com.veritas.domain.detection.Detector
import com.veritas.domain.detection.FallbackLevel
import com.veritas.domain.detection.ForensicEvidence
import com.veritas.domain.detection.ForensicEvidenceFactory
import com.veritas.domain.detection.Reason
import com.veritas.domain.detection.ReasonCode
import com.veritas.domain.detection.ReasonEvidence
import com.veritas.domain.detection.Severity
import com.veritas.domain.detection.UncertainReason
import com.veritas.feature.detect.audio.decode.AudioDecoder
import com.veritas.feature.detect.audio.decode.PcmConverter
import com.veritas.feature.detect.audio.forensics.AudioForensicSignals
import com.veritas.feature.detect.audio.forensics.CodecFingerprint
import com.veritas.feature.detect.audio.forensics.DurationSanity
import com.veritas.feature.detect.audio.fusion.AudioFusion
import com.veritas.feature.detect.audio.model.DeepfakeAudioDetectorModel
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioDetector @Inject constructor(
    private val model: DeepfakeAudioDetectorModel,
) : Detector<AudioDetectionInput, BasicDetectorResult> {
    private val decoder = AudioDecoder()
    private val pcmConverter = PcmConverter()
    private val durationSanity = DurationSanity()
    private val codecFingerprint = CodecFingerprint()

    override suspend fun detect(input: AudioDetectionInput): BasicDetectorResult {
        val startedAt = Clock.System.now()
        val decoded = decoder.decode(input.file)
        val samples = pcmConverter.toMonoFloat(decoded)
        val modelScore = model.score(samples)
        val durationSignals = durationSanity.analyze(decoded)
        val codecPlausibility = codecFingerprint.plausibility(decoded)
        val signals = AudioForensicSignals(
            tooShort = durationSignals.tooShort,
            tooLong = durationSignals.tooLong,
            lowSampleRate = durationSignals.lowSampleRate,
            monoPlausible = durationSignals.monoPlausible,
            codecPlausibilityScore = codecPlausibility,
        )
        val fusedScore = AudioFusion.fuse(modelScore.syntheticScore, codecPlausibility)
        val confidenceInterval = Calibrator.scoreToInterval(fusedScore)
        val uncertainReasons = uncertaintyReasons(signals, fusedScore, modelScore.fallbackLevel)
        val elapsedMs = Clock.System.now().toEpochMilliseconds() - startedAt.toEpochMilliseconds()

        val reasons = reasonsFor(modelScore.syntheticScore, signals)
        return BasicDetectorResult(
            detectorId = DETECTOR_ID,
            syntheticScore = fusedScore,
            confidence = (confidenceInterval.high - confidenceInterval.low).let { 1f - it }.coerceIn(0f, 0.95f),
            reasons = reasons,
            elapsedMs = elapsedMs,
            confidenceInterval = confidenceInterval,
            subScores = mapOf(
                "wav2vec2_model" to modelScore.syntheticScore,
                "codec" to codecPlausibility,
            ),
            uncertainReasons = uncertainReasons,
            fallbackUsed = modelScore.fallbackLevel,
            forensicEvidence =
                ForensicEvidence.Audio(
                    waveform = ForensicEvidenceFactory.waveform(
                        durationMs = input.media.durationMs,
                        score = fusedScore,
                        reasons = reasons,
                    ),
                    temporalConfidence = ForensicEvidenceFactory.temporalConfidence(
                        durationMs = input.media.durationMs,
                        scores = emptyList(),
                        fallbackScore = fusedScore,
                    ),
                ),
        )
    }

    private fun uncertaintyReasons(
        signals: AudioForensicSignals,
        fusedScore: Float,
        fallbackLevel: FallbackLevel,
    ): List<UncertainReason> = buildList {
        if (signals.tooShort) add(UncertainReason.TOO_SHORT)
        if (signals.lowSampleRate) add(UncertainReason.LOW_SAMPLE_RATE)
        if (signals.tooLong) add(UncertainReason.TOO_LONG_PROCESSED_TRUNCATED)
        if (fusedScore in LOW_CONFIDENCE_MIN..LOW_CONFIDENCE_MAX) add(UncertainReason.LOW_CONFIDENCE_RANGE)
        if (fallbackLevel == FallbackLevel.CPU_XNNPACK) add(UncertainReason.CPU_FALLBACK)
    }

    private fun reasonsFor(
        wav2vec2Score: Float,
        signals: AudioForensicSignals,
    ): List<Reason> = buildList {
        if (wav2vec2Score > MODEL_HIGH_THRESHOLD) {
            add(Reason(ReasonCode.AUD_SYNTHETIC_VOICE_HIGH, 0.70f, Severity.MAJOR, ReasonEvidence.Qualitative("AI voice detection model flagged synthetic speech patterns.")))
        }
        if (signals.codecPlausibilityScore < CODEC_MISMATCH_THRESHOLD) {
            add(Reason(ReasonCode.AUD_CODEC_MISMATCH, 0.08f, Severity.MINOR, ReasonEvidence.Scalar(signals.codecPlausibilityScore, "codec_plausibility")))
        }
        if (signals.tooShort) {
            add(Reason(ReasonCode.AUD_TOO_SHORT, 0.05f, Severity.NEUTRAL, ReasonEvidence.Qualitative("Audio is too short for reliable speech analysis.")))
        }
        if (signals.lowSampleRate) {
            add(Reason(ReasonCode.AUD_LOW_QUALITY, 0.05f, Severity.NEUTRAL, ReasonEvidence.Qualitative("Sample rate is too low for reliable speech analysis.")))
        }
        if (wav2vec2Score < MODEL_NATURAL_THRESHOLD) {
            add(Reason(ReasonCode.AUD_NATURAL_PROSODY, 0.30f, Severity.POSITIVE, ReasonEvidence.Qualitative("The audio detector did not find strong synthetic speech artifacts.")))
        }
    }.ifEmpty {
        listOf(Reason(ReasonCode.CODEC_CONSISTENT, 0.20f, Severity.POSITIVE, ReasonEvidence.Qualitative("Audio codec and model signals did not indicate synthetic speech.")))
    }.sortedByDescending { it.weight }

    companion object {
        const val DETECTOR_ID = "audio_deepfake_detector_hemgg_wi8"
        private const val MODEL_HIGH_THRESHOLD = 0.70f
        private const val MODEL_NATURAL_THRESHOLD = 0.30f
        private const val CODEC_MISMATCH_THRESHOLD = 0.40f
        private const val LOW_CONFIDENCE_MIN = 0.35f
        private const val LOW_CONFIDENCE_MAX = 0.65f
    }
}
