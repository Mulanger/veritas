package com.veritas.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.veritas.core.design.VeritasTheme
import com.veritas.feature.onboarding.OnboardingRoute
import com.veritas.feature.onboarding.OnboardingStatusStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {
    @Inject
    lateinit var onboardingStatusStore: OnboardingStatusStore

    private var overlayPermissionGranted by mutableStateOf(false)
    private var overlayPermissionCallback: ((Boolean) -> Unit)? = null
    private var notificationPermissionCallback: ((Boolean) -> Unit)? = null

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val granted = Settings.canDrawOverlays(this)
            overlayPermissionGranted = granted
            overlayPermissionCallback?.invoke(granted)
            overlayPermissionCallback = null
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationPermissionCallback?.invoke(granted)
            notificationPermissionCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overlayPermissionGranted = Settings.canDrawOverlays(this)

        setContent {
            VeritasTheme {
                OnboardingRoute(
                    statusStore = onboardingStatusStore,
                    supportsNotificationsPermission = supportsNotificationsPermission(),
                    notificationPermissionGrantedAtLaunch = notificationPermissionGranted(),
                    overlayPermissionGranted = overlayPermissionGranted,
                    requestOverlayPermission = { callback ->
                        overlayPermissionCallback = callback
                        overlayPermissionLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName"),
                            ),
                        )
                    },
                    requestNotificationsPermission = { callback ->
                        if (supportsNotificationsPermission()) {
                            notificationPermissionCallback = callback
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            callback(true)
                        }
                    },
                    onComplete = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayPermissionGranted = Settings.canDrawOverlays(this)
    }

    private fun notificationPermissionGranted(): Boolean =
        if (!supportsNotificationsPermission()) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun supportsNotificationsPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
