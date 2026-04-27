package com.veritas.app

import com.veritas.data.detection.HistoryItem
import com.veritas.domain.detection.ConfidenceRange
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.Reason
import com.veritas.domain.detection.ReasonCode
import com.veritas.domain.detection.ReasonEvidence
import com.veritas.domain.detection.Severity
import com.veritas.domain.detection.VerdictOutcome
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiagnosticExportGeneratorTest {
    @Test
    fun exportIncludesOperationalStateWithoutMediaPathsOrReasonText() {
        val export =
            DiagnosticExportGenerator().generate(
                settings = VeritasSettings(overlayEnabled = true, telemetryOptIn = true),
                historyItems =
                    listOf(
                        HistoryItem(
                            id = "history-1",
                            mediaType = MediaType.IMAGE,
                            mediaMimeType = "image/jpeg",
                            durationMs = null,
                            sourcePackage = "com.sender",
                            thumbnailPath = "/data/user/0/com.veritas.app/files/history/thumb.jpg",
                            verdictOutcome = VerdictOutcome.LIKELY_SYNTHETIC,
                            confidence = ConfidenceRange(82, 94),
                            summary = "Private user-facing verdict summary",
                            topReasons =
                                listOf(
                                    Reason(
                                        code = ReasonCode.IMG_EXIF_MISSING,
                                        weight = 0.4f,
                                        severity = Severity.MINOR,
                                        evidence = ReasonEvidence.Qualitative("private reason detail"),
                                    ),
                                ),
                            modelVersions = mapOf("image" to "private-model-version"),
                            scannedAt = System.currentTimeMillis(),
                        ),
                    ),
                logs =
                    listOf(
                        "D/Veritas: opened file:///sdcard/DCIM/private.jpg",
                        "D/Veritas: provider content://media/external/images/media/12",
                    ),
            )

        assertTrue(export.contains("[settings]"))
        assertTrue(export.contains("likely_synthetic=1"))
        assertTrue(export.contains("telemetry_opt_in=true"))
        assertTrue(export.contains("file://[redacted]"))
        assertTrue(export.contains("content://[redacted]"))
        assertFalse(export.contains("/data/user/0/com.veritas.app/files/history/thumb.jpg"))
        assertFalse(export.contains("Private user-facing verdict summary"))
        assertFalse(export.contains("private reason detail"))
        assertFalse(export.contains("private-model-version"))
        assertFalse(export.contains("content://media/external/images/media/12"))
    }
}
