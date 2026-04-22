@file:Suppress("FunctionName", "TooManyFunctions", "UnusedPrivateMember")

package com.veritas.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private const val GALLERY_PHASE_LABEL = "PHASE 1 / DESIGN SYSTEM"
private const val SAMPLE_METADATA = "VERDICT / VIDEO / 0:23"

@Composable
fun GalleryScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VeritasScaffold(
        modifier = modifier,
        onClose = onClose,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(VeritasSpacing.space24),
        ) {
            GalleryIntro()
            ColorsSection()
            TypeSection()
            MetricsSection()
            PrimitivesSection()
            Spacer(modifier = Modifier.height(VeritasSpacing.space24))
        }
    }
}

@Composable
private fun GalleryIntro() {
    Column(verticalArrangement = Arrangement.spacedBy(VeritasSpacing.space8)) {
        VeritasTag(text = GALLERY_PHASE_LABEL)
        Text(
            text = "Instrumentation, not decoration.",
            style = VeritasType.displayMd.copy(color = VeritasColors.ink),
        )
        Text(
            text = "Tokens, primitives, and gallery surfaces mirror the Phase 1 visual spec for later screens.",
            style = VeritasType.bodyMd.copy(color = VeritasColors.inkDim),
        )
    }
}

@Composable
private fun ColorsSection() {
    GallerySection(
        index = "01",
        title = "Colors",
        description = "Veritas uses one dark surface stack, one accent, and verdict tones with dim partners.",
    ) {
        ColorSwatch("BG", VeritasColors.bg)
        ColorSwatch("PANEL", VeritasColors.panel)
        ColorSwatch("PANEL 2", VeritasColors.panel2)
        ColorSwatch("LINE", VeritasColors.line)
        ColorSwatch("LINE 2", VeritasColors.line2)
        ColorSwatch("INK", VeritasColors.ink)
        ColorSwatch("INK DIM", VeritasColors.inkDim)
        ColorSwatch("INK MUTE", VeritasColors.inkMute)
        ColorSwatch("OK", VeritasColors.ok)
        ColorSwatch("OK DIM", VeritasColors.okDim)
        ColorSwatch("WARN", VeritasColors.warn)
        ColorSwatch("WARN DIM", VeritasColors.warnDim)
        ColorSwatch("BAD", VeritasColors.bad)
        ColorSwatch("BAD DIM", VeritasColors.badDim)
        ColorSwatch("ACCENT", VeritasColors.accent)
    }
}

@Composable
private fun TypeSection() {
    GallerySection(
        index = "02",
        title = "Type",
        description = "Manrope carries primary UI. JetBrains Mono marks metadata, state, and evidence.",
    ) {
        TypeSample("displayXl", VeritasType.displayXl, "Know what's real.")
        TypeSample("displayLg", VeritasType.displayLg, "Calm forensic UI")
        TypeSample("displayMd", VeritasType.displayMd, "Signals, not guesses")
        TypeSample("headingLg", VeritasType.headingLg, "Gallery components")
        TypeSample("headingMd", VeritasType.headingMd, "Evidence and verdicts")
        TypeSample("headingSm", VeritasType.headingSm, "Token sample")
        TypeSample(
            label = "bodyLg",
            style = VeritasType.bodyLg,
            text = "Veritas checks media on-device and shows calibrated confidence ranges.",
        )
        TypeSample("bodyMd", VeritasType.bodyMd, "The design stays dark, measured, and readable.")
        TypeSample("bodySm", VeritasType.bodySm, "Small copy still stays calm.")
        TypeSample("monoSm", VeritasType.monoSm, SAMPLE_METADATA)
        TypeSample("monoXs", VeritasType.monoXs, "TEMPORAL CONFIDENCE")
    }
}

@Composable
private fun MetricsSection() {
    GallerySection(
        index = "03",
        title = "Metrics",
        description = "Spacing and radius stay on a small, repeatable grid.",
    ) {
        MetricRow("Spacing", "4, 8, 12, 16, 20, 24, 32, 48, 64")
        MetricRow("Radius", "6, 10, 14, 22, pill")
    }
}

