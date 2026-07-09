import { Injectable } from '@nestjs/common';
import { getPrisma } from '@/lib/prisma/client';

type PeriodKey = '7d' | '30d' | '90d' | 'all' | 'custom';

export interface AnalyticsPeriodQuery {
  period?: PeriodKey;
  from?: string;
  to?: string;
  limit?: string | number;
}

export interface ResolvedPeriod {
  period: PeriodKey;
  start: Date | null;
  end: Date;
  prevStart: Date | null;
  prevEnd: Date | null;
  bucket: 'day' | 'week' | 'month';
}

export interface MetricDelta {
  value: number;
  previous: number;
  delta: number;
}

interface SeriesPoint {
  date: string;
  value: number;
}

const DAY_MS = 24 * 60 * 60 * 1000;

function round(value: number, digits = 1) {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}

function toNumber(value: unknown) {
  if (value == null) return 0;
  if (typeof value === 'number') return value;
  if (typeof value === 'bigint') return Number(value);
  if (typeof value === 'object' && 'toNumber' in value && typeof value.toNumber === 'function') {
    return value.toNumber();
  }
  return Number(value) || 0;
}

function dateKey(date: Date, bucket: ResolvedPeriod['bucket']) {
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  const day = String(date.getUTCDate()).padStart(2, '0');
  if (bucket === 'month') return `${year}-${month}`;
  if (bucket === 'week') {
    const firstDay = new Date(Date.UTC(year, date.getUTCMonth(), date.getUTCDate()));
    const dayOfWeek = firstDay.getUTCDay();
    firstDay.setUTCDate(firstDay.getUTCDate() - dayOfWeek);
    return `${firstDay.getUTCFullYear()}-${String(firstDay.getUTCMonth() + 1).padStart(2, '0')}-${String(firstDay.getUTCDate()).padStart(2, '0')}`;
  }
  return `${year}-${month}-${day}`;
}

function groupByDate<T>(items: T[], getDate: (item: T) => Date, getValue: (item: T) => number, bucket: ResolvedPeriod['bucket']) {
  const map = new Map<string, number>();
  for (const item of items) {
    const key = dateKey(getDate(item), bucket);
    map.set(key, (map.get(key) ?? 0) + getValue(item));
  }
  return Array.from(map.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, value]) => ({ date, value: round(value, 2) }));
}

function metric(value: number, previous: number): MetricDelta {
  const delta = previous === 0 ? (value > 0 ? 100 : 0) : ((value - previous) / previous) * 100;
  return { value: round(value, 2), previous: round(previous, 2), delta: round(delta, 1) };
}

@Injectable()
export class AdminReportsService {
  resolvePeriod(query: AnalyticsPeriodQuery = {}): ResolvedPeriod {
    const now = new Date();
    const period = query.period ?? '30d';

    if (period === 'custom' && query.from && query.to) {
      const start = new Date(query.from);
      const end = new Date(query.to);
      const duration = Math.max(end.getTime() - start.getTime(), DAY_MS);
      return {
        period,
        start,
        end,
        prevStart: new Date(start.getTime() - duration),
        prevEnd: new Date(start.getTime()),
        bucket: duration > 90 * DAY_MS ? 'week' : 'day',
      };
    }

    if (period === 'all') {
      return { period, start: null, end: now, prevStart: null, prevEnd: null, bucket: 'month' };
    }

    const days = period === '7d' ? 7 : period === '90d' ? 90 : 30;
    const start = new Date(now.getTime() - days * DAY_MS);
    return {
      period,
      start,
      end: now,
      prevStart: new Date(start.getTime() - days * DAY_MS),
      prevEnd: start,
      bucket: days > 60 ? 'week' : 'day',
    };
  }

  private dateWhere(field: string, period: ResolvedPeriod) {
    if (!period.start) return {};
    return { [field]: { gte: period.start, lte: period.end } };
  }

  private prevDateWhere(field: string, period: ResolvedPeriod) {
    if (!period.prevStart || !period.prevEnd) return {};
    return { [field]: { gte: period.prevStart, lt: period.prevEnd } };
  }

  private async countWithDelta(delegate: any, field: string, period: ResolvedPeriod, extraWhere: Record<string, unknown> = {}) {
    const [value, previous] = await Promise.all([
      delegate.count({ where: { ...extraWhere, ...this.dateWhere(field, period) } }),
      period.prevStart ? delegate.count({ where: { ...extraWhere, ...this.prevDateWhere(field, period) } }) : Promise.resolve(0),
    ]);
    return metric(value, previous);
  }

