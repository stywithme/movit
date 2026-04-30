/**
 * Progression Analytics Service — Phase 5.3 & 5.4
 *
 * Analyzes progression rule effectiveness and provides admin-level analytics:
 *   - Rule hit rates (which rules fire most)
 *   - Program effectiveness (completion rate, avg improvement)
 *   - User progression trends (body score over time)
 *   - Level distribution across users
 *   - Assessment analytics (scores, templates, exercise performance)
 *   - Level transition timing stats
 */

import { getPrisma } from '@/lib/prisma/client';
import { typeStringFromProgramDomain } from '@/lib/program-domain';

export const progressionAnalyticsService = {
  /**
   * Get rule effectiveness stats (which rules fire most, average changes).
   */
  async getRuleEffectiveness() {
    const prisma = await getPrisma();

    const rules = await prisma.progressionRule.findMany({
      include: {
        _count: { select: { history: true } },
        history: {
          select: { field: true, previousValue: true, newValue: true },
        },
      },
    });

    return rules.map((rule) => {
      const totalFires = rule._count.history;
      const changes = rule.history;

      const avgDelta = changes.length > 0
        ? changes.reduce((sum, c) => sum + (c.newValue - c.previousValue), 0) / changes.length
        : 0;

      return {
        id: rule.id,
        name: rule.name,
        scope: rule.scope,
        trigger: rule.trigger,
        isActive: rule.isActive,
        totalFires,
        avgDelta: Math.round(avgDelta * 100) / 100,
        fields: [...new Set(changes.map((c) => c.field))],
      };
    });
  },

  /**
   * Get program effectiveness stats.
   */
  async getProgramEffectiveness() {
    const prisma = await getPrisma();

    const programs = await prisma.program.findMany({
      where: { isPublished: true, deletedAt: null },
      include: {
        _count: { select: { userPrograms: true } },
        userPrograms: {
          include: {
            progress: { where: { status: 'completed' } },
          },
        },
        programSessionReports: {
          where: { status: 'completed' },
          select: { avgFormScore: true, avgAccuracy: true },
        },
      },
    });

    return programs.map((program) => {
      const enrollments = program._count.userPrograms;
      const activeUsers = program.userPrograms.filter((up) => up.isActive).length;
      const completedUsers = program.userPrograms.filter((up) =>
        up.progress.length > 0 &&
        up.progress.length >= program.durationWeeks * 7 * 0.5,
      ).length;

      const reports = program.programSessionReports;
      const avgFormScore = reports.length > 0
        ? reports.reduce((sum, r) => sum + (r.avgFormScore ?? 0), 0) / reports.length
        : 0;
      const avgAccuracy = reports.length > 0
        ? reports.reduce((sum, r) => sum + (r.avgAccuracy ?? 0), 0) / reports.length
        : 0;

      return {
        id: program.id,
        name: program.name,
        slug: program.slug,
        type: typeStringFromProgramDomain(program.programDomain),
        durationWeeks: program.durationWeeks,
        totalEnrollments: enrollments,
        activeUsers,
        completedUsers,
        completionRate: enrollments > 0 ? Math.round((completedUsers / enrollments) * 100) : 0,
        avgFormScore: Math.round(avgFormScore * 10) / 10,
        avgAccuracy: Math.round(avgAccuracy * 10) / 10,
        totalReports: reports.length,
      };
    });
  },

  /**
   * Get user progression trends (body score over time, level changes).
   */
  async getUserTrends(limit = 100) {
    const prisma = await getPrisma();

    const profiles = await prisma.userLevelProfile.findMany({
      orderBy: { classifiedAt: 'desc' },
      take: limit,
      include: {
        user: { select: { id: true, name: true } },
      },
    });

    // Group by user
    const byUser = new Map<string, typeof profiles>();
    for (const profile of profiles) {
      const userId = profile.userId;
      if (!byUser.has(userId)) byUser.set(userId, []);
      byUser.get(userId)!.push(profile);
    }

    const trends = Array.from(byUser.entries()).map(([userId, userProfiles]) => {
      const sorted = userProfiles.sort(
        (a, b) => a.classifiedAt.getTime() - b.classifiedAt.getTime(),
      );

      const first = sorted[0];
      const latest = sorted[sorted.length - 1];
      const improvement = latest.bodyScore - first.bodyScore;

      return {
        userId,
        userName: latest.user.name,
        assessmentCount: sorted.length,
        firstLevel: first.overallLevel,
        currentLevel: latest.overallLevel,
        firstBodyScore: first.bodyScore,
        currentBodyScore: latest.bodyScore,
        improvement: Math.round(improvement * 10) / 10,
        leveledUp: latest.overallLevel > first.overallLevel,
        lastAssessment: latest.classifiedAt.toISOString(),
      };
    });

    return trends.sort((a, b) => b.improvement - a.improvement);
  },

  /**
   * Get overall platform stats.
   */
  async getPlatformStats() {
    const prisma = await getPrisma();

    const [
      totalUsers,
      totalAssessments,
      totalSessions,
      totalProgressionChanges,
      activeUserPrograms,
      pendingReassessments,
    ] = await Promise.all([
      prisma.user.count({ where: { isActive: true } }),
      prisma.bodyScanResult.count(),
      prisma.trainingSession.count(),
      prisma.progressionHistory.count(),
      prisma.userProgram.count({ where: { isActive: true } }),
      prisma.reassessmentSchedule.count({ where: { status: 'pending' } }),
    ]);

    return {
      totalUsers,
      totalAssessments,
      totalSessions,
      totalProgressionChanges,
      activeUserPrograms,
      pendingReassessments,
    };
  },

  /**
   * Get distribution of users across levels.
   */
  async getLevelDistribution() {
    const prisma = await getPrisma();

    const levels = await prisma.level.findMany({
      orderBy: { number: 'asc' },
    });

    // Get latest profile per user via a subquery approach:
    // fetch all profiles, group by userId, take the latest one.
    const allProfiles = await prisma.userLevelProfile.findMany({
      orderBy: { classifiedAt: 'desc' },
      select: { userId: true, overallLevel: true },
    });

    // Deduplicate to latest profile per user
    const latestByUser = new Map<string, number>();
    for (const profile of allProfiles) {
      if (!latestByUser.has(profile.userId)) {
        latestByUser.set(profile.userId, profile.overallLevel);
      }
    }

    // Count per level
    const levelCounts = new Map<number, number>();
    for (const level of latestByUser.values()) {
      levelCounts.set(level, (levelCounts.get(level) ?? 0) + 1);
    }

    return levels.map((level) => ({
      levelNumber: level.number,
      code: level.code,
      name: level.name,
      color: level.color,
      userCount: levelCounts.get(level.number) ?? 0,
    }));
  },

  /**
   * Get assessment analytics: totals, time-based counts, template averages, exercise scores.
   */
  async getAssessmentAnalytics() {
    const prisma = await getPrisma();

    const now = new Date();
    const oneWeekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    const oneMonthAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);

    const [totalAssessments, assessmentsThisWeek, assessmentsThisMonth] = await Promise.all([
      prisma.bodyScanResult.count(),
      prisma.bodyScanResult.count({ where: { completedAt: { gte: oneWeekAgo } } }),
      prisma.bodyScanResult.count({ where: { completedAt: { gte: oneMonthAgo } } }),
    ]);

    // Average bodyScore per template
    const assessmentsByTemplate = await prisma.bodyScanResult.groupBy({
      by: ['templateId'],
      _avg: { bodyScore: true },
      _count: { id: true },
      where: { templateId: { not: null } },
    });

    const templateIds = assessmentsByTemplate
      .map((a) => a.templateId)
      .filter((id): id is string => id !== null);

    const templates = templateIds.length > 0
      ? await prisma.assessmentTemplate.findMany({
          where: { id: { in: templateIds } },
          select: { id: true, name: true },
        })
      : [];

    const templateMap = new Map(templates.map((t) => [t.id, t.name]));

    const avgBodyScorePerTemplate = assessmentsByTemplate.map((group) => ({
      templateId: group.templateId,
      templateName: group.templateId ? (templateMap.get(group.templateId) ?? null) : null,
      avgBodyScore: Math.round((group._avg.bodyScore ?? 0) * 10) / 10,
      count: group._count.id,
    }));

    // Exercises with lowest average scores (from regions JSON)
    // BodyScanResult.regions is JSON array of region assessments,
    // so we aggregate mobilityScore, controlScore, safetyScore from the main result
    const allResults = await prisma.bodyScanResult.findMany({
      select: { mobilityScore: true, controlScore: true, safetyScore: true, symmetryScore: true },
      orderBy: { completedAt: 'desc' },
      take: 500,
    });

    const domainAverages = allResults.length > 0
      ? {
          mobility: Math.round(
            (allResults.reduce((sum, r) => sum + r.mobilityScore, 0) / allResults.length) * 10,
          ) / 10,
          control: Math.round(
            (allResults.reduce((sum, r) => sum + r.controlScore, 0) / allResults.length) * 10,
          ) / 10,
          safety: Math.round(
            (allResults.reduce((sum, r) => sum + r.safetyScore, 0) / allResults.length) * 10,
          ) / 10,
          symmetry: (() => {
            const symResults = allResults.filter((r) => r.symmetryScore != null);
            if (symResults.length === 0) return null;
            return Math.round(
              (symResults.reduce((sum, r) => sum + r.symmetryScore!, 0) / symResults.length) * 10,
            ) / 10;
          })(),
        }
      : { mobility: 0, control: 0, safety: 0, symmetry: null };

    // Find the lowest-scoring domains
    const domainEntries = Object.entries(domainAverages)
      .filter(([, v]) => v !== null)
      .map(([domain, avg]) => ({ domain, avgScore: avg as number }))
      .sort((a, b) => a.avgScore - b.avgScore);

    return {
      totalAssessments,
      assessmentsThisWeek,
      assessmentsThisMonth,
      avgBodyScorePerTemplate,
      domainAverages,
      lowestScoringDomains: domainEntries.slice(0, 3),
    };
  },

  /**
   * Get level transition timing stats: average time between level changes per user.
   */
  async getLevelTransitionStats() {
    const prisma = await getPrisma();

    const profiles = await prisma.userLevelProfile.findMany({
      orderBy: [{ userId: 'asc' }, { classifiedAt: 'asc' }],
      select: { userId: true, overallLevel: true, classifiedAt: true },
    });

    // Group by user
    const byUser = new Map<string, typeof profiles>();
    for (const profile of profiles) {
      if (!byUser.has(profile.userId)) byUser.set(profile.userId, []);
      byUser.get(profile.userId)!.push(profile);
    }

    // Track transitions: from level X → level Y
    interface Transition { from: number; to: number; daysElapsed: number }
    const transitions: Transition[] = [];

    for (const userProfiles of byUser.values()) {
      if (userProfiles.length < 2) continue;

      let lastLevel = userProfiles[0].overallLevel;
      let lastDate = userProfiles[0].classifiedAt;

      for (let i = 1; i < userProfiles.length; i++) {
        const current = userProfiles[i];
        if (current.overallLevel !== lastLevel) {
          const daysElapsed = (current.classifiedAt.getTime() - lastDate.getTime()) / (1000 * 60 * 60 * 24);
          transitions.push({ from: lastLevel, to: current.overallLevel, daysElapsed });
          lastLevel = current.overallLevel;
          lastDate = current.classifiedAt;
        }
      }
    }

    // Aggregate transitions
    const transitionMap = new Map<string, number[]>();
    for (const t of transitions) {
      const key = `${t.from}→${t.to}`;
      if (!transitionMap.has(key)) transitionMap.set(key, []);
      transitionMap.get(key)!.push(t.daysElapsed);
    }

    const transitionStats = Array.from(transitionMap.entries()).map(([key, days]) => {
      const [from, to] = key.split('→').map(Number);
      const avgDays = Math.round((days.reduce((s, d) => s + d, 0) / days.length) * 10) / 10;
      const minDays = Math.round(Math.min(...days) * 10) / 10;
      const maxDays = Math.round(Math.max(...days) * 10) / 10;

      return { fromLevel: from, toLevel: to, count: days.length, avgDays, minDays, maxDays };
    }).sort((a, b) => a.fromLevel - b.fromLevel || a.toLevel - b.toLevel);

    const totalTransitions = transitions.length;
    const usersWithTransitions = new Set(
      Array.from(byUser.entries())
        .filter(([, profiles]) => {
          const levels = profiles.map((p) => p.overallLevel);
          return new Set(levels).size > 1;
        })
        .map(([userId]) => userId),
    ).size;

    const overallAvgDays = transitions.length > 0
      ? Math.round((transitions.reduce((s, t) => s + t.daysElapsed, 0) / transitions.length) * 10) / 10
      : 0;

    return {
      totalTransitions,
      usersWithTransitions,
      overallAvgDays,
      transitions: transitionStats,
    };
  },
};
