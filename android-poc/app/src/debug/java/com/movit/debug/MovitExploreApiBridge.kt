package com.movit.debug

import android.content.Context
import com.movit.feature.explore.ExploreContent
import com.movit.feature.explore.ExploreItemType
import com.movit.feature.explore.ExploreItemUi
import com.movit.feature.explore.remote.ExploreContentFetcher
import com.movit.feature.explore.remote.ExploreContentFetcherBridge
import com.movit.shared.AppResult
import com.trainingvalidator.poc.network.ExploreData
import com.trainingvalidator.poc.network.ExploreExerciseItem
import com.trainingvalidator.poc.network.ExploreProgramItem
import com.trainingvalidator.poc.network.ExploreWorkoutItem
import com.trainingvalidator.poc.network.LocalizedName
import com.trainingvalidator.poc.storage.ExploreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wires Movit Explore KMP to the existing Retrofit-backed explore cache.
 * Installed once from debug pilot hosts before Compose content is set.
 */
object MovitExploreApiBridge {

    fun install(context: Context) {
        val appContext = context.applicationContext
        ExploreContentFetcherBridge.fetcher = ExploreContentFetcher {
            fetchFromLegacyRepository(appContext)
        }
    }

    private suspend fun fetchFromLegacyRepository(context: Context): AppResult<ExploreContent> {
        return withContext(Dispatchers.IO) {
            try {
                val repository = ExploreRepository.getInstance(context)
                val data = repository.syncFromServer(limit = 50) ?: repository.getCachedData()
                if (data == null) {
                    AppResult.Failure("No explore content available. Check your connection.")
                } else {
                    AppResult.Success(mapExploreData(data))
                }
            } catch (error: Exception) {
                AppResult.Failure(
                    message = error.message ?: "Explore sync failed.",
                    cause = error,
                )
            }
        }
    }
}

internal fun mapExploreData(data: ExploreData): ExploreContent {
    val workouts = data.workoutTemplates.map { it.toExploreItemUi() }
    val exercises = data.exercises.map { it.toExploreItemUi() }
    val programs = data.programs.map { it.toExploreItemUi() }
    val allItems = workouts + exercises + programs
    val featured = workouts.take(2).ifEmpty { allItems.take(1) }
    return ExploreContent(
        featured = featured,
        exercises = allItems,
    )
}

private fun ExploreWorkoutItem.toExploreItemUi(): ExploreItemUi {
    val duration = estimatedDurationMin?.let { "~$it min" }
    val metadata = buildList {
        add("${exerciseCount} exercises")
        duration?.let { add(it) }
        level?.name?.let { add(it.displayName()) }
    }
    return ExploreItemUi(
        id = slug,
        title = name.displayName(),
        subtitle = "Workout template",
        type = ExploreItemType.Workout,
        imageUrl = coverImageUrl,
        metadata = metadata,
    )
}

private fun ExploreExerciseItem.toExploreItemUi(): ExploreItemUi {
    val metadata = buildList {
        categoryName?.displayName()?.let { add(it) }
        if (musclesCount > 0) add("$musclesCount muscles")
    }
    return ExploreItemUi(
        id = slug,
        title = name.displayName(),
        subtitle = categoryCode ?: "Exercise",
        type = ExploreItemType.Exercise,
        metadata = metadata,
    )
}

private fun ExploreProgramItem.toExploreItemUi(): ExploreItemUi {
    val metadata = buildList {
        add("${durationWeeks} weeks")
        levelMin?.name?.displayName()?.let { add(it) }
    }
    return ExploreItemUi(
        id = slug,
        title = name.displayName(),
        subtitle = "Structured program",
        type = ExploreItemType.Program,
        imageUrl = coverImageUrl,
        metadata = metadata,
    )
}

private fun LocalizedName.displayName(): String {
    return en.ifBlank { ar }.ifBlank { "" }
}
