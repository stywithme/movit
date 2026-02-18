/**
 * Training Sessions Service
 * 
 * Handles saving and retrieving exercise session data
 * Uses separate tables for Session, SessionMetrics, and RepMetrics
 */

import { prisma } from '@/lib/prisma/client';
import { Prisma } from '@prisma/client';
import { intX10ToFloat } from '@/lib/metrics';
import { progressionService } from '@/modules/progression/progression.service';
import {
  SessionUploadPayload,
  TrainingSessionResponse,
  ExerciseHistoryResponse,
  ExerciseHistoryItem,
  HistoryQueryParams,
  ProgressionData,
  RepMetricsData,
  SessionMetrics as SessionMetricsType,
  ProgramSessionStartPayload,
  ProgramSessionCompletePayload,
} from './training-sessions.types';

// ============================================
// Save Session
// ============================================

export async function saveSession(
  userId: string,
  payload: SessionUploadPayload
): Promise<TrainingSessionResponse> {
  // Validate exercise exists (search by id OR slug)
  let exercise = await prisma.exercise.findUnique({
    where: { id: payload.exerciseId },
    select: { id: true, name: true, slug: true },
  });
  
  // If not found by id, try by slug
  if (!exercise) {
    exercise = await prisma.exercise.findUnique({
      where: { slug: payload.exerciseId },
      select: { id: true, name: true, slug: true },
    });
  }

  if (!exercise) {
    throw new Error(`Exercise not found: ${payload.exerciseId}`);
  }

  // Use transaction to ensure all data is saved together
  const result = await prisma.$transaction(async (tx) => {
    // 1. Create or update TrainingSession (use actual exercise.id, not slug)
    const session = await tx.trainingSession.upsert({
      where: { id: payload.id },
      create: {
        id: payload.id,
        userId,
        exerciseId: exercise.id,
        timestamp: new Date(payload.timestamp),
        durationMs: payload.durationMs,
        totalReps: payload.totalReps,
        countedReps: payload.countedReps,
        invalidReps: payload.invalidReps,
        weightKg: payload.weightKg,
        weightUnit: payload.weightUnit,
        legacyReport: payload.legacyReport as any,
      },
      update: {
        durationMs: payload.durationMs,
        totalReps: payload.totalReps,
        countedReps: payload.countedReps,
        invalidReps: payload.invalidReps,
        weightKg: payload.weightKg,
        legacyReport: payload.legacyReport as any,
      },
    });

    // 2. Delete existing metrics (for upsert behavior)
    await tx.sessionMetrics.deleteMany({ where: { sessionId: session.id } });
    await tx.repMetrics.deleteMany({ where: { sessionId: session.id } });

    // 3. Create SessionMetrics (if provided)
    if (payload.sessionMetrics) {
      const sm = payload.sessionMetrics;
      await tx.sessionMetrics.create({
        data: {
          sessionId: session.id,
          avgRom: sm.avgRom,
          avgSymmetry: sm.avgSymmetry,
          avgStability: sm.avgStability,
          avgVelocity: sm.avgVelocity,
          avgFormScore: sm.avgFormScore,
          avgAlignmentAccuracy: sm.avgAlignmentAccuracy,
          avgTempo: sm.avgTempo,
          totalTUT: sm.totalTUT,
          totalVolume: sm.totalVolume,
          maxWeight: sm.maxWeight,
          est1RM: sm.est1RM,
          relativeStrength: sm.relativeStrength,
          intensityPercentage: sm.intensityPercentage,
          formConsistency: sm.formConsistency,
          fatigueIndex: sm.fatigueIndex,
        },
      });
    }

    // 4. Create RepMetrics (batch insert)
    if (payload.repMetrics && payload.repMetrics.length > 0) {
      await tx.repMetrics.createMany({
        data: payload.repMetrics.map((rep) => ({
          sessionId: session.id,
          repNumber: rep.num,
          durationMs: rep.durationMs,
          worstState: rep.worstState,
          score: rep.score,
          weightKg: rep.weightKg,
          rom: rep.metrics.rom,
          symmetry: rep.metrics.symmetry,
          stability: rep.metrics.stability,
          velocity: rep.metrics.velocity,
          formScore: rep.metrics.formScore,
          alignmentAccuracy: rep.metrics.alignmentAccuracy,
          tempo: rep.metrics.tempo,
        })),
      });
    }

    return session;
  });

  // Update user stats
  await updateUserStats(userId);

  console.log(`[Sessions] Saved session ${result.id}:`, {
    totalReps: result.totalReps,
    repMetricsSaved: payload.repMetrics?.length || 0,
  });

  // Fetch complete session for response
  return getSession(userId, result.id) as Promise<TrainingSessionResponse>;
}

