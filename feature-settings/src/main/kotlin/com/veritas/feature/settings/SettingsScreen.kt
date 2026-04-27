@file:Suppress("FunctionName", "LongMethod", "LongParameterList", "MaxLineLength", "TooManyFunctions")

package com.veritas.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.core.design.BrandMark
import com.veritas.core.design.VeritasButton
import com.veritas.core.design.VeritasButtonVariant
import com.veritas.core.design.VeritasColors
import com.veritas.core.design.VeritasType

const val SETTINGS_SCREEN_ROOT_TAG = "screen_settings"
const val SETTINGS_ROW_OVERLAY_TAG = "settings_row_overlay"
const val SETTINGS_ROW_MODELS_TAG = "settings_row_models"
const val SETTINGS_ROW_PRIVACY_TAG = "settings_row_privacy"
const val SETTINGS_ROW_DIAGNOSTICS_TAG = "settings_row_diagnostics"
const val SETTINGS_ROW_ABOUT_TAG = "settings_row_about"
const val SETTINGS_TELEMETRY_TOGGLE_TAG = "settings_telemetry_toggle"
const val SETTINGS_CLEAR_HISTORY_TAG = "settings_clear_history"

@Composable
fun SettingsScreen(
    overlayEnabled: Boolean,
    bubbleHaptics: Boolean,
    modelAutoUpdate: Boolean,
    modelWifiOnly: Boolean,
    telemetryOptIn: Boolean,
    onOverlayEnabledChange: (Boolean) -> Unit,
    onBubbleHapticsChange: (Boolean) -> Unit,
    onModelAutoUpdateChange: (Boolean) -> Unit,
    onModelWifiOnlyChange: (Boolean) -> Unit,
    onTelemetryOptInChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onCreateDiagnosticExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var screen by remember { mutableStateOf(SettingsPane.Home) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    val homeTitle = stringResource(SettingsPane.Home.titleRes)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(VeritasColors.bg),
    ) {
        SettingsTopBar(
            title = stringResource(screen.titleRes),
            homeTitle = homeTitle,
            onBack = { screen = SettingsPane.Home },
        )
        Column(
            modifier =
                Modifier
                    .testTag(SETTINGS_SCREEN_ROOT_TAG)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (screen) {
                SettingsPane.Home -> SettingsHome(overlayEnabled) { screen = it }
                SettingsPane.Overlay ->
                    OverlaySettings(
                        overlayEnabled = overlayEnabled,
                        bubbleHaptics = bubbleHaptics,
                        onOverlayEnabledChange = onOverlayEnabledChange,
                        onBubbleHapticsChange = onBubbleHapticsChange,
                    )
                SettingsPane.Models ->
                    ModelSettings(
                        modelAutoUpdate = modelAutoUpdate,
                        modelWifiOnly = modelWifiOnly,
                        onModelAutoUpdateChange = onModelAutoUpdateChange,
                        onModelWifiOnlyChange = onModelWifiOnlyChange,
                    )
                SettingsPane.Privacy ->
                    PrivacySettings(
                        telemetryOptIn = telemetryOptIn,
                        onTelemetryOptInChange = onTelemetryOptInChange,
                        onClearHistory = { confirmClearHistory = true },
                    )
                SettingsPane.Diagnostics -> DiagnosticSettings(onCreateDiagnosticExport)
                SettingsPane.About -> AboutSettings()
            }
        }
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearHistory = false
                        onClearHistory()
                    },
                ) {
                    Text(stringResource(R.string.settings_clear_history))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
            title = { Text(stringResource(R.string.settings_clear_history_question)) },
            text = { Text(stringResource(R.string.settings_clear_history_body)) },
            containerColor = VeritasColors.panel,
            titleContentColor = VeritasColors.ink,
            textContentColor = VeritasColors.inkDim,
        )
    }
}

