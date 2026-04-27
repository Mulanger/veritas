package com.veritas.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.veritas.domain.detection.ConfidenceRange
import com.veritas.domain.detection.InferenceHardware
import com.veritas.domain.detection.MediaSource
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.ScannedMedia
import com.veritas.domain.detection.Verdict
import com.veritas.domain.detection.VerdictOutcome
import com.veritas.feature.history.HISTORY_EMPTY_STATE_TAG
import com.veritas.feature.history.HISTORY_ITEM_TAG_PREFIX
import com.veritas.feature.history.HistoryListItemUi
import com.veritas.feature.history.HistoryScreen
import com.veritas.feature.settings.SETTINGS_ROW_DIAGNOSTICS_TAG
import com.veritas.feature.settings.SETTINGS_ROW_PRIVACY_TAG
import com.veritas.feature.settings.SETTINGS_SCREEN_ROOT_TAG
import com.veritas.feature.settings.SETTINGS_TELEMETRY_TOGGLE_TAG
import com.veritas.feature.settings.SettingsScreen
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Phase11HistorySettingsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun historyEmptyStateAndRowActionsRenderWithComposeApi() {
        val openedItems = mutableListOf<String>()
        val diagnosticItems = mutableListOf<String>()

        composeRule.setContent {
            HistoryScreen(
                items =
                    listOf(
                        HistoryListItemUi(
                            id = "history-1",
                            mediaType = MediaType.IMAGE,
                            durationMs = null,
                            sourceLabel = "Share sheet",
                            thumbnailPath = "",
                            verdictOutcome = VerdictOutcome.LIKELY_SYNTHETIC,
                            scannedAtEpochMs = System.currentTimeMillis(),
                        ),
                    ),
                onCheckSomething = {},
                onOpenItem = { openedItems += it },
                onDeleteItem = {},
                onExportDiagnostic = { diagnosticItems += it },
            )
        }

        composeRule.onNodeWithTag("${HISTORY_ITEM_TAG_PREFIX}history-1").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("history-1"), openedItems)
            assertTrue(diagnosticItems.isEmpty())
        }

        composeRule.setContent {
            HistoryScreen(
                items = emptyList(),
                onCheckSomething = {},
                onOpenItem = {},
                onDeleteItem = {},
                onExportDiagnostic = {},
            )
        }

        composeRule.onNodeWithTag(HISTORY_EMPTY_STATE_TAG).assertIsDisplayed()
    }

    @Test
    fun settingsPrivacyAndDiagnosticsPanesRenderWithComposeApi() {
        var telemetryOptIn by mutableStateOf(false)
        var exportCount = 0

        composeRule.setContent {
            var overlayEnabled by remember { mutableStateOf(false) }
            SettingsScreen(
                overlayEnabled = overlayEnabled,
                bubbleHaptics = true,
                modelAutoUpdate = true,
                modelWifiOnly = true,
                telemetryOptIn = telemetryOptIn,
                onOverlayEnabledChange = { overlayEnabled = it },
                onBubbleHapticsChange = {},
                onModelAutoUpdateChange = {},
                onModelWifiOnlyChange = {},
                onTelemetryOptInChange = { telemetryOptIn = it },
                onClearHistory = {},
                onCreateDiagnosticExport = { exportCount += 1 },
            )
        }

        composeRule.onNodeWithTag(SETTINGS_SCREEN_ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_ROW_PRIVACY_TAG).performClick()
        composeRule.onNodeWithTag(SETTINGS_TELEMETRY_TOGGLE_TAG).performClick()
        composeRule.runOnIdle { assertTrue(telemetryOptIn) }
        composeRule.onNodeWithText("PRIVACY & DATA").performClick()
        composeRule.onNodeWithTag(SETTINGS_ROW_DIAGNOSTICS_TAG).performClick()
        composeRule.onNodeWithText("Create export").performClick()
        composeRule.runOnIdle { assertEquals(1, exportCount) }
    }

    @Test
    fun telemetryModalDismissesWithComposeApi() {
        var decideLaterCount = 0

        composeRule.setContent {
            ScanFlowScreen(
                state = ScanUiState(media = media(), surface = ScanSurface.Verdict, verdict = verdict()),
                onClose = {},
                onPrimaryVerdictAction = {},
                onDone = {},
                onBackToVerdict = {},
                onReasonSelected = {},
                onReasonDismiss = {},
                onFindOriginalDismiss = {},
                showTelemetryPrompt = true,
                onTelemetryDecideLater = { decideLaterCount += 1 },
            )
        }

        composeRule.onNodeWithTag(TELEMETRY_OPT_IN_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(TELEMETRY_DECIDE_LATER_TAG).performClick()
        composeRule.runOnIdle { assertEquals(1, decideLaterCount) }
    }

    private fun media(): ScannedMedia =
        ScannedMedia(
            id = "phase11-media",
            uri = "file:///tmp/phase11.jpg",
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            sizeBytes = 1024,
            durationMs = null,
            widthPx = 1280,
            heightPx = 720,
            source = MediaSource.FilePicker,
            ingestedAt = Clock.System.now(),
        )

    private fun verdict(): Verdict =
        Verdict(
            id = "phase11-verdict",
            mediaId = "phase11-media",
            mediaType = MediaType.IMAGE,
            outcome = VerdictOutcome.LIKELY_AUTHENTIC,
            confidence = ConfidenceRange(66, 84),
            summary = "No strong synthetic indicators were found.",
            reasons = emptyList(),
            modelVersions = mapOf("image" to "phase11-test"),
            scannedAt = Clock.System.now(),
            inferenceHardware = InferenceHardware.CPU_XNNPACK,
            elapsedMs = 100,
        )
}