  async getOverview(query: AnalyticsPeriodQuery = {}) {
    const prisma = await getPrisma();
    const period = this.resolvePeriod(query);

    const [
      totalUsers,
      newUsers,
      activeWorkoutExecutions,
      proUsers,
      subscriptions,
      workoutExecutions,
      prevWorkoutExecutions,
      executionMetrics,
      assessments,
      levels,
      pendingReassessments,
      abandonedReports,
    ] = await Promise.all([
      prisma.user.count({ where: { deletedAt: null } }),
      this.countWithDelta(prisma.user, 'createdAt', period, { deletedAt: null }),
      prisma.workoutExecution.findMany({ where: this.dateWhere('timestamp', period), select: { userId: true, timestamp: true } }),
      prisma.user.count({ where: { isPro: true, deletedAt: null } }),
      prisma.subscription.findMany({
        where: { ...this.dateWhere('createdAt', period), status: { in: ['active', 'paid', 'completed'] } },
        select: { amountPaid: true, createdAt: true, status: true, billingPeriod: true },
      }),
      prisma.workoutExecution.findMany({
        where: this.dateWhere('timestamp', period),
        select: { timestamp: true, durationMs: true, totalReps: true, countedReps: true, invalidReps: true, context: true },
      }),
      period.prevStart
        ? prisma.workoutExecution.count({ where: this.prevDateWhere('timestamp', period) })
        : Promise.resolve(0),
      prisma.workoutExecutionMetrics.findMany({
        where: { workoutExecution: this.dateWhere('timestamp', period) as any },
        select: { avgFormScore: true, avgRom: true, avgSymmetry: true, avgStability: true, fatigueIndex: true },
      }),
      this.countWithDelta(prisma.bodyScanResult, 'completedAt', period),
      this.getLatestLevelDistribution(),
      prisma.reassessmentSchedule.count({ where: { status: 'pending' } }),
      prisma.plannedWorkoutReport.count({ where: { status: 'abandoned', ...this.dateWhere('startedAt', period) } }),
    ]);

    const activeUsers = new Set(activeWorkoutExecutions.map((s) => s.userId)).size;
    const revenue = subscriptions.reduce((sum, s) => sum + toNumber(s.amountPaid), 0);
    const avgFormScore = executionMetrics.length
      ? round(executionMetrics.reduce((sum, m) => sum + (m.avgFormScore ?? 0), 0) / executionMetrics.length)
      : 0;

    const firstWeekStar = await this.getNorthStar(period);

    return {
      period,
      kpis: {
        totalUsers: metric(totalUsers, 0),
        newUsers,
        activeUsers: metric(activeUsers, 0),
        proUsers: metric(proUsers, 0),
        revenue: metric(revenue, 0),
        workoutExecutions: metric(workoutExecutions.length, prevWorkoutExecutions),
        avgFormScore: metric(avgFormScore, 0),
        assessments,
      },
      northStar: firstWeekStar,
      activationFunnel: await this.getActivationFunnel(period),
      trends: {
        workoutExecutions: groupByDate(workoutExecutions, (s) => s.timestamp, () => 1, period.bucket),
        revenue: groupByDate(
          subscriptions.map((s) => ({ date: s.createdAt, value: toNumber(s.amountPaid) })),
          (item) => item.date,
          (item) => item.value,
          period.bucket,
        ),
      },
      distributions: {
        levels,
        executionContexts: this.countBy(workoutExecutions, (s) => s.context || 'unknown'),
      },
      safety: {
        dangerRepRate: this.safePercent(
          workoutExecutions.reduce((sum, s) => sum + (s.invalidReps ?? 0), 0),
          workoutExecutions.reduce((sum, s) => sum + (s.totalReps ?? 0), 0),
        ),
        abandonedReports,
      },
      alerts: {
        pendingReassessments,
        failedCheckouts: await prisma.subscriptionCheckout.count({ where: { status: 'failed', ...this.dateWhere('createdAt', period) } }),
        abandonedReports,
      },
    };
  }

