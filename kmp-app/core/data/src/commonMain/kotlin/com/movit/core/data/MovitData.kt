package com.movit.core.data

import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.cache.ColdOfflineBundleSeeder
import com.movit.core.data.cache.SystemMessageCache
import com.movit.core.data.di.movitDataModule
import com.movit.core.data.local.DefaultMovitLocalStoreFactory
import com.movit.core.data.local.GUEST_OUTBOX_RETENTION_MS
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.local.MovitLocalStoreFactory
import com.movit.core.data.cache.logCacheFreshnessLine
import com.movit.core.data.cache.MovitCacheFreshnessDiagnostics
import com.movit.core.data.cache.MovitCacheFreshnessReport
import com.movit.core.data.outbox.GuestOutboxAttributionGate
import com.movit.core.data.outbox.GuestOutboxAttributionPrompt
import com.movit.core.data.outbox.LegacyAnalyticsPendingCleaner
import com.movit.core.data.outbox.LegacyWorkoutSyncGate
import com.movit.core.data.outbox.OfflineWriteQueue
import com.movit.core.data.outbox.OutboxReplayAcquisition
import com.movit.core.data.outbox.WorkoutRunStoreBridge
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.repository.AccountSyncRepository
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.MobileWriteSyncRepository
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.data.repository.PlanSyncRepository
import com.movit.core.data.repository.ProgramFlowSyncRepository
import com.movit.core.data.repository.ReportsSyncRepository
import com.movit.core.data.preferences.MovitTrainingPreferences
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.data.repository.TrainingSessionWriteCoordinator
import com.movit.core.data.repository.WorkoutSessionSyncRepository
import com.movit.core.data.sync.MovitCacheInvalidation
import com.movit.core.data.sync.MovitSyncOrchestrator
import com.movit.core.data.sync.SyncStatusBus
import com.movit.core.data.sync.WeekOfflinePackPrefetcher
import com.movit.core.data.readiness.BackgroundMediaPrefetcher
import com.movit.core.data.readiness.DataReadinessGate
import com.movit.core.network.MovitClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module

/**
 * Typed accessors for Movit sync repositories resolved through Koin.
 * Call [install] once per process from the platform entry point.
 *
 * Holds the [KoinApplication] reference directly rather than reading
 * `GlobalContext`, which is JVM-only and does not resolve on Kotlin/Native (iOS).
 * `startKoin`/`stopKoin` + the returned [KoinApplication.koin] are the
 * multiplatform-safe surface.
 */
