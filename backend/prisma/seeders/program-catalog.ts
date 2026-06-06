import type { Prisma, ProgramDomain, ProgramType, WorkoutBlockRole, TrainingGoal } from '@prisma/client';

const defaultEquipmentStarter: Prisma.InputJsonValue = ['bodyweight', 'mat', 'dumbbell', 'resistance_band'];
const defaultEquipmentMobility: Prisma.InputJsonValue = ['bodyweight', 'mat'];
const defaultEquipmentStrength: Prisma.InputJsonValue = ['bodyweight', 'mat', 'dumbbell', 'barbell', 'bench'];

export type CatalogExerciseSlot = {
  slug: string;
  sets?: number;
  reps?: number;
  duration?: number;
  restMs?: number;
  weight?: number;
  weightPerSet?: number[];
};

export type CatalogPlannedWorkoutItem = CatalogExerciseSlot | { restMs: number };

export type CatalogPlannedWorkout = {
  name: { ar: string; en: string };
  sortOrder?: number;
  estimatedDurationMin?: number;
  /** Planned-workout block role (replaces per-item roles + legacy category). */
  role?: WorkoutBlockRole;
  items: CatalogPlannedWorkoutItem[];
};

export type CatalogDay = {
  dayNumber: number;
  isRestDay?: boolean;
  dayType?: string;
  dayFocus?: string | null;
  plannedWorkouts?: CatalogPlannedWorkout[];
};

export type CatalogWeek = {
  weekNumber: number;
  weekType?: 'NORMAL' | 'DELOAD';
  days: CatalogDay[];
};

export type ProgramCatalogEntry = {
  slug: string;
  name: { ar: string; en: string };
  description: { ar: string; en: string };
  durationWeeks: number;
  difficulty: 'beginner' | 'intermediate' | 'advanced';
  isDefault?: boolean;
  isPublished: boolean;
  tags: string[];
  programType: ProgramType;
  programDomain: ProgramDomain;
  trainingGoal: TrainingGoal | null;
  targetDomain: string | null;
  targetRegions: string[];
  targetEquipment: Prisma.InputJsonValue;
  levelRangeMin: number;
  levelRangeMax: number;
  prescriptionPriority: number;
  contraindications: string[];
  autoAssignable: boolean;
  version: number;
  weeklyWorkoutTarget: number | null;
  estimatedWorkoutMinutes: number | null;
  coachingNotes: Prisma.InputJsonValue | null;
  prerequisiteProgramSlug?: string | null;
  nextProgramSlug?: string | null;
  weeks: CatalogWeek[];
};

function workoutCategoryToBlockRole(
  category: 'strength' | 'mobility' | 'conditioning' | 'recovery' | 'mixed',
): WorkoutBlockRole {
  switch (category) {
    case 'mobility':
      return 'WARMUP';
    case 'recovery':
      return 'COOLDOWN';
    case 'conditioning':
      return 'ACCESSORY';
    case 'strength':
    case 'mixed':
    default:
      return 'MAIN';
  }
}

const BANDS = [
  { min: 1, max: 2, key: 'l12' },
  { min: 2, max: 3, key: 'l23' },
  { min: 3, max: 4, key: 'l34' },
  { min: 4, max: 5, key: 'l45' },
] as const;

type GoalKey = 'STRENGTH' | 'HYPERTROPHY' | 'POWER' | 'GENERAL_HEALTH';

const ACSM_STRENGTH_WEEKLY = 3;

const GOAL_ROTATION: Record<
  GoalKey,
  { warmup: CatalogExerciseSlot[]; upper: CatalogExerciseSlot[]; lower: CatalogExerciseSlot[]; full: CatalogExerciseSlot[] }
