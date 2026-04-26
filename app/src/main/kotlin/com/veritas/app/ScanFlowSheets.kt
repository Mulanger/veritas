@file:Suppress("CyclomaticComplexMethod", "FunctionNaming", "LongMethod", "MaxLineLength")

package com.veritas.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.core.design.VeritasButton
import com.veritas.core.design.VeritasButtonVariant
import com.veritas.core.design.VeritasColors
import com.veritas.core.design.VeritasType
import com.veritas.domain.detection.Reason
import com.veritas.domain.detection.ScannedMedia
import java.util.Locale

@Composable
fun ReasonRow(
    reason: Reason,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = reasonAccentColor(reason)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(VeritasColors.panel, RoundedCornerShape(12.dp))
                .border(1.dp, VeritasColors.line, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .background(accent, RoundedCornerShape(2.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = reason.code.name,
                        style = VeritasType.monoXs.copy(color = accent, fontWeight = FontWeight.W700),
                    )
                    Text(
                        text = String.format(Locale.US, "weight %.2f", reason.weight),
                        style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
                    )
                }
                Text(
                    text = reasonDescription(reason),
                    style = VeritasType.bodySm.copy(color = VeritasColors.inkDim),
                )
            }
        }
    }
}

@Composable
fun ReasonDetailSheet(
    reason: Reason,
    onClose: () -> Unit,
    onTimestampSelected: (Long) -> Unit = {},
) {
    val copy = reasonCopyResource(reason.code)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(REASON_DETAIL_SHEET_TAG)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .background(VeritasColors.line2, RoundedCornerShape(999.dp)),
        )
        Text(
            text = reason.code.name,
            style = VeritasType.monoSm.copy(color = VeritasColors.accent),
        )
        Text(
            text = copy.primaryName,
            style = VeritasType.headingLg.copy(color = VeritasColors.ink),
        )
        Text(
            text = "Contribution: ${(reason.weight * 100).toInt()}% of the current verdict",
            style = VeritasType.bodySm.copy(color = VeritasColors.inkMute),
        )
        SheetSection(title = "What this means", body = copy.whatItMeans)
        SheetSection(title = "Why it matters", body = copy.whyItMatters)
        SheetSection(title = "False positive risk", body = copy.falsePositiveRisk)
        reasonTimestamps(reason)?.let { timestamps ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "FLAGGED MOMENTS",
                    style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    timestamps.forEach { timestamp ->
                        Text(
                            text = formatScanDuration(timestamp),
                            modifier =
                                Modifier
                                    .background(VeritasColors.panel2, RoundedCornerShape(999.dp))
                                    .clickable { onTimestampSelected(timestamp) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            style = VeritasType.monoXs.copy(color = VeritasColors.accent, fontWeight = FontWeight.W700),
                        )
                    }
                }
            }
        }
        VeritasButton(
            text = "Close",
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            variant = VeritasButtonVariant.Ghost,
            testTag = REASON_DETAIL_CLOSE_TAG,
        )
    }
}

@Composable
fun reasonCopyResource(code: com.veritas.domain.detection.ReasonCode): ReasonCodeCopy {
    val fallbackTitle = code.prettyTitle()
    val ids = reasonStringIds(code)
    return ReasonCodeCopy(
        primaryName = ids?.let { stringResource(it.title) } ?: fallbackTitle,
        whatItMeans = ids?.let { stringResource(it.what) } ?: stringResource(R.string.reason_generic_what, fallbackTitle),
        whyItMatters = ids?.let { stringResource(it.why) } ?: stringResource(R.string.reason_generic_why, fallbackTitle),
        falsePositiveRisk = ids?.let { stringResource(it.risk) } ?: stringResource(R.string.reason_generic_risk, fallbackTitle),
    )
}

@Composable
fun reasonChipLabelResource(code: com.veritas.domain.detection.ReasonCode): String =
    reasonStringIds(code)?.let { stringResource(it.label) } ?: reasonChipCode(code)

internal data class ReasonStringIds(
    val label: Int,
    val title: Int,
    val what: Int,
    val why: Int,
    val risk: Int,
)

