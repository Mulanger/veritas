package com.veritas.feature.detect.video.decode

object FrameSampler {
    fun sampleTimestamps(
        durationMs: Long,
        targetFrameCount: Int = DEFAULT_TARGET_FRAMES,
    ): List<Long> {
        if (durationMs <= 0L || targetFrameCount <= 0) return emptyList()
        val count = targetFrameCount.coerceAtMost((durationMs / MIN_FRAME_INTERVAL_MS).coerceAtLeast(1L).toInt())
        if (count == 1) return listOf(durationMs / 2L)
        val first = FRAME_EDGE_PADDING_MS.coerceAtMost(durationMs / 4L)
        val last = (durationMs - first).coerceAtLeast(first)
        return List(count) { index ->
            first + ((last - first) * index / (count - 1))
        }.distinct()
    }

    const val DEFAULT_TARGET_FRAMES = 4
    private const val MIN_FRAME_INTERVAL_MS = 125L
    private const val FRAME_EDGE_PADDING_MS = 100L
}