> = {
  STRENGTH: {
    warmup: [
      { slug: 'lib_wall_slides', sets: 2, reps: 12, restMs: 20000 },
      { slug: 'lib_shadow_squat', sets: 2, reps: 10, restMs: 22000 },
    ],
    upper: [
      { slug: 'lib_dumbbell_press', sets: 3, reps: 8, restMs: 120000 },
      { slug: 'lib_dumbbell_row', sets: 3, reps: 8, restMs: 120000 },
      { slug: 'shoulder_press', sets: 3, reps: 8, restMs: 90000 },
    ],
    lower: [
      { slug: 'squat', sets: 3, reps: 6, restMs: 120000 },
      { slug: 'deadlift', sets: 3, reps: 5, restMs: 120000 },
      { slug: 'lib_romanian_deadlift', sets: 3, reps: 8, restMs: 90000 },
    ],
    full: [
      { slug: 'lib_goblet_squat', sets: 3, reps: 8, restMs: 90000 },
      { slug: 'pushup', sets: 3, reps: 10, restMs: 60000 },
      { slug: 'plank', sets: 3, duration: 40, restMs: 60000 },
    ],
  },
  HYPERTROPHY: {
    warmup: [
      { slug: 'lib_incline_pushup', sets: 2, reps: 12, restMs: 20000 },
      { slug: 'lib_tempo_squat', sets: 2, reps: 12, restMs: 22000 },
    ],
    upper: [
      { slug: 'lib_dumbbell_press', sets: 4, reps: 12, restMs: 75000 },
      { slug: 'lib_dumbbell_row', sets: 4, reps: 12, restMs: 75000 },
      { slug: 'lateral_raises', sets: 3, reps: 15, restMs: 45000 },
      { slug: 'bicep_curl', sets: 3, reps: 12, restMs: 45000 },
    ],
    lower: [
      { slug: 'lunge', sets: 4, reps: 12, restMs: 75000 },
      { slug: 'lib_hamstring_slide', sets: 3, reps: 12, restMs: 45000 },
      { slug: 'calf_raises', sets: 4, reps: 15, restMs: 45000 },
    ],
    full: [
      { slug: 'pushup', sets: 3, reps: 15, restMs: 60000 },
      { slug: 'lib_decline_pushup', sets: 3, reps: 10, restMs: 60000 },
      { slug: 'crunch', sets: 3, reps: 20, restMs: 45000 },
    ],
  },
  POWER: {
    warmup: [
      { slug: 'lib_shadow_squat', sets: 2, reps: 10, restMs: 20000 },
      { slug: 'lib_box_jump', sets: 3, reps: 3, restMs: 90000 },
    ],
    upper: [
      { slug: 'lib_push_press', sets: 4, reps: 3, restMs: 120000 },
      { slug: 'pushup', sets: 3, reps: 5, restMs: 90000 },
      { slug: 'lib_band_pull_apart', sets: 3, reps: 12, restMs: 60000 },
    ],
    lower: [
      { slug: 'lib_kettlebell_swing', sets: 5, reps: 5, restMs: 90000 },
      { slug: 'lib_squat_jump', sets: 3, reps: 4, restMs: 120000 },
      { slug: 'deadlift', sets: 3, reps: 3, restMs: 120000 },
    ],
    full: [
      { slug: 'lib_lateral_bound', sets: 3, reps: 5, restMs: 90000 },
      { slug: 'squat', sets: 3, reps: 3, restMs: 120000 },
      { slug: 'plank', sets: 2, duration: 30, restMs: 45000 },
    ],
  },
  GENERAL_HEALTH: {
    warmup: [
      { slug: 'lib_cat_camel', sets: 2, reps: 10, restMs: 20000 },
      { slug: 'arm_hold', sets: 1, duration: 25, restMs: 20000 },
    ],
    upper: [
      { slug: 'pushup', sets: 3, reps: 10, restMs: 45000 },
      { slug: 'lateral_raises', sets: 2, reps: 12, restMs: 45000 },
    ],
    lower: [
      { slug: 'lib_reverse_lunge', sets: 3, reps: 10, restMs: 45000 },
      { slug: 'glute_bridge', sets: 3, reps: 12, restMs: 45000 },
      { slug: 'calf_raises', sets: 3, reps: 15, restMs: 30000 },
    ],
    full: [
      { slug: 'wall_sit', sets: 2, duration: 30, restMs: 45000 },
      { slug: 'plank', sets: 2, duration: 30, restMs: 45000 },
      { slug: 'forearm_rest', sets: 1, duration: 30, restMs: 30000 },
    ],
  },
};

function scaleSlots(slots: CatalogExerciseSlot[], bandIdx: number, goal: GoalKey): CatalogExerciseSlot[] {
  const setBump = bandIdx >= 2 ? 1 : 0;
  const repBump = goal === 'STRENGTH' ? bandIdx : goal === 'HYPERTROPHY' ? bandIdx * 2 : bandIdx;
  return slots.map((s) => ({
    ...s,
    sets: Math.min(6, (s.sets ?? 3) + setBump),
    reps: s.reps != null ? Math.min(20, s.reps + repBump) : s.reps,
  }));
}

function withRests(items: CatalogExerciseSlot[]): CatalogPlannedWorkoutItem[] {
  const out: CatalogPlannedWorkoutItem[] = [];
  for (let i = 0; i < items.length; i++) {
    out.push({ ...items[i] });
    if (i < items.length - 1) out.push({ restMs: 45000 });
  }
  return out;
}

function buildSystemTrainingWeeks(goal: GoalKey, bandIdx: number): CatalogWeek[] {
  const rot = GOAL_ROTATION[goal];
  const wu = scaleSlots(rot.warmup, bandIdx, goal);
  const up = scaleSlots(rot.upper, bandIdx, goal);
  const lo = scaleSlots(rot.lower, bandIdx, goal);
  const fu = scaleSlots(rot.full, bandIdx, goal);

  const catalogPlannedWorkout = (
    name: { ar: string; en: string },
    parts: CatalogExerciseSlot[],
    category: 'strength' | 'mobility' | 'conditioning' | 'recovery' | 'mixed',
    est: number,
  ): CatalogPlannedWorkout => ({
    name,
    sortOrder: 1,
    estimatedDurationMin: est,
    role: workoutCategoryToBlockRole(category),
    items: withRests(parts),
  });

  const week = (num: number, type: 'NORMAL' | 'DELOAD'): CatalogWeek => ({
    weekNumber: num,
    weekType: type,
    days: [
      {
        dayNumber: 1,
        dayFocus: 'upper',
        plannedWorkouts: [catalogPlannedWorkout({ ar: 'صباحًا', en: 'Morning' }, [...wu, ...up], 'strength', 42)],
      },
      { dayNumber: 2, isRestDay: true },
      {
        dayNumber: 3,
        dayFocus: 'lower',
        plannedWorkouts: [catalogPlannedWorkout({ ar: 'مساءً', en: 'Evening' }, [...wu, ...lo], 'strength', 44)],
      },
      { dayNumber: 4, isRestDay: true },
      {
        dayNumber: 5,
        dayFocus: 'full_body',
        plannedWorkouts: [catalogPlannedWorkout({ ar: 'تمرين مختلط', en: 'Mixed workout' }, [...wu, ...fu], 'mixed', 40)],
      },
      { dayNumber: 6, isRestDay: true },
      { dayNumber: 7, isRestDay: true },
    ],
  });

  const w2: 'NORMAL' | 'DELOAD' = bandIdx === 3 && goal === 'STRENGTH' ? 'DELOAD' : 'NORMAL';
  return [week(1, 'NORMAL'), week(2, w2)];
}

