package com.veritas.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.veritas.core.design.VeritasTheme
import com.veritas.data.detection.MediaIngestionCoordinator
import com.veritas.domain.detection.DetectionPipeline
import com.veritas.domain.detection.ScanStage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
@Suppress("TooManyFunctions")
class ScanActivity : ComponentActivity() {
    @Inject
    lateinit var detectionPipeline: DetectionPipeline

    @Inject
    lateinit var mediaIngestionCoordinator: MediaIngestionCoordinator

    private val scannedMedia by lazy { intent.scannedMediaOrNull() }
    private val ingestionError by lazy { intent.ingestionErrorOrNull() }

    private var scanUiState by mutableStateOf(ScanUiState())
    private var scanJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scannedMedia?.let { media ->
            mediaIngestionCoordinator.schedulePurge(media)
            startScan(media)
        }

        setContent {
            VeritasTheme {
                when {
                    scannedMedia != null ->
                        ScanFlowScreen(
                            state = scanUiState,
                            onClose = ::handleClose,
                            onPrimaryVerdictAction = ::handlePrimaryVerdictAction,
                            onDone = ::finish,
                            onBackToVerdict = ::showVerdict,
                            onReasonSelected = ::showReason,
                            onReasonDismiss = ::dismissReason,
                            onFindOriginalDismiss = ::dismissFindOriginalSheet,
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
            detectionPipeline.cancel()
            scanJob?.cancel()
            scannedMedia?.let(mediaIngestionCoordinator::purgeNow)
        }
        super.onDestroy()
    }

    private fun startScan(media: com.veritas.domain.detection.ScannedMedia) {
        scanUiState = ScanUiState.scanning(media)
        scanJob?.cancel()
        scanJob =
            lifecycleScope.launch {
                detectionPipeline.scan(media).collect { stage ->
                    when (stage) {
                        is ScanStage.Cancelled -> finishSafely()
                        is ScanStage.Failed -> finishSafely()
                        else -> scanUiState = scanUiState.applyStage(stage)
                    }
                }
            }
    }

    private fun handleClose() {
        if (scanUiState.surface == ScanSurface.Scanning) {
            detectionPipeline.cancel()
            scanJob?.cancel()
        }
        finish()
    }

    private fun handlePrimaryVerdictAction() {
        when (scanUiState.verdict?.outcome) {
            com.veritas.domain.detection.VerdictOutcome.VERIFIED_AUTHENTIC,
            com.veritas.domain.detection.VerdictOutcome.LIKELY_AUTHENTIC,
            com.veritas.domain.detection.VerdictOutcome.LIKELY_SYNTHETIC,
            -> scanUiState = scanUiState.copy(surface = ScanSurface.Forensic, showFindOriginalSheet = false)

            com.veritas.domain.detection.VerdictOutcome.UNCERTAIN ->
                scanUiState = scanUiState.copy(showFindOriginalSheet = true)

            null -> Unit
        }
    }

    private fun showVerdict() {
        scanUiState =
            scanUiState.copy(
                surface = ScanSurface.Verdict,
                selectedReason = null,
                showFindOriginalSheet = false,
            )
    }

    private fun showReason(reason: com.veritas.domain.detection.Reason) {
        scanUiState = scanUiState.copy(selectedReason = reason)
    }

    private fun dismissReason() {
        scanUiState = scanUiState.copy(selectedReason = null)
    }

    private fun dismissFindOriginalSheet() {
        scanUiState = scanUiState.copy(showFindOriginalSheet = false)
    }

    private fun finishSafely() {
        if (!isFinishing) {
            finish()
        }
    }

    private fun openStorageSettings() {
        startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
    }
}
