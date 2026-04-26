@file:Suppress("MagicNumber", "TooManyFunctions")

package com.veritas.feature.detect.audio

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.data.detection.ml.inference.RunnerFactory
import com.veritas.data.detection.ml.runtime.LiteRtRuntime
import com.veritas.domain.detection.MediaSource
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.ScannedMedia
import com.veritas.domain.detection.UncertainReason
import com.veritas.feature.detect.audio.domain.AudioDetectionInput
import com.veritas.feature.detect.audio.domain.AudioDetector
import com.veritas.feature.detect.audio.model.DeepfakeAudioDetectorModel
import java.io.File
import kotlin.math.ceil
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioEvalHarness {
    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun phase8GoldenAudioSetMeetsProductFunctionalGate() = runBlocking {
        val rows = loadManifest()
        val detector = AudioDetector(
            model = DeepfakeAudioDetectorModel(
                runnerFactory = RunnerFactory(
                    appContext = appContext,
                    liteRtRuntime = LiteRtRuntime(appContext),
                ),
            ),
        )
        val results = rows.mapIndexed { index, row ->
            val file = copyAsset(row.filename)
            val started = System.nanoTime()
            val detectorResult = detector.detect(
                AudioDetectionInput(
                    media = ScannedMedia(
                        id = "phase8-eval-$index",
                        uri = file.toURI().toString(),
                        mediaType = MediaType.AUDIO,
                        mimeType = mimeFor(row.codec),
                        sizeBytes = file.length(),
                        durationMs = row.durationMs,
                        widthPx = null,
                        heightPx = null,
                        source = MediaSource.FilePicker,
                        ingestedAt = Clock.System.now(),
                    ),
                    file = file,
                ),
            )
            val elapsedMs = (System.nanoTime() - started) / NANOS_PER_MILLI
            val uncertain = detectorResult.blocksVerdict()
            val predicted = if (!uncertain && detectorResult.syntheticScore >= SYNTHETIC_THRESHOLD) LABEL_SYNTHETIC else LABEL_REAL
            EvalResult(
                row = row,
                score = detectorResult.syntheticScore,
                uncertain = uncertain,
                predicted = predicted,
                elapsedMs = elapsedMs,
            ).also {
                if ((index + 1) % LOG_EVERY == 0) {
                    Log.i(TAG, "evaluated ${index + 1}/${rows.size}")
                }
            }
        }
        val summary = summarize(results)
        val json = summary.toJson()
        File(appContext.filesDir, RESULT_FILE).writeText(json)
        Log.i(TAG, "PHASE8_EVAL_RESULTS_JSON=$json")

        assertTrue("overall accuracy ${summary.overallAccuracy}", summary.overallAccuracy > MIN_ACCURACY)
        assertTrue("false positive rate ${summary.falsePositiveRate}", summary.falsePositiveRate <= MAX_FPR)
        assertTrue("p95 latency ${summary.p95LatencyMs}", summary.p95LatencyMs <= MAX_P95_MS)
    }

    private fun loadManifest(): List<ManifestRow> {
        val text = testContext.assets.open("$ASSET_ROOT/MANIFEST.csv").bufferedReader().use { it.readText() }
        return text.lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split(",")
                ManifestRow(
                    filename = parts[0],
                    label = parts[1],
                    sourceDataset = parts[2],
                    ttsSystemOrSpeaker = parts[3],
                    codec = parts[4],
                    sampleRate = parts[5].toInt(),
                    durationMs = parts[6].toLong(),
                )
            }.toList()
    }

    private fun copyAsset(relativeName: String): File {
        val destination = File(appContext.cacheDir, "$ASSET_ROOT/$relativeName")
        if (destination.exists() && destination.length() > 0) return destination
        destination.parentFile?.mkdirs()
        testContext.assets.open("$ASSET_ROOT/$relativeName").use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return destination
    }

    private fun com.veritas.domain.detection.BasicDetectorResult.blocksVerdict(): Boolean {
        val interval = confidenceInterval
        return uncertainReasons.any { it in TERMINAL_UNCERTAIN_REASONS } ||
            (interval.low < 0.5f && interval.high > 0.5f)
    }

    private fun summarize(results: List<EvalResult>): EvalSummary {
        val certain = results.filterNot { it.uncertain }
        val real = results.filter { it.row.label == LABEL_REAL }
        val synthetic = results.filter { it.row.label == LABEL_SYNTHETIC }
        val correct = certain.count { it.predicted == it.row.label }
        val falsePositives = real.count { !it.uncertain && it.predicted == LABEL_SYNTHETIC }
        val falseNegatives = synthetic.count { !it.uncertain && it.predicted == LABEL_REAL }
        return EvalSummary(
            total = results.size,
            overallAccuracy = correct.toDouble() / results.size.toDouble(),
            falsePositiveRate = falsePositives.toDouble() / real.size.toDouble(),
            falseNegativeRate = falseNegatives.toDouble() / synthetic.size.toDouble(),
            uncertainRate = results.count { it.uncertain }.toDouble() / results.size.toDouble(),
            p50LatencyMs = percentile(results.map { it.elapsedMs }, 0.50),
            p95LatencyMs = percentile(results.map { it.elapsedMs }, 0.95),
            perTtsSystem = groupAccuracy(results) { it.row.ttsSystemOrSpeaker },
            perCodec = groupAccuracy(results) { it.row.codec },
        )
    }

    private fun groupAccuracy(
        results: List<EvalResult>,
        key: (EvalResult) -> String,
    ): Map<String, GroupSummary> =
        results.groupBy(key).mapValues { (_, values) ->
            val certain = values.filterNot { it.uncertain }
            GroupSummary(
                count = values.size,
                accuracy = certain.count { it.predicted == it.row.label }.toDouble() / values.size.toDouble(),
                uncertainRate = values.count { it.uncertain }.toDouble() / values.size.toDouble(),
            )
        }.toSortedMap()

    private fun percentile(values: List<Long>, quantile: Double): Long {
        val sorted = values.sorted()
        if (sorted.isEmpty()) return 0L
        val index = (ceil(sorted.size * quantile).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun mimeFor(codec: String): String =
        when (codec) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "opus" -> "audio/opus"
            else -> "audio/$codec"
        }

    private companion object {
        private const val TAG = "AudioEvalHarness"
        private const val ASSET_ROOT = "golden-audio"
        private const val RESULT_FILE = "phase_8_eval_results.json"
        private const val LABEL_REAL = "real"
        private const val LABEL_SYNTHETIC = "synthetic"
        private const val SYNTHETIC_THRESHOLD = 0.65f
        private const val MIN_ACCURACY = 0.60
        private const val MAX_FPR = 0.18
        private const val MAX_P95_MS = 3500L
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val LOG_EVERY = 25
        private val TERMINAL_UNCERTAIN_REASONS = setOf(
            UncertainReason.TOO_SHORT,
            UncertainReason.TOO_LONG_PROCESSED_TRUNCATED,
            UncertainReason.LOW_SAMPLE_RATE,
            UncertainReason.LOW_CONFIDENCE_RANGE,
        )
    }
}

