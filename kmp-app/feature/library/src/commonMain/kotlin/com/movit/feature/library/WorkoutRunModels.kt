package com.movit.feature.library

import com.movit.core.network.MovitClock
import com.movit.core.training.session.TrainingFlowItem
import kotlin.random.Random

/** Stable identity for one workout attempt (created at Start, not when opening details). */
data class WorkoutRunId(val value: String) {
    override fun toString(): String = value

    companion object {
        fun generate(): WorkoutRunId =
            WorkoutRunId("run-${MovitClock.nowEpochMs()}-${Random.nextInt(1_000_000)}")
    }
}

sealed interface ExerciseTarget {
    data class Reps(val count: Int) : ExerciseTarget
    data class Duration(val seconds: Int) : ExerciseTarget
}

sealed interface WorkoutRunBlock {
    data class Exercise(
        val exerciseId: String,
        val slug: String,
        val displayName: String,
        val phaseRole: String,
        val target: ExerciseTarget,
        val sets: Int,
        val restBetweenSetsMs: Long,
        val restAfterExerciseMs: Long,
        val poseVariantIndex: Int,
        val weightPerSetKg: List<Float>?,
    ) : WorkoutRunBlock

    data class Rest(val durationMs: Long) : WorkoutRunBlock
}

data class WorkoutRunSnapshot(
    val workoutId: String,
    val title: String,
    val blocks: List<WorkoutRunBlock>,
    val version: Long = MovitClock.nowEpochMs(),
) {
    val exercises: List<WorkoutRunBlock.Exercise>
        get() = blocks.filterIsInstance<WorkoutRunBlock.Exercise>()

    /** Start is safe when every exercise has a slug and a concrete target. */
    val isStartable: Boolean
        get() = exercises.isNotEmpty() && exercises.all { ex ->
            ex.slug.isNotBlank() && when (val t = ex.target) {
                is ExerciseTarget.Reps -> t.count > 0
                is ExerciseTarget.Duration -> t.seconds > 0
            }
        }
}

sealed interface WorkoutRunSource {
    data object Explore : WorkoutRunSource
    data object Train : WorkoutRunSource
    data object Program : WorkoutRunSource
    data object Resume : WorkoutRunSource
}

/** Where Back / Done from the post-run report should land. */
sealed interface ReturnTarget {
    data object Train : ReturnTarget
    data object Explore : ReturnTarget
    data class WorkoutSession(val workoutId: String) : ReturnTarget
    data class ProgramDetail(val programId: String, val weekNumber: Int? = null) : ReturnTarget
}

sealed interface ReportTarget {
    data class Exercise(val reportId: String) : ReportTarget
    data class WorkoutRun(val reportId: String, val runId: String) : ReportTarget
    data class ProgramDay(
        val reportId: String,
        val plannedWorkoutId: String,
        val programId: String?,
        val weekNumber: Int,
        val dayNumber: Int,
    ) : ReportTarget
    data class ProgramWeek(val programId: String, val weekNumber: Int) : ReportTarget
}

enum class WorkoutRunStatus {
    Active,
    Completed,
    Abandoned,
}

data class WorkoutRunRecord(
    val runId: WorkoutRunId,
    val workoutId: String,
    val source: WorkoutRunSource,
    val returnTarget: ReturnTarget,
    val doneTarget: ReturnTarget,
    val snapshot: WorkoutRunSnapshot,
    /** Upload grouping key — equals [runId] so repeat runs never share uploads. */
    val workoutGroupId: String,
    val status: WorkoutRunStatus = WorkoutRunStatus.Active,
    val reportTarget: ReportTarget? = null,
    /** P1.4 — last known exercise/set/block for Save and exit / Resume. */
    val progress: WorkoutRunProgressCursor = WorkoutRunProgressCursor(),
    /** Account that started the run — blocks Resume after account switch. */
    val ownerUserId: String? = null,
    /** P1 — partial day/session report built as exercises complete (survives Save/exit). */
    val accumulatedReport: com.movit.core.training.report.MovitSessionReport? = null,
)

/** In-run cursor persisted for durable resume (process recreation). */
data class WorkoutRunProgressCursor(
    val exerciseIndex: Int = 0,
    val currentSet: Int = 1,
    val blockPhase: String = "PRE_EXERCISE",
    val exerciseSlug: String = "",
    val updatedAtEpochMs: Long = 0L,
)

