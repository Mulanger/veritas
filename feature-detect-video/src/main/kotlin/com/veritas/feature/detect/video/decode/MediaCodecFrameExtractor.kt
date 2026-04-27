@file:Suppress("TooGenericExceptionCaught")

package com.veritas.feature.detect.video.decode

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlin.math.abs

class MediaCodecFrameExtractor @Inject constructor() {
    fun extract(
        file: File,
        targetFrameCount: Int = FrameSampler.DEFAULT_TARGET_FRAMES,
    ): ExtractedVideoFrames {
        runCatching { extractWithMediaCodec(file, targetFrameCount) }
            .getOrNull()
            ?.takeIf { it.frames.isNotEmpty() }
            ?.let { return it }
        return extractWithRetriever(file, targetFrameCount)
    }

    private fun extractWithMediaCodec(
        file: File,
        targetFrameCount: Int,
    ): ExtractedVideoFrames {
        val metadata = readMetadata(file)
        val targetTimestampsUs = FrameSampler.sampleTimestamps(metadata.durationMs, targetFrameCount)
            .map { it * MICROS_PER_MILLI }
        if (targetTimestampsUs.isEmpty()) {
            return ExtractedVideoFrames(metadata = metadata, frames = emptyList())
        }

        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        } ?: error("No video track found")
        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Video MIME missing")
        val decoder = MediaCodec.createDecoderByType(mime)
        val frames = mutableListOf<ExtractedVideoFrame>()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var targetIndex = 0

        try {
            decoder.configure(format, null, null, 0)
            decoder.start()
            while (!outputDone && targetIndex < targetTimestampsUs.size) {
                if (!inputDone) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                        val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                when {
                    outputBufferIndex >= 0 -> {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                        if (bufferInfo.size > 0 && bufferInfo.presentationTimeUs >= targetTimestampsUs[targetIndex]) {
                            decoder.getOutputImage(outputBufferIndex)?.use { image ->
                                frames += ExtractedVideoFrame(
                                    timestampMs = bufferInfo.presentationTimeUs / MICROS_PER_MILLI,
                                    bitmap = image.toBitmap(),
                                )
                            }
                            targetIndex++
                            while (targetIndex < targetTimestampsUs.size &&
                                abs(bufferInfo.presentationTimeUs - targetTimestampsUs[targetIndex]) < TARGET_DUPLICATE_WINDOW_US
                            ) {
                                targetIndex++
                            }
                        }
                        decoder.releaseOutputBuffer(outputBufferIndex, false)
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                }
            }
        } finally {
            runCatching { decoder.stop() }
            decoder.release()
            extractor.release()
        }
        return ExtractedVideoFrames(metadata = metadata, frames = frames)
    }

    private fun extractWithRetriever(
        file: File,
        targetFrameCount: Int,
    ): ExtractedVideoFrames {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)
        return retriever.useAndExtract(targetFrameCount)
    }

    private fun MediaMetadataRetriever.useAndExtract(targetFrameCount: Int): ExtractedVideoFrames {
        try {
            val metadata = metadataFromRetriever()
            val timestamps = FrameSampler.sampleTimestamps(metadata.durationMs, targetFrameCount)
            val frames = timestamps.mapNotNull { timestampMs ->
                runCatching {
                    getFrameAtTime(timestampMs * MICROS_PER_MILLI, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                        ?.let { ExtractedVideoFrame(timestampMs = timestampMs, bitmap = it) }
                }.getOrNull()
            }
            return ExtractedVideoFrames(metadata = metadata, frames = frames)
        } finally {
            release()
        }
    }

    private fun readMetadata(file: File): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)
        return try {
            retriever.metadataFromRetriever()
        } finally {
            retriever.release()
        }
    }

    private fun MediaMetadataRetriever.metadataFromRetriever(): VideoMetadata =
        VideoMetadata(
            durationMs = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
            width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
            height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
            rotationDegrees = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,
            bitrate = extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull(),
            mimeType = extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
        )

    private fun Image.toBitmap(): Bitmap {
        val argb = IntArray(width * height)
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        for (row in 0 until height) {
            for (col in 0 until width) {
                val y = yBuffer.getUnsigned(row * yPlane.rowStride + col * yPlane.pixelStride)
                val chromaRow = row / 2
                val chromaCol = col / 2
                val u = uBuffer.getUnsigned(chromaRow * uPlane.rowStride + chromaCol * uPlane.pixelStride) - 128
                val v = vBuffer.getUnsigned(chromaRow * vPlane.rowStride + chromaCol * vPlane.pixelStride) - 128
                val r = (y + 1.402f * v).toInt().coerceIn(0, 255)
                val g = (y - 0.344136f * u - 0.714136f * v).toInt().coerceIn(0, 255)
                val b = (y + 1.772f * u).toInt().coerceIn(0, 255)
                argb[row * width + col] = (ALPHA_MASK or (r shl 16) or (g shl 8) or b)
            }
        }
        return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun ByteBuffer.getUnsigned(index: Int): Int = get(index).toInt() and BYTE_MASK

    private companion object {
        private const val MICROS_PER_MILLI = 1_000L
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val TARGET_DUPLICATE_WINDOW_US = 20_000L
        private const val BYTE_MASK = 0xFF
        private const val ALPHA_MASK = -0x1000000
    }
}
