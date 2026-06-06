/**
 * Insights Engine — Rule-based insights from metrics comparison.
 * ===============================================================
 *
 * Generates human-readable insights from workout/exercise/week data.
 * These insights appear in the Workout Report and Reports Hub.
 */

export interface Insight {
  type: 'positive' | 'warning' | 'info' | 'milestone';
  icon: string;  // emoji for mobile display
  message: string;
}

export interface InsightContext {
  workoutFormScore?: number;
  previousWorkoutFormScore?: number;
  exercises?: Array<{
    name: string;
    formScore: number;
    dropOffRate: number;
    setsCompleted: number;
    setsPlanned: number;
  }>;
  weekDaysTrained?: number;
  weekDaysTotal?: number;
  currentStreak?: number;
  totalReps?: number;
  programGrade?: string;
  improvementRate?: number;
}

/**
 * Generate insights from a context object.
 * Returns an array of insights sorted by priority (milestones first, then positive, etc.)
 */
export function generateInsights(ctx: InsightContext): Insight[] {
  const insights: Insight[] = [];

  // ── Planned-workout-level insights ──

  if (ctx.workoutFormScore !== undefined && ctx.previousWorkoutFormScore !== undefined) {
    const delta = ctx.workoutFormScore - ctx.previousWorkoutFormScore;
    if (delta > 5) {
      insights.push({
        type: 'positive',
        icon: '📈',
        message: `Your form improved ${Math.round(delta)}% since your last workout!`,
      });
    } else if (delta < -10) {
      insights.push({
        type: 'warning',
        icon: '⚠️',
        message: `Form dropped ${Math.abs(Math.round(delta))}% compared to your last workout. Consider lighter weights or more rest.`,
      });
    }
  }

  // Exercise insights
  if (ctx.exercises && ctx.exercises.length > 0) {
    // Strongest exercise
    const strongest = ctx.exercises.reduce((a, b) =>
      a.formScore > b.formScore ? a : b
    );
    if (strongest.formScore >= 85) {
      insights.push({
        type: 'positive',
        icon: '💪',
        message: `Your strongest exercise: ${strongest.name} (${Math.round(strongest.formScore)}%)`,
      });
    }

    // Weakest exercise
    const weakest = ctx.exercises.reduce((a, b) =>
      a.formScore < b.formScore ? a : b
    );
    if (weakest.formScore < 70 && ctx.exercises.length > 1) {
      insights.push({
        type: 'info',
        icon: '🎯',
        message: `Focus opportunity: ${weakest.name} scored ${Math.round(weakest.formScore)}% — try slower reps next time.`,
      });
    }

    // Fatigue detection: high drop-off rate
    for (const ex of ctx.exercises) {
      if (ex.dropOffRate > 20) {
        insights.push({
          type: 'warning',
          icon: '🔋',
          message: `Form dropped ${Math.round(ex.dropOffRate)}% during ${ex.name} — consider reducing sets or weight.`,
        });
        break; // Only one fatigue warning per planned workout report
      }
    }

    // Incomplete exercise
    const incomplete = ctx.exercises.filter((e) => e.setsCompleted < e.setsPlanned);
    if (incomplete.length === 1) {
      insights.push({
        type: 'info',
        icon: '📋',
        message: `Almost there: you completed ${incomplete[0].setsCompleted}/${incomplete[0].setsPlanned} sets on ${incomplete[0].name}.`,
      });
    }
  }

  // ── Streak milestones ──

  if (ctx.currentStreak !== undefined) {
    if (ctx.currentStreak === 3) {
      insights.push({
        type: 'milestone',
        icon: '🔥',
        message: '3-day streak! Consistency is the key to progress.',
      });
    } else if (ctx.currentStreak === 7) {
      insights.push({
        type: 'milestone',
        icon: '🏆',
        message: 'Amazing 7-day streak! You are building a strong habit.',
      });
    } else if (ctx.currentStreak === 14) {
      insights.push({
        type: 'milestone',
        icon: '⭐',
        message: '2-week streak! Your dedication is inspiring.',
      });
    }
  }

  // ── Rep milestones ──

  if (ctx.totalReps !== undefined) {
    if (ctx.totalReps >= 1000) {
      insights.push({
        type: 'milestone',
        icon: '🎉',
        message: `You've completed ${ctx.totalReps.toLocaleString()} total reps! Keep crushing it.`,
      });
    } else if (ctx.totalReps >= 500) {
      insights.push({
        type: 'milestone',
        icon: '💯',
        message: `500+ reps milestone reached! Halfway to 1,000.`,
      });
    } else if (ctx.totalReps >= 100) {
      insights.push({
        type: 'milestone',
        icon: '✅',
        message: 'First 100 reps completed! The journey has begun.',
      });
    }
  }

  // ── Attendance insight ──

  if (ctx.weekDaysTrained !== undefined && ctx.weekDaysTotal !== undefined) {
    const attendance = ctx.weekDaysTotal > 0
      ? ctx.weekDaysTrained / ctx.weekDaysTotal
      : 0;
    if (attendance >= 1.0) {
      insights.push({
        type: 'positive',
        icon: '🌟',
        message: 'Perfect attendance this week! Every training day completed.',
      });
    } else if (attendance < 0.5 && ctx.weekDaysTotal > 0) {
      insights.push({
        type: 'info',
        icon: '📅',
        message: `You've trained ${ctx.weekDaysTrained} of ${ctx.weekDaysTotal} days this week. Every workout counts!`,
      });
    }
  }

  // ── Program improvement ──

  if (ctx.improvementRate !== undefined && ctx.improvementRate > 10) {
    insights.push({
      type: 'positive',
      icon: '📊',
      message: `Overall improvement: +${Math.round(ctx.improvementRate)}% since you started the program!`,
    });
  }

  // Sort: milestones first, then positive, then info, then warnings
  const priority = { milestone: 0, positive: 1, info: 2, warning: 3 };
  insights.sort((a, b) => priority[a.type] - priority[b.type]);

  // Limit to top 5 most relevant insights
  return insights.slice(0, 5);
}
