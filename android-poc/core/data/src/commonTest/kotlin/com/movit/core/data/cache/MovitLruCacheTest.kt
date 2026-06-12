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
}
