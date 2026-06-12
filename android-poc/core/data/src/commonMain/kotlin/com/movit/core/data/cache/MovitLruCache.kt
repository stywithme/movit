package com.movit.core.data.cache

/**
 * Small in-memory LRU map for parsed entities and handoff caches (F6/F7).
 */
class MovitLruCache<K, V>(private val maxSize: Int) {
    init {
        require(maxSize > 0) { "maxSize must be positive" }
    }

    private val entries = linkedMapOf<K, V>()

    fun get(key: K): V? {
        val value = entries.remove(key) ?: return null
        entries[key] = value
        return value
    }

    fun put(key: K, value: V) {
        entries.remove(key)
        entries[key] = value
        trimToSize()
    }

    fun remove(key: K) {
        entries.remove(key)
    }

    fun clear() {
        entries.clear()
    }

    fun size(): Int = entries.size

    private fun trimToSize() {
        while (entries.size > maxSize) {
            val eldest = entries.keys.first()
            entries.remove(eldest)
        }
    }
}
