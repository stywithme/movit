import type { PrismaClient } from '@prisma/client';

export async function clearDatabase(prisma: PrismaClient) {
  console.log('🧹 Clearing existing data...');

  const safeDelete = async (name: string, action: () => Promise<unknown>) => {
    try {
      await action();
    } catch (error: any) {
      if (error?.code === 'P2021') {
        console.warn(`⚠️ Skip missing table: ${name}`);
        return;
      }
      throw error;
    }
  };

  const steps: Array<{ name: string; action: () => Promise<unknown> }> = [
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
    { name: 'program_session_reports', action: () => prisma.programSessionReport.deleteMany() },
    { name: 'user_program_progress', action: () => prisma.userProgramProgress.deleteMany() },
    { name: 'user_programs', action: () => prisma.userProgram.deleteMany() },
    { name: 'program_session_items', action: () => prisma.programSessionItem.deleteMany() },
    { name: 'program_sessions', action: () => prisma.programSession.deleteMany() },
    { name: 'program_days', action: () => prisma.programDay.deleteMany() },
    { name: 'program_weeks', action: () => prisma.programWeek.deleteMany() },
    { name: 'programs', action: () => prisma.program.deleteMany() },
    { name: 'rep_metrics', action: () => prisma.repMetrics.deleteMany() },
    { name: 'session_metrics', action: () => prisma.sessionMetrics.deleteMany() },
    { name: 'training_sessions', action: () => prisma.trainingSession.deleteMany() },
    { name: 'refresh_tokens', action: () => prisma.refreshToken.deleteMany() },
    { name: 'workout_exercises', action: () => prisma.workoutExercise.deleteMany() },
    { name: 'workouts', action: () => prisma.workout.deleteMany() },
    { name: 'feedback_message_assignments', action: () => prisma.feedbackMessageAssignment.deleteMany() },
    { name: 'feedback_message_templates', action: () => prisma.feedbackMessageTemplate.deleteMany() },
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
  ];

  for (const step of steps) {
    await safeDelete(step.name, step.action);
  }

  console.log('✅ Database cleared');
}
