@file:Suppress("FunctionNaming", "MaxLineLength")

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.core.design.VeritasButton
import com.veritas.core.design.VeritasButtonVariant
import com.veritas.core.design.VeritasColors
import com.veritas.core.design.VeritasType
import com.veritas.domain.detection.Reason
import com.veritas.domain.detection.ScannedMedia
import java.text.DecimalFormat
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
                        text = "weight ${DecimalFormat("0.00").format(reason.weight)}",
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
) {
    val copy = remember(reason.code) { reasonCopy(reason.code) }

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
            SheetSection(
                title = "Flagged moments",
                body = timestamps.joinToString(separator = " · ") { formatScanDuration(it) },
            )
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
