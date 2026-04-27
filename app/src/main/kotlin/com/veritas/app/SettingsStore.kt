package com.veritas.app

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore by preferencesDataStore(name = "veritas_settings")

object SettingsKeys {
    val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("completed_onboarding")
    val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
    val BUBBLE_HAPTICS = booleanPreferencesKey("bubble_haptics")
    val TOAST_AUTO_DISMISS_SECONDS = intPreferencesKey("toast_auto_dismiss")
    val MODEL_AUTO_UPDATE = booleanPreferencesKey("model_auto_update")
    val MODEL_WIFI_ONLY = booleanPreferencesKey("model_wifi_only")
    val TELEMETRY_OPT_IN = booleanPreferencesKey("telemetry_opt_in")
    val TELEMETRY_PROMPT_SHOWN = booleanPreferencesKey("telemetry_prompt_shown")
}

data class VeritasSettings(
    val overlayEnabled: Boolean = false,
    val bubbleHaptics: Boolean = true,
    val toastAutoDismissSeconds: Int = 8,
    val modelAutoUpdate: Boolean = true,
    val modelWifiOnly: Boolean = true,
    val telemetryOptIn: Boolean = false,
    val telemetryPromptShown: Boolean = false,
)

@Singleton
class SettingsRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        val settings: Flow<VeritasSettings> =
            context.settingsDataStore.data.map { preferences ->
                preferences.toSettings()
            }

        suspend fun setOverlayEnabled(enabled: Boolean) = set(SettingsKeys.OVERLAY_ENABLED, enabled)

        suspend fun setBubbleHaptics(enabled: Boolean) = set(SettingsKeys.BUBBLE_HAPTICS, enabled)

        suspend fun setModelAutoUpdate(enabled: Boolean) = set(SettingsKeys.MODEL_AUTO_UPDATE, enabled)

        suspend fun setModelWifiOnly(enabled: Boolean) = set(SettingsKeys.MODEL_WIFI_ONLY, enabled)

        suspend fun setTelemetryOptIn(enabled: Boolean) {
            context.settingsDataStore.edit { preferences ->
                preferences[SettingsKeys.TELEMETRY_OPT_IN] = enabled
                preferences[SettingsKeys.TELEMETRY_PROMPT_SHOWN] = true
            }
        }

        suspend fun dismissTelemetryPrompt(markShown: Boolean) {
            if (markShown) {
                context.settingsDataStore.edit { preferences ->
                    preferences[SettingsKeys.TELEMETRY_PROMPT_SHOWN] = true
                }
            }
        }

        suspend fun resetAll() {
            context.settingsDataStore.edit { preferences ->
                preferences.clear()
            }
        }

        private suspend fun set(
            key: Preferences.Key<Boolean>,
            value: Boolean,
        ) {
            context.settingsDataStore.edit { preferences ->
                preferences[key] = value
            }
        }

        private fun Preferences.toSettings(): VeritasSettings =
            VeritasSettings(
                overlayEnabled = this[SettingsKeys.OVERLAY_ENABLED] ?: false,
                bubbleHaptics = this[SettingsKeys.BUBBLE_HAPTICS] ?: true,
                toastAutoDismissSeconds = this[SettingsKeys.TOAST_AUTO_DISMISS_SECONDS] ?: 8,
                modelAutoUpdate = this[SettingsKeys.MODEL_AUTO_UPDATE] ?: true,
                modelWifiOnly = this[SettingsKeys.MODEL_WIFI_ONLY] ?: true,
                telemetryOptIn = this[SettingsKeys.TELEMETRY_OPT_IN] ?: false,
                telemetryPromptShown = this[SettingsKeys.TELEMETRY_PROMPT_SHOWN] ?: false,
            )
    }
