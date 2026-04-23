@file:Suppress("FunctionName", "MagicNumber", "TooManyFunctions")

package com.veritas.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

enum class VeritasButtonVariant {
    Primary,
    Secondary,
    Ghost,
}

enum class VerdictTone {
    Ok,
    Warn,
    Bad,
}

enum class EvidenceChipVariant {
    Plus,
    Mixed,
    Minus,
}

enum class StageRowState {
    Idle,
    Active,
    Done,
}

private data class StageRowPalette(
    val borderColor: Color,
    val textColor: Color,
    val dotColor: Color,
    val alpha: Float,
)

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .border(
                    width = 1.5.dp,
                    color = VeritasColors.accent,
                    shape = RoundedCornerShape(4.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(size - 6.dp)
                    .border(
                        width = 1.dp,
                        color = VeritasColors.accent.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(1.dp),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .size(size / 2.25f)
                    .background(
                        color = VeritasColors.accent.copy(alpha = 0.24f),
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(size / 4.5f)
                        .background(
                            color = VeritasColors.accent,
                            shape = CircleShape,
                        ),
            )
        }
    }
}

@Composable
fun VeritasScaffold(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(VeritasColors.bg),
    ) {
        VeritasScaffoldHeader(onClose = onClose)
        HorizontalDivider(color = VeritasColors.line)

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = VeritasSpacing.space20,
                        vertical = VeritasSpacing.space20,
                    ),
        ) {
            content()
        }
    }
}

@Composable
private fun VeritasScaffoldHeader(onClose: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = VeritasSpacing.space20,
                    vertical = VeritasSpacing.space16,
                ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(VeritasSpacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark()
            Text(
                text = "VERITAS",
                style =
                    VeritasType.monoSm.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W700,
                        letterSpacing = 0.18.em,
                        color = VeritasColors.ink,
                    ),
            )
        }

        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(VeritasColors.panel)
                    .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "x",
                style =
                    VeritasType.bodyMd.copy(
                        color = VeritasColors.inkDim,
                        fontWeight = FontWeight.W300,
                        fontSize = 16.sp,
                    ),
            )
        }
    }
}

@Composable
fun VeritasButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: VeritasButtonVariant = VeritasButtonVariant.Primary,
    testTag: String? = null,
) {
    val backgroundColor =
        when (variant) {
            VeritasButtonVariant.Primary -> VeritasColors.ink
            VeritasButtonVariant.Secondary -> VeritasColors.panel
            VeritasButtonVariant.Ghost -> VeritasColors.panel
        }
    val textColor =
        when (variant) {
            VeritasButtonVariant.Primary -> VeritasColors.bg
            VeritasButtonVariant.Secondary -> VeritasColors.inkDim
            VeritasButtonVariant.Ghost -> VeritasColors.ink
        }
    val borderColor =
        when (variant) {
            VeritasButtonVariant.Primary -> Color.Transparent
            VeritasButtonVariant.Secondary,
            VeritasButtonVariant.Ghost,
            -> VeritasColors.line
        }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(VeritasRadius.md))
                .background(backgroundColor)
                .border(
                    BorderStroke(1.dp, borderColor),
                    RoundedCornerShape(VeritasRadius.md),
                )
                .clickable(onClick = onClick)
                .then(
                    if (testTag == null) {
                        Modifier
                    } else {
                        Modifier.testTag(testTag)
                    },
                )
                .padding(
                    horizontal = VeritasSpacing.space16,
                    vertical = 13.dp,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style =
                VeritasType.bodyMd.copy(
                    color = textColor,
                    fontWeight = FontWeight.W600,
                    fontSize = 13.sp,
                ),
        )
    }
}

@Composable
fun VeritasTag(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style =
            VeritasType.monoXs.copy(
                color = VeritasColors.inkMute,
                fontSize = 10.sp,
            ),
    )
}

@Composable
fun ConfidenceRange(
    low: Int,
    high: Int,
    tone: VerdictTone,
    modifier: Modifier = Modifier,
) {
    val rangeColor = toneColor(tone)
    val clampedLow = low.coerceIn(0, 100)
    val clampedHigh = high.coerceIn(clampedLow, 100)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(VeritasSpacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(VeritasColors.panel),
        ) {
            val startOffset = maxWidth * (clampedLow / 100f)
            val rangeWidth = maxWidth * ((clampedHigh - clampedLow) / 100f)

            Box(
                modifier =
                    Modifier
                        .offset(x = startOffset)
                        .width(rangeWidth)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(rangeColor),
            )
        }

        Text(
            text = "$clampedLow-$clampedHigh%",
            style = VeritasType.monoSm.copy(color = VeritasColors.inkDim),
        )
    }
}

