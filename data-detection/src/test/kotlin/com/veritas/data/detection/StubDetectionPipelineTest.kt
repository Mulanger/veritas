package com.veritas.data.detection

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StubDetectionPipelineTest {
    @Test
    fun emitsScaffoldingStageMessage() = runBlocking {
        val pipeline = StubDetectionPipeline()

        assertEquals("Scaffolding only", pipeline.stageMessages().first())
    }
}
