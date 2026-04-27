package com.veritas.app

import com.veritas.domain.detection.ReasonCode
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReasonCodeDictionaryTest {
    @Test
    fun emittedDetectorReasonCodesResolveToResourceTemplates() {
        emittedDetectorReasonCodes().forEach { code ->
            assertNotNull("Expected resource template for $code", reasonStringIds(code))
        }
    }

    @Test
    fun provenanceReasonCodesResolveToResourceTemplates() {
        listOf(
            ReasonCode.C2PA_VERIFIED,
            ReasonCode.C2PA_INVALID_SIGNATURE,
            ReasonCode.C2PA_REVOKED,
            ReasonCode.SYNTHID_DETECTED,
            ReasonCode.DIFFUSION_SPECTRAL_SIG,
            ReasonCode.ELA_INCONSISTENT,
            ReasonCode.METADATA_IMPLAUSIBLE,
        ).forEach { code ->
            assertNotNull("Expected resource template for $code", reasonStringIds(code))
        }
    }

    private fun emittedDetectorReasonCodes(): Set<ReasonCode> =
        setOf(
            ReasonCode.IMG_DEEPFAKE_MODEL_HIGH,
            ReasonCode.IMG_EXIF_MISSING,
            ReasonCode.IMG_EXIF_SUSPICIOUS,
            ReasonCode.IMG_ELA_ANOMALY,
            ReasonCode.IMG_LOW_QUALITY,
            ReasonCode.AUD_SYNTHETIC_VOICE_HIGH,
            ReasonCode.AUD_CODEC_MISMATCH,
            ReasonCode.AUD_TOO_SHORT,
            ReasonCode.AUD_LOW_QUALITY,
            ReasonCode.AUD_NATURAL_PROSODY,
            ReasonCode.VID_SPATIAL_SYNTHETIC_FRAMES,
            ReasonCode.VID_TEMPORAL_DRIFT_HIGH,
            ReasonCode.VID_FACE_INCONSISTENT,
            ReasonCode.VID_LOW_QUALITY,
            ReasonCode.VID_DECODE_FAILED,
            ReasonCode.CODEC_CONSISTENT,
        )
}
