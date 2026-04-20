import type { Prisma, ProgramDomain, ProgramType, TrainingGoal } from '@prisma/client';

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
  role?: 'WARMUP' | 'ACTIVATION' | 'MAIN' | 'ACCESSORY' | 'CORRECTIVE' | 'COOLDOWN' | 'TEST';
  intent?: 'STANDARD' | 'POWER' | 'ECCENTRIC' | 'VELOCITY_BASED';
};

export type CatalogSessionItem = CatalogExerciseSlot | { restMs: number };

export type CatalogSession = {
  name: { ar: string; en: string };
  sortOrder?: number;
  estimatedDurationMin?: number;
  sessionCategory?: 'strength' | 'mobility' | 'conditioning' | 'recovery' | 'mixed';
  items: CatalogSessionItem[];
};

export type CatalogDay = {
  dayNumber: number;
  isRestDay?: boolean;
  dayType?: string;
  dayFocus?: string | null;
  sessions?: CatalogSession[];
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
  entryRecommendations: Prisma.InputJsonValue | null;
  exitRecommendations: Prisma.InputJsonValue | null;
  contraindications: string[];
  autoAssignable: boolean;
  version: number;
  weeklySessionTarget: number | null;
  estimatedSessionMinutes: number | null;
  coachingNotes: Prisma.InputJsonValue | null;
  prerequisiteProgramSlug?: string | null;
  nextProgramSlug?: string | null;
  weeks: CatalogWeek[];
};

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
      { slug: 'lib_wall_slides', sets: 2, reps: 12, restMs: 20000, role: 'WARMUP' },
      { slug: 'lib_shadow_squat', sets: 2, reps: 10, restMs: 22000, role: 'ACTIVATION' },
    ],
    upper: [
      { slug: 'lib_dumbbell_press', sets: 3, reps: 8, restMs: 120000, role: 'MAIN', intent: 'STANDARD' },
      { slug: 'lib_dumbbell_row', sets: 3, reps: 8, restMs: 120000, role: 'MAIN' },
      { slug: 'shoulder_press', sets: 3, reps: 8, restMs: 90000, role: 'ACCESSORY' },
    ],
    lower: [
      { slug: 'squat', sets: 3, reps: 6, restMs: 120000, role: 'MAIN' },
      { slug: 'deadlift', sets: 3, reps: 5, restMs: 120000, role: 'MAIN' },
      { slug: 'lib_romanian_deadlift', sets: 3, reps: 8, restMs: 90000, role: 'ACCESSORY' },
    ],
    full: [
      { slug: 'lib_goblet_squat', sets: 3, reps: 8, restMs: 90000, role: 'MAIN' },
      { slug: 'pushup', sets: 3, reps: 10, restMs: 60000, role: 'MAIN' },
      { slug: 'plank', sets: 3, duration: 40, restMs: 60000, role: 'ACCESSORY' },
    ],
  },
  HYPERTROPHY: {
    warmup: [
      { slug: 'lib_incline_pushup', sets: 2, reps: 12, restMs: 20000, role: 'WARMUP' },
      { slug: 'lib_tempo_squat', sets: 2, reps: 12, restMs: 22000, role: 'ACTIVATION' },
    ],
    upper: [
      { slug: 'lib_dumbbell_press', sets: 4, reps: 12, restMs: 75000, role: 'MAIN' },
      { slug: 'lib_dumbbell_row', sets: 4, reps: 12, restMs: 75000, role: 'MAIN' },
      { slug: 'lateral_raises', sets: 3, reps: 15, restMs: 45000, role: 'ACCESSORY' },
      { slug: 'bicep_curl', sets: 3, reps: 12, restMs: 45000, role: 'ACCESSORY' },
    ],
    lower: [
      { slug: 'lunge', sets: 4, reps: 12, restMs: 75000, role: 'MAIN' },
      { slug: 'lib_hamstring_slide', sets: 3, reps: 12, restMs: 45000, role: 'ACCESSORY' },
      { slug: 'calf_raises', sets: 4, reps: 15, restMs: 45000, role: 'ACCESSORY' },
    ],
    full: [
      { slug: 'pushup', sets: 3, reps: 15, restMs: 60000, role: 'MAIN' },
      { slug: 'lib_decline_pushup', sets: 3, reps: 10, restMs: 60000, role: 'MAIN' },
      { slug: 'crunch', sets: 3, reps: 20, restMs: 45000, role: 'ACCESSORY' },
    ],
  },
  POWER: {
    warmup: [
      { slug: 'lib_shadow_squat', sets: 2, reps: 10, restMs: 20000, role: 'WARMUP' },
      { slug: 'lib_box_jump', sets: 3, reps: 3, restMs: 90000, role: 'MAIN', intent: 'POWER' },
    ],
    upper: [
      { slug: 'lib_push_press', sets: 4, reps: 3, restMs: 120000, role: 'MAIN', intent: 'POWER' },
      { slug: 'pushup', sets: 3, reps: 5, restMs: 90000, role: 'MAIN', intent: 'POWER' },
      { slug: 'lib_band_pull_apart', sets: 3, reps: 12, restMs: 60000, role: 'ACCESSORY', intent: 'STANDARD' },
    ],
    lower: [
      { slug: 'lib_kettlebell_swing', sets: 5, reps: 5, restMs: 90000, role: 'MAIN', intent: 'POWER' },
      { slug: 'lib_squat_jump', sets: 3, reps: 4, restMs: 120000, role: 'MAIN', intent: 'POWER' },
      { slug: 'deadlift', sets: 3, reps: 3, restMs: 120000, role: 'MAIN', intent: 'POWER' },
    ],
    full: [
      { slug: 'lib_lateral_bound', sets: 3, reps: 5, restMs: 90000, role: 'MAIN', intent: 'POWER' },
      { slug: 'squat', sets: 3, reps: 3, restMs: 120000, role: 'MAIN', intent: 'POWER' },
      { slug: 'plank', sets: 2, duration: 30, restMs: 45000, role: 'COOLDOWN' },
    ],
  },
  GENERAL_HEALTH: {
    warmup: [
      { slug: 'lib_cat_camel', sets: 2, reps: 10, restMs: 20000, role: 'WARMUP' },
      { slug: 'arm_hold', sets: 1, duration: 25, restMs: 20000, role: 'ACTIVATION' },
    ],
    upper: [
      { slug: 'pushup', sets: 3, reps: 10, restMs: 45000, role: 'MAIN' },
      { slug: 'lateral_raises', sets: 2, reps: 12, restMs: 45000, role: 'ACCESSORY' },
    ],
    lower: [
      { slug: 'lib_reverse_lunge', sets: 3, reps: 10, restMs: 45000, role: 'MAIN' },
      { slug: 'glute_bridge', sets: 3, reps: 12, restMs: 45000, role: 'MAIN' },
      { slug: 'calf_raises', sets: 3, reps: 15, restMs: 30000, role: 'ACCESSORY' },
    ],
    full: [
      { slug: 'wall_sit', sets: 2, duration: 30, restMs: 45000, role: 'MAIN' },
      { slug: 'plank', sets: 2, duration: 30, restMs: 45000, role: 'MAIN' },
      { slug: 'forearm_rest', sets: 1, duration: 30, restMs: 30000, role: 'COOLDOWN' },
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

function withRests(items: CatalogExerciseSlot[]): CatalogSessionItem[] {
  const out: CatalogSessionItem[] = [];
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

  const session = (
    name: { ar: string; en: string },
    parts: CatalogExerciseSlot[],
    category: CatalogSession['sessionCategory'],
    est: number,
  ): CatalogSession => ({
    name,
    sortOrder: 1,
    estimatedDurationMin: est,
    sessionCategory: category,
    items: withRests(parts),
  });

  const week = (num: number, type: 'NORMAL' | 'DELOAD'): CatalogWeek => ({
    weekNumber: num,
    weekType: type,
    days: [
      {
        dayNumber: 1,
        dayFocus: 'upper',
        sessions: [session({ ar: 'صباحًا', en: 'Morning' }, [...wu, ...up], 'strength', 42)],
      },
      { dayNumber: 2, isRestDay: true },
      {
        dayNumber: 3,
        dayFocus: 'lower',
        sessions: [session({ ar: 'مساءً', en: 'Evening' }, [...wu, ...lo], 'strength', 44)],
      },
      { dayNumber: 4, isRestDay: true },
      {
        dayNumber: 5,
        dayFocus: 'full_body',
        sessions: [session({ ar: 'جلسة مختلطة', en: 'Mixed session' }, [...wu, ...fu], 'mixed', 40)],
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
    entryRecommendations: { bodyScore: { min: 20 + bandIdx * 5 } },
    exitRecommendations: { bodyScore: { min: 45 + bandIdx * 5 } },
    contraindications: [],
    autoAssignable: true,
    version: 1,
    weeklySessionTarget: ACSM_STRENGTH_WEEKLY,
    estimatedSessionMinutes: goal === 'POWER' ? 40 : 45,
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
    role: 'MAIN',
    intent: 'STANDARD',
    ...o,
  });
  const R = (ms: number): CatalogSessionItem => ({ restMs: ms });

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
    entryRecommendations: { bodyScore: { max: 55 } },
    exitRecommendations: { bodyScore: { min: 40 } },
    contraindications: [],
    autoAssignable: true,
    version: 1,
    weeklySessionTarget: 4,
    estimatedSessionMinutes: 40,
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
            sessions: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 38,
                sessionCategory: 'mixed',
                items: [
                  S('arm_hold', { sets: 2, reps: 8, duration: 25, restMs: 25000, role: 'WARMUP' }),
                  R(60000),
                  S('pushup', { sets: 3, reps: 10, restMs: 30000, role: 'MAIN' }),
                  S('lateral_raises', { sets: 2, reps: 10, restMs: 20000, role: 'ACCESSORY' }),
                  S('forearm_rest', { sets: 1, reps: 12, duration: 30, restMs: 20000, role: 'COOLDOWN' }),
                ],
              },
            ],
          },
          {
            dayNumber: 2,
            dayFocus: 'lower',
            sessions: [
              {
                name: { ar: 'قبل النوم', en: 'Evening' },
                sortOrder: 1,
                estimatedDurationMin: 40,
                sessionCategory: 'mixed',
                items: [
                  S('lib_shadow_squat', { sets: 2, reps: 8, restMs: 22000, role: 'WARMUP' }),
                  S('squat', { sets: 3, reps: 8, restMs: 25000, role: 'MAIN' }),
                  R(45000),
                  S('lunge', { sets: 3, reps: 12, restMs: 30000, role: 'MAIN' }),
                  S('plank', { sets: 1, reps: 10, duration: 30, restMs: 20000, role: 'COOLDOWN' }),
                ],
              },
            ],
          },
          { dayNumber: 3, isRestDay: true },
          {
            dayNumber: 4,
            dayFocus: 'full_body',
            sessions: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 42,
                sessionCategory: 'mixed',
                items: [
                  S('glute_bridge', { sets: 2, reps: 8, restMs: 20000, role: 'WARMUP' }),
                  S('deadlift', { sets: 2, reps: 10, restMs: 20000, weight: 5, weightPerSet: [5, 7.5], role: 'MAIN' }),
                  R(50000),
                  S('pushup', { sets: 3, reps: 10, restMs: 25000, role: 'MAIN' }),
                ],
              },
            ],
          },
          { dayNumber: 5, isRestDay: true },
          {
            dayNumber: 6,
            dayFocus: 'core_upper',
            sessions: [
              {
                name: { ar: 'مساءً', en: 'Evening' },
                sortOrder: 1,
                estimatedDurationMin: 36,
                sessionCategory: 'mixed',
                items: [
                  S('crunch', { sets: 2, reps: 10, restMs: 22000, role: 'ACTIVATION' }),
                  S('pushup', { sets: 2, reps: 12, restMs: 25000, role: 'MAIN' }),
                  R(45000),
                  S('squat', { sets: 2, reps: 10, restMs: 20000, role: 'ACCESSORY' }),
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
            sessions: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 40,
                sessionCategory: 'strength',
                items: [
                  S('lib_incline_pushup', { sets: 2, reps: 10, restMs: 25000, role: 'WARMUP' }),
                  S('pushup', { sets: 3, reps: 12, restMs: 30000, role: 'MAIN' }),
                  R(60000),
                  S('lunge', { sets: 2, reps: 10, restMs: 20000, role: 'MAIN' }),
                ],
              },
            ],
          },
          { dayNumber: 2, isRestDay: true },
          {
            dayNumber: 3,
            dayFocus: 'core',
            sessions: [
              {
                name: { ar: 'خلال العمل', en: 'Midday' },
                sortOrder: 1,
                estimatedDurationMin: 34,
                sessionCategory: 'mixed',
                items: [
                  S('plank', { sets: 2, reps: 10, duration: 30, restMs: 22000, role: 'WARMUP' }),
                  S('side_plank', { sets: 3, duration: 30, restMs: 25000, role: 'MAIN' }),
                  R(45000),
                  S('squat', { sets: 2, reps: 10, restMs: 20000, role: 'ACCESSORY' }),
                ],
              },
            ],
          },
          { dayNumber: 4, isRestDay: true },
          {
            dayNumber: 5,
            dayFocus: 'full_body',
            sessions: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 44,
                sessionCategory: 'mixed',
                items: [
                  S('glute_bridge', { sets: 2, reps: 10, restMs: 24000, role: 'WARMUP' }),
                  S('squat', { sets: 3, reps: 10, restMs: 25000, role: 'MAIN' }),
                  R(50000),
                  S('pushup', { sets: 3, reps: 12, restMs: 25000, role: 'MAIN' }),
                ],
              },
            ],
          },
          { dayNumber: 6, isRestDay: true },
          {
            dayNumber: 7,
            dayFocus: 'upper',
            sessions: [
              {
                name: { ar: 'مساءً', en: 'Evening' },
                sortOrder: 1,
                estimatedDurationMin: 38,
                sessionCategory: 'mixed',
                items: [
                  S('lunge', { sets: 2, reps: 10, restMs: 22000, role: 'ACTIVATION' }),
                  S('shoulder_press', { sets: 3, reps: 10, restMs: 25000, role: 'MAIN' }),
                  R(45000),
                  S('lateral_raises', { sets: 2, reps: 12, restMs: 20000, role: 'ACCESSORY' }),
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
            sessions: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 48,
                sessionCategory: 'mixed',
                items: [
                  S('arm_hold', { sets: 2, reps: 10, restMs: 24000, role: 'WARMUP' }),
                  S('pushup', { sets: 3, reps: 12, restMs: 25000, role: 'MAIN' }),
                  R(45000),
                  S('crunch', { sets: 3, reps: 12, restMs: 25000, role: 'MAIN' }),
                  R(45000),
                  S('squat', { sets: 2, reps: 10, restMs: 20000, role: 'ACCESSORY' }),
                ],
              },
            ],
          },
          { dayNumber: 2, isRestDay: true },
          {
            dayNumber: 3,
            dayFocus: 'lower',
            sessions: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 45,
                sessionCategory: 'strength',
                items: [
                  S('lib_reverse_lunge', { sets: 2, reps: 10, restMs: 26000, role: 'WARMUP' }),
                  S('lunge', { sets: 3, reps: 12, restMs: 30000, role: 'MAIN' }),
                  R(50000),
                  S('deadlift', { sets: 3, reps: 12, restMs: 25000, weight: 7.5, role: 'MAIN' }),
                ],
              },
            ],
          },
          { dayNumber: 4, isRestDay: true },
          {
            dayNumber: 5,
            dayFocus: 'core_stability',
            sessions: [
              {
                name: { ar: 'خلال العمل', en: 'Midday' },
                sortOrder: 1,
                estimatedDurationMin: 40,
                sessionCategory: 'mixed',
                items: [
                  S('glute_bridge', { sets: 2, reps: 10, restMs: 24000, role: 'ACTIVATION' }),
                  S('plank', { sets: 3, reps: 12, duration: 35, restMs: 25000, role: 'MAIN' }),
                  R(45000),
                  S('pushup', { sets: 3, reps: 12, restMs: 25000, role: 'ACCESSORY' }),
                ],
              },
            ],
          },
          {
            dayNumber: 6,
            dayFocus: 'upper',
            sessions: [
              {
                name: { ar: 'مساءً', en: 'Evening' },
                sortOrder: 1,
                estimatedDurationMin: 38,
                sessionCategory: 'mixed',
                items: [
                  S('squat', { sets: 2, reps: 8, restMs: 22000, role: 'WARMUP' }),
                  S('squat', { sets: 3, reps: 10, restMs: 25000, role: 'MAIN' }),
                  R(45000),
                  S('lunge', { sets: 3, reps: 10, restMs: 25000, role: 'MAIN' }),
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
            sessions: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 42,
                sessionCategory: 'mixed',
                items: [
                  S('lib_cat_camel', { sets: 2, reps: 10, restMs: 24000, role: 'WARMUP' }),
                  S('pushup', { sets: 3, reps: 12, restMs: 25000, role: 'MAIN' }),
                  R(45000),
                  S('crunch', { sets: 3, reps: 12, restMs: 25000, role: 'MAIN' }),
                ],
              },
            ],
          },
          {
            dayNumber: 2,
            dayFocus: 'full_body',
            sessions: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 42,
                sessionCategory: 'mixed',
                items: [
                  S('squat', { sets: 2, reps: 10, restMs: 24000, role: 'WARMUP' }),
                  S('lunge', { sets: 3, reps: 12, restMs: 25000, role: 'MAIN' }),
                  R(45000),
                  S('glute_bridge', { sets: 3, reps: 12, restMs: 25000, role: 'MAIN' }),
                ],
              },
            ],
          },
          { dayNumber: 3, isRestDay: true },
          {
            dayNumber: 4,
            dayFocus: 'strength',
            sessions: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 46,
                sessionCategory: 'strength',
                items: [
                  S('deadlift', { sets: 2, reps: 10, restMs: 28000, role: 'WARMUP' }),
                  S('deadlift', { sets: 3, reps: 12, restMs: 25000, weight: 10, weightPerSet: [7.5, 10, 10], role: 'MAIN', intent: 'POWER' }),
                  R(50000),
                  S('pushup', { sets: 3, reps: 12, restMs: 25000, role: 'MAIN' }),
                ],
              },
            ],
          },
          { dayNumber: 5, isRestDay: true },
          {
            dayNumber: 6,
            dayFocus: 'mixed',
            sessions: [
              {
                name: { ar: 'صباحًا', en: 'Morning' },
                sortOrder: 1,
                estimatedDurationMin: 52,
                sessionCategory: 'mixed',
                items: [
                  S('crunch', { sets: 2, reps: 12, duration: 45, restMs: 22000, role: 'ACTIVATION' }),
                  S('arm_hold', { sets: 3, reps: 15, duration: 45, restMs: 20000, role: 'MAIN' }),
                  R(40000),
                  S('squat', { sets: 3, reps: 12, restMs: 20000, role: 'MAIN' }),
                  R(40000),
                  S('deadlift', { sets: 3, reps: 12, restMs: 20000, weight: 10, role: 'MAIN' }),
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
    role: 'MAIN',
    intent: 'STANDARD',
    ...o,
  });
  const mkWeek = (w: number): CatalogWeek => ({
    weekNumber: w,
    weekType: 'NORMAL',
    days: [
      {
        dayNumber: 1,
        dayFocus: 'shoulder_hip',
        sessions: [
          {
            name: { ar: 'جلسة مرونة', en: 'Mobility Session' },
            sortOrder: 1,
            estimatedDurationMin: 32,
            sessionCategory: 'mobility',
            items: [
              mob('assessment_shoulder_mobility', { sets: 2, role: 'WARMUP' }),
              { restMs: 45000 },
              mob('lib_worlds_greatest_stretch', { role: 'MAIN' }),
              {
                slug: 'lib_hip_9090',
                sets: 1,
                duration: 30,
                restMs: 20000,
                role: 'COOLDOWN',
                intent: 'STANDARD',
              },
            ],
          },
        ],
      },
      { dayNumber: 2, isRestDay: true },
      {
        dayNumber: 3,
        dayFocus: 'spine',
        sessions: [
          {
            name: { ar: 'تمديد وتحرك', en: 'Stretch & Move' },
            sortOrder: 1,
            estimatedDurationMin: 30,
            sessionCategory: 'mobility',
            items: [
              mob('lib_cat_camel', { role: 'WARMUP' }),
              { restMs: 40000 },
              {
                slug: 'assessment_forward_fold',
                sets: 2,
                duration: 50,
                restMs: 20000,
                role: 'MAIN',
                intent: 'STANDARD',
              },
            ],
          },
        ],
      },
      { dayNumber: 4, isRestDay: true },
      {
        dayNumber: 5,
        dayFocus: 'full_mobility',
        sessions: [
          {
            name: { ar: 'مرونة شاملة', en: 'Full Mobility' },
            sortOrder: 1,
            estimatedDurationMin: 32,
            sessionCategory: 'mobility',
            items: [
              {
                slug: 'lib_yoga_down_dog',
                sets: 2,
                duration: 45,
                restMs: 20000,
                role: 'ACTIVATION',
                intent: 'STANDARD',
              },
              { restMs: 40000 },
              mob('lib_ankle_inversion_eversion', { role: 'MAIN' }),
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
    entryRecommendations: { mobilityScore: { max: 50 } },
    exitRecommendations: { mobilityScore: { min: 65 } },
    contraindications: [],
    autoAssignable: true,
    version: 1,
    weeklySessionTarget: 3,
    estimatedSessionMinutes: 30,
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
    role: 'MAIN',
    intent: 'STANDARD',
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
          sessions: [
            {
              name: { ar: 'قوة علوية', en: 'Upper Strength' },
              sortOrder: 1,
              estimatedDurationMin: 50,
              sessionCategory: 'strength',
              items: [
                E('lib_face_pull', { sets: baseSets, reps: baseReps, restMs: 45000, role: 'WARMUP' }),
                E('lib_dumbbell_press', { sets: baseSets, reps: baseReps, restMs: 45000, weight: baseWeight, role: 'MAIN', intent: 'POWER' }),
                { restMs: 60000 },
                E('lib_dumbbell_row', { sets: baseSets, reps: baseReps, restMs: 45000, weight: baseWeight, role: 'MAIN' }),
              ],
            },
          ],
        },
        {
          dayNumber: 2,
          dayFocus: 'lower_strength',
          sessions: [
            {
              name: { ar: 'قوة سفلية', en: 'Lower Strength' },
              sortOrder: 1,
              estimatedDurationMin: 50,
              sessionCategory: 'strength',
              items: [
                E('lib_goblet_squat', { sets: baseSets, reps: baseReps, restMs: 45000, weight: baseWeight, role: 'WARMUP' }),
                E('squat', { sets: baseSets, reps: baseReps, restMs: 45000, weight: baseWeight, role: 'MAIN' }),
                { restMs: 60000 },
                E('deadlift', { sets: baseSets, reps: baseReps, restMs: 45000, weight: baseWeight, role: 'MAIN' }),
              ],
            },
          ],
        },
        { dayNumber: 3, isRestDay: true },
        {
          dayNumber: 4,
          dayFocus: 'full_body',
          sessions: [
            {
              name: { ar: 'قوة شاملة', en: 'Full Body Power' },
              sortOrder: 1,
              estimatedDurationMin: 48,
              sessionCategory: 'strength',
              items: [
                E('lib_kettlebell_swing', { sets: baseSets, reps: baseReps, restMs: 50000, weight: baseWeight + 2.5, role: 'MAIN', intent: 'POWER' }),
                { restMs: 60000 },
                E('pushup', { sets: baseSets, reps: baseReps, restMs: 45000, role: 'MAIN' }),
              ],
            },
          ],
        },
        { dayNumber: 5, isRestDay: true },
        {
          dayNumber: 6,
          dayFocus: 'strength_challenge',
          sessions: [
            {
              name: { ar: 'تحدي القوة', en: 'Strength Challenge' },
              sortOrder: 1,
              estimatedDurationMin: 52,
              sessionCategory: 'strength',
              items: [
                E('lib_dumbbell_row', { sets: 2, reps: baseReps, restMs: 35000, weight: baseWeight, role: 'ACTIVATION' }),
                E('lib_barbell_bench', { sets: baseSets + 1, reps: baseReps, restMs: 40000, weight: baseWeight + 2.5, role: 'MAIN' }),
                { restMs: 50000 },
                E('lib_romanian_deadlift', { sets: baseSets, reps: baseReps, restMs: 40000, weight: baseWeight, role: 'ACCESSORY' }),
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
    entryRecommendations: { bodyScore: { min: 40 } },
    exitRecommendations: { bodyScore: { min: 65 } },
    contraindications: [],
    autoAssignable: true,
    version: 1,
    weeklySessionTarget: 4,
    estimatedSessionMinutes: 50,
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
    role: 'MAIN',
    intent: 'STANDARD',
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
    entryRecommendations: { painFlag: { max: 1 } },
    exitRecommendations: { mobilityScore: { min: 50 } },
    contraindications: ['acute_radicular_pain'],
    autoAssignable: false,
    version: 1,
    weeklySessionTarget: 2,
    estimatedSessionMinutes: 25,
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
            sessions: [
              {
                name: { ar: 'جلسة 1', en: 'Session 1' },
                sortOrder: 1,
                estimatedDurationMin: 28,
                sessionCategory: 'recovery',
                items: [
                  T('lib_cat_camel', { role: 'WARMUP' }),
                  T('lib_dead_bug', { role: 'MAIN' }),
                  T('lib_bird_dog', { role: 'MAIN' }),
                  T('glute_bridge', { role: 'ACCESSORY' }),
                ],
              },
            ],
          },
          { dayNumber: 2, isRestDay: true },
          {
            dayNumber: 3,
            dayFocus: 'mobility',
            sessions: [
              {
                name: { ar: 'جلسة 2', en: 'Session 2' },
                sortOrder: 1,
                estimatedDurationMin: 26,
                sessionCategory: 'mobility',
                items: [
                  T('lib_hip_9090', { role: 'WARMUP' }),
                  T('lib_jefferson_curl_light', { sets: 2, reps: 8, role: 'MAIN' }),
                  T('forearm_rest', { sets: 1, duration: 30, role: 'COOLDOWN' }),
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
            sessions: [
              {
                name: { ar: 'جلسة 3', en: 'Session 3' },
                sortOrder: 1,
                estimatedDurationMin: 30,
                sessionCategory: 'recovery',
                items: [
                  T('side_plank', { duration: 25, role: 'MAIN' }),
                  T('lib_side_plank_hip_abduction', { duration: 20, role: 'ACCESSORY' }),
                  T('lib_quadruped_rocking', { role: 'COOLDOWN' }),
                ],
              },
            ],
          },
          { dayNumber: 2, isRestDay: true },
          {
            dayNumber: 3,
            dayFocus: 'hip',
            sessions: [
              {
                name: { ar: 'جلسة 4', en: 'Session 4' },
                sortOrder: 1,
                estimatedDurationMin: 28,
                sessionCategory: 'mobility',
                items: [
                  T('lib_adductor_rockback', { role: 'WARMUP' }),
                  T('lib_glute_activation_bridge', { role: 'MAIN' }),
                  T('lib_elevated_pigeon', { sets: 2, duration: 40, role: 'ACCESSORY' }),
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
    role: 'MAIN',
    intent: 'STANDARD',
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
    entryRecommendations: { painFlag: { max: 1 } },
    exitRecommendations: { mobilityScore: { min: 55 } },
    contraindications: ['acute_shoulder_dislocation'],
    autoAssignable: false,
    version: 1,
    weeklySessionTarget: 2,
    estimatedSessionMinutes: 22,
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
            sessions: [
              {
                name: { ar: 'جلسة كتف', en: 'Shoulder session' },
                sortOrder: 1,
                estimatedDurationMin: 24,
                sessionCategory: 'recovery',
                items: [
                  T('lib_neck_retraction', { role: 'WARMUP' }),
                  T('lib_wall_slides', { role: 'MAIN' }),
                  T('lib_band_pull_apart', { role: 'ACCESSORY' }),
                ],
              },
            ],
          },
          { dayNumber: 2, isRestDay: true },
          {
            dayNumber: 3,
            dayFocus: 'shoulder',
            sessions: [
              {
                name: { ar: 'جلسة كتف 2', en: 'Shoulder session 2' },
                sortOrder: 1,
                estimatedDurationMin: 22,
                sessionCategory: 'mobility',
                items: [
                  T('lib_forearm_wall_slide', { role: 'WARMUP' }),
                  T('lib_cross_body_stretch', { sets: 2, duration: 35, role: 'MAIN' }),
                  T('lib_sleeper_stretch', { sets: 1, duration: 30, role: 'COOLDOWN' }),
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
            sessions: [
              {
                name: { ar: 'تقدم خفيف', en: 'Light progression' },
                sortOrder: 1,
                estimatedDurationMin: 26,
                sessionCategory: 'strength',
                items: [
                  T('lib_face_pull', { role: 'MAIN' }),
                  T('lib_prone_y_raise', { role: 'ACCESSORY' }),
                  T('lib_prone_t_raise', { role: 'ACCESSORY' }),
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
    entryRecommendations: null,
    exitRecommendations: null,
    contraindications: [],
    autoAssignable: false,
    version: 1,
    weeklySessionTarget: 3,
    estimatedSessionMinutes: 50,
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
    entryRecommendations: null,
    exitRecommendations: null,
    contraindications: [],
    autoAssignable: false,
    version: 1,
    weeklySessionTarget: 2,
    estimatedSessionMinutes: 30,
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
            sessions: [
              {
                name: { ar: 'جلسة واحدة', en: 'Single session' },
                sortOrder: 1,
                estimatedDurationMin: 30,
                sessionCategory: 'mixed',
                items: [
                  { slug: 'pushup', sets: 3, reps: 10, restMs: 45000, role: 'MAIN' },
                  { slug: 'squat', sets: 3, reps: 10, restMs: 45000, role: 'MAIN' },
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
