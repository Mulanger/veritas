@file:Suppress("FunctionName")

package com.veritas.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class VeritasPrimitivesScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun BrandMarkScreenshot() {
        capture("brand_mark.png") {
            Box(modifier = Modifier.padding(VeritasSpacing.space20)) {
                BrandMark(size = 24.dp)
            }
        }
    }

    @Test
    fun VeritasScaffoldScreenshot() {
        capture("veritas_scaffold.png") {
            Box(
                modifier =
                    Modifier
                        .width(320.dp)
                        .height(220.dp),
            ) {
                VeritasScaffold(onClose = {}) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(VeritasSpacing.space12),
                    ) {
                        VeritasTag(text = "GALLERY")
                        androidx.compose.material3.Text(
                            text = "Know what's real.",
                            style = VeritasType.displayMd.copy(color = VeritasColors.ink),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun VeritasButtonScreenshot() {
        capture("veritas_button.png") {
            Column(
                modifier =
                    Modifier
                        .width(320.dp)
                        .padding(VeritasSpacing.space20),
                verticalArrangement = Arrangement.spacedBy(VeritasSpacing.space8),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(VeritasSpacing.space8)) {
                    VeritasButton(
                        text = "Primary",
                        onClick = {},
                        variant = VeritasButtonVariant.Primary,
                        modifier = Modifier.weight(1f),
                    )
                    VeritasButton(
                        text = "Secondary",
                        onClick = {},
                        variant = VeritasButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                }
                VeritasButton(
                    text = "Ghost",
                    onClick = {},
                    variant = VeritasButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    @Test
    fun VeritasTagScreenshot() {
        capture("veritas_tag.png") {
            Box(modifier = Modifier.padding(VeritasSpacing.space20)) {
                VeritasTag(text = "VERDICT · VIDEO · 0:23")
            }
        }
    }

    @Test
    fun ConfidenceRangeScreenshot() {
        capture("confidence_range.png") {
            Column(
                modifier =
                    Modifier
                        .width(320.dp)
                        .padding(VeritasSpacing.space20),
                verticalArrangement = Arrangement.spacedBy(VeritasSpacing.space12),
            ) {
                ConfidenceRange(low = 78, high = 94, tone = VerdictTone.Ok)
                ConfidenceRange(low = 31, high = 65, tone = VerdictTone.Warn)
                ConfidenceRange(low = 82, high = 96, tone = VerdictTone.Bad)
            }
        }
    }

    @Test
    fun EvidenceChipScreenshot() {
        capture("evidence_chip.png") {
            Column(
                modifier =
                    Modifier
                        .width(320.dp)
                        .padding(VeritasSpacing.space20),
                verticalArrangement = Arrangement.spacedBy(VeritasSpacing.space8),
            ) {
                EvidenceChip(
                    code = "C2PA_VALID",
                    description = "Content Credentials match a trusted issuer and intact capture chain.",
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
            }
        }
    }

    @Test
    fun StageRowScreenshot() {
        capture("stage_row.png") {
            Column(
                modifier =
                    Modifier
                        .width(320.dp)
                        .padding(VeritasSpacing.space20),
                verticalArrangement = Arrangement.spacedBy(VeritasSpacing.space8),
            ) {
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
            }
        }
    }

    @Test
    fun VerdictPillScreenshot() {
        capture("verdict_pill.png") {
            Row(
                modifier = Modifier.padding(VeritasSpacing.space20),
                horizontalArrangement = Arrangement.spacedBy(VeritasSpacing.space8),
            ) {
                VerdictPill(text = "AUTHENTIC", tone = VerdictTone.Ok)
                VerdictPill(text = "UNCERTAIN", tone = VerdictTone.Warn)
                VerdictPill(text = "SYNTHETIC", tone = VerdictTone.Bad)
            }
        }
    }

    @Test
    fun StatusBarScreenshot() {
        capture("status_bar.png") {
            Box(
                modifier =
                    Modifier
                        .width(360.dp)
                        .padding(vertical = VeritasSpacing.space20),
            ) {
                StatusBar()
            }
        }
    }

    private fun capture(
        fileName: String,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            VeritasTheme {
                Box(modifier = Modifier.background(VeritasColors.bg)) {
                    content()
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(fileName)
    }
}
