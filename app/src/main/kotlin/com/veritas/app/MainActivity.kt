package com.veritas.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.veritas.core.design.VeritasTheme
import com.veritas.domain.detection.DetectionPipeline
import com.veritas.feature.home.HomeRecentMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var detectionPipeline: DetectionPipeline

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("Launching shell with %s", detectionPipeline.label)

        setContent {
            val documentPicker =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri == null) {
                        Timber.i("File picker dismissed")
                    } else {
                        Timber.i("File picker returned %s", uri)
                    }
                }

            VeritasTheme {
                VeritasApp(
                    initialRecentMode =
                        if (BuildConfig.ENABLE_HOME_MOCK_HISTORY) {
                            HomeRecentMode.Populated
                        } else {
                            HomeRecentMode.Empty
                        },
                    enableHomeDevMenu = BuildConfig.ENABLE_HOME_DEV_MENU,
                    onPickFile = { documentPicker.launch(SUPPORTED_MIME_TYPES) },
                    onPasteLink = { Timber.i("Paste link requested") },
                )
            }
        }
    }

    private companion object {
        val SUPPORTED_MIME_TYPES = arrayOf("video/*", "audio/*", "image/*")
    }
}