/** Train / session CTA surface — Phase C may map labels from this. */
data class WorkoutRunOpenState(
    val workoutId: String,
    val runId: String,
    val exerciseIndex: Int,
    val currentSet: Int,
    val exerciseSlug: String,
    val blockPhase: String,
)

/**
 * In-memory store for active/recent workout runs, with a durable progress sidecar
 * so Save and exit / process recreation can resume (P1.4).
 * Created at Start; closed as completed/abandoned.
 */
object WorkoutRunStore {
    private const val MAX_RUNS = 8
    private val runs = linkedMapOf<String, WorkoutRunRecord>()

    init {
        // Wire logout / account-switch memory clear without core→feature compile dep.
        com.movit.core.data.outbox.WorkoutRunStoreBridge.clearMemory = { clearAll() }
    }

    fun start(
        workoutId: String,
        snapshot: WorkoutRunSnapshot,
        source: WorkoutRunSource = WorkoutRunSource.Explore,
        returnTarget: ReturnTarget = ReturnTarget.WorkoutSession(workoutId),
        doneTarget: ReturnTarget = ReturnTarget.Explore,
        runId: WorkoutRunId = WorkoutRunId.generate(),
        progress: WorkoutRunProgressCursor = WorkoutRunProgressCursor(),
        ownerUserId: String? = currentOwnerUserId(),
    ): WorkoutRunRecord {
        // One active run per workout — abandon prior open attempt (unless reusing same runId).
        activeForWorkout(workoutId)?.let { prior ->
            if (prior.runId.value != runId.value) abandon(prior.runId.value)
        }
        val priorSame = runs[runId.value]
        val preservedReport = priorSame?.accumulatedReport
            ?: readDurableAccumulatedReport(runId.value)
        val record = WorkoutRunRecord(
            runId = runId,
            workoutId = workoutId,
            source = source,
            returnTarget = returnTarget,
            doneTarget = doneTarget,
            snapshot = snapshot,
            workoutGroupId = runId.value,
            progress = progress,
            ownerUserId = ownerUserId,
            accumulatedReport = preservedReport,
        )
        runs[runId.value] = record
        persistProgress(record)
        trim()
        return record
    }

    fun get(runId: String): WorkoutRunRecord? =
        (runs[runId] ?: hydrateFromDurable(runId))?.takeIf { isVisibleToCurrentUser(it) }

    fun get(runId: WorkoutRunId): WorkoutRunRecord? = get(runId.value)

    fun activeForWorkout(workoutId: String): WorkoutRunRecord? =
        runs.values.lastOrNull {
            it.workoutId == workoutId &&
                it.status == WorkoutRunStatus.Active &&
                isVisibleToCurrentUser(it)
        } ?: hydrateActiveForWorkout(workoutId)?.takeIf { isVisibleToCurrentUser(it) }

    /** Alias used by prepare-mode helpers. */
    fun activeRunForWorkout(workoutId: String): WorkoutRunRecord? = activeForWorkout(workoutId)

    fun openStateForWorkout(workoutId: String): WorkoutRunOpenState? {
        val active = activeForWorkout(workoutId) ?: return null
        val p = active.progress
        return WorkoutRunOpenState(
            workoutId = active.workoutId,
            runId = active.runId.value,
            exerciseIndex = p.exerciseIndex,
            currentSet = p.currentSet,
            exerciseSlug = p.exerciseSlug,
            blockPhase = p.blockPhase,
        )
    }

    fun hasOpenRun(workoutId: String): Boolean = openStateForWorkout(workoutId) != null

    fun saveProgress(
        runId: String,
        exerciseIndex: Int,
        currentSet: Int,
        exerciseSlug: String,
        blockPhase: String,
        nowMs: Long = MovitClock.nowEpochMs(),
    ): WorkoutRunRecord? {
        val existing = get(runId) ?: return null
        if (existing.status != WorkoutRunStatus.Active) return existing
        val updated = existing.copy(
            progress = WorkoutRunProgressCursor(
                exerciseIndex = exerciseIndex.coerceAtLeast(0),
                currentSet = currentSet.coerceAtLeast(1),
                blockPhase = blockPhase,
                exerciseSlug = exerciseSlug,
                updatedAtEpochMs = nowMs,
            ),
        )
        runs[runId] = updated
        persistProgress(updated)
        return updated
    }

