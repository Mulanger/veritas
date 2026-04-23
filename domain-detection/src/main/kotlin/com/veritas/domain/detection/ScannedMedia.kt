@file:Suppress("DEPRECATION")

package com.veritas.domain.detection

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class MediaType {
    VIDEO,
    AUDIO,
    IMAGE,
}

@Serializable
data class ScannedMedia(
    val id: String,
    val uri: String,
    val mediaType: MediaType,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long?,
    val widthPx: Int?,
    val heightPx: Int?,
    val source: MediaSource,
    val ingestedAt: Instant,
)

@Serializable
sealed class MediaSource {
    @Serializable
    data class ShareIntent(
        val sourcePackage: String?,
    ) : MediaSource()

    @Serializable
    data object FilePicker : MediaSource()

    @Serializable
    data object Overlay : MediaSource()

    @Serializable
    data class Link(
        val url: String,
    ) : MediaSource()
}
