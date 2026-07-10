package com.movit.feature.library

import com.movit.core.network.dto.WorkoutTemplateEmbeddedExerciseDto
import com.movit.core.network.dto.WorkoutTemplateExerciseDto
import com.movit.core.network.dto.WorkoutTemplatePhaseDto
import com.movit.core.network.dto.WorkoutTemplateTrainingConfigDto
import com.movit.core.training.session.TrainingFlowItem
import com.movit.resources.strings.SessionStrings
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorkoutRunSnapshotMappingTest {

    private val strings = runBlocking { SessionStrings.load("en") }

    @AfterTest
    fun tearDown() {
        WorkoutRunStore.clearAll()
        WorkoutFlowCache.clearAll()
    }

    @Test
    fun dtoPhases_preserveWarmupMainCooldownRestVariantDurationWeight() {
        val config = WorkoutTemplateTrainingConfigDto(
            id = "wt-1",
            slug = "full-body",
            name = mapOf("en" to "Full Body"),
            phases = listOf(
                WorkoutTemplatePhaseDto(
                    id = "p-warm",
                    role = "WARMUP",
                    name = mapOf("en" to "Warm-up"),
                    sortOrder = 0,
                    exercises = listOf(
                        WorkoutTemplateExerciseDto(
                            workoutExerciseId = "we-hold",
                            sortOrder = 1,
                            variantIndex = 2,
                            sets = 1,
                            targetDuration = 30,
                            restBetweenSetsMs = 15_000,
                            restAfterExerciseMs = 45_000,
                            weightPerSet = listOf(5.0),
                            exercise = WorkoutTemplateEmbeddedExerciseDto(
                                slug = "plank",
                                name = mapOf("en" to "Plank"),
                            ),
                        ),
                    ),
                ),
                WorkoutTemplatePhaseDto(
                    id = "p-main",
                    role = "MAIN",
                    name = mapOf("en" to "Main"),
                    sortOrder = 1,
                    exercises = listOf(
                        WorkoutTemplateExerciseDto(
                            workoutExerciseId = "we-squat",
                            sortOrder = 1,
                            variantIndex = 1,
                            sets = 3,
                            targetReps = 10,
                            restBetweenSetsMs = 60_000,
                            restAfterExerciseMs = 90_000,
                            weightPerSet = listOf(40.0, 42.5, 45.0),
                            exercise = WorkoutTemplateEmbeddedExerciseDto(
                                slug = "barbell-squat",
                                name = mapOf("en" to "Squat"),
                            ),
                        ),
                    ),
                ),
                WorkoutTemplatePhaseDto(
                    id = "p-cool",
                    role = "COOLDOWN",
                    name = mapOf("en" to "Cool-down"),
                    sortOrder = 2,
                    exercises = listOf(
                        WorkoutTemplateExerciseDto(
                            workoutExerciseId = "we-stretch",
                            sortOrder = 1,
                            sets = 1,
                            targetDuration = 45,
                            restBetweenSetsMs = 0,
                            restAfterExerciseMs = 0,
                            exercise = WorkoutTemplateEmbeddedExerciseDto(
                                slug = "quad-stretch",
                                name = mapOf("en" to "Quad Stretch"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val session = WorkoutTemplateSessionMapper.mapSession(
            slugOrId = "full-body",
            config = config,
            templateMeta = null,
            language = "en",
            strings = strings,
            exerciseBySlug = emptyMap(),
        )
        val snapshot = session.toRunSnapshot()
        assertTrue(snapshot.isStartable)

        val blocks = snapshot.blocks
        val warm = blocks[0] as WorkoutRunBlock.Exercise
        assertEquals("WARMUP", warm.phaseRole)
        assertEquals(ExerciseTarget.Duration(30), warm.target)
        assertEquals(2, warm.poseVariantIndex)
        assertEquals(15_000L, warm.restBetweenSetsMs)
        assertEquals(45_000L, warm.restAfterExerciseMs)
        assertEquals(listOf(5f), warm.weightPerSetKg)

        val warmRest = blocks[1] as WorkoutRunBlock.Rest
        assertEquals(45_000L, warmRest.durationMs)

        val main = blocks[2] as WorkoutRunBlock.Exercise
        assertEquals("MAIN", main.phaseRole)
        assertEquals(ExerciseTarget.Reps(10), main.target)
        assertEquals(1, main.poseVariantIndex)
        assertEquals(60_000L, main.restBetweenSetsMs)
        assertEquals(90_000L, main.restAfterExerciseMs)
        assertNotEquals(main.restBetweenSetsMs, main.restAfterExerciseMs)
        assertEquals(listOf(40f, 42.5f, 45f), main.weightPerSetKg)

        val mainRest = blocks[3] as WorkoutRunBlock.Rest
        assertEquals(90_000L, mainRest.durationMs)

        val cool = blocks[4] as WorkoutRunBlock.Exercise
        assertEquals("COOLDOWN", cool.phaseRole)
        assertEquals(ExerciseTarget.Duration(45), cool.target)

        val flow = snapshot.toTrainingFlowItems()
        assertEquals(3, flow.size)
        val hold = flow[0] as TrainingFlowItem.Exercise
        assertEquals(0, hold.targetReps)
        assertEquals(30, hold.targetDurationSeconds)
        assertEquals(15_000L, hold.restBetweenSetsMs)
        assertEquals(45_000L, hold.restAfterExerciseMs)
        assertEquals(2, hold.poseVariantIndex)

        val squat = flow[1] as TrainingFlowItem.Exercise
        assertEquals(10, squat.targetReps)
        assertEquals(null, squat.targetDurationSeconds)
        assertEquals(60_000L, squat.restBetweenSetsMs)
        assertEquals(90_000L, squat.restAfterExerciseMs)
        assertEquals(listOf(40f, 42.5f, 45f), squat.weightPerSetKg)
    }

    @Test
    fun timeBasedExercise_doesNotDefaultToTwelveReps() {
        val session = WorkoutSessionUi(
            id = "w-time",
            title = "Hold",
            subtitle = "",
            exerciseCount = 1,
            durationLabel = "~1m",
            setCount = 1,
            sections = listOf(
                WorkoutSessionSectionUi(
                    title = "Main",
                    phaseRole = "MAIN",
                    items = listOf(
                        WorkoutSessionBlockUi.Exercise(
                            id = "ex-1",
                            exerciseSlug = "plank",
                            index = 1,
                            name = "Plank",
                            category = "",
                            sets = 1,
                            reps = null,
                            durationSeconds = 40,
                            restSeconds = 20,
                            setsLabel = "1 × 40s",
                            restLabel = "20s rest",
                            phaseRole = "MAIN",
                        ),
                    ),
                ),
            ),
        )
        val item = session.toRunSnapshot().toTrainingFlowItems().single() as TrainingFlowItem.Exercise
        assertEquals(0, item.targetReps)
        assertEquals(40, item.targetDurationSeconds)
    }

    @Test
    fun flowConfig_missingReps_doesNotInventTwelveReps() {
        val config = WorkoutFlowConfigUi(
            workoutId = "w-flow-bad",
            title = "Broken",
            subtitle = "",
            exercises = listOf(
                WorkoutFlowExerciseUi(
                    id = "ex-1",
                    exerciseSlug = "mystery",
                    name = "Mystery",
                    sets = 3,
                    reps = null,
                    durationSeconds = null,
                ),
            ),
        )
        val item = config.toTrainingFlowItems().single() as TrainingFlowItem.Exercise
        assertEquals(0, item.targetReps)
        assertNotEquals(12, item.targetReps)
        assertEquals(null, item.targetDurationSeconds)
    }

    @Test
    fun unknownTarget_isNotStartable_andDoesNotInventTwelveReps() {
        val session = WorkoutSessionUi(
            id = "w-bad",
            title = "Broken",
            subtitle = "",
            exerciseCount = 1,
            durationLabel = "",
            setCount = 1,
            sections = listOf(
                WorkoutSessionSectionUi(
                    title = "Main",
                    phaseRole = "MAIN",
                    items = listOf(
                        WorkoutSessionBlockUi.Exercise(
                            id = "ex-1",
                            exerciseSlug = "mystery",
                            index = 1,
                            name = "Mystery",
                            category = "",
                            sets = 3,
                            reps = null,
                            durationSeconds = null,
                            restSeconds = 60,
                            setsLabel = "3 × ?",
                            restLabel = "60s rest",
                            phaseRole = "MAIN",
                        ),
                    ),
                ),
            ),
        )
        val snapshot = session.toRunSnapshot()
        val target = snapshot.exercises.single().target
        assertIs<ExerciseTarget.Reps>(target)
        assertEquals(0, target.count)
        assertNotEquals(12, target.count)
        assertTrue(!snapshot.isStartable)
    }

    @Test
    fun repeatRuns_produceDistinctRunIdsAndGroupIds() {
        val snapshot = WorkoutSessionPreviewData.preview.toRunSnapshot()
        val first = WorkoutRunStore.start(
            workoutId = "same-workout",
            snapshot = snapshot,
            source = WorkoutRunSource.Explore,
        )
        WorkoutRunStore.complete(first.runId.value)
        val second = WorkoutRunStore.start(
            workoutId = "same-workout",
            snapshot = snapshot,
            source = WorkoutRunSource.Explore,
        )
        assertNotEquals(first.runId, second.runId)
        assertNotEquals(first.workoutGroupId, second.workoutGroupId)
        assertEquals(second.runId.value, second.workoutGroupId)
    }

    @Test
    fun previewData_restBlockFoldsIntoPrecedingExerciseRestAfter() {
        val snapshot = WorkoutSessionPreviewData.preview.toRunSnapshot()
        assertTrue(snapshot.blocks.any { it is WorkoutRunBlock.Rest })
        val flow = snapshot.toTrainingFlowItems()
        val lungeIndex = flow.indexOfFirst {
            it is TrainingFlowItem.Exercise && it.slug == "walking-lunge"
        }
        assertTrue(lungeIndex > 0)
        val beforeLunge = flow[lungeIndex - 1] as TrainingFlowItem.Exercise
        // Rest block (90s) between RDL and Lunge folds into RDL restAfter.
        assertEquals(90_000L, beforeLunge.restAfterExerciseMs)
    }
}
