package com.movit.core.training.observability

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * In-memory ring buffer of recent pipeline / engine diagnostic lines (I-10).
 * Consumed by debug UI behind [PipelineTraceConfig.isEnabled] via [snapshot].
 * Thread-safe across engine frame thread and debug UI reads (KMP Mutex, not JVM [synchronized]).
 */
class PipelineTrace(private val capacity: Int = DEFAULT_CAPACITY) {
    private val mutex = Mutex()
    private val buffer = ArrayDeque<String>(capacity)

    @Volatile
    private var totalRecorded: Long = 0L

    fun record(line: String) {
        if (!PipelineTraceConfig.isEnabled) return
        runBlocking {
            mutex.withLock {
                while (buffer.size >= capacity) buffer.removeFirst()
                buffer.addLast(line)
                totalRecorded++
            }
        }
    }

    /** Structured diagnostic line for debug HUD (I-10). */
    fun record(stage: String, detail: String) {
        record("$stage:$detail")
    }

    fun clear() {
        runBlocking {
            mutex.withLock {
                buffer.clear()
            }
        }
    }

    /** Immutable copy of buffered lines for debug FPS / pipeline overlay (Agent C). */
    fun snapshot(): List<String> = runBlocking {
        mutex.withLock { buffer.toList() }
    }

    fun stats(): PipelineTraceStats = runBlocking {
        mutex.withLock {
            PipelineTraceStats(
                bufferedCount = buffer.size,
                totalRecorded = totalRecorded,
                capacity = capacity,
            )
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 200
    }
}

data class PipelineTraceStats(
    val bufferedCount: Int,
    val totalRecorded: Long,
    val capacity: Int,
)
