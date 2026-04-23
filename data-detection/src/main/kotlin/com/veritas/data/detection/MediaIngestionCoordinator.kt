@file:Suppress(
    "CyclomaticComplexMethod",
    "DEPRECATION",
    "LongMethod",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "SwallowedException",
    "TooGenericExceptionCaught",
    "TooManyFunctions",
)

package com.veritas.data.detection

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.veritas.domain.detection.MediaSource
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.ScannedMedia
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

private const val INGESTION_CACHE_DIR = "ingested_media"
private const val MAX_FILE_BYTES = 200L * 1024L * 1024L
private const val MAX_VIDEO_DURATION_MS = 60_000L
private const val MAX_AUDIO_DURATION_MS = 180_000L

data class MediaIngestionRequest(
    val uri: Uri,
    val source: MediaSource,
)

sealed interface MediaIngestionResult {
    data class Success(
        val media: ScannedMedia,
    ) : MediaIngestionResult

    data class Failure(
        val error: MediaIngestionFailure,
    ) : MediaIngestionResult
}

sealed interface MediaIngestionFailure {
    data object FileTooLarge : MediaIngestionFailure

    data class DurationTooLong(
        val mediaType: MediaType,
        val actualDurationMs: Long,
    ) : MediaIngestionFailure

    data class UnsupportedFormat(
        val mimeType: String?,
    ) : MediaIngestionFailure

    data object CorruptedFile : MediaIngestionFailure

    data object StorageFull : MediaIngestionFailure
}

