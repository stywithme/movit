package com.movit.core.data.outbox

/**
 * Documented conflict and retry policy for offline write replay.
 *
 * ## Idempotency
 * Each outbox row has a stable client [OutboxEntry.id] stored as `idempotency_key` locally.
 * Replay skips rows already marked [OutboxStatus.SUCCEEDED] or [OutboxStatus.FAILED_PERMANENT],
 * so a successful flush is never sent twice.
 * Legacy mobile write endpoints do not accept `Idempotency-Key` header or body field — server
 * deduplication is not wired; client-side operation id is the sole guard against double replay.
 *
 * ## Server wins (conflict)
 * HTTP **409** or an explicit stale-version response: the server state is authoritative.
 * The entry is marked [OutboxStatus.SUCCEEDED] (dropped from the queue) and callers should
 * invalidate affected read caches on the next sync so UI reflects server data.
 *
 * ## Retries
 * Network errors and HTTP **5xx** increment [OutboxEntry.attempts] and stay [OutboxStatus.PENDING]
 * until [MAX_ATTEMPTS] is reached, then [OutboxStatus.FAILED_PERMANENT].
 *
 * ## Permanent client errors
 * HTTP **4xx** (except 409): no retry — [OutboxStatus.FAILED_PERMANENT].
 *
 * ## Optimistic local writes
 * UI-facing repositories update local cache immediately, enqueue the outbox row, then attempt
 * inline replay when online. User sees local state until server sync or server-wins invalidation.
 */
object OutboxConflictPolicy {
    const val MAX_ATTEMPTS: Int = 3

    fun isServerWins(httpStatus: Int): Boolean = httpStatus == 409

    fun isPermanentClientError(httpStatus: Int): Boolean =
        httpStatus in 400..499 && !isServerWins(httpStatus)

    fun isRetryable(httpStatus: Int?): Boolean =
        httpStatus == null || httpStatus >= 500

    fun shouldMarkPermanent(attempts: Int, httpStatus: Int?): Boolean =
        (httpStatus != null && isPermanentClientError(httpStatus)) ||
            (attempts >= MAX_ATTEMPTS && isRetryable(httpStatus))
}
