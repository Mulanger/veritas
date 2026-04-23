package com.veritas.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.veritas.core.design.VeritasTheme
import com.veritas.data.detection.MediaIngestionCoordinator
import com.veritas.data.detection.MediaIngestionRequest
import com.veritas.data.detection.MediaIngestionResult
import com.veritas.domain.detection.DetectionPipeline
import com.veritas.domain.detection.MediaSource
import com.veritas.feature.home.HomeRecentMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

const val EXTRA_INITIAL_PASTE_LINK = "extra_initial_paste_link"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var detectionPipeline: DetectionPipeline

    @Inject
    lateinit var mediaIngestionCoordinator: MediaIngestionCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("Launching shell with %s", detectionPipeline.label)

        setContent {
            val scope = rememberCoroutineScope()
            var isProcessingSelection by rememberSaveable { mutableStateOf(false) }

            fun ingestPickedUri(uri: android.net.Uri?) {
                if (uri == null) {
                    Timber.i("Picker dismissed")
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
                    routeIngestionResult(result)
                }
            }

            val visualPicker =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia(),
                ) { uri ->
                    ingestPickedUri(uri)
                }

            val audioPicker =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        try {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        } catch (_: SecurityException) {
                            Timber.d("No persistable permission grant available for %s", uri)
                        }
                    }
                    ingestPickedUri(uri)
                }

            VeritasTheme {
                VeritasHomeEntryHost(
                    initialRecentMode =
                        if (BuildConfig.ENABLE_HOME_MOCK_HISTORY) {
                            HomeRecentMode.Populated
                        } else {
                            HomeRecentMode.Empty
                        },
                    enableHomeDevMenu = BuildConfig.ENABLE_HOME_DEV_MENU,
                    onPickVisualMedia = {
                        visualPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                            ),
                        )
                    },
                    onPickAudio = { audioPicker.launch(AUDIO_MIME_TYPES) },
                    initialPasteLink = intent.getStringExtra(EXTRA_INITIAL_PASTE_LINK),
                    isProcessingSelection = isProcessingSelection,
                )
            }
        }
    }

    private fun routeIngestionResult(result: MediaIngestionResult) {
        val nextIntent =
            when (result) {
                is MediaIngestionResult.Success -> buildScanStubIntent(result.media)
                is MediaIngestionResult.Failure -> buildIngestionErrorIntent(result.error.toErrorScreen())
            }
        startActivity(nextIntent)
    }

    private companion object {
        val AUDIO_MIME_TYPES = arrayOf("audio/mpeg", "audio/aac", "audio/wav", "audio/mp4", "audio/x-m4a")
    }
}
