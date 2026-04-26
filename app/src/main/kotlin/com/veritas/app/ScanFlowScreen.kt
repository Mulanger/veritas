@file:Suppress(
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
)

package com.veritas.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.veritas.core.design.BrandMark
import com.veritas.core.design.ConfidenceRange
import com.veritas.core.design.EvidenceChip
import com.veritas.core.design.StageRow
import com.veritas.core.design.StageRowState
import com.veritas.core.design.VerdictPill
import com.veritas.core.design.VeritasButton
import com.veritas.core.design.VeritasButtonVariant
import com.veritas.core.design.VeritasColors
import com.veritas.core.design.VeritasTag
import com.veritas.core.design.VeritasType
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.Reason
import com.veritas.domain.detection.ForensicEvidence
import com.veritas.domain.detection.HeatmapData
import com.veritas.domain.detection.HeatmapFrame
import com.veritas.domain.detection.ScannedMedia
import com.veritas.domain.detection.Severity
import com.veritas.domain.detection.TemporalBin
import com.veritas.domain.detection.TemporalConfidence
import com.veritas.domain.detection.WaveformData
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanFlowScreen(
    state: ScanUiState,
    onClose: () -> Unit,
    onPrimaryVerdictAction: () -> Unit,
    onDone: () -> Unit,
    onBackToVerdict: () -> Unit,
    onReasonSelected: (Reason) -> Unit,
    onReasonDismiss: () -> Unit,
    onFindOriginalDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val media = requireNotNull(state.media)
    val verdict = state.verdict
    var forensicTimestampMs by remember(verdict?.id) { mutableLongStateOf(0L) }

    if (state.surface == ScanSurface.Scanning) {
        BackHandler(onBack = onClose)
    }

    if (state.surface == ScanSurface.Forensic && state.selectedReason == null) {
        BackHandler(onBack = onBackToVerdict)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (state.surface) {
            ScanSurface.Scanning ->
                ScanningScreen(
                    media = media,
                    stages = state.stages,
                    onClose = onClose,
                )

            ScanSurface.Verdict ->
                VerdictScreen(
                    media = media,
                    verdict = requireNotNull(verdict),
                    onClose = onClose,
                    onPrimaryAction = onPrimaryVerdictAction,
                    onDone = onDone,
                )

            ScanSurface.Forensic ->
                ForensicScreen(
                    media = media,
                    verdict = requireNotNull(verdict),
                    selectedTimestampMs = forensicTimestampMs,
                    onTimestampSelected = { forensicTimestampMs = it },
                    onClose = onClose,
                    onBackToVerdict = onBackToVerdict,
                    onReasonSelected = onReasonSelected,
                )
        }

        state.selectedReason?.let { reason ->
            ModalBottomSheet(
                onDismissRequest = onReasonDismiss,
                containerColor = VeritasColors.panel,
                contentColor = VeritasColors.ink,
            ) {
                ReasonDetailSheet(
                    reason = reason,
                    onTimestampSelected = { timestamp ->
                        forensicTimestampMs = timestamp
                        onReasonDismiss()
                    },
                    onClose = onReasonDismiss,
                )
            }
        }

        if (state.showFindOriginalSheet) {
            ModalBottomSheet(
                onDismissRequest = onFindOriginalDismiss,
                containerColor = VeritasColors.panel,
                contentColor = VeritasColors.ink,
            ) {
                FindOriginalSheet(
                    media = media,
                    onClose = onFindOriginalDismiss,
                )
            }
        }
    }
}

@Composable
private fun ScanningScreen(
    media: ScannedMedia,
    stages: List<StageUiModel>,
    onClose: () -> Unit,
) {
    val activeIndex = stages.indexOfFirst { it.state == StageRowState.Active }.takeIf { it >= 0 }
    val completedCount = stages.count { it.state == StageRowState.Done }
    val currentStageCount = activeIndex?.plus(1) ?: completedCount.coerceAtLeast(1)

    ScanFlowScaffold(
        brandName = "VERITAS",
        onClose = onClose,
        rootTag = SCAN_SCREEN_TAG,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ScanPreviewCard(media = media, modifier = Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VeritasTag(text = "ANALYSIS · $currentStageCount OF ${stages.size} STAGES")
                stages.forEach { stage ->
                    StageRow(
                        label = stage.stage.label,
                        meta = stageMeta(stage),
                        state = stage.state,
                    )
                }
            }
        }
    }
}

