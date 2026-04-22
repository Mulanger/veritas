package com.veritas.data.detection

import com.veritas.core.common.BuildMarker
import com.veritas.domain.detection.DetectionPipeline
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class StubDetectionPipeline @Inject constructor() : DetectionPipeline {
    override val label: String = "${BuildMarker.PHASE_NAME} stub"

    override fun stageMessages(): Flow<String> {
        return flowOf("Scaffolding only")
    }
}
