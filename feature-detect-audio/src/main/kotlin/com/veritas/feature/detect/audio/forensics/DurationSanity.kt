package com.veritas.feature.detect.audio.forensics

import com.veritas.feature.detect.audio.decode.DecodedAudio

class DurationSanity {
    fun analyze(decoded: DecodedAudio): DurationSignals =
        DurationSignals(
            tooShort = decoded.durationMs < MIN_RELIABLE_DURATION_MS,
            tooLong = decoded.durationMs > MAX_RELIABLE_DURATION_MS,
            lowSampleRate = decoded.sampleRate < MIN_RELIABLE_SAMPLE_RATE,
            monoPlausible = decoded.channelCount <= MONO_CHANNELS,
        )

    companion object {
        const val MIN_RELIABLE_DURATION_MS = 1_000L
        const val MAX_RELIABLE_DURATION_MS = 60_000L
        const val MIN_RELIABLE_SAMPLE_RATE = 8_000
        private const val MONO_CHANNELS = 1
    }
}

data class DurationSignals(
    val tooShort: Boolean,
    val tooLong: Boolean,
    val lowSampleRate: Boolean,
    val monoPlausible: Boolean,
)
