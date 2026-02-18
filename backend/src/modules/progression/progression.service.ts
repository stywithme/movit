/**
 * Progression Engine — Evaluates performance data and adjusts training parameters.
 *
 * Starts with 3 rules (architecture plan Section 7):
 *   1. Weight increase: avgFormScore >= 75 for last 2 sessions, completionRate >= 90% → +2.5kg
 *   2. Rep increase: avgFormScore >= 80 for full week, avgROM >= 95% → +2 reps
 *   3. Deload safety: avgFormScore < 60 for last 2 sessions → -2.5kg + notification
 *
 * The engine always works with Float 0-100 values (Metrics Contract Section 5.4).
 */

import { getPrisma } from '@/lib/prisma/client';
import { intX10ToFloat } from '@/lib/metrics';

// ── Types ──

interface ProgressionCondition {
  metric: string;  // avgFormScore | completionRate | avgROM | totalVolume | symmetryScore
  operator: string; // >= | <= | > | < | ==
  value: number;
  window: string;  // last_session | last_2_sessions | last_week | all_program
}

interface ProgressionAction {
  type: string;    // increase_weight | decrease_weight | increase_reps | decrease_reps | increase_sets | change_difficulty | suggest_reassessment
  amount: number | null;
  notification: Record<string, string> | null; // { ar: "...", en: "..." }
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

// ── Metric Resolution ──

async function resolveMetric(
  userId: string,
  exerciseId: string | undefined,
  programId: string | undefined,
  metric: string,
  window: string,
): Promise<number | null> {
  const prisma = await getPrisma();

  // Build date filter for 'last_week' window (actual 7-day window)
  const dateFilter = window === 'last_week'
    ? { gte: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000) }
    : undefined;

  if (metric === 'avgFormScore' || metric === 'avgROM' || metric === 'avgSymmetry' || metric === 'avgStability') {
    const take = window === 'last_session' ? 1
      : window === 'last_2_sessions' ? 2
      : undefined; // 'last_week' uses date filter, 'all_program' gets all

    const sessions = await prisma.trainingSession.findMany({
      where: {
        userId,
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
      if (!r.totalReps || r.totalReps === 0) return 100;
      const planned = r.totalReps;
      const done = r.completedSets ?? r.totalSets ?? 0;
      return (r.avgAccuracy ?? (done / planned) * 100);
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

// ── Service ──

export const progressionService = {
  /**
   * Evaluate all applicable progression rules after a session is completed.
   * Returns list of changes that were applied.
   */
  async evaluateAfterSession(
    userId: string,
    sessionId: string,
    exerciseId?: string,
    programId?: string,
  ): Promise<ProgressionChange[]> {
    const prisma = await getPrisma();
    const changes: ProgressionChange[] = [];

    // Load applicable rules (priority: global → program → exercise)
    const scopeFilters: Record<string, unknown>[] = [
      { scope: 'global' },
    ];
    if (programId) {
      scopeFilters.push({ scope: 'program', programId });
    }
    if (exerciseId) {
      scopeFilters.push({ scope: 'exercise', exerciseSlug: exerciseId });
    }

    const rules = await prisma.progressionRule.findMany({
      where: {
        isActive: true,
        trigger: 'session_completed',
        OR: scopeFilters,
      },
      orderBy: { priority: 'desc' },
    });

    for (const rule of rules) {
      const conditions = Array.isArray(rule.conditions) ? rule.conditions as unknown as ProgressionCondition[] : [];
      const action = rule.action as unknown as ProgressionAction;

      if (!action || conditions.length === 0) continue;

      // Evaluate all conditions
      let allMet = true;
      for (const cond of conditions) {
        const value = await resolveMetric(userId, exerciseId, programId, cond.metric, cond.window);
        if (!evaluateCondition(value, cond.operator, cond.value)) {
          allMet = false;
          break;
        }
      }

      if (!allMet) continue;

      // Execute action
      const change = await this.executeAction(userId, rule.id, rule.name, sessionId, action, exerciseId, programId);
      if (change) {
        changes.push(change);
      }
    }

    return changes;
  },

  /**
   * Execute a progression action and log the change.
   */
  async executeAction(
    userId: string,
    ruleId: string,
    ruleName: string,
    sessionId: string,
    action: ProgressionAction,
    exerciseId?: string,
    programId?: string,
  ): Promise<ProgressionChange | null> {
    const prisma = await getPrisma();
    const amount = action.amount ?? 0;

    // Handle suggest_reassessment (doesn't need a session item)
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

    // Find the next session item to adjust.
    // For exercise-specific rules: find that exact exercise.
    // For global/program rules: find next uncompleted session's first exercise item.
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
        newValue = previousValue + amount;
        await prisma.programSessionItem.update({
          where: { id: nextItem.id },
          data: { weightKg: newValue, isPersonalized: true },
        });
        break;

      case 'decrease_weight':
        field = 'weightKg';
        previousValue = nextItem.weightKg ?? 0;
        newValue = Math.max(0, previousValue - amount);
        await prisma.programSessionItem.update({
          where: { id: nextItem.id },
          data: { weightKg: newValue, isPersonalized: true },
        });
        break;

      case 'increase_reps':
        field = 'targetReps';
        previousValue = nextItem.targetReps ?? 0;
        newValue = previousValue + amount;
        await prisma.programSessionItem.update({
          where: { id: nextItem.id },
          data: { targetReps: Math.round(newValue), isPersonalized: true },
        });
        break;

      case 'decrease_reps':
        field = 'targetReps';
        previousValue = nextItem.targetReps ?? 0;
        newValue = Math.max(1, previousValue - amount);
        await prisma.programSessionItem.update({
          where: { id: nextItem.id },
          data: { targetReps: Math.round(newValue), isPersonalized: true },
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

    // Log the change
    const reason = `Rule "${ruleName}": ${field} ${previousValue} → ${newValue}`;

    await prisma.progressionHistory.create({
      data: {
        userId,
        ruleId,
        sessionId,
        field,
        previousValue,
        newValue,
        reason,
      },
    });

    return {
      ruleId,
      ruleName,
      field,
      previousValue,
      newValue,
      reason,
      notification: action.notification ?? null,
    };
  },

  /**
   * Get progression history for a user.
   */
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
    }));
  },
};
