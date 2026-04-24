package com.veritas.domain.detection

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
sealed class C2PAResult {
    abstract val outcome: C2PAOutcome

    abstract val elapsedMs: Long

    data object NotPresent : C2PAResult() {
        override val outcome: C2PAOutcome = C2PAOutcome.NOT_PRESENT
        override val elapsedMs: Long = 0L
    }

    data class Present(
        val instanceId: String?,
        val issuerName: String?,
        val deviceName: String?,
        val signedAt: Instant?,
        val claimGenerator: String?,
        val actions: List<String>,
    ) : C2PAResult() {
        override val outcome: C2PAOutcome = C2PAOutcome.VALID
        override val elapsedMs: Long = 0L
    }

    data class Invalid(
        val reason: String,
    ) : C2PAResult() {
        override val outcome: C2PAOutcome = C2PAOutcome.INVALID
        override val elapsedMs: Long = 0L
    }

    data class Revoked(
        val reason: String,
    ) : C2PAResult() {
        override val outcome: C2PAOutcome = C2PAOutcome.REVOKED
        override val elapsedMs: Long = 0L
    }
}

@Serializable
enum class C2PAOutcome {
    NOT_PRESENT,
    VALID,
    INVALID,
    REVOKED,
}
