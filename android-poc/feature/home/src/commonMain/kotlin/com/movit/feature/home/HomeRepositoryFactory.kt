package com.movit.feature.home

/** Platform-provided default [HomeRepository] backed by the shared Ktor data layer. */
expect fun defaultHomeRepository(): HomeRepository