// ============================================
// Get Session by ID
// ============================================

export async function getSession(
  userId: string,
  sessionId: string
): Promise<TrainingSessionResponse | null> {
  const session = await prisma.trainingSession.findFirst({
    where: {
      id: sessionId,
      userId,
    },
    include: {
      exercise: { select: { name: true } },
      sessionMetrics: true,
      repMetrics: { orderBy: { repNumber: 'asc' } },
    },
  });

  if (!session) return null;

  return mapSessionToResponse(session);
}

// ============================================
// Get Exercise History
// ============================================

export async function getExerciseHistory(
  userId: string,
  exerciseId: string,
  params: HistoryQueryParams = {}
): Promise<ExerciseHistoryResponse> {
  const { limit = 20, offset = 0, startDate, endDate } = params;

  // Get exercise info
  const exercise = await prisma.exercise.findUnique({
    where: { id: exerciseId },
    select: { id: true, name: true },
  });

  if (!exercise) {
    throw new Error(`Exercise not found: ${exerciseId}`);
  }

  // Build date filter
  const dateFilter: any = {};
  if (startDate) dateFilter.gte = new Date(startDate);
  if (endDate) dateFilter.lte = new Date(endDate);

  // Get sessions with metrics
  const sessions = await prisma.trainingSession.findMany({
    where: {
      userId,
      exerciseId,
      ...(Object.keys(dateFilter).length > 0 && { timestamp: dateFilter }),
    },
    orderBy: { timestamp: 'desc' },
    take: limit,
    skip: offset,
    include: {
      sessionMetrics: true,
    },
  });

  // Get total count
  const totalSessions = await prisma.trainingSession.count({
    where: {
      userId,
      exerciseId,
      ...(Object.keys(dateFilter).length > 0 && { timestamp: dateFilter }),
    },
  });

  // Map to history items
  const historyItems: ExerciseHistoryItem[] = sessions.map((s) => ({
    id: s.id,
    timestamp: s.timestamp.toISOString(),
    durationMs: s.durationMs,
    totalReps: s.totalReps,
    countedReps: s.countedReps,
    avgScore: s.sessionMetrics ? s.sessionMetrics.avgFormScore / 10 : 0,
    weightKg: s.weightKg,
    totalVolume: s.sessionMetrics?.totalVolume || null,
    est1RM: s.sessionMetrics?.est1RM || null,
  }));

  // Calculate aggregated stats
  const stats = await calculateExerciseStats(userId, exerciseId);

  return {
    exerciseId,
    exerciseName: exercise.name as { ar: string; en: string },
    totalSessions,
    sessions: historyItems,
    stats,
  };
}

// ============================================
// Get All History
// ============================================

export async function getAllHistory(
  userId: string,
  params: HistoryQueryParams = {}
): Promise<ExerciseHistoryItem[]> {
  const { limit = 50, offset = 0, startDate, endDate } = params;

  const dateFilter: any = {};
  if (startDate) dateFilter.gte = new Date(startDate);
  if (endDate) dateFilter.lte = new Date(endDate);

  const sessions = await prisma.trainingSession.findMany({
    where: {
      userId,
      ...(Object.keys(dateFilter).length > 0 && { timestamp: dateFilter }),
    },
    orderBy: { timestamp: 'desc' },
    take: limit,
    skip: offset,
    include: {
      exercise: { select: { name: true } },
      sessionMetrics: true,
    },
  });

  return sessions.map((s) => ({
    id: s.id,
    timestamp: s.timestamp.toISOString(),
    durationMs: s.durationMs,
    totalReps: s.totalReps,
    countedReps: s.countedReps,
    avgScore: s.sessionMetrics ? s.sessionMetrics.avgFormScore / 10 : 0,
    weightKg: s.weightKg,
    totalVolume: s.sessionMetrics?.totalVolume || null,
    est1RM: s.sessionMetrics?.est1RM || null,
  }));
}

