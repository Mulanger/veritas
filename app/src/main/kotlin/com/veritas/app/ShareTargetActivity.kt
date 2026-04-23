package com.veritas.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.veritas.core.design.VeritasTheme
import com.veritas.data.detection.MediaIngestionCoordinator
import com.veritas.data.detection.MediaIngestionRequest
import com.veritas.data.detection.MediaIngestionResult
import com.veritas.domain.detection.MediaSource
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ShareTargetActivity : ComponentActivity() {
    @Inject
    lateinit var mediaIngestionCoordinator: MediaIngestionCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VeritasTheme {
                ShareTargetLoadingScreen()
            }
        }

        lifecycleScope.launch {
            when {
                intent.type == "text/plain" -> routeTextShare()
                else -> ingestSharedMedia()
            }
        }
    }

    private suspend fun ingestSharedMedia() {
        val uri = extractIncomingUri() ?: run {
            finish()
            return
        }

        val result =
            mediaIngestionCoordinator.ingest(
                MediaIngestionRequest(
                    uri = uri,
                    source =
                        MediaSource.ShareIntent(
                            sourcePackage = resolveSourcePackage(),
                        ),
                ),
            )
        routeResult(result)
    }

    private fun routeTextShare() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_PASTE_LINK, intent.getStringExtra(Intent.EXTRA_TEXT))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        finish()
    }

    private fun routeResult(result: MediaIngestionResult) {
        val nextIntent =
            when (result) {
                is MediaIngestionResult.Success -> buildScanStubIntent(result.media)
                is MediaIngestionResult.Failure -> buildIngestionErrorIntent(result.error.toErrorScreen())
            }
        startActivity(nextIntent)
        finish()
    }

    private fun extractIncomingUri(): Uri? =
        when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_SEND_MULTIPLE ->
                intent
                    .getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    ?.firstOrNull()
            else -> null
        }

    private fun resolveSourcePackage(): String? =
        callingPackage
            ?: referrer?.host
            ?: intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)
}
