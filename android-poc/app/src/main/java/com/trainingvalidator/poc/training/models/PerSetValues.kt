package com.trainingvalidator.poc.training.models

/**
 * Expand short per-set lists: last value repeats for remaining sets.
 * e.g. [10, 12] with 4 sets → [10, 12, 12, 12]
 */
object PerSetValues {
    fun expandInts(values: List<Int>?, sets: Int, fallback: Int? = null): List<Int>? {
        val count = sets.coerceAtLeast(1)
        if (values.isNullOrEmpty()) {
            return fallback?.let { value -> List(count) { value } }
        }
        return List(count) { index -> values[minOf(index, values.lastIndex)] }
    }

    fun expandLongs(values: List<Long>?, sets: Int, fallback: Long? = null): List<Long>? {
        val count = sets.coerceAtLeast(1)
        if (values.isNullOrEmpty()) {
            return fallback?.let { value -> List(count) { value } }
        }
        return List(count) { index -> values[minOf(index, values.lastIndex)] }
    }

    fun expandFloats(values: List<Float>?, sets: Int, fallback: Float? = null): List<Float>? {
        val count = sets.coerceAtLeast(1)
        if (values.isNullOrEmpty()) {
            return fallback?.let { value -> List(count) { value } }
        }
        return List(count) { index -> values[minOf(index, values.lastIndex)] }
    }

    fun intAt(values: List<Int>?, setNumber: Int, sets: Int, fallback: Int? = null): Int? {
        val expanded = expandInts(values, sets, fallback) ?: return fallback
        return expanded.getOrNull((setNumber - 1).coerceAtLeast(0)) ?: fallback
    }

    fun longAt(values: List<Long>?, setNumber: Int, sets: Int, fallback: Long? = null): Long? {
        val expanded = expandLongs(values, sets, fallback) ?: return fallback
        return expanded.getOrNull((setNumber - 1).coerceAtLeast(0)) ?: fallback
    }

    fun floatAt(values: List<Float>?, setNumber: Int, sets: Int, fallback: Float? = null): Float? {
        val expanded = expandFloats(values, sets, fallback) ?: return fallback
        return expanded.getOrNull((setNumber - 1).coerceAtLeast(0)) ?: fallback
    }
}
