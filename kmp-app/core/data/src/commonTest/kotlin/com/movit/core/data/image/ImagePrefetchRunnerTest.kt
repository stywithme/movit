package com.movit.core.data.image

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.SyncCatalogOfflineRepository
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.data.repository.testMobileApi
import com.movit.core.network.dto.MobileSyncDataDto
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.ExerciseConfigRecord
import com.movit.core.training.config.PoseVariant
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImagePrefetchRunnerTest {

    @Test
    fun manifest_collectsExerciseThumbnailsPoseFramesAndCovers() {
        val fixture = fixture()
        val manifest = fixture.runner.buildManifest()

        assertEquals(
            setOf(
                "https://cdn.test/ex/squat.png",
                "https://cdn.test/pose/squat-side.png",
                "https://cdn.test/wt/legs.png",
                "https://cdn.test/prog/strength.png",
            ),
            manifest.urls.toSet(),
        )
    }

    @Test
    fun manifest_downloadsPoseFramesBeforeCatalogCovers() {
        val fixture = fixture()
        val ordered = fixture.runner.buildManifest().sortedByPriority().map { it.kind }

        assertEquals(ImageAssetKind.PosePosition, ordered.first())
        assertTrue(ordered.indexOf(ImageAssetKind.ExerciseThumbnail) < ordered.indexOf(ImageAssetKind.ProgramCover))
    }

    @Test
    fun prefetch_downloadsEveryMissingAssetAndReportsFullCoverage() = runBlocking {
        val fixture = fixture()

        val report = fixture.runner.prefetchCatalog()

        assertEquals(4, report.requested)
        assertEquals(4, report.downloaded)
        assertTrue(report.coverage.isComplete)
        assertEquals(100, report.coverage.percent)
    }

    @Test
    fun prefetch_skipsAlreadyCachedAssets() = runBlocking {
        val fixture = fixture()
        fixture.downloader.seed("https://cdn.test/ex/squat.png")

        val report = fixture.runner.prefetchCatalog()

        assertEquals(3, report.requested)
        assertTrue(fixture.downloader.downloadCalls.flatten().none { it.url == "https://cdn.test/ex/squat.png" })
    }

    @Test
    fun prefetch_doesNotHitTheNetworkWhenOffline() = runBlocking {
        val fixture = fixture(networkAvailable = false)

        val report = fixture.runner.prefetchCatalog()

        assertEquals(0, report.downloaded)
        assertTrue(fixture.downloader.downloadCalls.isEmpty())
        assertEquals(0, report.coverage.cached)
    }

    @Test
    fun prefetch_removesOrphansOnlyOnFullSync() = runBlocking {
        val fixture = fixture()
        fixture.downloader.seed("https://cdn.test/removed/old.png")

        fixture.runner.prefetchCatalog(removeOrphans = false)
        assertTrue(fixture.downloader.hasImage(imageFilenameFor("https://cdn.test/removed/old.png")))

        fixture.runner.prefetchCatalog(removeOrphans = true)
        assertTrue(!fixture.downloader.hasImage(imageFilenameFor("https://cdn.test/removed/old.png")))
    }

    @Test
    fun localPath_isNullUntilDownloadedThenResolvesToTheCachedFile() = runBlocking {
        val fixture = fixture()
        val url = "https://cdn.test/pose/squat-side.png"

        assertNull(fixture.runner.localPathFor(url))

        fixture.runner.prefetchCatalog()

        assertEquals("/fake/images/${imageFilenameFor(url)}", fixture.runner.localPathFor(url))
    }

    @Test
    fun weekManifest_isScopedToTheGivenExercises() {
        val fixture = fixture()

        val manifest = fixture.runner.manifestFor(
            exerciseSlugs = listOf("bodyweight-squat"),
            extraUrls = listOf("https://cdn.test/prog/strength.png"),
        )

        assertEquals(
            setOf(
                "https://cdn.test/pose/squat-side.png",
                "https://cdn.test/ex/squat.png",
                "https://cdn.test/prog/strength.png",
            ),
            manifest.urls.toSet(),
        )
    }

    private class Fixture(
        val runner: ImagePrefetchRunner,
        val downloader: FakeImageFileDownloader,
    )

    private fun fixture(networkAvailable: Boolean = true): Fixture {
        val store = InMemoryMovitLocalStore()
        val platform = OfflineAwarePlatform(networkAvailable)
        val trainingConfig = TrainingConfigRepository(store)
        val catalog = SyncCatalogOfflineRepository(store, trainingConfig)
        val explore = ExploreSyncRepository(
            api = testMobileApi(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }),
            platform = { platform },
            localStore = { store },
        )

        trainingConfig.seedRecord(
            ExerciseConfigRecord(
                id = "ex-1",
                slug = "bodyweight-squat",
                updatedAt = "2026-06-11",
                config = ExerciseConfig(
                    imageUrl = "https://cdn.test/ex/squat.png",
                    poseVariants = listOf(
                        PoseVariant(positionImageUrl = "https://cdn.test/pose/squat-side.png"),
                    ),
                ),
            ),
        )

        val payload = MobileSyncDataDto(
            programs = listOf(
                buildJsonObject {
                    put("id", "prog-1")
                    put("slug", "strength")
                    put("coverImageUrl", "https://cdn.test/prog/strength.png")
                    putJsonObject("name") { put("en", "Strength") }
                },
            ),
            workoutTemplates = listOf(
                buildJsonObject {
                    put("id", "wt-1")
                    put("slug", "legs")
                    put("coverImageUrl", "https://cdn.test/wt/legs.png")
                    putJsonObject("name") { put("en", "Legs") }
                },
            ),
        )
        catalog.applyFromSync(payload, isFullSync = true)
        explore.applyFromSync(payload, isFullSync = true)

        val downloader = FakeImageFileDownloader()
        return Fixture(
            runner = ImagePrefetchRunner(
                manifestBuilder = ImageAssetManifestBuilder(explore, trainingConfig, catalog),
                downloader = downloader,
                platform = { platform },
                warmLoader = {},
            ),
            downloader = downloader,
        )
    }

    private class OfflineAwarePlatform(
        private val networkAvailable: Boolean,
    ) : MovitPlatformBindings {
        private val cache = mutableMapOf<String, String>()
        override fun apiBaseUrl(): String = "https://api.test"
        override fun authHeader(): String = "Bearer test"
        override fun preferredLanguage(): String = "en"
        override fun userDisplayName(fallback: String): String = fallback
        override fun readCache(store: String, key: String): String? = cache["$store::$key"]
        override fun writeCache(store: String, key: String, value: String) {
            cache["$store::$key"] = value
        }
        override fun removeCache(store: String, key: String) {
            cache.remove("$store::$key")
        }
        override fun isNetworkAvailable(): Boolean = networkAvailable
    }
}