private data class ManifestRow(
    val filename: String,
    val label: String,
    val sourceDataset: String,
    val ttsSystemOrSpeaker: String,
    val codec: String,
    val sampleRate: Int,
    val durationMs: Long,
)

private data class EvalResult(
    val row: ManifestRow,
    val score: Float,
    val uncertain: Boolean,
    val predicted: String,
    val elapsedMs: Long,
)

private data class EvalSummary(
    val total: Int,
    val overallAccuracy: Double,
    val falsePositiveRate: Double,
    val falseNegativeRate: Double,
    val uncertainRate: Double,
    val p50LatencyMs: Long,
    val p95LatencyMs: Long,
    val perTtsSystem: Map<String, GroupSummary>,
    val perCodec: Map<String, GroupSummary>,
) {
    fun toJson(): String =
        buildString {
            append("{")
            append("\"total\":").append(total).append(',')
            append("\"overall_accuracy\":").append(overallAccuracy).append(',')
            append("\"false_positive_rate\":").append(falsePositiveRate).append(',')
            append("\"false_negative_rate\":").append(falseNegativeRate).append(',')
            append("\"uncertain_rate\":").append(uncertainRate).append(',')
            append("\"p50_latency_ms\":").append(p50LatencyMs).append(',')
            append("\"p95_latency_ms\":").append(p95LatencyMs).append(',')
            append("\"per_tts_system\":").append(perTtsSystem.toJson()).append(',')
            append("\"per_codec\":").append(perCodec.toJson())
            append("}")
        }
}

private data class GroupSummary(
    val count: Int,
    val accuracy: Double,
    val uncertainRate: Double,
)

private fun Map<String, GroupSummary>.toJson(): String =
    entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "\"${key.escapeJson()}\":{\"count\":${value.count},\"accuracy\":${value.accuracy},\"uncertain_rate\":${value.uncertainRate}}"
    }

private fun String.escapeJson(): String = replace("\\", "\\\\").replace("\"", "\\\"")
