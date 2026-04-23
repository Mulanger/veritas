package com.veritas.app.debug.testing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.veritas.app.BuildConfig
import com.veritas.app.VeritasHomeEntryHost
import com.veritas.app.buildIngestionErrorIntent
import com.veritas.app.buildScanStubIntent
import com.veritas.app.toErrorScreen
import com.veritas.core.design.VeritasTheme
import com.veritas.data.detection.MediaIngestionCoordinator
import com.veritas.data.detection.MediaIngestionRequest
import com.veritas.data.detection.MediaIngestionResult
import com.veritas.domain.detection.MediaSource
import com.veritas.feature.home.HomeRecentMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class Phase4TestActivity : ComponentActivity() {
    @Inject
    lateinit var mediaIngestionCoordinator: MediaIngestionCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val scope = rememberCoroutineScope()
            var isProcessingSelection by rememberSaveable { mutableStateOf(false) }

            fun ingest(uri: android.net.Uri?) {
                if (uri == null) {
                    return
                }
                scope.launch {
                    isProcessingSelection = true
                    val result =
                        mediaIngestionCoordinator.ingest(
                            MediaIngestionRequest(
                                uri = uri,
                                source = MediaSource.FilePicker,
                            ),
                        )
                    isProcessingSelection = false
                    routeResult(result)
                }
            }

            VeritasTheme {
                VeritasHomeEntryHost(
                    initialRecentMode = HomeRecentMode.Empty,
                    enableHomeDevMenu = BuildConfig.ENABLE_HOME_DEV_MENU,
                    onPickVisualMedia = { ingest(Phase4TestHarness.visualUri) },
                    onPickAudio = { ingest(Phase4TestHarness.audioUri) },
                    initialPasteLink = Phase4TestHarness.initialPasteLink,
                    isProcessingSelection = isProcessingSelection,
                )
            }
        }
    }

    private fun routeResult(result: MediaIngestionResult) {
        val nextIntent =
            when (result) {
                is MediaIngestionResult.Success -> buildScanStubIntent(result.media)
                is MediaIngestionResult.Failure -> buildIngestionErrorIntent(result.error.toErrorScreen())
            }
        startActivity(nextIntent)
    }
}