  async getUsersGrowth(query: AnalyticsPeriodQuery = {}) {
    const prisma = await getPrisma();
    const period = this.resolvePeriod(query);

    const [users, profiles, goalGroups] = await Promise.all([
      prisma.user.findMany({
        where: this.dateWhere('createdAt', period),
        select: { id: true, createdAt: true, isActive: true, emailVerified: true, deletedAt: true, trainingGoal: true },
      }),
      prisma.trainingProfile.findMany({
        where: this.dateWhere('createdAt', period),
        select: { heightCm: true, weightKg: true, dateOfBirth: true, availableDaysPerWeek: true, trainingLocation: true },
      }),
      prisma.user.groupBy({ by: ['trainingGoal'], _count: { id: true }, where: { deletedAt: null } }),
    ]);

    return {
      total: users.length,
      trend: groupByDate(users, (u) => u.createdAt, () => 1, period.bucket),
      cumulative: this.cumulative(groupByDate(users, (u) => u.createdAt, () => 1, period.bucket)),
      status: [
        { name: 'Active', value: users.filter((u) => u.isActive && !u.deletedAt).length },
        { name: 'Inactive', value: users.filter((u) => !u.isActive && !u.deletedAt).length },
        { name: 'Deleted', value: users.filter((u) => u.deletedAt).length },
        { name: 'Email Verified', value: users.filter((u) => u.emailVerified).length },
      ],
      trainingGoals: goalGroups.map((g) => ({ name: g.trainingGoal, value: g._count.id })),
      demographics: {
        ageBuckets: this.bucketAges(profiles.map((p) => p.dateOfBirth).filter(Boolean) as Date[]),
        availableDays: this.countBy(profiles, (p) => String(p.availableDaysPerWeek ?? 'unknown')),
        trainingLocations: this.countBy(profiles, (p) => p.trainingLocation ?? 'unknown'),
        avgHeightCm: this.avg(profiles.map((p) => p.heightCm)),
        avgWeightKg: this.avg(profiles.map((p) => p.weightKg)),
      },
    };
  }

  async getActivation(query: AnalyticsPeriodQuery = {}) {
    const period = this.resolvePeriod(query);
    return {
      period,
      funnel: await this.getActivationFunnel(period),
      timeToFirstWorkout: await this.getTimeToFirstWorkout(period),
      northStar: await this.getNorthStar(period),
    };
  }

  async getRetention(query: AnalyticsPeriodQuery = {}) {
    const prisma = await getPrisma();
    const period = this.resolvePeriod(query);
    const users = await prisma.user.findMany({
      where: { deletedAt: null, ...this.dateWhere('createdAt', period) },
      select: {
        id: true,
        createdAt: true,
        workoutExecutions: { select: { timestamp: true }, orderBy: { timestamp: 'asc' } },
      },
    });
    const periodExecutions = await prisma.workoutExecution.findMany({ where: this.dateWhere('timestamp', period), select: { userId: true, timestamp: true } });
    const activeUserIds = new Set(periodExecutions.map((s) => s.userId));
    const lastSeen = new Map<string, Date>();
    for (const execution of periodExecutions) lastSeen.set(execution.userId, execution.timestamp);

    return {
      activeUsers: activeUserIds.size,
      stickiness: {
        dau: this.uniqueUsersByWindow(periodExecutions, 1),
        wau: this.uniqueUsersByWindow(periodExecutions, 7),
        mau: this.uniqueUsersByWindow(periodExecutions, 30),
      },
      workoutExecutionsPerActiveUser: activeUserIds.size ? round(periodExecutions.length / activeUserIds.size, 2) : 0,
      cohorts: this.buildRetentionCohorts(users),
      churnSignals: {
        inactive7d: await this.countInactiveUsers(7),
        inactive14d: await this.countInactiveUsers(14),
        inactive30d: await this.countInactiveUsers(30),
      },
    };
  }

  async getTrainingPerformance(query: AnalyticsPeriodQuery = {}) {
    const prisma = await getPrisma();
    const period = this.resolvePeriod(query);
    const [workoutExecutions, metrics, topExercises] = await Promise.all([
      prisma.workoutExecution.findMany({
        where: this.dateWhere('timestamp', period),
        select: { id: true, timestamp: true, context: true, totalReps: true, countedReps: true, invalidReps: true, durationMs: true },
      }),
      prisma.workoutExecutionMetrics.findMany({
        where: { workoutExecution: this.dateWhere('timestamp', period) as any },
        select: { avgFormScore: true, avgRom: true, avgSymmetry: true, avgStability: true, fatigueIndex: true, workoutExecution: { select: { timestamp: true } } },
      }),
      prisma.workoutExecution.groupBy({
        by: ['exerciseId'],
        _count: { id: true },
        _sum: { invalidReps: true, totalReps: true },
        where: this.dateWhere('timestamp', period),
        orderBy: { _count: { id: 'desc' } },
        take: 15,
      }),
    ]);
    const exerciseIds = topExercises.map((e) => e.exerciseId);
    const exercises = exerciseIds.length
      ? await prisma.exercise.findMany({ where: { id: { in: exerciseIds } }, select: { id: true, name: true, slug: true } })
      : [];
    const exerciseMap = new Map(exercises.map((e) => [e.id, e]));

    return {
      volume: {
        workoutExecutions: workoutExecutions.length,
        totalReps: workoutExecutions.reduce((sum, s) => sum + (s.totalReps ?? 0), 0),
        countedReps: workoutExecutions.reduce((sum, s) => sum + (s.countedReps ?? 0), 0),
        invalidReps: workoutExecutions.reduce((sum, s) => sum + (s.invalidReps ?? 0), 0),
        totalMinutes: round(workoutExecutions.reduce((sum, s) => sum + (s.durationMs ?? 0), 0) / 60000),
      },
      contexts: this.countBy(workoutExecutions, (s) => s.context || 'unknown'),
      trends: {
        workoutExecutions: groupByDate(workoutExecutions, (s) => s.timestamp, () => 1, period.bucket),
        formScore: groupByDate(metrics, (m) => m.workoutExecution.timestamp, (m) => m.avgFormScore ?? 0, period.bucket),
      },
      averages: {
        formScore: this.avg(metrics.map((m) => m.avgFormScore)),
        rom: this.avg(metrics.map((m) => m.avgRom)),
        symmetry: this.avg(metrics.map((m) => m.avgSymmetry)),
        stability: this.avg(metrics.map((m) => m.avgStability)),
        fatigueIndex: this.avg(metrics.map((m) => m.fatigueIndex)),
      },
      exercises: topExercises.map((row) => {
        const exercise = exerciseMap.get(row.exerciseId);
        return {
          exerciseId: row.exerciseId,
          name: this.localizedName(exercise?.name) || exercise?.slug || row.exerciseId,
          executions: row._count.id,
          dangerRepRate: this.safePercent(row._sum.invalidReps ?? 0, row._sum.totalReps ?? 0),
        };
      }),
    };
  }