@Composable
private fun VerdictScreen(
    media: ScannedMedia,
    verdict: com.veritas.domain.detection.Verdict,
    onClose: () -> Unit,
    onPrimaryAction: () -> Unit,
    onDone: () -> Unit,
) {
    val presentation = remember(verdict.outcome) { verdictPresentation(verdict.outcome) }
    val evidenceVariant = evidenceVariantFor(verdict.outcome)

    ScanFlowScaffold(
        brandName = "VERITAS",
        onClose = onClose,
        rootTag = VERDICT_SCREEN_TAG,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                VeritasTag(text = verdictTag(media))
                Text(
                    text = presentation.headline,
                    style =
                        VeritasType.displayLg.copy(
                            color = presentation.headlineColor,
                            fontWeight = FontWeight.W800,
                        ),
                )
                ConfidenceRange(
                    low = verdict.confidence.lowPct,
                    high = verdict.confidence.highPct,
                    tone = presentation.tone,
                )
                Text(
                    text = verdict.summary,
                    style = VeritasType.bodyLg.copy(color = VeritasColors.inkDim),
                    lineHeight = 24.sp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    verdict.reasons.take(3).forEach { reason ->
                        EvidenceChip(
                            code = reasonChipLabelResource(reason.code),
                            description = reasonDescription(reason),
                            variant = evidenceVariant,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VeritasButton(
                    text = presentation.primaryAction,
                    onClick = onPrimaryAction,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = VERDICT_PRIMARY_ACTION_TAG,
                )
                VeritasButton(
                    text = "Done",
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    variant = VeritasButtonVariant.Ghost,
                    testTag = VERDICT_DONE_ACTION_TAG,
                )
            }
        }
    }
}

@Composable
private fun ForensicScreen(
    media: ScannedMedia,
    verdict: com.veritas.domain.detection.Verdict,
    selectedTimestampMs: Long,
    onTimestampSelected: (Long) -> Unit,
    onClose: () -> Unit,
    onBackToVerdict: () -> Unit,
    onReasonSelected: (Reason) -> Unit,
) {
    var fullscreenHeatmap by remember(verdict.id) { mutableStateOf(false) }
    var imageTab by remember(verdict.id) { mutableStateOf(ImageForensicTab.Heatmap) }
    val forensicEvidence = verdict.forensicEvidence
    val selectedHeatmapFrame =
        remember(forensicEvidence, selectedTimestampMs) {
            (forensicEvidence as? ForensicEvidence.Image)?.heatmap?.nearestFrame(selectedTimestampMs)
                ?: (forensicEvidence as? ForensicEvidence.Video)?.heatmap?.nearestFrame(selectedTimestampMs)
        }
    val temporalConfidence =
        when (forensicEvidence) {
            is ForensicEvidence.Audio -> forensicEvidence.temporalConfidence
            is ForensicEvidence.Video -> forensicEvidence.temporalConfidence
            else -> null
        }

    ScanFlowScaffold(
        brandName = "FORENSIC",
        onClose = onClose,
        rootTag = FORENSIC_SCREEN_TAG,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VerdictPill(
                    text = forensicPillText(verdict.outcome),
                    tone = verdictPresentation(verdict.outcome).tone,
                )
                Text(
                    text = forensicIndicator(media.mediaType),
                    style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
                )
            }

            ForensicViewport(
                mediaType = media.mediaType,
                evidence = forensicEvidence,
                selectedTimestampMs = selectedTimestampMs,
                imageTab = imageTab,
                onHeatmapClick = { if (selectedHeatmapFrame != null) fullscreenHeatmap = true },
            )

            if (media.mediaType == MediaType.IMAGE) {
                ImageForensicTabs(
                    selected = imageTab,
                    onSelected = { imageTab = it },
                )
            }

            if (temporalConfidence != null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "TEMPORAL CONFIDENCE",
                            style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
                        )
                        Text(
                            text = media.durationMs?.let(::formatScanDuration) ?: "N/A",
                            style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
                        )
                    }
                    TemporalTimeline(
                        temporalConfidence = temporalConfidence,
                        selectedTimestampMs = selectedTimestampMs,
                        onTimestampSelected = onTimestampSelected,
                    )
                    TimelineTicks(durationMs = media.durationMs)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "REASON CODES · ${verdict.reasons.size} FLAGS",
                    style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
                )
                verdict.reasons.forEachIndexed { index, reason ->
                    ReasonRow(
                        reason = reason,
                        modifier = Modifier.testTag("$FORENSIC_REASON_TAG_PREFIX$index"),
                        onClick = { onReasonSelected(reason) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VeritasButton(
                    text = "Export",
                    onClick = {},
                    modifier = Modifier.weight(0.42f),
                    variant = VeritasButtonVariant.Secondary,
                    enabled = false,
                )
                VeritasButton(
                    text = "Back to verdict",
                    onClick = onBackToVerdict,
                    modifier = Modifier.weight(0.58f),
                    testTag = FORENSIC_BACK_TO_VERDICT_TAG,
                )
            }
        }
    }

    if (fullscreenHeatmap && selectedHeatmapFrame != null) {
        FullscreenHeatmap(
            frame = selectedHeatmapFrame,
            onClose = { fullscreenHeatmap = false },
        )
    }
}

