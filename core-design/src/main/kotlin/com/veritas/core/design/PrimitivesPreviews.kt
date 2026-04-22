@file:Suppress("FunctionName", "UnusedPrivateMember")

package com.veritas.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private const val SAMPLE_METADATA = "VERDICT / VIDEO / 0:23"

@Composable
private fun PreviewPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    VeritasTheme {
        Column(
            modifier =
                modifier
                    .background(VeritasColors.bg)
                    .padding(VeritasSpacing.space20),
            verticalArrangement = Arrangement.spacedBy(VeritasSpacing.space16),
            content = content,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0B0D)
@Composable
private fun BrandMarkPreview() {
    PreviewPanel {
        BrandMark(size = 28.dp)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0B0D, widthDp = 360)
@Composable
private fun VeritasScaffoldPreview() {
    PreviewPanel(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .width(320.dp)
                    .height(200.dp),
        ) {
            VeritasScaffold(onClose = {}) {
                Column(verticalArrangement = Arrangement.spacedBy(VeritasSpacing.space12)) {
                    VeritasTag(text = "GALLERY")
                    Text(
                        text = "Know what's real.",
                        style = VeritasType.displayMd.copy(color = VeritasColors.ink),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0B0D, widthDp = 360)
@Composable
private fun VeritasButtonPreview() {
    PreviewPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.width(320.dp),
            horizontalArrangement = Arrangement.spacedBy(VeritasSpacing.space8),
        ) {
            VeritasButton(
                text = "Primary",
                onClick = {},
                modifier = Modifier.weight(1f),
                variant = VeritasButtonVariant.Primary,
            )
            VeritasButton(
                text = "Secondary",
                onClick = {},
                modifier = Modifier.weight(1f),
                variant = VeritasButtonVariant.Secondary,
            )
        }
        VeritasButton(
            text = "Ghost",
            onClick = {},
            modifier = Modifier.width(320.dp),
            variant = VeritasButtonVariant.Ghost,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0B0D)
@Composable
private fun VeritasTagPreview() {
    PreviewPanel {
        VeritasTag(text = SAMPLE_METADATA)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0B0D, widthDp = 360)
@Composable
private fun ConfidenceRangePreview() {
    PreviewPanel {
        ConfidenceRange(
            low = 82,
            high = 96,
            tone = VerdictTone.Bad,
            modifier = Modifier.width(320.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0B0D, widthDp = 360)
@Composable
private fun EvidenceChipPreview() {
    PreviewPanel {
        EvidenceChip(
            code = "LIP_SYNC_DRIFT",
            description = "Mouth movement slips against the detected speech cadence.",
            variant = EvidenceChipVariant.Minus,
            modifier = Modifier.width(320.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0B0D, widthDp = 360)
@Composable
private fun StageRowPreview() {
    PreviewPanel {
        StageRow(
            label = "Temporal consistency",
            meta = "0.8S",
            state = StageRowState.Active,
            modifier = Modifier.width(320.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0B0D)
@Composable
private fun VerdictPillPreview() {
    PreviewPanel {
        VerdictPill(text = "SYNTHETIC", tone = VerdictTone.Bad)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0B0D, widthDp = 360)
@Composable
private fun StatusBarPreview() {
    PreviewPanel(modifier = Modifier.width(360.dp)) {
        StatusBar()
    }
}
