package com.movit.feature.library

import com.movit.core.model.ExploreContent
import com.movit.core.model.ExploreItemUi
import com.movit.shared.AppResult

class FakeLibraryRepository(
    private val content: ExploreContent = LibraryTestFixtures.sampleLibraryContent(LibraryListKind.Exercises),
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

internal fun sampleExerciseItems(): List<ExploreItemUi> = LibraryTestFixtures.exercises

internal fun sampleWorkoutItems(): List<ExploreItemUi> = LibraryTestFixtures.workouts

internal fun sampleLibraryContent(kind: LibraryListKind): ExploreContent =
    LibraryTestFixtures.sampleLibraryContent(kind)