function buildSystemTrainingMeta(goal: GoalKey, bandIdx: number): Omit<ProgramCatalogEntry, 'weeks'> {
  const band = BANDS[bandIdx];
  const slug = `system-${goal.toLowerCase()}-${band.key}`;
  const names: Record<GoalKey, { ar: string; en: string }> = {
    STRENGTH: { en: `System strength (${band.key})`, ar: `قوة نظام (${band.key})` },
    HYPERTROPHY: { en: `System hypertrophy (${band.key})`, ar: `تضخم نظام (${band.key})` },
    POWER: { en: `System power (${band.key})`, ar: `قدرة نظام (${band.key})` },
    GENERAL_HEALTH: { en: `System general health (${band.key})`, ar: `صحة عامة نظام (${band.key})` },
  };
  return {
    slug,
    name: names[goal],
    description: {
      en: `Curated SYSTEM training — ${goal} for user levels ${band.min}-${band.max}.`,
      ar: `تدريب نظام مُنقّى — ${goal} للمستويات ${band.min}-${band.max}.`,
    },
    durationWeeks: 2,
    difficulty: bandIdx >= 2 ? 'intermediate' : 'beginner',
    isPublished: true,
    tags: ['system', 'catalog', goal.toLowerCase(), band.key],
    programType: 'SYSTEM',
    programDomain: 'TRAINING',
    trainingGoal: goal,
    targetDomain: goal === 'GENERAL_HEALTH' ? 'general' : goal.toLowerCase(),
    targetRegions: [],
    targetEquipment: defaultEquipmentStrength,
    levelRangeMin: band.min,
    levelRangeMax: band.max,
    prescriptionPriority: 70 - bandIdx * 3,
    contraindications: [],
    autoAssignable: true,
    version: 1,
    weeklyWorkoutTarget: ACSM_STRENGTH_WEEKLY,
    estimatedWorkoutMinutes: goal === 'POWER' ? 40 : 45,
    coachingNotes: {
      en: 'Prioritize technique; stop if sharp pain.',
      ar: 'ركّز على التقنية؛ توقف عند ألم حاد.',
    },
    prerequisiteProgramSlug: null,
    nextProgramSlug: null,
  };
}

function systemTrainingPrograms(): ProgramCatalogEntry[] {
  const goals: GoalKey[] = ['STRENGTH', 'HYPERTROPHY', 'POWER', 'GENERAL_HEALTH'];
  const out: ProgramCatalogEntry[] = [];
  for (const g of goals) {
    for (let b = 0; b < BANDS.length; b++) {
      out.push({
        ...buildSystemTrainingMeta(g, b),
        weeks: buildSystemTrainingWeeks(g, b),
      });
    }
  }
  return out;
}

