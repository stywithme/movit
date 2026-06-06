/**
 * Level Seed Data
 * ===============
 *
 * Seeds the 5 training levels with their default parameters.
 * These levels align with the FitnessLevel enum and scoreToLevel() function.
 */

import type { PrismaClient } from '@prisma/client';

export async function seedLevels(prisma: PrismaClient) {
  console.log('🎯 Seeding levels...');

  const levels = [
    {
      number: 1,
      code: 'foundation',
      name: { en: 'Foundation', ar: 'أساسي' },
      description: {
        en: 'Safety first. Corrective focus. Bodyweight only.',
        ar: 'السلامة أولاً. تركيز تصحيحي. وزن الجسم فقط.',
      },
      color: '#FF5252',
      entryThreshold: 0,
      defaultSetsMin: 2,
      defaultSetsMax: 3,
      defaultRepsMin: 8,
      defaultRepsMax: 10,
      defaultIntensityGuide: 'bodyweight_only',
      defaultRestBetweenSetsMs: 90000,
      defaultWorkoutDurMin: 15,
      defaultWorkoutDurMax: 25,
      defaultWeeklyFreqMin: 2,
      defaultWeeklyFreqMax: 3,
    },
    {
      number: 2,
      code: 'building',
      name: { en: 'Building', ar: 'بناء' },
      description: {
        en: 'Basic movement patterns. Light resistance.',
        ar: 'أنماط حركة أساسية. مقاومة خفيفة.',
      },
      color: '#FF9800',
      entryThreshold: 25,
      defaultSetsMin: 2,
      defaultSetsMax: 3,
      defaultRepsMin: 10,
      defaultRepsMax: 12,
      defaultIntensityGuide: 'light',
      defaultRestBetweenSetsMs: 75000,
      defaultWorkoutDurMin: 25,
      defaultWorkoutDurMax: 35,
      defaultWeeklyFreqMin: 3,
      defaultWeeklyFreqMax: 4,
    },
    {
      number: 3,
      code: 'intermediate',
      name: { en: 'Intermediate', ar: 'متوسط' },
      description: {
        en: 'Progressive overload. Moderate intensity.',
        ar: 'زيادة تدريجية. شدة متوسطة.',
      },
      color: '#FFC107',
      entryThreshold: 45,
      defaultSetsMin: 3,
      defaultSetsMax: 4,
      defaultRepsMin: 8,
      defaultRepsMax: 12,
      defaultIntensityGuide: 'moderate',
      defaultRestBetweenSetsMs: 60000,
      defaultWorkoutDurMin: 30,
      defaultWorkoutDurMax: 45,
      defaultWeeklyFreqMin: 3,
      defaultWeeklyFreqMax: 5,
    },
    {
      number: 4,
      code: 'advanced',
      name: { en: 'Advanced', ar: 'متقدم' },
      description: {
        en: 'Complex movements. Heavy loads.',
        ar: 'حركات معقدة. أحمال ثقيلة.',
      },
      color: '#8BC34A',
      entryThreshold: 65,
      defaultSetsMin: 3,
      defaultSetsMax: 5,
      defaultRepsMin: 6,
      defaultRepsMax: 10,
      defaultIntensityGuide: 'heavy',
      defaultRestBetweenSetsMs: 60000,
      defaultWorkoutDurMin: 40,
      defaultWorkoutDurMax: 60,
      defaultWeeklyFreqMin: 4,
      defaultWeeklyFreqMax: 5,
    },
    {
      number: 5,
      code: 'elite',
      name: { en: 'Elite', ar: 'نخبة' },
      description: {
        en: 'Performance optimization. Peak training.',
        ar: 'تحسين الأداء. تدريب الذروة.',
      },
      color: '#4CAF50',
      entryThreshold: 85,
      defaultSetsMin: 4,
      defaultSetsMax: 6,
      defaultRepsMin: 4,
      defaultRepsMax: 8,
      defaultIntensityGuide: 'max_effort',
      defaultRestBetweenSetsMs: 90000,
      defaultWorkoutDurMin: 45,
      defaultWorkoutDurMax: 75,
      defaultWeeklyFreqMin: 4,
      defaultWeeklyFreqMax: 6,
    },
  ];

  for (const level of levels) {
    await prisma.level.upsert({
      where: { code: level.code },
      create: level,
      update: level,
    });
  }

  console.log(`  ✅ Seeded ${levels.length} levels`);
}
