package com.movit.core.data.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MovitLruCacheTest {

    @Test
    fun evictsLeastRecentlyUsedWhenOverCapacity() {
        val cache = MovitLruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.get("a")
        cache.put("c", 3)

        assertNull(cache.get("b"))
        assertEquals(1, cache.get("a"))
        assertEquals(3, cache.get("c"))
    }

    @Test
    fun put_protectSkipsProtectedKeysDuringTrim() {
        val cache = MovitLruCache<String, Int>(2)
        cache.put("keep-a", 1)
        cache.put("keep-b", 2)
        cache.put("other", 3) { key -> key.startsWith("keep-") }

        assertEquals(1, cache.get("keep-a"))
        assertEquals(2, cache.get("keep-b"))
        assertEquals(3, cache.get("other"))
        assertEquals(3, cache.size())
    }
}