    fun saveProgressForWorkout(
        workoutId: String,
        exerciseIndex: Int,
        currentSet: Int,
        exerciseSlug: String,
        blockPhase: String,
        nowMs: Long = MovitClock.nowEpochMs(),
    ): WorkoutRunRecord? {
        val active = activeForWorkout(workoutId) ?: return null
        return saveProgress(
            runId = active.runId.value,
            exerciseIndex = exerciseIndex,
            currentSet = currentSet,
            exerciseSlug = exerciseSlug,
            blockPhase = blockPhase,
            nowMs = nowMs,
        )
    }

    fun saveAccumulatedReport(
        runId: String,
        report: com.movit.core.training.report.MovitSessionReport,
    ): WorkoutRunRecord? {
        val existing = get(runId) ?: return null
        if (existing.status != WorkoutRunStatus.Active) return existing
        val updated = existing.copy(accumulatedReport = report)
        runs[runId] = updated
        persistAccumulatedReport(runId, report)
        return updated
    }

    fun getAccumulatedReport(runId: String): com.movit.core.training.report.MovitSessionReport? {
        val record = get(runId) ?: return null
        return record.accumulatedReport ?: readDurableAccumulatedReport(runId)
    }

    fun complete(runId: String, reportTarget: ReportTarget? = null): WorkoutRunRecord? {
        val existing = runs[runId] ?: return null
        val updated = existing.copy(
            status = WorkoutRunStatus.Completed,
            reportTarget = reportTarget ?: existing.reportTarget,
        )
        runs[runId] = updated
        clearDurable(runId)
        return updated
    }

    fun abandon(runId: String): WorkoutRunRecord? {
        val existing = runs[runId] ?: hydrateFromDurable(runId) ?: return null
        val updated = existing.copy(status = WorkoutRunStatus.Abandoned)
        runs[runId] = updated
        clearDurable(runId)
        return updated
    }

    fun abandonActiveForWorkout(workoutId: String) {
        activeForWorkout(workoutId)?.let { abandon(it.runId.value) }
        clearDurableForWorkout(workoutId)
    }

    /** In-memory only — durable JSON is cleared by [com.movit.core.data.MovitData] logout / account switch. */
    internal fun clearAll() {
        runs.clear()
    }

    private fun currentOwnerUserId(): String? {
        if (!com.movit.core.data.MovitData.isInstalled) return null
        return com.movit.core.data.MovitData.requirePlatform().userId()?.takeIf { it.isNotBlank() }
    }

    private fun isVisibleToCurrentUser(record: WorkoutRunRecord): Boolean {
        val owner = record.ownerUserId?.takeIf { it.isNotBlank() } ?: return true
        val current = currentOwnerUserId() ?: return false
        return owner == current
    }

    private fun trim() {
        while (runs.size > MAX_RUNS) {
            val oldest = runs.keys.firstOrNull() ?: break
            runs.remove(oldest)
        }
    }

    private fun persistProgress(record: WorkoutRunRecord) {
        if (!com.movit.core.data.MovitData.isInstalled) return
        if (record.status != WorkoutRunStatus.Active) return
        val payload = DurableWorkoutRunProgress(
            runId = record.runId.value,
            workoutId = record.workoutId,
            exerciseIndex = record.progress.exerciseIndex,
            currentSet = record.progress.currentSet,
            blockPhase = record.progress.blockPhase,
            exerciseSlug = record.progress.exerciseSlug,
            status = record.status.name,
            updatedAtEpochMs = record.progress.updatedAtEpochMs.takeIf { it > 0 }
                ?: MovitClock.nowEpochMs(),
            ownerUserId = record.ownerUserId,
        )
        runCatching {
            val json = payload.encode()
            com.movit.core.data.MovitData.localStore.writeJsonCache(
                com.movit.core.data.repository.MovitCacheKeys.WORKOUT_RUN_STORE,
                com.movit.core.data.repository.MovitCacheKeys.workoutRunKey(record.workoutId),
                json,
            )
            com.movit.core.data.MovitData.localStore.writeJsonCache(
                com.movit.core.data.repository.MovitCacheKeys.WORKOUT_RUN_STORE,
                durableRunKey(record.runId.value),
                json,
            )
            // Full snapshot so Resume after process death does not depend on session rebuild.
            if (record.snapshot.blocks.isNotEmpty()) {
                val snap = DurableWorkoutRunSnapshotCodec.encode(
                    snapshot = record.snapshot,
                    source = record.source,
                    returnTarget = record.returnTarget,
                    doneTarget = record.doneTarget,
                )
                com.movit.core.data.MovitData.localStore.writeJsonCache(
                    com.movit.core.data.repository.MovitCacheKeys.WORKOUT_RUN_STORE,
                    durableSnapshotKey(record.runId.value),
                    snap,
                )
            }
        }
    }

