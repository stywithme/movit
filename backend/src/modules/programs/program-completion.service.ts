import { assessmentTemplateService } from '@/modules/assessment-templates/assessment-templates-admin.service';
import { getPrisma } from '@/lib/prisma/client';

type ThresholdRule = {
  min?: number;
  max?: number;
};

export interface ProgramCompletionDecision {
  nextAction: 'next_program' | 'reassess' | 'journey_summary';
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

function isWeekTrainingComplete(
  week: {
    weekNumber: number;
    days: { dayNumber: number; isRestDay: boolean; sessions: { id: string }[] }[];
  },
  weekNumber: number,
  completedDayKeys: Set<string>,
): boolean {
  const trainingDays = week.days.filter(
    (d) => !d.isRestDay && d.sessions.length > 0,
  );
  if (trainingDays.length === 0) {
    return true;
  }
  return trainingDays.every((d) => completedDayKeys.has(`${weekNumber}:${d.dayNumber}`));
}

function countCompletedCalendarWeeks(
  program: {
    durationWeeks: number;
    weeks: {
      weekNumber: number;
      days: { dayNumber: number; isRestDay: boolean; sessions: { id: string }[] }[];
    }[];
  },
  progress: { weekNumber: number; dayNumber: number; sessionId: string; status: string }[],
): number {
  const completedDayKeys = new Set(
    progress
      .filter((p) => p.status === 'completed' && p.sessionId === '__day__')
      .map((p) => `${p.weekNumber}:${p.dayNumber}`),
  );

  let count = 0;
  for (let wn = 1; wn <= program.durationWeeks; wn++) {
    const week = program.weeks.find((w) => w.weekNumber === wn);
    if (!week) continue;
    if (isWeekTrainingComplete(week, wn, completedDayKeys)) {
      count++;
    }
  }
  return count;
}

export const programCompletionService = {
  async evaluate(userId: string, userProgramId: string): Promise<ProgramCompletionDecision | null> {
    const prisma = await getPrisma();

    const [userProgram, latestAssessment, latestLevelProfile, pendingReassessment] = await Promise.all([
      prisma.userProgram.findFirst({
        where: { id: userProgramId, userId },
        include: {
          program: {
            include: {
              weeks: {
                orderBy: { weekNumber: 'asc' },
                include: {
                  days: {
                    orderBy: { dayNumber: 'asc' },
                    include: { sessions: { select: { id: true } } },
                  },
                },
              },
            },
          },
          progress: true,
        },
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

    const program = userProgram.program;
    const completedWeeks = countCompletedCalendarWeeks(
      {
        durationWeeks: program.durationWeeks,
        weeks: program.weeks ?? [],
      },
      userProgram.progress,
    );

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

    const needsReassessment = unmetRequirements.length > 0;

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

    if (userProgram.program.nextProgramId) {
      return {
        nextAction: 'next_program',
        nextProgramId: userProgram.program.nextProgramId,
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
