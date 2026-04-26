package com.veritas.feature.detect.audio.forensics

import com.veritas.feature.detect.audio.decode.DecodedAudio
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioForensicsTest {
    @Test
    fun durationSanity_flagsShortAndLowSampleRateAudio() {
        val signals = DurationSanity().analyze(
            DecodedAudio(
                pcmBytes = ByteArray(100),
                sampleRate = 7_000,
                channelCount = 1,
                durationMs = 500,
                mimeType = "audio/raw",
                bitrate = null,
            ),
        )

        assertTrue(signals.tooShort)
        assertTrue(signals.lowSampleRate)
        assertFalse(signals.tooLong)
    }

    @Test
    fun codecFingerprint_keepsCommonVoiceNoteBitratePlausible() {
        val score = CodecFingerprint().plausibility(
            DecodedAudio(
                pcmBytes = ByteArray(100),
                sampleRate = 48_000,
                channelCount = 1,
                durationMs = 5_000,
                mimeType = "audio/opus",
                bitrate = 32_000,
            ),
        )

        assertTrue(score > 0.8f)
    }
}
