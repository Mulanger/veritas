package com.veritas.app.debug.testing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.veritas.app.VeritasApp
import com.veritas.core.design.VeritasTheme
import com.veritas.feature.home.HomeRecentMode
import com.veritas.feature.onboarding.OnboardingRoute

class Phase3TestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var showHome by remember { mutableStateOf(false) }
            var overlayPermissionGranted by remember { mutableStateOf(Phase3TestHarness.overlayPermissionGranted) }
            val hasCompletedOnboarding by
                Phase3TestHarness.statusStore.hasCompletedOnboarding.collectAsState(initial = false)

            VeritasTheme {
                if (showHome || hasCompletedOnboarding) {
                    VeritasApp(
                        initialRecentMode = HomeRecentMode.Empty,
                        enableHomeDevMenu = false,
                        onPickFile = {},
                        onPasteLink = {},
                    )
                } else {
                    OnboardingRoute(
                        statusStore = Phase3TestHarness.statusStore,
                        supportsNotificationsPermission = Phase3TestHarness.supportsNotificationsPermission,
                        notificationPermissionGrantedAtLaunch = Phase3TestHarness.notificationPermissionGrantedAtLaunch,
                        overlayPermissionGranted = overlayPermissionGranted,
                        requestOverlayPermission = { callback ->
                            overlayPermissionGranted = Phase3TestHarness.overlayGrantResult
                            callback(overlayPermissionGranted)
                        },
                        requestNotificationsPermission = { callback ->
                            callback(Phase3TestHarness.notificationGrantResult)
                        },
                        onComplete = {
                            Phase3TestHarness.completionCount.incrementAndGet()
                            showHome = true
                        },
                    )
                }
            }
        }
    }
}
