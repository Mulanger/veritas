package com.veritas.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.veritas.core.design.VeritasTheme
import com.veritas.data.detection.MediaIngestionCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ScanStubActivity : ComponentActivity() {
    @Inject
    lateinit var mediaIngestionCoordinator: MediaIngestionCoordinator

    private val scannedMedia by lazy { intent.scannedMediaOrNull() }
    private val ingestionError by lazy { intent.ingestionErrorOrNull() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scannedMedia?.let(mediaIngestionCoordinator::schedulePurge)

        setContent {
            VeritasTheme {
                when {
                    scannedMedia != null ->
                        ScanStubScreen(
                            media = requireNotNull(scannedMedia),
                            onClose = ::finish,
                        )

                    ingestionError != null ->
                        IngestionErrorScreen(
                            error = requireNotNull(ingestionError),
                            onDone = ::finish,
                            onOpenStorageSettings = ::openStorageSettings,
                        )

                    else -> finish()
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            scannedMedia?.let(mediaIngestionCoordinator::purgeNow)
        }
        super.onDestroy()
    }

    private fun openStorageSettings() {
        startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
    }
}
