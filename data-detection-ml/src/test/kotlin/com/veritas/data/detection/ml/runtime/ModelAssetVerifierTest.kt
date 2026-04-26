package com.veritas.data.detection.ml.runtime

import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyPairGenerator
import java.security.Signature

@RunWith(RobolectricTestRunner::class)
class ModelAssetVerifierTest {
    private val verifier = ModelAssetVerifier()

    @Test
    fun sha256_returnsExpectedDigest() {
        val digest = verifier.sha256("veritas".toByteArray())

        assertEquals("b068c5093aed09a10a9732de95a450c094e59f0ac8afede78474b8c75eda95b3", digest)
    }

    @Test
    fun verifyEd25519Signature_acceptsValidModelBytes() {
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val payload = "phase-7-model-bytes".toByteArray()
        val signatureBytes = sign(payload, keyPair.private)
        val publicKey = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

        assertTrue(verifier.verifyEd25519Signature(payload, signatureBytes, publicKey))
    }

    @Test
    fun verifyEd25519Signature_rejectsTamperedModelBytes() {
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val payload = "phase-7-model-bytes".toByteArray()
        val signatureBytes = sign(payload, keyPair.private)
        val publicKey = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        val tamperedPayload = payload.clone().also { it[0] = (it[0] + 1).toByte() }

        assertFalse(verifier.verifyEd25519Signature(tamperedPayload, signatureBytes, publicKey))
    }

    private fun sign(
        payload: ByteArray,
        privateKey: java.security.PrivateKey,
    ): ByteArray {
        val signature = Signature.getInstance("Ed25519")
        signature.initSign(privateKey)
        signature.update(payload)
        return signature.sign()
    }
}
