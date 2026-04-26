package com.veritas.feature.onboarding

import kotlinx.coroutines.flow.Flow

const val LAUNCH_SPLASH_TAG = "launch_splash"
const val ONBOARDING_WELCOME_TAG = "onboarding_welcome"
const val ONBOARDING_CAPABILITIES_TAG = "onboarding_capabilities"
const val ONBOARDING_HOW_IT_WORKS_TAG = "onboarding_how_it_works"
const val ONBOARDING_SHARE_SETUP_TAG = "onboarding_share_setup"
const val ONBOARDING_OVERLAY_INTRO_TAG = "onboarding_overlay_intro"
const val ONBOARDING_OVERLAY_PERMISSION_TAG = "onboarding_overlay_permission"
const val ONBOARDING_MEDIA_PROJECTION_TAG = "onboarding_media_projection"
const val ONBOARDING_NOTIFICATIONS_TAG = "onboarding_notifications"
const val ONBOARDING_READY_TAG = "onboarding_ready"
const val ONBOARDING_PRIVACY_POLICY_TAG = "onboarding_privacy_policy"
const val ONBOARDING_WELCOME_PRIMARY_TAG = "onboarding_welcome_primary"
const val ONBOARDING_CAPABILITIES_PRIMARY_TAG = "onboarding_capabilities_primary"
const val ONBOARDING_HOW_IT_WORKS_PRIMARY_TAG = "onboarding_how_it_works_primary"
const val ONBOARDING_SHARE_SETUP_PRIMARY_TAG = "onboarding_share_setup_primary"
const val ONBOARDING_OVERLAY_ENABLE_TAG = "onboarding_overlay_enable"
const val ONBOARDING_OVERLAY_SKIP_TAG = "onboarding_overlay_skip"
const val ONBOARDING_OVERLAY_SETTINGS_TAG = "onboarding_overlay_settings"
const val ONBOARDING_OVERLAY_CONTINUE_TAG = "onboarding_overlay_continue"
const val ONBOARDING_MEDIA_PROJECTION_PRIMARY_TAG = "onboarding_media_projection_primary"
const val ONBOARDING_NOTIFICATIONS_ALLOW_TAG = "onboarding_notifications_allow"
const val ONBOARDING_NOTIFICATIONS_CONTINUE_TAG = "onboarding_notifications_continue"
const val ONBOARDING_READY_PRIMARY_TAG = "onboarding_ready_primary"

interface OnboardingStatusStore {
    val hasCompletedOnboarding: Flow<Boolean>

    suspend fun setHasCompletedOnboarding(completed: Boolean)
}

class InMemoryOnboardingStatusStore(
    initialCompleted: Boolean = false,
) : OnboardingStatusStore {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(initialCompleted)

    override val hasCompletedOnboarding: Flow<Boolean> = state

    override suspend fun setHasCompletedOnboarding(completed: Boolean) {
        state.value = completed
    }
}
