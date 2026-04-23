package com.veritas.domain.detection

import kotlinx.coroutines.flow.Flow

interface DetectionPipeline {
    fun scan(media: ScannedMedia): Flow<ScanStage>

    fun cancel()
}

interface DetectorInput {
    val media: ScannedMedia
}

data class VideoInput(
    override val media: ScannedMedia,
) : DetectorInput

data class AudioInput(
    override val media: ScannedMedia,
) : DetectorInput

data class ImageInput(
    override val media: ScannedMedia,
) : DetectorInput

interface Detector<in TInput : DetectorInput, out TResult : DetectorResult> {
    suspend fun detect(input: TInput): TResult
}

sealed interface DetectorResult {
    val detectorId: String
    val syntheticScore: Float
    val confidence: Float
    val reasons: List<Reason>
    val elapsedMs: Long
}

data class BasicDetectorResult(
    override val detectorId: String,
    override val syntheticScore: Float,
    override val confidence: Float,
    override val reasons: List<Reason>,
    override val elapsedMs: Long,
) : DetectorResult
