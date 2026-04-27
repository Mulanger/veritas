@file:Suppress("MagicNumber", "MaxLineLength", "TooManyFunctions")

package com.veritas.data.detection

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.veritas.domain.detection.ConfidenceRange
import com.veritas.domain.detection.InferenceHardware
import com.veritas.domain.detection.MediaSource
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.Reason
import com.veritas.domain.detection.ScannedMedia
import com.veritas.domain.detection.Verdict
import com.veritas.domain.detection.VerdictOutcome
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val HISTORY_DATABASE_NAME = "veritas_history.db"
private const val HISTORY_LIMIT = 100
private const val THUMBNAIL_MAX_SIZE_PX = 240
private const val THUMBNAIL_JPEG_QUALITY = 75

@Entity(tableName = "history_items")
data class HistoryItemEntity(
    @PrimaryKey val id: String,
    val mediaType: MediaType,
    val mediaMimeType: String,
    val durationMs: Long?,
    val sourcePackage: String?,
    val thumbnailPath: String,
    val verdictOutcome: VerdictOutcome,
    val confidenceLowPct: Int,
    val confidenceHighPct: Int,
    val summary: String,
    val topReasonsJson: String,
    val modelVersionsJson: String,
    val scannedAt: Long,
)

data class HistoryItem(
    val id: String,
    val mediaType: MediaType,
    val mediaMimeType: String,
    val durationMs: Long?,
    val sourcePackage: String?,
    val thumbnailPath: String,
    val verdictOutcome: VerdictOutcome,
    val confidence: ConfidenceRange,
    val summary: String,
    val topReasons: List<Reason>,
    val modelVersions: Map<String, String>,
    val scannedAt: Long,
) {
    fun toVerdict(): Verdict =
        Verdict(
            id = id,
            mediaId = id,
            mediaType = mediaType,
            outcome = verdictOutcome,
            confidence = confidence,
            summary = summary,
            reasons = topReasons,
            modelVersions = modelVersions,
            scannedAt = Instant.fromEpochMilliseconds(scannedAt),
            inferenceHardware = InferenceHardware.MIXED,
            elapsedMs = 0L,
        )
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_items ORDER BY scannedAt DESC")
    fun observeAll(): Flow<List<HistoryItemEntity>>

    @Query("SELECT * FROM history_items WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<HistoryItemEntity?>

    @Query("SELECT * FROM history_items ORDER BY scannedAt DESC")
    suspend fun allOnce(): List<HistoryItemEntity>

    @Query("SELECT * FROM history_items WHERE id = :id LIMIT 1")
    suspend fun byIdOnce(id: String): HistoryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HistoryItemEntity)

    @Query(
        """
        DELETE FROM history_items
        WHERE id NOT IN (
            SELECT id FROM history_items ORDER BY scannedAt DESC LIMIT :limit
        )
        """,
    )
    suspend fun pruneToLimit(limit: Int = HISTORY_LIMIT)

    @Query("DELETE FROM history_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM history_items")
    suspend fun clear()

    @Delete
    suspend fun delete(entity: HistoryItemEntity)

    @Transaction
    suspend fun insertAndPrune(entity: HistoryItemEntity) {
        insert(entity)
        pruneToLimit()
    }
}

class HistoryConverters {
    @TypeConverter
    fun mediaTypeToString(value: MediaType): String = value.name

    @TypeConverter
    fun stringToMediaType(value: String): MediaType = MediaType.valueOf(value)

    @TypeConverter
    fun verdictOutcomeToString(value: VerdictOutcome): String = value.name

    @TypeConverter
    fun stringToVerdictOutcome(value: String): VerdictOutcome = VerdictOutcome.valueOf(value)
}

