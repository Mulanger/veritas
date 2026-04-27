package com.veritas.feature.detect.video.decode

data class VideoMetadata(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val bitrate: Int?,
    val mimeType: String?,
)

data class ExtractedVideoFrame(
    val timestampMs: Long,
    val bitmap: android.graphics.Bitmap,
)

data class ExtractedVideoFrames(
    val metadata: VideoMetadata,
    val frames: List<ExtractedVideoFrame>,
)