@Composable
private fun ScanPreviewCard(
    media: ScannedMedia,
    modifier: Modifier = Modifier,
) {
    val scanTransition = rememberInfiniteTransition(label = "scan-line")
    val scanOffset by
        scanTransition.animateFloat(
            initialValue = -0.15f,
            targetValue = 1.15f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "scan-line-offset",
        )

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    VeritasColors.panel2,
                                    VeritasColors.bg,
                                ),
                        ),
                )
                .border(1.dp, VeritasColors.line2, RoundedCornerShape(20.dp)),
    ) {
        Row(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 38.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            repeat(3) {
                Box(
                    modifier =
                        Modifier
                            .width(1.dp)
                            .height(140.dp)
                            .background(VeritasColors.line),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(126.dp)
                    .clip(CircleShape)
                    .background(VeritasColors.panel.copy(alpha = 0.9f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VeritasColors.accent.copy(alpha = 0.16f)),
            )
        }
        CornerBrackets(modifier = Modifier.matchParentSize(), color = VeritasColors.accent)
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .offset(y = maxHeight * scanOffset)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        brush =
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        VeritasColors.accent,
                                        Color.Transparent,
                                    ),
                            ),
                    ),
        )
        Text(
            text = media.mediaType.name,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
            style =
                VeritasType.monoSm.copy(
                    color = VeritasColors.inkMute,
                    fontWeight = FontWeight.W700,
                ),
        )
    }
}

@Composable
private fun ForensicViewport(
    mediaType: MediaType,
    evidence: ForensicEvidence,
    selectedTimestampMs: Long,
    imageTab: ImageForensicTab,
    onHeatmapClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(18.dp))
                .background(VeritasColors.bg)
                .border(1.dp, VeritasColors.line2, RoundedCornerShape(18.dp))
                .then(
                    if (mediaType != MediaType.AUDIO) {
                        Modifier.clickable(onClick = onHeatmapClick)
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        CornerBrackets(modifier = Modifier.matchParentSize(), color = VeritasColors.line2)
        when (evidence) {
            is ForensicEvidence.Audio -> WaveformRenderer(evidence.waveform)
            is ForensicEvidence.Image ->
                when (imageTab) {
                    ImageForensicTab.Original -> OriginalImageRenderer()
                    ImageForensicTab.Heatmap -> HeatmapRenderer(evidence.heatmap.nearestFrame(selectedTimestampMs))
                    ImageForensicTab.Frequency -> FrequencyRenderer()
                    ImageForensicTab.Metadata -> MetadataRenderer()
                }
            is ForensicEvidence.Video -> HeatmapRenderer(evidence.heatmap.nearestFrame(selectedTimestampMs))
            ForensicEvidence.None ->
                Text(
                    text =
                        when (mediaType) {
                            MediaType.AUDIO -> "Waveform unavailable"
                            else -> "Heatmap unavailable"
                        },
                    style = VeritasType.headingMd.copy(color = VeritasColors.inkMute),
                )
        }
        Text(
            text = if (mediaType == MediaType.AUDIO) "Waveform" else "Heatmap",
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .background(VeritasColors.bg.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
        )
    }
}

private enum class ImageForensicTab(
    val label: String,
) {
    Original("ORIGINAL"),
    Heatmap("HEATMAP"),
    Frequency("FREQ."),
    Metadata("METADATA"),
}

@Composable
private fun ImageForensicTabs(
    selected: ImageForensicTab,
    onSelected: (ImageForensicTab) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(FORENSIC_IMAGE_TABS_TAG)
                .background(VeritasColors.panel, RoundedCornerShape(999.dp))
                .border(1.dp, VeritasColors.line, RoundedCornerShape(999.dp))
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ImageForensicTab.entries.forEach { tab ->
            val active = tab == selected
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) VeritasColors.ink else Color.Transparent)
                        .clickable { onSelected(tab) }
                        .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    style =
                        VeritasType.monoXs.copy(
                            color = if (active) VeritasColors.bg else VeritasColors.inkMute,
                            fontWeight = FontWeight.W700,
                        ),
                )
            }
        }
    }
}

