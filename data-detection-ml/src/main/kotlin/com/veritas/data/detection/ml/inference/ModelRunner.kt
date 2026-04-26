package com.veritas.data.detection.ml.inference

import com.veritas.data.detection.ml.runtime.DelegateSelection
import com.veritas.data.detection.ml.runtime.LoadedModelAsset
import org.tensorflow.lite.InterpreterApi
import java.io.Closeable
import java.nio.ByteBuffer

class ModelRunner(
    modelAsset: LoadedModelAsset,
    delegateSelection: DelegateSelection,
) : Closeable {
    private val interpreter: InterpreterApi =
        InterpreterApi.create(modelAsset.buffer.asReadOnlyBuffer(), delegateSelection.options)

    fun run(
        input: ByteBuffer,
        output: ByteBuffer,
    ) {
        input.rewind()
        output.rewind()
        interpreter.run(input, output)
        output.rewind()
    }

    override fun close() {
        interpreter.close()
    }
}
