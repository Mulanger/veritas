package com.veritas.app

import android.content.Context
import android.content.Intent
import com.veritas.data.detection.MediaIngestionFailure
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.ScannedMedia
import kotlinx.serialization.json.Json

const val SCAN_SCREEN_TAG = "scan_screen"
const val SCAN_STUB_SCREEN_TAG = SCAN_SCREEN_TAG
const val SCAN_STUB_DONE_TAG = "scan_stub_done"
const val SCAN_CLOSE_BUTTON_TAG = "scan_close_button"
const val VERDICT_SCREEN_TAG = "verdict_screen"
const val VERDICT_PRIMARY_ACTION_TAG = "verdict_primary_action"
const val VERDICT_DONE_ACTION_TAG = "verdict_done_action"
const val FORENSIC_SCREEN_TAG = "forensic_screen"
const val FORENSIC_REASON_TAG_PREFIX = "forensic_reason_"
const val FORENSIC_BACK_TO_VERDICT_TAG = "forensic_back_to_verdict"
const val REASON_DETAIL_SHEET_TAG = "reason_detail_sheet"
const val REASON_DETAIL_CLOSE_TAG = "reason_detail_close"
const val FIND_ORIGINAL_SHEET_TAG = "find_original_sheet"
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

fun Context.buildScanIntent(media: ScannedMedia): Intent =
    Intent(this, ScanActivity::class.java).apply {
        putExtra(EXTRA_SCANNED_MEDIA_JSON, ingestionJson.encodeToString(ScannedMedia.serializer(), media))
    }

fun Context.buildScanStubIntent(media: ScannedMedia): Intent = buildScanIntent(media)

fun Context.buildIngestionErrorIntent(error: IngestionErrorScreen): Intent =
    Intent(this, ScanActivity::class.java).apply {
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
