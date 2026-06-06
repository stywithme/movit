/**
 * Workout domain contract — single source of truth for naming after Session removal.
 *
 * | Concept              | Prisma model              | DB table                    |
 * |----------------------|---------------------------|-----------------------------|
 * | Catalog template     | WorkoutTemplate           | workout_templates           |
 * | Template exercise    | WorkoutTemplateExercise   | workout_template_exercises  |
 * | Planned program block| PlannedWorkout            | planned_workouts            |
 * | Planned line item    | PlannedWorkoutItem        | planned_workout_items       |
 * | Program completion   | PlannedWorkoutReport      | planned_workout_reports     |
 * | Exercise execution   | WorkoutExecution          | workout_executions          |
 * | Execution metrics    | WorkoutExecutionMetrics   | workout_execution_metrics   |
 *
 * API paths:
 * - /workout-templates, /mobile/workout-templates — catalog CRUD & sync
 * - /planned-workouts/:id/start|complete|report — program block lifecycle
 * - /workout-executions, /mobile/workout-executions — per-exercise runs & history
 */

export const WORKOUT_DOMAIN = {
  models: {
    template: 'WorkoutTemplate',
    templateExercise: 'WorkoutTemplateExercise',
    planned: 'PlannedWorkout',
    plannedItem: 'PlannedWorkoutItem',
    plannedReport: 'PlannedWorkoutReport',
    execution: 'WorkoutExecution',
    executionMetrics: 'WorkoutExecutionMetrics',
  },
  tables: {
    template: 'workout_templates',
    templateExercise: 'workout_template_exercises',
    planned: 'planned_workouts',
    plannedItem: 'planned_workout_items',
    plannedReport: 'planned_workout_reports',
    execution: 'workout_executions',
    executionMetrics: 'workout_execution_metrics',
  },
  api: {
    templates: '/workout-templates',
    mobileTemplates: '/mobile/workout-templates',
    plannedWorkouts: '/planned-workouts',
    mobilePlannedWorkouts: '/mobile/planned-workouts',
    executions: '/workout-executions',
    mobileExecutions: '/mobile/workout-executions',
  },
  stats: {
    totalExecutions: 'totalWorkoutExecutions',
    weeklyPlanned: 'weeklyPlannedWorkouts',
    thisWeekExecutions: 'thisWeekExecutions',
    totalTemplates: 'totalWorkoutTemplates',
  },
  sync: {
    workoutTemplates: 'workoutTemplates',
    deletedWorkoutTemplateIds: 'deletedWorkoutTemplateIds',
    workoutTemplatesInResponse: 'workoutTemplatesInResponse',
  },
  reports: {
    workoutsCompleted: 'workoutsCompleted',
    workoutsPlanned: 'workoutsPlanned',
    workoutRating: 'workoutRating',
    workoutsCount: 'workoutsCount',
    workoutTimeline: 'workoutTimeline',
    mostRepsInWorkout: 'mostRepsInWorkout',
    executionQuality: 'executionQuality',
  },
} as const;
