import { getPrisma } from '@/lib/prisma/client';

export interface ProgramProgressWeekPoint {
  weekNumber: number;
  totalVolumeLoad: number;
  avgFormScore: number | null;
  sessionCount: number;
  avgRpe: number | null;
  volumeChangePercent: number | null;
}

export interface ProgramProgressExerciseOverload {
  exerciseId: string;
  exerciseSlug: string | null;
  currentWeightKg: number | null;
  initialWeightKg: number | null;
  overloadPercent: number | null;
}

export interface ProgramProgressMetricsResponse {
  userProgramId: string;
  programId: string | null;
  weeks: ProgramProgressWeekPoint[];
  exerciseOverload: ProgramProgressExerciseOverload[];
}

function estimateVolumeFromReport(reportJson: unknown): number {
  if (!reportJson || typeof reportJson !== 'object' || Array.isArray(reportJson)) return 0;
  const rec = reportJson as Record<string, unknown>;
  const tv = rec.totalVolume;
  if (typeof tv === 'number' && Number.isFinite(tv)) return tv;
  const exercises = rec.exerciseReports;
  if (!Array.isArray(exercises)) return 0;
  let sum = 0;
  for (const ex of exercises) {
    if (!ex || typeof ex !== 'object' || Array.isArray(ex)) continue;
    const e = ex as Record<string, unknown>;
    const reps = typeof e.totalReps === 'number' ? e.totalReps : 0;
    const w = typeof e.averageWeightKg === 'number' ? e.averageWeightKg : typeof e.weightKg === 'number' ? e.weightKg : 0;
    sum += reps * w;
  }
  return sum;
}

export const programProgressService = {
  async getProgressMetrics(
    userId: string,
    userProgramId: string,
  ): Promise<ProgramProgressMetricsResponse | null> {
    const prisma = await getPrisma();
    const up = await prisma.userProgram.findFirst({
      where: { id: userProgramId, userId },
      include: {
        program: { select: { id: true } },
        progressionStates: {
          include: { exercise: { select: { slug: true } } },
        },
      },
    });
    if (!up?.programId) return null;

    const reports = await prisma.programSessionReport.findMany({
      where: {
        userId,
        programId: up.programId,
        status: 'completed',
      },
      orderBy: [{ weekNumber: 'asc' }, { completedAt: 'asc' }],
    });

    const byWeek = new Map<
      number,
      { volume: number; formSum: number; formCount: number; rpeSum: number; rpeCount: number; sessions: number }
    >();

    for (const r of reports) {
      const wn = r.weekNumber;
      const bucket =
        byWeek.get(wn) ?? {
          volume: 0,
          formSum: 0,
          formCount: 0,
          rpeSum: 0,
          rpeCount: 0,
          sessions: 0,
        };
      bucket.volume += estimateVolumeFromReport(r.report);
      if (r.avgFormScore != null) {
        bucket.formSum += r.avgFormScore;
        bucket.formCount += 1;
      }
      if (r.rpe != null) {
        bucket.rpeSum += r.rpe;
        bucket.rpeCount += 1;
      }
      bucket.sessions += 1;
      byWeek.set(wn, bucket);
    }

    const sortedWeeks = [...byWeek.keys()].sort((a, b) => a - b);
    const weeks: ProgramProgressWeekPoint[] = [];
    let prevVolume: number | null = null;
    for (const wn of sortedWeeks) {
      const b = byWeek.get(wn)!;
      const avgForm = b.formCount > 0 ? b.formSum / b.formCount : null;
      const avgRpe = b.rpeCount > 0 ? b.rpeSum / b.rpeCount : null;
      let volumeChangePercent: number | null = null;
      if (prevVolume != null && prevVolume > 0) {
        volumeChangePercent = Math.round(((b.volume - prevVolume) / prevVolume) * 1000) / 10;
      }
      weeks.push({
        weekNumber: wn,
        totalVolumeLoad: Math.round(b.volume * 10) / 10,
        avgFormScore: avgForm != null ? Math.round(avgForm * 10) / 10 : null,
        sessionCount: b.sessions,
        avgRpe: avgRpe != null ? Math.round(avgRpe * 10) / 10 : null,
        volumeChangePercent,
      });
      prevVolume = b.volume;
    }

    const exerciseOverload: ProgramProgressExerciseOverload[] = up.progressionStates.map((st) => ({
      exerciseId: st.exerciseId,
      exerciseSlug: st.exercise?.slug ?? null,
      currentWeightKg: st.currentWeightKg,
      initialWeightKg: null,
      overloadPercent: null,
    }));

    return {
      userProgramId: up.id,
      programId: up.programId,
      weeks,
      exerciseOverload,
    };
  },
};
