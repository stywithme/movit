/**
 * Progression Engine — Evaluates performance data and adjusts training parameters.
 *
 * Uses ExerciseProgressionProfile (archetype-based) for bounds/gates,
 * and ProgressionRule for runtime decisions.
 *
 * Quality Gate: form/stability/symmetry must pass threshold BEFORE any quantity change.
 * Bounds: weight/reps/duration never exceed profile-defined min/max.
 */

import { getPrisma } from '@/lib/prisma/client';
import { intX10ToFloat } from '@/lib/metrics';
import type { ProgressionCondition } from './archetype-defaults';

// ── Types ──

interface ProgressionAction {
  type: string;
  amount: number | null;
  notification: Record<string, string> | null;
}

export interface ProgressionChange {
  ruleId: string;
  ruleName: string;
  field: string;
  previousValue: number;
  newValue: number;
  reason: string;
  notification: Record<string, string> | null;
}

export interface EvaluateOptions {
  scope: 'global_and_program' | 'exercise';
  exerciseId?: string;
  programId: string;
}

interface ProfileBounds {
  repRange?: { min: number; max: number } | null;
  weightBounds?: { min: number; max: number; step: number } | null;
  durationBounds?: { min: number; max: number; step: number } | null;
  qualityGate?: { metric: string; threshold: number } | null;
}

// ── Metric Resolution ──

async function resolveMetric(
  userId: string,
  exerciseId: string | undefined,
  programId: string | undefined,
  metric: string,
  window: string,
): Promise<number | null> {
  const prisma = await getPrisma();

  const dateFilter = window === 'last_week'
    ? { gte: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000) }
    : undefined;

  if (metric === 'avgFormScore' || metric === 'avgROM' || metric === 'avgSymmetry' || metric === 'avgStability') {
    const take = window === 'last_session' ? 1
      : window === 'last_2_sessions' ? 2
      : undefined;

    const sessions = await prisma.trainingSession.findMany({
      where: {
        userId,
        context: 'program',
        ...(exerciseId ? { exerciseId } : {}),
        ...(dateFilter ? { timestamp: dateFilter } : {}),
      },
      include: { sessionMetrics: true },
      orderBy: { timestamp: 'desc' },
      ...(take ? { take } : {}),
    });

    if (sessions.length === 0) return null;

    const metricsArr = sessions
      .map((s) => s.sessionMetrics)
      .filter((m) => m !== null);

    if (metricsArr.length === 0) return null;

    switch (metric) {
      case 'avgFormScore':
        return metricsArr.reduce((sum, m) => sum + intX10ToFloat(m.avgFormScore), 0) / metricsArr.length;
      case 'avgROM':
        return metricsArr.reduce((sum, m) => sum + intX10ToFloat(m.avgRom), 0) / metricsArr.length;
      case 'avgSymmetry': {
        const symValues = metricsArr.filter((m) => m.avgSymmetry != null);
        if (symValues.length === 0) return null;
        return symValues.reduce((sum, m) => sum + intX10ToFloat(m.avgSymmetry!), 0) / symValues.length;
      }
      case 'avgStability':
        return metricsArr.reduce((sum, m) => sum + intX10ToFloat(m.avgStability), 0) / metricsArr.length;
      default:
        return null;
    }
  }

  if (metric === 'completionRate') {
    const take = window === 'last_session' ? 1
      : window === 'last_2_sessions' ? 2
      : undefined;

    const reports = await prisma.programSessionReport.findMany({
      where: {
        userId,
        ...(programId ? { programId } : {}),
        status: 'completed',
        ...(dateFilter ? { completedAt: dateFilter } : {}),
      },
      orderBy: { completedAt: 'desc' },
      ...(take ? { take } : {}),
    });

    if (reports.length === 0) return null;

    const rates = reports.map((r) => {
      if (r.avgAccuracy != null && r.avgAccuracy > 0) return r.avgAccuracy;
      const totalSets = r.totalSets ?? 0;
      if (totalSets === 0) return 100;
      const completedSets = r.completedSets ?? 0;
      return (completedSets / totalSets) * 100;
    });

    return rates.reduce((sum, r) => sum + r, 0) / rates.length;
  }

  return null;
}

// ── Condition Evaluation ──

