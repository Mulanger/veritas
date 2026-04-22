package com.veritas.domain.detection

import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class DetectionPipelineContractTest {
    @Test
    fun keepsLabelAccessibleWithoutAndroidRuntime() {
        val pipeline =
            object : DetectionPipeline {
                override val label: String = "contract"

                override fun stageMessages() = emptyFlow<String>()
            }

        assertEquals("contract", pipeline.label)
    }
}
