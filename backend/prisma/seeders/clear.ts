import type { PrismaClient } from '@prisma/client';

export type ClearDatabaseOptions = {
  /**
   * When true, `feedback_message_templates` rows are not deleted (keeps recorded TTS / audio URLs).
   * Assignments are still cleared so exercises can be re-linked cleanly.
   */
  preserveMessageTemplates: boolean;
};

export async function clearDatabase(prisma: PrismaClient, options: ClearDatabaseOptions) {
  const { preserveMessageTemplates } = options;
  console.log(
    preserveMessageTemplates
      ? '🧹 Clearing data (preserving feedback_message_templates for audio/TTS)'
      : '🧹 Clearing all data including feedback_message_templates',
  );

  const safeDelete = async (name: string, action: () => Promise<unknown>) => {
    try {
      await action();
    } catch (error: unknown) {
      const err = error as { code?: string };
      if (err?.code === 'P2021') {
        console.warn(`⚠️ Skip missing table: ${name}`);
        return;
      }
      throw error;
    }
  };

  const steps: Array<{ name: string; action: () => Promise<unknown> }> = [
    { name: 'booking_reports', action: () => prisma.bookingReport.deleteMany() },
    { name: 'bookings', action: () => prisma.booking.deleteMany() },
    { name: 'doctor_work_times', action: () => prisma.doctorWorkTime.deleteMany() },
    { name: 'close_times', action: () => prisma.closeTime.deleteMany() },
    { name: 'assessment_template_exercises', action: () => prisma.assessmentTemplateExercise.deleteMany() },
    { name: 'assessment_templates', action: () => prisma.assessmentTemplate.deleteMany() },
    { name: 'progression_history', action: () => prisma.progressionHistory.deleteMany() },
    { name: 'progression_rules', action: () => prisma.progressionRule.deleteMany() },
    { name: 'reassessment_schedules', action: () => prisma.reassessmentSchedule.deleteMany() },
    { name: 'active_plan_programs', action: () => prisma.activePlanProgram.deleteMany() },
    { name: 'active_plans', action: () => prisma.activePlan.deleteMany() },
    { name: 'user_level_profiles', action: () => prisma.userLevelProfile.deleteMany() },
    { name: 'body_scan_results', action: () => prisma.bodyScanResult.deleteMany() },
    { name: 'levels', action: () => prisma.level.deleteMany() },
    { name: 'planned_workout_reports', action: () => prisma.plannedWorkoutReport.deleteMany() },
    { name: 'user_program_progress', action: () => prisma.userProgramProgress.deleteMany() },
    { name: 'user_programs', action: () => prisma.userProgram.deleteMany() },
    { name: 'planned_workout_items', action: () => prisma.plannedWorkoutItem.deleteMany() },
    { name: 'planned_workouts', action: () => prisma.plannedWorkout.deleteMany() },
    { name: 'program_days', action: () => prisma.programDay.deleteMany() },
    { name: 'program_weeks', action: () => prisma.programWeek.deleteMany() },
    { name: 'programs', action: () => prisma.program.deleteMany() },
    { name: 'rep_metrics', action: () => prisma.repMetrics.deleteMany() },
    { name: 'workout_execution_metrics', action: () => prisma.workoutExecutionMetrics.deleteMany() },
    { name: 'workout_executions', action: () => prisma.workoutExecution.deleteMany() },
    { name: 'refresh_tokens', action: () => prisma.refreshToken.deleteMany() },
    { name: 'workout_template_exercises', action: () => prisma.workoutTemplateExercise.deleteMany() },
    { name: 'workout_templates', action: () => prisma.workoutTemplate.deleteMany() },
    { name: 'feedback_message_assignments', action: () => prisma.feedbackMessageAssignment.deleteMany() },
    ...(preserveMessageTemplates
      ? []
      : [{ name: 'feedback_message_templates', action: () => prisma.feedbackMessageTemplate.deleteMany() }]),
    { name: 'position_checks', action: () => prisma.positionCheck.deleteMany() },
    { name: 'pose_variants', action: () => prisma.poseVariant.deleteMany() },
    { name: 'exercise_media', action: () => prisma.exerciseMedia.deleteMany() },
    { name: 'exercise_attributes', action: () => prisma.exerciseAttribute.deleteMany() },
    { name: 'exercises', action: () => prisma.exercise.deleteMany() },
    { name: 'pose_position_joints', action: () => prisma.posePositionJoint.deleteMany() },
    { name: 'pose_positions', action: () => prisma.posePosition.deleteMany() },
    { name: 'difficulty_levels', action: () => prisma.difficultyLevel.deleteMany() },
    { name: 'attribute_values', action: () => prisma.attributeValue.deleteMany() },
    { name: 'attributes', action: () => prisma.attribute.deleteMany() },
    { name: 'users', action: () => prisma.user.deleteMany() },
    { name: 'admins', action: () => prisma.admin.deleteMany() },
    { name: 'system', action: () => prisma.system.deleteMany() },
  ];

  for (const step of steps) {
    await safeDelete(step.name, step.action);
  }

  console.log('✅ Database cleared');
}
