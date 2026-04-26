package com.veritas.feature.detect.image.forensics

data class ForensicSignals(
    val hasCameraMake: Boolean,
    val hasCameraModel: Boolean,
    val hasCaptureDateTime: Boolean,
    val hasGps: Boolean,
    val hasExposureMetadata: Boolean,
    val quantTableIsStandard: Boolean,
    val exifCompletenessScore: Float,
    val elaAnomalyScore: Float?,
)
