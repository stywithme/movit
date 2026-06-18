package com.movit.core.data.local

import com.movit.core.data.outbox.DayCustomizationCacheDto
import com.movit.core.data.repository.DayCustomizationKeyResolver
import com.movit.core.data.repository.ExerciseIdResolver
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.data.repository.UserProgramEnrollmentLocalStore
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.UserProgramExportDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CanonicalCacheKeyMigratorTest {

    @Test
    fun migrateIfNeeded_rewritesDayCustomizationProgramIdToUserProgramId() {
        val platform = FakeMovitPlatformBindings()
        val sql = InMemoryMovitLocalStore()
        val legacyPayload = MovitJson.encodeToString(
            DayCustomizationCacheDto.serializer(),
            DayCustomizationCacheDto(
                userProgramId = "prog-1",
                weekNumber = 1,
                dayNumber = 2,
                plannedWorkouts = emptyList(),
            ),
        )
        platform.writeCache(
            MovitCacheKeys.LEGACY_DAY_CUSTOMIZATION_STORE,
            MovitCacheKeys.dayCustomizationKey("prog-1", 1, 2),
            legacyPayload,
        )
        platform.writeCache(
            MovitCacheKeys.LEGACY_USER_PROGRAM_STORE,
            MovitCacheKeys.LEGACY_USER_PROGRAMS_KEY,
            MovitJson.encodeToString(
                ListSerializer(UserProgramExportDto.serializer()),
                listOf(
                    UserProgramExportDto(id = "up-1", programId = "prog-1", isActive = true),
                ),
            ),
        )

        CanonicalCacheKeyMigrator(sql, platform = { platform }).migrateIfNeeded()

        val migrated = sql.readJsonCache(
            MovitCacheKeys.DAY_CUSTOMIZATION_STORE,
            MovitCacheKeys.dayCustomizationKey("up-1", 1, 2),
        )
        assertNotNull(migrated)
        val dto = MovitJson.decodeFromString(DayCustomizationCacheDto.serializer(), migrated)
        assertEquals("up-1", dto.userProgramId)
        assertNull(
            sql.readJsonCache(
                MovitCacheKeys.DAY_CUSTOMIZATION_STORE,
                MovitCacheKeys.dayCustomizationKey("prog-1", 1, 2),
            ),
        )
        assertEquals(
            "1",
            sql.readString(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.CANONICAL_CACHE_KEYS_MIGRATED),
        )
    }

    @Test
    fun migrateIfNeeded_rewritesExercisePreferenceSlugToExerciseId() {
        val platform = FakeMovitPlatformBindings()
        val sql = InMemoryMovitLocalStore()
        sql.writeJsonCache(
            MovitCacheKeys.EXERCISE_CONFIG_STORE,
            MovitCacheKeys.EXERCISE_CONFIG_SLUG_ALIASES,
            MovitJson.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                mapOf("ex-squat" to "bodyweight-squat"),
            ),
        )
        platform.writeCache(
            MovitCacheKeys.LEGACY_USER_EXERCISE_PREFERENCES_STORE,
            MovitCacheKeys.exercisePreferenceKey("bodyweight-squat"),
            """{"customReps":10}""",
        )

        CanonicalCacheKeyMigrator(sql, platform = { platform }).migrateIfNeeded()

        assertEquals(
            """{"customReps":10}""",
            sql.readJsonCache(
                MovitCacheKeys.PREFERENCES_STORE,
                MovitCacheKeys.exercisePreferenceKey("ex-squat"),
            ),
        )
        assertNull(
            sql.readJsonCache(
                MovitCacheKeys.PREFERENCES_STORE,
                MovitCacheKeys.exercisePreferenceKey("bodyweight-squat"),
            ),
        )
    }

    @Test
    fun migrateIfNeeded_runsOnlyOnce() {
        val platform = FakeMovitPlatformBindings()
        val sql = InMemoryMovitLocalStore()
        val migrator = CanonicalCacheKeyMigrator(sql, platform = { platform })
        migrator.migrateIfNeeded()
        sql.writeJsonCache(
            MovitCacheKeys.PREFERENCES_STORE,
            MovitCacheKeys.exercisePreferenceKey("slug-only"),
            """{"customReps":1}""",
        )
        migrator.migrateIfNeeded()
        assertNotNull(
            sql.readJsonCache(
                MovitCacheKeys.PREFERENCES_STORE,
                MovitCacheKeys.exercisePreferenceKey("slug-only"),
            ),
        )
    }
}

class ExerciseIdResolverTest {

    @Test
    fun resolveCanonicalExerciseId_mapsSlugAliasToServerId() {
        val store = InMemoryMovitLocalStore()
        store.writeJsonCache(
            MovitCacheKeys.EXERCISE_CONFIG_STORE,
            MovitCacheKeys.EXERCISE_CONFIG_SLUG_ALIASES,
            MovitJson.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                mapOf("ex-001" to "bodyweight-squat"),
            ),
        )
        val resolver = ExerciseIdResolver(store)

        assertEquals("ex-001", resolver.resolveCanonicalExerciseId("bodyweight-squat"))
        assertEquals("ex-001", resolver.resolveCanonicalExerciseId("ex-001"))
    }
}

class DayCustomizationKeyResolverTest {

    @Test
    fun parseDayCustomizationKey_supportsCompositeIds() {
        val parts = DayCustomizationKeyResolver.parseDayCustomizationKey("day_up_42_1_3")
        assertNotNull(parts)
        assertEquals("up_42", parts.enrollmentOrProgramId)
        assertEquals(1, parts.weekNumber)
        assertEquals(3, parts.dayNumber)
    }

    @Test
    fun resolveCanonicalUserProgramId_mapsProgramIdUsingEnrollmentStore() {
        val store = InMemoryMovitLocalStore()
        val enrollments = UserProgramEnrollmentLocalStore(store)
        enrollments.hydrateFromSync(
            listOf(UserProgramExportDto(id = "up-9", programId = "prog-9", isActive = true)),
            isFullSync = true,
        )
        val resolver = DayCustomizationKeyResolver(enrollments)

        assertEquals("up-9", resolver.resolveCanonicalUserProgramId("prog-9"))
        assertEquals("up-9", resolver.resolveCanonicalUserProgramId("up-9"))
    }
}
