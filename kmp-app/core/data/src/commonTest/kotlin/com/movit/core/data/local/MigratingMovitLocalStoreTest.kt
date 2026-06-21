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
    }

    @Test
    fun migrateKnownCachesFromPlatform_canonicalizesLegacySlugPreferenceKeys() {
        val platform = FakeMovitPlatformBindings()
        platform.writeCache(
            MovitCacheKeys.LEGACY_USER_EXERCISE_PREFERENCES_STORE,
            MovitCacheKeys.exercisePreferenceKey("bodyweight-squat"),
            """{"customReps":10}""",
        )
        platform.writeCache(
            MovitCacheKeys.LEGACY_DAY_CUSTOMIZATION_STORE,
            MovitCacheKeys.dayCustomizationKey("prog-1", 2, 1),
            """{"userProgramId":"prog-1","weekNumber":2,"dayNumber":1,"plannedWorkouts":[]}""",
        )
        platform.writeCache(
            MovitCacheKeys.LEGACY_USER_PROGRAM_STORE,
            MovitCacheKeys.LEGACY_USER_PROGRAMS_KEY,
            MovitJson.encodeToString(
                ListSerializer(UserProgramExportDto.serializer()),
                listOf(UserProgramExportDto(id = "up-1", programId = "prog-1", isActive = true)),
            ),
        )

        val sql = InMemoryMovitLocalStore()
        sql.writeJsonCache(
            MovitCacheKeys.EXERCISE_CONFIG_STORE,
            MovitCacheKeys.EXERCISE_CONFIG_SLUG_ALIASES,
            """{"ex-squat":"bodyweight-squat"}""",
        )
        val store = MigratingMovitLocalStore(sql, platform = { platform })
        store.migrateKnownCachesFromPlatform()

        assertNotNull(
            sql.readJsonCache(
                MovitCacheKeys.DAY_CUSTOMIZATION_STORE,
                MovitCacheKeys.dayCustomizationKey("up-1", 2, 1),
            ),
        )
        assertEquals(
            """{"customReps":10}""",
            sql.readJsonCache(
                MovitCacheKeys.PREFERENCES_STORE,
                MovitCacheKeys.exercisePreferenceKey("ex-squat"),
            ),
        )
    }
}
