/**
 * Progression Engine V2 — Profile-First
 *
 * Decision flow:
 *   1. Load ExerciseProgressionProfile (skip if none)
 *   2. Load or lazy-create UserProgramExerciseProgressionState
 *   3. Compute eligibility from session metrics + qualityGate
 *   4. Apply strategy: promotion, regression, or hold
 *   5. Update state → materialize ProgramSessionItem → write history
 *
 * Axes: load, reps, duration, sets, difficulty
 */

import { getPrisma } from '@/lib/prisma/client';
import { intX10ToFloat } from '@/lib/metrics';
import type { AxisConfig, QualityGate, PromotionPolicy, RegressionPolicy } from './archetype-defaults';

// ── Types ──

type EligibilityResult = 'promotion' | 'regression' | 'hold';

export interface ProgressionChange {
  field: string;
  previousValue: number;
  newValue: number;
  reason: string;
  notification: Record<string, string> | null;
  axis?: string;
  decisionType?: string;
}

interface SessionMetricsSummary {
  avgFormScore: number | null;
  completionRate: number | null;
  avgROM: number | null;
  avgSymmetry: number | null;
  avgStability: number | null;
}

// ── Axis Handlers ──

function applyAxisStep(
  currentValue: number,
  config: AxisConfig,
  direction: 'up' | 'down',
): { newValue: number; atLimit: boolean } {
  if (direction === 'up') {
    const newValue = Math.min(currentValue + config.step, config.cap);
    return { newValue, atLimit: newValue >= config.cap };
  }
  const newValue = Math.max(currentValue - config.step, config.floor);
  return { newValue, atLimit: newValue <= config.floor };
}

function advanceDifficulty(
  currentCode: string | null,
  ladder: string[],
  direction: 'up' | 'down',
): { newCode: string; atLimit: boolean } | null {
  if (!ladder || ladder.length === 0) return null;

  const currentIndex = currentCode ? ladder.indexOf(currentCode) : -1;
  const idx = currentIndex === -1 ? 0 : currentIndex;

  if (direction === 'up') {
    if (idx >= ladder.length - 1) return { newCode: ladder[ladder.length - 1], atLimit: true };
    return { newCode: ladder[idx + 1], atLimit: false };
  }
  if (idx <= 0) return { newCode: ladder[0], atLimit: true };
  return { newCode: ladder[idx - 1], atLimit: false };
}

// ── Metric Resolution ──

async function getRecentMetrics(
  userId: string,
  exerciseId: string,
  sessionCount: number,
): Promise<SessionMetricsSummary> {
  const prisma = await getPrisma();

  const sessions = await prisma.trainingSession.findMany({
    where: { userId, exerciseId, context: 'program' },
    include: { sessionMetrics: true },
    orderBy: { timestamp: 'desc' },
    take: sessionCount,
  });

  if (sessions.length === 0) {
    return { avgFormScore: null, completionRate: null, avgROM: null, avgSymmetry: null, avgStability: null };
  }

  const metrics = sessions.map((s) => s.sessionMetrics).filter((m) => m !== null);
  if (metrics.length === 0) {
    return { avgFormScore: null, completionRate: null, avgROM: null, avgSymmetry: null, avgStability: null };
  }

  const avg = (arr: number[]) => arr.length > 0 ? arr.reduce((a, b) => a + b, 0) / arr.length : null;

  return {
    avgFormScore: avg(metrics.map((m) => intX10ToFloat(m.avgFormScore))),
    completionRate: avg(
      sessions.map((s) => {
        const total = s.totalReps;
        const counted = s.countedReps;
        return total > 0 ? (counted / total) * 100 : 100;
      }),
    ),
    avgROM: avg(metrics.map((m) => intX10ToFloat(m.avgRom))),
    avgSymmetry: avg(metrics.filter((m) => m.avgSymmetry != null).map((m) => intX10ToFloat(m.avgSymmetry!))),
    avgStability: avg(metrics.map((m) => intX10ToFloat(m.avgStability))),
  };
}

// ── Eligibility ──