@Composable
private fun SettingsHome(
    overlayEnabled: Boolean,
    onOpen: (SettingsPane) -> Unit,
) {
    SettingsRow(
        title = stringResource(R.string.settings_overlay_title),
        subtitle = if (overlayEnabled) stringResource(R.string.settings_state_on) else stringResource(R.string.settings_state_off),
        testTag = SETTINGS_ROW_OVERLAY_TAG,
    ) { onOpen(SettingsPane.Overlay) }
    SettingsRow(stringResource(R.string.settings_models_title), stringResource(R.string.settings_models_summary), SETTINGS_ROW_MODELS_TAG) { onOpen(SettingsPane.Models) }
    SettingsRow(stringResource(R.string.settings_privacy_title), stringResource(R.string.settings_privacy_summary), SETTINGS_ROW_PRIVACY_TAG) { onOpen(SettingsPane.Privacy) }
    SettingsRow(stringResource(R.string.settings_diagnostics_title), stringResource(R.string.settings_diagnostics_summary), SETTINGS_ROW_DIAGNOSTICS_TAG) { onOpen(SettingsPane.Diagnostics) }
    SettingsRow(stringResource(R.string.settings_about_title), stringResource(R.string.settings_about_summary), SETTINGS_ROW_ABOUT_TAG) { onOpen(SettingsPane.About) }
}

@Composable
private fun OverlaySettings(
    overlayEnabled: Boolean,
    bubbleHaptics: Boolean,
    onOverlayEnabledChange: (Boolean) -> Unit,
    onBubbleHapticsChange: (Boolean) -> Unit,
) {
    ToggleRow(stringResource(R.string.settings_floating_bubble), stringResource(R.string.settings_floating_bubble_summary), overlayEnabled, onOverlayEnabledChange)
    ToggleRow(stringResource(R.string.settings_haptic_feedback), stringResource(R.string.settings_haptic_feedback_summary), bubbleHaptics, onBubbleHapticsChange)
    Text(stringResource(R.string.settings_overlay_permissions), style = VeritasType.bodySm.copy(color = VeritasColors.inkDim))
}

@Composable
private fun ModelSettings(
    modelAutoUpdate: Boolean,
    modelWifiOnly: Boolean,
    onModelAutoUpdateChange: (Boolean) -> Unit,
    onModelWifiOnlyChange: (Boolean) -> Unit,
) {
    Text(stringResource(R.string.settings_active_models), style = VeritasType.monoXs.copy(color = VeritasColors.inkMute, fontWeight = FontWeight.W700))
    ModelRow(stringResource(R.string.settings_video_detector), stringResource(R.string.settings_model_video_version), stringResource(R.string.settings_model_video_size), stringResource(R.string.settings_model_updated_two_days))
    ModelRow(stringResource(R.string.settings_audio_detector), stringResource(R.string.settings_model_audio_version), stringResource(R.string.settings_model_audio_size), stringResource(R.string.settings_model_updated_two_days))
    ModelRow(stringResource(R.string.settings_image_detector), stringResource(R.string.settings_model_image_version), stringResource(R.string.settings_model_image_size), stringResource(R.string.settings_model_updated_five_days))
    VeritasButton(text = stringResource(R.string.settings_check_for_updates), onClick = {}, modifier = Modifier.fillMaxWidth(), variant = VeritasButtonVariant.Ghost)
    ToggleRow(stringResource(R.string.settings_auto_update), stringResource(R.string.settings_auto_update_summary), modelAutoUpdate, onModelAutoUpdateChange)
    ToggleRow(stringResource(R.string.settings_wifi_only), stringResource(R.string.settings_wifi_only_summary), modelWifiOnly, onModelWifiOnlyChange)
}