@Singleton
class MediaIngestionCoordinator
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        suspend fun ingest(request: MediaIngestionRequest): MediaIngestionResult =
            withContext(Dispatchers.IO) {
                val destinationDirectory = File(context.cacheDir, INGESTION_CACHE_DIR).apply { mkdirs() }
                val destinationFile = File(destinationDirectory, "${UUID.randomUUID()}.bin")
                val inputStream =
                    openIncomingStream(request.uri)
                        ?: return@withContext MediaIngestionResult.Failure(MediaIngestionFailure.CorruptedFile)

                try {
                    val copiedSize =
                        inputStream.use { stream ->
                            destinationFile.outputStream().use { output ->
                                copyIntoScopedStorage(
                                    source = stream,
                                    destination = output,
                                )
                            }
                        }

                    val descriptor =
                        extractMediaDescriptor(destinationFile)
                            ?: run {
                                destinationFile.delete()
                                return@withContext MediaIngestionResult.Failure(MediaIngestionFailure.UnsupportedFormat(null))
                            }

                    if (descriptor.durationMs != null) {
                        val durationLimit =
                            when (descriptor.mediaType) {
                                MediaType.VIDEO -> MAX_VIDEO_DURATION_MS
                                MediaType.AUDIO -> MAX_AUDIO_DURATION_MS
                                MediaType.IMAGE -> Long.MAX_VALUE
                            }
                        if (descriptor.durationMs > durationLimit) {
                            destinationFile.delete()
                            return@withContext MediaIngestionResult.Failure(
                                MediaIngestionFailure.DurationTooLong(
                                    mediaType = descriptor.mediaType,
                                    actualDurationMs = descriptor.durationMs,
                                ),
                            )
                        }
                    }

                    val scannedMedia =
                        ScannedMedia(
                            id = destinationFile.nameWithoutExtension,
                            uri = Uri.fromFile(destinationFile).toString(),
                            mediaType = descriptor.mediaType,
                            mimeType = descriptor.mimeType,
                            sizeBytes = copiedSize,
                            durationMs = descriptor.durationMs,
                            widthPx = descriptor.widthPx,
                            heightPx = descriptor.heightPx,
                            source = request.source,
                            ingestedAt = Clock.System.now(),
                        )

                    MediaIngestionResult.Success(scannedMedia)
                } catch (_: FileTooLargeException) {
                    destinationFile.delete()
                    MediaIngestionResult.Failure(MediaIngestionFailure.FileTooLarge)
                } catch (exception: IOException) {
                    destinationFile.delete()
                    if (exception.isStorageFull()) {
                        MediaIngestionResult.Failure(MediaIngestionFailure.StorageFull)
                    } else {
                        MediaIngestionResult.Failure(MediaIngestionFailure.CorruptedFile)
                    }
                } catch (exception: RuntimeException) {
                    destinationFile.delete()
                    MediaIngestionResult.Failure(MediaIngestionFailure.CorruptedFile)
                }
            }

        fun schedulePurge(
            media: ScannedMedia,
            delayMs: Long = TimeUnit.SECONDS.toMillis(60),
        ) {
            val request =
                OneTimeWorkRequestBuilder<MediaPurgeWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setInputData(
                        Data.Builder()
                            .putString(MediaPurgeWorker.KEY_MEDIA_URI, media.uri)
                            .build(),
                    ).build()

            workManager().enqueueUniqueWork(
                uniqueWorkName(media.id),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun purgeNow(media: ScannedMedia) {
            workManager().cancelUniqueWork(uniqueWorkName(media.id))
            deleteScopedCopy(media.uri)
        }

        private fun workManager(): WorkManager = WorkManager.getInstance(context)

        private fun uniqueWorkName(mediaId: String): String = "purge-scanned-media-$mediaId"

        private fun openIncomingStream(uri: Uri) =
            when (uri.scheme) {
                ContentResolver.SCHEME_CONTENT -> context.contentResolver.openInputStream(uri)
                ContentResolver.SCHEME_FILE -> File(requireNotNull(uri.path)).inputStream()
                else -> context.contentResolver.openInputStream(uri)
            }

        private fun extractMediaDescriptor(file: File): MediaDescriptor? {
            val container = sniffContainer(file) ?: return null
            return when (container) {
                DetectedContainer.Jpeg ->
                    extractImageDescriptor(
                        file = file,
                        mimeType = "image/jpeg",
                    )
                DetectedContainer.Png ->
                    extractImageDescriptor(
                        file = file,
                        mimeType = "image/png",
                    )
                DetectedContainer.Webp ->
                    extractImageDescriptor(
                        file = file,
                        mimeType = "image/webp",
                    )
                DetectedContainer.Heic ->
                    extractImageDescriptor(
                        file = file,
                        mimeType = "image/heic",
                    )
                DetectedContainer.Mp3 ->
                    extractAudioDescriptor(
                        file = file,
                        mimeType = "audio/mpeg",
                    )
                DetectedContainer.Wav ->
                    extractAudioDescriptor(
                        file = file,
                        mimeType = "audio/wav",
                    )
                DetectedContainer.Aac ->
                    extractAudioDescriptor(
                        file = file,
                        mimeType = "audio/aac",
                    )
                DetectedContainer.Webm -> extractAvDescriptor(file = file, defaultMimeType = "video/webm")
                DetectedContainer.IsoBmff -> extractIsoBmffDescriptor(file)
            }
        }

        private fun extractIsoBmffDescriptor(file: File): MediaDescriptor? {
            val metadata = extractRetrieverMetadata(file) ?: return null
            return when {
                metadata.hasVideo -> {
                    MediaDescriptor(
                        mediaType = MediaType.VIDEO,
                        mimeType = "video/mp4",
                        sizeBytes = file.length(),
                        durationMs = metadata.durationMs ?: return null,
                        widthPx = metadata.widthPx,
                        heightPx = metadata.heightPx,
                    )
                }

                metadata.hasAudio -> {
                    MediaDescriptor(
                        mediaType = MediaType.AUDIO,
                        mimeType = "audio/mp4",
                        sizeBytes = file.length(),
                        durationMs = metadata.durationMs ?: return null,
                        widthPx = null,
                        heightPx = null,
                    )
                }

                else -> null
            }
        }

        private fun extractAvDescriptor(
            file: File,
            defaultMimeType: String,
        ): MediaDescriptor? {
            val metadata = extractRetrieverMetadata(file) ?: return null
            return when {
                metadata.hasVideo -> {
                    MediaDescriptor(
                        mediaType = MediaType.VIDEO,
                        mimeType = defaultMimeType,
                        sizeBytes = file.length(),
                        durationMs = metadata.durationMs ?: return null,
                        widthPx = metadata.widthPx,
                        heightPx = metadata.heightPx,
                    )
                }

                metadata.hasAudio && defaultMimeType == "video/webm" -> null
                metadata.hasAudio -> {
                    MediaDescriptor(
                        mediaType = MediaType.AUDIO,
                        mimeType = defaultMimeType,
                        sizeBytes = file.length(),
                        durationMs = metadata.durationMs ?: return null,
                        widthPx = null,
                        heightPx = null,
                    )
                }

                else -> null
            }
        }

        private fun extractAudioDescriptor(
            file: File,
            mimeType: String,
        ): MediaDescriptor? {
            val metadata = extractRetrieverMetadata(file) ?: return null
            if (!metadata.hasAudio) {
                return null
            }
            return MediaDescriptor(
                mediaType = MediaType.AUDIO,
                mimeType = mimeType,
                sizeBytes = file.length(),
                durationMs = metadata.durationMs ?: return null,
                widthPx = null,
                heightPx = null,
            )
        }

        private fun extractImageDescriptor(
            file: File,
            mimeType: String,
        ): MediaDescriptor? {
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return null
            }
            return MediaDescriptor(
                mediaType = MediaType.IMAGE,
                mimeType = mimeType,
                sizeBytes = file.length(),
                durationMs = null,
                widthPx = options.outWidth,
                heightPx = options.outHeight,
            )
        }

        private fun extractRetrieverMetadata(file: File): RetrieverMetadata? {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                RetrieverMetadata(
                    hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes",
                    hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                    widthPx = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                    heightPx = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                )
            } catch (_: RuntimeException) {
                null
            } finally {
                retriever.release()
            }
        }

        private fun sniffContainer(file: File): DetectedContainer? {
            val header = ByteArray(32)
            val bytesRead =
                file.inputStream().use { input ->
                    input.read(header)
                }
            if (bytesRead <= 0) {
                return null
            }

            if (header.startsWith(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())) {
                return DetectedContainer.Jpeg
            }
            if (
                header.startsWith(
                    0x89.toByte(),
                    0x50.toByte(),
                    0x4E.toByte(),
                    0x47.toByte(),
                    0x0D.toByte(),
                    0x0A.toByte(),
                    0x1A.toByte(),
                    0x0A.toByte(),
                )
            ) {
                return DetectedContainer.Png
            }
            if (
                header.startsWith(
                    0x52.toByte(),
                    0x49.toByte(),
                    0x46.toByte(),
                    0x46.toByte(),
                ) && header.readAscii(8, 4) == "WEBP"
            ) {
                return DetectedContainer.Webp
            }
            if (
                header.startsWith(
                    0x52.toByte(),
                    0x49.toByte(),
                    0x46.toByte(),
                    0x46.toByte(),
                ) && header.readAscii(8, 4) == "WAVE"
            ) {
                return DetectedContainer.Wav
            }
            if (header.readAscii(4, 4) == "ftyp") {
                val brand = header.readAscii(8, 4)
                return if (brand in HEIC_BRANDS) {
                    DetectedContainer.Heic
                } else {
                    DetectedContainer.IsoBmff
                }
            }
            if (header.startsWith(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte())) {
                return DetectedContainer.Webm
            }
            if (header.readAscii(0, 3) == "ID3" || header.isLikelyMp3Frame()) {
                return DetectedContainer.Mp3
            }
            if (header.isAdtsAac()) {
                return DetectedContainer.Aac
            }

            return null
        }

        private fun copyIntoScopedStorage(
            source: java.io.InputStream,
            destination: java.io.OutputStream,
        ): Long {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0L
            while (true) {
                val read = source.read(buffer)
                if (read == -1) {
                    break
                }
                totalBytes += read
                if (totalBytes > MAX_FILE_BYTES) {
                    throw FileTooLargeException()
                }
                destination.write(buffer, 0, read)
            }
            destination.flush()
            return totalBytes
        }

        private fun deleteScopedCopy(uriString: String) {
            val uri = Uri.parse(uriString)
            if (uri.scheme != ContentResolver.SCHEME_FILE) {
                return
            }
            val file = File(requireNotNull(uri.path))
            if (file.exists() && !file.delete()) {
                file.deleteOnExit()
            }
        }

        private companion object {
            val HEIC_BRANDS = setOf("heic", "heix", "hevc", "heif", "mif1", "msf1")
        }
    }

class MediaPurgeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): ListenableWorker.Result {
        val uriString = inputData.getString(KEY_MEDIA_URI) ?: return ListenableWorker.Result.failure()
        val uri = Uri.parse(uriString)
        if (uri.scheme != ContentResolver.SCHEME_FILE) {
            return ListenableWorker.Result.success()
        }

        return try {
            val file = File(requireNotNull(uri.path))
            if (file.exists()) {
                file.delete()
            }
            ListenableWorker.Result.success()
        } catch (_: IOException) {
            ListenableWorker.Result.retry()
        }
    }

    companion object {
        const val KEY_MEDIA_URI = "media_uri"
    }
}

private data class MediaDescriptor(
    val mediaType: MediaType,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long?,
    val widthPx: Int?,
    val heightPx: Int?,
)

private data class RetrieverMetadata(
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val durationMs: Long?,
    val widthPx: Int?,
    val heightPx: Int?,
)

private sealed interface DetectedContainer {
    data object Jpeg : DetectedContainer

    data object Png : DetectedContainer

    data object Webp : DetectedContainer

    data object Heic : DetectedContainer

    data object Mp3 : DetectedContainer

    data object Wav : DetectedContainer

    data object Aac : DetectedContainer

    data object Webm : DetectedContainer

    data object IsoBmff : DetectedContainer
}

private class FileTooLargeException : IOException()

private fun IOException.isStorageFull(): Boolean {
    val message = generateSequence(this as Throwable?) { it.cause }
        .mapNotNull { it.message }
        .joinToString(separator = " ")
        .lowercase()
    return message.contains("no space left on device") || message.contains("enospc")
}

private fun ByteArray.startsWith(vararg bytes: Byte): Boolean {
    if (size < bytes.size) {
        return false
    }
    return bytes.indices.all { index -> this[index] == bytes[index] }
}

private fun ByteArray.readAscii(
    start: Int,
    length: Int,
): String {
    if (start + length > size) {
        return ""
    }
    return String(copyOfRange(start, start + length), Charsets.US_ASCII)
}

private fun ByteArray.isLikelyMp3Frame(): Boolean {
    if (size < 2) {
        return false
    }
    val first = this[0].toInt() and 0xFF
    val second = this[1].toInt() and 0xFF
    return first == 0xFF && (second and 0xE0) == 0xE0
}

private fun ByteArray.isAdtsAac(): Boolean {
    if (size < 2) {
        return false
    }
    val first = this[0].toInt() and 0xFF
    val second = this[1].toInt() and 0xFF
    return first == 0xFF && (second and 0xF6) == 0xF0
}
