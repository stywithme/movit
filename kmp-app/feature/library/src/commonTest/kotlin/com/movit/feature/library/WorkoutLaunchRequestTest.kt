package com.movit.feature.library

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WorkoutLaunchRequestTest {
    @AfterTest
    fun tearDown() {
        WorkoutLaunchCoordinator.clearAll()
    }

    @Test
    fun exploreAndTrain_resolveDistinctSessionIds_sameRouteShape() {
        val explore = WorkoutLaunchCoordinator.fromExploreWorkout("workout-lower")
        val train = WorkoutLaunchCoordinator.fromTrainProgramDay(
            programId = "prog-1",
            weekNumber = 2,
            dayNumber = 3,
            plannedWorkoutId = "pw-lower",
        )
        assertEquals("workout-lower", WorkoutLaunchCoordinator.sessionWorkoutId(explore))
        assertEquals(
            WorkoutSessionKeys.encode("prog-1", 2, 3, "pw-lower"),
            WorkoutLaunchCoordinator.sessionWorkoutId(train),
        )
        assertEquals(ReturnTarget.Explore, explore.returnTarget)
        assertEquals(ReturnTarget.Train, train.returnTarget)
        assertEquals(LaunchSource.Explore, explore.source)
        assertEquals(LaunchSource.Train, train.source)
    }

    @Test
    fun remember_peek_preservesRequestForStart() {
        val request = WorkoutLaunchCoordinator.fromTrainProgramDay(
            programId = "prog-1",
            weekNumber = 1,
            dayNumber = 2,
            plannedWorkoutId = "pw-1",
        )
        val sessionId = WorkoutLaunchCoordinator.remember(request)
        val peeked = WorkoutLaunchCoordinator.peek(sessionId)
        assertEquals(request, peeked)
        assertEquals(WorkoutRunSource.Train, WorkoutLaunchCoordinator.toRunSource(request))
        assertEquals(ReturnTarget.Train, WorkoutLaunchCoordinator.doneTargetFor(request))
    }

    @Test
    fun beginFresh_isDistinctFromBeginOrResume() {
        val resume = WorkoutLaunchCoordinator.fromExploreWorkout(
            workoutId = "w1",
            requestedStart = RequestedStart.ResumeOnly,
        )
        val fresh = WorkoutLaunchCoordinator.fromExploreWorkout(
            workoutId = "w1",
            requestedStart = RequestedStart.BeginFresh,
        )
        assertEquals(RequestedStart.ResumeOnly, resume.requestedStart)
        assertEquals(RequestedStart.BeginFresh, fresh.requestedStart)
        assertEquals(
            WorkoutLaunchCoordinator.sessionWorkoutId(resume),
            WorkoutLaunchCoordinator.sessionWorkoutId(fresh),
        )
    }

    @Test
    fun fromSessionKey_parsesProgramSession() {
        val key = WorkoutSessionKeys.encode("prog-x", 4, 5, "pw-z")
        val request = WorkoutLaunchCoordinator.fromSessionKey(
            sessionWorkoutId = key,
            source = LaunchSource.Program,
            returnTarget = ReturnTarget.ProgramDetail("prog-x", 4),
        )
        assertIs<WorkoutRef.ProgramSession>(request.workoutRef)
        assertEquals(key, WorkoutLaunchCoordinator.sessionWorkoutId(request))
        assertNull(WorkoutLaunchCoordinator.peek("missing"))
    }
}
