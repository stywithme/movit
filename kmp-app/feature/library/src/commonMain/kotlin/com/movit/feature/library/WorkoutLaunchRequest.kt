package com.movit.feature.library

/**
 * Single launch contract for Explore / Train / Program → [WorkoutSession] path.
 * Entry points convert user intent into this request; they do not invent alternate flows.
 */
data class WorkoutLaunchRequest(
    val source: LaunchSource,
    val workoutRef: WorkoutRef,
    val returnTarget: ReturnTarget,
    val requestedStart: RequestedStart = RequestedStart.BeginOrResume,
)

enum class LaunchSource {
    Explore,
    Train,
    Program,
    Resume,
}

sealed interface WorkoutRef {
    /** Catalog / template workout id (Explore). */
    data class Template(val workoutId: String) : WorkoutRef

    /** Program day session key components (Train / Program Detail). */
    data class ProgramSession(
        val programId: String,
        val weekNumber: Int,
        val dayNumber: Int,
        val plannedWorkoutId: String,
    ) : WorkoutRef
}

enum class RequestedStart {
    /** Prefer Resume CTA when an open run exists; otherwise Start. */
    BeginOrResume,

    /** Abandon any open run and start fresh. */
    BeginFresh,

    /** Only meaningful when an open run exists (Resume CTA). */
    ResumeOnly,
}

/**
 * Resolves session identity and remembers the last request per workout id so Start
 * can reuse source / return targets. Preflight stays inside WorkoutSessionViewModel.
 */
object WorkoutLaunchCoordinator {
    private val pendingBySessionId = mutableMapOf<String, WorkoutLaunchRequest>()

    fun fromExploreWorkout(
        workoutId: String,
        requestedStart: RequestedStart = RequestedStart.BeginOrResume,
    ): WorkoutLaunchRequest =
        WorkoutLaunchRequest(
            source = LaunchSource.Explore,
            workoutRef = WorkoutRef.Template(workoutId),
            returnTarget = ReturnTarget.Explore,
            requestedStart = requestedStart,
        )

    fun fromTrainProgramDay(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        plannedWorkoutId: String,
        requestedStart: RequestedStart = RequestedStart.BeginOrResume,
    ): WorkoutLaunchRequest =
        WorkoutLaunchRequest(
            source = LaunchSource.Train,
            workoutRef = WorkoutRef.ProgramSession(
                programId = programId,
                weekNumber = weekNumber,
                dayNumber = dayNumber,
                plannedWorkoutId = plannedWorkoutId,
            ),
            returnTarget = ReturnTarget.Train,
            requestedStart = requestedStart,
        )

    fun fromProgramDetail(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        plannedWorkoutId: String,
        requestedStart: RequestedStart = RequestedStart.BeginOrResume,
    ): WorkoutLaunchRequest =
        WorkoutLaunchRequest(
            source = LaunchSource.Program,
            workoutRef = WorkoutRef.ProgramSession(
                programId = programId,
                weekNumber = weekNumber,
                dayNumber = dayNumber,
                plannedWorkoutId = plannedWorkoutId,
            ),
            returnTarget = ReturnTarget.ProgramDetail(programId, weekNumber),
            requestedStart = requestedStart,
        )

    fun fromSessionKey(
        sessionWorkoutId: String,
        source: LaunchSource,
        returnTarget: ReturnTarget,
        requestedStart: RequestedStart = RequestedStart.BeginOrResume,
    ): WorkoutLaunchRequest {
        val parsed = WorkoutSessionKeys.parse(sessionWorkoutId)
        val ref = if (parsed != null) {
            WorkoutRef.ProgramSession(
                programId = parsed.programId,
                weekNumber = parsed.weekNumber,
                dayNumber = parsed.dayNumber,
                plannedWorkoutId = parsed.plannedWorkoutId,
            )
        } else {
            WorkoutRef.Template(sessionWorkoutId)
        }
        return WorkoutLaunchRequest(
            source = source,
            workoutRef = ref,
            returnTarget = returnTarget,
            requestedStart = requestedStart,
        )
    }

    fun sessionWorkoutId(request: WorkoutLaunchRequest): String =
        when (val ref = request.workoutRef) {
            is WorkoutRef.Template -> ref.workoutId
            is WorkoutRef.ProgramSession ->
                WorkoutSessionKeys.encode(
                    programId = ref.programId,
                    weekNumber = ref.weekNumber,
                    dayNumber = ref.dayNumber,
                    plannedWorkoutId = ref.plannedWorkoutId,
                )
        }

    /** Remember request for the resolved session id (used at Start for source/return). */
    fun remember(request: WorkoutLaunchRequest): String {
        val id = sessionWorkoutId(request)
        pendingBySessionId[id] = request
        return id
    }

    fun peek(sessionWorkoutId: String): WorkoutLaunchRequest? =
        pendingBySessionId[sessionWorkoutId]

    fun clear(sessionWorkoutId: String) {
        pendingBySessionId.remove(sessionWorkoutId)
    }

    internal fun clearAll() {
        pendingBySessionId.clear()
    }

    fun toRunSource(request: WorkoutLaunchRequest): WorkoutRunSource =
        when (request.source) {
            LaunchSource.Explore -> WorkoutRunSource.Explore
            LaunchSource.Train -> WorkoutRunSource.Train
            LaunchSource.Program -> WorkoutRunSource.Program
            LaunchSource.Resume -> WorkoutRunSource.Resume
        }

    fun doneTargetFor(request: WorkoutLaunchRequest): ReturnTarget =
        when (val target = request.returnTarget) {
            ReturnTarget.Train,
            is ReturnTarget.ProgramDetail,
            -> target
            ReturnTarget.Explore,
            is ReturnTarget.WorkoutSession,
            -> ReturnTarget.Explore
        }
}
