package com.movit.core.data.outbox

import kotlin.random.Random

/**
 * Documented conflict and retry policy for offline write replay.
 *
 * ## Idempotency
 * Each outbox row has a stable client [OutboxEntry.id]. Replay skips rows already marked
 * [OutboxStatus.SUCCEEDED] or [OutboxStatus.FAILED_PERMANENT], so a successful flush is never
 * sent twice. Enqueue replaces an existing row only when it is [OutboxStatus.FAILED_PERMANENT].
 *
 * ## Server wins (conflict)
 * HTTP **409**: mark [OutboxStatus.SUCCEEDED] (dropped) and refresh read caches on next sync.
 *
 * ## Retries (P1.2)
 * - Network / timeout / **5xx** ([OutboxDispatchOutcome.RETRYABLE_NETWORK]): **no attempt ceiling**;
 *   exponential backoff via [OutboxEntry.nextAttemptAtEpochMs] (30s → 2m → 10m → 30m cap + jitter).
 * - Unexpected decode/mapping ([OutboxDispatchOutcome.RETRYABLE_UNEXPECTED]): ceiling
 *   [UNEXPECTED_MAX_ATTEMPTS] then [OutboxStatus.FAILED_PERMANENT].
 * - HTTP **4xx** (except 409): [OutboxStatus.FAILED_PERMANENT] immediately.
 *
 * ## Optimistic local writes
 * UI updates local cache, enqueues, then attempts inline replay when online.
 */
object OutboxConflictPolicy {
    /** Decode/mapping bugs only — network retries are uncapped. */
    const val UNEXPECTED_MAX_ATTEMPTS: Int = 50

    private val BACKOFF_STEPS_MS = longArrayOf(
        30_000L,
        120_000L,
        600_000L,
        1_800_000L,
    )

    fun isServerWins(httpStatus: Int): Boolean = httpStatus == 409

    fun isPermanentClientError(httpStatus: Int): Boolean =
        httpStatus in 400..499 && !isServerWins(httpStatus)

    fun isRetryableHttp(httpStatus: Int?): Boolean =
        httpStatus == null || httpStatus >= 500

    /**
     * @param attempts attempt count **after** the failed dispatch (1 = first failure).
     * @param outcome classified dispatch result — pass the real outcome (never invent null status).
     */
    internal fun shouldMarkPermanent(attempts: Int, outcome: OutboxDispatchOutcome): Boolean =
        when (outcome) {
            OutboxDispatchOutcome.PERMANENT_FAILURE -> true
            OutboxDispatchOutcome.RETRYABLE_UNEXPECTED -> attempts >= UNEXPECTED_MAX_ATTEMPTS
            OutboxDispatchOutcome.RETRYABLE_NETWORK -> false
            OutboxDispatchOutcome.SUCCESS,
            OutboxDispatchOutcome.SERVER_WINS,
            -> false
        }

    /** Delay before the next retry after [attempts] failures (1-based). First post-enqueue try waits 0. */
    fun nextAttemptDelayMs(attempts: Int, random: Random = Random.Default): Long {
        if (attempts <= 0) return 0L
        val step = BACKOFF_STEPS_MS[(attempts - 1).coerceAtMost(BACKOFF_STEPS_MS.lastIndex)]
        val jitter = random.nextLong(0L, (step / 5).coerceAtLeast(1L))
        return step + jitter
    }

    fun nextAttemptAtEpochMs(
        nowEpochMs: Long,
        attempts: Int,
        random: Random = Random.Default,
    ): Long? {
        val delay = nextAttemptDelayMs(attempts, random)
        return if (delay <= 0L) null else nowEpochMs + delay
    }
}