    private fun clearDurable(runId: String) {
        if (!com.movit.core.data.MovitData.isInstalled) return
        val workoutId = runs[runId]?.workoutId
            ?: readDurableByRunId(runId)?.workoutId
        runCatching {
            com.movit.core.data.MovitData.localStore.removeJsonCache(
                com.movit.core.data.repository.MovitCacheKeys.WORKOUT_RUN_STORE,
                durableRunKey(runId),
            )
            com.movit.core.data.MovitData.localStore.removeJsonCache(
                com.movit.core.data.repository.MovitCacheKeys.WORKOUT_RUN_STORE,
                durableSnapshotKey(runId),
            )
            com.movit.core.data.MovitData.localStore.removeJsonCache(
                com.movit.core.data.repository.MovitCacheKeys.WORKOUT_RUN_STORE,
                durableAccumKey(runId),
            )
            if (workoutId != null) {
                com.movit.core.data.MovitData.localStore.removeJsonCache(
                    com.movit.core.data.repository.MovitCacheKeys.WORKOUT_RUN_STORE,
                    com.movit.core.data.repository.MovitCacheKeys.workoutRunKey(workoutId),
                )
            }
        }
    }

    private fun clearDurableForWorkout(workoutId: String) {
        if (!com.movit.core.data.MovitData.isInstalled) return
        runCatching {
            val byWorkout = readDurableByWorkoutId(workoutId)
            if (byWorkout != null) {
                com.movit.core.data.MovitData.localStore.removeJsonCache(
                    com.movit.core.data.repository.MovitCacheKeys.WORKOUT_RUN_STORE,
                    durableRunKey(byWorkout.runId),
                )
                com.movit.core.data.MovitData.localStore.removeJsonCache(
                    com.movit.core.data.repository.MovitCacheKeys.WORKOUT_RUN_STORE,
                    durableSnapshotKey(byWorkout.runId),
                )
                com.movit.core.data.MovitData.localStore.removeJsonCache(
                    com.movit.core.data.repository.MovitCacheKeys.WORKOUT_RUN_STORE,
                    durableAccumKey(byWorkout.runId),
                )
            }
            com.movit.core.data.MovitData.localStore.removeJsonCache(
                com.movit.core.data.repository.MovitCacheKeys.WORKOUT_RUN_STORE,
                com.movit.core.data.repository.MovitCacheKeys.workoutRunKey(workoutId),
            )
        }
    }

    private fun hydrateActiveForWorkout(workoutId: String): WorkoutRunRecord? {
        val durable = readDurableByWorkoutId(workoutId) ?: return null
        if (durable.status != WorkoutRunStatus.Active.name) return null
        return materializeStub(durable)
    }

    private fun hydrateFromDurable(runId: String): WorkoutRunRecord? {
        val durable = readDurableByRunId(runId) ?: return null
        if (durable.status != WorkoutRunStatus.Active.name) return null
        return materializeStub(durable)
    }

    /**
     * Process recreation: progress + snapshot sidecars are durable.
     * When snapshot is missing (legacy cursor-only payloads), stub keeps open-state CTAs
     * working until WorkoutSession reloads and [start] rehydrates.
     */
    private fun materializeStub(durable: DurableWorkoutRunProgress): WorkoutRunRecord {
        val existing = runs[durable.runId]
        if (existing != null) return existing
        val decodedSnap = readDurableSnapshot(durable.runId)
        val stub = rebuildRecordFromDurable(durable, decodedSnap).copy(
            accumulatedReport = readDurableAccumulatedReport(durable.runId),
        )
        runs[durable.runId] = stub
        return stub
    }

    private fun persistAccumulatedReport(
        runId: String,
        report: com.movit.core.training.report.MovitSessionReport,
    ) {
        if (!com.movit.core.data.MovitData.isInstalled) return
        runCatching {
            val json = kotlinx.serialization.json.Json.encodeToString(
                com.movit.core.training.report.MovitSessionReport.serializer(),
                report,
            )
            com.movit.core.data.MovitData.localStore.writeJsonCache(
                com.movit.core.data.repository.MovitCacheKeys.WORKOUT_RUN_STORE,
                durableAccumKey(runId),
                json,
            )
        }
    }

