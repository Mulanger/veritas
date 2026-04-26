@file:Suppress("TooGenericExceptionCaught")

package com.veritas.data.detection.ml.runtime

import android.content.Context
import android.util.Base64
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

data class LoadedModelAsset(
    val id: String,
    val version: String,
    val buffer: ByteBuffer,
    val sha256: String,
    val sizeBytes: Long,
)

data class ModelAssetSpec(
    val id: String,
    val version: String,
    val assetPath: String,
    val signatureAssetPath: String,
    val expectedSha256: String,
    val publicKeyBase64: String,
)

class ModelAssetVerifier {
    fun loadVerified(
        context: Context,
        spec: ModelAssetSpec,
    ): LoadedModelAsset {
        val bytes = context.assets.open(spec.assetPath).use { it.readBytes() }
        val signatureBytes = context.assets.open(spec.signatureAssetPath).use { it.readBytes() }
        val actualSha256 = sha256(bytes)

        require(spec.expectedSha256.equals(actualSha256, ignoreCase = true)) {
            "Model ${spec.id} checksum mismatch: expected ${spec.expectedSha256}, got $actualSha256"
        }
        require(verifyEd25519Signature(bytes, signatureBytes, spec.publicKeyBase64)) {
            "Model ${spec.id} signature verification failed"
        }

        val direct = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        direct.put(bytes)
        direct.rewind()
        return LoadedModelAsset(
            id = spec.id,
            version = spec.version,
            buffer = direct.asReadOnlyBuffer().order(ByteOrder.nativeOrder()),
            sha256 = actualSha256,
            sizeBytes = bytes.size.toLong(),
        )
    }

    internal fun verifyEd25519Signature(
        payload: ByteArray,
        signatureBytes: ByteArray,
        publicKeyBase64: String,
    ): Boolean {
        val keyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
        val rawPublicKey = keyBytes.copyOfRange(keyBytes.size - ED25519_PUBLIC_KEY_BYTES, keyBytes.size)
        val signer = Ed25519Signer()
        signer.init(false, Ed25519PublicKeyParameters(rawPublicKey, 0))
        signer.update(payload, 0, payload.size)
        return signer.verifySignature(signatureBytes)
    }

    internal fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private companion object {
        private const val ED25519_PUBLIC_KEY_BYTES = 32
    }
}
