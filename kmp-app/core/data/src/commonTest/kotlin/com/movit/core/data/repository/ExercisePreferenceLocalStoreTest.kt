package com.movit.core.data.repository

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.UserExercisePreferenceSyncDto
import com.movit.core.network.dto.UserExercisePreferenceUpsertRequest
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class ExercisePreferenceLocalStoreTest {

    @Test
    fun get_resolvesSlugAliasToCanonicalExerciseId() {
        val localStore = InMemoryMovitLocalStore()
        seedSlugAlias(localStore, exerciseId = "ex-squat", slug = "bodyweight-squat")
        localStore.writeJsonCache(
            MovitCacheKeys.PREFERENCES_STORE,
            MovitCacheKeys.exercisePreferenceKey("ex-squat"),
            MovitJson.encodeToString(
                UserExercisePreferenceUpsertRequest.serializer(),
                UserExercisePreferenceUpsertRequest(customReps = 8),
            ),
        )
        val store = ExercisePreferenceLocalStore(localStore)

        assertEquals(8, store.get("bodyweight-squat")?.customReps)
        assertEquals(8, store.get("ex-squat")?.customReps)
    }

    @Test
    fun upsert_writesCanonicalExerciseIdAndDropsLegacySlugKey() {
        val localStore = InMemoryMovitLocalStore()
        seedSlugAlias(localStore, exerciseId = "ex-squat", slug = "bodyweight-squat")
        localStore.writeJsonCache(
            MovitCacheKeys.PREFERENCES_STORE,
            MovitCacheKeys.exercisePreferenceKey("bodyweight-squat"),
            MovitJson.encodeToString(
                UserExercisePreferenceUpsertRequest.serializer(),
                UserExercisePreferenceUpsertRequest(customReps = 5),
            ),
        )
        val store = ExercisePreferenceLocalStore(localStore)

        store.upsert(
            "bodyweight-squat",
            UserExercisePreferenceUpsertRequest(customReps = 12),
        )

        assertEquals(12, store.get("ex-squat")?.customReps)
        assertNull(
            localStore.readJsonCache(
                MovitCacheKeys.PREFERENCES_STORE,
                MovitCacheKeys.exercisePreferenceKey("bodyweight-squat"),
            ),
        )
    }

    @Test
    fun hydrateFromSync_storesByExerciseIdNotSlug() = runBlocking {
        val localStore = InMemoryMovitLocalStore()
        seedSlugAlias(localStore, exerciseId = "ex-1", slug = "squat")
        val store = ExercisePreferenceLocalStore(localStore)

        store.hydrateFromSync(
            listOf(
                UserExercisePreferenceSyncDto(
                    exerciseId = "ex-1",
                    exerciseSlug = "squat",
                    customReps = 15,
                ),
            ),
        )

        assertEquals(15, store.get("squat")?.customReps)
        assertEquals(
            15,
            MovitJson.decodeFromString(
                UserExercisePreferenceUpsertRequest.serializer(),
                localStore.readJsonCache(
                    MovitCacheKeys.PREFERENCES_STORE,
                    MovitCacheKeys.exercisePreferenceKey("ex-1"),
                )!!,
            ).customReps,
        )
    }

    private fun seedSlugAlias(
        localStore: InMemoryMovitLocalStore,
        exerciseId: String,
        slug: String,
    ) {
        localStore.writeJsonCache(
            MovitCacheKeys.EXERCISE_CONFIG_STORE,
            MovitCacheKeys.EXERCISE_CONFIG_SLUG_ALIASES,
            MovitJson.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                mapOf(exerciseId to slug),
            ),
        )
    }
}