    private fun readDurableAccumulatedReport(
        runId: String,
    ): com.movit.core.training.report.MovitSessionReport? {
        if (!com.movit.core.data.MovitData.isInstalled) return null
        val payload = com.movit.core.data.MovitData.localStore.readJsonCache(
            com.movit.core.data.repository.MovitCacheKeys.WORKOUT_RUN_STORE,
            durableAccumKey(runId),
        ) ?: return null
        return runCatching {
            kotlinx.serialization.json.Json.decodeFromString(
                com.movit.core.training.report.MovitSessionReport.serializer(),
                payload,
            )
        }.getOrNull()
    }

    private fun readDurableSnapshot(runId: String): DurableWorkoutRunSnapshotCodec.Decoded? {
        if (!com.movit.core.data.MovitData.isInstalled) return null
        val payload = com.movit.core.data.MovitData.localStore.readJsonCache(
            com.movit.core.data.repository.MovitCacheKeys.WORKOUT_RUN_STORE,
            durableSnapshotKey(runId),
        ) ?: return null
        return DurableWorkoutRunSnapshotCodec.decode(payload)
    }

    private fun readDurableByWorkoutId(workoutId: String): DurableWorkoutRunProgress? {
        if (!com.movit.core.data.MovitData.isInstalled) return null
        val payload = com.movit.core.data.MovitData.localStore.readJsonCache(
            com.movit.core.data.repository.MovitCacheKeys.WORKOUT_RUN_STORE,
            com.movit.core.data.repository.MovitCacheKeys.workoutRunKey(workoutId),
        ) ?: return null
        return decodeDurable(payload)
    }

    private fun readDurableByRunId(runId: String): DurableWorkoutRunProgress? {
        if (!com.movit.core.data.MovitData.isInstalled) return null
        val payload = com.movit.core.data.MovitData.localStore.readJsonCache(
            com.movit.core.data.repository.MovitCacheKeys.WORKOUT_RUN_STORE,
            durableRunKey(runId),
        ) ?: return null
        return decodeDurable(payload)
    }

    private fun decodeDurable(payload: String): DurableWorkoutRunProgress? =
        DurableWorkoutRunProgress.decode(payload)

    private fun durableRunKey(runId: String): String = "workout_run_id_$runId"

    private fun durableSnapshotKey(runId: String): String = "workout_run_snap_$runId"

    private fun durableAccumKey(runId: String): String = "workout_run_accum_$runId"
}

/**
 * Rebuild an in-memory record from durable progress (+ optional snapshot sidecar).
 * Used after process death and covered by unit tests without MovitData.
 */
internal fun rebuildRecordFromDurable(
    durable: DurableWorkoutRunProgress,
    decodedSnap: DurableWorkoutRunSnapshotCodec.Decoded?,
): WorkoutRunRecord {
    val snapshot = decodedSnap?.snapshot?.copy(workoutId = durable.workoutId)
        ?: WorkoutRunSnapshot(
            workoutId = durable.workoutId,
            title = "",
            blocks = emptyList(),
        )
    return WorkoutRunRecord(
        runId = WorkoutRunId(durable.runId),
        workoutId = durable.workoutId,
        source = decodedSnap?.source ?: WorkoutRunSource.Resume,
        returnTarget = decodedSnap?.returnTarget ?: ReturnTarget.WorkoutSession(durable.workoutId),
        doneTarget = decodedSnap?.doneTarget ?: ReturnTarget.Explore,
        snapshot = snapshot,
        workoutGroupId = durable.runId,
        status = WorkoutRunStatus.Active,
        progress = WorkoutRunProgressCursor(
            exerciseIndex = durable.exerciseIndex,
            currentSet = durable.currentSet,
            blockPhase = durable.blockPhase,
            exerciseSlug = durable.exerciseSlug,
            updatedAtEpochMs = durable.updatedAtEpochMs,
        ),
        ownerUserId = durable.ownerUserId,
    )
}

/**
 * ponytail: pipe-delimited progress sidecar — avoids kotlinx.serialization in feature:library.
 * Ceiling: field values must not contain '|'.
 */