function evaluateEligibility(
  summary: SessionMetricsSummary,
  qualityGate: QualityGate,
  regressionPolicy: RegressionPolicy,
): EligibilityResult {
  if (summary.avgFormScore === null) return 'hold';

  if (summary.avgFormScore < regressionPolicy.maxFormScore) {
    return 'regression';
  }

  let eligible = true;
  if (summary.avgFormScore < qualityGate.minFormScore) eligible = false;
  if (summary.completionRate !== null && summary.completionRate < qualityGate.minCompletionRate) eligible = false;
  if (qualityGate.minROM && summary.avgROM !== null && summary.avgROM < qualityGate.minROM) eligible = false;
  if (qualityGate.minSymmetry && summary.avgSymmetry !== null && summary.avgSymmetry < qualityGate.minSymmetry) eligible = false;
  if (qualityGate.minStability && summary.avgStability !== null && summary.avgStability < qualityGate.minStability) eligible = false;

  return eligible ? 'promotion' : 'hold';
}

// ── Service ──

export const progressionService = {
  async evaluateAfterSession(
    userId: string,
    sessionId: string,
    exerciseIds: string[],
    userProgramId: string,
  ): Promise<ProgressionChange[]> {
    const prisma = await getPrisma();
    const allChanges: ProgressionChange[] = [];

    for (const exerciseId of exerciseIds) {
      const profile = await prisma.exerciseProgressionProfile.findUnique({
        where: { exerciseId },
      });
      if (!profile) continue;

      const qualityGate = profile.qualityGate as unknown as QualityGate;
      const promotionPolicy = profile.promotionPolicy as unknown as PromotionPolicy;
      const regressionPolicy = profile.regressionPolicy as unknown as RegressionPolicy;
      const allowedAxes = profile.allowedAxes as string[];
      const priorityOrder = profile.priorityOrder as string[];

      const windowSize = Math.max(promotionPolicy.requiredStreakSessions, 2);
      const summary = await getRecentMetrics(userId, exerciseId, windowSize);

      const eligibility = evaluateEligibility(summary, qualityGate, regressionPolicy);

      let state = await prisma.userProgramExerciseProgressionState.findUnique({
        where: { userProgramId_exerciseId: { userProgramId, exerciseId } },
      });

      if (!state) {
        state = await this.initializeState(userProgramId, exerciseId, profile);
      }

      if (!state) continue;

      const stateBefore = {
        currentAxis: state.currentAxis,
        currentWeightKg: state.currentWeightKg,
        currentTargetReps: state.currentTargetReps,
        currentTargetDuration: state.currentTargetDuration,
        currentTargetSets: state.currentTargetSets,
        currentDifficultyCode: state.currentDifficultyCode,
        successStreak: state.successStreak,
        regressionStreak: state.regressionStreak,
      };

      if (eligibility === 'hold') {
        await prisma.progressionHistory.create({
          data: {
            userId,
            sessionId,
            field: 'none',
            previousValue: 0,
            newValue: 0,
            reason: 'Quality gate met but streak not sufficient or metrics neutral',
            decisionType: 'hold',
            axis: state.currentAxis,
            eligibilitySnapshot: summary as any,
            stateBefore: stateBefore as any,
            stateAfter: stateBefore as any,
          },
        });
        continue;
      }

      if (eligibility === 'promotion') {
        const newStreak = state.successStreak + 1;

        if (newStreak < promotionPolicy.requiredStreakSessions) {
          await prisma.userProgramExerciseProgressionState.update({
            where: { id: state.id },
            data: { successStreak: newStreak, regressionStreak: 0 },
          });

          const stateAfterStreak = { ...stateBefore, successStreak: newStreak, regressionStreak: 0 };
          await prisma.progressionHistory.create({
            data: {
              userId,
              sessionId,
              field: 'successStreak',
              previousValue: state.successStreak,
              newValue: newStreak,
              reason: `Streak ${newStreak}/${promotionPolicy.requiredStreakSessions} — not yet ready to promote`,
              decisionType: 'hold',
              axis: state.currentAxis,
              eligibilitySnapshot: summary as any,
              stateBefore: stateBefore as any,
              stateAfter: stateAfterStreak as any,
            },
          });
          continue;
        }

        const change = await this.applyPromotion(
          userId, sessionId, userProgramId, exerciseId, state, profile, priorityOrder, allowedAxes, summary, stateBefore,
        );
        if (change) allChanges.push(change);
        continue;
      }

      if (eligibility === 'regression') {
        const change = await this.applyRegression(
          userId, sessionId, userProgramId, exerciseId, state, profile, summary, stateBefore,
        );
        if (change) allChanges.push(change);
      }
    }

    return allChanges;
  },

  async initializeState(userProgramId: string, exerciseId: string, profile: any) {
    const prisma = await getPrisma();

    const repAxis = profile.repAxis as AxisConfig | null;
    const loadAxis = profile.loadAxis as AxisConfig | null;
    const durationAxis = profile.durationAxis as AxisConfig | null;
    const setAxis = profile.setAxis as AxisConfig | null;
    const priorityOrder = profile.priorityOrder as string[];
    const ladder = profile.difficultyLadder as string[] | null;

    const existingItem = await prisma.programSessionItem.findFirst({
      where: {
        exerciseId,
        session: { day: { week: { program: { userPrograms: { some: { id: userProgramId } } } } } },
      },
      orderBy: { sortOrder: 'asc' },
    });

    return prisma.userProgramExerciseProgressionState.create({
      data: {
        userProgramId,
        exerciseId,
        currentAxis: priorityOrder[0] || 'reps',
        currentWeightKg: existingItem?.weightKg ?? loadAxis?.default ?? null,
        currentTargetReps: existingItem?.targetReps ?? repAxis?.default ?? null,
        currentTargetDuration: existingItem?.targetDuration ?? durationAxis?.default ?? null,
        currentTargetSets: existingItem?.sets ?? setAxis?.default ?? null,
        currentDifficultyCode: existingItem?.difficultyCode ?? (ladder ? ladder[0] : null),
        successStreak: 0,
        regressionStreak: 0,
      },
    });
  },

  async applyPromotion(
    userId: string,
    sessionId: string,
    userProgramId: string,
    exerciseId: string,
    state: any,
    profile: any,
    priorityOrder: string[],
    allowedAxes: string[],
    summary: SessionMetricsSummary,
    stateBefore: any,
  ): Promise<ProgressionChange | null> {
    const prisma = await getPrisma();

    const repAxis = profile.repAxis as AxisConfig | null;
    const loadAxis = profile.loadAxis as AxisConfig | null;
    const durationAxis = profile.durationAxis as AxisConfig | null;
    const setAxis = profile.setAxis as AxisConfig | null;
    const ladder = profile.difficultyLadder as string[] | null;

    let appliedAxis: string | null = null;
    let field = '';
    let previousValue = 0;
    let newValue = 0;
    let reason = '';

    for (const axis of priorityOrder) {
      if (!allowedAxes.includes(axis)) continue;

      if (axis === 'reps' && repAxis) {
        const current = state.currentTargetReps ?? repAxis.default;
        const result = applyAxisStep(current, repAxis, 'up');
        if (!result.atLimit || current < repAxis.cap) {
          appliedAxis = 'reps';
          field = 'targetReps';
          previousValue = current;
          newValue = result.newValue;
          reason = `Reps increased: ${current} → ${result.newValue} (cap: ${repAxis.cap})`;
          await prisma.userProgramExerciseProgressionState.update({
            where: { id: state.id },
            data: {
              currentTargetReps: result.newValue,
              currentAxis: 'reps',
              successStreak: 0,
              regressionStreak: 0,
              lastProgressedAt: new Date(),
            },
          });
          break;
        }
      }

      if (axis === 'load' && loadAxis) {
        const current = state.currentWeightKg ?? loadAxis.default;
        const result = applyAxisStep(current, loadAxis, 'up');
        if (!result.atLimit || current < loadAxis.cap) {
          appliedAxis = 'load';
          field = 'weightKg';
          previousValue = current;
          newValue = result.newValue;
          reason = `Load increased: ${current} → ${result.newValue}kg (cap: ${loadAxis.cap}kg)`;

          const resetReps = repAxis ? repAxis.floor : undefined;
          await prisma.userProgramExerciseProgressionState.update({
            where: { id: state.id },
            data: {
              currentWeightKg: result.newValue,
              ...(resetReps !== undefined ? { currentTargetReps: resetReps } : {}),
              currentAxis: 'load',
              successStreak: 0,
              regressionStreak: 0,
              lastProgressedAt: new Date(),
            },
          });
          break;
        }
      }

      if (axis === 'duration' && durationAxis) {
        const current = state.currentTargetDuration ?? durationAxis.default;
        const result = applyAxisStep(current, durationAxis, 'up');
        if (!result.atLimit || current < durationAxis.cap) {
          appliedAxis = 'duration';
          field = 'targetDuration';
          previousValue = current;
          newValue = result.newValue;
          reason = `Duration increased: ${current}s → ${result.newValue}s (cap: ${durationAxis.cap}s)`;
          await prisma.userProgramExerciseProgressionState.update({
            where: { id: state.id },
            data: {
              currentTargetDuration: result.newValue,
              currentAxis: 'duration',
              successStreak: 0,
              regressionStreak: 0,
              lastProgressedAt: new Date(),
            },
          });
          break;
        }
      }

      if (axis === 'sets' && setAxis) {
        const current = state.currentTargetSets ?? setAxis.default;
        const result = applyAxisStep(current, setAxis, 'up');
        if (!result.atLimit || current < setAxis.cap) {
          appliedAxis = 'sets';
          field = 'sets';
          previousValue = current;
          newValue = result.newValue;
          reason = `Sets increased: ${current} → ${result.newValue} (cap: ${setAxis.cap})`;
          await prisma.userProgramExerciseProgressionState.update({
            where: { id: state.id },
            data: {
              currentTargetSets: result.newValue,
              currentAxis: 'sets',
              successStreak: 0,
              regressionStreak: 0,
              lastProgressedAt: new Date(),
            },
          });
          break;
        }
      }

      if (axis === 'difficulty' && ladder && ladder.length > 0) {
        const result = advanceDifficulty(state.currentDifficultyCode, ladder, 'up');
        if (result && !result.atLimit) {
          appliedAxis = 'difficulty';
          field = 'difficultyCode';
          const currentIdx = state.currentDifficultyCode ? ladder.indexOf(state.currentDifficultyCode) : 0;
          previousValue = currentIdx;
          newValue = ladder.indexOf(result.newCode);
          reason = `Difficulty advanced: ${state.currentDifficultyCode || ladder[0]} → ${result.newCode}`;

          const resetReps = repAxis ? repAxis.floor : undefined;
          const resetDuration = durationAxis ? durationAxis.floor : undefined;
          await prisma.userProgramExerciseProgressionState.update({
            where: { id: state.id },
            data: {
              currentDifficultyCode: result.newCode,
              ...(resetReps !== undefined ? { currentTargetReps: resetReps } : {}),
              ...(resetDuration !== undefined ? { currentTargetDuration: resetDuration } : {}),
              currentAxis: 'difficulty',
              successStreak: 0,
              regressionStreak: 0,
              lastProgressedAt: new Date(),
            },
          });
          break;
        }
      }
    }

    if (!appliedAxis) {
      reason = 'All axes at cap — no further promotion possible';
      await prisma.progressionHistory.create({
        data: {
          userId,
          sessionId,
          field: 'none',
          previousValue: 0,
          newValue: 0,
          reason,
          decisionType: 'hold',
          axis: state.currentAxis,
          eligibilitySnapshot: summary as any,
          stateBefore: stateBefore as any,
          stateAfter: stateBefore as any,
        },
      });
      return null;
    }

    await this.materializeToNextItem(userProgramId, exerciseId);

    const updatedState = await prisma.userProgramExerciseProgressionState.findUnique({
      where: { userProgramId_exerciseId: { userProgramId, exerciseId } },
    });
    const stateAfter = updatedState ? {
      currentAxis: updatedState.currentAxis,
      currentWeightKg: updatedState.currentWeightKg,
      currentTargetReps: updatedState.currentTargetReps,
      currentTargetDuration: updatedState.currentTargetDuration,
      currentTargetSets: updatedState.currentTargetSets,
      currentDifficultyCode: updatedState.currentDifficultyCode,
      successStreak: updatedState.successStreak,
      regressionStreak: updatedState.regressionStreak,
    } : stateBefore;

    await prisma.progressionHistory.create({
      data: {
        userId,
        sessionId,
        field,
        previousValue,
        newValue,
        reason,
        decisionType: 'promotion',
        axis: appliedAxis,
        eligibilitySnapshot: summary as any,
        stateBefore: stateBefore as any,
        stateAfter: stateAfter as any,
      },
    });

    return {
      field,
      previousValue,
      newValue,
      reason,
      axis: appliedAxis,
      decisionType: 'promotion',
      notification: {
        ar: `تقدم! ${reason}`,
        en: `Progress! ${reason}`,
      },
    };
  },

  async applyRegression(
    userId: string,
    sessionId: string,
    userProgramId: string,
    exerciseId: string,
    state: any,
    profile: any,
    summary: SessionMetricsSummary,
    stateBefore: any,
  ): Promise<ProgressionChange | null> {
    const prisma = await getPrisma();

    const repAxis = profile.repAxis as AxisConfig | null;
    const loadAxis = profile.loadAxis as AxisConfig | null;
    const durationAxis = profile.durationAxis as AxisConfig | null;
    const setAxis = profile.setAxis as AxisConfig | null;
    const ladder = profile.difficultyLadder as string[] | null;

    let field = '';
    let previousValue = 0;
    let newValue = 0;
    let reason = '';
    const appliedAxis = state.currentAxis;

    const currentAxis = state.currentAxis;

    if (currentAxis === 'load' && loadAxis) {
      const current = state.currentWeightKg ?? loadAxis.default;
      const result = applyAxisStep(current, loadAxis, 'down');
      field = 'weightKg';
      previousValue = current;
      newValue = result.newValue;
      reason = `Load decreased for safety: ${current} → ${result.newValue}kg`;
    } else if (currentAxis === 'reps' && repAxis) {
      const current = state.currentTargetReps ?? repAxis.default;
      const result = applyAxisStep(current, repAxis, 'down');
      field = 'targetReps';
      previousValue = current;
      newValue = result.newValue;
      reason = `Reps decreased: ${current} → ${result.newValue}`;
    } else if (currentAxis === 'duration' && durationAxis) {
      const current = state.currentTargetDuration ?? durationAxis.default;
      const result = applyAxisStep(current, durationAxis, 'down');
      field = 'targetDuration';
      previousValue = current;
      newValue = result.newValue;
      reason = `Duration decreased: ${current}s → ${result.newValue}s`;
    } else if (currentAxis === 'sets' && setAxis) {
      const current = state.currentTargetSets ?? setAxis.default;
      const result = applyAxisStep(current, setAxis, 'down');
      field = 'sets';
      previousValue = current;
      newValue = result.newValue;
      reason = `Sets decreased: ${current} → ${result.newValue}`;
    } else if (currentAxis === 'difficulty' && ladder && ladder.length > 0) {
      const result = advanceDifficulty(state.currentDifficultyCode, ladder, 'down');
      if (result) {
        field = 'difficultyCode';
        previousValue = state.currentDifficultyCode ? ladder.indexOf(state.currentDifficultyCode) : 0;
        newValue = ladder.indexOf(result.newCode);
        reason = `Difficulty regressed: ${state.currentDifficultyCode} → ${result.newCode}`;
      }
    }

    if (!field) return null;

    const updateData: any = {
      regressionStreak: state.regressionStreak + 1,
      successStreak: 0,
      lastProgressedAt: new Date(),
    };

    if (field === 'weightKg') updateData.currentWeightKg = newValue;
    if (field === 'targetReps') updateData.currentTargetReps = newValue;
    if (field === 'targetDuration') updateData.currentTargetDuration = newValue;
    if (field === 'sets') updateData.currentTargetSets = newValue;
    if (field === 'difficultyCode') {
      updateData.currentDifficultyCode = ladder![newValue];
    }

    await prisma.userProgramExerciseProgressionState.update({
      where: { id: state.id },
      data: updateData,
    });

    await this.materializeToNextItem(userProgramId, exerciseId);

    const updatedState = await prisma.userProgramExerciseProgressionState.findUnique({
      where: { userProgramId_exerciseId: { userProgramId, exerciseId } },
    });
    const stateAfter = updatedState ? {
      currentAxis: updatedState.currentAxis,
      currentWeightKg: updatedState.currentWeightKg,
      currentTargetReps: updatedState.currentTargetReps,
      currentTargetDuration: updatedState.currentTargetDuration,
      currentTargetSets: updatedState.currentTargetSets,
      currentDifficultyCode: updatedState.currentDifficultyCode,
      successStreak: updatedState.successStreak,
      regressionStreak: updatedState.regressionStreak,
    } : stateBefore;

    await prisma.progressionHistory.create({
      data: {
        userId,
        sessionId,
        field,
        previousValue,
        newValue,
        reason,
        decisionType: 'regression',
        axis: appliedAxis,
        eligibilitySnapshot: summary as any,
        stateBefore: stateBefore as any,
        stateAfter: stateAfter as any,
      },
    });

    return {
      field,
      previousValue,
      newValue,
      reason,
      axis: appliedAxis,
      decisionType: 'regression',
      notification: {
        ar: `تعديل: ${reason}`,
        en: `Adjusted: ${reason}`,
      },
    };
  },

  async materializeToNextItem(userProgramId: string, exerciseId: string) {
    const prisma = await getPrisma();

    const state = await prisma.userProgramExerciseProgressionState.findUnique({
      where: { userProgramId_exerciseId: { userProgramId, exerciseId } },
    });
    if (!state) return;

    const nextItems = await prisma.programSessionItem.findMany({
      where: {
        exerciseId,
        session: {
          day: {
            week: {
              program: {
                userPrograms: { some: { id: userProgramId, isActive: true } },
              },
            },
          },
          reports: { none: { status: 'completed' } },
        },
      },
      orderBy: { sortOrder: 'asc' },
    });

    for (const item of nextItems) {
      await prisma.programSessionItem.update({
        where: { id: item.id },
        data: {
          ...(state.currentTargetReps != null ? { targetReps: state.currentTargetReps } : {}),
          ...(state.currentWeightKg != null ? { weightKg: state.currentWeightKg } : {}),
          ...(state.currentTargetDuration != null ? { targetDuration: state.currentTargetDuration } : {}),
          ...(state.currentTargetSets != null ? { sets: state.currentTargetSets } : {}),
          ...(state.currentDifficultyCode != null ? { difficultyCode: state.currentDifficultyCode } : {}),
          isPersonalized: true,
        },
      });
    }
  },

  async getHistory(userId: string, limit = 20): Promise<unknown[]> {
    const prisma = await getPrisma();

    const entries = await prisma.progressionHistory.findMany({
      where: { userId },
      orderBy: { appliedAt: 'desc' },
      take: limit,
    });

    return entries.map((e) => ({
      id: e.id,
      field: e.field,
      previousValue: e.previousValue,
      newValue: e.newValue,
      reason: e.reason,
      axis: e.axis,
      decisionType: e.decisionType,
      appliedAt: e.appliedAt.toISOString(),
      seen: e.seen,
    }));
  },

  async getRecent(userId: string): Promise<unknown[]> {
    const prisma = await getPrisma();

    const entries = await prisma.progressionHistory.findMany({
      where: {
        userId,
        seen: false,
        decisionType: { in: ['promotion', 'regression'] },
      },
      orderBy: { appliedAt: 'desc' },
      take: 10,
    });

    return entries.map((e) => ({
      id: e.id,
      field: e.field,
      previousValue: e.previousValue,
      newValue: e.newValue,
      reason: e.reason,
      axis: e.axis,
      decisionType: e.decisionType,
      appliedAt: e.appliedAt.toISOString(),
      seen: false,
    }));
  },

  async getBySession(userId: string, sessionId: string): Promise<unknown[]> {
    const prisma = await getPrisma();

    const entries = await prisma.progressionHistory.findMany({
      where: { userId, sessionId },
      orderBy: { appliedAt: 'desc' },
    });

    return entries.map((e) => ({
      id: e.id,
      field: e.field,
      previousValue: e.previousValue,
      newValue: e.newValue,
      reason: e.reason,
      axis: e.axis,
      decisionType: e.decisionType,
      appliedAt: e.appliedAt.toISOString(),
      seen: e.seen,
    }));
  },

  async markSeen(userId: string, ids: string[]): Promise<number> {
    const prisma = await getPrisma();

    const result = await prisma.progressionHistory.updateMany({
      where: { userId, id: { in: ids }, seen: false },
      data: { seen: true, seenAt: new Date() },
    });

    return result.count;
  },
};
