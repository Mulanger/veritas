package com.veritas.feature.detect.audio.domain

import com.veritas.domain.detection.DetectorInput
import com.veritas.domain.detection.ScannedMedia
import java.io.File

data class AudioDetectionInput(
    override val media: ScannedMedia,
    val file: File,
) : DetectorInput
