package com.trainingvalidator.poc.training.engine.observability

import java.util.concurrent.ArrayBlockingQueue

/**
 * In-memory ring buffer of recent pipeline / engine diagnostic lines.
 * (Phase 8 — optional hook for a future debug screen.)
 */
class PipelineTrace(private val capacity: Int = 200) {
    private val q = ArrayBlockingQueue<String>(capacity)

    @Synchronized
    fun record(line: String) {
        while (q.size >= capacity) q.poll()
        q.offer(line)
    }

    @Synchronized
    fun clear() {
        q.clear()
    }

    @Synchronized
    fun snapshot(): List<String> = q.toList()
}