// ============================================
// Get Session Details (with all rep metrics)
// ============================================

export async function getSessionDetails(
  userId: string,
  sessionId: string
): Promise<TrainingSessionResponse | null> {
  return getSession(userId, sessionId);
}

// ============================================
// Delete Session
// ============================================

export async function deleteSession(
  userId: string,
  sessionId: string
): Promise<boolean> {
  // Cascade will delete related metrics
  const result = await prisma.trainingSession.deleteMany({
    where: {
      id: sessionId,
      userId,
    },
  });

  if (result.count > 0) {
    await updateUserStats(userId);
  }

  return result.count > 0;
}

// ============================================
// Program Session Reports
// ============================================

export async function startProgramSessionReport(
  userId: string,
  sessionId: string,
  payload: ProgramSessionStartPayload
) {
  const session = await prisma.programSession.findFirst({
    where: { id: sessionId },
    include: { day: { include: { week: true } } },
  });

  if (!session) {
    throw new Error('Program session not found');
  }

  const weekNumber = session.day.week.weekNumber;
  const dayNumber = session.day.dayNumber;

  if (payload.weekNumber !== weekNumber || payload.dayNumber !== dayNumber) {
    throw new Error('Invalid week/day for session');
  }

  return prisma.programSessionReport.create({
    data: {
      userId,
      programId: session.day.week.programId,
      programSessionId: session.id,
      weekNumber,
      dayNumber,
      startedAt: payload.startedAt ? new Date(payload.startedAt) : new Date(),
      status: 'in_progress',
    },
  });
}

