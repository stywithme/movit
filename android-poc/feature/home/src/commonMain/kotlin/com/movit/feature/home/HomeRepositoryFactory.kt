package com.movit.feature.home

/**
 * Platform-provided default [HomeRepository].
 * Android may bridge to Retrofit via [com.movit.feature.home.remote.HomeContentFetcherBridge].
 * iOS uses fake data until Ktor integration lands.
 */
expect fun defaultHomeRepository(): HomeRepository
