package com.trainingvalidator.poc.ui.utils

import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.LocalizedText

/**
 * Case-insensitive exercise search across name, slug, description, category, muscles, etc.
 * Multi-word queries require every token to match at least one field (AND).
 */
object ExerciseSearchMatcher {

    fun matches(
        exercise: ExerciseConfig,
        rawQuery: String,
        preferredLanguage: String = "en",
    ): Boolean {
        val query = rawQuery.trim()
        if (query.isEmpty()) return true

        val tokens = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return tokens.all { token -> tokenMatches(exercise, token, preferredLanguage) }
    }

    private fun tokenMatches(
        exercise: ExerciseConfig,
        token: String,
        preferredLanguage: String,
    ): Boolean {
        val fields = buildSearchFields(exercise, preferredLanguage)
        if (fields.any { containsIgnoreCase(it, token) }) return true

        val slugFields = buildSlugFields(exercise.fileName)
        if (slugFields.any { containsIgnoreCase(it, token) }) return true

        return slugVariants(token).any { variant ->
            slugFields.any { field -> containsIgnoreCase(field, variant) }
        }
    }

    private fun buildSearchFields(exercise: ExerciseConfig, preferredLanguage: String): List<String> {
        val fields = mutableListOf<String>()

        fun add(value: String?) {
            if (!value.isNullOrBlank()) fields.add(value)
        }

        add(exercise.name.get(preferredLanguage).ifBlank { exercise.name.en })
        addLocalized(exercise.name, fields)
        addLocalized(exercise.description, fields)
        addLocalized(exercise.instructions, fields)
        addLocalized(exercise.category.name, fields)

        add(exercise.fileName)
        add(exercise.category.code)
        add(exercise.countingMethod.name)

        exercise.muscles.forEach { add(it) }
        exercise.equipment.forEach { add(it) }
        exercise.tags.forEach { add(it) }

        return fields.distinct()
    }

    private fun addLocalized(text: LocalizedText?, target: MutableList<String>) {
        if (text == null) return
        listOf(text.en, text.ar).forEach { value ->
            if (value.isNotBlank()) target.add(value)
        }
    }

    private fun buildSlugFields(fileName: String): List<String> {
        if (fileName.isBlank()) return emptyList()
        return slugVariants(fileName).distinct()
    }

    private fun slugVariants(value: String): List<String> {
        return listOf(
            value,
            value.replace(' ', '_'),
            value.replace(' ', '-'),
            value.replace('_', ' '),
            value.replace('-', ' '),
        ).filter { it.isNotBlank() }.distinct()
    }

    private fun containsIgnoreCase(haystack: String, needle: String): Boolean {
        return haystack.contains(needle, ignoreCase = true)
    }
}
