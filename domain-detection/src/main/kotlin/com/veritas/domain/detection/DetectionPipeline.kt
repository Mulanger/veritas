package com.veritas.domain.detection

import kotlinx.coroutines.flow.Flow

interface DetectionPipeline {
    val label: String

    fun stageMessages(): Flow<String>
}