@Composable
private fun HeatmapRenderer(frame: HeatmapFrame) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val labelMaxWidth = maxWidth - 88.dp
        val labelMaxHeight = maxHeight - 32.dp
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag(FORENSIC_HEATMAP_CANVAS_TAG),
        ) {
            drawRect(
                brush =
                    Brush.verticalGradient(
                        listOf(
                            VeritasColors.panel2,
                            VeritasColors.bg,
                        ),
                    ),
            )
            val cellWidth = size.width / frame.widthBins
            val cellHeight = size.height / frame.heightBins
            frame.intensities.forEachIndexed { index, raw ->
                val intensity = raw.coerceIn(0f, 1f)
                if (intensity > 0.08f) {
                    val x = index % frame.widthBins
                    val y = index / frame.widthBins
                    drawRect(
                        color = heatmapColor(intensity),
                        topLeft = androidx.compose.ui.geometry.Offset(x * cellWidth, y * cellHeight),
                        size = androidx.compose.ui.geometry.Size(cellWidth + 0.6f, cellHeight + 0.6f),
                        blendMode = BlendMode.Screen,
                    )
                }
            }
            frame.labeledRegions.forEach { region ->
                val left = region.bbox.x * size.width
                val top = region.bbox.y * size.height
                val width = region.bbox.w * size.width
                val height = region.bbox.h * size.height
                val color = severityColor(region.severity)
                drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(width, height),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(left + width, top + height / 2f),
                    end = androidx.compose.ui.geometry.Offset((left + width + 34.dp.toPx()).coerceAtMost(size.width), top + height / 2f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        frame.labeledRegions.take(4).forEachIndexed { index, region ->
            val labelX =
                (maxWidth * (region.bbox.x + region.bbox.w)).coerceAtMost(labelMaxWidth)
            val labelY =
                (maxHeight * (region.bbox.y + region.bbox.h / 2f) + (index * 24).dp)
                    .coerceAtMost(labelMaxHeight)
            Text(
                text = region.label,
                modifier =
                    Modifier
                        .offset(x = labelX, y = labelY)
                        .background(VeritasColors.bg.copy(alpha = 0.78f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                style = VeritasType.monoXs.copy(color = severityColor(region.severity), fontWeight = FontWeight.W700),
            )
        }
    }
}

@Composable
private fun WaveformRenderer(waveform: WaveformData) {
    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(FORENSIC_WAVEFORM_CANVAS_TAG),
    ) {
        val centerY = size.height / 2f
        val binWidth = size.width / waveform.rmsBins.size.coerceAtLeast(1)
        waveform.flaggedRegions.forEach { region ->
            val left = size.width * (region.startMs / waveform.durationMs.toFloat()).coerceIn(0f, 1f)
            val right = size.width * (region.endMs / waveform.durationMs.toFloat()).coerceIn(0f, 1f)
            drawRect(
                color = severityColor(region.severity).copy(alpha = 0.18f),
                topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                size = androidx.compose.ui.geometry.Size((right - left).coerceAtLeast(2f), size.height),
            )
        }
        waveform.rmsBins.forEachIndexed { index, raw ->
            val x = index * binWidth + binWidth / 2f
            val halfHeight = (raw.coerceIn(0f, 1f) * size.height * 0.42f).coerceAtLeast(2f)
            drawLine(
                color = VeritasColors.accent.copy(alpha = 0.78f),
                start = androidx.compose.ui.geometry.Offset(x, centerY - halfHeight),
                end = androidx.compose.ui.geometry.Offset(x, centerY + halfHeight),
                strokeWidth = binWidth.coerceIn(1f, 4f),
            )
        }
    }
}

@Composable
private fun OriginalImageRenderer() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(VeritasColors.panel2, VeritasColors.bg)))
        drawCircle(VeritasColors.inkMute.copy(alpha = 0.18f), radius = size.minDimension * 0.22f, center = center)
        drawCircle(VeritasColors.accent.copy(alpha = 0.10f), radius = size.minDimension * 0.34f, center = center)
    }
}

