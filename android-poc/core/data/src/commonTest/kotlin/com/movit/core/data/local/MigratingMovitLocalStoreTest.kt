package com.movit.core.data.local

import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.ExploreExerciseDto
import com.movit.core.network.dto.UserProgramExportDto
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MigratingMovitLocalStoreTest {

    @Test
    fun migrateKnownCachesFromPlatform_copiesStaticAndDynamicStores() {
        val platform = FakeMovitPlatformBindings()
        val exploreJson = MovitJson.encodeToString(
            ExploreDataDto.serializer(),
            ExploreDataDto(exercises = listOf(ExploreExerciseDto(slug = "squat", imageUrl = "https://img/squat"))),
        )
        platform.writeCache(MovitCacheKeys.EXPLORE_STORE, MovitCacheKeys.EXPLORE_DATA, exploreJson)
        platform.writeCache(MovitCacheKeys.SESSION_STORE, "effective_plan_up-1_1_1", """{"success":true}""")
        platform.writeCache(
            MovitCacheKeys.LEGACY_USER_EXERCISE_PREFERENCES_STORE,
            MovitCacheKeys.exercisePreferenceKey("squat"),
            """{"customReps":12}""",
        )

        val sql = InMemoryMovitLocalStore()
        val store = MigratingMovitLocalStore(sql, platform = { platform })
        store.migrateKnownCachesFromPlatform()

        assertEquals(exploreJson, sql.readJsonCache(MovitCacheKeys.EXPLORE_STORE, MovitCacheKeys.EXPLORE_DATA))
        assertNotNull(sql.readJsonCache(MovitCacheKeys.SESSION_STORE, "effective_plan_up-1_1_1"))
        assertEquals(
            """{"customReps":12}""",
            sql.readJsonCache(
                MovitCacheKeys.PREFERENCES_STORE,
                MovitCacheKeys.exercisePreferenceKey("squat"),
            ),
        )
    }

    @Test
    fun migrateActiveUserProgramId_fromLegacyUserProgramsJson() {
        val platform = FakeMovitPlatformBindings(userProgramId = null)
        val programsJson = MovitJson.encodeToString(
            ListSerializer(UserProgramExportDto.serializer()),
            listOf(
                UserProgramExportDto(id = "up-old", isActive = false),
                UserProgramExportDto(id = "up-active", isActive = true),
            ),
        )
        platform.writeCache(
            MovitCacheKeys.LEGACY_USER_PROGRAM_STORE,
            MovitCacheKeys.LEGACY_USER_PROGRAMS_KEY,
            programsJson,
        )

        val sql = InMemoryMovitLocalStore()
        val store = MigratingMovitLocalStore(sql, platform = { platform })
        store.migrateKnownCachesFromPlatform()

        assertEquals(
            "up-active",
            sql.readJsonCache(MovitCacheKeys.PROGRAM_STORE, MovitCacheKeys.ACTIVE_USER_PROGRAM_ID),
        )
        assertEquals(
            "up-active",
            platform.readCache(MovitCacheKeys.PROGRAM_STORE, MovitCacheKeys.ACTIVE_USER_PROGRAM_ID),
        )
    }
}
