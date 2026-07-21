package com.movit.core.data.readiness

import com.movit.core.data.cache.MessageLibraryCache
import com.movit.core.data.cache.SystemMessageCache
import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.data.repository.SyncCatalogOfflineRepository
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.data.repository.testMobileApi
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.ExploreExerciseDto
import com.movit.core.network.dto.ExploreProgramDto
import com.movit.core.network.dto.ExploreWorkoutDto
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.SyncMessageContentDto
import com.movit.core.network.dto.SyncMessageTemplateDto
import com.movit.core.network.dto.SyncSystemMessageDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DataReadinessGateTest {

    @Test
    fun emptyCaches_reportsMissingParts() {
        val gate = buildGate(InMemoryMovitLocalStore())
        val result = gate.evaluate()
        val missing = assertIs<DataReadinessResult.Missing>(result)
        assertTrue(DataReadinessPart.ExploreCatalog in missing.parts)
        assertTrue(DataReadinessPart.ExerciseConfigs in missing.parts)
        assertFalse(gate.isCoreReady())
    }

    @Test
    fun exploreWithConfig_isCoreReady_withoutMessagesOrExports() {
        val store = InMemoryMovitLocalStore()
        seedCoreReady(store)
        val gate = buildGate(store)
        assertIs<DataReadinessResult.Ready>(gate.evaluate())
        assertTrue(gate.isCoreReady())
    }

    @Test
    fun catalogExerciseWithoutConfig_reportsMissingConfigs() {
        val store = InMemoryMovitLocalStore()
        seedCoreReady(store, exerciseSlug = "squat", withConfig = false)
        val missing = assertIs<DataReadinessResult.Missing>(buildGate(store).evaluate())
        assertTrue(DataReadinessPart.ExerciseConfigs in missing.parts)
        assertFalse(buildGate(store).isCoreReady())
    }

    @Test
    fun exploreCardWithoutMatchingExport_stillCoreReady() {
        // ponytail regression: lenient explore mapping + strict export decode used to 1:1-fail Splash.
        val store = InMemoryMovitLocalStore()
        seedCoreReady(store)
        store.writeJsonCache(
            MovitCacheKeys.EXPLORE_STORE,
            MovitCacheKeys.EXPLORE_DATA,
            MovitJson.encodeToString(
                ExploreDataDto.serializer(),
                ExploreDataDto(
                    exercises = listOf(ExploreExerciseDto(id = "ex-1", slug = "squat")),
                    workoutTemplates = listOf(
                        ExploreWorkoutDto(id = "wt-listed", slug = "listed"),
                        ExploreWorkoutDto(id = "wt-orphan", slug = "orphan-card"),
                    ),
                    programs = listOf(ExploreProgramDto(id = "prog-1", slug = "p1")),
                ),
            ),
        )
        // No workout/program exports at all — soft gap, must not block CoreReady.
        assertTrue(buildGate(store).isCoreReady())
        assertIs<DataReadinessResult.Ready>(buildGate(store).evaluate())
    }

    @Test
    fun softGaps_reportedOnlyWhenRequested_doNotBlockCoreReady() {
        val store = InMemoryMovitLocalStore()
        seedCoreReady(store, withHome = false, withMessages = false)
        store.writeJsonCache(
            MovitCacheKeys.EXPLORE_STORE,
            MovitCacheKeys.EXPLORE_DATA,
            MovitJson.encodeToString(
                ExploreDataDto.serializer(),
                ExploreDataDto(
                    exercises = listOf(ExploreExerciseDto(id = "ex-1", slug = "squat")),
                    workoutTemplates = listOf(ExploreWorkoutDto(id = "wt-1", slug = "w1")),
                    programs = listOf(ExploreProgramDto(id = "prog-1", slug = "p1")),
                ),
            ),
        )
        val gate = buildGate(store)
        assertTrue(gate.isCoreReady())
        assertIs<DataReadinessResult.Ready>(gate.evaluate(includeSoftGaps = false))

        val soft = assertIs<DataReadinessResult.Missing>(
            gate.evaluate(includeSoftGaps = true),
        )
        assertTrue(DataReadinessPart.CatalogExports in soft.parts)
        assertTrue(DataReadinessPart.MessageLibrary in soft.parts)
        assertTrue(DataReadinessPart.SystemMessages in soft.parts)
        // Home is soft for boot; only appears when requireHome=true.
        assertTrue(DataReadinessPart.HomeDashboard !in soft.parts)
        assertFalse(gate.isCoreReady(requireHome = true))
    }

    @Test
    fun requireHome_blocksCoreReadyWhenHomeMissing() {
        val store = InMemoryMovitLocalStore()
        seedCoreReady(store, withHome = false)
        val gate = buildGate(store)
        assertTrue(gate.isCoreReady(requireHome = false))
        assertFalse(gate.isCoreReady(requireHome = true))
    }

    private fun buildGate(store: InMemoryMovitLocalStore): DataReadinessGate {
        val platform = FakeMovitPlatformBindings()
        val api = testMobileApi(MockEngine { respond("{}", HttpStatusCode.OK) }, platform)
        val explore = ExploreSyncRepository(api, { platform }, { store })
        val home = HomeSyncRepository(api, { platform }, { store })
        val training = TrainingConfigRepository(store)
        val catalog = SyncCatalogOfflineRepository(store, training)
        val messages = MessageLibraryCache(store)
        val system = SystemMessageCache(store)
        return DataReadinessGate(explore, training, catalog, messages, system, home)
    }

    private fun seedCoreReady(
        store: InMemoryMovitLocalStore,
        exerciseSlug: String = "squat",
        withConfig: Boolean = true,
        withHome: Boolean = true,
        withMessages: Boolean = true,
    ) {
        val explore = ExploreDataDto(
            exercises = listOf(ExploreExerciseDto(id = "ex-1", slug = exerciseSlug)),
        )
        store.writeJsonCache(
            MovitCacheKeys.EXPLORE_STORE,
            MovitCacheKeys.EXPLORE_DATA,
            MovitJson.encodeToString(ExploreDataDto.serializer(), explore),
        )
        if (withHome) {
            store.writeJsonCache(
                MovitCacheKeys.HOME_STORE,
                MovitCacheKeys.HOME_DATA,
                MovitJson.encodeToString(HomeDataDto.serializer(), HomeDataDto()),
            )
        }
        if (withMessages) {
            MessageLibraryCache(store).replaceFull(
                listOf(
                    SyncMessageTemplateDto(
                        id = "m1",
                        code = "c1",
                        content = SyncMessageContentDto(en = "x"),
                    ),
                ),
            )
            SystemMessageCache(store).save(
                listOf(SyncSystemMessageDto(code = "s1", content = LocalizedNameDto(en = "y"))),
            )
        }
        if (withConfig) {
            val exerciseJson = buildJsonObject {
                put("slug", exerciseSlug)
                put("configVersion", 1)
            }
            TrainingConfigRepository(store, MessageLibraryCache(store)).applySyncExercises(
                exercises = listOf(exerciseJson),
                isFullSync = true,
            )
        }
    }
}
