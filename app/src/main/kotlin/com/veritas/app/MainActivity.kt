package com.veritas.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.veritas.core.design.VeritasTheme
import com.veritas.domain.detection.DetectionPipeline
import com.veritas.feature.home.HomeScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var detectionPipeline: DetectionPipeline

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("Launching with %s", detectionPipeline.label)

        setContent {
            VeritasTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(statusLabel = detectionPipeline.label)
                }
            }
        }
    }
}
