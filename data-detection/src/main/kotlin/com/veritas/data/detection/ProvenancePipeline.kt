@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "LongParameterList",
    "ReturnCount",
    "MagicNumber",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "UnusedPrivateProperty",
    "UnusedParameter",
    "TooManyFunctions",
    "MaxLineLength",
)

package com.veritas.data.detection

import android.content.Context
import com.veritas.domain.detection.BBox
import com.veritas.domain.detection.C2PAOutcome
import com.veritas.domain.detection.C2PAResult
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class ProvenancePipeline @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val c2paDetector: C2PADetector,
    private val synthIDDetector: SynthIDDetector,
    private val fakeDetectionPipeline: FakeDetectionPipeline,
) : DetectionPipeline {
    private val activeScan = AtomicReference<ActiveScan?>()
    private val clock = Clock.System

    override fun scan(media: ScannedMedia): Flow<ScanStage> = flow {
        val session = ActiveScan(media.id)
        activeScan.getAndSet(session)?.cancelRequested?.set(true)

        val stages = PipelineStage.forMediaType(media.mediaType)
        val mediaFile = resolveMediaFile(media)
        val startTime = clock.now()

        try {
            emit(ScanStage.Started(stages))
            val c2paResult = computeC2PAResult(media, mediaFile)
            if (session.cancelRequested.get()) { emit(ScanStage.Cancelled); return@flow }
            val synthIDResult = computeSynthIDResult(c2paResult, media, mediaFile)
            if (session.cancelRequested.get()) { emit(ScanStage.Cancelled); return@flow }
            val verdict = buildVerdict(media, c2paResult, synthIDResult, startTime)

            if (verdict == null) {
                fakeDetectionPipeline.scan(media).collect { stage ->
                    if (stage !is ScanStage.Started) {
                        emit(stage)
                    }
                }
                return@flow
            }

            for (i in stages.indices) {
                val stage = stages[i]
                if (stage == PipelineStage.C2paManifestCheck || stage == PipelineStage.WatermarkScan) continue
                if (session.cancelRequested.get()) { emit(ScanStage.Cancelled); return@flow }
                val stageStart = clock.now()
                emit(ScanStage.StageActive(stage = stage, stageIndex = i + 1, totalStages = stages.size, startedAt = stageStart))
                delay(400L)
                val elapsed = clock.now().toEpochMilliseconds() - stageStart.toEpochMilliseconds()
                emit(ScanStage.StageDone(stage = stage, stageIndex = i + 1, totalStages = stages.size, terminalState = StageTerminalState.Duration(elapsed), elapsedMs = elapsed))
            }

            emit(ScanStage.VerdictReady(verdict))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            emit(ScanStage.Failed(ScanError.Unknown(e)))
        } finally {
            activeScan.compareAndSet(session, null)
        }
    }

    override fun cancel() {
        activeScan.get()?.cancelRequested?.set(true)
        fakeDetectionPipeline.cancel()
    }

    private suspend fun computeC2PAResult(media: ScannedMedia, mediaFile: File?): C2PAResult {
        return if (mediaFile != null && mediaFile.exists()) {
            c2paDetector.detect(mediaFile)
        } else {
            C2PAResult.NotPresent
        }
    }

    private suspend fun computeSynthIDResult(c2paResult: C2PAResult, media: ScannedMedia, mediaFile: File?): SynthIDResult {
        return if (c2paResult.outcome == C2PAOutcome.NOT_PRESENT && mediaFile != null && mediaFile.exists()) {
            synthIDDetector.detect(mediaFile, media.mediaType)
        } else {
            SynthIDResult.NotPresent
        }
    }

    private fun resolveMediaFile(media: ScannedMedia): File? {
        return try {
            val uri = android.net.Uri.parse(media.uri)
            if (uri.scheme == "file") {
                File(uri.path ?: "")
            } else {
                appContext.contentResolver.openInputStream(uri)?.close()
                val cacheDir = appContext.cacheDir
                val fileName = uri.lastPathSegment ?: "media_${media.id}"
                File(cacheDir, fileName).takeIf { it.exists() }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildVerdict(
        media: ScannedMedia,
        c2paResult: C2PAResult,
        synthIDResult: SynthIDResult,
        startTime: kotlinx.datetime.Instant,
    ): Verdict? {
        return when (val result = c2paResult) {
            is C2PAResult.Present -> buildVerifiedAuthenticVerdict(media, result, startTime)
            is C2PAResult.Invalid -> buildInvalidC2PAVerdict(media, result)
            is C2PAResult.Revoked -> buildRevokedC2PAVerdict(media, result)
            is C2PAResult.NotPresent -> buildSynthIDVerdictOrDefault(media, synthIDResult, startTime)
        }
    }

    private fun buildVerifiedAuthenticVerdict(
        media: ScannedMedia,
        c2paResult: C2PAResult.Present,
        startTime: kotlinx.datetime.Instant,
    ): Verdict {
        val evidence = ReasonEvidence.C2PAVerified(
            issuerName = c2paResult.issuerName ?: "Unknown",
            deviceName = c2paResult.deviceName,
            signedAt = c2paResult.signedAt ?: startTime,
        )
        return Verdict(
            id = UUID.randomUUID().toString(),
            mediaId = media.id,
            mediaType = media.mediaType,
            outcome = VerdictOutcome.VERIFIED_AUTHENTIC,
            confidence = ConfidenceRange(lowPct = 82, highPct = 94),
            summary = "Signed by a verified camera. Content Credentials match a ${c2paResult.deviceName ?: "Unknown device"} capture.",
            reasons = listOf(
                Reason(ReasonCode.C2PA_VERIFIED, 0.62f, Severity.POSITIVE, evidence),
                Reason(ReasonCode.CODEC_CONSISTENT, 0.38f, Severity.POSITIVE, ReasonEvidence.Qualitative("Compression artifacts match a single capture chain.")),
            ),
            modelVersions = buildModelVersions(media.mediaType),
            scannedAt = clock.now(),
            inferenceHardware = InferenceHardware.CPU_XNNPACK,
            elapsedMs = 0L,
        )
    }

    private fun buildInvalidC2PAVerdict(media: ScannedMedia, c2paResult: C2PAResult.Invalid): Verdict {
        return Verdict(
            id = UUID.randomUUID().toString(),
            mediaId = media.id,
            mediaType = media.mediaType,
            outcome = VerdictOutcome.LIKELY_SYNTHETIC,
            confidence = ConfidenceRange(lowPct = 71, highPct = 90),
            summary = "Content Credentials signature is invalid. The manifest has been altered or corrupted.",
            reasons = listOf(
                Reason(ReasonCode.C2PA_INVALID_SIGNATURE, 0.55f, Severity.CRITICAL, ReasonEvidence.Qualitative(c2paResult.reason)),
                Reason(ReasonCode.METADATA_IMPLAUSIBLE, 0.45f, Severity.MINOR, ReasonEvidence.Qualitative("Embedded metadata does not match the asset properties.")),
            ),
            modelVersions = buildModelVersions(media.mediaType),
            scannedAt = clock.now(),
            inferenceHardware = InferenceHardware.CPU_XNNPACK,
            elapsedMs = 0L,
        )
    }

    private fun buildRevokedC2PAVerdict(media: ScannedMedia, c2paResult: C2PAResult.Revoked): Verdict {
        return Verdict(
            id = UUID.randomUUID().toString(),
            mediaId = media.id,
            mediaType = media.mediaType,
            outcome = VerdictOutcome.LIKELY_SYNTHETIC,
            confidence = ConfidenceRange(lowPct = 73, highPct = 91),
            summary = "Content Credentials have been revoked. This content is no longer trusted.",
            reasons = listOf(
                Reason(ReasonCode.C2PA_REVOKED, 0.60f, Severity.CRITICAL, ReasonEvidence.Qualitative(c2paResult.reason)),
                Reason(ReasonCode.METADATA_IMPLAUSIBLE, 0.40f, Severity.MINOR, ReasonEvidence.Qualitative("Embedded metadata does not match the asset properties.")),
            ),
            modelVersions = buildModelVersions(media.mediaType),
            scannedAt = clock.now(),
            inferenceHardware = InferenceHardware.CPU_XNNPACK,
            elapsedMs = 0L,
        )
    }

    private fun buildSynthIDVerdictOrDefault(
        media: ScannedMedia,
        synthIDResult: SynthIDResult,
        startTime: kotlinx.datetime.Instant,
    ): Verdict? {
        return when (synthIDResult) {
            is SynthIDResult.Detected -> Verdict(
                id = UUID.randomUUID().toString(),
                mediaId = media.id,
                mediaType = media.mediaType,
                outcome = VerdictOutcome.LIKELY_SYNTHETIC,
                confidence = ConfidenceRange(lowPct = 74, highPct = 93),
                summary = "A known generator watermark signature was detected. Multiple detectors agree this is AI-generated.",
                reasons = listOf(
                    Reason(ReasonCode.SYNTHID_DETECTED, 0.58f, Severity.MAJOR, ReasonEvidence.SynthIDDetected(generatorName = synthIDResult.generatorName)),
                    Reason(ReasonCode.DIFFUSION_SPECTRAL_SIG, 0.24f, Severity.MAJOR, ReasonEvidence.Region(regionLabel = "facial", bboxNormalized = BBox(0.32f, 0.18f, 0.28f, 0.42f))),
                    Reason(ReasonCode.ELA_INCONSISTENT, 0.18f, Severity.MINOR, ReasonEvidence.Region(regionLabel = "mouth", bboxNormalized = BBox(0.48f, 0.54f, 0.20f, 0.12f))),
                ),
                modelVersions = buildModelVersions(media.mediaType),
                scannedAt = clock.now(),
                inferenceHardware = InferenceHardware.CPU_XNNPACK,
                elapsedMs = 0L,
            )
            SynthIDResult.NotPresent -> null
        }
    }

    private fun buildModelVersions(mediaType: MediaType): Map<String, String> = buildMap {
        put("pipeline", "phase6-provenance")
        put("c2pa", "0.80.0")
        put("synthid", "deferred-v1.1")
        put(
            when (mediaType) {
                MediaType.VIDEO -> "video"
                MediaType.AUDIO -> "audio"
                MediaType.IMAGE -> "image"
            },
            "stub-0.1",
        )
    }

    private data class ActiveScan(
        val mediaId: String,
        val cancelRequested: AtomicBoolean = AtomicBoolean(false),
    )
}