function starterProgram(): ProgramCatalogEntry {
  const S = (slug: string, o: Partial<Omit<CatalogExerciseSlot, 'slug'>> = {}): CatalogExerciseSlot => ({
    slug,
    sets: 3,
    reps: 10,
    restMs: 30000,
    ...o,
  });
  const R = (ms: number): CatalogPlannedWorkoutItem => ({ restMs: ms });

  return {
    slug: 'starter-4-weeks',
    name: { ar: 'برنامج البداية 4 أسابيع', en: 'Starter 4-Week Program' },
    description: {
      ar: 'بناء أساس آمن للحركة خلال 4 أسابيع',
      en: 'Build a safe movement foundation over 4 weeks',
    },
    durationWeeks: 4,
    difficulty: 'beginner',
    isDefault: true,
    isPublished: true,
    tags: ['beginner', 'foundation', 'system'],
    programType: 'SYSTEM',
    programDomain: 'TRAINING',
    trainingGoal: 'GENERAL_HEALTH',
    targetDomain: 'general',
    targetRegions: [],
    targetEquipment: defaultEquipmentStarter,
    levelRangeMin: 1,
    levelRangeMax: 2,
    prescriptionPriority: 50,
    contraindications: [],
    autoAssignable: true,
    version: 1,
    weeklyWorkoutTarget: 4,
    estimatedWorkoutMinutes: 40,
    coachingNotes: {
      ar: 'ابدأ بخفة وركّز على الجودة قبل الكمية.',
      en: 'Start light and prioritize quality before volume.',
    },
    prerequisiteProgramSlug: null,
    nextProgramSlug: 'intermediate-strength-4w',
    weeks: [
      {
        weekNumber: 1,
        weekType: 'NORMAL',
        days: [
          {
            dayNumber: 1,
            dayFocus: 'upper',
            plannedWorkouts: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 38,
                role: 'MAIN',
                items: [
                  S('arm_hold', { sets: 2, reps: 8, duration: 25, restMs: 25000 }),
                  R(60000),
                  S('pushup', { sets: 3, reps: 10, restMs: 30000 }),
                  S('lateral_raises', { sets: 2, reps: 10, restMs: 20000 }),
                  S('forearm_rest', { sets: 1, reps: 12, duration: 30, restMs: 20000 }),
                ],
              },
            ],
          },
          {
            dayNumber: 2,
            dayFocus: 'lower',
            plannedWorkouts: [
              {
                name: { ar: 'قبل النوم', en: 'Evening' },
                sortOrder: 1,
                estimatedDurationMin: 40,
                role: 'MAIN',
                items: [
                  S('lib_shadow_squat', { sets: 2, reps: 8, restMs: 22000 }),
                  S('squat', { sets: 3, reps: 8, restMs: 25000 }),
                  R(45000),
                  S('lunge', { sets: 3, reps: 12, restMs: 30000 }),
                  S('plank', { sets: 1, reps: 10, duration: 30, restMs: 20000 }),
                ],
              },
            ],
          },
          { dayNumber: 3, isRestDay: true },
          {
            dayNumber: 4,
            dayFocus: 'full_body',
            plannedWorkouts: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 42,
                role: 'MAIN',
                items: [
                  S('glute_bridge', { sets: 2, reps: 8, restMs: 20000 }),
                  S('deadlift', { sets: 2, reps: 10, restMs: 20000, weight: 5, weightPerSet: [5, 7.5] }),
                  R(50000),
                  S('pushup', { sets: 3, reps: 10, restMs: 25000 }),
                ],
              },
            ],
          },
          { dayNumber: 5, isRestDay: true },
          {
            dayNumber: 6,
            dayFocus: 'core_upper',
            plannedWorkouts: [
              {
                name: { ar: 'مساءً', en: 'Evening' },
                sortOrder: 1,
                estimatedDurationMin: 36,
                role: 'MAIN',
                items: [
                  S('crunch', { sets: 2, reps: 10, restMs: 22000 }),
                  S('pushup', { sets: 2, reps: 12, restMs: 25000 }),
                  R(45000),
                  S('squat', { sets: 2, reps: 10, restMs: 20000 }),
                ],
              },
            ],
          },
          { dayNumber: 7, isRestDay: true },
        ],
      },
      {
        weekNumber: 2,
        weekType: 'NORMAL',
        days: [
          {
            dayNumber: 1,
            dayFocus: 'push',
            plannedWorkouts: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 40,
                role: 'MAIN',
                items: [
                  S('lib_incline_pushup', { sets: 2, reps: 10, restMs: 25000 }),
                  S('pushup', { sets: 3, reps: 12, restMs: 30000 }),
                  R(60000),
                  S('lunge', { sets: 2, reps: 10, restMs: 20000 }),
                ],
              },
            ],
          },
          { dayNumber: 2, isRestDay: true },
          {
            dayNumber: 3,
            dayFocus: 'core',
            plannedWorkouts: [
              {
                name: { ar: 'خلال العمل', en: 'Midday' },
                sortOrder: 1,
                estimatedDurationMin: 34,
                role: 'MAIN',
                items: [
                  S('plank', { sets: 2, reps: 10, duration: 30, restMs: 22000 }),
                  S('side_plank', { sets: 3, duration: 30, restMs: 25000 }),
                  R(45000),
                  S('squat', { sets: 2, reps: 10, restMs: 20000 }),
                ],
              },
            ],
          },
          { dayNumber: 4, isRestDay: true },
          {
            dayNumber: 5,
            dayFocus: 'full_body',
            plannedWorkouts: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 44,
                role: 'MAIN',
                items: [
                  S('glute_bridge', { sets: 2, reps: 10, restMs: 24000 }),
                  S('squat', { sets: 3, reps: 10, restMs: 25000 }),
                  R(50000),
                  S('pushup', { sets: 3, reps: 12, restMs: 25000 }),
                ],
              },
            ],
          },
          { dayNumber: 6, isRestDay: true },
          {
            dayNumber: 7,
            dayFocus: 'upper',
            plannedWorkouts: [
              {
                name: { ar: 'مساءً', en: 'Evening' },
                sortOrder: 1,
                estimatedDurationMin: 38,
                role: 'MAIN',
                items: [
                  S('lunge', { sets: 2, reps: 10, restMs: 22000 }),
                  S('shoulder_press', { sets: 3, reps: 10, restMs: 25000 }),
                  R(45000),
                  S('lateral_raises', { sets: 2, reps: 12, restMs: 20000 }),
                ],
              },
            ],
          },
        ],
      },
      {
        weekNumber: 3,
        weekType: 'NORMAL',
        days: [
          {
            dayNumber: 1,
            dayFocus: 'full_body',
            plannedWorkouts: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 48,
                role: 'MAIN',
                items: [
                  S('arm_hold', { sets: 2, reps: 10, restMs: 24000 }),
                  S('pushup', { sets: 3, reps: 12, restMs: 25000 }),
                  R(45000),
                  S('crunch', { sets: 3, reps: 12, restMs: 25000 }),
                  R(45000),
                  S('squat', { sets: 2, reps: 10, restMs: 20000 }),
                ],
              },
            ],
          },
          { dayNumber: 2, isRestDay: true },
          {
            dayNumber: 3,
            dayFocus: 'lower',
            plannedWorkouts: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 45,
                role: 'MAIN',
                items: [
                  S('lib_reverse_lunge', { sets: 2, reps: 10, restMs: 26000 }),
                  S('lunge', { sets: 3, reps: 12, restMs: 30000 }),
                  R(50000),
                  S('deadlift', { sets: 3, reps: 12, restMs: 25000, weight: 7.5 }),
                ],
              },
            ],
          },
          { dayNumber: 4, isRestDay: true },
          {
            dayNumber: 5,
            dayFocus: 'core_stability',
            plannedWorkouts: [
              {
                name: { ar: 'خلال العمل', en: 'Midday' },
                sortOrder: 1,
                estimatedDurationMin: 40,
                role: 'MAIN',
                items: [
                  S('glute_bridge', { sets: 2, reps: 10, restMs: 24000 }),
                  S('plank', { sets: 3, reps: 12, duration: 35, restMs: 25000 }),
                  R(45000),
                  S('pushup', { sets: 3, reps: 12, restMs: 25000 }),
                ],
              },
            ],
          },
          {
            dayNumber: 6,
            dayFocus: 'upper',
            plannedWorkouts: [
              {
                name: { ar: 'مساءً', en: 'Evening' },
                sortOrder: 1,
                estimatedDurationMin: 38,
                role: 'MAIN',
                items: [
                  S('squat', { sets: 2, reps: 8, restMs: 22000 }),
                  S('squat', { sets: 3, reps: 10, restMs: 25000 }),
                  R(45000),
                  S('lunge', { sets: 3, reps: 10, restMs: 25000 }),
                ],
              },
            ],
          },
          { dayNumber: 7, isRestDay: true },
        ],
      },
      {
        weekNumber: 4,
        weekType: 'DELOAD',
        days: [
          {
            dayNumber: 1,
            dayFocus: 'full_body',
            plannedWorkouts: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 42,
                role: 'MAIN',
                items: [
                  S('lib_cat_camel', { sets: 2, reps: 10, restMs: 24000 }),
                  S('pushup', { sets: 3, reps: 12, restMs: 25000 }),
                  R(45000),
                  S('crunch', { sets: 3, reps: 12, restMs: 25000 }),
                ],
              },
            ],
          },
          {
            dayNumber: 2,
            dayFocus: 'full_body',
            plannedWorkouts: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 42,
                role: 'MAIN',
                items: [
                  S('squat', { sets: 2, reps: 10, restMs: 24000 }),
                  S('lunge', { sets: 3, reps: 12, restMs: 25000 }),
                  R(45000),
                  S('glute_bridge', { sets: 3, reps: 12, restMs: 25000 }),
                ],
              },
            ],
          },
          { dayNumber: 3, isRestDay: true },
          {
            dayNumber: 4,
            dayFocus: 'strength',
            plannedWorkouts: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 46,
                role: 'MAIN',
                items: [
                  S('deadlift', { sets: 2, reps: 10, restMs: 28000 }),
                  S('deadlift', { sets: 3, reps: 12, restMs: 25000, weight: 10, weightPerSet: [7.5, 10, 10] }),
                  R(50000),
                  S('pushup', { sets: 3, reps: 12, restMs: 25000 }),
                ],
              },
            ],
          },
          { dayNumber: 5, isRestDay: true },
          {
            dayNumber: 6,
            dayFocus: 'mixed',
            plannedWorkouts: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 52,
                role: 'MAIN',
                items: [
                  S('crunch', { sets: 2, reps: 12, duration: 45, restMs: 22000 }),
                  S('arm_hold', { sets: 3, reps: 15, duration: 45, restMs: 20000 }),
                  R(40000),
                  S('squat', { sets: 3, reps: 12, restMs: 20000 }),
                  R(40000),
                  S('deadlift', { sets: 3, reps: 12, restMs: 20000, weight: 10 }),
                ],
              },
            ],
          },
          { dayNumber: 7, isRestDay: true },
        ],
      },
    ],
  };
}