internal data class DurableWorkoutRunProgress(
    val runId: String,
    val workoutId: String,
    val exerciseIndex: Int = 0,
    val currentSet: Int = 1,
    val blockPhase: String = "PRE_EXERCISE",
    val exerciseSlug: String = "",
    val status: String = WorkoutRunStatus.Active.name,
    val updatedAtEpochMs: Long = 0L,
    val ownerUserId: String? = null,
) {
    fun encode(): String = listOf(
        runId,
        workoutId,
        exerciseIndex.toString(),
        currentSet.toString(),
        blockPhase,
        exerciseSlug,
        status,
        updatedAtEpochMs.toString(),
        ownerUserId.orEmpty(),
    ).joinToString("|")

    companion object {
        fun decode(payload: String): DurableWorkoutRunProgress? {
            val parts = payload.split('|')
            if (parts.size < 8) return null
            return DurableWorkoutRunProgress(
                runId = parts[0],
                workoutId = parts[1],
                exerciseIndex = parts[2].toIntOrNull() ?: 0,
                currentSet = parts[3].toIntOrNull() ?: 1,
                blockPhase = parts[4],
                exerciseSlug = parts[5],
                status = parts[6],
                updatedAtEpochMs = parts[7].toLongOrNull() ?: 0L,
                ownerUserId = parts.getOrNull(8)?.takeIf { it.isNotBlank() },
            )
        }
    }
}

/**
 * ponytail: US/RS-delimited snapshot sidecar (no kotlinx.serialization in feature:library).
 * Ceiling: field values must not contain U+001F / U+001E; upgrade to JSON if needed.
 */
internal object DurableWorkoutRunSnapshotCodec {
    private const val RS = '\u001e'
    private const val US = '\u001f'

    data class Decoded(
        val snapshot: WorkoutRunSnapshot,
        val source: WorkoutRunSource,
        val returnTarget: ReturnTarget,
        val doneTarget: ReturnTarget,
    )

    fun encode(
        snapshot: WorkoutRunSnapshot,
        source: WorkoutRunSource,
        returnTarget: ReturnTarget,
        doneTarget: ReturnTarget,
    ): String {
        val header = listOf(
            "v1",
            sanitize(snapshot.workoutId),
            sanitize(snapshot.title),
            snapshot.version.toString(),
            encodeSource(source),
            encodeReturn(returnTarget),
            encodeReturn(doneTarget),
        ).joinToString(US.toString())
        val blockLines = snapshot.blocks.map { block ->
            when (block) {
                is WorkoutRunBlock.Exercise -> {
                    val (targetKind, targetValue) = when (val t = block.target) {
                        is ExerciseTarget.Reps -> "R" to t.count.toString()
                        is ExerciseTarget.Duration -> "D" to t.seconds.toString()
                    }
                    val weights = block.weightPerSetKg?.joinToString(",") { it.toString() }.orEmpty()
                    listOf(
                        "E",
                        sanitize(block.exerciseId),
                        sanitize(block.slug),
                        sanitize(block.displayName),
                        sanitize(block.phaseRole),
                        targetKind,
                        targetValue,
                        block.sets.toString(),
                        block.restBetweenSetsMs.toString(),
                        block.restAfterExerciseMs.toString(),
                        block.poseVariantIndex.toString(),
                        weights,
                    ).joinToString(US.toString())
                }
                is WorkoutRunBlock.Rest ->
                    listOf("R", block.durationMs.toString()).joinToString(US.toString())
            }
        }
        return (listOf(header) + blockLines).joinToString(RS.toString())
    }

