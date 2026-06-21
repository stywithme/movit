package com.movit.core.data.cache

import com.movit.core.data.sync.currentTimeMs
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Stale-while-revalidate contract for screen reads (DS-0).
 *
 * Every `Shared*Repository` should:
 * 1. emit cached value immediately via [readCached],
 * 2. request a background refresh via [fetchFresh],
 * 3. emit the refresh as [CacheState.Fresh] or [CacheState.Error].
 */
sealed class CacheState<out T> {
    data object Loading : CacheState<Nothing>()

    data class Cached<T>(val value: T) : CacheState<T>()

    data class Fresh<T>(val value: T) : CacheState<T>()

    data class Error(
        val message: String,
        val cached: Any? = null,
    ) : CacheState<Nothing>()
}

/**
 * Emits [CacheState] in cache-first order: cached (or loading) immediately, then fresh or error.
 */
object StaleWhileRevalidate {
    fun <T> stream(
        screenId: String,
        readCached: () -> T?,
        fetchFresh: suspend () -> AppResult<T>,
    ): Flow<CacheState<T>> = flow {
        val startedAtMs = currentTimeMs()
        val cached = readCached()

        if (cached != null) {
            emit(CacheState.Cached(cached))
            FirstFrameMetric.record(screenId, "Cached", currentTimeMs() - startedAtMs)
        } else {
            emit(CacheState.Loading)
            FirstFrameMetric.record(screenId, "Loading", currentTimeMs() - startedAtMs)
        }

        when (val result = fetchFresh()) {
            is AppResult.Success -> emit(CacheState.Fresh(result.value))
            is AppResult.Failure -> emit(CacheState.Error(result.message, cached))
        }
    }
}

/**
 * Lightweight hook for proving first-frame latency improvements (DS-0).
 * Override [log] in tests; production default logs to stdout.
 */
object FirstFrameMetric {
    var log: (screenId: String, stateKind: String, elapsedMs: Long) -> Unit =
        { screenId, stateKind, elapsedMs ->
            println("[MovitFirstFrame] screen=$screenId state=$stateKind elapsedMs=$elapsedMs")
        }

    fun record(screenId: String, stateKind: String, elapsedMs: Long) {
        log(screenId, stateKind, elapsedMs)
    }
}

/**
 * Screen-layer SWR helper: emit cached data first, refresh in background.
 * On refresh failure, keeps showing stale cache (no [CacheState.Error] when cache exists).
 */
fun <T> staleWhileRevalidate(
    screenId: String,
    readCached: suspend () -> T?,
    syncFresh: suspend () -> AppResult<T>,
): Flow<CacheState<T>> = flow {
    val startedAtMs = currentTimeMs()
    val cached = readCached()

    if (cached != null) {
        emit(CacheState.Cached(cached))
        FirstFrameMetric.record(screenId, "Cached", currentTimeMs() - startedAtMs)
    } else {
        emit(CacheState.Loading)
        FirstFrameMetric.record(screenId, "Loading", currentTimeMs() - startedAtMs)
    }

    when (val result = syncFresh()) {
        is AppResult.Success -> emit(CacheState.Fresh(result.value))
        is AppResult.Failure -> {
            if (cached == null) {
                emit(CacheState.Error(result.message))
            }
        }
    }
}