@Composable
private fun PrivacySettings(
    telemetryOptIn: Boolean,
    onTelemetryOptInChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
) {
    Text(stringResource(R.string.settings_privacy_heading), style = VeritasType.headingLg.copy(color = VeritasColors.ink))
    Text(stringResource(R.string.settings_privacy_stores), style = VeritasType.bodyMd.copy(color = VeritasColors.inkDim))
    Text(stringResource(R.string.settings_privacy_sends), style = VeritasType.bodyMd.copy(color = VeritasColors.inkDim))
    Text(stringResource(R.string.settings_privacy_never), style = VeritasType.bodyMd.copy(color = VeritasColors.inkDim))
    ToggleRow(
        title = stringResource(R.string.settings_telemetry_title),
        subtitle = stringResource(R.string.settings_telemetry_summary),
        checked = telemetryOptIn,
        onCheckedChange = onTelemetryOptInChange,
        testTag = SETTINGS_TELEMETRY_TOGGLE_TAG,
    )
    VeritasButton(
        text = stringResource(R.string.settings_clear_history),
        onClick = onClearHistory,
        modifier = Modifier.fillMaxWidth().testTag(SETTINGS_CLEAR_HISTORY_TAG),
        variant = VeritasButtonVariant.Ghost,
    )
}

@Composable
private fun DiagnosticSettings(onCreateDiagnosticExport: () -> Unit) {
    Text(stringResource(R.string.settings_export_diagnostic), style = VeritasType.headingLg.copy(color = VeritasColors.ink))
    Text(stringResource(R.string.settings_export_diagnostic_body), style = VeritasType.bodyMd.copy(color = VeritasColors.inkDim))
    VeritasButton(text = stringResource(R.string.settings_create_export), onClick = onCreateDiagnosticExport, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.settings_export_included), style = VeritasType.bodySm.copy(color = VeritasColors.inkMute))
}

@Composable
private fun AboutSettings() {
    SettingsRow(stringResource(R.string.settings_app_version), stringResource(R.string.settings_about_summary), SETTINGS_ROW_ABOUT_TAG) {}
    SettingsRow(stringResource(R.string.settings_terms), stringResource(R.string.settings_terms_summary), "settings_terms") {}
    SettingsRow(stringResource(R.string.settings_privacy_policy), stringResource(R.string.settings_privacy_policy_summary), "settings_privacy_policy") {}
    SettingsRow(stringResource(R.string.settings_licenses), stringResource(R.string.settings_licenses_summary), "settings_licenses") {}
    SettingsRow(stringResource(R.string.settings_credits), stringResource(R.string.settings_credits_summary), "settings_credits") {}
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .testTag(testTag)
                .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = VeritasType.monoXs.copy(color = VeritasColors.ink, fontWeight = FontWeight.W700))
        Text(subtitle, style = VeritasType.bodySm.copy(color = VeritasColors.inkMute))
    }
    HorizontalDivider(color = VeritasColors.line)
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String = "settings_toggle_$title",
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = VeritasType.bodyMd.copy(color = VeritasColors.ink, fontWeight = FontWeight.W700))
            Text(subtitle, style = VeritasType.bodySm.copy(color = VeritasColors.inkMute))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ModelRow(
    title: String,
    version: String,
    size: String,
    updated: String,
) {
    SettingsRow(title, stringResource(R.string.settings_model_row, version, size, updated), "settings_model_$title") {}
}

@Composable
private fun SettingsTopBar(
    title: String,
    homeTitle: String,
    onBack: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Text(stringResource(R.string.settings_brand), style = VeritasType.monoSm.copy(color = VeritasColors.ink, fontSize = 12.sp))
        }
        Text(
            text = title,
            modifier = Modifier.clickable(enabled = title != homeTitle, onClick = onBack),
            style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
        )
    }
    HorizontalDivider(color = VeritasColors.line)
}

private enum class SettingsPane(
    @param:StringRes val titleRes: Int,
) {
    Home(R.string.settings_title),
    Overlay(R.string.settings_overlay_title),
    Models(R.string.settings_models_title),
    Privacy(R.string.settings_privacy_title),
    Diagnostics(R.string.settings_diagnostics_title),
    About(R.string.settings_about_title),
}
