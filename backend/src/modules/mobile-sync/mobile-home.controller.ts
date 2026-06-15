/**
 * Mobile Home Controller
 * ======================
 *
 * GET /mobile/home � Unified home screen data
 *
 * Returns a single, fully-structured response that the Home screen
 * consumes directly. The `trainMode` field drives all UI state decisions.
 *
 * TrainMode status values:
 *   - no_assessment   : User has never done a Body Scan
 *   - no_plan         : Assessment done but no active program
 *   - rest_day        : Active plan, but today is a rest or active-recovery day
 *   - active          : Active plan with planned workouts to complete today
 *   - program_complete: All weeks done, awaiting reassessment
 *   - reassessment_due: A ReassessmentSchedule is pending
 */

import { Controller, Get, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { getPrisma } from '@/lib/prisma/client';
import { PlannedWorkoutItemType, WorkoutExecutionContext } from '@prisma/client';
import {
  countEffectiveExerciseItems,
  effectivePlanService,
} from '@/modules/effective-plan/effective-plan.service';
import {
  resolveTrainingPositionMeta,
  countTrainingDaySlots,
  isProgramTrainingDaySlot,
  type TrainingPositionMeta,
} from '@/modules/active-plan/plan-position';
import {
  buildCatchUpSuggestionFromMeta,
  getLastPlannedWorkoutCompletedAt,
  type CatchUpSuggestion,
} from '@/modules/programs/program-catchup';

// -- Types ------------------------------------------------------------------

type TrainModeStatus =
  | 'no_assessment'
  | 'no_plan'
  | 'rest_day'
  | 'active'
  | 'program_complete'
  | 'reassessment_due';

/** One cell in the Train week-calendar component. Per-day truth, no client-side guessing. */
type WeekCalendarDayStatus =
  | 'completed'
  | 'today'
  | 'in_progress'
  | 'upcoming'
  | 'rest'
  | 'active_recovery';

interface WeekCalendarWorkout {
  plannedWorkoutId: string;
  name: Record<string, string>;
  exerciseCount: number;
  estimatedMinutes: number | null;
  allWorkoutsCount: number;
  completedWorkoutsCount: number;
}

interface WeekCalendarDay {
  dayNumber: number;
  /** 0=Sun … 6=Sat — best-effort from trainingWeekdays; null when not derivable (e.g. rest slots). */
  weekdayIndex: number | null;
  dayType: string;
  isRestDay: boolean;
  status: WeekCalendarDayStatus;
  isToday: boolean;
  workout: WeekCalendarWorkout | null;
}

interface WeekCalendar {
  weekNumber: number;
  isCurrentWeek: boolean;
  completedDays: number;
  totalTrainingDays: number;
  days: WeekCalendarDay[];
}

interface TrainModeData {
  status: TrainModeStatus;
  activeProgram: {
    id: string;
    name: Record<string, string>;
    weekNumber: number;
    dayNumber: number;
    totalWeeks: number;
    weekProgress: { completed: number; total: number };
  } | null;
  /** Per-week, per-day calendar for the Train week strip. Empty when no active program. */
  weekCalendars: WeekCalendar[];
  todayWorkout: {
    plannedWorkoutId: string;
    name: Record<string, string>;
    exerciseCount: number;
    estimatedMinutes: number | null;
    workoutTemplateId: string | null;
    isCompleted: boolean;
    allWorkoutsCount: number;
    completedWorkoutsCount: number;
  } | null;
  dayType: string | null;
  nextReassessment: { scheduledDate: string; reason: string } | null;
  isTrainingDay: boolean;
  catchUpSuggestion: CatchUpSuggestion | null;
}

interface AlertData {
  type: 'reassessment_due' | 'progression_applied' | 'level_up' | 'streak_at_risk';
  titleAr: string;
  titleEn: string;
  messageAr: string;
  messageEn: string;
  actionRoute?: string;
}

interface HomeResponse {
  user: {
    name: string;
    avatarUrl: string | null;
    level: number | null;
    levelCode: string | null;
    bodyScore: number | null;
    levelProgress: number | null; // 0-100, how far to next level
  };
  trainMode: TrainModeData;
  stats: {
    totalWorkoutExecutions: number;
    avgFormScore: number;
    streak: number;
    thisWeekExecutions: number;
    totalMinutes: number;
  };
  recentWorkouts: {
    exerciseId: string;
    exerciseName: Record<string, string>;
    formScore: number;
    totalReps: number;
    date: string;
    context: WorkoutExecutionContext;
  }[];
  alerts: AlertData[];
}

// -- Controller -------------------------------------------------------------

@Controller('mobile/home')
export class MobileHomeController {
  @Get()
  async getHomeData(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const userId = authResult.userId;
      const data = await buildHomeData(userId);

      return {
        success: true,
        data,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      console.error('[Mobile Home] Error building home data:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch home data' };
    }
  }
}

// -- Core Builder -----------------------------------------------------------

async function buildHomeData(userId: string): Promise<HomeResponse> {
  const prisma = await getPrisma();

  // Fetch all needed data in parallel
  const [user, levelProfile, latestAssessment, activePlan, pendingReassessment, unseenProgression, recentSessionsRaw, trainingProfile] =
    await Promise.all([
      prisma.user.findUnique({
        where: { id: userId },
        select: { name: true, avatarUrl: true, totalWorkoutExecutions: true, totalMinutes: true },
      }),
      prisma.userLevelProfile.findFirst({
        where: { userId },
        orderBy: { classifiedAt: 'desc' },
      }),
      prisma.bodyScanResult.findFirst({
        where: { userId },
        orderBy: { completedAt: 'desc' },
        select: { bodyScore: true, levelId: true, completedAt: true },
      }),
      prisma.activePlan.findUnique({
        where: { userId },
        include: {
          programs: {
            where: { status: 'active' },
            orderBy: { sortOrder: 'asc' },
            include: {
              userProgram: {
                include: {
                  program: {
                    include: {
                      weeks: {
                        include: {
                          days: {
                            include: { plannedWorkouts: {
                                include: {
                                  items: { where: { type: PlannedWorkoutItemType.exercise }, select: { id: true } },
                                  reports: {
                                    where: { userId, status: 'completed' },
                                    select: { id: true, plannedWorkoutId: true },
                                  },
                                },
                              },
                            },
                          },
                        },
                      },
                    },
                  },
                  progress: true,
                },
              },
            },
          },
        },
      }),
      prisma.reassessmentSchedule.findFirst({
        where: { userId, status: { in: ['pending', 'overdue'] } },
        orderBy: { scheduledDate: 'asc' },
      }),
      prisma.progressionHistory.count({ where: { userId, seen: false } }),
      prisma.workoutExecution.findMany({
        where: { userId },
        orderBy: { timestamp: 'desc' },
        take: 5,
        include: {
          exercise: { select: { name: true } },
          executionMetrics: { select: { avgFormScore: true } },
        },
      }),
      prisma.trainingProfile.findUnique({
        where: { userId },
        select: { trainingWeekdays: true },
      }),
    ]);

  // -- User header ----------------------------------------------------------

  const levelData = levelProfile
    ? await buildLevelProgress(prisma, levelProfile.overallLevel, levelProfile.bodyScore)
    : null;

  // -- Stats ----------------------------------------------------------------

  const stats = await buildStats(prisma, userId);

  // -- Train mode -----------------------------------------------------------

  const trainMode = await buildTrainMode(
    userId,
    latestAssessment,
    activePlan,
    pendingReassessment,
    trainingProfile,
  );

  // -- Recent workout executions --------------------------------------------

  const recentWorkoutExecutions = recentSessionsRaw.map((s) => ({
    exerciseId: s.exerciseId,
    exerciseName: s.exercise.name as Record<string, string>,
    formScore: s.executionMetrics ? Math.round(s.executionMetrics.avgFormScore / 10) : 0,
    totalReps: s.totalReps,
    date: s.timestamp.toISOString(),
    context: s.context,
  }));

  // -- Alerts ---------------------------------------------------------------

  const alerts = buildAlerts(trainMode.status, unseenProgression, stats.streak, pendingReassessment);

  return {
    user: {
      name: user?.name ?? '',
      avatarUrl: user?.avatarUrl ?? null,
      level: levelProfile?.overallLevel ?? null,
      levelCode: levelData?.code ?? null,
      bodyScore: latestAssessment?.bodyScore ?? null,
      levelProgress: levelData?.progressPercent ?? null,
    },
    trainMode,
    stats: {
      totalWorkoutExecutions: user?.totalWorkoutExecutions ?? 0,
      avgFormScore: stats.avgFormScore,
      streak: stats.streak,
      thisWeekExecutions: stats.thisWeekExecutions,
      totalMinutes: user?.totalMinutes ?? 0,
    },
    recentWorkouts: recentWorkoutExecutions,
    alerts,
  };
}

// -- TrainMode Builder ------------------------------------------------------

async function buildTrainMode(
  userId: string,
  latestAssessment: { bodyScore: number; levelId: string | null; completedAt: Date } | null,
  activePlan: any,
  pendingReassessment: { scheduledDate: Date; reason: string } | null,
  trainingProfile: { trainingWeekdays: number[] } | null,
): Promise<TrainModeData> {
  const reassessmentData = pendingReassessment
    ? { scheduledDate: pendingReassessment.scheduledDate.toISOString(), reason: pendingReassessment.reason }
    : null;

  const trainingWeekdays =
    trainingProfile?.trainingWeekdays && trainingProfile.trainingWeekdays.length > 0
      ? trainingProfile.trainingWeekdays
      : null;

  const activeSlot = activePlan?.programs?.find((slot: { status: string }) => slot.status === 'active');
  const hasActiveProgram = !!(activeSlot?.userProgram?.program);

  // State 1: No assessment ever done
  if (!latestAssessment && !hasActiveProgram) {
    return {
      status: 'no_assessment',
      activeProgram: null,
      weekCalendars: [],
      todayWorkout: null,
      dayType: null,
      nextReassessment: null,
      isTrainingDay: true,
      catchUpSuggestion: null,
    };
  }

  // State 2: Reassessment overdue � show before anything else
  if (
    pendingReassessment &&
    new Date() >= pendingReassessment.scheduledDate &&
    !hasActiveProgram
  ) {
    return {
      status: 'reassessment_due',
      activeProgram: null,
      weekCalendars: [],
      todayWorkout: null,
      dayType: null,
      nextReassessment: reassessmentData,
      isTrainingDay: true,
      catchUpSuggestion: null,
    };
  }

  // State 3: No active program in plan
  if (!activeSlot || !activeSlot.userProgram?.program) {
    return {
      status: 'no_plan',
      activeProgram: null,
      weekCalendars: [],
      todayWorkout: null,
      dayType: null,
      nextReassessment: reassessmentData,
      isTrainingDay: true,
      catchUpSuggestion: null,
    };
  }

  const program = activeSlot.userProgram.program;
  const progressEntries: any[] = activeSlot.userProgram.progress ?? [];
  const lastAt = await getLastPlannedWorkoutCompletedAt(userId, program.id);
  const meta = resolveTrainingPositionMeta(program.weeks as any[], progressEntries, {
    lastWorkoutCompletedAt: lastAt,
    trainingWeekdays,
    durationWeeks: program.durationWeeks,
  });
  const position = meta.position;
  const catchUpSuggestion = buildCatchUpSuggestionFromMeta(meta);

  const targetWeek = position.targetWeekNumber;
  const targetDay = position.targetDayNumber;

  const weekCalendars = buildWeekCalendars(program, meta, trainingWeekdays);

  const totalProgramTrainingDays = countTrainingDaySlots(program.weeks as any[]);

  // State 4: Program complete (exceeded all weeks)
  if (position.isProgramComplete) {
    return {
      status: 'program_complete',
      activeProgram: {
        id: program.id,
        name: program.name as Record<string, string>,
        weekNumber: targetWeek,
        dayNumber: targetDay,
        totalWeeks: program.durationWeeks,
        weekProgress: {
          completed: position.completedDayCount,
          total: totalProgramTrainingDays,
        },
      },
      weekCalendars,
      todayWorkout: null,
      dayType: 'completed',
      nextReassessment: reassessmentData,
      isTrainingDay: position.isTrainingDay ?? true,
      catchUpSuggestion: null,
    };
  }

  const week = position.targetWeek as any;
  const day = position.targetDay as any;

  const daysInTargetWeek = week?.days?.filter((d: any) => isProgramTrainingDaySlot(d)).length ?? 0;

  const activeProgram = {
    id: program.id,
    name: program.name as Record<string, string>,
    weekNumber: targetWeek,
    dayNumber: targetDay,
    totalWeeks: program.durationWeeks,
    weekProgress: {
      completed: position.targetWeekCompletedDays,
      total: daysInTargetWeek > 0 ? daysInTargetWeek : 1,
    },
  };

  const isUserOffDay = !position.isTrainingDay;
  const isTemplateRest =
    !day || day.isRestDay || day.dayType === 'rest' || day.dayType === 'active_recovery';

  // State 5: Rest day (template or user's off day)
  if (isTemplateRest || isUserOffDay) {
    return {
      status: 'rest_day',
      activeProgram,
      weekCalendars,
      todayWorkout: null,
      dayType: isUserOffDay ? 'off_schedule' : day?.dayType ?? 'rest',
      nextReassessment: reassessmentData,
      isTrainingDay: position.isTrainingDay ?? true,
      catchUpSuggestion,
    };
  }

  // State 6: Active training day - find first incomplete planned workout
  const todayPlannedWorkouts: any[] = [...day.plannedWorkouts].sort((a: any, b: any) => a.sortOrder - b.sortOrder);
  const completedPlannedWorkoutIds = new Set(
    todayPlannedWorkouts.flatMap((pw: any) => pw.reports.map((r: any) => r.plannedWorkoutId)),
  );

  // All planned workouts completed for today - treat as rest (day done)
  const nextPlannedWorkout = todayPlannedWorkouts.find((pw: any) => !completedPlannedWorkoutIds.has(pw.id));
  if (!nextPlannedWorkout) {
    return {
      status: 'rest_day',
      activeProgram,
      weekCalendars,
      todayWorkout: null,
      dayType: 'day_complete',
      nextReassessment: reassessmentData,
      isTrainingDay: position.isTrainingDay ?? true,
      catchUpSuggestion,
    };
  }

  let exerciseCount = (nextPlannedWorkout.items as any[]).filter((it: any) => it.type === 'exercise').length;
  try {
    const eff = await effectivePlanService.getEffectivePlan(
      userId,
      activeSlot.userProgram.id,
      targetWeek,
      targetDay,
    );
    const effectivePlannedWorkout = eff?.plannedWorkouts.find((pw) => pw.id === nextPlannedWorkout.id);
    if (effectivePlannedWorkout) {
      exerciseCount = countEffectiveExerciseItems(effectivePlannedWorkout);
    }
  } catch (e) {
    console.warn('[Mobile Home] effective plan for today planned workout:', e);
  }

  return {
    status: 'active',
    activeProgram,
    weekCalendars,
    todayWorkout: {
      plannedWorkoutId: nextPlannedWorkout.id,
      name: nextPlannedWorkout.name as Record<string, string>,
      exerciseCount,
      estimatedMinutes: nextPlannedWorkout.estimatedDurationMin,
      workoutTemplateId: nextPlannedWorkout.workoutTemplateId ?? null,
      isCompleted: false,
      allWorkoutsCount: todayPlannedWorkouts.length,
      completedWorkoutsCount: completedPlannedWorkoutIds.size,
    },
    dayType: 'training',
    nextReassessment: reassessmentData,
    isTrainingDay: position.isTrainingDay ?? true,
    catchUpSuggestion,
  };
}

// -- Week Calendar Builder --------------------------------------------------

/**
 * Builds the per-week, per-day calendar for the Train week strip directly from
 * the already-loaded program template + progress reports. No extra queries.
 *
 * Status is derived from the real completion-based position (not a guessed grid):
 *   - rest / active_recovery slots are first-class (never mislabelled as missed).
 *   - the single current slot is `today` (or `in_progress` when partially done).
 *   - earlier training days are `completed`, later ones `upcoming`.
 *   - there is intentionally NO "missed" state — the program waits for the user.
 */
function buildWeekCalendars(
  program: any,
  meta: TrainingPositionMeta<any, any>,
  trainingWeekdays: number[] | null,
): WeekCalendar[] {
  const position = meta.position;
  const targetWeek = position.targetWeekNumber;
  const targetDay = position.targetDayNumber;
  const isComplete = position.isProgramComplete;

  const orderIndex = new Map<string, number>();
  meta.orderedTrainingDays.forEach((ref: any, i: number) => {
    orderIndex.set(`${ref.weekNumber}:${ref.dayNumber}`, i);
  });
  const targetOrder = orderIndex.get(`${targetWeek}:${targetDay}`) ?? -1;

  const weeks = [...((program.weeks as any[]) ?? [])].sort(
    (a, b) => a.weekNumber - b.weekNumber,
  );

  return weeks.map((week: any): WeekCalendar => {
    const days = [...((week.days as any[]) ?? [])].sort((a, b) => a.dayNumber - b.dayNumber);
    let trainingDayCounter = 0;
    let completedDays = 0;
    let totalTrainingDays = 0;

    const calendarDays: WeekCalendarDay[] = days.map((day: any): WeekCalendarDay => {
      if (!isProgramTrainingDaySlot(day)) {
        const restStatus: WeekCalendarDayStatus =
          day.dayType === 'active_recovery' ? 'active_recovery' : 'rest';
        return {
          dayNumber: day.dayNumber,
          weekdayIndex: null,
          dayType: day.dayType ?? 'rest',
          isRestDay: true,
          status: restStatus,
          isToday: false,
          workout: null,
        };
      }

      totalTrainingDays++;
      const weekdayIndex =
        trainingWeekdays && trainingDayCounter < trainingWeekdays.length
          ? trainingWeekdays[trainingDayCounter]
          : null;
      trainingDayCounter++;

      const plannedWorkouts = [...((day.plannedWorkouts as any[]) ?? [])].sort(
        (a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0),
      );
      const allWorkoutsCount = plannedWorkouts.length;
      const completedWorkoutsCount = plannedWorkouts.filter(
        (pw: any) => (pw.reports?.length ?? 0) > 0,
      ).length;
      const dayCompleted = allWorkoutsCount > 0 && completedWorkoutsCount === allWorkoutsCount;
      if (dayCompleted) completedDays++;

      const exerciseCount = plannedWorkouts.reduce(
        (sum: number, pw: any) => sum + ((pw.items as any[])?.length ?? 0),
        0,
      );
      const estimatedMinutes = plannedWorkouts.reduce(
        (sum: number, pw: any) => sum + (pw.estimatedDurationMin ?? 0),
        0,
      );
      const firstWorkout = plannedWorkouts[0];

      const isToday =
        !isComplete && week.weekNumber === targetWeek && day.dayNumber === targetDay;

      let status: WeekCalendarDayStatus;
      if (dayCompleted) {
        status = 'completed';
      } else if (isToday) {
        status = completedWorkoutsCount > 0 ? 'in_progress' : 'today';
      } else {
        const ord =
          orderIndex.get(`${week.weekNumber}:${day.dayNumber}`) ?? Number.MAX_SAFE_INTEGER;
        status = targetOrder >= 0 && ord < targetOrder ? 'completed' : 'upcoming';
      }

      return {
        dayNumber: day.dayNumber,
        weekdayIndex,
        dayType: day.dayType ?? 'training',
        isRestDay: false,
        status,
        isToday,
        workout: firstWorkout
          ? {
              plannedWorkoutId: firstWorkout.id,
              name: (firstWorkout.name ?? {}) as Record<string, string>,
              exerciseCount,
              estimatedMinutes: estimatedMinutes > 0 ? estimatedMinutes : null,
              allWorkoutsCount,
              completedWorkoutsCount,
            }
          : null,
      };
    });

    return {
      weekNumber: week.weekNumber,
      isCurrentWeek: week.weekNumber === targetWeek,
      completedDays,
      totalTrainingDays,
      days: calendarDays,
    };
  });
}

// -- Level Progress Builder -------------------------------------------------

async function buildLevelProgress(
  prisma: any,
  currentLevel: number,
  bodyScore: number,
): Promise<{ code: string; progressPercent: number } | null> {
  const levels = await prisma.level.findMany({
    where: { number: { in: [currentLevel, currentLevel + 1] } },
    select: { number: true, code: true, entryThreshold: true, maxThreshold: true },
  });

  const current = levels.find((l: any) => l.number === currentLevel);
  const next = levels.find((l: any) => l.number === currentLevel + 1);

  if (!current) return null;

  const minScore = current.entryThreshold;
  const maxScore = next?.entryThreshold ?? 100;
  const range = maxScore - minScore;
  const progressPercent = range > 0 ? Math.min(100, Math.round(((bodyScore - minScore) / range) * 100)) : 100;

  return { code: current.code, progressPercent };
}

// -- Stats Builder ----------------------------------------------------------

async function buildStats(prisma: any, userId: string) {
  const now = new Date();
  const startOfWeek = new Date(now);
  startOfWeek.setDate(now.getDate() - ((now.getDay() + 6) % 7));
  startOfWeek.setHours(0, 0, 0, 0);

  const [thisWeekExecutions, recentScores, allDates] = await Promise.all([
    prisma.workoutExecution.count({
      where: { userId, timestamp: { gte: startOfWeek } },
    }),
    prisma.workoutExecution.findMany({
      where: { userId },
      orderBy: { timestamp: 'desc' },
      take: 10,
      select: { executionMetrics: { select: { avgFormScore: true } } },
    }),
    prisma.workoutExecution.findMany({
      where: { userId },
      orderBy: { timestamp: 'desc' },
      take: 365,
      select: { timestamp: true },
    }),
  ]);

  // Average form score
  const validScores = recentScores
    .map((s: any) => s.executionMetrics?.avgFormScore)
    .filter((v: any): v is number => v != null && v > 0);
  const avgFormScore =
    validScores.length > 0
      ? Math.round(validScores.reduce((a: number, b: number) => a + b, 0) / validScores.length / 10)
      : 0;

  // Training streak
  const trainedDays = new Set<string>();
  for (const s of allDates) {
    trainedDays.add((s.timestamp as Date).toISOString().split('T')[0]);
  }

  const today = now.toISOString().split('T')[0];
  const yesterday = new Date(now.getTime() - 86400000).toISOString().split('T')[0];

  let streak = 0;
  if (trainedDays.has(today) || trainedDays.has(yesterday)) {
    let checkDate = trainedDays.has(today) ? now : new Date(now.getTime() - 86400000);
    for (let i = 0; i < 365; i++) {
      if (trainedDays.has(checkDate.toISOString().split('T')[0])) {
        streak++;
        checkDate = new Date(checkDate.getTime() - 86400000);
      } else {
        break;
      }
    }
  }

  return { thisWeekExecutions, avgFormScore, streak };
}

// -- Alerts Builder ---------------------------------------------------------

function buildAlerts(
  trainStatus: TrainModeStatus,
  unseenProgressionCount: number,
  streak: number,
  pendingReassessment: { scheduledDate: Date; reason: string } | null,
): AlertData[] {
  const alerts: AlertData[] = [];

  if (trainStatus === 'reassessment_due') {
    alerts.push({
      type: 'reassessment_due',
      titleAr: '??? ??? ????? ???????',
      titleEn: 'Time for Reassessment',
      messageAr: '???? ????? Body Scan ?????? ?????? ??????? ??? ??? ?????',
      messageEn: 'Complete a Body Scan to update your level and get a new plan',
      actionRoute: 'assessment',
    });
  }

  if (unseenProgressionCount > 0) {
    alerts.push({
      type: 'progression_applied',
      titleAr: '?? ????? ??? ???????',
      titleEn: 'Training Plan Adjusted',
      messageAr: `?? ????? ${unseenProgressionCount} ????? ????? ??? ?????`,
      messageEn: `${unseenProgressionCount} exercise${unseenProgressionCount > 1 ? 's' : ''} adjusted based on your performance`,
      actionRoute: 'progression/recent',
    });
  }

  if (streak > 0 && streak % 7 === 0) {
    alerts.push({
      type: 'level_up',
      titleAr: `?? ${streak} ??? ??????!`,
      titleEn: `?? ${streak} day streak!`,
      messageAr: '??? ?? ??? ??????. ?????!',
      messageEn: 'You are at peak performance. Keep it up!',
    });
  }

  return alerts;
}
