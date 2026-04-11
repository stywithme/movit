package com.trainingvalidator.poc.storage

/**
 * Generic cached entity wrapper for JSON-backed config caches (exercise, workout, program).
 * On-disk shape matches legacy per-type classes: id, slug, updatedAt, config.
 */
data class CachedEntity<T>(
    val id: String,
    val slug: String,
    val updatedAt: String,
    val config: T
)
