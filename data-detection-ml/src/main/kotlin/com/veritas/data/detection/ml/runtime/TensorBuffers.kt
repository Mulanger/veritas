package com.veritas.data.detection.ml.runtime

import java.nio.ByteBuffer
import java.nio.ByteOrder

object TensorBuffers {
    fun floatBuffer(floatCount: Int): ByteBuffer =
        ByteBuffer.allocateDirect(floatCount * FLOAT_BYTES).order(ByteOrder.nativeOrder())

    private const val FLOAT_BYTES = 4
}
