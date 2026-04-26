@file:Suppress("MagicNumber")

package com.veritas.feature.detect.video

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.data.detection.ml.inference.RunnerFactory
import com.veritas.data.detection.ml.runtime.LiteRtRuntime
import com.veritas.domain.detection.BasicDetectorResult
import com.veritas.domain.detection.MediaSource
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.ScannedMedia
import com.veritas.feature.detect.image.model.DeepfakeDetectorV2Model
import com.veritas.feature.detect.video.decode.MediaCodecFrameExtractor
import com.veritas.feature.detect.video.domain.VideoDetectionInput
import com.veritas.feature.detect.video.domain.VideoDetector
import com.veritas.feature.detect.video.domain.VideoStageTiming
import com.veritas.feature.detect.video.face.FaceConsistencyAnalyzer
import com.veritas.feature.detect.video.face.FaceDetectorWrapper
import com.veritas.feature.detect.video.temporal.MoViNetA0Streaming
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase9GoldenSetEvalTest {
    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun phase9GoldenSet_runsOnPhysicalDeviceAndWritesMetrics() = runBlocking {
        val manifest = readManifest()
        assertEquals(EXPECTED_TOTAL, manifest.size)

        val runnerFactory = RunnerFactory(appContext = appContext, liteRtRuntime = LiteRtRuntime(appContext))
        val spatialModel = DeepfakeDetectorV2Model(runnerFactory)
        val detector = VideoDetector(
            frameExtractor = MediaCodecFrameExtractor(),
            spatialModel = spatialModel,
            temporalModel = MoViNetA0Streaming(runnerFactory),
            faceAnalyzer = FaceConsistencyAnalyzer(FaceDetectorWrapper(appContext), spatialModel),
        )

        val outcomes = manifest.mapIndexed { index, row ->
            val file = copyGoldenAssetToCache(row.filename)
            val result = detector.detect(
                VideoDetectionInput(
                    media = row.toScannedMedia(index, file),
                    file = file,
                ),
            )
            EvalOutcome.from(row, result, VideoDetector.lastTiming).also { outcome ->
                if ((index + 1) % LOG_EVERY_N == 0 || index == manifest.lastIndex) {
                    Log.i(TAG, "evaluated=${index + 1}/${manifest.size}, latest=${outcome.logSummary()}")
                }
            }
        }

        val metrics = EvalMetrics.from(outcomes)
        writeResults(metrics.toJson())
        Log.i(TAG, metrics.logSummary())

        assertTrue("binary accuracy must be > 65%, actual=${metrics.binaryAccuracy}", metrics.binaryAccuracy > 0.65)
        assertTrue("binary FPR must be <= 15%, actual=${metrics.falsePositiveRate}", metrics.falsePositiveRate <= 0.15)
        assertTrue("pipeline accuracy should be >= 70%, actual=${metrics.pipelineAccuracy}", metrics.pipelineAccuracy >= 0.70)
        assertTrue("uncertain rate must be >= 5%, actual=${metrics.uncertainRate}", metrics.uncertainRate >= 0.05)
        assertTrue("uncertain rate must be <= 25%, actual=${metrics.uncertainRate}", metrics.uncertainRate <= 0.25)
        assertTrue("p95 latency must be <= 4000ms, actual=${metrics.p95LatencyMs}", metrics.p95LatencyMs <= 4_000L)
    }

    private fun readManifest(): List<ManifestRow> =
        testContext.assets.open("$GOLDEN_ASSET_ROOT/MANIFEST.csv").bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map { line ->
                val parts = line.split(',', limit = 10)
                require(parts.size == 10) { "Malformed manifest row: $line" }
                ManifestRow(
                    filename = parts[0],
                    label = ExpectedLabel.from(parts[1]),
                    sourceDataset = parts[2],
                    generatorFamily = parts[3],
                    codec = parts[4],
                    durationMs = parts[5].toLong(),
                    width = parts[6].toInt(),
                    height = parts[7].toInt(),
                )
            }.toList()
        }

    private fun copyGoldenAssetToCache(relativePath: String): File {
        val dest = File(appContext.cacheDir, "phase9_golden/$relativePath")
        if (dest.exists()) return dest
        dest.parentFile?.mkdirs()
        testContext.assets.open("$GOLDEN_ASSET_ROOT/$relativePath").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    private fun writeResults(json: String) {
        val outputFiles = mutableListOf<File>()
        val additionalOutputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        if (!additionalOutputDir.isNullOrBlank()) {
            outputFiles += File(additionalOutputDir, RESULTS_FILE_NAME)
        }
        outputFiles += File(appContext.filesDir, RESULTS_FILE_NAME)
        outputFiles.forEach { file ->
            file.parentFile?.mkdirs()
            file.writeText(json)
            Log.i(TAG, "phase_9_eval_results=${file.absolutePath}")
        }
    }

    private data class ManifestRow(
        val filename: String,
        val label: ExpectedLabel,
        val sourceDataset: String,
        val generatorFamily: String,
        val codec: String,
        val durationMs: Long,
        val width: Int,
        val height: Int,
    ) {
        val durationBucket: String
            get() = when {
                durationMs < 5_000L -> "0-5s"
                durationMs < 10_000L -> "5-10s"
                else -> "10s+"
            }

        fun toScannedMedia(index: Int, file: File): ScannedMedia =
            ScannedMedia(
                id = "phase9-golden-$index",
                uri = file.toURI().toString(),
                mediaType = MediaType.VIDEO,
                mimeType = mimeTypeFor(file),
                sizeBytes = file.length(),
                durationMs = durationMs,
                widthPx = width,
                heightPx = height,
                source = MediaSource.FilePicker,
                ingestedAt = Clock.System.now(),
            )
    }

    private enum class ExpectedLabel(val jsonValue: String) {
        REAL("real"),
        SYNTHETIC("synthetic"),
        ;

        companion object {
            fun from(value: String): ExpectedLabel =
                entries.first { it.jsonValue == value.lowercase(Locale.US) }
        }
    }

    private enum class Prediction {
        REAL,
        SYNTHETIC,
    }

    private data class EvalOutcome(
        val row: ManifestRow,
        val score: Float,
        val spatialOnlyScore: Float,
        val fullPrediction: Prediction,
        val spatialOnlyPrediction: Prediction,
        val pipelinePrediction: PipelinePrediction,
        val elapsedMs: Long,
        val fallback: String,
        val temporalFallback: String,
        val faceFallback: String,
        val durationBucket: String,
        val extractionMs: Long,
        val extractionShare: Double,
        val uncertainReasons: List<String>,
    ) {
        val fullCorrect: Boolean =
            (row.label == ExpectedLabel.REAL && fullPrediction == Prediction.REAL) ||
                (row.label == ExpectedLabel.SYNTHETIC && fullPrediction == Prediction.SYNTHETIC)

        val pipelineCorrect: Boolean =
            (row.label == ExpectedLabel.REAL && pipelinePrediction == PipelinePrediction.REAL) ||
                (row.label == ExpectedLabel.SYNTHETIC && pipelinePrediction == PipelinePrediction.SYNTHETIC)

        val spatialOnlyCorrect: Boolean =
            (row.label == ExpectedLabel.REAL && spatialOnlyPrediction == Prediction.REAL) ||
                (row.label == ExpectedLabel.SYNTHETIC && spatialOnlyPrediction == Prediction.SYNTHETIC)

        fun logSummary(): String =
            "label=${row.label.jsonValue}, generator=${row.generatorFamily}, score=$score, spatial=$spatialOnlyScore, " +
                "elapsedMs=$elapsedMs, extractionMs=$extractionMs, temporalFallback=$temporalFallback, faceFallback=$faceFallback"

        companion object {
            fun from(row: ManifestRow, result: BasicDetectorResult, timing: VideoStageTiming?): EvalOutcome {
                val spatialOnlyScore = result.subScores.getValue("spatial_vit")
                val extractionMs = timing?.extractionMs ?: 0L
                return EvalOutcome(
                    row = row,
                    score = result.syntheticScore,
                    spatialOnlyScore = spatialOnlyScore,
                    fullPrediction = if (result.syntheticScore >= SYNTHETIC_THRESHOLD) Prediction.SYNTHETIC else Prediction.REAL,
                    spatialOnlyPrediction = if (spatialOnlyScore >= SYNTHETIC_THRESHOLD) Prediction.SYNTHETIC else Prediction.REAL,
                    pipelinePrediction = pipelinePredictionFor(result),
                    elapsedMs = result.elapsedMs,
                    fallback = result.fallbackUsed.name,
                    temporalFallback = timing?.temporalFallback?.name ?: "UNKNOWN",
                    faceFallback = timing?.faceFallback?.name ?: "UNKNOWN",
                    durationBucket = row.durationBucket,
                    extractionMs = extractionMs,
                    extractionShare = if (result.elapsedMs <= 0L) 0.0 else extractionMs.toDouble() / result.elapsedMs.toDouble(),
                    uncertainReasons = result.uncertainReasons.map { it.name },
                )
            }

            private fun pipelinePredictionFor(result: BasicDetectorResult): PipelinePrediction {
                val interval = result.confidenceInterval
                return if (result.uncertainReasons.any { it.name in TERMINAL_UNCERTAIN_REASONS } ||
                    (interval.low < SYNTHETIC_THRESHOLD && interval.high > SYNTHETIC_THRESHOLD)
                ) {
                    PipelinePrediction.UNCERTAIN
                } else if (result.syntheticScore >= SYNTHETIC_THRESHOLD) {
                    PipelinePrediction.SYNTHETIC
                } else {
                    PipelinePrediction.REAL
                }
            }
        }
    }

    private data class EvalMetrics(
        val total: Int,
        val binaryAccuracy: Double,
        val pipelineAccuracy: Double,
        val spatialOnlyAccuracy: Double,
        val falsePositiveRate: Double,
        val falseNegativeRate: Double,
        val uncertainRate: Double,
        val p50LatencyMs: Long,
        val p95LatencyMs: Long,
        val p50ExtractionMs: Long,
        val p95ExtractionMs: Long,
        val latencyByDurationBucket: Map<String, LatencyBucket>,
        val fallbackCounts: Map<String, Int>,
        val temporalFallbackCounts: Map<String, Int>,
        val faceFallbackCounts: Map<String, Int>,
        val uncertainReasonCounts: Map<String, Int>,
        val perGenerator: Map<String, EvalBucket>,
    ) {
        fun logSummary(): String =
            "total=$total, fullAccuracy=$binaryAccuracy, spatialOnlyAccuracy=$spatialOnlyAccuracy, " +
                "pipelineAccuracy=$pipelineAccuracy, fpr=$falsePositiveRate, fnr=$falseNegativeRate, " +
                "uncertainRate=$uncertainRate, p50Ms=$p50LatencyMs, p95Ms=$p95LatencyMs"

        fun toJson(): String =
            buildString {
                appendLine("{")
                appendLine("  \"generatedAt\": \"${Clock.System.now()}\",")
                appendLine("  \"dataset\": \"phase7_image_to_video_static\",")
                appendLine("  \"decisionPolicy\": {")
                appendLine("    \"syntheticThreshold\": $SYNTHETIC_THRESHOLD,")
                appendLine("    \"goldenSetIsStaticImageDerived\": true")
                appendLine("  },")
                appendLine("  \"overall\": {")
                appendLine("    \"total\": $total,")
                appendLine("    \"binaryAccuracy\": ${binaryAccuracy.jsonNumber()},")
                appendLine("    \"pipelineAccuracy\": ${pipelineAccuracy.jsonNumber()},")
                appendLine("    \"spatialOnlyAccuracy\": ${spatialOnlyAccuracy.jsonNumber()},")
                appendLine("    \"fullMinusSpatialAccuracy\": ${(binaryAccuracy - spatialOnlyAccuracy).jsonNumber()},")
                appendLine("    \"falsePositiveRate\": ${falsePositiveRate.jsonNumber()},")
                appendLine("    \"falseNegativeRate\": ${falseNegativeRate.jsonNumber()},")
                appendLine("    \"uncertainRate\": ${uncertainRate.jsonNumber()},")
                appendLine("    \"p50LatencyMs\": $p50LatencyMs,")
                appendLine("    \"p95LatencyMs\": $p95LatencyMs,")
                appendLine("    \"p50ExtractionMs\": $p50ExtractionMs,")
                appendLine("    \"p95ExtractionMs\": $p95ExtractionMs")
                appendLine("  },")
                appendCountObject("fallbackCounts", fallbackCounts, trailingComma = true)
                appendCountObject("temporalFallbackCounts", temporalFallbackCounts, trailingComma = true)
                appendCountObject("faceFallbackCounts", faceFallbackCounts, trailingComma = true)
                appendCountObject("uncertainReasonCounts", uncertainReasonCounts, trailingComma = true)
                appendLine("  \"latencyByDurationBucket\": {")
                latencyByDurationBucket.entries.sortedBy { it.key }.forEachIndexed { index, (bucket, value) ->
                    appendLine("    \"${bucket.jsonEscaped()}\": ${value.toJson()}${if (index == latencyByDurationBucket.size - 1) "" else ","}")
                }
                appendLine("  },")
                appendLine("  \"perGenerator\": {")
                perGenerator.entries.sortedBy { it.key }.forEachIndexed { index, (generator, bucket) ->
                    appendLine("    \"${generator.jsonEscaped()}\": ${bucket.toJson()}${if (index == perGenerator.size - 1) "" else ","}")
                }
                appendLine("  }")
                appendLine("}")
            }

        companion object {
            fun from(outcomes: List<EvalOutcome>): EvalMetrics {
                val real = outcomes.filter { it.row.label == ExpectedLabel.REAL }
                val synthetic = outcomes.filter { it.row.label == ExpectedLabel.SYNTHETIC }
                val perGenerator = outcomes.groupBy { it.row.generatorFamily }.mapValues { (_, group) -> EvalBucket.from(group) }
                return EvalMetrics(
                    total = outcomes.size,
                    binaryAccuracy = outcomes.count { it.fullCorrect }.rate(outcomes.size),
                    pipelineAccuracy = outcomes.count { it.pipelineCorrect }.rate(outcomes.size),
                    spatialOnlyAccuracy = outcomes.count { it.spatialOnlyCorrect }.rate(outcomes.size),
                    falsePositiveRate = real.count { it.fullPrediction == Prediction.SYNTHETIC }.rate(real.size),
                    falseNegativeRate = synthetic.count { it.fullPrediction == Prediction.REAL }.rate(synthetic.size),
                    uncertainRate = outcomes.count { it.pipelinePrediction == PipelinePrediction.UNCERTAIN }.rate(outcomes.size),
                    p50LatencyMs = outcomes.map { it.elapsedMs }.percentile(0.50),
                    p95LatencyMs = outcomes.map { it.elapsedMs }.percentile(0.95),
                    p50ExtractionMs = outcomes.map { it.extractionMs }.percentile(0.50),
                    p95ExtractionMs = outcomes.map { it.extractionMs }.percentile(0.95),
                    latencyByDurationBucket = outcomes.groupBy { it.durationBucket }.mapValues { (_, group) -> LatencyBucket.from(group) },
                    fallbackCounts = outcomes.groupingBy { it.fallback }.eachCount(),
                    temporalFallbackCounts = outcomes.groupingBy { it.temporalFallback }.eachCount(),
                    faceFallbackCounts = outcomes.groupingBy { it.faceFallback }.eachCount(),
                    uncertainReasonCounts = outcomes.flatMap { it.uncertainReasons }.groupingBy { it }.eachCount(),
                    perGenerator = perGenerator,
                )
            }
        }
    }

    private data class EvalBucket(
        val total: Int,
        val binaryAccuracy: Double,
        val pipelineAccuracy: Double,
        val falsePositiveRate: Double,
        val falseNegativeRate: Double,
        val uncertainRate: Double,
        val p95LatencyMs: Long,
    ) {
        fun toJson(): String =
            "{ \"total\": $total, " +
                "\"binaryAccuracy\": ${binaryAccuracy.jsonNumber()}, " +
                "\"pipelineAccuracy\": ${pipelineAccuracy.jsonNumber()}, " +
                "\"falsePositiveRate\": ${falsePositiveRate.jsonNumber()}, " +
                "\"falseNegativeRate\": ${falseNegativeRate.jsonNumber()}, " +
                "\"uncertainRate\": ${uncertainRate.jsonNumber()}, " +
                "\"p95LatencyMs\": $p95LatencyMs }"

        companion object {
            fun from(outcomes: List<EvalOutcome>): EvalBucket {
                val real = outcomes.filter { it.row.label == ExpectedLabel.REAL }
                val synthetic = outcomes.filter { it.row.label == ExpectedLabel.SYNTHETIC }
                return EvalBucket(
                    total = outcomes.size,
                    binaryAccuracy = outcomes.count { it.fullCorrect }.rate(outcomes.size),
                    pipelineAccuracy = outcomes.count { it.pipelineCorrect }.rate(outcomes.size),
                    falsePositiveRate = real.count { it.fullPrediction == Prediction.SYNTHETIC }.rate(real.size),
                    falseNegativeRate = synthetic.count { it.fullPrediction == Prediction.REAL }.rate(synthetic.size),
                    uncertainRate = outcomes.count { it.uncertainReasons.isNotEmpty() }.rate(outcomes.size),
                    p95LatencyMs = outcomes.map { it.elapsedMs }.percentile(0.95),
                )
            }
        }
    }

    private data class LatencyBucket(
        val total: Int,
        val p50LatencyMs: Long,
        val p95LatencyMs: Long,
        val p50ExtractionMs: Long,
        val p95ExtractionMs: Long,
        val meanExtractionShare: Double,
    ) {
        fun toJson(): String =
            "{ \"total\": $total, " +
                "\"p50LatencyMs\": $p50LatencyMs, " +
                "\"p95LatencyMs\": $p95LatencyMs, " +
                "\"p50ExtractionMs\": $p50ExtractionMs, " +
                "\"p95ExtractionMs\": $p95ExtractionMs, " +
                "\"meanExtractionShare\": ${meanExtractionShare.jsonNumber()} }"

        companion object {
            fun from(outcomes: List<EvalOutcome>): LatencyBucket =
                LatencyBucket(
                    total = outcomes.size,
                    p50LatencyMs = outcomes.map { it.elapsedMs }.percentile(0.50),
                    p95LatencyMs = outcomes.map { it.elapsedMs }.percentile(0.95),
                    p50ExtractionMs = outcomes.map { it.extractionMs }.percentile(0.50),
                    p95ExtractionMs = outcomes.map { it.extractionMs }.percentile(0.95),
                    meanExtractionShare = outcomes.map { it.extractionShare }.average(),
                )
        }
    }

    private companion object {
        private const val TAG = "Phase9GoldenSetEval"
        private const val GOLDEN_ASSET_ROOT = "golden-video"
        private const val RESULTS_FILE_NAME = "phase_9_eval_results.json"
        private const val EXPECTED_TOTAL = 200
        private const val LOG_EVERY_N = 10
        private const val SYNTHETIC_THRESHOLD = 0.50f
        private val TERMINAL_UNCERTAIN_REASONS = setOf(
            "VID_TOO_SHORT",
            "VID_LOW_RESOLUTION",
            "VID_HEAVY_COMPRESSION",
            "VID_INSUFFICIENT_FRAMES",
            "VID_DECODE_FAILED",
            "LOW_CONFIDENCE_RANGE",
        )

        private fun mimeTypeFor(file: File): String =
            when (file.extension.lowercase(Locale.US)) {
                "mp4" -> "video/mp4"
                "mov" -> "video/quicktime"
                "webm" -> "video/webm"
                else -> "application/octet-stream"
            }

        private fun Int.rate(denominator: Int): Double =
            if (denominator == 0) 0.0 else toDouble() / denominator.toDouble()

        private fun List<Long>.percentile(p: Double): Long {
            if (isEmpty()) return 0L
            val sorted = sorted()
            val index = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)
            return sorted[index]
        }

        private fun Double.jsonNumber(): String = String.format(Locale.US, "%.6f", this)

        private fun String.jsonEscaped(): String =
            replace("\\", "\\\\").replace("\"", "\\\"")

        private fun StringBuilder.appendCountObject(
            name: String,
            counts: Map<String, Int>,
            trailingComma: Boolean,
        ) {
            appendLine("  \"$name\": {")
            counts.entries.sortedBy { it.key }.forEachIndexed { index, (key, value) ->
                appendLine("    \"${key.jsonEscaped()}\": $value${if (index == counts.size - 1) "" else ","}")
            }
            appendLine("  }${if (trailingComma) "," else ""}")
        }
    }

    private enum class PipelinePrediction {
        REAL,
        SYNTHETIC,
        UNCERTAIN,
    }
}
