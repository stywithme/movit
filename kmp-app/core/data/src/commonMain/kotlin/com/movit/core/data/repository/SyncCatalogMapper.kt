package com.movit.core.data.repository

import com.movit.core.network.MovitJson
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.ExploreExerciseDto
import com.movit.core.network.dto.ExploreLevelDto
import com.movit.core.network.dto.ExploreProgramDto
import com.movit.core.network.dto.ExploreProgramLevelDto
import com.movit.core.network.dto.ExploreWorkoutDto
import com.movit.core.network.dto.ExploreWorkoutLevelDto
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.MobileSyncDataDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Maps authoritative `/api/mobile/sync` catalog payloads into [ExploreDataDto] card shape.
 * Replaces the redundant `/api/mobile/explore` catalog fetch.
 */
internal object SyncCatalogMapper {
    fun mapSyncPayloadToExploreSlice(payload: MobileSyncDataDto): ExploreDataDto {
        val workouts = payload.workoutTemplates
            .mapNotNull(::mapWorkoutTemplate)
            .sortedWith(catalogOrder { it.dto.updatedAt to it.featured })
            .map { it.dto }
        val programs = payload.programs
            .mapNotNull(::mapProgram)
            .sortedWith(catalogOrder { it.dto.updatedAt to it.featured })
            .map { it.dto }
        val exercises = payload.exercises
            .mapNotNull(::mapExercise)
            .sortedWith(catalogOrder { it.dto.updatedAt to it.featured })
            .map { it.dto }

        return ExploreDataDto(
            levels = deriveLevels(workouts, programs),
            programs = programs,
            workoutTemplates = workouts,
            exercises = exercises,
            deletedProgramIds = payload.deletedProgramIds,
            deletedWorkoutTemplateIds = payload.deletedWorkoutTemplateIds,
            deletedExerciseIds = payload.deletedExerciseIds,
        )
    }

    private data class MappedItem<T>(
        val dto: T,
        val featured: Boolean,
    )

    private fun <T> catalogOrder(selector: (MappedItem<T>) -> Pair<String, Boolean>): Comparator<MappedItem<T>> =
        compareByDescending<MappedItem<T>> { selector(it).second }
            .thenByDescending { selector(it).first }

    private fun deriveLevels(
        workouts: List<ExploreWorkoutDto>,
        programs: List<ExploreProgramDto>,
    ): List<ExploreLevelDto> {
        val byNumber = linkedMapOf<Int, ExploreLevelDto>()
        workouts.mapNotNull { it.level }.forEach { level ->
            byNumber.getOrPut(level.number) { level.toExploreLevel() }
        }
        programs.forEach { program ->
            program.levelMin?.let { byNumber.getOrPut(it.number) { it.toExploreLevel() } }
            program.levelMax?.let { byNumber.getOrPut(it.number) { it.toExploreLevel() } }
        }
        return byNumber.values.sortedBy { it.number }
    }

    private fun mapWorkoutTemplate(element: JsonElement): MappedItem<ExploreWorkoutDto>? {
        val obj = element.jsonObject
        val id = obj.stringOrEmpty("id")
        if (id.isBlank()) return null
        val featured = obj["isFeatured"]?.jsonPrimitive?.booleanOrNull == true
        val exerciseCount = obj["exercises"]?.jsonArray?.size ?: 0
        return MappedItem(
            dto = ExploreWorkoutDto(
                id = id,
                slug = obj.stringOrEmpty("slug"),
                name = obj.localizedName("name"),
                level = obj["level"]?.jsonObject?.let(::mapWorkoutLevel),
                estimatedDurationMin = obj["estimatedDurationMin"]?.jsonPrimitive?.intOrNull,
                coverImageUrl = obj.stringOrNull("coverImageUrl"),
                exerciseCount = exerciseCount,
                updatedAt = obj.stringOrEmpty("updatedAt"),
            ),
            featured = featured,
        )
    }

    private fun mapProgram(element: JsonElement): MappedItem<ExploreProgramDto>? {
        val obj = element.jsonObject
        val id = obj.stringOrEmpty("id")
        if (id.isBlank()) return null
        return MappedItem(
            dto = ExploreProgramDto(
                id = id,
                slug = obj.stringOrEmpty("slug"),
                name = obj.localizedName("name"),
                levelMin = obj["levelMin"]?.jsonObject?.let(::mapProgramLevel),
                levelMax = obj["levelMax"]?.jsonObject?.let(::mapProgramLevel),
                durationWeeks = obj["durationWeeks"]?.jsonPrimitive?.intOrNull ?: 0,
                coverImageUrl = obj.stringOrNull("coverImageUrl"),
                updatedAt = obj.stringOrEmpty("updatedAt"),
            ),
            featured = obj["isFeatured"]?.jsonPrimitive?.booleanOrNull == true,
        )
    }

    private fun mapExercise(element: JsonElement): MappedItem<ExploreExerciseDto>? {
        val obj = element.jsonObject
        val id = obj.stringOrEmpty("id")
        val slug = obj.stringOrEmpty("slug")
        if (id.isBlank() && slug.isBlank()) return null
        val category = obj["category"]?.jsonObject
        return MappedItem(
            dto = ExploreExerciseDto(
                id = id,
                slug = slug,
                name = obj.localizedName("name"),
                categoryCode = category?.stringOrNull("code"),
                categoryName = category?.localizedName("name"),
                musclesCount = obj["muscles"]?.jsonArray?.size ?: 0,
                imageUrl = obj.stringOrNull("imageUrl"),
                updatedAt = obj.stringOrEmpty("updatedAt"),
            ),
            featured = obj["isFeatured"]?.jsonPrimitive?.booleanOrNull == true,
        )
    }

    private fun mapWorkoutLevel(obj: JsonObject): ExploreWorkoutLevelDto =
        ExploreWorkoutLevelDto(
            number = obj["number"]?.jsonPrimitive?.intOrNull ?: 0,
            code = obj.stringOrEmpty("code"),
            name = obj.localizedName("name"),
        )

    private fun mapProgramLevel(obj: JsonObject): ExploreProgramLevelDto =
        ExploreProgramLevelDto(
            number = obj["number"]?.jsonPrimitive?.intOrNull ?: 0,
            code = obj.stringOrEmpty("code"),
            name = obj.localizedName("name"),
        )

    private fun ExploreWorkoutLevelDto.toExploreLevel(): ExploreLevelDto =
        ExploreLevelDto(number = number, code = code, name = name)

    private fun ExploreProgramLevelDto.toExploreLevel(): ExploreLevelDto =
        ExploreLevelDto(number = number, code = code, name = name)

    private fun JsonObject.stringOrEmpty(key: String): String =
        get(key)?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.localizedName(key: String): LocalizedNameDto =
        get(key)?.let { runCatching { MovitJson.decodeFromJsonElement(LocalizedNameDto.serializer(), it) }.getOrNull() }
            ?: LocalizedNameDto()
}
