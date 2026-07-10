package com.movit.core.data.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.movit.core.data.db.MovitDatabase
import com.movit.core.data.outbox.OutboxEntry
import com.movit.core.data.outbox.OutboxOperationType
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.repository.FakeMovitPlatformBindings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P0.2: first SQLDelight migration — upgrade path from implicit v1 outbox schema.
 */
class OutboxSchemaMigrationTest {
    @Test
    fun schemaVersionIsTwoAfterFirstMigration() {
        assertEquals(2L, MovitDatabase.Schema.version)
    }

    @Test
    fun migrateFromV1_addsOwnerAndNextAttemptColumns() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createV1OutboxSchema(driver)
        MovitDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 2)

        val store = SqlDelightMovitLocalStore(MovitDatabase(driver))
        store.insertOutbox(
            OutboxEntry(
                id = "op-1",
                type = OutboxOperationType.WORKOUT_EXECUTION_UPLOAD,
                payload = "{}",
                createdAt = 1_700_000_000_000L,
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = "user-a",
                nextAttemptAtEpochMs = 1_700_000_060_000L,
            ),
        )
        val loaded = store.getOutboxById("op-1")
        assertEquals("user-a", loaded?.ownerUserId)
        assertEquals(1_700_000_060_000L, loaded?.nextAttemptAtEpochMs)
    }

    @Test
    fun backfill_setsOwnerOnNullRowsFromCurrentSession() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createV1OutboxSchema(driver)
        // Pre-migration row (no owner column yet)
        driver.execute(
            null,
            """
            INSERT INTO outbox_entry(
                id, operation_type, payload_json, idempotency_key,
                created_at_epoch_ms, attempts, status, last_error
            ) VALUES ('legacy-1', 'WORKOUT_EXECUTION_UPLOAD', '{}', 'legacy-1', 1, 0, 'pending', NULL)
            """.trimIndent(),
            0,
        )
        MovitDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 2)

        val sqlStore = SqlDelightMovitLocalStore(MovitDatabase(driver))
        val platform = FakeMovitPlatformBindings().also { it.setUserId("session-user") }
        MigratingMovitLocalStore(sqlStore, platform = { platform }).migrateKnownCachesFromPlatform()

        val loaded = sqlStore.getOutboxById("legacy-1")
        assertEquals("session-user", loaded?.ownerUserId)
        assertNull(loaded?.nextAttemptAtEpochMs)
    }

    @Test
    fun freshCreate_includesNewColumns() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MovitDatabase.Schema.create(driver)
        val store = SqlDelightMovitLocalStore(MovitDatabase(driver))
        store.insertOutbox(
            OutboxEntry(
                id = "fresh-1",
                type = OutboxOperationType.PLAN_COMPLETE,
                payload = "{}",
                createdAt = 2L,
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = null,
            ),
        )
        val loaded = store.getOutboxById("fresh-1")
        assertNull(loaded?.ownerUserId)
        assertTrue(MovitDatabase.Schema.version >= 2)
    }

    private fun createV1OutboxSchema(driver: JdbcSqliteDriver) {
        // Minimal v1 tables matching pre-P0.2 CREATE statements (version 1).
        driver.execute(
            null,
            """
            CREATE TABLE outbox_entry (
                id TEXT NOT NULL PRIMARY KEY,
                operation_type TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                idempotency_key TEXT NOT NULL,
                created_at_epoch_ms INTEGER NOT NULL,
                attempts INTEGER NOT NULL,
                status TEXT NOT NULL,
                last_error TEXT
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE json_cache_entry (
                store TEXT NOT NULL,
                cache_key TEXT NOT NULL,
                json_payload TEXT NOT NULL,
                updated_at_epoch_ms INTEGER NOT NULL,
                PRIMARY KEY(store, cache_key)
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE sync_metadata (
                scope TEXT NOT NULL PRIMARY KEY,
                version TEXT,
                last_sync_at TEXT,
                updated_at_epoch_ms INTEGER NOT NULL
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE session_journal_entry (
                session_id TEXT NOT NULL PRIMARY KEY,
                exercise_id TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                status TEXT NOT NULL,
                updated_at_epoch_ms INTEGER NOT NULL
            )
            """.trimIndent(),
            0,
        )
        // Mark as v1 so migrate(1→2) is the upgrade path under test.
        driver.execute(null, "PRAGMA user_version = 1", 0)
        val version = driver.executeQuery(null, "PRAGMA user_version", { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
        }, 0).value
        assertEquals(1L, version)
    }
}
