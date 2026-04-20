import { assessmentTemplateService } from '@/modules/assessment-templates/assessment-templates-admin.service';
import { getPrisma } from '@/lib/prisma/client';

type ThresholdRule = {
  min?: number;
  max?: number;
};

export interface ProgramCompletionDecision {
  nextAction: 'next_program' | 'reassess';
  nextProgramId: string | null;
  reassessmentTemplateId: string | null;
}

function parseThresholdRule(value: unknown): ThresholdRule | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const record = value as Record<string, unknown>;
  const rule: ThresholdRule = {};
  if (typeof record.min === 'number') rule.min = record.min;
  if (typeof record.max === 'number') rule.max = record.max;
  return Object.keys(rule).length > 0 ? rule : null;
}

function isRuleSatisfied(value: number | null | undefined, rule: ThresholdRule | null): boolean {
  if (!rule) return true;
  if (value == null) return false;
  if (rule.min != null && value < rule.min) return false;
  if (rule.max != null && value > rule.max) return false;
  return true;
}

function collectExitReviewFindings(
  exitRaw: unknown,
  metrics: {
    bodyScore: number | null | undefined;
    mobilityScore: number | null | undefined;
    controlScore: number | null | undefined;
    symmetryScore: number | null | undefined;
    safetyScore: number | null | undefined;
    overallLevel: number | null | undefined;
    completedWeeks: number;
  },
): string[] {
  if (!exitRaw || typeof exitRaw !== 'object' || Array.isArray(exitRaw)) return [];

  const record = exitRaw as Record<string, unknown>;
  const findings: string[] = [];

  const checks: Array<[string, number | null | undefined]> = [
    ['bodyScore', metrics.bodyScore],
    ['mobilityScore', metrics.mobilityScore],
    ['controlScore', metrics.controlScore],
    ['symmetryScore', metrics.symmetryScore],
    ['safetyScore', metrics.safetyScore],
    ['overallLevel', metrics.overallLevel],
  ];

  for (const [field, value] of checks) {
    const rule = parseThresholdRule(record[field]);
    if (rule && !isRuleSatisfied(value, rule)) {
      findings.push(field);
    }
  }

  const minWeeksCompleted =
    typeof record.minWeeksCompleted === 'number' ? record.minWeeksCompleted : null;
  if (minWeeksCompleted != null && metrics.completedWeeks < minWeeksCompleted) {
    findings.push('minWeeksCompleted');
  }

  return findings;
}

export const programCompletionService = {
  async evaluate(userId: string, userProgramId: string): Promise<ProgramCompletionDecision | null> {
    const prisma = await getPrisma();

    const [userProgram, latestAssessment, latestLevelProfile, pendingReassessment] = await Promise.all([
      prisma.userProgram.findFirst({
        where: { id: userProgramId, userId },
        include: { program: true, progress: true },
      }),
      prisma.bodyScanResult.findFirst({
        where: { userId },
        orderBy: { completedAt: 'desc' },
      }),
      prisma.userLevelProfile.findFirst({
        where: { userId },
        orderBy: { classifiedAt: 'desc' },
      }),
      prisma.reassessmentSchedule.findFirst({
        where: { userId, status: { in: ['pending', 'overdue'] } },
        orderBy: { scheduledDate: 'asc' },
      }),
    ]);

    if (!userProgram?.program) return null;
    const completedWeeks = new Set(
      userProgram.progress
        .filter((entry) => entry.status === 'completed')
        .map((entry) => entry.weekNumber),
    ).size;

    const unmetRequirements = collectExitReviewFindings(
      userProgram.program.exitRecommendations,
      {
        bodyScore: latestAssessment?.bodyScore,
        mobilityScore: latestAssessment?.mobilityScore,
        controlScore: latestAssessment?.controlScore,
        symmetryScore: latestAssessment?.symmetryScore,
        safetyScore: latestAssessment?.safetyScore,
        overallLevel: latestLevelProfile?.overallLevel,
        completedWeeks,
      },
    );

    const needsReassessment =
      unmetRequirements.length > 0 || !userProgram.program.nextProgramId;

    if (needsReassessment) {
      if (!pendingReassessment) {
        const notes = unmetRequirements.length > 0
          ? `Exit review required: ${unmetRequirements.join(', ')}`
          : 'Program complete: reassessment required before next decision';
        await prisma.reassessmentSchedule.create({
          data: {
            userId,
            reason: 'program_complete',
            scheduledDate: new Date(),
            status: 'pending',
            notes,
          },
        });
      }

      const resolvedTemplate = await assessmentTemplateService.resolveForUser(userId);
      return {
        nextAction: 'reassess',
        nextProgramId: null,
        reassessmentTemplateId: resolvedTemplate?.templateId ?? null,
      };
    }

    return {
      nextAction: 'next_program',
      nextProgramId: userProgram.program.nextProgramId,
      reassessmentTemplateId: null,
    };
  },
};