function evaluateCondition(metricValue: number | null, operator: string, threshold: number): boolean {
  if (metricValue === null) return false;
  switch (operator) {
    case '>=': return metricValue >= threshold;
    case '<=': return metricValue <= threshold;
    case '>': return metricValue > threshold;
    case '<': return metricValue < threshold;
    case '==': return Math.abs(metricValue - threshold) < 0.01;
    default: return false;
  }
}

// ── Bounds Enforcement ──

function clampToBounds(field: string, value: number, bounds: ProfileBounds): number {
  if (field === 'weightKg' && bounds.weightBounds) {
    return Math.min(bounds.weightBounds.max, Math.max(bounds.weightBounds.min, value));
  }
  if (field === 'targetReps' && bounds.repRange) {
    return Math.min(bounds.repRange.max, Math.max(bounds.repRange.min, value));
  }
  if (field === 'targetDuration' && bounds.durationBounds) {
    return Math.min(bounds.durationBounds.max, Math.max(bounds.durationBounds.min, value));
  }
  return Math.max(0, value);
}

// ── Service ──

export const progressionService = {
  async evaluateAfterSession(
    userId: string,
    sessionId: string,
    options: EvaluateOptions,
  ): Promise<ProgressionChange[]> {
    const prisma = await getPrisma();
    const changes: ProgressionChange[] = [];
    const { scope, exerciseId, programId } = options;

    // Build scope filter to avoid duplicate rule evaluation
    const scopeFilters: Record<string, unknown>[] = [];

    if (scope === 'global_and_program') {
      scopeFilters.push({ scope: 'global' });
      scopeFilters.push({ scope: 'program', programId });
    } else if (scope === 'exercise' && exerciseId) {
      scopeFilters.push({ scope: 'exercise', exerciseId });
    }

    if (scopeFilters.length === 0) return changes;

    const rules = await prisma.progressionRule.findMany({
      where: {
        isActive: true,
        trigger: 'session_completed',
        OR: scopeFilters,
      },
      orderBy: { priority: 'desc' },
    });

    // Load profile for exercise-scoped evaluation (for bounds/gates)
    let profileBounds: ProfileBounds = {};
    if (exerciseId) {
      const profile = await prisma.exerciseProgressionProfile.findUnique({
        where: { exerciseId },
      });
      if (profile) {
        profileBounds = {
          repRange: profile.repRange as ProfileBounds['repRange'],
          weightBounds: profile.weightBounds as ProfileBounds['weightBounds'],
          durationBounds: profile.durationBounds as ProfileBounds['durationBounds'],
          qualityGate: profile.qualityGate as ProfileBounds['qualityGate'],
        };
      }
    }

    // Quality gate check: if profile defines a gate, verify it before any promotion
    if (profileBounds.qualityGate) {
      const gateValue = await resolveMetric(
        userId, exerciseId, programId,
        profileBounds.qualityGate.metric, 'last_2_sessions',
      );
      if (gateValue !== null && gateValue < profileBounds.qualityGate.threshold) {
        // Quality gate not passed — skip promotion rules, only allow regression
        const regressionRules = rules.filter((r) => {
          const action = r.action as unknown as ProgressionAction;
          return action?.type?.startsWith('decrease') || action?.type === 'suggest_reassessment';
        });

        for (const rule of regressionRules) {
          const conditions = Array.isArray(rule.conditions) ? rule.conditions as unknown as ProgressionCondition[] : [];
          const action = rule.action as unknown as ProgressionAction;
          if (!action || conditions.length === 0) continue;

          let allMet = true;
          for (const cond of conditions) {
            const value = await resolveMetric(userId, exerciseId, programId, cond.metric, cond.window);
            if (!evaluateCondition(value, cond.operator, cond.value)) {
              allMet = false;
              break;
            }
          }

          if (allMet) {
            const change = await this.executeAction(userId, rule.id, rule.name, sessionId, action, exerciseId, programId, profileBounds);
            if (change) changes.push(change);
          }
        }

        return changes;
      }
    }

    for (const rule of rules) {
      const conditions = Array.isArray(rule.conditions) ? rule.conditions as unknown as ProgressionCondition[] : [];
      const action = rule.action as unknown as ProgressionAction;

      if (!action || conditions.length === 0) continue;

      let allMet = true;
      for (const cond of conditions) {
        const value = await resolveMetric(userId, exerciseId, programId, cond.metric, cond.window);
        if (!evaluateCondition(value, cond.operator, cond.value)) {
          allMet = false;
          break;
        }
      }

      if (!allMet) continue;

      const change = await this.executeAction(userId, rule.id, rule.name, sessionId, action, exerciseId, programId, profileBounds);
      if (change) {
        changes.push(change);
      }
    }

    return changes;
  },

  async executeAction(
    userId: string,
    ruleId: string,
    ruleName: string,
    sessionId: string,
    action: ProgressionAction,
    exerciseId?: string,
    programId?: string,
    bounds?: ProfileBounds,
  ): Promise<ProgressionChange | null> {
    const prisma = await getPrisma();
    const amount = action.amount ?? 0;

    if (action.type === 'suggest_reassessment') {
      await prisma.reassessmentSchedule.create({
        data: {
          userId,
          reason: 'progression_trigger',
          scheduledDate: new Date(Date.now() + 24 * 60 * 60 * 1000),
          status: 'pending',
          notes: `Triggered by rule: ${ruleName}`,
        },
      });

      const reason = `Rule "${ruleName}": reassessment scheduled`;
      await prisma.progressionHistory.create({
        data: { userId, ruleId, sessionId, field: 'reassessment', previousValue: 0, newValue: 1, reason },
      });

      return {
        ruleId, ruleName, field: 'reassessment', previousValue: 0, newValue: 1, reason,
        notification: action.notification ?? null,
      };
    }

    if (!programId) return null;

    const nextItem = exerciseId
      ? await prisma.programSessionItem.findFirst({
          where: {
            exerciseId,
            session: {
              day: { week: { program: { userPrograms: { some: { userId, isActive: true, programId } } } } },
              reports: { none: { userId, status: 'completed' } },
            },
          },
          orderBy: { sortOrder: 'asc' },
        })
      : await prisma.programSessionItem.findFirst({
          where: {
            type: 'exercise',
            session: {
              day: { week: { program: { userPrograms: { some: { userId, isActive: true, programId } } } } },
              reports: { none: { userId, status: 'completed' } },
            },
          },
          orderBy: { sortOrder: 'asc' },
        });

    if (!nextItem) return null;

    let field = '';
    let previousValue = 0;
    let newValue = 0;

    switch (action.type) {
      case 'increase_weight':
        field = 'weightKg';
        previousValue = nextItem.weightKg ?? 0;
        newValue = clampToBounds(field, previousValue + amount, bounds ?? {});
        if (newValue === previousValue) return null;
        await prisma.programSessionItem.update({
          where: { id: nextItem.id },
          data: { weightKg: newValue, isPersonalized: true },
        });
        break;

      case 'decrease_weight':
        field = 'weightKg';
        previousValue = nextItem.weightKg ?? 0;
        newValue = clampToBounds(field, previousValue - amount, bounds ?? {});
        if (newValue === previousValue) return null;
        await prisma.programSessionItem.update({
          where: { id: nextItem.id },
          data: { weightKg: newValue, isPersonalized: true },
        });
        break;

      case 'increase_reps':
        field = 'targetReps';
        previousValue = nextItem.targetReps ?? 0;
        newValue = clampToBounds(field, previousValue + amount, bounds ?? {});
        if (newValue === previousValue) return null;
        await prisma.programSessionItem.update({
          where: { id: nextItem.id },
          data: { targetReps: Math.round(newValue), isPersonalized: true },
        });
        break;

      case 'decrease_reps':
        field = 'targetReps';
        previousValue = nextItem.targetReps ?? 0;
        newValue = clampToBounds(field, previousValue - amount, bounds ?? {});
        if (newValue === previousValue) return null;
        await prisma.programSessionItem.update({
          where: { id: nextItem.id },
          data: { targetReps: Math.round(newValue), isPersonalized: true },
        });
        break;

      case 'increase_duration':
        field = 'targetDuration';
        previousValue = nextItem.targetDuration ?? 0;
        newValue = clampToBounds(field, previousValue + amount, bounds ?? {});
        if (newValue === previousValue) return null;
        await prisma.programSessionItem.update({
          where: { id: nextItem.id },
          data: { targetDuration: Math.round(newValue), isPersonalized: true },
        });
        break;

      case 'decrease_duration':
        field = 'targetDuration';
        previousValue = nextItem.targetDuration ?? 0;
        newValue = clampToBounds(field, previousValue - amount, bounds ?? {});
        if (newValue === previousValue) return null;
        await prisma.programSessionItem.update({
          where: { id: nextItem.id },
          data: { targetDuration: Math.round(newValue), isPersonalized: true },
        });
        break;

      case 'increase_sets':
        field = 'sets';
        previousValue = nextItem.sets ?? 1;
        newValue = previousValue + amount;
        await prisma.programSessionItem.update({
          where: { id: nextItem.id },
          data: { sets: Math.round(newValue), isPersonalized: true },
        });
        break;

      default:
        return null;
    }

    const reason = `Rule "${ruleName}": ${field} ${previousValue} → ${newValue}`;

    await prisma.progressionHistory.create({
      data: { userId, ruleId, sessionId, field, previousValue, newValue, reason },
    });

    return {
      ruleId, ruleName, field, previousValue, newValue, reason,
      notification: action.notification ?? null,
    };
  },

  async getHistory(userId: string, limit = 20): Promise<unknown[]> {
    const prisma = await getPrisma();

    const entries = await prisma.progressionHistory.findMany({
      where: { userId },
      include: { rule: { select: { name: true } } },
      orderBy: { appliedAt: 'desc' },
      take: limit,
    });

    return entries.map((e) => ({
      id: e.id,
      ruleName: e.rule.name,
      field: e.field,
      previousValue: e.previousValue,
      newValue: e.newValue,
      reason: e.reason,
      appliedAt: e.appliedAt.toISOString(),
      seen: e.seen,
    }));
  },

  async getRecent(userId: string): Promise<unknown[]> {
    const prisma = await getPrisma();

    const entries = await prisma.progressionHistory.findMany({
      where: { userId, seen: false },
      include: {
        rule: { select: { name: true, exerciseId: true } },
      },
      orderBy: { appliedAt: 'desc' },
      take: 10,
    });

    const exerciseIds = [
      ...new Set(entries.map((e) => e.rule.exerciseId).filter(Boolean) as string[]),
    ];
    const exerciseMap = new Map<string, Record<string, string>>();

    if (exerciseIds.length > 0) {
      const exercises = await prisma.exercise.findMany({
        where: { id: { in: exerciseIds } },
        select: { id: true, name: true, slug: true },
      });
      for (const ex of exercises) {
        exerciseMap.set(ex.id, ex.name as Record<string, string>);
      }
    }

    return entries.map((e) => {
      const exId = e.rule.exerciseId ?? null;
      return {
        id: e.id,
        ruleName: e.rule.name,
        exerciseName: exId ? (exerciseMap.get(exId) ?? null) : null,
        exerciseId: exId,
        field: e.field,
        previousValue: e.previousValue,
        newValue: e.newValue,
        reason: e.reason,
        appliedAt: e.appliedAt.toISOString(),
        seen: false,
      };
    });
  },

  /**
   * Session-specific progression data — independent of seen status.
   * Always returns changes for this session regardless of whether they've been seen.
   */
  async getBySession(userId: string, sessionId: string): Promise<unknown[]> {
    const prisma = await getPrisma();

    const entries = await prisma.progressionHistory.findMany({
      where: { userId, sessionId },
      include: {
        rule: { select: { name: true, exerciseId: true } },
      },
      orderBy: { appliedAt: 'desc' },
    });

    const exerciseIds = [
      ...new Set(entries.map((e) => e.rule.exerciseId).filter(Boolean) as string[]),
    ];
    const exerciseMap = new Map<string, Record<string, string>>();

    if (exerciseIds.length > 0) {
      const exercises = await prisma.exercise.findMany({
        where: { id: { in: exerciseIds } },
        select: { id: true, name: true, slug: true },
      });
      for (const ex of exercises) {
        exerciseMap.set(ex.id, ex.name as Record<string, string>);
      }
    }

    return entries.map((e) => {
      const exId = e.rule.exerciseId ?? null;
      return {
        id: e.id,
        ruleName: e.rule.name,
        exerciseName: exId ? (exerciseMap.get(exId) ?? null) : null,
        exerciseId: exId,
        field: e.field,
        previousValue: e.previousValue,
        newValue: e.newValue,
        reason: e.reason,
        appliedAt: e.appliedAt.toISOString(),
        seen: e.seen,
      };
    });
  },

  async markSeen(userId: string, ids: string[]): Promise<number> {
    const prisma = await getPrisma();

    const result = await prisma.progressionHistory.updateMany({
      where: {
        userId,
        id: { in: ids },
        seen: false,
      },
      data: {
        seen: true,
        seenAt: new Date(),
      },
    });

    return result.count;
  },
};
