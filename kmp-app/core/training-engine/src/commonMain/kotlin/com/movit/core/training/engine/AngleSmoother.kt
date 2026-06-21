package com.movit.core.training.engine

/**
 * Moving-average smoother for per-joint angles (ported from legacy).
 */
class AngleSmoother(
    private val windowSize: Int,
) {
    private val jointBuffers = mutableMapOf<String, CircularAngleBuffer>()

    private inner class CircularAngleBuffer {
        private val buffer = ArrayDeque<Double>(windowSize)
        private var runningSum = 0.0

        fun add(value: Double): Double {
            if (buffer.size >= windowSize) {
                runningSum -= buffer.removeFirst()
            }
            buffer.addLast(value)
            runningSum += value
            return runningSum / buffer.size
        }

        fun clear() {
            buffer.clear()
            runningSum = 0.0
        }
    }

    fun smooth(rawAngles: Map<String, Double>): Map<String, Double> =
        rawAngles.mapValues { (code, angle) ->
            jointBuffers.getOrPut(code) { CircularAngleBuffer() }.add(angle)
        }

    fun clearJoints(jointCodes: Set<String>) {
        jointCodes.forEach { jointBuffers.remove(it) }
    }

    fun reset() {
        jointBuffers.values.forEach { it.clear() }
        jointBuffers.clear()
    }
}
