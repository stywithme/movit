package com.movit.core.data.sync

/**
 * F11 — periodic background sync (WorkManager on Android).
 *
 * **Decision (2026-06-12):** Not implemented in this pass.
 *
 * - `androidx.work:work-runtime` is **not** on the classpath yet.
 * - Foreground sync is owned by [com.movit.feature.shell.ShellSyncCoordinator] +
 *   [MovitSyncOrchestrator] on app open / connectivity hooks (see migration plan F4/F11).
 *
 * **When adding WorkManager:**
 * 1. `implementation(libs.androidx.work.runtime)` in `:app` only (Android-specific).
 * 2. `PeriodicWorkRequest` (~12–24h) calling `MovitData.sync.syncIfNeeded(forceCheck = true)`.
 * 3. Constraints: `NetworkType.CONNECTED`, battery-not-low optional.
 * 4. Initialize from `PoseApp` or `MovitDataInstall` after Koin is ready.
 * 5. iOS counterpart: BGTaskScheduler (deferred).
 */
object BackgroundSyncScheduler
