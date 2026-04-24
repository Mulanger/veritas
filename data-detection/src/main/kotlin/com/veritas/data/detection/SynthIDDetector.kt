package com.veritas.data.detection

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class SynthIDResult {
    data object NotPresent : SynthIDResult()
    data class Detected(
        val generatorName: String,
        val signalStrength: Float,
    ) : SynthIDResult()
}

@Singleton
class SynthIDDetector @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    companion object {
        private const val TAG = "SynthIDDetector"
    }

    suspend fun detect(file: File, mediaType: com.veritas.domain.detection.MediaType): SynthIDResult =
        withContext(Dispatchers.IO) {
            Log.w(TAG, "SynthID detection deferred to v1.1 — no public SDK available at Phase 6 build time. See OQ-006 in glossary.")
            SynthIDResult.NotPresent
        }
}
