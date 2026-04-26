package com.veritas.data.detection.ml.runtime

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import com.google.android.gms.tflite.java.TfLite
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteRtRuntime @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) {
    private val executor = Executors.newFixedThreadPool(INFERENCE_THREADS)
    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
    @Volatile private var initialized = false

    suspend fun ensureInitialized() {
        if (initialized) return
        withContext(dispatcher) {
            if (!initialized) {
                val gpuOptions = TfLiteInitializationOptions.builder()
                    .setEnableGpuDelegateSupport(true)
                    .build()
                runCatching { Tasks.await(TfLite.initialize(appContext, gpuOptions)) }
                    .recoverCatching {
                        val cpuOptions = TfLiteInitializationOptions.builder()
                            .setEnableGpuDelegateSupport(false)
                            .build()
                        Tasks.await(TfLite.initialize(appContext, cpuOptions))
                    }
                    .getOrThrow()
                initialized = true
            }
        }
    }

    companion object {
        private const val INFERENCE_THREADS = 2
    }
}
