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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.veritas.domain.detection.ScannedMedia
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
                            code = reasonChipCode(reason.code),
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
    onClose: () -> Unit,
    onBackToVerdict: () -> Unit,
    onReasonSelected: (Reason) -> Unit,
) {
    val timelineSegments = remember(verdict.id, verdict.outcome, media.mediaType) { timelineSegmentsFor(verdict, media.mediaType) }

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

            ForensicViewport(mediaType = media.mediaType)

            if (timelineSegments.isNotEmpty()) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        timelineSegments.forEach { segment ->
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(toneColor(segment.tone)),
                            )
                        }
                    }
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
private fun ForensicViewport(mediaType: MediaType) {
    val label =
        when (mediaType) {
            MediaType.AUDIO -> "Waveform"
            MediaType.VIDEO,
            MediaType.IMAGE,
            -> "Heatmap"
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(18.dp))
                .background(VeritasColors.bg)
                .border(1.dp, VeritasColors.line2, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        CornerBrackets(modifier = Modifier.matchParentSize(), color = VeritasColors.line2)
        Text(
            text = label,
            style = VeritasType.headingMd.copy(color = VeritasColors.inkMute),
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