function mobilityProgram(): ProgramCatalogEntry {
  const mob = (slug: string, o: Partial<Omit<CatalogExerciseSlot, 'slug'>> = {}): CatalogExerciseSlot => ({
    slug,
    sets: 2,
    reps: 12,
    duration: 40,
    restMs: 20000,
    ...o,
  });
  const mkWeek = (w: number): CatalogWeek => ({
    weekNumber: w,
    weekType: 'NORMAL',
    days: [
      {
        dayNumber: 1,
        dayFocus: 'shoulder_hip',
        plannedWorkouts: [
          {
            name: { ar: 'تمرين مرونة', en: 'Mobility Workout' },
            sortOrder: 1,
            estimatedDurationMin: 32,
            role: 'WARMUP',
            items: [
              mob('assessment_shoulder_mobility', { sets: 2 }),
              { restMs: 45000 },
              mob('lib_worlds_greatest_stretch'),
              {
                slug: 'lib_hip_9090',
                sets: 1,
                duration: 30,
                restMs: 20000,
              },
            ],
          },
        ],
      },
      { dayNumber: 2, isRestDay: true },
      {
        dayNumber: 3,
        dayFocus: 'spine',
        plannedWorkouts: [
          {
            name: { ar: 'تمديد وتحرك', en: 'Stretch & Move' },
            sortOrder: 1,
            estimatedDurationMin: 30,
            role: 'WARMUP',
            items: [
              mob('lib_cat_camel'),
              { restMs: 40000 },
              {
                slug: 'assessment_forward_fold',
                sets: 2,
                duration: 50,
                restMs: 20000,
              },
            ],
          },
        ],
      },
      { dayNumber: 4, isRestDay: true },
      {
        dayNumber: 5,
        dayFocus: 'full_mobility',
        plannedWorkouts: [
          {
            name: { ar: 'مرونة شاملة', en: 'Full Mobility' },
            sortOrder: 1,
            estimatedDurationMin: 32,
            role: 'WARMUP',
            items: [
              {
                slug: 'lib_yoga_down_dog',
                sets: 2,
                duration: 45,
                restMs: 20000,
              },
              { restMs: 40000 },
              mob('lib_ankle_inversion_eversion'),
            ],
          },
        ],
      },
      { dayNumber: 6, isRestDay: true },
      { dayNumber: 7, isRestDay: true },
    ],
  });
  return {
    slug: 'mobility-focus-3w',
    name: { ar: 'برنامج المرونة والحركة', en: 'Mobility Focus Program' },
    description: {
      ar: 'تحسين المرونة ونطاق الحركة خلال 3 أسابيع',
      en: 'Improve flexibility and range of motion over 3 weeks',
    },
    durationWeeks: 3,
    difficulty: 'beginner',
    isPublished: true,
    tags: ['mobility', 'flexibility', 'correction', 'system'],
    programType: 'SYSTEM',
    programDomain: 'MOBILITY',
    trainingGoal: null,
    targetDomain: 'mobility',
    targetRegions: ['shoulder', 'hip', 'spine', 'ankle'],
    targetEquipment: defaultEquipmentMobility,
    levelRangeMin: 1,
    levelRangeMax: 3,
    prescriptionPriority: 30,
    contraindications: [],
    autoAssignable: true,
    version: 1,
    weeklyWorkoutTarget: 3,
    estimatedWorkoutMinutes: 30,
    coachingNotes: {
      ar: 'تحرك ضمن نطاق مريح بدون ألم حاد.',
      en: 'Stay within a comfortable range — no sharp pain.',
    },
    prerequisiteProgramSlug: null,
    nextProgramSlug: null,
    weeks: [mkWeek(1), mkWeek(2), mkWeek(3)],
  };
}

