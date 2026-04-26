package com.veritas.feature.detect.video.domain

import com.veritas.domain.detection.DetectorInput
import com.veritas.domain.detection.ScannedMedia
import java.io.File

data class VideoDetectionInput(
    override val media: ScannedMedia,
    val file: File,
) : DetectorInput