export async function completeProgramSessionReport(
  userId: string,
  sessionId: string,
  payload: ProgramSessionCompletePayload
) {
  // Find the active report (or the most recent one for this session)
  let report = await prisma.programSessionReport.findFirst({
    where: {
      userId,
      programSessionId: sessionId,
      status: 'in_progress',
    },
    orderBy: { createdAt: 'desc' },
  });

  // If no in_progress report, create one on the fly (supports offline-first: mobile may
  // call /complete without having called /start if it was offline when training began)
  if (!report) {
    const session = await prisma.programSession.findFirst({
      where: { id: sessionId },
      include: { day: { include: { week: true } } },
    });

    if (!session) {
      throw new Error('Program session not found');
    }

    report = await prisma.programSessionReport.create({
      data: {
        userId,
        programId: session.day.week.programId,
        programSessionId: session.id,
        weekNumber: session.day.week.weekNumber,
        dayNumber: session.day.dayNumber,
        startedAt: new Date(),
        status: 'in_progress',
      },
    });
  }

  const completedAt = payload.completedAt ? new Date(payload.completedAt) : new Date();

  // Update the report to completed
  const updatedReport = await prisma.programSessionReport.update({
    where: { id: report.id },
    data: {
      status: 'completed',
      completedAt,
      totalDurationMs: payload.totalDurationMs ?? report.totalDurationMs ?? undefined,
      totalExercises: payload.totalExercises ?? report.totalExercises ?? undefined,
      totalSets: payload.totalSets ?? report.totalSets ?? undefined,
      completedSets: payload.completedSets ?? report.completedSets ?? undefined,
      totalReps: payload.totalReps ?? report.totalReps ?? undefined,
      avgAccuracy: payload.avgAccuracy ?? report.avgAccuracy ?? undefined,
      avgFormScore: payload.avgFormScore ?? undefined,
      report: (payload.report ?? report.report) as Prisma.InputJsonValue | undefined,
    },
  });

  // ── Activate UserProgramProgress ──
  // Find the user's active program to update progress tracking
  try {
    const userProgram = await prisma.userProgram.findFirst({
      where: { userId, programId: report.programId ?? undefined, isActive: true },
    });

    if (userProgram) {
      await prisma.userProgramProgress.upsert({
        where: {
          userProgramId_weekNumber_dayNumber_sessionId: {
            userProgramId: userProgram.id,
            weekNumber: report.weekNumber,
            dayNumber: report.dayNumber,
            sessionId,
          },
        },
        create: {
          userProgramId: userProgram.id,
          weekNumber: report.weekNumber,
          dayNumber: report.dayNumber,
          sessionId,
          completedAt,
          status: 'completed',
        },
        update: {
          completedAt,
          status: 'completed',
        },
      });

      // Check if ALL sessions for this day are completed → mark day as completed
      const daySession = await prisma.programSession.findFirst({
        where: { id: sessionId },
        include: {
          day: {
            include: {
              sessions: { select: { id: true } },
            },
          },
        },
      });

      if (daySession) {
        const allSessionIds = daySession.day.sessions.map((s) => s.id);
        const completedReports = await prisma.programSessionReport.findMany({
          where: {
            userId,
            programSessionId: { in: allSessionIds },
            status: 'completed',
          },
          select: { programSessionId: true },
        });
        const completedSessionIds = new Set(completedReports.map((r) => r.programSessionId));

        if (allSessionIds.every((id) => completedSessionIds.has(id))) {
          // All sessions in this day are done — upsert day-level progress
          await prisma.userProgramProgress.upsert({
            where: {
              userProgramId_weekNumber_dayNumber_sessionId: {
                userProgramId: userProgram.id,
                weekNumber: report.weekNumber,
                dayNumber: report.dayNumber,
                sessionId: '__day__', // sentinel for day-level completion
              },
            },
            create: {
              userProgramId: userProgram.id,
              weekNumber: report.weekNumber,
              dayNumber: report.dayNumber,
              sessionId: '__day__',
              completedAt,
              status: 'completed',
            },
            update: {
              completedAt,
              status: 'completed',
            },
          });
        }
      }
    }
  } catch (progressError) {
    // Don't fail the main report update if progress tracking fails
    console.warn('[Sessions] Failed to update program progress:', progressError);
  }

  // ── Progression Engine — evaluate rules after session completion ──
  try {
    const changes = await progressionService.evaluateAfterSession(
      userId,
      sessionId,
      undefined, // Global rules don't need exerciseId
      report.programId ?? undefined,
    );
    if (changes.length > 0) {
      console.log(`[Progression] ${changes.length} change(s) applied for user ${userId}:`,
        changes.map((c) => `${c.field}: ${c.previousValue} → ${c.newValue}`).join(', '));
    }
  } catch (progressionError) {
    console.warn('[Sessions] Progression engine error (non-fatal):', progressionError);
  }

  return updatedReport;
}

export async function updateProgramSessionReport(
  userId: string,
  sessionId: string,
  payload: ProgramSessionCompletePayload
) {
  const report = await prisma.programSessionReport.findFirst({
    where: {
      userId,
      programSessionId: sessionId,
    },
    orderBy: { createdAt: 'desc' },
  });

  if (!report) {
    throw new Error('Report not found');
  }

  return prisma.programSessionReport.update({
    where: { id: report.id },
    data: {
      totalDurationMs: payload.totalDurationMs ?? report.totalDurationMs ?? undefined,
      totalExercises: payload.totalExercises ?? report.totalExercises ?? undefined,
      totalSets: payload.totalSets ?? report.totalSets ?? undefined,
      completedSets: payload.completedSets ?? report.completedSets ?? undefined,
      totalReps: payload.totalReps ?? report.totalReps ?? undefined,
      avgAccuracy: payload.avgAccuracy ?? report.avgAccuracy ?? undefined,
      avgFormScore: payload.avgFormScore ?? report.avgFormScore ?? undefined,
      report: (payload.report ?? report.report) as Prisma.InputJsonValue | undefined,
    },
  });
}

// ============================================
// User Home Stats
// ============================================