@Composable
@Suppress("LongMethod")
private fun PrimitivesSection() {
    GallerySection(
        index = "04",
        title = "Primitives",
        description = "Each primitive is reusable for later phases, not a one-off mockup fragment.",
    ) {
        StatusBar()
        PrimitiveHeader("BrandMark")
        BrandMark(size = 24.dp)

        PrimitiveHeader("Buttons")
        Row(
            horizontalArrangement = Arrangement.spacedBy(VeritasSpacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VeritasButton(
                text = "See details",
                onClick = {},
                variant = VeritasButtonVariant.Primary,
                modifier = Modifier.weight(1f),
            )
            VeritasButton(
                text = "Done",
                onClick = {},
                variant = VeritasButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
        }
        VeritasButton(
            text = "Paste link",
            onClick = {},
            variant = VeritasButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )

        PrimitiveHeader("VeritasTag")
        VeritasTag(text = SAMPLE_METADATA)

        PrimitiveHeader("ConfidenceRange")
        ConfidenceRange(low = 78, high = 94, tone = VerdictTone.Ok)
        ConfidenceRange(low = 31, high = 65, tone = VerdictTone.Warn)
        ConfidenceRange(low = 82, high = 96, tone = VerdictTone.Bad)

        PrimitiveHeader("EvidenceChip")
        EvidenceChip(
            code = "C2PA_VALID",
            description = "Content Credentials match a known capture device and trusted issuer.",
            variant = EvidenceChipVariant.Plus,
        )
        EvidenceChip(
            code = "LOW_QUALITY_INPUT",
            description = "Compression and bitrate loss soften edges enough to reduce certainty.",
            variant = EvidenceChipVariant.Mixed,
        )
        EvidenceChip(
            code = "LIP_SYNC_DRIFT",
            description = "Speech cadence and visible mouth motion diverge under frame analysis.",
            variant = EvidenceChipVariant.Minus,
        )

        PrimitiveHeader("StageRow")
        StageRow(
            label = "C2PA manifest check",
            meta = "NONE",
            state = StageRowState.Done,
        )
        StageRow(
            label = "Temporal consistency",
            meta = "0.8S",
            state = StageRowState.Active,
        )
        StageRow(
            label = "Facial artifact model",
            meta = "QUEUED",
            state = StageRowState.Idle,
        )

        PrimitiveHeader("VerdictPill")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(VeritasSpacing.space8),
            verticalArrangement = Arrangement.spacedBy(VeritasSpacing.space8),
        ) {
            VerdictPill(text = "AUTHENTIC", tone = VerdictTone.Ok)
            VerdictPill(text = "UNCERTAIN", tone = VerdictTone.Warn)
            VerdictPill(text = "SYNTHETIC", tone = VerdictTone.Bad)
        }
    }
}

@Composable
private fun GallerySection(
    index: String,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VeritasSpacing.space12)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VeritasSpacing.space12),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = index,
                style =
                    VeritasType.monoXs.copy(
                        color = VeritasColors.inkMute,
                        fontSize = 11.sp,
                    ),
            )
            Text(
                text = title,
                style = VeritasType.headingLg.copy(color = VeritasColors.ink),
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        Text(
            text = description,
            style = VeritasType.bodySm.copy(color = VeritasColors.inkDim),
        )
        HorizontalDivider(color = VeritasColors.line)
        Column(
            verticalArrangement = Arrangement.spacedBy(VeritasSpacing.space12),
            content = content,
        )
    }
}

@Composable
private fun ColorSwatch(
    label: String,
    color: Color,
) {
    val swatchShape = RoundedCornerShape(4.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VeritasSpacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 28.dp, height = 16.dp)
                    .clip(swatchShape)
                    .background(color)
                    .then(
                        if (color == VeritasColors.bg) {
                            Modifier.border(1.dp, VeritasColors.line2, swatchShape)
                        } else {
                            Modifier
                        },
                    ),
        )
        Text(
            text = label,
            modifier = Modifier.width(72.dp),
            style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
        )
        Text(
            text = color.toHex(),
            style = VeritasType.bodySm.copy(color = VeritasColors.inkDim),
        )
    }
}

@Composable
private fun TypeSample(
    label: String,
    style: androidx.compose.ui.text.TextStyle,
    text: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VeritasSpacing.space4)) {
        Text(
            text = label,
            style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
        )
        Text(
            text = text,
            style = style.copy(color = VeritasColors.ink),
        )
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = VeritasType.headingSm.copy(color = VeritasColors.ink),
        )
        Text(
            text = value,
            style = VeritasType.monoXs.copy(color = VeritasColors.inkDim),
        )
    }
}

@Composable
private fun PrimitiveHeader(text: String) {
    Text(
        text = text,
        style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
    )
}

private fun Color.toHex(): String = String.format(Locale.US, "#%08X", toArgb())

@Preview(showBackground = true, backgroundColor = 0xFF0A0B0D, widthDp = 390, heightDp = 844)
@Composable
private fun GalleryScreenPreview() {
    VeritasTheme {
        GalleryScreen(onClose = {})
    }
}
