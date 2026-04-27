package com.veritas.data.detection

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.veritas.domain.detection.ConfidenceRange
import com.veritas.domain.detection.InferenceHardware
import com.veritas.domain.detection.MediaSource
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.Reason
import com.veritas.domain.detection.ReasonCode
import com.veritas.domain.detection.ReasonEvidence
import com.veritas.domain.detection.ScannedMedia
import com.veritas.domain.detection.Severity
import com.veritas.domain.detection.Verdict
import com.veritas.domain.detection.VerdictOutcome
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: VeritasHistoryDatabase
    private lateinit var repository: HistoryRepository
    private lateinit var sourceImage: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "history").deleteRecursively()
        sourceImage =
            File(context.cacheDir, "history-source.jpg").apply {
                parentFile?.mkdirs()
                outputStream().use { output ->
                    Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
                        .compress(Bitmap.CompressFormat.JPEG, 90, output)
                }
            }
        database =
            Room.inMemoryDatabaseBuilder(context, VeritasHistoryDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = HistoryRepository(database.historyDao(), ThumbnailStore(context))
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "history").deleteRecursively()
        sourceImage.delete()
    }

    @Test
    fun saveScanStoresSummaryOnlyAndPrunesToOneHundredItems() =
        runTest {
            repeat(101) { index ->
                val id = "scan-$index"
                repository.saveScan(
                    media = media(id),
                    verdict = verdict(id, scannedAt = BASE_TIME_MS + index),
                )
            }

            val items = repository.observeHistory().first()
            val prunedThumbnail = File(context.filesDir, "history/thumbnails/scan-0.jpg")

            assertEquals(100, items.size)
            assertFalse(items.any { item -> item.id == "scan-0" })
            assertFalse(prunedThumbnail.exists())
            assertTrue(items.all { item -> item.thumbnailPath.startsWith(context.filesDir.absolutePath) })
            assertTrue(items.none { item -> item.thumbnailPath == sourceImage.absolutePath })
            assertEquals("Detector summary", items.first().summary)
            assertEquals(mapOf("image" to "test-model"), items.first().modelVersions)
        }

    @Test
    fun deleteAndClearRemoveThumbnailFiles() =
        runTest {
            repository.saveScan(media("delete-me"), verdict("delete-me", BASE_TIME_MS))
            repository.saveScan(media("clear-me"), verdict("clear-me", BASE_TIME_MS + 1))
            val deletePath = requireNotNull(repository.observeHistoryItem("delete-me").first()).thumbnailPath
            val clearPath = requireNotNull(repository.observeHistoryItem("clear-me").first()).thumbnailPath

            repository.delete("delete-me")
            repository.clear()

            assertFalse(File(deletePath).exists())
            assertFalse(File(clearPath).exists())
            assertTrue(repository.observeHistory().first().isEmpty())
        }

    private fun media(id: String): ScannedMedia =
        ScannedMedia(
            id = id,
            uri = Uri.fromFile(sourceImage).toString(),
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            sizeBytes = sourceImage.length(),
            durationMs = null,
            widthPx = 20,
            heightPx = 20,
            source = MediaSource.ShareIntent("com.example.sender"),
            ingestedAt = Instant.fromEpochMilliseconds(BASE_TIME_MS),
        )

    private fun verdict(
        id: String,
        scannedAt: Long,
    ): Verdict =
        Verdict(
            id = id,
            mediaId = id,
            mediaType = MediaType.IMAGE,
            outcome = VerdictOutcome.LIKELY_SYNTHETIC,
            confidence = ConfidenceRange(81, 93),
            summary = "Detector summary",
            reasons =
                listOf(
                    Reason(
                        code = ReasonCode.IMG_EXIF_MISSING,
                        weight = 0.7f,
                        severity = Severity.MINOR,
                        evidence = ReasonEvidence.Qualitative("EXIF missing"),
                    ),
                ),
            modelVersions = mapOf("image" to "test-model"),
            scannedAt = Instant.fromEpochMilliseconds(scannedAt),
            inferenceHardware = InferenceHardware.CPU_XNNPACK,
            elapsedMs = 42,
        )

    private companion object {
        const val BASE_TIME_MS = 1_700_000_000_000L
    }
}
