package com.movit.core.training.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovitRepReplaySamplerTest {

    private var nowMs = 1_000L

    private fun sampler() = MovitRepReplaySampler(timeProvider = { nowMs })

    @Test
    fun tryRegisterFrame_respectsPerRepCap() {
        val sampler = sampler()
        repeat(MovitRepReplaySampler.MAX_FRAMES_PER_REP) { index ->
            nowMs += 100L
            assertTrue(sampler.tryRegisterFrame(1, "/tmp/rep1-$index.jpg"))
        }
        assertFalse(sampler.tryRegisterFrame(1, "/tmp/rep1-overflow.jpg"))
        assertEquals(MovitRepReplaySampler.MAX_FRAMES_PER_REP, sampler.clips().single().frames.size)
    }

    @Test
    fun clipForRep_requiresMinimumFrames() {
        val sampler = sampler()
        assertNull(sampler.clipForRep(2))
        sampler.tryRegisterFrame(2, "/tmp/rep2-0.jpg")
        assertNull(sampler.clipForRep(2))
        nowMs += 200L
        sampler.tryRegisterFrame(2, "/tmp/rep2-1.jpg")
        val clip = sampler.clipForRep(2)
        assertNotNull(clip)
        assertEquals(2, clip.frames.size)
        assertEquals(200L, clip.frames[1].offsetMs)
    }

    @Test
    fun rollingWindow_evictsHighestRepFirst() {
        val sampler = sampler()
        repeat(MovitRepReplaySampler.MAX_TRACKED_REPS + 2) { index ->
            val rep = index + 1
            sampler.tryRegisterFrame(rep, "/tmp/rep$rep-a.jpg")
            nowMs += 50L
            sampler.tryRegisterFrame(rep, "/tmp/rep$rep-b.jpg")
            nowMs += 50L
        }
        val trackedReps = sampler.clips().map { it.repNumber }.toSet()
        assertEquals(MovitRepReplaySampler.MAX_TRACKED_REPS, trackedReps.size)
        assertTrue(12 in trackedReps)
        assertTrue(1 in trackedReps)
        assertFalse(11 in trackedReps)
    }
}