@Composable
private fun FrequencyRenderer() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(VeritasColors.bg)
        val center = center
        repeat(9) { index ->
            drawCircle(
                color = VeritasColors.accent.copy(alpha = 0.08f + index * 0.015f),
                radius = size.minDimension * (0.04f + index * 0.045f),
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        drawRect(
            color = VeritasColors.bad,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.38f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.13f, size.height * 0.13f),
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
    Text(
        text = "GRID PATTERN",
        modifier =
            Modifier
                .padding(18.dp)
                .background(VeritasColors.bg.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        style = VeritasType.monoXs.copy(color = VeritasColors.bad, fontWeight = FontWeight.W700),
    )
}

@Composable
private fun MetadataRenderer() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(
            "CAMERA" to "<none>",
            "EXIF" to "Stripped / absent",
            "DIMENSIONS" to "1024 x 1024",
            "ASPECT" to "1:1",
            "SOFTWARE" to "<none>",
        ).forEachIndexed { index, row ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(VeritasColors.panel, RoundedCornerShape(8.dp))
                        .border(1.dp, if (index in 0..1) VeritasColors.badDim else VeritasColors.line, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(row.first, style = VeritasType.monoXs.copy(color = VeritasColors.inkMute))
                Text(row.second, style = VeritasType.monoXs.copy(color = if (index in 0..1) VeritasColors.bad else VeritasColors.inkDim))
            }
        }
    }
}

@Composable
private fun TemporalTimeline(
    temporalConfidence: TemporalConfidence,
    selectedTimestampMs: Long,
    onTimestampSelected: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.testTag(FORENSIC_TIMELINE_STRIP_TAG),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        temporalConfidence.bins.forEachIndexed { index, bin ->
            val selected = selectedTimestampMs in bin.startMs..bin.endMs
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(if (selected) 16.dp else 12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(toneColor(toneForProbability(bin.syntheticProbability)))
                        .border(
                            width = if (selected) 1.dp else 0.dp,
                            color = if (selected) VeritasColors.ink else Color.Transparent,
                            shape = RoundedCornerShape(3.dp),
                        )
                        .testTag("$FORENSIC_TIMELINE_SEGMENT_TAG_PREFIX$index")
                        .clickable { onTimestampSelected(bin.startMs) },
            )
        }
    }
}

@Composable
private fun FullscreenHeatmap(
    frame: HeatmapFrame,
    onClose: () -> Unit,
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(FORENSIC_FULLSCREEN_HEATMAP_TAG)
                .background(VeritasColors.bg.copy(alpha = 0.98f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                },
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
        ) {
            HeatmapRenderer(frame)
        }
        VeritasButton(
            text = "Close",
            onClick = onClose,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp)
                    .fillMaxWidth(),
            variant = VeritasButtonVariant.Secondary,
        )
    }
}

@Composable
private fun CornerBrackets(
    modifier: Modifier = Modifier,
    color: Color,
) {
    Box(modifier = modifier.padding(14.dp)) {
        listOf(
            Alignment.TopStart,
            Alignment.TopEnd,
            Alignment.BottomStart,
            Alignment.BottomEnd,
        ).forEach { alignment ->
            Box(
                modifier =
                    Modifier
                        .align(alignment)
                        .size(18.dp)
                        .border(1.dp, color, RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
private fun TimelineTicks(durationMs: Long?) {
    val labels =
        if (durationMs == null || durationMs <= 0) {
            listOf("0:00", "0:04", "0:08", "0:12", "0:16")
        } else {
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { ratio ->
                formatScanDuration((durationMs * ratio).toLong())
            }
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
            )
        }
    }
}

@Composable
private fun ScanFlowScaffold(
    brandName: String,
    onClose: () -> Unit,
    rootTag: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(VeritasColors.bg)
                .statusBarsPadding()
                .testTag(rootTag),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark()
                Text(
                    text = brandName,
                    style =
                        VeritasType.monoSm.copy(
                            color = VeritasColors.ink,
                            fontWeight = FontWeight.W700,
                            letterSpacing = 0.18.em,
                        ),
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(VeritasColors.panel)
                        .clickable(onClick = onClose)
                        .testTag(SCAN_CLOSE_BUTTON_TAG),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "x",
                    style = VeritasType.bodyMd.copy(color = VeritasColors.inkDim, fontWeight = FontWeight.W300),
                )
            }
        }
        HorizontalDivider(color = VeritasColors.line)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun stageMeta(stage: StageUiModel): String {
    if (stage.state != StageRowState.Active || stage.startedAtEpochMs == null) {
        return stage.meta
    }

    val currentTimeMs by
        produceState(initialValue = stage.startedAtEpochMs, key1 = stage.startedAtEpochMs) {
            while (true) {
                value = System.currentTimeMillis()
                delay(100)
            }
        }

    val elapsedMs = (currentTimeMs - stage.startedAtEpochMs).coerceAtLeast(0L)
    return String.format(Locale.US, "%.1fs", elapsedMs / 1000f)
}
