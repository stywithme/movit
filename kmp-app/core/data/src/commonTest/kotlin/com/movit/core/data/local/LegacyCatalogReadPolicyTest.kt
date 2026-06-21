package com.movit.core.data.local

import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.MovitCacheKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LegacyCatalogReadPolicyTest {

    @Test
    fun kmpPrimaryStores_blockRuntimePlatformFallback() {
        assertEquals(false, LegacyCatalogReadPolicy.allowsRuntimePlatformFallback(MovitCacheKeys.EXPLORE_STORE))
        assertEquals(false, LegacyCatalogReadPolicy.allowsRuntimePlatformFallback(MovitCacheKeys.EXERCISE_CONFIG_STORE))
        assertEquals(false, LegacyCatalogReadPolicy.allowsRuntimePlatformFallback(MovitCacheKeys.LEGACY_DAY_CUSTOMIZATION_STORE))
    }
}

class MigratingMovitLocalStoreCutoverTest {

    @Test
    fun readJsonCache_doesNotFallbackToPlatformForKmpCatalogStores() {
        val platform = FakeMovitPlatformBindings()
        platform.writeCache(
            MovitCacheKeys.EXPLORE_STORE,
            MovitCacheKeys.EXPLORE_DATA,
            """{"exercises":[]}""",
        )

        val sql = InMemoryMovitLocalStore()
        val store = MigratingMovitLocalStore(sql, platform = { platform })

        assertNull(store.readJsonCache(MovitCacheKeys.EXPLORE_STORE, MovitCacheKeys.EXPLORE_DATA))
    }

    @Test
    fun migrateKnownCachesFromPlatform_stillCopiesLegacyPlatformJson() {
        val platform = FakeMovitPlatformBindings()
        val exploreJson = """{"exercises":[]}"""
        platform.writeCache(MovitCacheKeys.EXPLORE_STORE, MovitCacheKeys.EXPLORE_DATA, exploreJson)

        val sql = InMemoryMovitLocalStore()
        val store = MigratingMovitLocalStore(sql, platform = { platform })
        store.migrateKnownCachesFromPlatform()

        assertEquals(exploreJson, sql.readJsonCache(MovitCacheKeys.EXPLORE_STORE, MovitCacheKeys.EXPLORE_DATA))
        assertEquals(
            "true",
            sql.readJsonCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.LEGACY_CUTOVER_V1),
        )
    }
}