function intermediateStrengthProgram(): ProgramCatalogEntry {
  const E = (slug: string, o: Partial<Omit<CatalogExerciseSlot, 'slug'>> = {}): CatalogExerciseSlot => ({
    slug,
    sets: 3,
    reps: 10,
    restMs: 45000,
    ...o,
  });
  const weeks: CatalogWeek[] = [];
  for (let w = 1; w <= 4; w++) {
    const baseSets = w <= 2 ? 3 : 4;
    const baseReps = w <= 2 ? 10 : 12;
    const baseWeight = 5 + (w - 1) * 2.5;
    weeks.push({
      weekNumber: w,
      weekType: 'NORMAL',
      days: [
        {
          dayNumber: 1,
          dayFocus: 'upper_strength',
          plannedWorkouts: [
            {
              name: { ar: 'قوة علوية', en: 'Upper Strength' },
              sortOrder: 1,
              estimatedDurationMin: 50,
              role: 'MAIN',
              items: [
                E('lib_face_pull', { sets: baseSets, reps: baseReps, restMs: 45000 }),
                E('lib_dumbbell_press', { sets: baseSets, reps: baseReps, restMs: 45000, weight: baseWeight }),
                { restMs: 60000 },
                E('lib_dumbbell_row', { sets: baseSets, reps: baseReps, restMs: 45000, weight: baseWeight }),
              ],
            },
          ],
        },
        {
          dayNumber: 2,
          dayFocus: 'lower_strength',
          plannedWorkouts: [
            {
              name: { ar: 'قوة سفلية', en: 'Lower Strength' },
              sortOrder: 1,
              estimatedDurationMin: 50,
              role: 'MAIN',
              items: [
                E('lib_goblet_squat', { sets: baseSets, reps: baseReps, restMs: 45000, weight: baseWeight }),
                E('squat', { sets: baseSets, reps: baseReps, restMs: 45000, weight: baseWeight }),
                { restMs: 60000 },
                E('deadlift', { sets: baseSets, reps: baseReps, restMs: 45000, weight: baseWeight }),
              ],
            },
          ],
        },
        { dayNumber: 3, isRestDay: true },
        {
          dayNumber: 4,
          dayFocus: 'full_body',
          plannedWorkouts: [
            {
              name: { ar: 'قوة شاملة', en: 'Full Body Power' },
              sortOrder: 1,
              estimatedDurationMin: 48,
              role: 'MAIN',
              items: [
                E('lib_kettlebell_swing', { sets: baseSets, reps: baseReps, restMs: 50000, weight: baseWeight + 2.5 }),
                { restMs: 60000 },
                E('pushup', { sets: baseSets, reps: baseReps, restMs: 45000 }),
              ],
            },
          ],
        },
        { dayNumber: 5, isRestDay: true },
        {
          dayNumber: 6,
          dayFocus: 'strength_challenge',
          plannedWorkouts: [
            {
              name: { ar: 'تحدي القوة', en: 'Strength Challenge' },
              sortOrder: 1,
              estimatedDurationMin: 52,
              role: 'MAIN',
              items: [
                E('lib_dumbbell_row', { sets: 2, reps: baseReps, restMs: 35000, weight: baseWeight }),
                E('lib_barbell_bench', { sets: baseSets + 1, reps: baseReps, restMs: 40000, weight: baseWeight + 2.5 }),
                { restMs: 50000 },
                E('lib_romanian_deadlift', { sets: baseSets, reps: baseReps, restMs: 40000, weight: baseWeight }),
              ],
            },
          ],
        },
        { dayNumber: 7, isRestDay: true },
      ],
    });
  }
  return {
    slug: 'intermediate-strength-4w',
    name: { ar: 'برنامج القوة المتوسط', en: 'Intermediate Strength Program' },
    description: { ar: 'بناء القوة والتحكم لمستوى متوسط', en: 'Build strength and control for intermediate level' },
    durationWeeks: 4,
    difficulty: 'intermediate',
    isPublished: true,
    tags: ['strength', 'intermediate', 'progression', 'system'],
    programType: 'SYSTEM',
    programDomain: 'TRAINING',
    trainingGoal: 'STRENGTH',
    targetDomain: 'strength',
    targetRegions: [],
    targetEquipment: defaultEquipmentStrength,
    levelRangeMin: 2,
    levelRangeMax: 4,
    prescriptionPriority: 60,
    contraindications: [],
    autoAssignable: true,
    version: 1,
    weeklyWorkoutTarget: 4,
    estimatedWorkoutMinutes: 50,
    coachingNotes: {
      ar: 'زد الحمل تدريجيًا مع الحفاظ على تقنية آمنة.',
      en: 'Progress load gradually while keeping technique safe.',
    },
    prerequisiteProgramSlug: 'starter-4-weeks',
    nextProgramSlug: null,
    weeks,
  };
}

