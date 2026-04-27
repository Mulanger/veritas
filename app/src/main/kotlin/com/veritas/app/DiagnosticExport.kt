package com.veritas.app

import android.os.Build
import com.veritas.data.detection.HistoryItem
import com.veritas.domain.detection.VerdictOutcome
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticExportGenerator
    @Inject
    constructor() {
        fun generate(
            settings: VeritasSettings,
            historyItems: List<HistoryItem>,
            logs: List<String> = emptyList(),
        ): String {
            val counts = historyItems.countsByOutcome()
            return buildString {
                appendLine("Veritas diagnostic export")
                appendLine("format_version=1")
                appendLine()
                appendLine("[device]")
                appendLine("manufacturer=${Build.MANUFACTURER.safeValue()}")
                appendLine("model=${Build.MODEL.safeValue()}")
                appendLine("android_sdk=${Build.VERSION.SDK_INT}")
                appendLine("android_release=${Build.VERSION.RELEASE.safeValue()}")
                appendLine("available_processors=${Runtime.getRuntime().availableProcessors()}")
                appendLine("max_memory_mb=${Runtime.getRuntime().maxMemory() / BYTES_PER_MIB}")
                appendLine()
                appendLine("[app]")
                appendLine("version_name=${BuildConfig.VERSION_NAME}")
                appendLine("version_code=${BuildConfig.VERSION_CODE}")
                appendLine()
                appendLine("[models]")
                appendLine("video_detector=v2.4.1")
                appendLine("audio_detector=v1.8.0")
                appendLine("image_detector=v3.1.2")
                appendLine()
                appendLine("[settings]")
                appendLine("overlay_enabled=${settings.overlayEnabled}")
                appendLine("bubble_haptics=${settings.bubbleHaptics}")
                appendLine("toast_auto_dismiss_seconds=${settings.toastAutoDismissSeconds}")
                appendLine("model_auto_update=${settings.modelAutoUpdate}")
                appendLine("model_wifi_only=${settings.modelWifiOnly}")
                appendLine("telemetry_opt_in=${settings.telemetryOptIn}")
                appendLine()
                appendLine("[verdict_counts_last_30_days]")
                appendLine("verified_authentic=${counts[VerdictOutcome.VERIFIED_AUTHENTIC] ?: 0}")
                appendLine("likely_authentic=${counts[VerdictOutcome.LIKELY_AUTHENTIC] ?: 0}")
                appendLine("uncertain=${counts[VerdictOutcome.UNCERTAIN] ?: 0}")
                appendLine("likely_synthetic=${counts[VerdictOutcome.LIKELY_SYNTHETIC] ?: 0}")
                appendLine()
                appendLine("[veritas_logs_last_50]")
                if (logs.isEmpty()) {
                    appendLine("none")
                } else {
                    logs.takeLast(MAX_LOG_LINES).forEach { line ->
                        appendLine(line.sanitizeLogLine())
                    }
                }
            }
        }

        private fun List<HistoryItem>.countsByOutcome(): Map<VerdictOutcome, Int> {
            val cutoff = System.currentTimeMillis() - THIRTY_DAYS_MS
            return filter { it.scannedAt >= cutoff }.groupingBy { it.verdictOutcome }.eachCount()
        }

        private fun String.safeValue(): String = replace(Regex("[^A-Za-z0-9 ._\\-]"), "_")

        private fun String.sanitizeLogLine(): String =
            replace(Regex("file:/+[^\\s]+"), "file://[redacted]")
                .replace(Regex("content://[^\\s]+"), "content://[redacted]")
                .take(MAX_LOG_CHARS)

        private companion object {
            const val BYTES_PER_MIB = 1024L * 1024L
            const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L
            const val MAX_LOG_LINES = 50
            const val MAX_LOG_CHARS = 240
        }
    }

class VeritasLogBuffer : timber.log.Timber.Tree() {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        val resolvedTag = tag?.takeIf { it.startsWith("Veritas", ignoreCase = true) } ?: return
        lines += "${priority.toPriorityName()}/$resolvedTag: $message"
        while (lines.size > MAX_LINES) {
            lines.removeFirst()
        }
    }

    private fun Int.toPriorityName(): String =
        when (this) {
            android.util.Log.ERROR -> "E"
            android.util.Log.WARN -> "W"
            android.util.Log.INFO -> "I"
            android.util.Log.DEBUG -> "D"
            else -> toString().uppercase(Locale.US)
        }

    companion object {
        private const val MAX_LINES = 50
    }
}
