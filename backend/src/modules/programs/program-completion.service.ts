import { assessmentMatchingService } from '@/modules/assessments/assessment-matching.service';
import { getPrisma } from '@/lib/prisma/client';
import { assertEnrollableProgram } from './program-graph-validation';

export interface ProgramCompletionDecision {
  nextAction: 'next_program' | 'reassess' | 'journey_summary' | 'level_up_auto';
  nextProgramId: string | null;
  reassessmentTemplateId: string | null;
}

function isAutoPrescriptionTrack(assignmentReason: unknown): boolean {
  if (!assignmentReason || typeof assignmentReason !== 'object' || Array.isArray(assignmentReason)) return false;
  const src = (assignmentReason as { source?: string }).source;
  return src === 'selection_algorithm' || src === 'fallback_selection';
}

export const programCompletionService = {
  /**
   * Decide what happens after the user finishes the active program slot.
   *
   * 1. If a published progression assessment template matches the user's current level → reassess (gate).
   * 2. Else if the program defines `nextProgramId` → chain to that program.
   * 3. Else if enrollment was auto-prescribed → recommend next program by level (handled in completeActiveProgram).
   * 4. Else → journey summary (manual / off-track path).
   */
  async evaluate(userId: string, userProgramId: string): Promise<ProgramCompletionDecision | null> {
    const prisma = await getPrisma();

    const [userProgram, pendingReassessment] = await Promise.all([
      prisma.userProgram.findFirst({
        where: { id: userProgramId, userId },
        include: {
          program: true,
        },
      }),
      prisma.reassessmentSchedule.findFirst({
        where: { userId, status: { in: ['pending', 'overdue'] } },
        orderBy: { scheduledDate: 'asc' },
      }),
    ]);

    if (!userProgram?.program) return null;

    const { program } = userProgram;

    const levelProfile = await prisma.userLevelProfile.findFirst({
      where: { userId },
      orderBy: { classifiedAt: 'desc' },
    });
    const userLevel = levelProfile?.overallLevel ?? 1;

    const matchedProgressionTemplate = await assessmentMatchingService.matchProgression(userId, userLevel);

    if (matchedProgressionTemplate) {
      if (!pendingReassessment) {
        await prisma.reassessmentSchedule.create({
          data: {
            userId,
            reason: 'program_complete',
            scheduledDate: new Date(),
            status: 'pending',
            notes: 'Progression assessment required before next program',
          },
        });
      }

      return {
        nextAction: 'reassess',
        nextProgramId: null,
        reassessmentTemplateId: matchedProgressionTemplate.id,
      };
    }

    if (program.nextProgramId) {
      try {
        await assertEnrollableProgram(prisma, program.nextProgramId);
      } catch (error) {
        console.warn(
          `[ProgramCompletion] nextProgramId ${program.nextProgramId} is not enrollable:`,
          error instanceof Error ? error.message : error,
        );
        return {
          nextAction: 'journey_summary',
          nextProgramId: null,
          reassessmentTemplateId: null,
        };
      }

      return {
        nextAction: 'next_program',
        nextProgramId: program.nextProgramId,
        reassessmentTemplateId: null,
      };
    }

    if (isAutoPrescriptionTrack(userProgram.assignmentReason)) {
      return {
        nextAction: 'level_up_auto',
        nextProgramId: null,
        reassessmentTemplateId: null,
      };
    }

    return {
      nextAction: 'journey_summary',
      nextProgramId: null,
      reassessmentTemplateId: null,
    };
  },
};
