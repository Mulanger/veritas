package com.veritas.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.veritas.core.design.VeritasTheme
import com.veritas.feature.onboarding.LaunchSplashScreen
import com.veritas.feature.onboarding.OnboardingStatusStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LaunchActivity : ComponentActivity() {
    @Inject
    lateinit var onboardingStatusStore: OnboardingStatusStore

    private var showIndicator by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VeritasTheme {
                LaunchSplashScreen(showIndicator = showIndicator)
            }
        }

        lifecycleScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            val indicatorJob =
                launch {
                    delay(INDICATOR_DELAY_MILLIS)
                    showIndicator = true
                }

            val hasCompletedOnboarding = onboardingStatusStore.hasCompletedOnboarding.first()
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            if (elapsed < MIN_SPLASH_MILLIS) {
                delay(MIN_SPLASH_MILLIS - elapsed)
            }

            indicatorJob.cancel()

            val targetActivity =
                if (hasCompletedOnboarding) {
                    MainActivity::class.java
                } else {
                    OnboardingActivity::class.java
                }

            startActivity(Intent(this@LaunchActivity, targetActivity))
            finish()
        }
    }

    private companion object {
        const val INDICATOR_DELAY_MILLIS = 400L
        const val MIN_SPLASH_MILLIS = 500L
    }
}
