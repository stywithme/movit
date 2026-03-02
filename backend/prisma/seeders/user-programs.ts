import type { PrismaClient } from '@prisma/client';
import { ProgressStatus, ReportStatus } from '@prisma/client';

// ============================================
// Realistic training data generator
// ============================================

/** Generate a random float in range [min, max] */
function rand(min: number, max: number): number {
  return Math.round((min + Math.random() * (max - min)) * 10) / 10;
}

/** Generate rep details for a set */
function generateRepDetails(repsTarget: number, dayQuality: number) {
  const reps: {
    repNumber: number;
    score: number;
    worstState: number;
    isCounted: boolean;
    durationMs: number;
  }[] = [];

  for (let r = 1; r <= repsTarget; r++) {
    // Quality degrades toward end of set (fatigue simulation)
    const fatigueMultiplier = 1 - (r / repsTarget) * 0.25;
    const baseScore = dayQuality * fatigueMultiplier;
    const score = Math.min(100, Math.max(15, rand(baseScore - 15, baseScore + 8)));

    // Determine worst state from score
    let worstState = 0; // PERFECT
    if (score < 40) worstState = 4;      // DANGER
    else if (score < 55) worstState = 3;  // WARNING
    else if (score < 70) worstState = 2;  // PAD
    else if (score < 85) worstState = 1;  // NORMAL

    // Reps below 50 score are not counted (bad form / incomplete)
    const isCounted = score >= 50;
    const durationMs = Math.round(rand(2500, 5500));

    reps.push({ repNumber: r, score, worstState, isCounted, durationMs });
  }
  return reps;
}

/** Generate a full SessionReport JSON for a session */
function generateSessionReport(
  exercises: { slug: string; name: string; sets: number; repsTarget: number; weightKg?: number }[],
  dayQuality: number, // 60-95 base quality for the day
): object {
  const exerciseReports = exercises.map((ex, exIdx) => {
    const setMetrics: {
      exerciseSlug: string;
      exerciseIndex: number;
      setNumber: number;
      repsCompleted: number;
      repsTarget: number;
      durationMs: number;
      accuracy: number;
      formScore: number;
      weightKg: number | null;
      repDetails: ReturnType<typeof generateRepDetails>;
    }[] = [];
    let exTotalReps = 0;
    let exTotalAccuracy = 0;
    let exTotalFormScore = 0;

    for (let s = 1; s <= ex.sets; s++) {
      const repDetails = generateRepDetails(ex.repsTarget, dayQuality - (s - 1) * 2);
      const repsCompleted = repDetails.filter((r) => r.isCounted).length;
      const accuracy = (repsCompleted / ex.repsTarget) * 100;
      const formScore = repDetails.length > 0
        ? repDetails.reduce((sum, r) => sum + r.score, 0) / repDetails.length
        : 0;
      const durationMs = repDetails.reduce((sum, r) => sum + r.durationMs, 0) + rand(3000, 8000);

      setMetrics.push({
        exerciseSlug: ex.slug,
        exerciseIndex: exIdx,
        setNumber: s,
        repsCompleted,
        repsTarget: ex.repsTarget,
        durationMs: Math.round(durationMs),
        accuracy: Math.round(accuracy * 10) / 10,
        formScore: Math.round(formScore * 10) / 10,
        weightKg: ex.weightKg ?? null,
        repDetails,
      });

      exTotalReps += repsCompleted;
      exTotalAccuracy += accuracy;
      exTotalFormScore += formScore;
    }

    return {
      exerciseSlug: ex.slug,
      exerciseName: ex.name,
      setsCompleted: ex.sets,
      totalSets: ex.sets,
      totalReps: exTotalReps,
      averageAccuracy: Math.round((exTotalAccuracy / ex.sets) * 10) / 10,
      averageFormScore: Math.round((exTotalFormScore / ex.sets) * 10) / 10,
      setMetrics,
    };
  });

  const totalSetsCompleted = exerciseReports.reduce((s, e) => s + e.setsCompleted, 0);
  const totalSetsPlanned = exerciseReports.reduce((s, e) => s + e.totalSets, 0);
  const totalReps = exerciseReports.reduce((s, e) => s + e.totalReps, 0);
  const avgAccuracy = exerciseReports.length > 0
    ? exerciseReports.reduce((s, e) => s + e.averageAccuracy, 0) / exerciseReports.length
    : 0;
  const avgFormScore = exerciseReports.length > 0
    ? exerciseReports.reduce((s, e) => s + e.averageFormScore, 0) / exerciseReports.length
    : 0;
  const totalDurationMs = exerciseReports.reduce(
    (s, e) => s + e.setMetrics.reduce((ss, sm) => ss + sm.durationMs, 0),
    0,
  ) + exercises.length * 60000; // Add rest between exercises

  return {
    totalExercises: exercises.length,
    totalSetsCompleted,
    totalSetsPlanned,
    totalReps,
    totalDurationMs: Math.round(totalDurationMs),
    averageAccuracy: Math.round(avgAccuracy * 10) / 10,
    averageFormScore: Math.round(avgFormScore * 10) / 10,
    exerciseReports,
  };
}

