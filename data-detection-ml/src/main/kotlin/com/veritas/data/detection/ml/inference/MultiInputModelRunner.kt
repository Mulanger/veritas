package com.veritas.data.detection.ml.inference

import com.veritas.data.detection.ml.runtime.DelegateSelection
import com.veritas.data.detection.ml.runtime.LoadedModelAsset
import org.tensorflow.lite.DataType
import org.tensorflow.lite.InterpreterApi
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MultiInputModelRunner(
    modelAsset: LoadedModelAsset,
    delegateSelection: DelegateSelection,
) : Closeable {
    private val interpreter: InterpreterApi =
        InterpreterApi.create(modelAsset.buffer.asReadOnlyBuffer(), delegateSelection.options)

    val inputSpecs: List<TensorSpec> =
        List(interpreter.inputTensorCount) { index ->
            val tensor = interpreter.getInputTensor(index)
            TensorSpec(index = index, name = tensor.name(), shape = tensor.shape(), dataType = tensor.dataType())
        }

    val outputSpecs: List<TensorSpec> =
        List(interpreter.outputTensorCount) { index ->
            val tensor = interpreter.getOutputTensor(index)
            TensorSpec(index = index, name = tensor.name(), shape = tensor.shape(), dataType = tensor.dataType())
        }

    fun run(
        inputs: Array<Any>,
        outputs: MutableMap<Int, Any>,
    ) {
        inputs.filterIsInstance<ByteBuffer>().forEach { it.rewind() }
        outputs.values.filterIsInstance<ByteBuffer>().forEach { it.rewind() }
        interpreter.runForMultipleInputsOutputs(inputs, outputs)
        outputs.values.filterIsInstance<ByteBuffer>().forEach { it.rewind() }
    }

    override fun close() {
        interpreter.close()
    }
}

data class TensorSpec(
    val index: Int,
    val name: String,
    val shape: IntArray,
    val dataType: DataType,
) {
    val byteSize: Int
        get() = shape.fold(1) { acc, dim -> acc * dim.coerceAtLeast(1) } * bytesPerElement(dataType)

    fun newBuffer(): ByteBuffer = ByteBuffer.allocateDirect(byteSize).order(ByteOrder.nativeOrder())
}

private fun bytesPerElement(dataType: DataType): Int =
    when (dataType) {
        DataType.FLOAT32,
        DataType.INT32,
        -> 4
        DataType.UINT8,
        DataType.INT8,
        DataType.BOOL,
        -> 1
        else -> error("Unsupported tensor data type: $dataType")
    }
