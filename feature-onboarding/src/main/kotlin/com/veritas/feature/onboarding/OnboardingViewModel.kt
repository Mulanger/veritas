@file:Suppress("TooManyFunctions")

package com.veritas.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep {
    Welcome,
    WhatVeritasDoes,
    HowItWorks,
    ShareSetup,
    OverlayIntro,
    OverlayPermission,
    MediaProjectionEducation,
    Notifications,
    Ready,
}

enum class OverlayPath {
    Unknown,
    Skip,
    EnableWithSettings,
    EnableAlreadyGranted,
}

data class OnboardingUiState(
    val history: List<OnboardingStep>,
    val overlayPath: OverlayPath,
    val notificationsStepRequired: Boolean,
    val overlayPermissionDenied: Boolean,
    val notificationPermissionDenied: Boolean,
    val privacyPolicyVisible: Boolean,
) {
    val currentStep: OnboardingStep = history.last()
    val canGoBack: Boolean = history.size > 1
}

class OnboardingViewModel(
    private val statusStore: OnboardingStatusStore,
    supportsNotificationsPermission: Boolean,
    notificationPermissionGrantedAtLaunch: Boolean,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            OnboardingUiState(
                history = listOf(OnboardingStep.Welcome),
                overlayPath = OverlayPath.Unknown,
                notificationsStepRequired =
                    supportsNotificationsPermission && !notificationPermissionGrantedAtLaunch,
                overlayPermissionDenied = false,
                notificationPermissionDenied = false,
                privacyPolicyVisible = false,
            ),
        )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _completionEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completionEvents: SharedFlow<Unit> = _completionEvents.asSharedFlow()

    fun continueFromWelcome() = push(OnboardingStep.WhatVeritasDoes)

    fun continueFromCapabilities() = push(OnboardingStep.HowItWorks)

    fun continueFromHowItWorks() = push(OnboardingStep.ShareSetup)

    fun continueFromShareSetup() = push(OnboardingStep.OverlayIntro)

    fun chooseOverlayPath(
        wantsBubble: Boolean,
        overlayPermissionAlreadyGranted: Boolean,
    ) {
        if (wantsBubble) {
            _uiState.update {
                it.copy(
                    overlayPath =
                        if (overlayPermissionAlreadyGranted) {
                            OverlayPath.EnableAlreadyGranted
                        } else {
                            OverlayPath.EnableWithSettings
                        },
                    overlayPermissionDenied = false,
                )
            }
            push(
                if (overlayPermissionAlreadyGranted) {
                    OnboardingStep.MediaProjectionEducation
                } else {
                    OnboardingStep.OverlayPermission
                },
            )
        } else {
            _uiState.update {
                it.copy(
                    overlayPath = OverlayPath.Skip,
                    overlayPermissionDenied = false,
                )
            }
            push(nextStepAfterBubblePath())
        }
    }

    fun handleOverlayPermissionResult(granted: Boolean) {
        if (granted) {
            push(OnboardingStep.MediaProjectionEducation)
        } else {
            _uiState.update { it.copy(overlayPermissionDenied = true) }
        }
    }

    fun continueWithoutBubble() {
        _uiState.update {
            it.copy(
                overlayPath = OverlayPath.Skip,
                overlayPermissionDenied = false,
            )
        }
        push(nextStepAfterBubblePath())
    }

    fun continueFromMediaProjection() = push(nextStepAfterBubblePath())

    fun handleNotificationPermissionResult(granted: Boolean) {
        if (granted) {
            push(OnboardingStep.Ready)
        } else {
            _uiState.update { it.copy(notificationPermissionDenied = true) }
        }
    }

    fun continueAfterNotificationDenied() = push(OnboardingStep.Ready)

    fun skipNotifications() = push(OnboardingStep.Ready)

    fun showPrivacyPolicy() {
        _uiState.update { it.copy(privacyPolicyVisible = true) }
    }

    fun hidePrivacyPolicy() {
        _uiState.update { it.copy(privacyPolicyVisible = false) }
    }

    fun goBack() {
        _uiState.update { current ->
            if (current.history.size <= 1) {
                current
            } else {
                current.copy(
                    history = current.history.dropLast(1),
                    overlayPermissionDenied = false,
                    notificationPermissionDenied = false,
                    privacyPolicyVisible = false,
                )
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            statusStore.setHasCompletedOnboarding(true)
            _completionEvents.emit(Unit)
        }
    }

    private fun nextStepAfterBubblePath(): OnboardingStep =
        if (_uiState.value.notificationsStepRequired) {
            OnboardingStep.Notifications
        } else {
            OnboardingStep.Ready
        }

    private fun push(step: OnboardingStep) {
        if (_uiState.value.currentStep == step) {
            return
        }
        _uiState.update {
            it.copy(
                history = it.history + step,
                overlayPermissionDenied = false,
                notificationPermissionDenied = false,
                privacyPolicyVisible = false,
            )
        }
    }

    companion object {
        fun factory(
            statusStore: OnboardingStatusStore,
            supportsNotificationsPermission: Boolean,
            notificationPermissionGrantedAtLaunch: Boolean,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OnboardingViewModel(
                        statusStore = statusStore,
                        supportsNotificationsPermission = supportsNotificationsPermission,
                        notificationPermissionGrantedAtLaunch = notificationPermissionGrantedAtLaunch,
                    ) as T
            }
    }
}