// ============================================
// Seeder
// ============================================

export async function seedUserPrograms(prisma: PrismaClient) {
  const safeFindProgram = async () => {
    try {
      return await prisma.program.findUnique({ where: { slug: 'starter-4-weeks' } });
    } catch (error: any) {
      if (error?.code === 'P2021' || error?.code === 'P2022') {
        console.warn('⚠️ Programs table/column issue. Skipping user program seed.');
        return null;
      }
      throw error;
    }
  };

  const user = await prisma.user.findUnique({ where: { email: 'alustadh.manager@gmail.com' } });
  const program = await safeFindProgram();

  if (!user || !program) {
    console.warn('⚠️ Missing user or program for user program seed.');
    return;
  }

  // ── Date Setup ──
  // Start date MUST be a Saturday so dayNumber aligns with Sat-Fri calendar.
  // Today is Feb 11, 2026 (Wednesday).
  // startDate = Saturday Jan 31, 2026 → today = dayIndex 11 = Week 2, Day 5
  //
  // Calendar mapping:
  //   Week 1: Jan 31(Sat) Feb 1(Sun) Feb 2(Mon) Feb 3(Tue) Feb 4(Wed) Feb 5(Thu) Feb 6(Fri)
  //   Week 2: Feb 7(Sat)  Feb 8(Sun) Feb 9(Mon) Feb 10(Tue) Feb 11(Wed) Feb 12(Thu) Feb 13(Fri)
  //
  // dayNumber mapping: 1=Sat, 2=Sun, 3=Mon, 4=Tue, 5=Wed, 6=Thu, 7=Fri
  const startDate = new Date(Date.UTC(2026, 0, 31, 6, 0, 0)); // Jan 31, 2026 Saturday 06:00 UTC

  // Upsert user program enrollment
  const existingUserProgram = await prisma.userProgram.findFirst({
    where: { userId: user.id, programId: program.id },
  });

  const userProgram = existingUserProgram
    ? await prisma.userProgram.update({
        where: { id: existingUserProgram.id },
        data: { isActive: true, startDate },
      })
    : await prisma.userProgram.create({
        data: {
          userId: user.id,
          programId: program.id,
          name: program.name as object,
          isActive: true,
          startDate,
        },
      });

  // Clean up old seed data
  await prisma.userProgramProgress.deleteMany({ where: { userProgramId: userProgram.id } });
  await prisma.programSessionReport.deleteMany({ where: { userId: user.id, programId: program.id } });

  // Fetch all sessions with their exercises
  const sessions = await prisma.programSession.findMany({
    where: { day: { week: { programId: program.id } } },
    include: {
      day: { include: { week: true } },
      items: {
        where: { type: 'exercise', exerciseId: { not: null } },
        include: { exercise: true },
        orderBy: { sortOrder: 'asc' },
      },
    },
    orderBy: [{ day: { week: { weekNumber: 'asc' } } }, { day: { dayNumber: 'asc' } }, { sortOrder: 'asc' }],
  });

  // ── Training Days ──
  // Quality improves over time (user gets better): 60 → 85
  //
  // Week 1 (Jan 31 - Feb 6):
  //   Day 1 (Sat, Jan 31): Training ✓ — quality 62
  //   Day 2 (Sun, Feb 1):  Training ✓ — quality 66
  //   Day 3 (Mon, Feb 2):  Rest
  //   Day 4 (Tue, Feb 3):  Training ✓ — quality 70
  //   Day 5 (Wed, Feb 4):  Rest
  //   Day 6 (Thu, Feb 5):  Training ✓ — quality 74
  //   Day 7 (Fri, Feb 6):  Rest
  //
  // Week 2 (Feb 7 - Feb 13):
  //   Day 1 (Sat, Feb 7):  Training ✓ — quality 78
  //   Day 2 (Sun, Feb 8):  Rest
  //   Day 3 (Mon, Feb 9):  Training ✓ — quality 82
  //   Day 4 (Tue, Feb 10): Rest
  //   Day 5 (Wed, Feb 11): TODAY — training day (no report yet)
  //   Day 6 (Thu, Feb 12): Rest — future
  //   Day 7 (Fri, Feb 13): Training — future

  const trainingDays = [
    // Week 1 — all training days completed
    { weekNumber: 1, dayNumber: 1, daysFromStart: 0,  quality: 62 },  // Jan 31 (Sat)
    { weekNumber: 1, dayNumber: 2, daysFromStart: 1,  quality: 66 },  // Feb 1  (Sun)
    { weekNumber: 1, dayNumber: 4, daysFromStart: 3,  quality: 70 },  // Feb 3  (Tue)
    { weekNumber: 1, dayNumber: 6, daysFromStart: 5,  quality: 74 },  // Feb 5  (Thu)
    // Week 2 — first two training days completed
    { weekNumber: 2, dayNumber: 1, daysFromStart: 7,  quality: 78 },  // Feb 7  (Sat)
    { weekNumber: 2, dayNumber: 3, daysFromStart: 9,  quality: 82 },  // Feb 9  (Mon)
  ];

  let totalReportsCreated = 0;
  let totalProgressCreated = 0;

  for (const td of trainingDays) {
    // Find sessions for this day
    const daySessions = sessions.filter(
      (s) => s.day.week.weekNumber === td.weekNumber && s.day.dayNumber === td.dayNumber,
    );

    if (daySessions.length === 0) continue;

    const sessionDate = new Date(startDate.getTime() + td.daysFromStart * 24 * 60 * 60 * 1000);

    for (const session of daySessions) {
      // Build exercise list from session items
      const exercises = session.items
        .filter((item) => item.exercise)
        .map((item) => ({
          slug: item.exercise!.slug,
          name: ((item.exercise!.name as any)?.en || item.exercise!.slug) as string,
          sets: item.sets ?? 3,
          repsTarget: item.targetReps ?? 10,
          weightKg: item.weightKg ?? undefined,
        }));

      if (exercises.length === 0) continue;

      // Generate the full report
      const report = generateSessionReport(exercises, td.quality);
      const reportData = report as any;

      // Session start time: morning (7-9am) with some variation
      const sessionStart = new Date(sessionDate);
      sessionStart.setUTCHours(7 + Math.floor(Math.random() * 2), Math.floor(Math.random() * 60), 0, 0);
      const sessionEnd = new Date(sessionStart.getTime() + reportData.totalDurationMs);

      // Create ProgramSessionReport
      await prisma.programSessionReport.create({
        data: {
          userId: user.id,
          programId: program.id,
          programSessionId: session.id,
          weekNumber: td.weekNumber,
          dayNumber: td.dayNumber,
          startedAt: sessionStart,
          completedAt: sessionEnd,
          status: ReportStatus.completed,
          totalDurationMs: reportData.totalDurationMs,
          totalExercises: exercises.length,
          totalSets: reportData.totalSetsPlanned,
          completedSets: reportData.totalSetsCompleted,
          totalReps: reportData.totalReps,
          avgAccuracy: reportData.averageAccuracy,
          avgFormScore: reportData.averageFormScore,
          report: report as any,
        },
      });
      totalReportsCreated++;

      // Create session-level progress entry
      await prisma.userProgramProgress.create({
        data: {
          userProgramId: userProgram.id,
          weekNumber: td.weekNumber,
          dayNumber: td.dayNumber,
          sessionId: session.id,
          completedAt: sessionEnd,
          status: ProgressStatus.completed,
        },
      });
      totalProgressCreated++;
    }

    // Mark the day as complete (all sessions done)
    const dayComplete = new Date(sessionDate);
    dayComplete.setUTCHours(10, 0, 0, 0);

    await prisma.userProgramProgress.create({
      data: {
        userProgramId: userProgram.id,
        weekNumber: td.weekNumber,
        dayNumber: td.dayNumber,
        sessionId: '__day__',
        completedAt: dayComplete,
        status: ProgressStatus.completed,
      },
    });
    totalProgressCreated++;
  }

  console.log(
    `✅ User programs seeded: ${totalReportsCreated} session reports, ${totalProgressCreated} progress entries across ${trainingDays.length} training days`,
  );
}
