package com.veritas.data.detection

import com.veritas.domain.detection.C2PAOutcome
import com.veritas.domain.detection.C2PAResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class C2PADetectorTest {

    @Test
    fun detectFromJpeg_withNoC2PAManifest_returnsNotPresent() {
        val detector = C2PADetector(android.app.Application())
        val tempFile = File.createTempFile("test", ".jpg").apply {
            writeBytes(JPEG_NO_C2PA)
            deleteOnExit()
        }
        val result = kotlinx.coroutines.runBlocking {
            detector.detect(tempFile)
        }
        assertEquals(C2PAOutcome.NOT_PRESENT, result.outcome)
        tempFile.delete()
    }

    @Test
    fun detectFromMp4_withMp4Extension_returnsNotPresent() {
        val detector = C2PADetector(android.app.Application())
        val tempFile = File.createTempFile("test", ".mp4").apply {
            writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x00))
            deleteOnExit()
        }
        val result = kotlinx.coroutines.runBlocking {
            detector.detect(tempFile)
        }
        assertEquals(C2PAOutcome.NOT_PRESENT, result.outcome)
        tempFile.delete()
    }

    @Test
    fun detect_withUnsupportedExtension_returnsNotPresent() {
        val detector = C2PADetector(android.app.Application())
        val tempFile = File.createTempFile("test", ".gif").apply {
            writeBytes(byteArrayOf(0x47, 0x49, 0x46))
            deleteOnExit()
        }
        val result = kotlinx.coroutines.runBlocking {
            detector.detect(tempFile)
        }
        assertEquals(C2PAOutcome.NOT_PRESENT, result.outcome)
        tempFile.delete()
    }

    private val jpegNoC2pa = byteArrayOf(
        0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10,
        0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
        0x01, 0x00, 0x00, 0x01, 0x00, 0x01,
        0x00, 0x00, 0xFF, 0xDB, 0x00, 0x43,
        0x00, 0x08, 0x06, 0x06, 0x07, 0x07,
        0x07, 0x09, 0x09, 0x08, 0x0A, 0x0C,
        0x14, 0x0D, 0x0C, 0x0B, 0x0B, 0x0C,
        0x19, 0x12, 0x13, 0x0F, 0x14, 0x1D,
        0x1A, 0x1F, 0x1E, 0x23, 0x1C, 0x20,
        0x20, 0x27, 0x2C, 0x2E, 0x27, 0x25,
        0x29, 0x2A, 0xFF, 0xD9,
    )
}
