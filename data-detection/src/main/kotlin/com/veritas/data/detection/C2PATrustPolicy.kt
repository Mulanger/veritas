package com.veritas.data.detection

import android.content.res.AssetManager

data class C2PAValidationIssue(
    val code: String,
    val explanation: String?,
) {
    fun formatted(): String = "Validation failure: $code${explanation?.let { " - $it" } ?: ""}"
}

class C2PATrustPolicy(
    trustedIssuers: Set<String>,
    private val allowExpiredCredentials: Boolean = false,
    private val requireTrustedIssuer: Boolean = true,
) {
    private val normalizedTrustedIssuers =
        trustedIssuers.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()

    fun evaluate(
        issuerName: String?,
        validationIssues: List<C2PAValidationIssue>,
    ): List<String> {
        val issuerTrusted = issuerName != null && normalizedTrustedIssuers.contains(issuerName.trim().lowercase())
        val failures = mutableListOf<String>()

        if (requireTrustedIssuer && !issuerTrusted) {
            failures.add("Credential trust failure: issuer is not trusted: ${issuerName ?: "Unknown"}")
        }

        validationIssues.forEach { issue ->
            val normalizedCode = issue.code.lowercase()
            when {
                normalizedCode == "signingcredential.untrusted" -> {
                    if (!issuerTrusted) failures.add(issue.formatted())
                }
                normalizedCode == "signingcredential.expired" -> {
                    if (!allowExpiredCredentials) failures.add(issue.formatted())
                }
                isAlwaysFatalValidationCode(normalizedCode) -> failures.add(issue.formatted())
            }
        }

        return failures.distinct()
    }

    fun hasRevocationFailure(failures: List<String>): Boolean =
        failures.any { it.contains("revoked", ignoreCase = true) }

    private fun isAlwaysFatalValidationCode(normalizedCode: String): Boolean =
        normalizedCode.contains("mismatch") ||
            normalizedCode.contains("invalid") ||
            normalizedCode.contains("revoked") ||
            normalizedCode.contains("failure") ||
            normalizedCode.contains("malformed")

    companion object {
        private const val TRUSTED_ISSUERS_ASSET = "c2pa_trusted_issuers.txt"

        fun fromAssets(assetManager: AssetManager): C2PATrustPolicy {
            val issuers = runCatching {
                assetManager.open(TRUSTED_ISSUERS_ASSET).bufferedReader().useLines { lines ->
                    lines.map { it.trim() }
                        .filter { it.isNotBlank() && !it.startsWith("#") }
                        .toSet()
                }
            }.getOrDefault(emptySet())
            return C2PATrustPolicy(trustedIssuers = issuers)
        }

        fun fixturePolicy(): C2PATrustPolicy =
            C2PATrustPolicy(
                trustedIssuers = setOf("C2PA Test Signing Cert", "NIKON CORPORATION", "Truepic"),
                allowExpiredCredentials = true,
            )

        fun strictPolicy(trustedIssuers: Set<String> = emptySet()): C2PATrustPolicy =
            C2PATrustPolicy(trustedIssuers = trustedIssuers)
    }
}
