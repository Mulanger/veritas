@file:Suppress("MagicNumber", "NestedBlockDepth", "ReturnCount", "TooGenericExceptionCaught")

package com.veritas.feature.detect.audio.decode

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

class AudioDecoder {
    suspend fun decode(file: File): DecodedAudio = withContext(Dispatchers.Default) {
        require(file.exists()) { "Audio file does not exist: ${file.absolutePath}" }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = findFirstAudioTrack(extractor)
            require(trackIndex >= 0) { "No audio track found in ${file.absolutePath}" }
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mimeType = inputFormat.getString(MediaFormat.KEY_MIME)
            require(!mimeType.isNullOrBlank()) { "Audio track has no MIME type." }
            if (mimeType == MIME_AUDIO_RAW) {
                decodeRawTrack(extractor, inputFormat, mimeType)
            } else {
                decodeCompressedTrack(extractor, inputFormat, mimeType)
            }
        } finally {
            extractor.release()
        }
    }

    private fun findFirstAudioTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mimeType = format.getString(MediaFormat.KEY_MIME)
            if (mimeType?.startsWith(AUDIO_MIME_PREFIX) == true) {
                return index
            }
        }
        return NO_TRACK
    }

    private fun decodeRawTrack(
        extractor: MediaExtractor,
        format: MediaFormat,
        mimeType: String,
    ): DecodedAudio {
        val pcm = ByteArrayOutputStream()
        val sampleBuffer = ByteBuffer.allocateDirect(RAW_READ_BUFFER_BYTES)
        while (true) {
            sampleBuffer.clear()
            val size = extractor.readSampleData(sampleBuffer, 0)
            if (size < 0) break
            val chunk = ByteArray(size)
            sampleBuffer.rewind()
            sampleBuffer.get(chunk)
            pcm.write(chunk)
            extractor.advance()
        }
        val sampleRate = format.intOrDefault(MediaFormat.KEY_SAMPLE_RATE, DEFAULT_SAMPLE_RATE)
        val channelCount = format.intOrDefault(MediaFormat.KEY_CHANNEL_COUNT, DEFAULT_CHANNEL_COUNT)
        val encoding = format.pcmEncodingOrDefault()
        return DecodedAudio(
            pcmBytes = pcm.toByteArray(),
            sampleRate = sampleRate,
            channelCount = channelCount,
            durationMs = durationFromPcmBytes(pcm.size(), sampleRate, channelCount, encoding),
            mimeType = mimeType,
            bitrate = format.intOrNull(MediaFormat.KEY_BIT_RATE),
            pcmEncoding = encoding,
        )
    }

    private fun decodeCompressedTrack(
        extractor: MediaExtractor,
        format: MediaFormat,
        mimeType: String,
    ): DecodedAudio {
        val codec = MediaCodec.createDecoderByType(mimeType)
        val output = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var outputFormat = format
        var inputDone = false
        var outputDone = false

        try {
            codec.configure(format, null, null, 0)
            codec.start()
            while (!outputDone) {
                if (!inputDone) {
                    inputDone = queueInputBuffer(extractor, codec)
                }
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputIndex >= 0 -> {
                        drainOutputBuffer(codec, outputIndex, bufferInfo, output)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = codec.outputFormat
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }

        val sampleRate = outputFormat.intOrDefault(MediaFormat.KEY_SAMPLE_RATE, format.intOrDefault(MediaFormat.KEY_SAMPLE_RATE, DEFAULT_SAMPLE_RATE))
        val channelCount = outputFormat.intOrDefault(MediaFormat.KEY_CHANNEL_COUNT, format.intOrDefault(MediaFormat.KEY_CHANNEL_COUNT, DEFAULT_CHANNEL_COUNT))
        val encoding = outputFormat.pcmEncodingOrDefault()
        return DecodedAudio(
            pcmBytes = output.toByteArray(),
            sampleRate = sampleRate,
            channelCount = channelCount,
            durationMs = durationFromPcmBytes(output.size(), sampleRate, channelCount, encoding),
            mimeType = mimeType,
            bitrate = format.intOrNull(MediaFormat.KEY_BIT_RATE),
            pcmEncoding = encoding,
        )
    }

    private fun queueInputBuffer(
        extractor: MediaExtractor,
        codec: MediaCodec,
    ): Boolean {
        val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
        if (inputIndex < 0) return false
        val inputBuffer = codec.getInputBuffer(inputIndex) ?: return false
        inputBuffer.clear()
        val sampleSize = extractor.readSampleData(inputBuffer, 0)
        return if (sampleSize < 0) {
            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            true
        } else {
            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime.coerceAtLeast(0L), extractor.sampleFlags)
            extractor.advance()
            false
        }
    }

    private fun drainOutputBuffer(
        codec: MediaCodec,
        outputIndex: Int,
        bufferInfo: MediaCodec.BufferInfo,
        output: ByteArrayOutputStream,
    ) {
        if (bufferInfo.size > 0) {
            val outputBuffer = codec.getOutputBuffer(outputIndex) ?: return
            outputBuffer.position(bufferInfo.offset)
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
            val chunk = ByteArray(bufferInfo.size)
            outputBuffer.get(chunk)
            output.write(chunk)
        }
        codec.releaseOutputBuffer(outputIndex, false)
    }

    private fun durationFromPcmBytes(
        byteCount: Int,
        sampleRate: Int,
        channelCount: Int,
        encoding: PcmEncoding,
    ): Long {
        val bytesPerFrame = channelCount * encoding.bytesPerSample()
        if (sampleRate <= 0 || bytesPerFrame <= 0) return 0L
        val frames = byteCount / bytesPerFrame
        return frames * MILLIS_PER_SECOND / sampleRate
    }

    private fun PcmEncoding.bytesPerSample(): Int =
        when (this) {
            PcmEncoding.Pcm8Bit -> 1
            PcmEncoding.Pcm16Bit -> 2
            PcmEncoding.PcmFloat -> 4
        }

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) {
            try {
                getInteger(key)
            } catch (e: ClassCastException) {
                null
            }
        } else {
            null
        }

    private fun MediaFormat.intOrDefault(
        key: String,
        defaultValue: Int,
    ): Int = intOrNull(key) ?: defaultValue

    private fun MediaFormat.pcmEncodingOrDefault(): PcmEncoding =
        when (intOrNull(MediaFormat.KEY_PCM_ENCODING)) {
            AudioFormat.ENCODING_PCM_8BIT -> PcmEncoding.Pcm8Bit
            AudioFormat.ENCODING_PCM_FLOAT -> PcmEncoding.PcmFloat
            else -> PcmEncoding.Pcm16Bit
        }

    companion object {
        private const val AUDIO_MIME_PREFIX = "audio/"
        private const val MIME_AUDIO_RAW = "audio/raw"
        private const val NO_TRACK = -1
        private const val TIMEOUT_US = 10_000L
        private const val RAW_READ_BUFFER_BYTES = 64 * 1024
        private const val DEFAULT_SAMPLE_RATE = 44_100
        private const val DEFAULT_CHANNEL_COUNT = 1
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