    fun decode(payload: String): Decoded? {
        if (payload.isBlank()) return null
        val lines = payload.split(RS)
        if (lines.isEmpty()) return null
        val header = lines[0].split(US)
        if (header.size < 7 || header[0] != "v1") return null
        val workoutId = header[1]
        val title = header[2]
        val version = header[3].toLongOrNull() ?: 0L
        val source = decodeSource(header[4]) ?: return null
        val returnTarget = decodeReturn(header[5]) ?: return null
        val doneTarget = decodeReturn(header[6]) ?: return null
        val blocks = lines.drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val p = line.split(US)
            when (p.firstOrNull()) {
                "E" -> {
                    if (p.size < 12) return@mapNotNull null
                    val target = when (p[5]) {
                        "D" -> ExerciseTarget.Duration(p[6].toIntOrNull() ?: 0)
                        else -> ExerciseTarget.Reps(p[6].toIntOrNull() ?: 0)
                    }
                    val weights = p[11].takeIf { it.isNotBlank() }?.split(',')
                        ?.mapNotNull { it.toFloatOrNull() }
                        ?.takeIf { it.isNotEmpty() }
                    WorkoutRunBlock.Exercise(
                        exerciseId = p[1],
                        slug = p[2],
                        displayName = p[3],
                        phaseRole = p[4],
                        target = target,
                        sets = p[7].toIntOrNull() ?: 1,
                        restBetweenSetsMs = p[8].toLongOrNull() ?: 0L,
                        restAfterExerciseMs = p[9].toLongOrNull() ?: 0L,
                        poseVariantIndex = p[10].toIntOrNull() ?: 0,
                        weightPerSetKg = weights,
                    )
                }
                "R" -> {
                    if (p.size < 2) return@mapNotNull null
                    WorkoutRunBlock.Rest(durationMs = p[1].toLongOrNull() ?: 0L)
                }
                else -> null
            }
        }
        return Decoded(
            snapshot = WorkoutRunSnapshot(
                workoutId = workoutId,
                title = title,
                blocks = blocks,
                version = version,
            ),
            source = source,
            returnTarget = returnTarget,
            doneTarget = doneTarget,
        )
    }

    private fun sanitize(value: String): String =
        value.replace(US, ' ').replace(RS, ' ')

    private fun encodeSource(source: WorkoutRunSource): String =
        when (source) {
            WorkoutRunSource.Explore -> "Explore"
            WorkoutRunSource.Train -> "Train"
            WorkoutRunSource.Program -> "Program"
            WorkoutRunSource.Resume -> "Resume"
        }

    private fun decodeSource(raw: String): WorkoutRunSource? =
        when (raw) {
            "Explore" -> WorkoutRunSource.Explore
            "Train" -> WorkoutRunSource.Train
            "Program" -> WorkoutRunSource.Program
            "Resume" -> WorkoutRunSource.Resume
            else -> null
        }

    private fun encodeReturn(target: ReturnTarget): String =
        when (target) {
            ReturnTarget.Train -> "Train"
            ReturnTarget.Explore -> "Explore"
            is ReturnTarget.WorkoutSession -> "WorkoutSession:${sanitize(target.workoutId)}"
            is ReturnTarget.ProgramDetail ->
                "ProgramDetail:${sanitize(target.programId)}:${target.weekNumber ?: -1}"
        }

    private fun decodeReturn(raw: String): ReturnTarget? =
        when {
            raw == "Train" -> ReturnTarget.Train
            raw == "Explore" -> ReturnTarget.Explore
            raw.startsWith("WorkoutSession:") ->
                ReturnTarget.WorkoutSession(raw.removePrefix("WorkoutSession:"))
            raw.startsWith("ProgramDetail:") -> {
                val rest = raw.removePrefix("ProgramDetail:")
                val weekSep = rest.lastIndexOf(':')
                if (weekSep <= 0) return null
                val programId = rest.substring(0, weekSep)
                val week = rest.substring(weekSep + 1).toIntOrNull()
                ReturnTarget.ProgramDetail(
                    programId = programId,
                    weekNumber = week?.takeIf { it >= 0 },
                )
            }
            else -> null
        }
}

fun WorkoutSessionUi.toRunSnapshot(): WorkoutRunSnapshot {
    val blocks = mutableListOf<WorkoutRunBlock>()
    sectionsForTraining().forEach { section ->
        section.items.forEach { item ->
            when (item) {
                is WorkoutSessionBlockUi.Exercise -> {
                    // No invented targets — missing reps/duration → Reps(0) so isStartable fails.
                    val target = when {
                        item.durationSeconds != null && item.reps == null ->
                            ExerciseTarget.Duration(item.durationSeconds.coerceAtLeast(0))
                        item.reps != null ->
                            ExerciseTarget.Reps(item.reps.coerceAtLeast(0))
                        item.durationSeconds != null ->
                            ExerciseTarget.Duration(item.durationSeconds.coerceAtLeast(0))
                        else ->
                            ExerciseTarget.Reps(0)
                    }
                    val restBetweenMs = item.restBetweenSetsMs.takeIf { it > 0 }
                        ?: item.restSeconds.coerceAtLeast(0) * 1_000L
                    val restAfterMs = item.restAfterExerciseMs.takeIf { it > 0 }
                        ?: item.restAfterExerciseSeconds.coerceAtLeast(0) * 1_000L
                    val weights = item.weightPerSetKg
                        ?: item.weightKg?.let { listOf(it) }
                    blocks += WorkoutRunBlock.Exercise(
                        exerciseId = item.id,
                        slug = item.exerciseSlug,
                        displayName = item.name,
                        phaseRole = item.phaseRole.ifBlank { section.phaseRole },
                        target = target,
                        sets = item.sets.coerceAtLeast(1),
                        restBetweenSetsMs = restBetweenMs,
                        restAfterExerciseMs = restAfterMs,
                        poseVariantIndex = item.variantIndex,
                        weightPerSetKg = weights,
                    )
                }
                is WorkoutSessionBlockUi.Rest -> {
                    blocks += WorkoutRunBlock.Rest(
                        durationMs = item.durationSeconds.coerceAtLeast(0) * 1_000L,
                    )
                }
            }
        }
    }
    return WorkoutRunSnapshot(
        workoutId = id,
        title = title,
        blocks = blocks,
    )
}

