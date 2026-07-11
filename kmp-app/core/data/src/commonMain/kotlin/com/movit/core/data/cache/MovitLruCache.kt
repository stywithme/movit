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
        put(key, value, protect = null)
    }

    /**
     * @param protect when true for a key, trim skips it (may leave size > [maxSize]
     * if every entry is protected — used by report sibling retention / H-05).
     */
    fun put(key: K, value: V, protect: ((K) -> Boolean)?) {
        entries.remove(key)
        entries[key] = value
        trimToSize(protect, retainKey = key)
    }

    fun remove(key: K) {
        entries.remove(key)
    }

    fun clear() {
        entries.clear()
    }

    fun size(): Int = entries.size

    private fun trimToSize(protect: ((K) -> Boolean)? = null, retainKey: K? = null) {
        while (entries.size > maxSize) {
            val victim = entries.keys.firstOrNull { candidate ->
                candidate != retainKey && protect?.invoke(candidate) != true
            } ?: break
            entries.remove(victim)
        }
    }
}