  async getProgression(query: AnalyticsPeriodQuery = {}) {
    const prisma = await getPrisma();
    const period = this.resolvePeriod(query);
    const [history, states, rules] = await Promise.all([
      prisma.progressionHistory.findMany({
        where: this.dateWhere('appliedAt', period),
        select: { appliedAt: true, field: true, previousValue: true, newValue: true, decisionType: true, axis: true, ruleId: true },
      }),
      prisma.userProgramExerciseProgressionState.findMany({ select: { successStreak: true, regressionStreak: true, currentAxis: true } }),
      prisma.progressionRule.findMany({ select: { id: true, name: true, isActive: true } }),
    ]);
    const ruleMap = new Map(rules.map((r) => [r.id, r.name]));

    return {
      changes: history.length,
      activeRules: rules.filter((r) => r.isActive).length,
      trend: groupByDate(history, (h) => h.appliedAt, () => 1, period.bucket),
      fields: this.countBy(history, (h) => h.field),
      axes: this.countBy(history, (h) => h.axis ?? 'unknown'),
      decisions: this.countBy(history, (h) => h.decisionType ?? 'unknown'),
      streaks: {
        avgSuccess: this.avg(states.map((s) => s.successStreak)),
        avgRegression: this.avg(states.map((s) => s.regressionStreak)),
        byAxis: this.countBy(states, (s) => s.currentAxis),
      },
      topRules: this.countBy(history, (h) => ruleMap.get(h.ruleId ?? '') ?? 'No rule').slice(0, 10),
    };
  }

  async getRevenue(query: AnalyticsPeriodQuery = {}) {
    const prisma = await getPrisma();
    const period = this.resolvePeriod(query);
    const [subscriptions, checkouts, plans] = await Promise.all([
      prisma.subscription.findMany({ where: this.dateWhere('createdAt', period), include: { plan: true } }),
      prisma.subscriptionCheckout.findMany({ where: this.dateWhere('createdAt', period), include: { plan: true } }),
      prisma.plan.findMany({ select: { id: true, name: true, isActive: true, monthlyPrice: true, yearlyPrice: true, currency: true } }),
    ]);
    const subscriptionRevenue = subscriptions.reduce((sum, s) => sum + toNumber(s.amountPaid), 0);
    const activeSubscriptions = await prisma.subscription.count({ where: { status: 'active', endDate: { gte: new Date() } } });

    return {
      summary: {
        subscriptionRevenue: round(subscriptionRevenue, 2),
        totalRevenue: round(subscriptionRevenue, 2),
        activeSubscriptions,
        arpu: activeSubscriptions ? round(subscriptionRevenue / activeSubscriptions, 2) : 0,
      },
      revenueTrend: groupByDate(
        subscriptions.map((s) => ({ date: s.createdAt, value: toNumber(s.amountPaid) })),
        (i) => i.date,
        (i) => i.value,
        period.bucket,
      ),
      subscriptionsByStatus: this.countBy(subscriptions, (s) => s.status),
      subscriptionsByPlan: this.countBy(subscriptions, (s) => this.localizedName(s.plan?.name) || s.planId),
      checkoutFunnel: [
        { name: 'Started', value: checkouts.length },
        { name: 'Paid', value: checkouts.filter((c) => c.status === 'paid' || c.paidAt).length },
        { name: 'Failed', value: checkouts.filter((c) => c.status === 'failed' || c.failedAt).length },
        { name: 'Cancelled', value: checkouts.filter((c) => c.status === 'cancelled' || c.cancelledAt).length },
      ],
      plans: plans.map((plan) => ({
        id: plan.id,
        name: this.localizedName(plan.name),
        isActive: plan.isActive,
        monthlyPrice: toNumber(plan.monthlyPrice),
        yearlyPrice: toNumber(plan.yearlyPrice),
        currency: plan.currency,
      })),
    };
  }

