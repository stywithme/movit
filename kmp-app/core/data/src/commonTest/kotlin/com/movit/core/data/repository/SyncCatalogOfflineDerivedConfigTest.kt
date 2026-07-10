package com.movit.core.data.repository

import com.movit.core.data.cache.MessageLibraryCache
import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.MobileSyncDataDto
import com.movit.core.network.dto.WorkoutExportDto
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SyncCatalogOfflineDerivedConfigTest {

    @Test
    fun applyFromSync_doesNotWriteDerivedTrainingConfigKey() {
        val store = InMemoryMovitLocalStore()
        val training = TrainingConfigRepository(store, MessageLibraryCache(store))
        val catalog = SyncCatalogOfflineRepository(store, training)
        val workoutJson = MovitJson.encodeToJsonElement(
            WorkoutExportDto.serializer(),
            WorkoutExportDto(
                id = "wt-1",
                slug = "full-body",
                name = com.movit.core.network.dto.LocalizedNameDto(en = "Full Body"),
            ),
        )
        catalog.applyFromSync(
            MobileSyncDataDto(workoutTemplates = listOf(workoutJson)),
            isFullSync = true,
        )
        assertNotNull(catalog.readWorkoutExport("wt-1"))
        assertNotNull(catalog.readWorkoutTrainingConfig("wt-1"))
        assertNull(
            store.readJsonCache(
                MovitCacheKeys.SESSION_STORE,
                MovitCacheKeys.workoutTemplateTrainingConfigKey("wt-1"),
            ),
        )
    }
}
