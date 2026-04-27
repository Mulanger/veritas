package com.veritas.feature.detect.audio.decode

import org.junit.Assert.assertEquals
import org.junit.Test

class PcmConverterTest {
    @Test
    fun convertsPcm16StereoToMonoFloat() {
        val bytes = byteArrayOf(
            0x00, 0x40,
            0x00, 0x40,
            0x00, 0x80.toByte(),
            0x00, 0x00,
        )
        val decoded = DecodedAudio(
            pcmBytes = bytes,
            sampleRate = 16_000,
            channelCount = 2,
            durationMs = 0,
            mimeType = "audio/raw",
            bitrate = null,
        )

        val output = PcmConverter().toMonoFloat(decoded)

        assertEquals(2, output.size)
        assertEquals(0.5f, output[0], EPSILON)
        assertEquals(-0.5f, output[1], EPSILON)
    }

    @Test
    fun resamplesWithLinearInterpolation() {
        val converter = PcmConverter(targetSampleRate = 4)
        val decoded = DecodedAudio(
            pcmBytes = byteArrayOf(
                0x00, 0x00,
                0x00, 0x40,
                0x00, 0x00,
            ),
            sampleRate = 2,
            channelCount = 1,
            durationMs = 0,
            mimeType = "audio/raw",
            bitrate = null,
        )

        val output = converter.toMonoFloat(decoded)

        assertEquals(6, output.size)
        assertEquals(0f, output[0], EPSILON)
        assertEquals(0.4f, output[2], EPSILON)
        assertEquals(0f, output[5], EPSILON)
    }

    private companion object {
        private const val EPSILON = 0.0001f
    }
}