  async getSafety(query: AnalyticsPeriodQuery = {}) {
    const prisma = await getPrisma();
    const period = this.resolvePeriod(query);
    const [reps, workoutExecutions, profiles, assessments, abandoned] = await Promise.all([
      prisma.repMetrics.findMany({
        where: { workoutExecution: this.dateWhere('timestamp', period) as any },
        include: { workoutExecution: { include: { exercise: { select: { id: true, name: true, slug: true } } } } },
      }),
      prisma.workoutExecution.findMany({ where: this.dateWhere('timestamp', period), select: { id: true, timestamp: true, invalidReps: true, totalReps: true } }),
      prisma.trainingProfile.findMany({ select: { knownInjuries: true } }),
      prisma.bodyScanResult.findMany({ where: this.dateWhere('completedAt', period), select: { safetyScore: true, completedAt: true } }),
      prisma.plannedWorkoutReport.findMany({ where: { status: 'abandoned', ...this.dateWhere('startedAt', period) }, select: { startedAt: true } }),
    ]);
    const dangerReps = reps.filter((rep) => rep.worstState >= 3).length;
    const exerciseMap = new Map<string, { name: string; danger: number; total: number }>();
    for (const rep of reps) {
      const exercise = rep.workoutExecution.exercise;
      const current = exerciseMap.get(exercise.id) ?? { name: this.localizedName(exercise.name) || exercise.slug, danger: 0, total: 0 };
      current.total += 1;
      if (rep.worstState >= 3) current.danger += 1;
      exerciseMap.set(exercise.id, current);
    }

    return {
      summary: {
        dangerRepRate: this.safePercent(dangerReps, reps.length),
        highRiskWorkouts: workoutExecutions.filter((s) => this.safePercent(s.invalidReps ?? 0, s.totalReps ?? 0) >= 20).length,
        avgSafetyScore: this.avg(assessments.map((a) => a.safetyScore)),
        abandonedWorkouts: abandoned.length,
        usersWithKnownInjuries: profiles.length,
      },
      trends: {
        dangerReps: groupByDate(reps.filter((r) => r.worstState >= 3), (r) => r.createdAt, () => 1, period.bucket),
        abandoned: groupByDate(abandoned, (r) => r.startedAt, () => 1, period.bucket),
        safetyScore: groupByDate(assessments, (a) => a.completedAt, (a) => a.safetyScore, period.bucket),
      },
      riskyExercises: Array.from(exerciseMap.values())
        .map((row) => ({ ...row, dangerRate: this.safePercent(row.danger, row.total) }))
        .sort((a, b) => b.dangerRate - a.dangerRate)
        .slice(0, 15),
      injuries: this.extractInjuryBuckets(profiles.map((p) => p.knownInjuries)),
    };
  }

  async getContent(query: AnalyticsPeriodQuery = {}) {
    const prisma = await getPrisma();
    const period = this.resolvePeriod(query);
    const [exercises, workouts, programs, messages, positions, usage] = await Promise.all([
      prisma.exercise.findMany({ select: { id: true, status: true, familyKey: true, category: { select: { name: true } } } }),
      prisma.workoutTemplate.findMany({
        select: {
          id: true,
          status: true,
          createdAt: true,
          level: { select: { number: true, code: true } },
        },
      }),
      prisma.program.findMany({ select: { id: true, isPublished: true, programType: true, createdAt: true } }),
      prisma.feedbackMessageTemplate.findMany({ select: { id: true, category: true, isActive: true } }),
      prisma.posePosition.findMany({ select: { id: true, code: true, isActive: true } }),
      prisma.workoutExecution.groupBy({ by: ['exerciseId'], _count: { id: true }, where: this.dateWhere('timestamp', period), orderBy: { _count: { id: 'desc' } }, take: 15 }),
    ]);
    const exerciseIds = usage.map((u) => u.exerciseId);
    const usedExercises = exerciseIds.length
      ? await prisma.exercise.findMany({ where: { id: { in: exerciseIds } }, select: { id: true, name: true, slug: true } })
      : [];
    const exerciseMap = new Map(usedExercises.map((e) => [e.id, e]));
    return {
      exercises: {
        total: exercises.length,
        byStatus: this.countBy(exercises, (e) => e.status),
        byCategory: this.countBy(exercises, (e) => this.localizedName(e.category?.name) || 'Uncategorized'),
        familyCoverage: this.countBy(exercises, (e) => e.familyKey || 'No family'),
      },
      workoutTemplates: {
        total: workouts.length,
        byStatus: this.countBy(workouts, (w) => w.status),
        byLevel: this.countBy(workouts, (w) => w.level?.code ?? 'unassigned'),
      },
      programs: {
        total: programs.length,
        published: programs.filter((p) => p.isPublished).length,
        byType: this.countBy(programs, (p) => p.programType),
      },
      messages: {
        total: messages.length,
        active: messages.filter((m) => m.isActive).length,
        byCategory: this.countBy(messages, (m) => m.category),
      },
      cameraPositions: {
        total: positions.length,
        active: positions.filter((p) => p.isActive).length,
      },
      mostUsedExercises: usage.map((u) => ({
        exerciseId: u.exerciseId,
        name: this.localizedName(exerciseMap.get(u.exerciseId)?.name) || exerciseMap.get(u.exerciseId)?.slug || u.exerciseId,
        executions: u._count.id,
      })),
    };
  }

