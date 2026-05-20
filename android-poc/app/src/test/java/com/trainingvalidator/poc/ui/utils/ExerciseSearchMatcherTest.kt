package com.trainingvalidator.poc.ui.utils

import com.trainingvalidator.poc.training.models.CategoryInfo
import com.trainingvalidator.poc.training.models.CountingMethod
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.LocalizedText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSearchMatcherTest {

    private fun sampleExercise(
        slug: String = "ex026_plank",
        nameEn: String = "Plank",
        nameAr: String = "بلانك",
    ): ExerciseConfig {
        val exercise = ExerciseConfig(
            name = LocalizedText(ar = nameAr, en = nameEn),
            description = LocalizedText(ar = "وصف", en = "Core hold"),
            instructions = LocalizedText(ar = "تعليمات", en = "Keep body straight"),
            category = CategoryInfo(
                code = "core",
                name = LocalizedText(ar = "بطن", en = "Core"),
            ),
            countingMethod = CountingMethod.HOLD,
            muscles = listOf("abs", "core"),
            equipment = listOf("mat"),
            tags = listOf("hold_tag"),
        )
        exercise.fileName = slug
        return exercise
    }

    @Test
    fun emptyQueryMatchesEverything() {
        assertTrue(ExerciseSearchMatcher.matches(sampleExercise(), ""))
        assertTrue(ExerciseSearchMatcher.matches(sampleExercise(), "   "))
    }

    @Test
    fun matchesNameCaseInsensitive() {
        assertTrue(ExerciseSearchMatcher.matches(sampleExercise(), "PLANK"))
        assertTrue(ExerciseSearchMatcher.matches(sampleExercise(), "بلانك"))
    }

    @Test
    fun matchesSlugWithUnderscoreOrSpaces() {
        val exercise = sampleExercise(slug = "ex026_plank")
        assertTrue(ExerciseSearchMatcher.matches(exercise, "ex026_plank"))
        assertTrue(ExerciseSearchMatcher.matches(exercise, "ex026 plank"))
    }

    @Test
    fun matchesCategoryMusclesAndTags() {
        assertTrue(ExerciseSearchMatcher.matches(sampleExercise(), "core"))
        assertTrue(ExerciseSearchMatcher.matches(sampleExercise(), "abs"))
        assertTrue(ExerciseSearchMatcher.matches(sampleExercise(), "hold"))
    }

    @Test
    fun multiWordRequiresAllTokens() {
        val exercise = sampleExercise()
        assertTrue(ExerciseSearchMatcher.matches(exercise, "core plank"))
        assertFalse(ExerciseSearchMatcher.matches(exercise, "bench press"))
    }
}
