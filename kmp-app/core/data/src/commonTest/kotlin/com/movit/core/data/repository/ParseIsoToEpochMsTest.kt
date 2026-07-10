package com.movit.core.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ParseIsoToEpochMsTest {
    @Test
    fun zSuffix_matchesUtc() {
        val ms = DayCustomizationLocalStore.parseIsoToEpochMs("2026-07-10T12:00:00Z")
        assertNotNull(ms)
        assertEquals(
            DayCustomizationLocalStore.parseIsoToEpochMs("2026-07-10T12:00:00"),
            ms,
        )
    }

    @Test
    fun plusOffset_subtractsFromLocalWallTime() {
        val utc = DayCustomizationLocalStore.parseIsoToEpochMs("2026-07-10T12:00:00Z")
        val plus3 = DayCustomizationLocalStore.parseIsoToEpochMs("2026-07-10T15:00:00+03:00")
        assertNotNull(utc)
        assertNotNull(plus3)
        assertEquals(utc, plus3)
    }

    @Test
    fun minusOffset_addsToLocalWallTime() {
        val utc = DayCustomizationLocalStore.parseIsoToEpochMs("2026-07-10T12:00:00.000Z")
        val minus5 = DayCustomizationLocalStore.parseIsoToEpochMs("2026-07-10T07:00:00-05:00")
        assertNotNull(utc)
        assertNotNull(minus5)
        assertEquals(utc, minus5)
    }
}
