package com.veritas.data.detection.ml.inference

import android.content.Context
import android.util.Log
import com.veritas.data.detection.ml.runtime.DelegateChain
import com.veritas.data.detection.ml.runtime.LiteRtRuntime
import com.veritas.data.detection.ml.runtime.ModelAssetSpec
import com.veritas.data.detection.ml.runtime.ModelAssetVerifier
import com.veritas.domain.detection.FallbackLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunnerFactory @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val liteRtRuntime: LiteRtRuntime,
) {
    private val verifier = ModelAssetVerifier()
    private val delegateChain = DelegateChain()

    suspend fun create(spec: ModelAssetSpec): RunnerHandle {
        liteRtRuntime.ensureInitialized()
        val modelAsset = verifier.loadVerified(appContext, spec)
        val delegateSelection = delegateChain.optionsFor(appContext)
        val runnerSelection = runCatching { ModelRunner(modelAsset, delegateSelection) to delegateSelection.fallbackLevel }
            .onFailure { error ->
                Log.w(TAG, "Failed to create model runner with ${delegateSelection.fallbackLevel}; retrying CPU", error)
            }
            .recoverCatching { error ->
                if (delegateSelection.fallbackLevel != FallbackLevel.GPU) {
                    throw error
                }
                val cpuSelection = delegateChain.cpuOptions()
                ModelRunner(modelAsset, cpuSelection) to cpuSelection.fallbackLevel
            }
            .getOrThrow()
        return RunnerHandle(
            runner = runnerSelection.first,
            fallbackLevel = runnerSelection.second,
            modelVersion = modelAsset.version,
        )
    }

    suspend fun createCpu(spec: ModelAssetSpec): RunnerHandle {
        liteRtRuntime.ensureInitialized()
        val modelAsset = verifier.loadVerified(appContext, spec)
        val cpuSelection = delegateChain.cpuOptions()
        return RunnerHandle(
            runner = ModelRunner(modelAsset, cpuSelection),
            fallbackLevel = cpuSelection.fallbackLevel,
            modelVersion = modelAsset.version,
        )
    }

    private companion object {
        private const val TAG = "RunnerFactory"
    }
}

data class RunnerHandle(
    val runner: ModelRunner,
    val fallbackLevel: FallbackLevel,
    val modelVersion: String,
)
