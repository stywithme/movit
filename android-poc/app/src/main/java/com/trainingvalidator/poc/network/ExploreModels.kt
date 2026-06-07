package com.trainingvalidator.poc.network

/**
 * Explore endpoint response models.
 */
data class ExploreResponse(
    val success: Boolean,
    val timestamp: String,
    val data: ExploreData? = null,
    val meta: ExploreMeta? = null,
    val error: String? = null
)

data class ExploreData(
    val levels: List<ExploreLevelItem> = emptyList(),
    val programs: List<ExploreProgramItem> = emptyList(),
    val workoutTemplates: List<ExploreWorkoutItem> = emptyList(),
    val exercises: List<ExploreExerciseItem> = emptyList(),
    val deletedProgramIds: List<String> = emptyList(),
    val deletedWorkoutTemplateIds: List<String> = emptyList(),
    val deletedExerciseIds: List<String> = emptyList()
)

data class ExploreMeta(
    val isFullSync: Boolean,
    val serverVersion: String,
    val levelsInResponse: Int,
    val programsInResponse: Int,
    val workoutTemplatesInResponse: Int,
    val exercisesInResponse: Int
)

data class ExploreLevelItem(
    val number: Int,
    val code: String,
    val name: LocalizedName,
    val description: LocalizedName? = null,
    val color: String? = null,
    val updatedAt: String? = null
)

data class ExploreProgramItem(
    val id: String,
    val slug: String,
    val name: LocalizedName,
    val levelMinId: String? = null,
    val levelMaxId: String? = null,
    val levelMin: ExploreProgramLevel? = null,
    val levelMax: ExploreProgramLevel? = null,
    val durationWeeks: Int,
    val coverImageUrl: String? = null,
    val updatedAt: String
)

data class ExploreProgramLevel(
    val id: String? = null,
    val number: Int = 0,
    val code: String = "",
    val name: LocalizedName
)

data class ExploreWorkoutItem(
    val id: String,
    val slug: String,
    val name: LocalizedName,
    val levelId: String? = null,
    val level: ExploreWorkoutLevel? = null,
    val estimatedDurationMin: Int? = null,
    val coverImageUrl: String? = null,
    val exerciseCount: Int,
    val updatedAt: String
)

data class ExploreWorkoutLevel(
    val id: String,
    val number: Int = 0,
    val code: String = "",
    val name: LocalizedName
)

data class ExploreExerciseItem(
    val id: String,
    val slug: String,
    val name: LocalizedName,
    val categoryCode: String? = null,
    val categoryName: LocalizedName? = null,
    val musclesCount: Int,
    val updatedAt: String
)
