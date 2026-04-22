package com.veritas.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildMarkerTest {
    @Test
    fun exposesCurrentPhaseName() {
        assertEquals("Phase 0 - Scaffold", BuildMarker.PHASE_NAME)
    }
}