@Database(
    entities = [HistoryItemEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(HistoryConverters::class)
abstract class VeritasHistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}

@Singleton
class HistoryRepository
    @Inject
    constructor(
        private val historyDao: HistoryDao,
        private val thumbnailStore: ThumbnailStore,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        fun observeHistory(): Flow<List<HistoryItem>> = historyDao.observeAll().map { rows -> rows.map(::toHistoryItem) }

        fun observeHistoryItem(id: String): Flow<HistoryItem?> = historyDao.observeById(id).map { row -> row?.let(::toHistoryItem) }

        suspend fun allOnce(): List<HistoryItem> = historyDao.allOnce().map(::toHistoryItem)

        suspend fun saveScan(
            media: ScannedMedia,
            verdict: Verdict,
        ) = withContext(Dispatchers.IO) {
            val thumbnailPath = thumbnailStore.createThumbnail(media)
            historyDao.insertAndPrune(
                HistoryItemEntity(
                    id = verdict.id,
                    mediaType = media.mediaType,
                    mediaMimeType = media.mimeType,
                    durationMs = media.durationMs,
                    sourcePackage = (media.source as? MediaSource.ShareIntent)?.sourcePackage,
                    thumbnailPath = thumbnailPath,
                    verdictOutcome = verdict.outcome,
                    confidenceLowPct = verdict.confidence.lowPct,
                    confidenceHighPct = verdict.confidence.highPct,
                    summary = verdict.summary,
                    topReasonsJson = json.encodeToString(verdict.reasons.take(3)),
                    modelVersionsJson = json.encodeToString(verdict.modelVersions),
                    scannedAt = verdict.scannedAt.toEpochMilliseconds(),
                ),
            )
            thumbnailStore.pruneTo(historyDao.allOnce().map { row -> row.thumbnailPath }.toSet())
        }

        suspend fun delete(id: String) =
            withContext(Dispatchers.IO) {
                val row = historyDao.byIdOnce(id)
                historyDao.deleteById(id)
                row?.thumbnailPath?.let(thumbnailStore::deleteThumbnail)
            }

        suspend fun clear() {
            allOnce().forEach { item -> thumbnailStore.deleteThumbnail(item.thumbnailPath) }
            historyDao.clear()
        }

        private fun toHistoryItem(entity: HistoryItemEntity): HistoryItem =
            HistoryItem(
                id = entity.id,
                mediaType = entity.mediaType,
                mediaMimeType = entity.mediaMimeType,
                durationMs = entity.durationMs,
                sourcePackage = entity.sourcePackage,
                thumbnailPath = entity.thumbnailPath,
                verdictOutcome = entity.verdictOutcome,
                confidence = ConfidenceRange(entity.confidenceLowPct, entity.confidenceHighPct),
                summary = entity.summary,
                topReasons = json.decodeFromString(entity.topReasonsJson),
                modelVersions = json.decodeFromString(entity.modelVersionsJson),
                scannedAt = entity.scannedAt,
            )
    }

@Singleton
class ThumbnailStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        suspend fun createThumbnail(media: ScannedMedia): String =
            withContext(Dispatchers.IO) {
                val thumbnail =
                    when (media.mediaType) {
                        MediaType.IMAGE -> decodeImageThumbnail(media.uri)
                        MediaType.VIDEO -> decodeVideoThumbnail(media.uri)
                        MediaType.AUDIO -> null
                    } ?: placeholderThumbnail(media.mediaType)
                val destination =
                    File(thumbnailDirectory(), "${media.id}.jpg").apply {
                        parentFile?.mkdirs()
                    }
                destination.outputStream().use { output ->
                    thumbnail.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, output)
                }
                thumbnail.recycle()
                destination.absolutePath
            }

        fun deleteThumbnail(path: String) {
            if (path.isNotBlank()) {
                File(path).delete()
            }
        }

        fun pruneTo(keptPaths: Set<String>) {
            thumbnailDirectory()
                .listFiles()
                ?.filter { file -> file.absolutePath !in keptPaths }
                ?.forEach { file -> file.delete() }
        }

        private fun thumbnailDirectory(): File = File(context.filesDir, "history/thumbnails")

        private fun decodeImageThumbnail(uriString: String): Bitmap? {
            val uri = Uri.parse(uriString)
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            openInput(uri)?.use { input -> BitmapFactory.decodeStream(input, null, options) }
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return null
            }
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize(options.outWidth, options.outHeight)
            return openInput(uri)?.use { input -> BitmapFactory.decodeStream(input, null, options) }?.scaledToThumbnail()
        }

        private fun decodeVideoThumbnail(uriString: String): Bitmap? {
            val filePath = Uri.parse(uriString).path ?: return null
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(filePath)
                retriever.frameAtTime?.scaledToThumbnail()
            } catch (_: RuntimeException) {
                null
            } finally {
                retriever.release()
            }
        }

        private fun openInput(uri: Uri) =
            when (uri.scheme) {
                ContentResolver.SCHEME_FILE -> File(requireNotNull(uri.path)).inputStream()
                else -> context.contentResolver.openInputStream(uri)
            }

        private fun sampleSize(
            width: Int,
            height: Int,
        ): Int {
            var sample = 1
            while (width / sample > THUMBNAIL_MAX_SIZE_PX || height / sample > THUMBNAIL_MAX_SIZE_PX) {
                sample *= 2
            }
            return sample
        }

        private fun Bitmap.scaledToThumbnail(): Bitmap {
            val scale = THUMBNAIL_MAX_SIZE_PX.toFloat() / maxOf(width, height).toFloat()
            if (scale >= 1f) {
                return copy(Bitmap.Config.ARGB_8888, false)
            }
            return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
        }

        private fun placeholderThumbnail(mediaType: MediaType): Bitmap {
            val bitmap = Bitmap.createBitmap(THUMBNAIL_MAX_SIZE_PX, THUMBNAIL_MAX_SIZE_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(17, 19, 24)
                    style = Paint.Style.FILL
                }
            canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
            paint.color = Color.rgb(140, 170, 255)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 34f
            paint.typeface = android.graphics.Typeface.MONOSPACE
            val label = mediaType.name.take(1)
            val bounds = Rect()
            paint.getTextBounds(label, 0, label.length, bounds)
            canvas.drawText(label, bitmap.width / 2f, bitmap.height / 2f - bounds.exactCenterY(), paint)
            return bitmap
        }
    }

@Module
@InstallIn(SingletonComponent::class)
object HistoryPersistenceModule {
    @Provides
    @Singleton
    fun provideHistoryDatabase(
        @ApplicationContext context: Context,
    ): VeritasHistoryDatabase =
        Room.databaseBuilder(
            context,
            VeritasHistoryDatabase::class.java,
            HISTORY_DATABASE_NAME,
        ).build()

    @Provides
    fun provideHistoryDao(database: VeritasHistoryDatabase): HistoryDao = database.historyDao()
}