object MovitData {
    private var koinApp: KoinApplication? = null

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sessionExpiredEvents = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )

    /** Shell collects on Main — fired after session-expiry clearReadCaches completes. */
    val sessionExpiredEvents: SharedFlow<Unit> = _sessionExpiredEvents.asSharedFlow()

    /**
     * Legacy callback; prefer [sessionExpiredEvents]. Still invoked after the async clear
     * so existing shell wiring keeps working until fully migrated.
     */
    var onSessionExpired: (() -> Unit)? = null

    val isInstalled: Boolean
        get() = koinApp != null

    fun koin(): Koin = koinApp?.koin ?: error("MovitData.install() was not called.")

    fun install(
        platform: MovitPlatformBindings,
        localStoreFactory: MovitLocalStoreFactory = DefaultMovitLocalStoreFactory,
        additionalModules: List<Module> = emptyList(),
    ) {
        if (koinApp != null) {
            stopKoin()
        }
        onSessionExpired = null
        koinApp = startKoin {
            modules(movitDataModule(platform, localStoreFactory) + additionalModules)
        }
        // P2.10: optimistic writes + sync success share one invalidation sink.
        MovitCacheInvalidation.sink = {
            runCatching { koin().get<HomeSyncRepository>().invalidateMemoized() }
            runCatching { koin().get<ExploreSyncRepository>().invalidateMemoized() }
            runCatching { koin().get<MovitSyncOrchestrator>().notifyCacheInvalidated() }
        }
    }

    /**
     * Seeds bundled cold-start data and hydrates [SystemMessageRegistry] on first launch.
     * Call once after [install] from the shell entry point.
     * Also backfills [MovitPlatformBindings.userId] for legacy sessions that have a token but no id (F3).
     */
    suspend fun bootstrapLocalCaches() {
        if (!isInstalled) return
        val koin = koin()
        ensureUserIdFromProfileIfNeeded()
        koin.get<ColdOfflineBundleSeeder>().seedIfNeeded()
        koin.get<SystemMessageCache>().loadIntoRegistry()
    }

    /**
     * Legacy iOS/Android sessions may have an access token without a persisted userId.
     * One profile fetch re-persists the full session so outbox replay can attribute rows.
     */
    suspend fun ensureUserIdFromProfileIfNeeded() {
        if (!isInstalled) return
        val platform = koin().get<MovitPlatformBindings>()
        if (platform.authHeader().isNullOrBlank()) return
        if (!platform.userId().isNullOrBlank()) return
        runCatching { koin().get<AccountSyncRepository>().fetchProfile() }
    }

    /**
     * Auth refresh concluded the session is dead (401/403). Clears read caches + tokens only;
     * durable outbox / journal / unconfirmed reports stay (PR-7).
     * Non-blocking — no [kotlinx.coroutines.runBlocking].
     */
    internal fun notifySessionExpired() {
        if (!isInstalled) {
            onSessionExpired?.invoke()
            _sessionExpiredEvents.tryEmit(Unit)
            return
        }
        sessionScope.launch {
            runCatching { clearReadCaches() }
            _sessionExpiredEvents.emit(Unit)
            onSessionExpired?.invoke()
        }
    }

    /** PR-7 read scope — session expiry. */
    suspend fun clearReadCaches() {
        if (!isInstalled) return
        val koin = koin()
        koin.get<MovitLocalStore>().clearReadCaches()
        koin.get<AudioManifestCache>().clear()
        // Legacy SharedPreferences may still hold durable report mirrors — wipe only on logout.
    }

    /** PR-7 durable writes — explicit logout / delete account (after P1.8 warning). */
    suspend fun clearDurableWrites() {
        if (!isInstalled) return
        val koin = koin()
        koin.get<MovitLocalStore>().clearDurableWrites()
        WorkoutRunStoreBridge.clearMemoryIfRegistered()
        LegacyAnalyticsPendingCleaner.clearIfRegistered()
        koin.get<GuestOutboxAttributionGate>().clearAcceptance()
        clearLastKnownUserId()
        koin.get<MovitPlatformBindings>().clearLegacyUserCaches()
        koin.get<MovitPlatformBindings>().clearUserFiles()
    }

    /**
     * Clears all user-owned durable caches (SQLDelight JSON + sync metadata + outbox + audio manifest)
     * and legacy platform migration stores. Call before [MovitPlatformBindings.clearAuthSession] on logout.
     */
    suspend fun clearAllUserData() {
        if (!isInstalled) return
        clearReadCaches()
        clearDurableWrites()
    }

    /**
     * After a successful login/register: account-switch hygiene + guest retention + UX.7 prompt.
     * Returns a prompt when guest outbox rows exist (UI must ask before [acceptGuestOutboxAttribution]).
     */
    suspend fun onAuthenticatedSession(userId: String): GuestOutboxAttributionPrompt? {
        if (!isInstalled || userId.isBlank()) return null
        val store = koin().get<MovitLocalStore>()
        val gate = koin().get<GuestOutboxAttributionGate>()
        val previous = readLastKnownUserId()
        if (previous != null && previous != userId) {
            clearReadCaches()
            // Open runs are user-owned — do not let the next account Resume the prior user's session.
            store.clearWorkoutRunStore()
            WorkoutRunStoreBridge.clearMemoryIfRegistered()
            store.deleteOutboxOwnedByOtherUsers(userId)
            gate.clearAcceptance()
        }
        store.deleteGuestOutboxOlderThan(MovitClock.nowEpochMs() - GUEST_OUTBOX_RETENTION_MS)
        writeLastKnownUserId(userId)
        return gate.pendingPrompt()
    }

    /** UX.7 — attribute guest outbox rows to [userId] after the user accepts, then flush outbox. */
    suspend fun acceptGuestOutboxAttribution(userId: String) {
        if (!isInstalled) return
        koin().get<GuestOutboxAttributionGate>().accept(userId)
        // Accept alone only rewrites owner_user_id — replay immediately so uploads leave the device.
        offlineWrites.replayPending(OutboxReplayAcquisition.Wait)
    }

    /** UX.7 — discard guest outbox rows. */
    suspend fun discardGuestOutboxAttribution() {
        if (!isInstalled) return
        koin().get<GuestOutboxAttributionGate>().discard()
    }

    suspend fun pendingGuestOutboxPrompt(): GuestOutboxAttributionPrompt? {
        if (!isInstalled) return null
        return koin().get<GuestOutboxAttributionGate>().pendingPrompt()
    }

    fun requirePlatform(): MovitPlatformBindings = koin().get()

    val localStore: MovitLocalStore get() = koin().get()

    val explore: ExploreSyncRepository get() = koin().get()
    val home: HomeSyncRepository get() = koin().get()
    val plan: PlanSyncRepository get() = koin().get()
    val programFlow: ProgramFlowSyncRepository get() = koin().get()
    val reports: ReportsSyncRepository get() = koin().get()
    val workoutSession: WorkoutSessionSyncRepository get() = koin().get()
    val trainingWrites: TrainingSessionWriteCoordinator get() = koin().get()
    val mobileWrites: MobileWriteSyncRepository get() = koin().get()
    val account: AccountSyncRepository get() = koin().get()
    val billing: com.movit.core.data.repository.BillingSyncRepository get() = koin().get()
    val trainingConfig: TrainingConfigRepository get() = koin().get()
    val trainingPreferences: MovitTrainingPreferences get() = koin().get()
    val audioManifest: AudioManifestCache get() = koin().get()
    val audioPrefetch: AudioPrefetchRunner get() = koin().get()
    val offlineWrites: OfflineWriteQueue get() = koin().get()
    val sync: MovitSyncOrchestrator get() = koin().get()
    val syncStatus: SyncStatusBus get() = koin().get()
    val dataReadiness: DataReadinessGate get() = koin().get()
    val backgroundMediaPrefetch: BackgroundMediaPrefetcher get() = koin().get()
    val weekOfflinePrefetch: WeekOfflinePackPrefetcher get() = koin().get()
    val guestOutboxGate: GuestOutboxAttributionGate get() = koin().get()

    /**
     * Migrates legacy Android analytics pending files into the KMP outbox.
     * Blocks new outbox writes until drain completes ([LegacyWorkoutSyncGate]).
     */
    suspend fun drainLegacyWorkoutExecutions() {
        if (!isInstalled) return
        LegacyWorkoutSyncGate.drainLegacyExecutionsIfRegistered()
    }

    /** SQLDelight cache freshness snapshot for diagnostics and support logs. */
    suspend fun cacheFreshnessReport(): MovitCacheFreshnessReport {
        if (!isInstalled) error("MovitData.install() was not called.")
        return koin().get<MovitCacheFreshnessDiagnostics>().snapshot()
    }

    /** One-line log helper for support / debug builds. */
    suspend fun logCacheFreshness(tag: String = "MovitCache") {
        if (!isInstalled) return
        val line = cacheFreshnessReport().toLogLine()
        logCacheFreshnessLine(tag, line)
    }

    private fun readLastKnownUserId(): String? =
        localStore.readJsonCache(MovitCacheKeys.AUTH_LIFECYCLE_STORE, MovitCacheKeys.LAST_KNOWN_USER_ID)
            ?.takeIf { it.isNotBlank() }

    private fun writeLastKnownUserId(userId: String) {
        localStore.writeJsonCache(
            MovitCacheKeys.AUTH_LIFECYCLE_STORE,
            MovitCacheKeys.LAST_KNOWN_USER_ID,
            userId,
        )
    }

    private fun clearLastKnownUserId() {
        localStore.removeJsonCache(
            MovitCacheKeys.AUTH_LIFECYCLE_STORE,
            MovitCacheKeys.LAST_KNOWN_USER_ID,
        )
    }
}