  async getProgramDetail(programId: string, query: AnalyticsPeriodQuery = {}) {
    const prisma = await getPrisma();
    const period = this.resolvePeriod(query);
    const program = await prisma.program.findUnique({ where: { id: programId } });
    if (!program) return null;
    const [enrollments, progress, reports] = await Promise.all([
      prisma.userProgram.findMany({ where: { programId }, include: { user: { select: { id: true, name: true, email: true } } } }),
      prisma.userProgramProgress.findMany({ where: { userProgram: { programId } } }),
      prisma.plannedWorkoutReport.findMany({ where: { programId, ...this.dateWhere('startedAt', period) } }),
    ]);
    return {
      program,
      summary: {
        enrollments: enrollments.length,
        activeUsers: enrollments.filter((e) => e.isActive).length,
        completedPlannedWorkouts: progress.filter((p) => p.status === 'completed').length,
        reports: reports.length,
        avgFormScore: this.avg(reports.map((r) => r.avgFormScore)),
      },
      dropoffByWeek: this.countBy(progress, (p) => `Week ${p.weekNumber}`),
      trend: groupByDate(reports, (r) => r.startedAt, () => 1, period.bucket),
      users: enrollments.slice(0, 50).map((e) => ({
        userId: e.userId,
        name: e.user.name,
        email: e.user.email,
        isActive: e.isActive,
        startDate: e.startDate,
      })),
    };
  }

  async getUserReport(userId: string) {
    const prisma = await getPrisma();
    const user = await prisma.user.findUnique({
      where: { id: userId },
      include: {
        trainingProfile: true,
        levelProfiles: { orderBy: { classifiedAt: 'desc' }, take: 10 },
        bodyScanResults: { orderBy: { completedAt: 'desc' }, take: 10 },
        workoutExecutions: { orderBy: { timestamp: 'desc' }, take: 20, include: { exercise: { select: { name: true, slug: true } }, executionMetrics: true } },
        subscriptions: { orderBy: { createdAt: 'desc' }, take: 5, include: { plan: true } },
      },
    });
    if (!user) return null;
    return {
      profile: {
        id: user.id,
        name: user.name,
        email: user.email,
        isPro: user.isPro,
        totalWorkoutExecutions: user.totalWorkoutExecutions,
        totalMinutes: user.totalMinutes,
        trainingGoal: user.trainingGoal,
        createdAt: user.createdAt,
        trainingProfile: user.trainingProfile,
      },
      levels: user.levelProfiles,
      assessments: user.bodyScanResults,
      workoutExecutions: user.workoutExecutions.map((execution) => ({
        id: execution.id,
        exercise: this.localizedName(execution.exercise.name) || execution.exercise.slug,
        timestamp: execution.timestamp,
        totalReps: execution.totalReps,
        invalidReps: execution.invalidReps,
        avgFormScore: execution.executionMetrics?.avgFormScore ?? null,
      })),
      subscriptions: user.subscriptions,
    };
  }

