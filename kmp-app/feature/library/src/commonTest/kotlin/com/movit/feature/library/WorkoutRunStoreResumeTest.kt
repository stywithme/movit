package com.movit.feature.library

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutRunStoreResumeTest {
    @AfterTest
    fun tearDown() {
        WorkoutRunStore.clearAll()
    }

    @Test
    fun saveProgress_updatesOpenStateCursor() {
        val snapshot = WorkoutRunSnapshot(
            workoutId = "w1",
            title = "Test",
            blocks = listOf(
                WorkoutRunBlock.Exercise(
                    exerciseId = "e1",
                    slug = "squat",
                    displayName = "Squat",
                    phaseRole = "MAIN",
                    target = ExerciseTarget.Reps(10),
                    sets = 3,
                    restBetweenSetsMs = 30_000L,
                    restAfterExerciseMs = 60_000L,
                    poseVariantIndex = 0,
                    weightPerSetKg = null,
                ),
            ),
        )
        val started = WorkoutRunStore.start(workoutId = "w1", snapshot = snapshot)
        WorkoutRunStore.saveProgress(
            runId = started.runId.value,
            exerciseIndex = 0,
            currentSet = 2,
            exerciseSlug = "squat",
            blockPhase = "TRAINING",
        )
        val open = WorkoutRunStore.openStateForWorkout("w1")
        assertNotNull(open)
        assertEquals(2, open.currentSet)
        assertEquals("TRAINING", open.blockPhase)
        assertTrue(WorkoutRunStore.hasOpenRun("w1"))
    }

    @Test
    fun abandon_clearsOpenRun() {
        val snapshot = WorkoutRunSnapshot(
            workoutId = "w2",
            title = "Test",
            blocks = listOf(
                WorkoutRunBlock.Exercise(
                    exerciseId = "e1",
                    slug = "squat",
                    displayName = "Squat",
                    phaseRole = "MAIN",
                    target = ExerciseTarget.Reps(10),
                    sets = 1,
                    restBetweenSetsMs = 0L,
                    restAfterExerciseMs = 0L,
                    poseVariantIndex = 0,
                    weightPerSetKg = null,
                ),
            ),
        )
        val started = WorkoutRunStore.start(workoutId = "w2", snapshot = snapshot)
        WorkoutRunStore.abandon(started.runId.value)
        assertNull(WorkoutRunStore.openStateForWorkout("w2"))
    }

    @Test
    fun durablePayload_roundTrips() {
        val encoded = DurableWorkoutRunProgress(
            runId = "run-1",
            workoutId = "w3",
            exerciseIndex = 1,
            currentSet = 2,
            blockPhase = "REST",
            exerciseSlug = "lunge",
            status = WorkoutRunStatus.Active.name,
            updatedAtEpochMs = 42L,
        ).encode()
        val decoded = DurableWorkoutRunProgress.decode(encoded)
        assertNotNull(decoded)
        assertEquals("run-1", decoded.runId)
        assertEquals(1, decoded.exerciseIndex)
        assertEquals(2, decoded.currentSet)
        assertEquals("REST", decoded.blockPhase)
    }

    @Test
    fun durableSnapshot_roundTripsAndRebuildsRecord() {
        val snapshot = WorkoutRunSnapshot(
            workoutId = "w-snap",
            title = "Lower",
            blocks = listOf(
                WorkoutRunBlock.Exercise(
                    exerciseId = "e1",
                    slug = "squat",
                    displayName = "Squat",
                    phaseRole = "MAIN",
                    target = ExerciseTarget.Reps(8),
                    sets = 3,
                    restBetweenSetsMs = 45_000L,
                    restAfterExerciseMs = 90_000L,
                    poseVariantIndex = 1,
                    weightPerSetKg = listOf(40f, 42.5f),
                ),
                WorkoutRunBlock.Rest(durationMs = 60_000L),
                WorkoutRunBlock.Exercise(
                    exerciseId = "e2",
                    slug = "plank",
                    displayName = "Plank",
                    phaseRole = "MAIN",
                    target = ExerciseTarget.Duration(30),
                    sets = 1,
                    restBetweenSetsMs = 0L,
                    restAfterExerciseMs = 0L,
                    poseVariantIndex = 0,
                    weightPerSetKg = null,
                ),
            ),
            version = 99L,
        )
        val encoded = DurableWorkoutRunSnapshotCodec.encode(
            snapshot = snapshot,
            source = WorkoutRunSource.Train,
            returnTarget = ReturnTarget.Train,
            doneTarget = ReturnTarget.Train,
        )
        val decoded = DurableWorkoutRunSnapshotCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(snapshot.title, decoded.snapshot.title)
        assertEquals(snapshot.version, decoded.snapshot.version)
        assertEquals(3, decoded.snapshot.blocks.size)
        assertEquals(ExerciseTarget.Reps(8), (decoded.snapshot.blocks[0] as WorkoutRunBlock.Exercise).target)
        assertEquals(listOf(40f, 42.5f), (decoded.snapshot.blocks[0] as WorkoutRunBlock.Exercise).weightPerSetKg)
        assertEquals(60_000L, (decoded.snapshot.blocks[1] as WorkoutRunBlock.Rest).durationMs)
        assertEquals(ExerciseTarget.Duration(30), (decoded.snapshot.blocks[2] as WorkoutRunBlock.Exercise).target)
        assertEquals(WorkoutRunSource.Train, decoded.source)
        assertEquals(ReturnTarget.Train, decoded.returnTarget)

        val rebuilt = rebuildRecordFromDurable(
            durable = DurableWorkoutRunProgress(
                runId = "run-snap",
                workoutId = "w-snap",
                exerciseIndex = 1,
                currentSet = 1,
                blockPhase = "REST",
                exerciseSlug = "plank",
                status = WorkoutRunStatus.Active.name,
                updatedAtEpochMs = 7L,
            ),
            decodedSnap = decoded,
        )
        assertEquals("run-snap", rebuilt.runId.value)
        assertTrue(rebuilt.snapshot.isStartable)
        assertEquals(1, rebuilt.progress.exerciseIndex)
        assertEquals("plank", rebuilt.progress.exerciseSlug)
        assertEquals(ReturnTarget.Train, rebuilt.returnTarget)
        assertEquals(3, rebuilt.snapshot.blocks.size)
    }

    @Test
    fun rebuildWithoutSnapshot_keepsCursorOnlyStub() {
        val rebuilt = rebuildRecordFromDurable(
            durable = DurableWorkoutRunProgress(
                runId = "run-legacy",
                workoutId = "w-legacy",
                exerciseIndex = 0,
                currentSet = 2,
                blockPhase = "TRAINING",
                exerciseSlug = "squat",
            ),
            decodedSnap = null,
        )
        assertTrue(rebuilt.snapshot.blocks.isEmpty())
        assertEquals(2, rebuilt.progress.currentSet)
        assertEquals(WorkoutRunSource.Resume, rebuilt.source)
    }

    @Test
    fun foreignOwnerRun_isNotVisibleAsOpenRun() {
        val snapshot = WorkoutRunSnapshot(
            workoutId = "w-owner",
            title = "Test",
            blocks = listOf(
                WorkoutRunBlock.Exercise(
                    exerciseId = "e1",
                    slug = "squat",
                    displayName = "Squat",
                    phaseRole = "MAIN",
                    target = ExerciseTarget.Reps(10),
                    sets = 1,
                    restBetweenSetsMs = 0L,
                    restAfterExerciseMs = 0L,
                    poseVariantIndex = 0,
                    weightPerSetKg = null,
                ),
            ),
        )
        WorkoutRunStore.start(
            workoutId = "w-owner",
            snapshot = snapshot,
            ownerUserId = "user-a",
        )
        // Without MovitData the current user is null — stamped foreign owner stays hidden.
        assertNull(WorkoutRunStore.openStateForWorkout("w-owner"))
        assertNull(WorkoutRunStore.activeForWorkout("w-owner"))
    }

    @Test
    fun clearAll_viaBridge_dropsOpenRuns() {
        val snapshot = WorkoutRunSnapshot(
            workoutId = "w-clear",
            title = "Test",
            blocks = listOf(
                WorkoutRunBlock.Exercise(
                    exerciseId = "e1",
                    slug = "squat",
                    displayName = "Squat",
                    phaseRole = "MAIN",
                    target = ExerciseTarget.Reps(10),
                    sets = 1,
                    restBetweenSetsMs = 0L,
                    restAfterExerciseMs = 0L,
                    poseVariantIndex = 0,
                    weightPerSetKg = null,
                ),
            ),
        )
        WorkoutRunStore.start(workoutId = "w-clear", snapshot = snapshot)
        assertNotNull(WorkoutRunStore.openStateForWorkout("w-clear"))
        com.movit.core.data.outbox.WorkoutRunStoreBridge.clearMemoryIfRegistered()
        assertNull(WorkoutRunStore.openStateForWorkout("w-clear"))
    }

    @Test
    fun saveAccumulatedReport_survivesInMemoryAcrossStartSameRunId() {
        val snapshot = WorkoutRunSnapshot(
            workoutId = "w-accum",
            title = "Test",
            blocks = listOf(
                WorkoutRunBlock.Exercise(
                    exerciseId = "e1",
                    slug = "squat",
                    displayName = "Squat",
                    phaseRole = "MAIN",
                    target = ExerciseTarget.Reps(10),
                    sets = 2,
                    restBetweenSetsMs = 30_000L,
                    restAfterExerciseMs = 60_000L,
                    poseVariantIndex = 0,
                    weightPerSetKg = null,
                ),
            ),
        )
        val started = WorkoutRunStore.start(workoutId = "w-accum", snapshot = snapshot)
        val report = com.movit.core.training.report.MovitSessionReport(
            totalExercises = 1,
            totalSetsCompleted = 1,
            totalSetsPlanned = 2,
            totalReps = 10,
            totalDurationMs = 60_000L,
            averageAccuracy = 90f,
            averageFormScore = 85f,
            exerciseReports = listOf(
                com.movit.core.training.report.MovitExerciseSessionReport(
                    exerciseSlug = "squat",
                    exerciseName = "Squat",
                    setsCompleted = 1,
                    totalSets = 2,
                    totalReps = 10,
                    averageAccuracy = 90f,
                    averageFormScore = 85f,
                ),
            ),
            executionIds = listOf("exec-1"),
        )
        WorkoutRunStore.saveAccumulatedReport(started.runId.value, report)
        assertEquals(10, WorkoutRunStore.getAccumulatedReport(started.runId.value)?.totalReps)

        // Resume re-start with same runId must keep prior work.
        WorkoutRunStore.start(
            workoutId = "w-accum",
            snapshot = snapshot,
            runId = started.runId,
            progress = WorkoutRunProgressCursor(exerciseIndex = 0, currentSet = 2, exerciseSlug = "squat"),
            source = WorkoutRunSource.Resume,
        )
        assertEquals(10, WorkoutRunStore.getAccumulatedReport(started.runId.value)?.totalReps)
        assertEquals(1, WorkoutRunStore.get(started.runId)?.accumulatedReport?.totalExercises)
    }
}
