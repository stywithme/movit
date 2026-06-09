package com.movit.feature.explore

/**
 * Platform-provided default [ExploreRepository].
 * Uses [SharedExploreRepository] on all platforms via [com.movit.core.data.MovitData].
 * iOS uses fake data until Ktor integration lands.
 */
expect fun defaultExploreRepository(): ExploreRepository
