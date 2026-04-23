package com.veritas.app.debug.testing

import com.veritas.feature.onboarding.InMemoryOnboardingStatusStore
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object Phase3TestHarness {
    var supportsNotificationsPermission: Boolean = true
    var notificationPermissionGrantedAtLaunch: Boolean = false
    var notificationGrantResult: Boolean = true
    var overlayPermissionGranted: Boolean = false
    var overlayGrantResult: Boolean = true
    var statusStore = InMemoryOnboardingStatusStore(initialCompleted = false)
    val completionCount = AtomicInteger(0)

    fun reset() {
        supportsNotificationsPermission = true
        notificationPermissionGrantedAtLaunch = false
        notificationGrantResult = true
        overlayPermissionGranted = false
        overlayGrantResult = true
        statusStore = InMemoryOnboardingStatusStore(initialCompleted = false)
        completionCount.set(0)
    }

    fun hasCompletedOnboarding(): Boolean =
        runBlocking {
            statusStore.hasCompletedOnboarding.first()
        }
}