export async function getUserHomeStats(userId: string) {
  const now = new Date();

  // Start of current week (Monday)
  const startOfWeek = new Date(now);
  startOfWeek.setDate(now.getDate() - ((now.getDay() + 6) % 7));
  startOfWeek.setHours(0, 0, 0, 0);

  // Weekly session count (completed program session reports this week)
  const weeklySessionCount = await prisma.programSessionReport.count({
    where: {
      userId,
      status: 'completed',
      completedAt: { gte: startOfWeek },
    },
  });

  // Average form score from last 5 completed sessions
  const recentReports = await prisma.programSessionReport.findMany({
    where: { userId, status: 'completed', avgFormScore: { not: null } },
    orderBy: { completedAt: 'desc' },
    take: 5,
    select: { avgFormScore: true },
  });

  let avgFormScore = 0;
  if (recentReports.length > 0) {
    const total = recentReports.reduce((sum, r) => sum + (r.avgFormScore ?? 0), 0);
    avgFormScore = Math.round((total / recentReports.length) * 10) / 10;
  }

  // Training streak: consecutive days with completed sessions (backwards from today)
  const allCompletedDates = await prisma.programSessionReport.findMany({
    where: { userId, status: 'completed', completedAt: { not: null } },
    orderBy: { completedAt: 'desc' },
    select: { completedAt: true },
  });

  let streak = 0;
  if (allCompletedDates.length > 0) {
    const trainedDays = new Set<string>();
    for (const r of allCompletedDates) {
      if (r.completedAt) {
        trainedDays.add(r.completedAt.toISOString().split('T')[0]);
      }
    }

    const today = now.toISOString().split('T')[0];
    const yesterday = new Date(now.getTime() - 86400000).toISOString().split('T')[0];

    // Start counting from today or yesterday
    let checkDate = trainedDays.has(today) ? now : new Date(now.getTime() - 86400000);
    if (!trainedDays.has(today) && !trainedDays.has(yesterday)) {
      streak = 0;
    } else {
      for (let i = 0; i < 365; i++) {
        const dateStr = checkDate.toISOString().split('T')[0];
        if (trainedDays.has(dateStr)) {
          streak++;
          checkDate = new Date(checkDate.getTime() - 86400000);
        } else {
          break;
        }
      }
    }
  }

  // Total lifetime stats from User record
  const user = await prisma.user.findUnique({
    where: { id: userId },
    select: { totalWorkouts: true, totalMinutes: true },
  });

  // Latest assessment info
  const latestAssessment = await prisma.bodyScanResult.findFirst({
    where: { userId },
    orderBy: { completedAt: 'desc' },
    select: { bodyScore: true, fitnessLevel: true, completedAt: true },
  });

  return {
    weeklyWorkouts: weeklySessionCount,
    avgFormScore,
    streak,
    totalWorkouts: user?.totalWorkouts ?? 0,
    totalMinutes: user?.totalMinutes ?? 0,
    latestAssessment: latestAssessment
      ? {
          bodyScore: latestAssessment.bodyScore,
          fitnessLevel: latestAssessment.fitnessLevel,
          completedAt: latestAssessment.completedAt.toISOString(),
        }
      : null,
  };
}

// ============================================
// Helper Functions
// ============================================

function mapSessionToResponse(session: any): TrainingSessionResponse {
  const sm = session.sessionMetrics;
  
  return {
    id: session.id,
    exerciseId: session.exerciseId,
    exerciseName: session.exercise?.name as { ar: string; en: string },
    timestamp: session.timestamp.toISOString(),
    durationMs: session.durationMs,
    totalReps: session.totalReps,
    countedReps: session.countedReps,
    invalidReps: session.invalidReps,
    weightKg: session.weightKg,
    weightUnit: session.weightUnit,
    sessionMetrics: sm ? {
      avgRom: sm.avgRom,
      avgSymmetry: sm.avgSymmetry,
      avgStability: sm.avgStability,
      avgTempo: sm.avgTempo as number[],
      avgVelocity: sm.avgVelocity,
      avgFormScore: sm.avgFormScore,
      avgAlignmentAccuracy: sm.avgAlignmentAccuracy,
      totalTUT: sm.totalTUT,
      totalVolume: sm.totalVolume,
      maxWeight: sm.maxWeight,
      est1RM: sm.est1RM,
      relativeStrength: sm.relativeStrength,
      intensityPercentage: sm.intensityPercentage,
      formConsistency: sm.formConsistency,
      fatigueIndex: sm.fatigueIndex,
    } : null,
    repMetrics: session.repMetrics?.map((rep: any) => ({
      num: rep.repNumber,
      durationMs: rep.durationMs,
      worstState: rep.worstState,
      score: rep.score,
      weightKg: rep.weightKg,
      metrics: {
        rom: rep.rom,
        symmetry: rep.symmetry,
        stability: rep.stability,
        tempo: rep.tempo as number[],
        velocity: rep.velocity,
        formScore: rep.formScore,
        alignmentAccuracy: rep.alignmentAccuracy,
      },
    })) || [],
  };
}

