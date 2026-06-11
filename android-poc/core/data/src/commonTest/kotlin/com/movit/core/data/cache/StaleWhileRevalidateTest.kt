package com.movit.core.data.cache

import com.movit.shared.AppResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaleWhileRevalidateTest {

    private var previousMetricLog: ((String, String, Long) -> Unit)? = null

    @AfterTest
    fun restoreMetricLog() {
        previousMetricLog?.let { FirstFrameMetric.log = it }
        previousMetricLog = null
    }

    @Test
    fun stream_withCache_emitsCachedThenFresh() = runBlocking {
        val states = StaleWhileRevalidate.stream(
            screenId = "home",
            readCached = { "cached-value" },
            fetchFresh = { AppResult.Success("fresh-value") },
        ).toList()

        assertEquals(2, states.size)
        assertEquals(CacheState.Cached("cached-value"), states[0])
        assertEquals(CacheState.Fresh("fresh-value"), states[1])
    }

    @Test
    fun stream_withCache_emitsCachedBeforeFreshCompletes() = runBlocking {
        val received = mutableListOf<CacheState<String>>()

        StaleWhileRevalidate.stream(
            screenId = "train",
            readCached = { "cached-value" },
            fetchFresh = {
                delay(100)
                AppResult.Success("fresh-value")
            },
        ).collect { received.add(it) }

        assertEquals(2, received.size)
        assertTrue(received[0] is CacheState.Cached)
        assertTrue(received[1] is CacheState.Fresh)
        assertEquals("cached-value", (received[0] as CacheState.Cached).value)
        assertEquals("fresh-value", (received[1] as CacheState.Fresh).value)
    }

    @Test
    fun stream_withoutCache_emitsLoadingThenFresh() = runBlocking {
        val states = StaleWhileRevalidate.stream(
            screenId = "reports",
            readCached = { null },
            fetchFresh = { AppResult.Success("fresh-value") },
        ).toList()

        assertEquals(2, states.size)
        assertEquals(CacheState.Loading, states[0])
        assertEquals(CacheState.Fresh("fresh-value"), states[1])
    }

    @Test
    fun stream_withCacheAndFetchFailure_emitsCachedThenError() = runBlocking {
        val states = StaleWhileRevalidate.stream(
            screenId = "session",
            readCached = { "cached-value" },
            fetchFresh = { AppResult.Failure("network down") },
        ).toList()

        assertEquals(2, states.size)
        assertEquals(CacheState.Cached("cached-value"), states[0])
        assertEquals(CacheState.Error("network down", "cached-value"), states[1])
    }

    @Test
    fun firstFrameMetric_recordsFirstEmit() = runBlocking {
        val recorded = mutableListOf<Triple<String, String, Long>>()
        previousMetricLog = FirstFrameMetric.log
        FirstFrameMetric.log = { screenId, stateKind, elapsedMs ->
            recorded.add(Triple(screenId, stateKind, elapsedMs))
        }

        StaleWhileRevalidate.stream(
            screenId = "home",
            readCached = { "cached" },
            fetchFresh = { AppResult.Success("fresh") },
        ).toList()

        assertEquals(1, recorded.size)
        assertEquals("home", recorded[0].first)
        assertEquals("Cached", recorded[0].second)
        assertTrue(recorded[0].third >= 0L)
    }
}
