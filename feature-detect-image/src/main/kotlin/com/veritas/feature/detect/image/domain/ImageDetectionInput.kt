package com.veritas.feature.detect.image.domain

import com.veritas.domain.detection.DetectorInput
import com.veritas.domain.detection.ScannedMedia
import java.io.File

data class ImageDetectionInput(
    override val media: ScannedMedia,
    val file: File,
) : DetectorInput
