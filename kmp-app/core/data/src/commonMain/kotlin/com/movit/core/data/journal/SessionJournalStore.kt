package com.movit.core.data.journal

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.local.SessionJournalRow
import com.movit.core.data.repository.MovitCacheKeys
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
        localStore.writeJsonCache(
            store = MovitCacheKeys.SESSION_JOURNAL_STORE,
            key = MovitCacheKeys.sessionJournalKey(snapshot.sessionId),
            value = MovitJson.encodeToString(SessionJournalSnapshot.serializer(), snapshot),
        )
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
        return runCatching {
            MovitJson.decodeFromString(SessionJournalSnapshot.serializer(), cached)
        }.getOrNull()
    }

    fun listActiveCheckpoints(): List<SessionJournalSnapshot> =
        localStore.listActiveSessionJournals().mapNotNull { row ->
            runCatching {
                MovitJson.decodeFromString(SessionJournalSnapshot.serializer(), row.payloadJson)
            }.getOrNull()
        }

    fun markCompleted(sessionId: String) {
        val snapshot = readCheckpoint(sessionId) ?: return
        saveCheckpoint(snapshot, SessionJournalStatus.COMPLETED)
        localStore.deleteSessionJournal(sessionId)
        localStore.removeJsonCache(
            MovitCacheKeys.SESSION_JOURNAL_STORE,
            MovitCacheKeys.sessionJournalKey(sessionId),
        )
    }

    fun delete(sessionId: String) {
        localStore.deleteSessionJournal(sessionId)
        localStore.removeJsonCache(
            MovitCacheKeys.SESSION_JOURNAL_STORE,
            MovitCacheKeys.sessionJournalKey(sessionId),
        )
    }
}