async function calculateExerciseStats(userId: string, exerciseId: string) {
  // Get all sessions with metrics
  const sessions = await prisma.trainingSession.findMany({
    where: { userId, exerciseId },
    orderBy: { timestamp: 'desc' },
    include: { sessionMetrics: true },
  });

  if (sessions.length === 0) {
    return {
      totalWorkouts: 0,
      totalReps: 0,
      totalVolume: 0,
      avgScore: 0,
      bestScore: 0,
      maxWeight: null,
      maxEst1RM: null,
      avgROM: 0,
      progression: null,
    };
  }

  const totalWorkouts = sessions.length;
  const totalReps = sessions.reduce((sum, s) => sum + s.totalReps, 0);
  const totalVolume = sessions.reduce(
    (sum, s) => sum + (s.sessionMetrics?.totalVolume || 0),
    0
  );

  const scores = sessions
    .map((s) => s.sessionMetrics?.avgFormScore || 0)
    .filter((s) => s > 0);
  const avgScore = scores.length > 0 
    ? scores.reduce((a, b) => a + b, 0) / scores.length / 10 
    : 0;
  const bestScore = scores.length > 0 ? Math.max(...scores) / 10 : 0;

  const weights = sessions
    .map((s) => s.weightKg)
    .filter((w): w is number => w !== null);
  const maxWeight = weights.length > 0 ? Math.max(...weights) : null;

  const est1RMs = sessions
    .map((s) => s.sessionMetrics?.est1RM)
    .filter((e): e is number => typeof e === 'number' && e > 0);
  const maxEst1RM = est1RMs.length > 0 ? Math.max(...est1RMs) : null;

  const roms = sessions
    .map((s) => s.sessionMetrics?.avgRom || 0)
    .filter((r) => r > 0);
  const avgROM = roms.length > 0 
    ? roms.reduce((a, b) => a + b, 0) / roms.length / 10 
    : 0;

  const progression = calculateProgression(sessions);

  return {
    totalWorkouts,
    totalReps,
    totalVolume,
    avgScore,
    bestScore,
    maxWeight,
    maxEst1RM,
    avgROM,
    progression,
  };
}

function calculateProgression(sessions: any[]): ProgressionData | null {
  if (sessions.length < 4) return null;

  const recent = sessions.slice(0, 4);
  const previous = sessions.slice(4, 8);

  if (previous.length < 2) return null;

  const avg = (arr: number[]) => 
    arr.length > 0 ? arr.reduce((a, b) => a + b, 0) / arr.length : 0;

  const recentVolume = avg(recent.map((s) => s.sessionMetrics?.totalVolume || 0));
  const previousVolume = avg(previous.map((s) => s.sessionMetrics?.totalVolume || 0));

  const recentScore = avg(recent.map((s) => s.sessionMetrics?.avgFormScore || 0));
  const previousScore = avg(previous.map((s) => s.sessionMetrics?.avgFormScore || 0));

  const recentStrength = avg(recent.map((s) => s.sessionMetrics?.est1RM || 0));
  const previousStrength = avg(previous.map((s) => s.sessionMetrics?.est1RM || 0));

  return {
    volumeChange: previousVolume > 0
      ? ((recentVolume - previousVolume) / previousVolume) * 100
      : 0,
    scoreChange: previousScore > 0
      ? ((recentScore - previousScore) / previousScore) * 100
      : 0,
    strengthChange: previousStrength > 0
      ? ((recentStrength - previousStrength) / previousStrength) * 100
      : 0,
  };
}

async function updateUserStats(userId: string) {
  const stats = await prisma.trainingSession.aggregate({
    where: { userId },
    _count: true,
    _sum: { durationMs: true },
  });

  await prisma.user.update({
    where: { id: userId },
    data: {
      totalWorkouts: stats._count,
      totalMinutes: Math.floor((stats._sum.durationMs || 0) / 60000),
    },
  });
}
