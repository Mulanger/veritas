package com.veritas.app

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.data.detection.C2PADetector
import com.veritas.data.detection.C2PATrustPolicy
import com.veritas.data.detection.FakeDetectionPipeline
import com.veritas.data.detection.ProvenancePipeline
import com.veritas.data.detection.SynthIDDetector
import com.veritas.domain.detection.C2PAOutcome
import com.veritas.domain.detection.C2PAResult
import com.veritas.domain.detection.MediaSource
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.ReasonCode
import com.veritas.domain.detection.ScanStage
import com.veritas.domain.detection.ScannedMedia
import com.veritas.domain.detection.VerdictOutcome
import java.io.File
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase6C2PAVerificationTest {

    companion object {
        private const val TAG = "Phase6C2PAVerification"
    }

    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    private fun detector(policy: C2PATrustPolicy = C2PATrustPolicy.fixturePolicy()): C2PADetector =
        C2PADetector(appContext, policy)

    private fun pipeline(policy: C2PATrustPolicy = C2PATrustPolicy.fixturePolicy()): ProvenancePipeline =
        ProvenancePipeline(
            appContext = appContext,
            c2paDetector = detector(policy),
            synthIDDetector = SynthIDDetector(appContext),
            fakeDetectionPipeline = FakeDetectionPipeline(appContext),
        )

    private fun copyFixtureToCache(fileName: String): File {
        val dest = File(appContext.cacheDir, "c2pa_fixtures/$fileName")
        if (dest.exists()) return dest
        dest.parentFile?.mkdirs()
        testContext.assets.open("test_fixtures/$fileName").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    private fun scannedImage(file: File): ScannedMedia =
        ScannedMedia(
            id = file.nameWithoutExtension,
            uri = file.toURI().toString(),
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            sizeBytes = file.length(),
            durationMs = null,
            widthPx = null,
            heightPx = null,
            source = MediaSource.FilePicker,
            ingestedAt = Clock.System.now(),
        )

    @Test
    fun c2paValidation_adobeFixture_returnsPresent() = runBlocking {
        val fixture = copyFixtureToCache("adobe-20220124-CA.jpg")

        val result = detector().detect(fixture)

        logResult("Adobe CA", result)
        assertTrue("Should be C2PAResult.Present, got ${result.javaClass.simpleName}", result is C2PAResult.Present)

        result as C2PAResult.Present
        assertNotNull("instanceId should not be null", result.instanceId)
        assertEquals("C2PA Test Signing Cert", result.issuerName)
        assertEquals("make_test_images/0.16.1 c2pa-rs/0.16.1", result.claimGenerator)
        assertNotNull("signedAt should not be null", result.signedAt)
        assertEquals(C2PAOutcome.VALID, result.outcome)
    }

    @Test
    fun c2paValidation_adobeCaFixture_returnsPresent() = runBlocking {
        val fixture = copyFixtureToCache("adobe-20220124-CIE-sig-CA.jpg")

        val result = detector().detect(fixture)

        logResult("Adobe CIE CA", result)
        assertTrue("Should be C2PAResult.Present, got ${result.javaClass.simpleName}", result is C2PAResult.Present)

        result as C2PAResult.Present
        assertNotNull("instanceId should not be null", result.instanceId)
        assertNotNull("issuerName should not be null", result.issuerName)
        assertNotNull("claimGenerator should not be null", result.claimGenerator)
        assertEquals(C2PAOutcome.VALID, result.outcome)
    }

    @Test
    fun c2paValidation_nikonFixture_returnsPresent() = runBlocking {
        val fixture = copyFixtureToCache("nikon-20221019-building.jpeg")

        val result = detector().detect(fixture)

        logResult("Nikon", result)
        assertTrue("Should be C2PAResult.Present, got ${result.javaClass.simpleName}", result is C2PAResult.Present)

        result as C2PAResult.Present
        assertEquals("NIKON CORPORATION", result.issuerName)
        assertNotNull("instanceId should not be null", result.instanceId)
        assertNotNull("claimGenerator should not be null", result.claimGenerator)
        assertEquals(C2PAOutcome.VALID, result.outcome)
    }

    @Test
    fun c2paValidation_truepicFixture_returnsPresent() = runBlocking {
        val fixture = copyFixtureToCache("truepic-20230212-camera.jpg")

        val result = detector().detect(fixture)

        logResult("Truepic", result)
        assertTrue("Should be C2PAResult.Present, got ${result.javaClass.simpleName}", result is C2PAResult.Present)

        result as C2PAResult.Present
        assertEquals("Truepic", result.issuerName)
        assertNotNull("instanceId should not be null", result.instanceId)
        assertNotNull("claimGenerator should not be null", result.claimGenerator)
        assertEquals(C2PAOutcome.VALID, result.outcome)
    }

    @Test
    fun c2paValidation_tamperedImage_returnsInvalid() = runBlocking {
        val original = copyFixtureToCache("adobe-20220124-CA.jpg")
        val tampered = tamperFixture(original)

        val result = detector().detect(tampered)

        logResult("Tampered Adobe CA", result)
        assertTrue("Tampered content should return C2PAResult.Invalid, got ${result.javaClass.simpleName}", result is C2PAResult.Invalid)
        assertEquals(C2PAOutcome.INVALID, result.outcome)
        assertTrue(
            "Invalid reason should mention validation failure, got: ${(result as? C2PAResult.Invalid)?.reason}",
            (result as? C2PAResult.Invalid)?.reason?.contains("assertion.hashedURI.mismatch", ignoreCase = true) == true,
        )
    }

    @Test
    fun c2paValidation_untrustedIssuer_returnsInvalid() = runBlocking {
        val fixture = copyFixtureToCache("adobe-20220124-CA.jpg")

        val result = detector(C2PATrustPolicy.strictPolicy()).detect(fixture)

        logResult("Untrusted Adobe CA", result)
        assertTrue("Untrusted signed content should return Invalid, got ${result.javaClass.simpleName}", result is C2PAResult.Invalid)
        assertTrue(
            "Invalid reason should mention issuer trust, got ${(result as? C2PAResult.Invalid)?.reason}",
            (result as? C2PAResult.Invalid)?.reason?.contains("issuer is not trusted", ignoreCase = true) == true,
        )
    }

    @Test
    fun c2paValidation_expiredCredential_returnsInvalidWhenPolicyDisallowsExpired() = runBlocking {
        val fixture = copyFixtureToCache("nikon-20221019-building.jpeg")
        val strictNikonPolicy = C2PATrustPolicy.strictPolicy(trustedIssuers = setOf("NIKON CORPORATION"))

        val result = detector(strictNikonPolicy).detect(fixture)

        logResult("Expired Nikon", result)
        assertTrue("Expired credential should return Invalid, got ${result.javaClass.simpleName}", result is C2PAResult.Invalid)
        assertTrue(
            "Invalid reason should mention signingCredential.expired, got ${(result as? C2PAResult.Invalid)?.reason}",
            (result as? C2PAResult.Invalid)?.reason?.contains("signingCredential.expired", ignoreCase = true) == true,
        )
    }

    @Test
    fun provenancePipeline_signedAdobeFixture_returnsVerifiedAuthenticVerdict() = runBlocking {
        val fixture = copyFixtureToCache("adobe-20220124-CA.jpg")

        val verdict = pipeline().scan(scannedImage(fixture)).filterIsInstance<ScanStage.VerdictReady>().first().verdict

        Log.d(TAG, "Pipeline valid verdict: ${verdict.outcome} | reasons: ${verdict.reasons.map { it.code }}")
        assertEquals(VerdictOutcome.VERIFIED_AUTHENTIC, verdict.outcome)
        assertTrue(verdict.reasons.any { it.code == ReasonCode.C2PA_VERIFIED })
    }

    @Test
    fun provenancePipeline_tamperedAdobeFixture_returnsInvalidSignatureReason() = runBlocking {
        val original = copyFixtureToCache("adobe-20220124-CA.jpg")
        val tampered = tamperFixture(original)

        val verdict = pipeline().scan(scannedImage(tampered)).filterIsInstance<ScanStage.VerdictReady>().first().verdict

        Log.d(TAG, "Pipeline tampered verdict: ${verdict.outcome} | reasons: ${verdict.reasons.map { it.code }}")
        assertEquals(VerdictOutcome.LIKELY_SYNTHETIC, verdict.outcome)
        assertTrue(verdict.reasons.any { it.code == ReasonCode.C2PA_INVALID_SIGNATURE })
    }

    @Test
    fun c2paValidation_unsignedJpeg_returnsNotPresent() = runBlocking {
        val unsignedJpeg = File(appContext.cacheDir, "unsigned_test.jpg").apply {
            writeBytes(UNSIGNED_JPEG)
        }

        val result = detector().detect(unsignedJpeg)

        logResult("Unsigned JPEG", result)
        assertEquals("Unsigned content should return NOT_PRESENT", C2PAOutcome.NOT_PRESENT, result.outcome)
    }

    private fun tamperFixture(original: File): File =
        File(appContext.cacheDir, "${original.nameWithoutExtension}_tampered.jpg").apply {
            val bytes = original.readBytes()
            bytes[5000] = (bytes[5000].toInt() xor 0xFF).toByte()
            writeBytes(bytes)
        }

    private fun logResult(label: String, result: C2PAResult) {
        Log.d(TAG, "=== C2PA Validation: $label ===")
        Log.d(TAG, "Result: ${result.javaClass.simpleName} | outcome: ${result.outcome}")
        when (result) {
            is C2PAResult.Present -> {
                Log.d(TAG, "instanceId: ${result.instanceId}")
                Log.d(TAG, "issuerName: ${result.issuerName}")
                Log.d(TAG, "claimGenerator: ${result.claimGenerator}")
                Log.d(TAG, "signedAt: ${result.signedAt}")
                Log.d(TAG, "actions: ${result.actions}")
            }
            is C2PAResult.Invalid -> Log.d(TAG, "Invalid reason: ${result.reason}")
            is C2PAResult.Revoked -> Log.d(TAG, "Revoked reason: ${result.reason}")
            C2PAResult.NotPresent -> Unit
        }
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