  async getWorkoutExecutionReport(workoutExecutionId: string) {
    const prisma = await getPrisma();
    const execution = await prisma.workoutExecution.findUnique({
      where: { id: workoutExecutionId },
      include: {
        user: { select: { id: true, name: true, email: true } },
        exercise: { select: { id: true, name: true, slug: true } },
        executionMetrics: true,
        repMetrics: { orderBy: { repNumber: 'asc' } },
      },
    });
    if (!execution) return null;
    return {
      id: execution.id,
      user: execution.user,
      exercise: { ...execution.exercise, name: this.localizedName(execution.exercise.name) || execution.exercise.slug },
      timestamp: execution.timestamp,
      durationMs: execution.durationMs,
      totals: {
        totalReps: execution.totalReps,
        countedReps: execution.countedReps,
        invalidReps: execution.invalidReps,
      },
      metrics: execution.executionMetrics,
      reps: execution.repMetrics,
    };
  }

  private async getLatestLevelDistribution() {
    const prisma = await getPrisma();
    const [levels, profiles] = await Promise.all([
      prisma.level.findMany({ orderBy: { number: 'asc' } }),
      prisma.userLevelProfile.findMany({ orderBy: { classifiedAt: 'desc' }, select: { userId: true, overallLevel: true } }),
    ]);
    const latest = new Map<string, number>();
    for (const profile of profiles) {
      if (!latest.has(profile.userId)) latest.set(profile.userId, profile.overallLevel);
    }
    return levels.map((level) => ({
      name: this.localizedName(level.name) || `Level ${level.number}`,
      value: Array.from(latest.values()).filter((value) => value === level.number).length,
      color: level.color,
    }));
  }

  private async getActivationFunnel(period: ResolvedPeriod) {
    const prisma = await getPrisma();
    const users = await prisma.user.findMany({
      where: { deletedAt: null, ...this.dateWhere('createdAt', period) },
      include: {
        trainingProfile: true,
        bodyScanResults: { select: { id: true } },
        levelProfiles: { select: { id: true } },
        userPrograms: { select: { id: true } },
        activePlan: { select: { id: true } },
        workoutExecutions: { select: { id: true, timestamp: true } },
      },
    });
    const withThreeWorkoutsIn7d = users.filter((u) => {
      const deadline = new Date(u.createdAt.getTime() + 7 * DAY_MS);
      return u.workoutExecutions.filter((s) => s.timestamp <= deadline).length >= 3;
    }).length;
    return [
      { name: 'Signup', value: users.length },
      { name: 'Profile', value: users.filter((u) => u.trainingProfile).length },
      { name: 'PAR-Q / Safety', value: users.filter((u) => u.trainingProfile?.healthDisclaimerAccepted).length },
      { name: 'Assessment', value: users.filter((u) => u.bodyScanResults.length > 0).length },
      { name: 'Level', value: users.filter((u) => u.levelProfiles.length > 0).length },
      { name: 'Plan', value: users.filter((u) => u.userPrograms.length > 0 || u.activePlan).length },
      { name: 'First Workout', value: users.filter((u) => u.workoutExecutions.length > 0).length },
      { name: '3 Workouts / 7d', value: withThreeWorkoutsIn7d },
    ].map((step, index, all) => ({
      ...step,
      conversion: index === 0 ? 100 : this.safePercent(step.value, all[0].value),
      dropoff: index === 0 ? 0 : Math.max(0, all[index - 1].value - step.value),
    }));
  }

  private async getNorthStar(period: ResolvedPeriod) {
    const prisma = await getPrisma();
    const users = await prisma.user.findMany({
      where: { deletedAt: null, ...this.dateWhere('createdAt', period) },
      select: { id: true, createdAt: true, workoutExecutions: { select: { timestamp: true, invalidReps: true, totalReps: true } } },
    });
    const completed = users.filter((user) => {
      const deadline = new Date(user.createdAt.getTime() + 7 * DAY_MS);
      const goodExecutions = user.workoutExecutions.filter((execution) =>
        execution.timestamp <= deadline && this.safePercent(execution.invalidReps ?? 0, execution.totalReps ?? 0) < 20,
      );
      return goodExecutions.length >= 3;
    }).length;
    return {
      completed,
      eligibleUsers: users.length,
      rate: this.safePercent(completed, users.length),
      label: 'Users with 3 correct workouts in first 7 days',
    };
  }

  private async getTimeToFirstWorkout(period: ResolvedPeriod) {
    const prisma = await getPrisma();
    const users = await prisma.user.findMany({
      where: { deletedAt: null, ...this.dateWhere('createdAt', period) },
      select: { createdAt: true, workoutExecutions: { select: { timestamp: true }, orderBy: { timestamp: 'asc' }, take: 1 } },
    });
    const values = users
      .map((user) => user.workoutExecutions[0]?.timestamp ? (user.workoutExecutions[0].timestamp.getTime() - user.createdAt.getTime()) / 3600000 : null)
      .filter((value): value is number => value != null && value >= 0);
    return {
      avgHours: this.avg(values),
      usersWithFirstWorkout: values.length,
      buckets: [
        { name: '< 1h', value: values.filter((v) => v < 1).length },
        { name: '1-24h', value: values.filter((v) => v >= 1 && v < 24).length },
        { name: '1-3d', value: values.filter((v) => v >= 24 && v < 72).length },
        { name: '> 3d', value: values.filter((v) => v >= 72).length },
      ],
    };
  }

