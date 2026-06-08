package com.movit.feature.explore

/**
 * Platform-provided default [ExploreRepository].
 * Android may bridge to Retrofit via [com.movit.feature.explore.remote.ExploreContentFetcherBridge].
 * iOS uses fake data until Ktor integration lands.
 */
expect fun defaultExploreRepository(): ExploreRepository
