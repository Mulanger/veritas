@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "MagicNumber",
    "MaxLineLength",
    "TooGenericExceptionCaught",
    "TooManyFunctions",
)

package com.veritas.data.detection

import android.content.Context
import com.veritas.domain.detection.BBox
import com.veritas.domain.detection.ConfidenceRange
import com.veritas.domain.detection.DetectionPipeline
import com.veritas.domain.detection.InferenceHardware
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.PipelineStage
import com.veritas.domain.detection.Reason
import com.veritas.domain.detection.ReasonCode
import com.veritas.domain.detection.ReasonEvidence
import com.veritas.domain.detection.ScanError
import com.veritas.domain.detection.ScanStage
import com.veritas.domain.detection.ScannedMedia
import com.veritas.domain.detection.Severity
import com.veritas.domain.detection.StageTerminalState
import com.veritas.domain.detection.Verdict
import com.veritas.domain.detection.VerdictOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlin.math.max
import kotlin.random.Random

private const val DEFAULT_STAGE_DELAY_MIN_MS = 300
private const val DEFAULT_STAGE_DELAY_MAX_MS = 800
private const val DEFAULT_DELAY_STEP_MS = 50L

class FakeDetectionPipeline private constructor(
    private val appContext: Context?,
    private val clock: Clock,
    private val stageDelayRangeMs: IntRange,
    private val delayStepMs: Long,
) : DetectionPipeline {
    @Inject
    constructor(
        @ApplicationContext appContext: Context,
    ) : this(
        appContext = appContext,
        clock = Clock.System,
        stageDelayRangeMs = DEFAULT_STAGE_DELAY_MIN_MS..DEFAULT_STAGE_DELAY_MAX_MS,
        delayStepMs = DEFAULT_DELAY_STEP_MS,
    )

    internal constructor(
        stageDelayRangeMs: IntRange,
        delayStepMs: Long,
        clock: Clock = Clock.System,
    ) : this(
        appContext = null,
        clock = clock,
        stageDelayRangeMs = stageDelayRangeMs,
        delayStepMs = delayStepMs,
    )

    private val activeScan = AtomicReference<ActiveScan?>()

    override fun scan(media: ScannedMedia): Flow<ScanStage> =
        flow {
            val session = ActiveScan(media.id)
            activeScan.getAndSet(session)?.cancelRequested?.set(true)

            try {
                val stages = PipelineStage.forMediaType(media.mediaType)
                val plan = buildScanPlan(media = media, stages = stages)

                emit(ScanStage.Started(stages))

                stages.forEachIndexed { index, stage ->
                    if (session.cancelRequested.get()) {
                        emit(ScanStage.Cancelled)
                        return@flow
                    }

                    val startedAt = clock.now()
                    emit(
                        ScanStage.StageActive(
                            stage = stage,
                            stageIndex = index + 1,
                            totalStages = stages.size,
                            startedAt = startedAt,
                        ),
                    )

                    if (!delayForStage(plan.stageDurationsMs.getValue(stage), session)) {
                        emit(ScanStage.Cancelled)
                        return@flow
                    }

                    val elapsedMs = max(clock.now().toEpochMilliseconds() - startedAt.toEpochMilliseconds(), 1L)
                    emit(
                        ScanStage.StageDone(
                            stage = stage,
                            stageIndex = index + 1,
                            totalStages = stages.size,
                            terminalState = plan.terminalStateFor(stage = stage, elapsedMs = elapsedMs),
                            elapsedMs = elapsedMs,
                        ),
                    )
                }

                if (session.cancelRequested.get()) {
                    emit(ScanStage.Cancelled)
                    return@flow
                }

                emit(ScanStage.VerdictReady(plan.verdict))
            } catch (cancellation: CancellationException) {
                if (!session.cancelRequested.get()) {
                    throw cancellation
                }
            } catch (exception: Exception) {
                emit(ScanStage.Failed(ScanError.Unknown(exception)))
            } finally {
                activeScan.compareAndSet(session, null)
            }
        }

    override fun cancel() {
        activeScan.get()?.cancelRequested?.set(true)
    }

    private suspend fun delayForStage(
        durationMs: Long,
        session: ActiveScan,
    ): Boolean {
        var remainingMs = durationMs
        while (remainingMs > 0) {
            if (session.cancelRequested.get()) {
                return false
            }

            val nextStep = minOf(delayStepMs, remainingMs)
            delay(nextStep)
            remainingMs -= nextStep
        }
        return !session.cancelRequested.get()
    }

    private fun buildScanPlan(
        media: ScannedMedia,
        stages: List<PipelineStage>,
    ): ScanPlan {
        val fingerprint = fingerprint(media)
        val random = Random(fingerprint.seed)
        val outcome = determineOutcome(fileName = fingerprint.fileNameLowercase, random = random)
        val watermarkHit =
            outcome == VerdictOutcome.LIKELY_SYNTHETIC &&
                (
                    fingerprint.fileNameLowercase.contains("_watermark") ||
                        random.nextInt(100) < 35
                )
        val stageDurationsMs =
            stages.associateWith {
                random.nextInt(stageDelayRangeMs.first, stageDelayRangeMs.last + 1).toLong()
            }
        val reasons =
            buildReasons(
                media = media,
                outcome = outcome,
                watermarkHit = watermarkHit,
                random = random,
            )
        val verdict =
            Verdict(
                id = UUID.randomUUID().toString(),
                mediaId = media.id,
                mediaType = media.mediaType,
                outcome = outcome,
                confidence = buildConfidenceRange(outcome = outcome, random = random),
                summary = buildSummary(mediaType = media.mediaType, outcome = outcome),
                reasons = reasons.sortedByDescending(Reason::weight),
                modelVersions = buildModelVersions(media.mediaType),
                scannedAt = clock.now(),
                inferenceHardware = InferenceHardware.MIXED,
                elapsedMs = stageDurationsMs.values.sum(),
            )

        return ScanPlan(
            verdict = verdict,
            stageDurationsMs = stageDurationsMs,
            stageOutcomes = buildStageOutcomes(media, outcome, watermarkHit),
        )
    }

    private fun determineOutcome(
        fileName: String,
        random: Random,
    ): VerdictOutcome =
        when {
            fileName.contains("_authentic") && fileName.contains("_c2pa") -> VerdictOutcome.VERIFIED_AUTHENTIC
            fileName.contains("_authentic") -> VerdictOutcome.LIKELY_AUTHENTIC
            fileName.contains("_uncertain") -> VerdictOutcome.UNCERTAIN
            fileName.contains("_synthetic") -> VerdictOutcome.LIKELY_SYNTHETIC
            else ->
                when (random.nextInt(3)) {
                    0 -> VerdictOutcome.LIKELY_AUTHENTIC
                    1 -> VerdictOutcome.UNCERTAIN
                    else -> VerdictOutcome.LIKELY_SYNTHETIC
                }
        }

    private fun buildConfidenceRange(
        outcome: VerdictOutcome,
        random: Random,
    ): ConfidenceRange =
        when (outcome) {
            VerdictOutcome.VERIFIED_AUTHENTIC -> {
                val low = random.nextInt(78, 89)
                val high = random.nextInt(max(low + 6, 90), 95).coerceAtMost(94)
                ConfidenceRange(lowPct = low.coerceAtLeast(65), highPct = high)
            }

            VerdictOutcome.LIKELY_AUTHENTIC -> {
                val low = random.nextInt(70, 86)
                val high = random.nextInt(max(low + 8, 84), 95).coerceAtMost(94)
                ConfidenceRange(lowPct = low.coerceAtLeast(65), highPct = high)
            }

            VerdictOutcome.UNCERTAIN -> {
                val low = random.nextInt(32, 49)
                val high = max(random.nextInt(52, 69), low + 14)
                ConfidenceRange(lowPct = low, highPct = high)
            }

            VerdictOutcome.LIKELY_SYNTHETIC -> {
                val low = random.nextInt(75, 89).coerceAtLeast(70)
                val high = random.nextInt(max(low + 8, 88), 97).coerceAtMost(96)
                ConfidenceRange(lowPct = low, highPct = high)
            }
        }

    private fun buildSummary(
        mediaType: MediaType,
        outcome: VerdictOutcome,
    ): String =
        when (outcome) {
            VerdictOutcome.VERIFIED_AUTHENTIC ->
                "Signed by a verified camera. Content Credentials match a Sony A7 IV capture from 23 Apr 2026."

            VerdictOutcome.LIKELY_AUTHENTIC ->
                "No signs of AI generation across the active detectors. No Content Credentials are attached, so authenticity is not confirmed, but nothing looks synthetic."

            VerdictOutcome.UNCERTAIN ->
                "Heavy compression and low resolution reduce the available signal. The checks disagree, so the result stays uncertain. Don't trust or dismiss this - seek a higher-quality source."

            VerdictOutcome.LIKELY_SYNTHETIC ->
                when (mediaType) {
                    MediaType.VIDEO ->
                        "Multiple detectors agree this is AI-generated. Temporal drift and an absent physiological signal are the strongest indicators."
                    MediaType.AUDIO ->
                        "Multiple detectors agree this sounds AI-generated. Spectral artifacts and overly regular prosody are the strongest indicators."
                    MediaType.IMAGE ->
                        "Multiple detectors agree this looks AI-generated. Frequency-domain residue and inconsistent image forensics are the strongest indicators."
                }
        }

    private fun buildModelVersions(mediaType: MediaType): Map<String, String> =
        buildMap {
            put("pipeline", "phase5-fake")
            put("fusion", "stub-0.1")
            put(
                when (mediaType) {
                    MediaType.VIDEO -> "video"
                    MediaType.AUDIO -> "audio"
                    MediaType.IMAGE -> "image"
                },
                "stub-0.1",
            )
        }

    private fun buildReasons(
        media: ScannedMedia,
        outcome: VerdictOutcome,
        watermarkHit: Boolean,
        random: Random,
    ): List<Reason> {
        val now = clock.now()
        return when (outcome) {
            VerdictOutcome.VERIFIED_AUTHENTIC ->
                listOf(
                    Reason(
                        code = ReasonCode.C2PA_VERIFIED,
                        weight = 0.56f,
                        severity = Severity.POSITIVE,
                        evidence =
                            ReasonEvidence.C2PAVerified(
                                issuerName = "Sony Imaging",
                                deviceName = "Sony A7 IV",
                                signedAt = now,
                            ),
                    ),
                    Reason(
                        code = positiveSignalFor(media.mediaType),
                        weight = 0.27f,
                        severity = Severity.POSITIVE,
                        evidence = positiveEvidenceFor(media.mediaType),
                    ),
                    Reason(
                        code = ReasonCode.CODEC_CONSISTENT,
                        weight = 0.17f,
                        severity = Severity.POSITIVE,
                        evidence = ReasonEvidence.Qualitative("Compression artifacts match a single capture and encode chain."),
                    ),
                )

            VerdictOutcome.LIKELY_AUTHENTIC ->
                listOf(
                    Reason(
                        code = positiveSignalFor(media.mediaType),
                        weight = 0.58f,
                        severity = Severity.POSITIVE,
                        evidence = positiveEvidenceFor(media.mediaType),
                    ),
                    Reason(
                        code = ReasonCode.CODEC_CONSISTENT,
                        weight = 0.42f,
                        severity = Severity.POSITIVE,
                        evidence = ReasonEvidence.Qualitative("Codec behavior stays consistent across the sampled content."),
                    ),
                )

            VerdictOutcome.UNCERTAIN ->
                listOf(
                    Reason(
                        code = ReasonCode.COMPRESSION_HEAVY,
                        weight = 0.37f,
                        severity = Severity.MINOR,
                        evidence = ReasonEvidence.Scalar(measurement = 3f, unit = "re-encodes"),
                    ),
                    Reason(
                        code = ReasonCode.DETECTOR_DISAGREEMENT,
                        weight = 0.34f,
                        severity = Severity.NEUTRAL,
                        evidence = ReasonEvidence.Qualitative("The active checks disagree on the same media."),
                    ),
                    Reason(
                        code = ReasonCode.LOW_RESOLUTION,
                        weight = 0.29f,
                        severity = Severity.MINOR,
                        evidence = ReasonEvidence.Scalar(measurement = 144f + random.nextInt(0, 97), unit = "p"),
                    ),
                )

            VerdictOutcome.LIKELY_SYNTHETIC ->
                when (media.mediaType) {
                    MediaType.VIDEO ->
                        buildList {
                            add(
                                Reason(
                                    code = ReasonCode.TEMPORAL_INCONSISTENCY,
                                    weight = 0.37f,
                                    severity = Severity.MAJOR,
                                    evidence = ReasonEvidence.Temporal(listOf(4_200, 9_100, 17_100)),
                                ),
                            )
                            add(
                                Reason(
                                    code = ReasonCode.RPPG_ABSENT,
                                    weight = 0.32f,
                                    severity = Severity.MAJOR,
                                    evidence = ReasonEvidence.Scalar(measurement = 0f, unit = "Hz"),
                                ),
                            )
                            add(
                                Reason(
                                    code = if (watermarkHit) ReasonCode.SYNTHID_DETECTED else ReasonCode.DIFFUSION_SPECTRAL_SIG,
                                    weight = 0.18f,
                                    severity = Severity.MAJOR,
                                    evidence =
                                        if (watermarkHit) {
                                            ReasonEvidence.Qualitative("A known generator watermark signature was detected.")
                                        } else {
                                            ReasonEvidence.Region(
                                                regionLabel = "facial",
                                                bboxNormalized = BBox(0.32f, 0.18f, 0.28f, 0.42f),
                                            )
                                        },
                                ),
                            )
                            add(
                                Reason(
                                    code = ReasonCode.EAR_GEOMETRY_DRIFT,
                                    weight = 0.13f,
                                    severity = Severity.MINOR,
                                    evidence = ReasonEvidence.Temporal(listOf(3_800, 8_600, 13_400)),
                                ),
                            )
                        }

                    MediaType.AUDIO ->
                        listOf(
                            Reason(
                                code = ReasonCode.AUDIO_SPECTRAL_NEURAL,
                                weight = 0.41f,
                                severity = Severity.MAJOR,
                                evidence = ReasonEvidence.Temporal(listOf(800, 3_100, 6_400)),
                            ),
                            Reason(
                                code = ReasonCode.AUDIO_PROSODY_UNNATURAL,
                                weight = 0.34f,
                                severity = Severity.MAJOR,
                                evidence = ReasonEvidence.Scalar(measurement = 0.91f, unit = "regularity"),
                            ),
                            Reason(
                                code = ReasonCode.AUDIO_CODEC_MISMATCH,
                                weight = 0.25f,
                                severity = Severity.MINOR,
                                evidence = ReasonEvidence.Qualitative("The codec fingerprint does not match a natural phone-recording path."),
                            ),
                        )

                    MediaType.IMAGE ->
                        listOf(
                            Reason(
                                code = ReasonCode.DIFFUSION_SPECTRAL_SIG,
                                weight = 0.39f,
                                severity = Severity.MAJOR,
                                evidence =
                                    ReasonEvidence.Region(
                                        regionLabel = "facial",
                                        bboxNormalized = BBox(0.30f, 0.22f, 0.32f, 0.40f),
                                    ),
                            ),
                            Reason(
                                code = ReasonCode.ELA_INCONSISTENT,
                                weight = 0.33f,
                                severity = Severity.MAJOR,
                                evidence =
                                    ReasonEvidence.Region(
                                        regionLabel = "mouth",
                                        bboxNormalized = BBox(0.48f, 0.54f, 0.20f, 0.12f),
                                    ),
                            ),
                            Reason(
                                code = ReasonCode.METADATA_IMPLAUSIBLE,
                                weight = 0.28f,
                                severity = Severity.MINOR,
                                evidence = ReasonEvidence.Qualitative("Embedded metadata does not match the image properties."),
                            ),
                        )
                }
        }
    }

    private fun positiveSignalFor(mediaType: MediaType): ReasonCode =
        when (mediaType) {
            MediaType.VIDEO -> ReasonCode.RPPG_NATURAL
            MediaType.AUDIO -> ReasonCode.CODEC_CONSISTENT
            MediaType.IMAGE -> ReasonCode.CODEC_CONSISTENT
        }

    private fun positiveEvidenceFor(mediaType: MediaType): ReasonEvidence =
        when (mediaType) {
            MediaType.VIDEO -> ReasonEvidence.Scalar(measurement = 1.1f, unit = "Hz")
            MediaType.AUDIO -> ReasonEvidence.Qualitative("The codec profile matches a natural capture chain.")
            MediaType.IMAGE -> ReasonEvidence.Qualitative("Compression behavior matches a single export path.")
        }

    private fun buildStageOutcomes(
        media: ScannedMedia,
        outcome: VerdictOutcome,
        watermarkHit: Boolean,
    ): Map<PipelineStage, FakeTerminalState> =
        when (media.mediaType) {
            MediaType.VIDEO ->
                when (outcome) {
                    VerdictOutcome.VERIFIED_AUTHENTIC ->
                        mapOf(
                            PipelineStage.C2paManifestCheck to FakeTerminalState.Verified,
                            PipelineStage.WatermarkScan to FakeTerminalState.Skipped,
                            PipelineStage.TemporalConsistency to FakeTerminalState.Skipped,
                            PipelineStage.SpatialArtifactModel to FakeTerminalState.Skipped,
                            PipelineStage.FacialPhysiologicalCheck to FakeTerminalState.Skipped,
                        )

                    VerdictOutcome.LIKELY_AUTHENTIC ->
                        mapOf(
                            PipelineStage.C2paManifestCheck to FakeTerminalState.None,
                            PipelineStage.WatermarkScan to FakeTerminalState.None,
                            PipelineStage.TemporalConsistency to FakeTerminalState.Duration,
                            PipelineStage.SpatialArtifactModel to FakeTerminalState.Duration,
                            PipelineStage.FacialPhysiologicalCheck to FakeTerminalState.Verified,
                        )

                    VerdictOutcome.UNCERTAIN ->
                        mapOf(
                            PipelineStage.C2paManifestCheck to FakeTerminalState.None,
                            PipelineStage.WatermarkScan to FakeTerminalState.None,
                            PipelineStage.TemporalConsistency to FakeTerminalState.Flagged,
                            PipelineStage.SpatialArtifactModel to FakeTerminalState.Duration,
                            PipelineStage.FacialPhysiologicalCheck to FakeTerminalState.Skipped,
                        )

                    VerdictOutcome.LIKELY_SYNTHETIC ->
                        mapOf(
                            PipelineStage.C2paManifestCheck to FakeTerminalState.None,
                            PipelineStage.WatermarkScan to if (watermarkHit) FakeTerminalState.Flagged else FakeTerminalState.None,
                            PipelineStage.TemporalConsistency to FakeTerminalState.Flagged,
                            PipelineStage.SpatialArtifactModel to FakeTerminalState.Flagged,
                            PipelineStage.FacialPhysiologicalCheck to FakeTerminalState.Flagged,
                        )
                }

            MediaType.AUDIO ->
                when (outcome) {
                    VerdictOutcome.VERIFIED_AUTHENTIC ->
                        mapOf(
                            PipelineStage.C2paManifestCheck to FakeTerminalState.Verified,
                            PipelineStage.WatermarkScan to FakeTerminalState.Skipped,
                            PipelineStage.SpectralAnalysis to FakeTerminalState.Skipped,
                            PipelineStage.ProsodyPacingCheck to FakeTerminalState.Skipped,
                            PipelineStage.CodecFingerprintCheck to FakeTerminalState.Skipped,
                        )

                    VerdictOutcome.LIKELY_AUTHENTIC ->
                        mapOf(
                            PipelineStage.C2paManifestCheck to FakeTerminalState.None,
                            PipelineStage.WatermarkScan to FakeTerminalState.None,
                            PipelineStage.SpectralAnalysis to FakeTerminalState.Duration,
                            PipelineStage.ProsodyPacingCheck to FakeTerminalState.Duration,
                            PipelineStage.CodecFingerprintCheck to FakeTerminalState.Verified,
                        )

                    VerdictOutcome.UNCERTAIN ->
                        mapOf(
                            PipelineStage.C2paManifestCheck to FakeTerminalState.None,
                            PipelineStage.WatermarkScan to FakeTerminalState.None,
                            PipelineStage.SpectralAnalysis to FakeTerminalState.Flagged,
                            PipelineStage.ProsodyPacingCheck to FakeTerminalState.Duration,
                            PipelineStage.CodecFingerprintCheck to FakeTerminalState.Flagged,
                        )

                    VerdictOutcome.LIKELY_SYNTHETIC ->
                        mapOf(
                            PipelineStage.C2paManifestCheck to FakeTerminalState.None,
                            PipelineStage.WatermarkScan to if (watermarkHit) FakeTerminalState.Flagged else FakeTerminalState.None,
                            PipelineStage.SpectralAnalysis to FakeTerminalState.Flagged,
                            PipelineStage.ProsodyPacingCheck to FakeTerminalState.Flagged,
                            PipelineStage.CodecFingerprintCheck to FakeTerminalState.Flagged,
                        )
                }

            MediaType.IMAGE ->
                when (outcome) {
                    VerdictOutcome.VERIFIED_AUTHENTIC ->
                        mapOf(
                            PipelineStage.C2paManifestCheck to FakeTerminalState.Verified,
                            PipelineStage.WatermarkScan to FakeTerminalState.Skipped,
                            PipelineStage.FrequencyDomainAnalysis to FakeTerminalState.Skipped,
                            PipelineStage.SpatialArtifactModel to FakeTerminalState.Skipped,
                            PipelineStage.MetadataForensics to FakeTerminalState.Skipped,
                        )

                    VerdictOutcome.LIKELY_AUTHENTIC ->
                        mapOf(
                            PipelineStage.C2paManifestCheck to FakeTerminalState.None,
                            PipelineStage.WatermarkScan to FakeTerminalState.None,
                            PipelineStage.FrequencyDomainAnalysis to FakeTerminalState.Duration,
                            PipelineStage.SpatialArtifactModel to FakeTerminalState.Duration,
                            PipelineStage.MetadataForensics to FakeTerminalState.Verified,
                        )

                    VerdictOutcome.UNCERTAIN ->
                        mapOf(
                            PipelineStage.C2paManifestCheck to FakeTerminalState.None,
                            PipelineStage.WatermarkScan to FakeTerminalState.None,
                            PipelineStage.FrequencyDomainAnalysis to FakeTerminalState.Flagged,
                            PipelineStage.SpatialArtifactModel to FakeTerminalState.Duration,
                            PipelineStage.MetadataForensics to FakeTerminalState.Flagged,
                        )

                    VerdictOutcome.LIKELY_SYNTHETIC ->
                        mapOf(
                            PipelineStage.C2paManifestCheck to FakeTerminalState.None,
                            PipelineStage.WatermarkScan to if (watermarkHit) FakeTerminalState.Flagged else FakeTerminalState.None,
                            PipelineStage.FrequencyDomainAnalysis to FakeTerminalState.Flagged,
                            PipelineStage.SpatialArtifactModel to FakeTerminalState.Flagged,
                            PipelineStage.MetadataForensics to FakeTerminalState.Flagged,
                        )
                }
        }

    private fun fingerprint(media: ScannedMedia): MediaFingerprint {
        val uri = URI(media.uri)
        val fileName = (uri.path?.substringAfterLast('/') ?: "scan_${media.id}").lowercase(Locale.US)
        val digestBytes = sha256(uri)?.takeIf { it.isNotEmpty() } ?: media.uri.toByteArray()
        val seed =
            digestBytes
                .take(4)
                .fold(17) { acc, byte -> (acc * 31) + byte.toInt() }
                .let { if (it == 0) 17 else it }

        return MediaFingerprint(
            fileNameLowercase = fileName,
            seed = seed,
        )
    }

    private fun sha256(uri: URI): ByteArray? =
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            when (uri.scheme) {
                "file" -> {
                    val file = File(requireNotNull(uri.path))
                    file.inputStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) {
                                break
                            }
                            digest.update(buffer, 0, read)
                        }
                    }
                }

                "content" -> {
                    appContext?.contentResolver?.openInputStream(android.net.Uri.parse(uri.toString()))?.use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) {
                                break
                            }
                            digest.update(buffer, 0, read)
                        }
                    } ?: return null
                }

                else -> digest.update(uri.toString().toByteArray())
            }

            digest.digest()
        }.getOrNull()

    private data class ActiveScan(
        val mediaId: String,
        val cancelRequested: AtomicBoolean = AtomicBoolean(false),
    )

    private data class MediaFingerprint(
        val fileNameLowercase: String,
        val seed: Int,
    )

    private data class ScanPlan(
        val verdict: Verdict,
        val stageDurationsMs: Map<PipelineStage, Long>,
        val stageOutcomes: Map<PipelineStage, FakeTerminalState>,
    ) {
        fun terminalStateFor(
            stage: PipelineStage,
            elapsedMs: Long,
        ): StageTerminalState =
            when (stageOutcomes.getValue(stage)) {
                FakeTerminalState.None -> StageTerminalState.None
                FakeTerminalState.Verified -> StageTerminalState.Verified
                FakeTerminalState.Flagged -> StageTerminalState.Flagged
                FakeTerminalState.Skipped -> StageTerminalState.Skipped
                FakeTerminalState.Duration -> StageTerminalState.Duration(elapsedMs)
            }
    }

    private enum class FakeTerminalState {
        None,
        Verified,
        Flagged,
        Skipped,
        Duration,
    }
}
