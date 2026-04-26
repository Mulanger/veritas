package com.veritas.feature.detect.image.forensics

import java.io.File

object JpegQuantization {
    fun isStandardOrAbsent(file: File): Boolean {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return true
        if (bytes.size < MIN_JPEG_BYTES || bytes[0] != JPEG_MARKER_PREFIX || bytes[1] != JPEG_SOI) return true

        val tables = mutableListOf<Int>()
        var index = JPEG_HEADER_SIZE
        while (index + MARKER_HEADER_BYTES < bytes.size) {
            if (bytes[index] != JPEG_MARKER_PREFIX) {
                index++
                continue
            }
            val marker = bytes[index + 1]
            if (marker == JPEG_SOS || marker == JPEG_EOI) break
            val segmentLength = readUInt16(bytes, index + 2)
            if (segmentLength < MARKER_HEADER_BYTES || index + 2 + segmentLength > bytes.size) break
            if (marker == JPEG_DQT) {
                tables.addAll(readDqtFirstValues(bytes, index + 4, segmentLength - MARKER_HEADER_BYTES))
            }
            index += 2 + segmentLength
        }
        if (tables.isEmpty()) return true
        return tables.any { firstValue -> firstValue in STANDARD_FIRST_QUANT_VALUE_RANGE }
    }

    private fun readDqtFirstValues(
        bytes: ByteArray,
        start: Int,
        length: Int,
    ): List<Int> {
        val values = mutableListOf<Int>()
        var cursor = start
        val end = start + length
        while (cursor + QUANT_HEADER_BYTES < end) {
            val precisionAndId = bytes[cursor].toInt() and BYTE_MASK
            val precision = precisionAndId shr PRECISION_SHIFT
            cursor += QUANT_HEADER_BYTES
            if (precision == PRECISION_8_BIT && cursor + QUANT_TABLE_8BIT_SIZE <= end) {
                values.add(bytes[cursor].toInt() and BYTE_MASK)
                cursor += QUANT_TABLE_8BIT_SIZE
            } else if (precision == PRECISION_16_BIT && cursor + QUANT_TABLE_16BIT_SIZE <= end) {
                values.add(readUInt16(bytes, cursor))
                cursor += QUANT_TABLE_16BIT_SIZE
            } else {
                break
            }
        }
        return values
    }

    private fun readUInt16(
        bytes: ByteArray,
        offset: Int,
    ): Int = ((bytes[offset].toInt() and BYTE_MASK) shl BYTE_BITS) or (bytes[offset + 1].toInt() and BYTE_MASK)

    private const val MIN_JPEG_BYTES = 4
    private const val JPEG_HEADER_SIZE = 2
    private const val MARKER_HEADER_BYTES = 2
    private const val BYTE_MASK = 0xFF
    private const val BYTE_BITS = 8
    private const val PRECISION_SHIFT = 4
    private const val PRECISION_8_BIT = 0
    private const val PRECISION_16_BIT = 1
    private const val QUANT_HEADER_BYTES = 1
    private const val QUANT_TABLE_8BIT_SIZE = 64
    private const val QUANT_TABLE_16BIT_SIZE = 128
    private const val JPEG_MARKER_PREFIX = 0xFF.toByte()
    private const val JPEG_SOI = 0xD8.toByte()
    private const val JPEG_EOI = 0xD9.toByte()
    private const val JPEG_DQT = 0xDB.toByte()
    private const val JPEG_SOS = 0xDA.toByte()
    private val STANDARD_FIRST_QUANT_VALUE_RANGE = 1..32
}
