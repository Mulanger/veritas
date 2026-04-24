package com.veritas.app

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.ContextRegistry
import com.veritas.data.detection.C2PADetector
import com.veritas.domain.detection.C2PAOutcome
import com.veritas.domain.detection.C2PAResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class Phase6C2PAVerificationTest {

    companion object {
        private const val TAG = "Phase6C2PAVerification"
    }

    private val appContext: Context
        get() = ContextRegistry.getApplicationContext()

    @Test
    fun c2paExtraction_adobeSignedImage_extractsIssuerAndGenerator() = runBlocking {
        val detector = C2PADetector(appContext)
        val fixture = copyFixtureToCache("adobe-20220124-CA.jpg")

        val result = detector.detect(fixture)

        Log.d(TAG, "=== C2PA Extraction: Adobe CA ===")
        Log.d(TAG, "Result: ${result.javaClass.simpleName} | outcome: ${result.outcome}")
        if (result is C2PAResult.Present) {
            Log.d(TAG, "instanceId: ${result.instanceId}")
            Log.d(TAG, "issuerName: ${result.issuerName}")
            Log.d(TAG, "claimGenerator: ${result.claimGenerator}")
            Log.d(TAG, "signedAt: ${result.signedAt}")
            Log.d(TAG, "actions: ${result.actions}")
        }

        assertTrue("Should be C2PAResult.Present, got ${result.javaClass.simpleName}",
            result is C2PAResult.Present)

        result as C2PAResult.Present
        assertNotNull("instanceId should not be null", result.instanceId)
        assertNotNull("issuerName should not be null", result.issuerName)
        assertNotNull("claimGenerator should not be null", result.claimGenerator)
        assertTrue("issuerName should contain 'Adobe' or 'Test', got: ${result.issuerName}",
            result.issuerName!!.contains("Adobe", ignoreCase = true) ||
            result.issuerName!!.contains("Test", ignoreCase = true))
        assertEquals("C2PA_VALID", C2PAOutcome.VALID.name, result.outcome.name)
    }

    @Test
    fun c2paExtraction_nikonSignedImage_extractsIssuer() = runBlocking {
        val detector = C2PADetector(appContext)
        val fixture = copyFixtureToCache("nikon-20221019-building.jpeg")

        val result = detector.detect(fixture)

        Log.d(TAG, "=== C2PA Extraction: Nikon ===")
        Log.d(TAG, "Result: ${result.javaClass.simpleName} | outcome: ${result.outcome}")
        if (result is C2PAResult.Present) {
            Log.d(TAG, "instanceId: ${result.instanceId}")
            Log.d(TAG, "issuerName: ${result.issuerName}")
            Log.d(TAG, "claimGenerator: ${result.claimGenerator}")
        }

        assertTrue("Should be C2PAResult.Present, got ${result.javaClass.simpleName}",
            result is C2PAResult.Present)

        result as C2PAResult.Present
        assertNotNull("instanceId should not be null", result.instanceId)
        assertNotNull("issuerName should not be null", result.issuerName)
        assertEquals("C2PA_VALID", C2PAOutcome.VALID.name, result.outcome.name)
    }

    @Test
    fun c2paExtraction_truepicSignedImage_extractsTruepicIssuer() = runBlocking {
        val detector = C2PADetector(appContext)
        val fixture = copyFixtureToCache("truepic-20230212-camera.jpg")

        val result = detector.detect(fixture)

        Log.d(TAG, "=== C2PA Extraction: Truepic ===")
        Log.d(TAG, "Result: ${result.javaClass.simpleName} | outcome: ${result.outcome}")
        if (result is C2PAResult.Present) {
            Log.d(TAG, "instanceId: ${result.instanceId}")
            Log.d(TAG, "issuerName: ${result.issuerName}")
            Log.d(TAG, "claimGenerator: ${result.claimGenerator}")
        }

        assertTrue("Should be C2PAResult.Present, got ${result.javaClass.simpleName}",
            result is C2PAResult.Present)

        result as C2PAResult.Present
        assertNotNull("instanceId should not be null", result.instanceId)
        assertNotNull("issuerName should not be null", result.issuerName)
        assertEquals("C2PA_VALID", C2PAOutcome.VALID.name, result.outcome.name)
    }

    @Test
    fun c2paValidation_unsignedJpeg_returnsNotPresent() = runBlocking {
        val detector = C2PADetector(appContext)
        val unsignedJpeg = File(appContext.cacheDir, "unsigned_test.jpg").apply {
            writeBytes(UNSIGNED_JPEG)
        }

        val result = detector.detect(unsignedJpeg)

        Log.d(TAG, "=== C2PA Validation: Unsigned JPEG ===")
        Log.d(TAG, "Result: ${result.javaClass.simpleName} | outcome: ${result.outcome}")

        assertEquals("Unsigned content should return NOT_PRESENT",
            C2PAOutcome.NOT_PRESENT, result.outcome)
    }

    private fun copyFixtureToCache(fileName: String): File {
        val dest = File(appContext.cacheDir, "c2pa_fixtures/$fileName")
        if (dest.exists()) return dest
        dest.parentFile?.mkdirs()
        appContext.assets.open("test_fixtures/$fileName").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    private val UNSIGNED_JPEG = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
        0x00.toByte(), 0x10.toByte(), 0x4A.toByte(), 0x46.toByte(),
        0x49.toByte(), 0x46.toByte(), 0x00.toByte(), 0x01.toByte(),
        0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
        0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(),
        0xFF.toByte(), 0xDB.toByte(), 0x00.toByte(), 0x43.toByte(),
        0x00.toByte(), 0x08.toByte(), 0x06.toByte(), 0x06.toByte(),
        0x07.toByte(), 0x07.toByte(), 0x07.toByte(), 0x09.toByte(),
        0x09.toByte(), 0x08.toByte(), 0x0A.toByte(), 0x0C.toByte(),
        0x14.toByte(), 0x0D.toByte(), 0x0C.toByte(), 0x0B.toByte(),
        0x0B.toByte(), 0x0C.toByte(), 0x19.toByte(), 0x12.toByte(),
        0x13.toByte(), 0x0F.toByte(), 0x14.toByte(), 0x1D.toByte(),
        0x1A.toByte(), 0x1F.toByte(), 0x1E.toByte(), 0x23.toByte(),
        0x1C.toByte(), 0x20.toByte(), 0x20.toByte(), 0x27.toByte(),
        0x2C.toByte(), 0x2E.toByte(), 0x27.toByte(), 0x25.toByte(),
        0x29.toByte(), 0x2A.toByte(), 0xFF.toByte(), 0xD9.toByte(),
    )
}