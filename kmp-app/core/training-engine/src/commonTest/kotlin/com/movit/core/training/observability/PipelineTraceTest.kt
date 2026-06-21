package com.movit.core.training.observability

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PipelineTraceTest {
    @BeforeTest
    fun enableTrace() {
        PipelineTraceConfig.setEnabled(true)
    }

    @AfterTest
    fun disableTrace() {
        PipelineTraceConfig.setEnabled(false)
    }

    @Test
    fun recordsWhenEnabled_respectsCapacity() {
        val trace = PipelineTrace(capacity = 3)
        trace.record("a")
        trace.record("b")
        trace.record("c")
        trace.record("d")
        assertEquals(listOf("b", "c", "d"), trace.snapshot())
        assertEquals(4, trace.stats().totalRecorded)
    }

    @Test
    fun doesNotRecordWhenDisabled() {
        PipelineTraceConfig.setEnabled(false)
        val trace = PipelineTrace()
        trace.record("x")
        assertTrue(trace.snapshot().isEmpty())
    }

    @Test
    fun recordStageDetail_formatsLine() {
        val trace = PipelineTrace()
        trace.record("phase", "idle->down")
        assertEquals(listOf("phase:idle->down"), trace.snapshot())
    }
}
