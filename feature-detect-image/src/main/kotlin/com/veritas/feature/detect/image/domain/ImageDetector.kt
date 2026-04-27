@file:Suppress("MagicNumber")

package com.veritas.feature.detect.image.domain

import android.graphics.BitmapFactory
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
import com.veritas.feature.detect.image.forensics.ElaAnalyzer
import com.veritas.feature.detect.image.forensics.ExifAnalyzer
import com.veritas.feature.detect.image.forensics.ForensicSignals
import com.veritas.feature.detect.image.forensics.JpegQuantization
import com.veritas.feature.detect.image.fusion.ImageFusion
import com.veritas.feature.detect.image.model.DeepfakeDetectorV2Model
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageDetector @Inject constructor(
    private val model: DeepfakeDetectorV2Model,
) : Detector<ImageDetectionInput, BasicDetectorResult> {
    private val exifAnalyzer = ExifAnalyzer()
    private val elaAnalyzer = ElaAnalyzer()

    override suspend fun detect(input: ImageDetectionInput): BasicDetectorResult {
        val startedAt = Clock.System.now()
        val bitmap = requireNotNull(decodeModelBitmap(input.file.absolutePath)) {
            "Image decoder returned null for ${input.file.absolutePath}"
        }
        val modelScore = model.score(bitmap)
        bitmap.recycle()

        val exifSignals = exifAnalyzer.analyze(input.file)
        val forensicSignals = ForensicSignals(
            hasCameraMake = exifSignals.hasCameraMake,
            hasCameraModel = exifSignals.hasCameraModel,
            hasCaptureDateTime = exifSignals.hasCaptureDateTime,
            hasGps = exifSignals.hasGps,
            hasExposureMetadata = exifSignals.hasExposureMetadata,
            quantTableIsStandard = JpegQuantization.isStandardOrAbsent(input.file),
            exifCompletenessScore = exifSignals.exifCompletenessScore,
            elaAnomalyScore = elaAnalyzer.analyze(input.file),
        )
        val fusedScore = ImageFusion.fuse(modelScore.score, forensicSignals)
        val confidenceInterval = Calibrator.scoreToInterval(fusedScore)
        val uncertainReasons = uncertaintyReasons(input, fusedScore, modelScore.fallbackLevel)
        val elapsedMs = Clock.System.now().toEpochMilliseconds() - startedAt.toEpochMilliseconds()

        val reasons = reasonsFor(modelScore.score, forensicSignals, uncertainReasons)
        return BasicDetectorResult(
            detectorId = DETECTOR_ID,
            syntheticScore = fusedScore,
            confidence = (confidenceInterval.high - confidenceInterval.low).let { 1f - it }.coerceIn(0f, 0.95f),
            reasons = reasons,
            elapsedMs = elapsedMs,
            confidenceInterval = confidenceInterval,
            subScores = mapOf(
                "vit_model" to modelScore.score,
                "exif_ela" to forensicSignalScore(forensicSignals),
            ),
            uncertainReasons = uncertainReasons,
            fallbackUsed = modelScore.fallbackLevel,
            forensicEvidence =
                ForensicEvidence.Image(
                    heatmap = ForensicEvidenceFactory.imageHeatmap(
                        mediaType = input.media.mediaType,
                        syntheticScore = fusedScore,
                        reasons = reasons,
                    ),
                ),
        )
    }

    private fun decodeModelBitmap(path: String): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            },
        )
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (
            width / (sampleSize * 2) >= MAX_MODEL_DECODE_DIMENSION ||
            height / (sampleSize * 2) >= MAX_MODEL_DECODE_DIMENSION
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun uncertaintyReasons(
        input: ImageDetectionInput,
        fusedScore: Float,
        fallbackLevel: FallbackLevel,
    ): List<UncertainReason> = buildList {
        val width = input.media.widthPx
        val height = input.media.heightPx
        if ((width != null && width < MIN_RELIABLE_DIMENSION) || (height != null && height < MIN_RELIABLE_DIMENSION)) {
            add(UncertainReason.TOO_SMALL)
        }
        if (input.file.extension.equals("jpg", ignoreCase = true) && isVeryCompressed(input)) {
            add(UncertainReason.HEAVY_COMPRESSION)
        }
        if (fusedScore in LOW_CONFIDENCE_MIN..LOW_CONFIDENCE_MAX) {
            add(UncertainReason.LOW_CONFIDENCE_RANGE)
        }
        if (fallbackLevel == FallbackLevel.CPU_XNNPACK) {
            add(UncertainReason.CPU_FALLBACK)
        }
    }

    private fun isVeryCompressed(input: ImageDetectionInput): Boolean {
        val pixels = ((input.media.widthPx ?: 0) * (input.media.heightPx ?: 0)).coerceAtLeast(1)
        return input.file.length() / pixels.toFloat() < HEAVY_COMPRESSION_BYTES_PER_PIXEL
    }

    private fun reasonsFor(
        vitScore: Float,
        signals: ForensicSignals,
        uncertainReasons: List<UncertainReason>,
    ): List<Reason> = buildList {
        if (vitScore > MODEL_HIGH_THRESHOLD) {
            add(Reason(ReasonCode.IMG_DEEPFAKE_MODEL_HIGH, 0.70f, Severity.MAJOR, ReasonEvidence.Qualitative("AI detection model flagged generative image patterns.")))
        }
        if (signals.exifCompletenessScore < EXIF_MISSING_THRESHOLD) {
            add(Reason(ReasonCode.IMG_EXIF_MISSING, 0.10f, Severity.MINOR, ReasonEvidence.Qualitative("No camera metadata is present. This is weak evidence because platforms often strip EXIF.")))
        }
        if (!signals.quantTableIsStandard) {
            add(Reason(ReasonCode.IMG_EXIF_SUSPICIOUS, 0.08f, Severity.MINOR, ReasonEvidence.Qualitative("JPEG quantization tables are unusual for common camera and editing export paths.")))
        }
        if ((signals.elaAnomalyScore ?: 0f) > ELA_ANOMALY_THRESHOLD) {
            add(Reason(ReasonCode.IMG_ELA_ANOMALY, 0.07f, Severity.MINOR, ReasonEvidence.Scalar(signals.elaAnomalyScore ?: 0f, "ela_ratio")))
        }
        if (uncertainReasons.isNotEmpty()) {
            add(Reason(ReasonCode.IMG_LOW_QUALITY, 0.05f, Severity.NEUTRAL, ReasonEvidence.Qualitative("Image quality or runtime fallback reduced detector confidence.")))
        }
    }.ifEmpty {
        listOf(Reason(ReasonCode.CODEC_CONSISTENT, 0.30f, Severity.POSITIVE, ReasonEvidence.Qualitative("The image detector did not find strong synthetic signals.")))
    }.sortedByDescending { it.weight }

    private fun forensicSignalScore(signals: ForensicSignals): Float {
        val exifSignal = 1f - signals.exifCompletenessScore
        val elaSignal = signals.elaAnomalyScore ?: 0.5f
        return (0.67f * exifSignal + 0.33f * elaSignal).coerceIn(0f, 1f)
    }

    companion object {
        const val DETECTOR_ID = "image_deepfake_detector_v2"
        private const val MIN_RELIABLE_DIMENSION = 256
        private const val HEAVY_COMPRESSION_BYTES_PER_PIXEL = 0.088f
        private const val LOW_CONFIDENCE_MIN = 0.35f
        private const val LOW_CONFIDENCE_MAX = 0.65f
        private const val MODEL_HIGH_THRESHOLD = 0.70f
        private const val EXIF_MISSING_THRESHOLD = 0.30f
        private const val ELA_ANOMALY_THRESHOLD = 0.60f
        private const val MAX_MODEL_DECODE_DIMENSION = 1024
    }
}
