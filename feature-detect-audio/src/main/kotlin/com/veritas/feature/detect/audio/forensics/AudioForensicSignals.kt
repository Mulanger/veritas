package com.veritas.feature.detect.audio.forensics

data class AudioForensicSignals(
    val tooShort: Boolean,
    val tooLong: Boolean,
    val lowSampleRate: Boolean,
    val monoPlausible: Boolean,
    val codecPlausibilityScore: Float,
)
