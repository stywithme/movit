package com.movit.core.data.cache

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.network.MovitJson
import com.movit.shared.AppResult
import kotlinx.serialization.KSerializer

/**
 * Unified read-through / write-through cache policy for sync repositories.
 */
object MovitCachePolicy {
    fun <T> readJson(
        store: MovitLocalStore,
        namespace: String,
        key: String,
        serializer: KSerializer<T>,
    ): T? {
        val raw = store.readJsonCache(namespace, key) ?: return null
        return runCatching { MovitJson.decodeFromString(serializer, raw) }.getOrNull()
    }

    fun <T> writeJson(
        store: MovitLocalStore,
        namespace: String,
        key: String,
        value: T,
        serializer: KSerializer<T>,
    ) {
        store.writeJsonCache(namespace, key, MovitJson.encodeToString(serializer, value))
    }

    suspend fun <T> syncWithFallback(
        cached: T?,
        authRequired: Boolean,
        hasAuth: Boolean,
        noAuthMessage: String,
        fetch: suspend () -> Result<T>,
        isSuccess: (T) -> Boolean,
        errorMessage: (T) -> String,
        persist: (T) -> Unit,
        failureMessage: (Throwable) -> String = { it.message ?: "Sync failed." },
    ): AppResult<T> {
        if (authRequired && !hasAuth) {
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(noAuthMessage)
        }

        val response = fetch().getOrElse { error ->
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(failureMessage(error))
        }

        if (!isSuccess(response)) {
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(errorMessage(response))
        }

        persist(response)
        return AppResult.Success(response)
    }
}
