package com.movit.core.data.repository

/** Set-weighted cumulative form score for local exercise metrics cache. */
internal fun computeWeightedAverageFormScore(
    previousAverage: Float?,
    previousSetsCompleted: Int,
    newSetFormScore: Float,
): Float {
    val prevSets = previousSetsCompleted.coerceAtLeast(0)
    if (prevSets == 0 || previousAverage == null) return newSetFormScore
    return ((previousAverage * prevSets) + newSetFormScore) / (prevSets + 1)
}