function therapeuticLowBack(): ProgramCatalogEntry {
  const T = (slug: string, o: Partial<Omit<CatalogExerciseSlot, 'slug'>> = {}): CatalogExerciseSlot => ({
    slug,
    sets: 2,
    reps: 10,
    restMs: 30000,
    ...o,
  });
  return {
    slug: 'therapeutic-low-back-foundation',
    name: { ar: 'أساس أسفل الظهر (محافظ)', en: 'Low-back foundation (conservative)' },
    description: {
      en: 'Graded exposure for trunk control and hip mobility with low irritability.',
      ar: 'تعرّج تدريجي للتحكم بالجذع ومرونة الورك بتهيج منخفض.',
    },
    durationWeeks: 2,
    difficulty: 'beginner',
    isPublished: true,
    tags: ['therapeutic', 'low_back', 'system'],
    programType: 'SYSTEM',
    programDomain: 'THERAPEUTIC',
    trainingGoal: 'GENERAL_HEALTH',
    targetDomain: 'rehab',
    targetRegions: ['spine', 'hip'],
    targetEquipment: defaultEquipmentMobility,
    levelRangeMin: 1,
    levelRangeMax: 3,
    prescriptionPriority: 20,
    contraindications: ['acute_radicular_pain'],
    autoAssignable: false,
    version: 1,
    weeklyWorkoutTarget: 2,
    estimatedWorkoutMinutes: 25,
    coachingNotes: {
      en: 'Manual-first progression; medical clearance recommended.',
      ar: 'تقدم يدوي أولًا؛ يُفضّل موافقة طبية.',
    },
    prerequisiteProgramSlug: null,
    nextProgramSlug: null,
    weeks: [
      {
        weekNumber: 1,
        weekType: 'NORMAL',
        days: [
          {
            dayNumber: 1,
            dayFocus: 'stability',
            plannedWorkouts: [
              {
                name: { ar: 'تمرين 1', en: 'Workout 1' },
                sortOrder: 1,
                estimatedDurationMin: 28,
                role: 'COOLDOWN',
                items: [
                  T('lib_cat_camel'),
                  T('lib_dead_bug'),
                  T('lib_bird_dog'),
                  T('glute_bridge'),
                ],
              },
            ],
          },
          { dayNumber: 2, isRestDay: true },
          {
            dayNumber: 3,
            dayFocus: 'mobility',
            plannedWorkouts: [
              {
                name: { ar: 'تمرين 2', en: 'Workout 2' },
                sortOrder: 1,
                estimatedDurationMin: 26,
                role: 'WARMUP',
                items: [
                  T('lib_hip_9090'),
                  T('lib_jefferson_curl_light', { sets: 2, reps: 8 }),
                  T('forearm_rest', { sets: 1, duration: 30 }),
                ],
              },
            ],
          },
          { dayNumber: 4, isRestDay: true },
          { dayNumber: 5, isRestDay: true },
          { dayNumber: 6, isRestDay: true },
          { dayNumber: 7, isRestDay: true },
        ],
      },
      {
        weekNumber: 2,
        weekType: 'NORMAL',
        days: [
          {
            dayNumber: 1,
            dayFocus: 'stability',
            plannedWorkouts: [
              {
                name: { ar: 'تمرين 3', en: 'Workout 3' },
                sortOrder: 1,
                estimatedDurationMin: 30,
                role: 'COOLDOWN',
                items: [
                  T('side_plank', { duration: 25 }),
                  T('lib_side_plank_hip_abduction', { duration: 20 }),
                  T('lib_quadruped_rocking'),
                ],
              },
            ],
          },
          { dayNumber: 2, isRestDay: true },
          {
            dayNumber: 3,
            dayFocus: 'hip',
            plannedWorkouts: [
              {
                name: { ar: 'تمرين 4', en: 'Workout 4' },
                sortOrder: 1,
                estimatedDurationMin: 28,
                role: 'WARMUP',
                items: [
                  T('lib_adductor_rockback'),
                  T('lib_glute_activation_bridge'),
                  T('lib_elevated_pigeon', { sets: 2, duration: 40 }),
                ],
              },
            ],
          },
          { dayNumber: 4, isRestDay: true },
          { dayNumber: 5, isRestDay: true },
          { dayNumber: 6, isRestDay: true },
          { dayNumber: 7, isRestDay: true },
        ],
      },
    ],
  };
}

