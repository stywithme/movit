package com.movit.feature.library

import com.movit.feature.explore.ExploreContent
import com.movit.feature.explore.ExploreItemType
import com.movit.feature.explore.ExploreItemUi
import com.movit.feature.explore.MovitExplorePreviewData
import com.movit.shared.AppResult

class FakeLibraryRepository(
    private val content: ExploreContent = MovitExplorePreviewData.content,
    private val shouldFail: Boolean = false,
) : LibraryRepository {

    override suspend fun loadContent(): AppResult<ExploreContent> {
        if (shouldFail) {
            return AppResult.Failure("Unable to load library content.")
        }
        return AppResult.Success(content)
    }

    override suspend fun findItem(id: String): ExploreItemUi? {
        return (content.workouts + content.exercises + content.programs + content.featured)
            .firstOrNull { it.id == id }
    }
}

internal fun sampleExerciseItems(): List<ExploreItemUi> =
    MovitExplorePreviewData.exerciseOnly

internal fun sampleWorkoutItems(): List<ExploreItemUi> =
    MovitExplorePreviewData.workouts

internal fun sampleLibraryContent(kind: LibraryListKind): ExploreContent =
    when (kind) {
        LibraryListKind.Exercises -> ExploreContent(
            featured = emptyList(),
            workouts = emptyList(),
            exercises = sampleExerciseItems(),
            programs = emptyList(),
        )
        LibraryListKind.Workouts -> ExploreContent(
            featured = emptyList(),
            workouts = sampleWorkoutItems(),
            exercises = emptyList(),
            programs = emptyList(),
        )
    }
