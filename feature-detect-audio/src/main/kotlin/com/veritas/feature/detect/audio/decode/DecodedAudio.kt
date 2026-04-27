package com.veritas.feature.detect.audio.decode

data class DecodedAudio(
    val pcmBytes: ByteArray,
    val sampleRate: Int,
    val channelCount: Int,
    val durationMs: Long,
    val mimeType: String?,
    val bitrate: Int?,
    val pcmEncoding: PcmEncoding = PcmEncoding.Pcm16Bit,
)

enum class PcmEncoding {
    Pcm8Bit,
    Pcm16Bit,
    PcmFloat,
}