/**
 * Maps a run snapshot into engine flow items.
 * Explicit [WorkoutRunBlock.Rest] blocks fold into the preceding exercise's
 * [TrainingFlowItem.Exercise.restAfterExerciseMs] so the coordinator's between-exercise path stays single-rest.
 */
fun WorkoutRunSnapshot.toTrainingFlowItems(
    startExerciseIndex: Int = 0,
): List<TrainingFlowItem> {
    val flow = mutableListOf<TrainingFlowItem.Exercise>()
    var i = 0
    while (i < blocks.size) {
        when (val block = blocks[i]) {
            is WorkoutRunBlock.Exercise -> {
                var restAfter = block.restAfterExerciseMs
                if (i + 1 < blocks.size) {
                    val next = blocks[i + 1]
                    if (next is WorkoutRunBlock.Rest && next.durationMs > 0) {
                        restAfter = next.durationMs
                        i++
                    }
                }
                val (reps, durationSec) = when (val t = block.target) {
                    is ExerciseTarget.Reps -> t.count to null
                    is ExerciseTarget.Duration -> 0 to t.seconds
                }
                flow += TrainingFlowItem.Exercise(
                    slug = resolveRunExerciseSlug(block),
                    displayName = block.displayName,
                    sets = block.sets.coerceAtLeast(1),
                    targetReps = reps,
                    targetDurationSeconds = durationSec,
                    restBetweenSetsMs = block.restBetweenSetsMs.coerceAtLeast(0L),
                    restAfterExerciseMs = restAfter.coerceAtLeast(0L),
                    phaseRole = block.phaseRole,
                    poseVariantIndex = block.poseVariantIndex,
                    weightPerSetKg = block.weightPerSetKg,
                )
            }
            is WorkoutRunBlock.Rest -> Unit // orphan rest without a preceding exercise — skip
        }
        i++
    }
    return flow.drop(startExerciseIndex.coerceAtLeast(0))
}

private fun resolveRunExerciseSlug(block: WorkoutRunBlock.Exercise): String {
    if (com.movit.core.data.MovitData.isInstalled) {
        com.movit.core.data.MovitData.trainingConfig.resolveAvailableSlug(
            block.slug,
            block.exerciseId,
            normalizeTrainingSlug(block.slug),
            normalizeTrainingSlug(block.exerciseId),
        )?.let { return it }
    }
    return normalizeTrainingSlug(block.slug.ifBlank { block.exerciseId })
}

/**
 * Finish navigation for modern in-session workout flow.
 * Rest-between-exercise via prepare + [WorkoutRunProgressStore] is retired —
 * [com.movit.core.training.session.TrainingSessionFlowCoordinator] owns in-session rest.
 */
sealed interface WorkoutRunFinishNav {
    data object BackToSession : WorkoutRunFinishNav
    data object Complete : WorkoutRunFinishNav
}

fun resolveWorkoutRunFinish(
    workoutId: String?,
    isWorkoutFlowComplete: Boolean,
): WorkoutRunFinishNav {
    if (workoutId != null) {
        WorkoutRunStore.activeForWorkout(workoutId)?.let { active ->
            if (isWorkoutFlowComplete) {
                WorkoutRunStore.complete(active.runId.value)
            } else {
                WorkoutRunStore.abandon(active.runId.value)
            }
        }
    }
    return if (isWorkoutFlowComplete) {
        WorkoutRunFinishNav.Complete
    } else {
        WorkoutRunFinishNav.BackToSession
    }
}
