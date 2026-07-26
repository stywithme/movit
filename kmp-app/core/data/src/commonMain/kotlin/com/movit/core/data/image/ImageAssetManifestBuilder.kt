package com.movit.core.data.image

import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.SyncCatalogOfflineRepository
import com.movit.core.data.repository.TrainingConfigRepository

/**
 * Collects every catalog image URL the app can render from data already on disk.
 *
 * Sources — all already persisted by `/api/mobile/sync`, so the manifest is buildable offline:
 * - exercise training configs → `imageUrl` + each pose variant's `positionImageUrl`
 * - explore cards → exercise thumbnails, workout and program covers
 * - catalog exports → workout template and program covers (kept when explore is trimmed)
 */
class ImageAssetManifestBuilder(
    private val exploreSync: ExploreSyncRepository,
    private val trainingConfig: TrainingConfigRepository,
    private val catalogOffline: SyncCatalogOfflineRepository,
) {
    fun build(): ImageAssetManifest {
        val byUrl = linkedMapOf<String, ImageAsset>()

        // Training-critical first so priority sorting is stable for equal kinds.
        trainingConfig.allCachedSlugs().forEach { slug ->
            val config = trainingConfig.getExercise(slug) ?: return@forEach
            config.poseVariants.forEach { variant ->
                byUrl.addAsset(variant.positionImageUrl, ImageAssetKind.PosePosition)
            }
            byUrl.addAsset(config.imageUrl, ImageAssetKind.ExerciseThumbnail)
        }

        exploreSync.readCached()?.let { explore ->
            explore.exercises.forEach { byUrl.addAsset(it.imageUrl, ImageAssetKind.ExerciseThumbnail) }
            explore.workoutTemplates.forEach { byUrl.addAsset(it.coverImageUrl, ImageAssetKind.WorkoutCover) }
            explore.programs.forEach { byUrl.addAsset(it.coverImageUrl, ImageAssetKind.ProgramCover) }
        }

        catalogOffline.allWorkoutTemplateIds().forEach { id ->
            byUrl.addAsset(catalogOffline.readWorkoutExport(id)?.coverImageUrl, ImageAssetKind.WorkoutCover)
        }
        catalogOffline.allProgramIds().forEach { id ->
            byUrl.addAsset(catalogOffline.readProgram(id)?.coverImageUrl, ImageAssetKind.ProgramCover)
        }

        return ImageAssetManifest(byUrl.values.toList())
    }

    /** Images needed for one program's week — used by the «حزمة الأسبوع» prefetch. */
    fun buildForExercises(slugs: Collection<String>, extraUrls: Collection<String> = emptyList()): ImageAssetManifest {
        val byUrl = linkedMapOf<String, ImageAsset>()
        slugs.forEach { slug ->
            val config = trainingConfig.getExercise(slug) ?: return@forEach
            config.poseVariants.forEach { variant ->
                byUrl.addAsset(variant.positionImageUrl, ImageAssetKind.PosePosition)
            }
            byUrl.addAsset(config.imageUrl, ImageAssetKind.ExerciseThumbnail)
        }
        exploreSync.readCached()?.exercises
            ?.filter { it.slug in slugs }
            ?.forEach { byUrl.addAsset(it.imageUrl, ImageAssetKind.ExerciseThumbnail) }
        extraUrls.forEach { byUrl.addAsset(it, ImageAssetKind.WorkoutCover) }
        return ImageAssetManifest(byUrl.values.toList())
    }

    private fun MutableMap<String, ImageAsset>.addAsset(url: String?, kind: ImageAssetKind) {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return
        // First writer wins: an URL first seen as PosePosition keeps the higher priority.
        if (containsKey(trimmed)) return
        put(trimmed, ImageAsset(url = trimmed, kind = kind))
    }
}
