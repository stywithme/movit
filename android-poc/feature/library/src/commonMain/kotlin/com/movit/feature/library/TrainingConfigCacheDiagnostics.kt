package com.movit.feature.library

import com.movit.core.data.MovitData

data class TrainingConfigCacheSnapshot(
    val exerciseName: String,
    val slugCandidates: List<String>,
    val resolvedSlug: String?,
    val inSlugIndex: Boolean,
    val onDisk: Boolean,
    val poseVariantCount: Int,
    val totalCachedExercises: Int,
    val readyToStart: Boolean,
)

fun inspectTrainingConfigCache(
    slug: String,
    exerciseId: String? = null,
    exerciseName: String = slug,
): TrainingConfigCacheSnapshot {
    if (!MovitData.isInstalled) {
        return TrainingConfigCacheSnapshot(
            exerciseName = exerciseName,
            slugCandidates = emptyList(),
            resolvedSlug = null,
            inSlugIndex = false,
            onDisk = false,
            poseVariantCount = 0,
            totalCachedExercises = 0,
            readyToStart = false,
        )
    }

    val candidates = trainingSlugCandidates(slug, exerciseId)
    val resolved = resolveCachedTrainingSlug(slug, exerciseId)
    val repo = MovitData.trainingConfig
    val record = resolved?.let { repo.getBySlug(it) }
    val inIndex = resolved?.let { repo.supports(it) } == true
    val onDisk = record != null

    return TrainingConfigCacheSnapshot(
        exerciseName = exerciseName,
        slugCandidates = candidates,
        resolvedSlug = resolved,
        inSlugIndex = inIndex,
        onDisk = onDisk,
        poseVariantCount = record?.config?.poseVariants?.size ?: 0,
        totalCachedExercises = repo.allCachedSlugs().size,
        readyToStart = resolved != null && onDisk,
    )
}

fun logTrainingConfigCache(
    context: String,
    snapshot: TrainingConfigCacheSnapshot,
    workoutId: String? = null,
    extra: String? = null,
) {
    val workoutPart = workoutId?.let { " workoutId=$it" }.orEmpty()
    val extraPart = extra?.let { " $it" }.orEmpty()
    val status = if (snapshot.readyToStart) "READY" else "MISSING"
    println(
        "[TrainingConfigCache] $context status=$status" +
            " exercise=\"${snapshot.exerciseName}\"" +
            " candidates=${snapshot.slugCandidates}" +
            " resolved=${snapshot.resolvedSlug ?: "null"}" +
            " inIndex=${snapshot.inSlugIndex}" +
            " onDisk=${snapshot.onDisk}" +
            " variants=${snapshot.poseVariantCount}" +
            " totalCached=${snapshot.totalCachedExercises}" +
            workoutPart +
            extraPart,
    )
}

internal fun trainingSlugCandidates(slug: String, exerciseId: String? = null): List<String> =
    listOfNotNull(
        slug.takeIf { it.isNotBlank() },
        exerciseId?.takeIf { it.isNotBlank() },
        slug.takeIf { it.isNotBlank() }?.let(::normalizeTrainingSlug),
        exerciseId?.let(::normalizeTrainingSlug),
    ).distinct()