internal fun reasonStringIds(code: com.veritas.domain.detection.ReasonCode): ReasonStringIds? =
    when (code) {
        com.veritas.domain.detection.ReasonCode.IMG_DEEPFAKE_MODEL_HIGH ->
            ReasonStringIds(R.string.reason_img_deepfake_model_high_label, R.string.reason_img_deepfake_model_high_title, R.string.reason_img_deepfake_model_high_what, R.string.reason_img_deepfake_model_high_why, R.string.reason_img_deepfake_model_high_risk)
        com.veritas.domain.detection.ReasonCode.IMG_EXIF_MISSING ->
            ReasonStringIds(R.string.reason_img_exif_missing_label, R.string.reason_img_exif_missing_title, R.string.reason_img_exif_missing_what, R.string.reason_img_exif_missing_why, R.string.reason_img_exif_missing_risk)
        com.veritas.domain.detection.ReasonCode.IMG_EXIF_SUSPICIOUS ->
            ReasonStringIds(R.string.reason_img_exif_suspicious_label, R.string.reason_img_exif_suspicious_title, R.string.reason_img_exif_suspicious_what, R.string.reason_img_exif_suspicious_why, R.string.reason_img_exif_suspicious_risk)
        com.veritas.domain.detection.ReasonCode.IMG_ELA_ANOMALY ->
            ReasonStringIds(R.string.reason_img_ela_anomaly_label, R.string.reason_img_ela_anomaly_title, R.string.reason_img_ela_anomaly_what, R.string.reason_img_ela_anomaly_why, R.string.reason_img_ela_anomaly_risk)
        com.veritas.domain.detection.ReasonCode.AUD_SYNTHETIC_VOICE_HIGH ->
            ReasonStringIds(R.string.reason_aud_synthetic_voice_high_label, R.string.reason_aud_synthetic_voice_high_title, R.string.reason_aud_synthetic_voice_high_what, R.string.reason_aud_synthetic_voice_high_why, R.string.reason_aud_synthetic_voice_high_risk)
        com.veritas.domain.detection.ReasonCode.AUD_CODEC_MISMATCH ->
            ReasonStringIds(R.string.reason_aud_codec_mismatch_label, R.string.reason_aud_codec_mismatch_title, R.string.reason_aud_codec_mismatch_what, R.string.reason_aud_codec_mismatch_why, R.string.reason_aud_codec_mismatch_risk)
        com.veritas.domain.detection.ReasonCode.AUD_TOO_SHORT ->
            ReasonStringIds(R.string.reason_aud_too_short_label, R.string.reason_aud_too_short_title, R.string.reason_aud_too_short_what, R.string.reason_aud_too_short_why, R.string.reason_aud_too_short_risk)
        com.veritas.domain.detection.ReasonCode.AUD_NATURAL_PROSODY ->
            ReasonStringIds(R.string.reason_aud_natural_prosody_label, R.string.reason_aud_natural_prosody_title, R.string.reason_aud_natural_prosody_what, R.string.reason_aud_natural_prosody_why, R.string.reason_aud_natural_prosody_risk)
        com.veritas.domain.detection.ReasonCode.VID_SPATIAL_SYNTHETIC_FRAMES ->
            ReasonStringIds(R.string.reason_vid_spatial_synthetic_frames_label, R.string.reason_vid_spatial_synthetic_frames_title, R.string.reason_vid_spatial_synthetic_frames_what, R.string.reason_vid_spatial_synthetic_frames_why, R.string.reason_vid_spatial_synthetic_frames_risk)
        com.veritas.domain.detection.ReasonCode.VID_TEMPORAL_DRIFT_HIGH ->
            ReasonStringIds(R.string.reason_vid_temporal_drift_high_label, R.string.reason_vid_temporal_drift_high_title, R.string.reason_vid_temporal_drift_high_what, R.string.reason_vid_temporal_drift_high_why, R.string.reason_vid_temporal_drift_high_risk)
        com.veritas.domain.detection.ReasonCode.VID_FACE_INCONSISTENT ->
            ReasonStringIds(R.string.reason_vid_face_inconsistent_label, R.string.reason_vid_face_inconsistent_title, R.string.reason_vid_face_inconsistent_what, R.string.reason_vid_face_inconsistent_why, R.string.reason_vid_face_inconsistent_risk)
        com.veritas.domain.detection.ReasonCode.VID_DECODE_FAILED ->
            ReasonStringIds(R.string.reason_vid_decode_failed_label, R.string.reason_vid_decode_failed_title, R.string.reason_vid_decode_failed_what, R.string.reason_vid_decode_failed_why, R.string.reason_vid_decode_failed_risk)
        com.veritas.domain.detection.ReasonCode.CODEC_CONSISTENT ->
            ReasonStringIds(R.string.reason_codec_consistent_label, R.string.reason_codec_consistent_title, R.string.reason_codec_consistent_what, R.string.reason_codec_consistent_why, R.string.reason_codec_consistent_risk)
        com.veritas.domain.detection.ReasonCode.COMPRESSION_HEAVY,
        com.veritas.domain.detection.ReasonCode.IMG_LOW_QUALITY,
        com.veritas.domain.detection.ReasonCode.AUD_LOW_QUALITY,
        com.veritas.domain.detection.ReasonCode.VID_LOW_QUALITY,
        -> ReasonStringIds(R.string.reason_quality_label, R.string.reason_quality_title, R.string.reason_quality_what, R.string.reason_quality_why, R.string.reason_quality_risk)
        com.veritas.domain.detection.ReasonCode.C2PA_VERIFIED ->
            ReasonStringIds(R.string.reason_c2pa_verified_label, R.string.reason_c2pa_verified_title, R.string.reason_c2pa_verified_what, R.string.reason_c2pa_verified_why, R.string.reason_c2pa_verified_risk)
        com.veritas.domain.detection.ReasonCode.C2PA_INVALID_SIGNATURE ->
            ReasonStringIds(R.string.reason_c2pa_invalid_signature_label, R.string.reason_c2pa_invalid_signature_title, R.string.reason_c2pa_invalid_signature_what, R.string.reason_c2pa_invalid_signature_why, R.string.reason_c2pa_invalid_signature_risk)
        com.veritas.domain.detection.ReasonCode.C2PA_REVOKED ->
            ReasonStringIds(R.string.reason_c2pa_revoked_label, R.string.reason_c2pa_revoked_title, R.string.reason_c2pa_revoked_what, R.string.reason_c2pa_revoked_why, R.string.reason_c2pa_revoked_risk)
        com.veritas.domain.detection.ReasonCode.SYNTHID_DETECTED ->
            ReasonStringIds(R.string.reason_synthid_detected_label, R.string.reason_synthid_detected_title, R.string.reason_synthid_detected_what, R.string.reason_synthid_detected_why, R.string.reason_synthid_detected_risk)
        com.veritas.domain.detection.ReasonCode.DIFFUSION_SPECTRAL_SIG ->
            ReasonStringIds(R.string.reason_diffusion_spectral_sig_label, R.string.reason_diffusion_spectral_sig_title, R.string.reason_diffusion_spectral_sig_what, R.string.reason_diffusion_spectral_sig_why, R.string.reason_diffusion_spectral_sig_risk)
        com.veritas.domain.detection.ReasonCode.ELA_INCONSISTENT ->
            ReasonStringIds(R.string.reason_ela_inconsistent_label, R.string.reason_ela_inconsistent_title, R.string.reason_ela_inconsistent_what, R.string.reason_ela_inconsistent_why, R.string.reason_ela_inconsistent_risk)
        com.veritas.domain.detection.ReasonCode.METADATA_IMPLAUSIBLE ->
            ReasonStringIds(R.string.reason_metadata_implausible_label, R.string.reason_metadata_implausible_title, R.string.reason_metadata_implausible_what, R.string.reason_metadata_implausible_why, R.string.reason_metadata_implausible_risk)
        else -> null
    }