  private buildRetentionCohorts(users: Array<{ createdAt: Date; workoutExecutions: { timestamp: Date }[] }>) {
    const cohorts = new Map<string, { users: number; day1: number; day2: number; week1: number; week4: number }>();
    for (const user of users) {
      const key = dateKey(user.createdAt, 'week');
      const current = cohorts.get(key) ?? { users: 0, day1: 0, day2: 0, week1: 0, week4: 0 };
      current.users += 1;
      const offsets = user.workoutExecutions.map((execution) => Math.floor((execution.timestamp.getTime() - user.createdAt.getTime()) / DAY_MS));
      if (offsets.some((day) => day <= 1)) current.day1 += 1;
      if (offsets.some((day) => day >= 1 && day <= 2)) current.day2 += 1;
      if (offsets.some((day) => day <= 7)) current.week1 += 1;
      if (offsets.some((day) => day <= 28)) current.week4 += 1;
      cohorts.set(key, current);
    }
    return Array.from(cohorts.entries()).sort(([a], [b]) => a.localeCompare(b)).map(([cohort, row]) => ({
      cohort,
      users: row.users,
      day1: this.safePercent(row.day1, row.users),
      day2: this.safePercent(row.day2, row.users),
      week1: this.safePercent(row.week1, row.users),
      week4: this.safePercent(row.week4, row.users),
    }));
  }

  private async countInactiveUsers(days: number) {
    const prisma = await getPrisma();
    const cutoff = new Date(Date.now() - days * DAY_MS);
    const users = await prisma.user.findMany({
      where: { deletedAt: null },
      select: { workoutExecutions: { orderBy: { timestamp: 'desc' }, take: 1, select: { timestamp: true } } },
    });
    return users.filter((user) => !user.workoutExecutions[0] || user.workoutExecutions[0].timestamp < cutoff).length;
  }

  private uniqueUsersByWindow(workoutExecutions: Array<{ userId: string; timestamp: Date }>, days: number) {
    const cutoff = new Date(Date.now() - days * DAY_MS);
    return new Set(workoutExecutions.filter((s) => s.timestamp >= cutoff).map((s) => s.userId)).size;
  }

  private countBy<T>(items: T[], keyFn: (item: T) => string) {
    const map = new Map<string, number>();
    for (const item of items) {
      const key = keyFn(item) || 'unknown';
      map.set(key, (map.get(key) ?? 0) + 1);
    }
    return Array.from(map.entries())
      .map(([name, value]) => ({ name, value }))
      .sort((a, b) => b.value - a.value);
  }

  private cumulative(series: SeriesPoint[]) {
    let total = 0;
    return series.map((point) => {
      total += point.value;
      return { date: point.date, value: total };
    });
  }

  private avg(values: Array<number | null | undefined>) {
    const filtered = values.filter((value): value is number => value != null);
    return filtered.length ? round(filtered.reduce((sum, value) => sum + value, 0) / filtered.length, 1) : 0;
  }

  private safePercent(value: number, total: number) {
    return total > 0 ? round((value / total) * 100, 1) : 0;
  }

  private bucketAges(dates: Date[]) {
    const now = Date.now();
    const buckets = [
      { name: '18-24', value: 0 },
      { name: '25-34', value: 0 },
      { name: '35-44', value: 0 },
      { name: '45+', value: 0 },
    ];
    for (const date of dates) {
      const age = Math.floor((now - date.getTime()) / (365.25 * DAY_MS));
      if (age < 25) buckets[0].value += 1;
      else if (age < 35) buckets[1].value += 1;
      else if (age < 45) buckets[2].value += 1;
      else buckets[3].value += 1;
    }
    return buckets;
  }

  private localizedName(value: unknown) {
    if (!value) return '';
    if (typeof value === 'string') return value;
    if (typeof value === 'object') {
      const record = value as Record<string, unknown>;
      return String(record.en ?? record.ar ?? record.name ?? Object.values(record)[0] ?? '');
    }
    return String(value);
  }

  private extractInjuryBuckets(values: unknown[]) {
    const names: string[] = [];
    for (const value of values) {
      if (Array.isArray(value)) {
        names.push(...value.map((item) => String(item)));
      } else if (value && typeof value === 'object') {
        names.push(...Object.values(value as Record<string, unknown>).flat().map((item) => String(item)));
      }
    }
    return this.countBy(names, (name) => name);
  }
}
