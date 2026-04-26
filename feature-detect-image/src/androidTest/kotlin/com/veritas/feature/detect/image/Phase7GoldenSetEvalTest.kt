@file:Suppress("MagicNumber")

package com.veritas.feature.detect.image

import android.content.Context
import android.graphics.BitmapFactory
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
import com.veritas.domain.detection.UncertainReason
import com.veritas.feature.detect.image.domain.ImageDetectionInput
import com.veritas.feature.detect.image.domain.ImageDetector
import com.veritas.feature.detect.image.model.DeepfakeDetectorV2Model
import java.io.File
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase7GoldenSetEvalTest {
    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun phase7GoldenSet_runsOnDeviceAndWritesMetrics() = runBlocking {
        val manifest = readManifest()
        assertEquals(EXPECTED_TOTAL, manifest.size)

        val detector = ImageDetector(
            model = DeepfakeDetectorV2Model(
                runnerFactory = RunnerFactory(
                    appContext = appContext,
                    liteRtRuntime = LiteRtRuntime(appContext),
                ),
            ),
        )

        val outcomes = manifest.mapIndexed { index, row ->
            val file = copyGoldenAssetToCache(row.filename)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val result = detector.detect(
                ImageDetectionInput(
                    media = ScannedMedia(
                        id = "phase7-golden-$index",
                        uri = file.toURI().toString(),
                        mediaType = MediaType.IMAGE,
                        mimeType = mimeTypeFor(file),
                        sizeBytes = file.length(),
                        durationMs = null,
                        widthPx = bounds.outWidth,
                        heightPx = bounds.outHeight,
                        source = MediaSource.FilePicker,
                        ingestedAt = Clock.System.now(),
                    ),
                    file = file,
                ),
            )
            val outcome = EvalOutcome.from(row, result)
            if ((index + 1) % LOG_EVERY_N == 0 || index == manifest.lastIndex) {
                Log.i(TAG, "evaluated=${index + 1}/${manifest.size}, latest=${outcome.logSummary()}")
            }
            outcome
        }

        val metrics = EvalMetrics.from(outcomes)
        val outputFiles = writeResults(metrics.toJson())
        Log.i(TAG, "phase_7_eval_results=${outputFiles.joinToString { it.absolutePath }}")
        Log.i(TAG, metrics.logSummary())

        assertTrue("binary accuracy must be > 65%, actual=${metrics.binaryAccuracy}", metrics.binaryAccuracy > 0.65)
        assertTrue("binary FPR must be <= 15%, actual=${metrics.falsePositiveRate}", metrics.falsePositiveRate <= 0.15)
        assertTrue("uncertain rate must be >= 5%, actual=${metrics.uncertainRate}", metrics.uncertainRate >= 0.05)
        assertTrue("uncertain rate must be <= 20%, actual=${metrics.uncertainRate}", metrics.uncertainRate <= 0.20)
        assertTrue("p95 latency must be <= 2500ms, actual=${metrics.p95LatencyMs}", metrics.p95LatencyMs <= 2_500L)
    }

    private fun readManifest(): List<ManifestRow> =
        testContext.assets.open("$GOLDEN_ASSET_ROOT/MANIFEST.csv").bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map { line ->
                val parts = line.split(',', limit = 5)
                require(parts.size == 5) { "Malformed manifest row: $line" }
                ManifestRow(
                    filename = parts[0],
                    label = ExpectedLabel.from(parts[1]),
                    sourceDataset = parts[2],
                    generator = parts[3],
                    originalPath = parts[4],
                )
            }.toList()
        }

    private fun copyGoldenAssetToCache(relativePath: String): File {
        val dest = File(appContext.cacheDir, "phase7_golden/$relativePath")
        if (dest.exists()) return dest
        dest.parentFile?.mkdirs()
        testContext.assets.open("$GOLDEN_ASSET_ROOT/$relativePath").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    private fun writeResults(json: String): List<File> {
        val outputFiles = mutableListOf<File>()
        val additionalOutputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        if (!additionalOutputDir.isNullOrBlank()) {
            outputFiles += File(additionalOutputDir, RESULTS_FILE_NAME)
        }
        outputFiles += File(appContext.filesDir, RESULTS_FILE_NAME)
        return outputFiles.onEach { file ->
            file.parentFile?.mkdirs()
            file.writeText(json)
        }
    }

    private fun mimeTypeFor(file: File): String =
        when (file.extension.lowercase(Locale.US)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }

    private data class ManifestRow(
        val filename: String,
        val label: ExpectedLabel,
        val sourceDataset: String,
        val generator: String,
        val originalPath: String,
    )

    private enum class ExpectedLabel(val jsonValue: String) {
        REAL("real"),
        SYNTHETIC("synthetic"),
        ;

        companion object {
            fun from(value: String): ExpectedLabel =
                entries.first { it.jsonValue == value.lowercase(Locale.US) }
        }
    }

    private enum class BinaryPrediction(val jsonValue: String) {
        REAL("real"),
        SYNTHETIC("synthetic"),
    }

    private enum class PipelinePrediction(val jsonValue: String) {
        REAL("real"),
        SYNTHETIC("synthetic"),
        UNCERTAIN("uncertain"),
    }

    private data class EvalOutcome(
        val row: ManifestRow,
        val score: Float,
        val binaryPrediction: BinaryPrediction,
        val pipelinePrediction: PipelinePrediction,
        val elapsedMs: Long,
        val fallback: String,
        val uncertainReasons: List<String>,
        val vitScore: Float?,
        val exifElaScore: Float?,
    ) {
        val binaryCorrect: Boolean =
            (row.label == ExpectedLabel.REAL && binaryPrediction == BinaryPrediction.REAL) ||
                (row.label == ExpectedLabel.SYNTHETIC && binaryPrediction == BinaryPrediction.SYNTHETIC)

        val pipelineCorrect: Boolean =
            (row.label == ExpectedLabel.REAL && pipelinePrediction == PipelinePrediction.REAL) ||
                (row.label == ExpectedLabel.SYNTHETIC && pipelinePrediction == PipelinePrediction.SYNTHETIC)

        fun logSummary(): String =
            "label=${row.label.jsonValue}, generator=${row.generator}, score=$score, " +
                "binary=${binaryPrediction.jsonValue}, pipeline=${pipelinePrediction.jsonValue}, elapsedMs=$elapsedMs"

        companion object {
            fun from(row: ManifestRow, result: BasicDetectorResult): EvalOutcome {
                val pipelinePrediction =
                    if (result.blocksPipelineVerdict()) {
                        PipelinePrediction.UNCERTAIN
                    } else if (result.syntheticScore >= PIPELINE_SYNTHETIC_THRESHOLD) {
                        PipelinePrediction.SYNTHETIC
                    } else {
                        PipelinePrediction.REAL
                    }
                return EvalOutcome(
                    row = row,
                    score = result.syntheticScore,
                    binaryPrediction =
                        if (result.syntheticScore >= BINARY_SYNTHETIC_THRESHOLD) {
                            BinaryPrediction.SYNTHETIC
                        } else {
                            BinaryPrediction.REAL
                        },
                    pipelinePrediction = pipelinePrediction,
                    elapsedMs = result.elapsedMs,
                    fallback = result.fallbackUsed.name,
                    uncertainReasons = result.uncertainReasons.map { it.name },
                    vitScore = result.subScores["vit_model"],
                    exifElaScore = result.subScores["exif_ela"],
                )
            }
        }
    }

    private data class EvalMetrics(
        val total: Int,
        val realCount: Int,
        val syntheticCount: Int,
        val binaryAccuracy: Double,
        val falsePositiveRate: Double,
        val falseNegativeRate: Double,
        val pipelineAccuracy: Double,
        val uncertainRate: Double,
        val p50LatencyMs: Long,
        val p95LatencyMs: Long,
        val meanRealScore: Double,
        val meanSyntheticScore: Double,
        val fallbackCounts: Map<String, Int>,
        val uncertainReasonCounts: Map<String, Int>,
        val perGenerator: Map<String, EvalBucket>,
    ) {
        fun logSummary(): String =
            "total=$total, binaryAccuracy=$binaryAccuracy, fpr=$falsePositiveRate, fnr=$falseNegativeRate, " +
                "uncertainRate=$uncertainRate, p50Ms=$p50LatencyMs, p95Ms=$p95LatencyMs"

        fun toJson(): String =
            buildString {
                appendLine("{")
                appendJsonString("generatedAt", Clock.System.now().toString(), trailingComma = true)
                appendJsonString("model", "prithivMLmods/Deep-Fake-Detector-v2-Model", trailingComma = true)
                appendJsonString("dataset", "manjilkarki/deepfake-and-real-images", trailingComma = true)
                appendLine("  \"decisionPolicy\": {")
                appendLine("    \"binarySyntheticThreshold\": $BINARY_SYNTHETIC_THRESHOLD,")
                appendLine("    \"pipelineSyntheticThreshold\": $PIPELINE_SYNTHETIC_THRESHOLD,")
                appendLine("    \"pipelineUncertainOnIntervalCrossing\": true,")
                appendLine("    \"pipelineUncertainReasons\": [\"TOO_SMALL\", \"HEAVY_COMPRESSION\", \"LOW_CONFIDENCE_RANGE\"],")
                appendLine("    \"cpuFallbackDiagnosticOnly\": true")
                appendLine("  },")
                appendLine("  \"acceptance\": {")
                appendLine("    \"accuracyGt65Pct\": ${binaryAccuracy > 0.65},")
                appendLine("    \"falsePositiveRateLte15Pct\": ${falsePositiveRate <= 0.15},")
                appendLine("    \"uncertainRateGte5Pct\": ${uncertainRate >= 0.05},")
                appendLine("    \"uncertainRateLte20Pct\": ${uncertainRate <= 0.20},")
                appendLine("    \"p95LatencyLte2500Ms\": ${p95LatencyMs <= 2_500L}")
                appendLine("  },")
                appendLine("  \"overall\": {")
                appendLine("    \"total\": $total,")
                appendLine("    \"realCount\": $realCount,")
                appendLine("    \"syntheticCount\": $syntheticCount,")
                appendLine("    \"binaryAccuracy\": ${binaryAccuracy.jsonNumber()},")
                appendLine("    \"falsePositiveRate\": ${falsePositiveRate.jsonNumber()},")
                appendLine("    \"falseNegativeRate\": ${falseNegativeRate.jsonNumber()},")
                appendLine("    \"pipelineAccuracy\": ${pipelineAccuracy.jsonNumber()},")
                appendLine("    \"uncertainRate\": ${uncertainRate.jsonNumber()},")
                appendLine("    \"p50LatencyMs\": $p50LatencyMs,")
                appendLine("    \"p95LatencyMs\": $p95LatencyMs,")
                appendLine("    \"meanRealScore\": ${meanRealScore.jsonNumber()},")
                appendLine("    \"meanSyntheticScore\": ${meanSyntheticScore.jsonNumber()}")
                appendLine("  },")
                appendCountObject("fallbackCounts", fallbackCounts, trailingComma = true)
                appendCountObject("uncertainReasonCounts", uncertainReasonCounts, trailingComma = true)
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
                val falsePositives = real.count { it.binaryPrediction == BinaryPrediction.SYNTHETIC }
                val falseNegatives = synthetic.count { it.binaryPrediction == BinaryPrediction.REAL }
                val perGenerator = outcomes.groupBy { it.row.generator }.mapValues { (_, group) -> EvalBucket.from(group) }
                return EvalMetrics(
                    total = outcomes.size,
                    realCount = real.size,
                    syntheticCount = synthetic.size,
                    binaryAccuracy = outcomes.count { it.binaryCorrect }.rate(outcomes.size),
                    falsePositiveRate = falsePositives.rate(real.size),
                    falseNegativeRate = falseNegatives.rate(synthetic.size),
                    pipelineAccuracy = outcomes.count { it.pipelineCorrect }.rate(outcomes.size),
                    uncertainRate = outcomes.count { it.pipelinePrediction == PipelinePrediction.UNCERTAIN }.rate(outcomes.size),
                    p50LatencyMs = outcomes.map { it.elapsedMs }.percentile(0.50),
                    p95LatencyMs = outcomes.map { it.elapsedMs }.percentile(0.95),
                    meanRealScore = real.meanScore(),
                    meanSyntheticScore = synthetic.meanScore(),
                    fallbackCounts = outcomes.groupingBy { it.fallback }.eachCount(),
                    uncertainReasonCounts = outcomes.flatMap { it.uncertainReasons }.groupingBy { it }.eachCount(),
                    perGenerator = perGenerator,
                )
            }
        }
    }

    private data class EvalBucket(
        val total: Int,
        val binaryAccuracy: Double,
        val falsePositiveRate: Double,
        val falseNegativeRate: Double,
        val uncertainRate: Double,
        val meanScore: Double,
        val p95LatencyMs: Long,
    ) {
        fun toJson(): String =
            "{ \"total\": $total, " +
                "\"binaryAccuracy\": ${binaryAccuracy.jsonNumber()}, " +
                "\"falsePositiveRate\": ${falsePositiveRate.jsonNumber()}, " +
                "\"falseNegativeRate\": ${falseNegativeRate.jsonNumber()}, " +
                "\"uncertainRate\": ${uncertainRate.jsonNumber()}, " +
                "\"meanScore\": ${meanScore.jsonNumber()}, " +
                "\"p95LatencyMs\": $p95LatencyMs }"

        companion object {
            fun from(outcomes: List<EvalOutcome>): EvalBucket {
                val real = outcomes.filter { it.row.label == ExpectedLabel.REAL }
                val synthetic = outcomes.filter { it.row.label == ExpectedLabel.SYNTHETIC }
                return EvalBucket(
                    total = outcomes.size,
                    binaryAccuracy = outcomes.count { it.binaryCorrect }.rate(outcomes.size),
                    falsePositiveRate = real.count { it.binaryPrediction == BinaryPrediction.SYNTHETIC }.rate(real.size),
                    falseNegativeRate = synthetic.count { it.binaryPrediction == BinaryPrediction.REAL }.rate(synthetic.size),
                    uncertainRate = outcomes.count { it.pipelinePrediction == PipelinePrediction.UNCERTAIN }.rate(outcomes.size),
                    meanScore = outcomes.meanScore(),
                    p95LatencyMs = outcomes.map { it.elapsedMs }.percentile(0.95),
                )
            }
        }
    }

    private companion object {
        private const val TAG = "Phase7GoldenSetEval"
        private const val GOLDEN_ASSET_ROOT = "golden-image"
        private const val RESULTS_FILE_NAME = "phase_7_eval_results.json"
        private const val EXPECTED_TOTAL = 500
        private const val LOG_EVERY_N = 25
        private const val BINARY_SYNTHETIC_THRESHOLD = 0.50f
        private const val PIPELINE_SYNTHETIC_THRESHOLD = 0.65f
        private val TERMINAL_UNCERTAIN_REASONS = setOf(
            UncertainReason.TOO_SMALL,
            UncertainReason.HEAVY_COMPRESSION,
            UncertainReason.LOW_CONFIDENCE_RANGE,
        )

        private fun BasicDetectorResult.blocksPipelineVerdict(): Boolean =
            uncertainReasons.any { it in TERMINAL_UNCERTAIN_REASONS } ||
                confidenceInterval.crossesDecisionThreshold()

        private fun com.veritas.domain.detection.ConfidenceInterval.crossesDecisionThreshold(): Boolean =
            low < BINARY_SYNTHETIC_THRESHOLD && high > BINARY_SYNTHETIC_THRESHOLD

        private fun List<EvalOutcome>.meanScore(): Double =
            if (isEmpty()) 0.0 else sumOf { it.score.toDouble() } / size.toDouble()

        private fun List<Long>.percentile(percentile: Double): Long {
            if (isEmpty()) return 0L
            val sorted = sorted()
            val index = (ceil(sorted.size * percentile).toInt() - 1).coerceIn(0, sorted.lastIndex)
            return sorted[index]
        }

        private fun Int.rate(denominator: Int): Double =
            if (denominator == 0) 0.0 else toDouble() / denominator.toDouble()

        private fun String.jsonEscaped(): String =
            replace("\\", "\\\\").replace("\"", "\\\"")

        private fun Double.jsonNumber(): String =
            String.format(Locale.US, "%.6f", this)

        private fun StringBuilder.appendJsonString(name: String, value: String, trailingComma: Boolean) {
            appendLine("  \"${name.jsonEscaped()}\": \"${value.jsonEscaped()}\"${if (trailingComma) "," else ""}")
        }

        private fun StringBuilder.appendCountObject(name: String, counts: Map<String, Int>, trailingComma: Boolean) {
            appendLine("  \"${name.jsonEscaped()}\": {")
            counts.entries.sortedBy { it.key }.forEachIndexed { index, (key, value) ->
                appendLine("    \"${key.jsonEscaped()}\": $value${if (index == counts.size - 1) "" else ","}")
            }
            appendLine("  }${if (trailingComma) "," else ""}")
        }
    }
}