@Composable
fun EvidenceChip(
    code: String,
    description: String,
    variant: EvidenceChipVariant,
    modifier: Modifier = Modifier,
) {
    val borderColor =
        when (variant) {
            EvidenceChipVariant.Plus -> VeritasColors.okDim
            EvidenceChipVariant.Mixed -> VeritasColors.warnDim
            EvidenceChipVariant.Minus -> VeritasColors.badDim
        }
    val codeColor =
        when (variant) {
            EvidenceChipVariant.Plus -> VeritasColors.ok
            EvidenceChipVariant.Mixed -> VeritasColors.warn
            EvidenceChipVariant.Minus -> VeritasColors.bad
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(VeritasRadius.md))
                .background(VeritasColors.panel)
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(VeritasRadius.md),
                )
                .padding(
                    horizontal = VeritasSpacing.space12,
                    vertical = VeritasSpacing.space10(),
                ),
        horizontalArrangement = Arrangement.spacedBy(VeritasSpacing.space12),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = code,
            style =
                VeritasType.monoXs.copy(
                    color = codeColor,
                    fontWeight = FontWeight.W700,
                ),
        )
        Text(
            text = description,
            modifier = Modifier.weight(1f),
            style = VeritasType.bodySm.copy(color = VeritasColors.inkDim),
        )
    }
}

@Composable
fun StageRow(
    label: String,
    meta: String,
    state: StageRowState,
    modifier: Modifier = Modifier,
) {
    val palette = stageRowPalette(state)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(palette.alpha)
                .clip(RoundedCornerShape(8.dp))
                .background(VeritasColors.panel)
                .border(
                    width = 1.dp,
                    color = palette.borderColor,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(
                    horizontal = VeritasSpacing.space12,
                    vertical = VeritasSpacing.space10(),
                ),
        horizontalArrangement = Arrangement.spacedBy(VeritasSpacing.space10()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(palette.dotColor, CircleShape),
        )

        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = VeritasType.bodySm.copy(color = palette.textColor),
        )

        Text(
            text = meta,
            style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
        )
    }
}

@Composable
fun VerdictPill(
    text: String,
    tone: VerdictTone,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(toneDimColor(tone))
                .padding(
                    horizontal = VeritasSpacing.space8,
                    vertical = VeritasSpacing.space4,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style =
                VeritasType.monoXs.copy(
                    color = toneColor(tone),
                    fontWeight = FontWeight.W700,
                    letterSpacing = 0.1.em,
                ),
        )
    }
}

@Composable
fun StatusBar(
    modifier: Modifier = Modifier,
    time: String = "9:41",
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = time,
            style =
                VeritasType.monoXs.copy(
                    color = VeritasColors.ink,
                    fontWeight = FontWeight.W700,
                ),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                SignalBar(3.dp)
                SignalBar(5.dp)
                SignalBar(7.dp)
                SignalBar(9.dp)
            }

            Row(
                modifier =
                    Modifier
                        .width(22.dp)
                        .height(10.dp)
                        .border(1.dp, VeritasColors.ink, RoundedCornerShape(2.dp))
                        .padding(1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .weight(0.7f)
                            .fillMaxSize()
                            .background(VeritasColors.ink, RoundedCornerShape(1.dp)),
                )
                Spacer(modifier = Modifier.weight(0.3f))
            }

            Box(
                modifier =
                    Modifier
                        .size(width = 2.dp, height = 4.dp)
                        .background(VeritasColors.ink, RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
private fun SignalBar(height: Dp) {
    Box(
        modifier =
            Modifier
                .width(2.dp)
                .height(height)
                .background(VeritasColors.ink, RoundedCornerShape(1.dp)),
    )
}

private fun toneColor(tone: VerdictTone): Color =
    when (tone) {
        VerdictTone.Ok -> VeritasColors.ok
        VerdictTone.Warn -> VeritasColors.warn
        VerdictTone.Bad -> VeritasColors.bad
    }

private fun toneDimColor(tone: VerdictTone): Color =
    when (tone) {
        VerdictTone.Ok -> VeritasColors.okDim
        VerdictTone.Warn -> VeritasColors.warnDim
        VerdictTone.Bad -> VeritasColors.badDim
    }

private fun stageRowPalette(state: StageRowState): StageRowPalette =
    when (state) {
        StageRowState.Active ->
            StageRowPalette(
                borderColor = VeritasColors.accent,
                textColor = VeritasColors.ink,
                dotColor = VeritasColors.accent,
                alpha = 1f,
            )
        StageRowState.Done ->
            StageRowPalette(
                borderColor = VeritasColors.line,
                textColor = VeritasColors.inkDim,
                dotColor = VeritasColors.ok,
                alpha = 0.6f,
            )
        StageRowState.Idle ->
            StageRowPalette(
                borderColor = VeritasColors.line,
                textColor = VeritasColors.inkDim,
                dotColor = VeritasColors.line2,
                alpha = 1f,
            )
    }

private fun VeritasSpacing.space10(): Dp = 10.dp
