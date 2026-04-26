@file:Suppress("MagicNumber")

package com.veritas.feature.detect.audio.decode

import kotlin.math.roundToInt

class PcmConverter(
    private val targetSampleRate: Int = TARGET_SAMPLE_RATE,
) {
    fun toMonoFloat(decoded: DecodedAudio): FloatArray {
        require(decoded.sampleRate > 0) { "Sample rate must be positive." }
        require(decoded.channelCount > 0) { "Channel count must be positive." }
        val source = decodeInterleaved(decoded)
        val mono = downmixToMono(source, decoded.channelCount)
        return resampleLinear(mono, decoded.sampleRate, targetSampleRate)
    }

    private fun decodeInterleaved(decoded: DecodedAudio): FloatArray =
        when (decoded.pcmEncoding) {
            PcmEncoding.Pcm8Bit -> decodePcm8(decoded.pcmBytes)
            PcmEncoding.Pcm16Bit -> decodePcm16Le(decoded.pcmBytes)
            PcmEncoding.PcmFloat -> decodeFloatLe(decoded.pcmBytes)
        }

    private fun decodePcm8(bytes: ByteArray): FloatArray =
        FloatArray(bytes.size) { index ->
            ((bytes[index].toInt() and BYTE_MASK) - PCM8_ZERO) / PCM8_ZERO.toFloat()
        }

    private fun decodePcm16Le(bytes: ByteArray): FloatArray {
        val samples = bytes.size / PCM16_BYTES
        return FloatArray(samples) { index ->
            val byteIndex = index * PCM16_BYTES
            val value = (bytes[byteIndex].toInt() and BYTE_MASK) or (bytes[byteIndex + 1].toInt() shl BYTE_BITS)
            value.toShort() / PCM16_SCALE
        }
    }

    private fun decodeFloatLe(bytes: ByteArray): FloatArray {
        val samples = bytes.size / FLOAT_BYTES
        return FloatArray(samples) { index ->
            val byteIndex = index * FLOAT_BYTES
            val bits = (bytes[byteIndex].toInt() and BYTE_MASK) or
                ((bytes[byteIndex + 1].toInt() and BYTE_MASK) shl BYTE_BITS) or
                ((bytes[byteIndex + 2].toInt() and BYTE_MASK) shl (BYTE_BITS * 2)) or
                ((bytes[byteIndex + 3].toInt() and BYTE_MASK) shl (BYTE_BITS * 3))
            Float.fromBits(bits).coerceIn(MIN_FLOAT_SAMPLE, MAX_FLOAT_SAMPLE)
        }
    }

    private fun downmixToMono(
        interleaved: FloatArray,
        channelCount: Int,
    ): FloatArray {
        if (channelCount == MONO_CHANNELS) return interleaved
        val frames = interleaved.size / channelCount
        return FloatArray(frames) { frame ->
            var sum = 0f
            for (channel in 0 until channelCount) {
                sum += interleaved[frame * channelCount + channel]
            }
            (sum / channelCount).coerceIn(MIN_FLOAT_SAMPLE, MAX_FLOAT_SAMPLE)
        }
    }

    internal fun resampleLinear(
        samples: FloatArray,
        sourceRate: Int,
        destinationRate: Int,
    ): FloatArray {
        if (samples.isEmpty()) return samples
        if (sourceRate == destinationRate) return samples.copyOf()
        val outputSize = (samples.size.toDouble() * destinationRate / sourceRate).roundToInt().coerceAtLeast(1)
        if (outputSize == 1) return floatArrayOf(samples.first())
        val scale = (samples.lastIndex).toDouble() / (outputSize - 1).toDouble()
        return FloatArray(outputSize) { outputIndex ->
            val sourcePosition = outputIndex * scale
            val left = sourcePosition.toInt()
            val right = (left + 1).coerceAtMost(samples.lastIndex)
            val fraction = (sourcePosition - left).toFloat()
            samples[left] + (samples[right] - samples[left]) * fraction
        }
    }

    companion object {
        const val TARGET_SAMPLE_RATE = 16_000

        private const val BYTE_MASK = 0xFF
        private const val BYTE_BITS = 8
        private const val PCM8_ZERO = 128
        private const val PCM16_BYTES = 2
        private const val PCM16_SCALE = 32768f
        private const val FLOAT_BYTES = 4
        private const val MONO_CHANNELS = 1
        private const val MIN_FLOAT_SAMPLE = -1f
        private const val MAX_FLOAT_SAMPLE = 1f
    }
}
