@file:Suppress("MagicNumber")

package com.veritas.feature.detect.audio.forensics

import com.veritas.feature.detect.audio.decode.DecodedAudio
import java.util.Locale

class CodecFingerprint {
    fun plausibility(decoded: DecodedAudio): Float {
        val mime = decoded.mimeType.orEmpty().lowercase(Locale.US)
        val bitrateKbps = decoded.bitrate?.let { it / BITS_PER_KILOBIT }
        val base =
            when {
                mime.contains("opus") -> scoreBitrate(bitrateKbps, idealLow = 16, idealHigh = 96, floorLow = 8, floorHigh = 160)
                mime.contains("aac") || mime.contains("mp4a") -> scoreBitrate(bitrateKbps, idealLow = 48, idealHigh = 160, floorLow = 24, floorHigh = 256)
                mime.contains("mpeg") || mime.contains("mp3") -> scoreBitrate(bitrateKbps, idealLow = 96, idealHigh = 256, floorLow = 48, floorHigh = 320)
                mime.contains("wav") || mime.contains("raw") -> 0.95f
                mime.contains("vorbis") || mime.contains("ogg") -> scoreBitrate(bitrateKbps, idealLow = 48, idealHigh = 160, floorLow = 24, floorHigh = 256)
                else -> 0.70f
            }
        val sampleRatePenalty = if (decoded.sampleRate < DurationSanity.MIN_RELIABLE_SAMPLE_RATE) 0.25f else 0f
        val channelPenalty = if (decoded.channelCount > 2) 0.10f else 0f
        return (base - sampleRatePenalty - channelPenalty).coerceIn(0f, 1f)
    }

    private fun scoreBitrate(
        bitrateKbps: Int?,
        idealLow: Int,
        idealHigh: Int,
        floorLow: Int,
        floorHigh: Int,
    ): Float {
        if (bitrateKbps == null) return UNKNOWN_BITRATE_SCORE
        return when {
            bitrateKbps in idealLow..idealHigh -> 0.95f
            bitrateKbps in floorLow until idealLow -> 0.70f
            bitrateKbps in (idealHigh + 1)..floorHigh -> 0.80f
            else -> 0.45f
        }
    }

    private companion object {
        private const val BITS_PER_KILOBIT = 1_000
        private const val UNKNOWN_BITRATE_SCORE = 0.75f
    }
}
