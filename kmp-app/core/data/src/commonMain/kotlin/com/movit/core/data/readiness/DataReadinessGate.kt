package com.movit.core.data.readiness

import com.movit.core.data.cache.MessageLibraryCache
import com.movit.core.data.cache.SystemMessageCache
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.SyncCatalogOfflineRepository
import com.movit.core.data.repository.TrainingConfigRepository

enum class DataReadinessPart {
    ExploreCatalog,
    ExerciseConfigs,
    CatalogExports,
    MessageLibrary,
    SystemMessages,
    HomeDashboard,
}

sealed class DataReadinessResult {
    data object Ready : DataReadinessResult()

    data class Missing(
        val parts: List<DataReadinessPart>,
    ) : DataReadinessResult() {
        val hasPartialCache: Boolean get() = parts.size < DataReadinessPart.entries.size
    }
}

/**
 * Local checks before showing main tabs (R6 / Option 3).
 *
 * **CoreReady (boot / Tabs)** = Explore catalog + exercise config index
 * (+ Home only when [requireHome] is true).
 *
 * Soft gaps filled by background sync after Tabs — never block Splash hide:
 * CatalogExports · MessageLibrary · SystemMessages.
 *
 * Presence checks (not 1:1 explore→cache): explore cards are mapped leniently from sync JSON
 * while configs/exports drop malformed rows — requiring every card blocked Splash after HTTP 200.
 */
class DataReadinessGate(
    private val exploreSync: ExploreSyncRepository,
    private val trainingConfig: TrainingConfigRepository,
    private val catalogOffline: SyncCatalogOfflineRepository,
    private val messageLibraryCache: MessageLibraryCache,
    private val systemMessageCache: SystemMessageCache,
    private val homeSync: HomeSyncRepository,
) {
    /**
     * @param requireHome when true, missing Home counts as not ready (cold-start opt-in).
     * @param includeSoftGaps when true, also report CatalogExports / messages as Missing
     *   for diagnostics — still not CoreReady blockers when those are the only gaps;
     *   prefer [isCoreReady] for tab entry.
     */
    fun evaluate(
        requireHome: Boolean = false,
        includeSoftGaps: Boolean = false,
    ): DataReadinessResult {
        val missing = mutableListOf<DataReadinessPart>()

        val explore = exploreSync.readCached()
        val hasExploreCatalog = explore != null &&
            (explore.exercises.isNotEmpty() ||
                explore.workoutTemplates.isNotEmpty() ||
                explore.programs.isNotEmpty())
        if (!hasExploreCatalog) {
            missing += DataReadinessPart.ExploreCatalog
        }

        // Index presence only — per-slug gaps use R3 ensure/download, not Splash.
        if (trainingConfig.allCachedSlugs().isEmpty()) {
            missing += DataReadinessPart.ExerciseConfigs
        }

        if (requireHome && homeSync.readCached() == null) {
            missing += DataReadinessPart.HomeDashboard
        }

        if (includeSoftGaps) {
            if (explore != null) {
                val needsWorkouts = explore.workoutTemplates.any { it.id.isNotBlank() }
                val needsPrograms = explore.programs.any { it.id.isNotBlank() }
                if ((needsWorkouts && catalogOffline.allWorkoutTemplateIds().isEmpty()) ||
                    (needsPrograms && catalogOffline.allProgramIds().isEmpty())
                ) {
                    missing += DataReadinessPart.CatalogExports
                }
            }
            // Soft: expensive JSON reads — only when diagnostics ask for soft gaps.
            if (messageLibraryCache.read().isEmpty()) {
                missing += DataReadinessPart.MessageLibrary
            }
            if (systemMessageCache.read().isEmpty()) {
                missing += DataReadinessPart.SystemMessages
            }
        }

        return if (missing.isEmpty()) {
            DataReadinessResult.Ready
        } else {
            DataReadinessResult.Missing(missing.distinct())
        }
    }

    /** Boot / Tabs gate: Explore + configs (+ optional Home). Soft gaps ignored. */
    fun isCoreReady(requireHome: Boolean = false): Boolean =
        evaluate(requireHome = requireHome, includeSoftGaps = false) is DataReadinessResult.Ready
}
