package com.veritas.feature.detect.image.forensics

import androidx.exifinterface.media.ExifInterface
import java.io.File

class ExifAnalyzer {
    fun analyze(file: File): ExifSignals {
        val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()
            ?: return ExifSignals.empty()

        val hasCameraMake = exif.getAttribute(ExifInterface.TAG_MAKE).isPresent()
        val hasCameraModel = exif.getAttribute(ExifInterface.TAG_MODEL).isPresent()
        val hasCaptureDateTime =
            exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL).isPresent() ||
                exif.getAttribute(ExifInterface.TAG_DATETIME).isPresent()
        val hasGps =
            exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE).isPresent() &&
                exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE).isPresent()
        val hasExposureMetadata =
            exif.getAttribute(ExifInterface.TAG_F_NUMBER).isPresent() ||
                exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME).isPresent() ||
                exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY).isPresent()
        val presentCount = listOf(hasCameraMake, hasCameraModel, hasCaptureDateTime, hasExposureMetadata).count { it }

        return ExifSignals(
            hasCameraMake = hasCameraMake,
            hasCameraModel = hasCameraModel,
            hasCaptureDateTime = hasCaptureDateTime,
            hasGps = hasGps,
            hasExposureMetadata = hasExposureMetadata,
            exifCompletenessScore = presentCount / EXPECTED_EXIF_FIELDS.toFloat(),
        )
    }

    private fun String?.isPresent(): Boolean = !isNullOrBlank()

    companion object {
        private const val EXPECTED_EXIF_FIELDS = 4
    }
}

data class ExifSignals(
    val hasCameraMake: Boolean,
    val hasCameraModel: Boolean,
    val hasCaptureDateTime: Boolean,
    val hasGps: Boolean,
    val hasExposureMetadata: Boolean,
    val exifCompletenessScore: Float,
) {
    companion object {
        fun empty(): ExifSignals =
            ExifSignals(
                hasCameraMake = false,
                hasCameraModel = false,
                hasCaptureDateTime = false,
                hasGps = false,
                hasExposureMetadata = false,
                exifCompletenessScore = 0f,
            )
    }
}