function therapeuticShoulder(): ProgramCatalogEntry {
  const T = (slug: string, o: Partial<Omit<CatalogExerciseSlot, 'slug'>> = {}): CatalogExerciseSlot => ({
    slug,
    sets: 2,
    reps: 12,
    restMs: 25000,
    ...o,
  });
  return {
    slug: 'therapeutic-shoulder-grade-1',
    name: { ar: 'كتف مرحلي (محافظ)', en: 'Shoulder grade-1 (conservative)' },
    description: {
      en: 'Pain-free range and scapular rhythm with band-supported drills.',
      ar: 'نطاق خالٍ من الألم وإيقاع لوح الكتف مع تمارين مدعومة بحبل.',
    },
    durationWeeks: 2,
    difficulty: 'beginner',
    isPublished: true,
    tags: ['therapeutic', 'shoulder', 'system'],
    programType: 'SYSTEM',
    programDomain: 'THERAPEUTIC',
    trainingGoal: 'GENERAL_HEALTH',
    targetDomain: 'rehab',
    targetRegions: ['shoulder'],
    targetEquipment: defaultEquipmentStarter,
    levelRangeMin: 1,
    levelRangeMax: 2,
    prescriptionPriority: 18,
    contraindications: ['acute_shoulder_dislocation'],
    autoAssignable: false,
    version: 1,
    weeklyWorkoutTarget: 2,
    estimatedWorkoutMinutes: 22,
    coachingNotes: {
      en: 'Stay below symptom threshold; clinician-guided preferred.',
      ar: 'تحت عتبة الأعراض؛ يُفضّل إشراف سريري.',
    },
    prerequisiteProgramSlug: null,
    nextProgramSlug: null,
    weeks: [
      {
        weekNumber: 1,
        weekType: 'NORMAL',
        days: [
          {
            dayNumber: 1,
            dayFocus: 'shoulder',
            plannedWorkouts: [
              {
                name: { ar: 'تمرين كتف', en: 'Shoulder workout' },
                sortOrder: 1,
                estimatedDurationMin: 24,
                role: 'COOLDOWN',
                items: [
                  T('lib_neck_retraction'),
                  T('lib_wall_slides'),
                  T('lib_band_pull_apart'),
                ],
              },
            ],
          },
          { dayNumber: 2, isRestDay: true },
          {
            dayNumber: 3,
            dayFocus: 'shoulder',
            plannedWorkouts: [
              {
                name: { ar: 'تمرين كتف 2', en: 'Shoulder workout 2' },
                sortOrder: 1,
                estimatedDurationMin: 22,
                role: 'WARMUP',
                items: [
                  T('lib_forearm_wall_slide'),
                  T('lib_cross_body_stretch', { sets: 2, duration: 35 }),
                  T('lib_sleeper_stretch', { sets: 1, duration: 30 }),
                ],
              },
            ],
          },
          { dayNumber: 4, isRestDay: true },
          { dayNumber: 5, isRestDay: true },
          { dayNumber: 6, isRestDay: true },
          { dayNumber: 7, isRestDay: true },
        ],
      },
      {
        weekNumber: 2,
        weekType: 'NORMAL',
        days: [
          {
            dayNumber: 1,
            dayFocus: 'shoulder',
            plannedWorkouts: [
              {
                name: { ar: 'تقدم خفيف', en: 'Light progression' },
                sortOrder: 1,
                estimatedDurationMin: 26,
                role: 'MAIN',
                items: [
                  T('lib_face_pull'),
                  T('lib_prone_y_raise'),
                  T('lib_prone_t_raise'),
                ],
              },
            ],
          },
          { dayNumber: 2, isRestDay: true },
          { dayNumber: 3, isRestDay: true },
          { dayNumber: 4, isRestDay: true },
          { dayNumber: 5, isRestDay: true },
          { dayNumber: 6, isRestDay: true },
          { dayNumber: 7, isRestDay: true },
        ],
      },
    ],
  };
}

function coachFixture(): ProgramCatalogEntry {
  return {
    slug: 'coach-template-4w',
    name: { ar: 'قالب مدرب 4 أسابيع', en: 'Coach template (4 weeks)' },
    description: { en: 'Fixture for manual coach-authored path.', ar: 'عيّنة لمسار المدرب اليدوي.' },
    durationWeeks: 4,
    difficulty: 'intermediate',
    isPublished: true,
    tags: ['coach', 'fixture'],
    programType: 'COACH',
    programDomain: 'TRAINING',
    trainingGoal: 'STRENGTH',
    targetDomain: 'strength',
    targetRegions: [],
    targetEquipment: defaultEquipmentStrength,
    levelRangeMin: 2,
    levelRangeMax: 4,
    prescriptionPriority: 40,
    contraindications: [],
    autoAssignable: false,
    version: 1,
    weeklyWorkoutTarget: 3,
    estimatedWorkoutMinutes: 50,
    coachingNotes: { en: 'Coach-owned template.', ar: 'قالب يملكه المدرب.' },
    prerequisiteProgramSlug: null,
    nextProgramSlug: null,
    weeks: intermediateStrengthProgram().weeks.slice(0, 2),
  };
}

function customFixture(): ProgramCatalogEntry {
  return {
    slug: 'custom-user-fixture',
    name: { ar: 'عيّنة مستخدم مخصص', en: 'Custom user fixture' },
    description: { en: 'Minimal CUSTOM program for compatibility tests.', ar: 'برنامج CUSTOM بسيط لاختبارات التوافق.' },
    durationWeeks: 1,
    difficulty: 'beginner',
    isPublished: false,
    tags: ['custom', 'fixture'],
    programType: 'CUSTOM',
    programDomain: 'TRAINING',
    trainingGoal: 'GENERAL_HEALTH',
    targetDomain: 'general',
    targetRegions: [],
    targetEquipment: defaultEquipmentStarter,
    levelRangeMin: 1,
    levelRangeMax: 2,
    prescriptionPriority: 10,
    contraindications: [],
    autoAssignable: false,
    version: 1,
    weeklyWorkoutTarget: 2,
    estimatedWorkoutMinutes: 30,
    coachingNotes: null,
    prerequisiteProgramSlug: null,
    nextProgramSlug: null,
    weeks: [
      {
        weekNumber: 1,
        weekType: 'NORMAL',
        days: [
          {
            dayNumber: 1,
            dayFocus: 'full_body',
            plannedWorkouts: [
              {
                name: { ar: 'تمرين واحد', en: 'Single workout' },
                sortOrder: 1,
                estimatedDurationMin: 30,
                role: 'MAIN',
                items: [
                  { slug: 'pushup', sets: 3, reps: 10, restMs: 45000 },
                  { slug: 'squat', sets: 3, reps: 10, restMs: 45000 },
                ],
              },
            ],
          },
          { dayNumber: 2, isRestDay: true },
          { dayNumber: 3, isRestDay: true },
          { dayNumber: 4, isRestDay: true },
          { dayNumber: 5, isRestDay: true },
          { dayNumber: 6, isRestDay: true },
          { dayNumber: 7, isRestDay: true },
        ],
      },
    ],
  };
}

/** Ordered catalog: fixtures first, then expanded SYSTEM library. */
export const PROGRAM_CATALOG: ProgramCatalogEntry[] = [
  starterProgram(),
  mobilityProgram(),
  intermediateStrengthProgram(),
  therapeuticLowBack(),
  therapeuticShoulder(),
  coachFixture(),
  customFixture(),
  ...systemTrainingPrograms(),
];