@Composable
fun FindOriginalSheet(
    media: ScannedMedia,
    onClose: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(FIND_ORIGINAL_SHEET_TAG)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .background(VeritasColors.line2, RoundedCornerShape(999.dp)),
        )
        Text(
            text = "FIND ORIGINAL",
            style = VeritasType.monoSm.copy(color = VeritasColors.warn),
        )
        Text(
            text = "Look for a higher-quality source.",
            style = VeritasType.headingLg.copy(color = VeritasColors.ink),
        )
        Text(
            text =
                "This Phase 5 sheet is a placeholder for the later source-finding flow. For now, search for the earliest upload, a better-quality copy, or the original publisher. ${media.mediaType.name.lowercase(Locale.US).replaceFirstChar(Char::uppercaseChar)} context often matters more than one uncertain scan.",
            style = VeritasType.bodyLg.copy(color = VeritasColors.inkDim),
            lineHeight = 24.sp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GuidanceRow(text = "Check the original poster or newsroom account.")
            GuidanceRow(text = "Prefer the highest-resolution version you can find.")
            GuidanceRow(text = "Re-scan if you get a cleaner copy.")
        }
        VeritasButton(
            text = "Close",
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            variant = VeritasButtonVariant.Ghost,
        )
    }
}

@Composable
private fun GuidanceRow(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier =
                Modifier
                    .padding(top = 6.dp)
                    .size(6.dp)
                    .background(VeritasColors.warn, CircleShape),
        )
        Text(
            text = text,
            style = VeritasType.bodyMd.copy(color = VeritasColors.inkDim),
        )
    }
}

@Composable
private fun SheetSection(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title.uppercase(Locale.US),
            style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
        )
        Text(
            text = body,
            style = VeritasType.bodyMd.copy(color = VeritasColors.inkDim),
            lineHeight = 22.sp,
        )
    }
}
