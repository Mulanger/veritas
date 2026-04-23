package com.veritas.app

import android.content.Context
import android.content.Intent
import com.veritas.data.detection.MediaIngestionFailure
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.ScannedMedia
import kotlinx.serialization.json.Json

const val SCAN_STUB_SCREEN_TAG = "scan_stub_screen"
const val SCAN_STUB_DONE_TAG = "scan_stub_done"
const val INGESTION_ERROR_SCREEN_TAG = "ingestion_error_screen"
const val INGESTION_ERROR_PRIMARY_TAG = "ingestion_error_primary"
const val INGESTION_ERROR_SECONDARY_TAG = "ingestion_error_secondary"

private const val EXTRA_SCANNED_MEDIA_JSON = "scanned_media_json"
private const val EXTRA_INGESTION_ERROR = "ingestion_error"

enum class IngestionErrorScreen {
    FILE_TOO_LARGE,
    VIDEO_TOO_LONG,
    AUDIO_TOO_LONG,
    UNSUPPORTED_FORMAT,
    CORRUPTED_FILE,
    STORAGE_FULL,
}

private val ingestionJson = Json { ignoreUnknownKeys = true }

fun Context.buildScanStubIntent(media: ScannedMedia): Intent =
    Intent(this, ScanStubActivity::class.java).apply {
        putExtra(EXTRA_SCANNED_MEDIA_JSON, ingestionJson.encodeToString(ScannedMedia.serializer(), media))
    }

fun Context.buildIngestionErrorIntent(error: IngestionErrorScreen): Intent =
    Intent(this, ScanStubActivity::class.java).apply {
        putExtra(EXTRA_INGESTION_ERROR, error.name)
    }

fun Intent.scannedMediaOrNull(): ScannedMedia? =
    getStringExtra(EXTRA_SCANNED_MEDIA_JSON)?.let { json ->
        ingestionJson.decodeFromString(ScannedMedia.serializer(), json)
    }

fun Intent.ingestionErrorOrNull(): IngestionErrorScreen? =
    getStringExtra(EXTRA_INGESTION_ERROR)?.let(IngestionErrorScreen::valueOf)

fun MediaIngestionFailure.toErrorScreen(): IngestionErrorScreen =
    when (this) {
        MediaIngestionFailure.FileTooLarge -> IngestionErrorScreen.FILE_TOO_LARGE
        is MediaIngestionFailure.DurationTooLong ->
            when (mediaType) {
                MediaType.VIDEO -> IngestionErrorScreen.VIDEO_TOO_LONG
                MediaType.AUDIO -> IngestionErrorScreen.AUDIO_TOO_LONG
                MediaType.IMAGE -> IngestionErrorScreen.UNSUPPORTED_FORMAT
            }
        is MediaIngestionFailure.UnsupportedFormat -> IngestionErrorScreen.UNSUPPORTED_FORMAT
        MediaIngestionFailure.CorruptedFile -> IngestionErrorScreen.CORRUPTED_FILE
        MediaIngestionFailure.StorageFull -> IngestionErrorScreen.STORAGE_FULL
    }
