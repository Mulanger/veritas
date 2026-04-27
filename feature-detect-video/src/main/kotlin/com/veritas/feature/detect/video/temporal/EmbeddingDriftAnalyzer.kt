package com.veritas.feature.detect.video.temporal

import kotlin.math.sqrt

object EmbeddingDriftAnalyzer {
    fun drift(logits: List<FloatArray>): Float {
        if (logits.size < 2) return 0f
        val distances = logits.zipWithNext { first, second -> cosineDistance(first, second) }
        return distances.average().toFloat().coerceIn(0f, 1f)
    }

    private fun cosineDistance(first: FloatArray, second: FloatArray): Float {
        val size = minOf(first.size, second.size)
        if (size == 0) return 0f
        var dot = 0.0
        var firstNorm = 0.0
        var secondNorm = 0.0
        for (index in 0 until size) {
            val a = first[index].toDouble()
            val b = second[index].toDouble()
            dot += a * b
            firstNorm += a * a
            secondNorm += b * b
        }
        val denom = sqrt(firstNorm) * sqrt(secondNorm)
        if (denom <= 0.0) return 0f
        return (1.0 - dot / denom).toFloat().coerceIn(0f, 1f)
    }
}
