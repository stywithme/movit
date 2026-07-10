package com.movit.core.data.journal

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.local.SessionJournalRow
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.network.MovitClock
import com.movit.core.network.MovitJson
import com.movit.core.training.journal.SessionJournalSnapshot

enum class SessionJournalStatus(val storageValue: String) {
    ACTIVE("active"),
    COMPLETED("completed"),
    ABANDONED("abandoned"),
}

class SessionJournalStore(
    private val localStore: MovitLocalStore,
) {
    fun saveCheckpoint(snapshot: SessionJournalSnapshot, status: SessionJournalStatus = SessionJournalStatus.ACTIVE) {
        // P2.12: SQL session_journal is the sole write source; JSON fallback remains for one-release reads.
        localStore.upsertSessionJournal(
            sessionId = snapshot.sessionId,
            exerciseId = snapshot.exerciseId,
            payloadJson = MovitJson.encodeToString(SessionJournalSnapshot.serializer(), snapshot),
            status = status.storageValue,
        )
    }

    fun readCheckpoint(sessionId: String): SessionJournalSnapshot? {
        localStore.selectSessionJournal(sessionId)?.payloadJson?.let { payload ->
            return runCatching {
                MovitJson.decodeFromString(SessionJournalSnapshot.serializer(), payload)
            }.getOrNull()
        }
        val cached = localStore.readJsonCache(
            MovitCacheKeys.SESSION_JOURNAL_STORE,
            MovitCacheKeys.sessionJournalKey(sessionId),
        ) ?: return null
        val snapshot = runCatching {
            MovitJson.decodeFromString(SessionJournalSnapshot.serializer(), cached)
        }.getOrNull() ?: return null
        // Lazy migrate JSON → SQL then drop JSON key.
        localStore.upsertSessionJournal(
            sessionId = snapshot.sessionId,
            exerciseId = snapshot.exerciseId,
            payloadJson = cached,
            status = SessionJournalStatus.ACTIVE.storageValue,
        )
        localStore.removeJsonCache(
            MovitCacheKeys.SESSION_JOURNAL_STORE,
            MovitCacheKeys.sessionJournalKey(sessionId),
        )
        return snapshot
    }

    fun listActiveCheckpoints(): List<SessionJournalSnapshot> =
        localStore.listActiveSessionJournals().mapNotNull { row ->
            runCatching {
                MovitJson.decodeFromString(SessionJournalSnapshot.serializer(), row.payloadJson)
            }.getOrNull()
        }

    /**
     * Orphan policy (P1.5): delete active journals older than [maxAgeMs];
     * if a fresh journal exists for [exerciseId], return it for a resume prompt.
     */
    fun cleanupOrphansAndFindResume(
        exerciseId: String,
        nowMs: Long = MovitClock.nowEpochMs(),
        maxAgeMs: Long = ORPHAN_MAX_AGE_MS,
    ): SessionJournalSnapshot? {
        var newestForExercise: Pair<SessionJournalRow, SessionJournalSnapshot>? = null
        for (row in localStore.listActiveSessionJournals()) {
            if (nowMs - row.updatedAtEpochMs > maxAgeMs) {
                delete(row.sessionId)
                continue
            }
            if (row.exerciseId != exerciseId) continue
            val snapshot = runCatching {
                MovitJson.decodeFromString(SessionJournalSnapshot.serializer(), row.payloadJson)
            }.getOrNull() ?: continue
            val current = newestForExercise
            if (current == null || row.updatedAtEpochMs >= current.first.updatedAtEpochMs) {
                newestForExercise = row to snapshot
            }
        }
        return newestForExercise?.second
    }

    /** P1.5 / H18: drop the journal immediately — no COMPLETED write-then-delete. */
    fun markCompleted(sessionId: String) {
        delete(sessionId)
    }

    fun delete(sessionId: String) {
        localStore.deleteSessionJournal(sessionId)
        localStore.removeJsonCache(
            MovitCacheKeys.SESSION_JOURNAL_STORE,
            MovitCacheKeys.sessionJournalKey(sessionId),
        )
    }

    companion object {
        const val ORPHAN_MAX_AGE_MS: Long = 6L * 60L * 60L * 1000L
    }
}
