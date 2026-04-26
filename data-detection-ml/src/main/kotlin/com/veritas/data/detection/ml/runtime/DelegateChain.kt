package com.veritas.data.detection.ml.runtime

import android.content.Context
import android.os.Build
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tflite.gpu.support.TfLiteGpu
import com.veritas.domain.detection.FallbackLevel
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime
import org.tensorflow.lite.gpu.GpuDelegateFactory
import java.util.concurrent.ConcurrentHashMap

class DelegateChain {
    private val gpuAvailabilityByDevice = ConcurrentHashMap<String, Boolean>()

    fun optionsFor(context: Context): DelegateSelection {
        val deviceKey = "${Build.MANUFACTURER}:${Build.MODEL}:${Build.VERSION.SDK_INT}"
        val gpuAvailable = gpuAvailabilityByDevice.getOrPut(deviceKey) {
            runCatching { Tasks.await(TfLiteGpu.isGpuDelegateAvailable(context)) }.getOrDefault(false)
        }
        return if (gpuAvailable) {
            DelegateSelection(options = baseOptions().addDelegateFactory(GpuDelegateFactory()), fallbackLevel = FallbackLevel.GPU)
        } else {
            cpuOptions()
        }
    }

    fun cpuOptions(): DelegateSelection =
        DelegateSelection(options = baseOptions(), fallbackLevel = FallbackLevel.CPU_XNNPACK)

    private fun baseOptions(): InterpreterApi.Options =
        InterpreterApi.Options()
            .setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
            .setNumThreads(CPU_THREAD_COUNT)

    companion object {
        private const val CPU_THREAD_COUNT = 2
    }
}

data class DelegateSelection(
    val options: InterpreterApi.Options,
    val fallbackLevel: FallbackLevel,
)
