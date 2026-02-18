import type { PrismaClient } from '@prisma/client';

/**
 * Seed the 3 initial progression rules (Section 7 of architecture plan).
 */
export async function seedProgressionRules(prisma: PrismaClient) {
  console.log('📈 Seeding progression rules...');

  const rules = [
    {
      name: 'Weight Increase (Positive)',
      scope: 'global',
      trigger: 'session_completed',
      conditions: [
        { metric: 'avgFormScore', operator: '>=', value: 75, window: 'last_2_sessions' },
        { metric: 'completionRate', operator: '>=', value: 90, window: 'last_2_sessions' },
      ],
      action: {
        type: 'increase_weight',
        amount: 2.5,
        notification: {
          ar: 'أداء ممتاز! تم زيادة الوزن 2.5 كجم للجلسة القادمة.',
          en: 'Great performance! Weight increased by 2.5kg for next session.',
        },
      },
      priority: 10,
      isActive: true,
    },
    {
      name: 'Rep Increase (Weekly)',
      scope: 'global',
      trigger: 'session_completed',
      conditions: [
        { metric: 'avgFormScore', operator: '>=', value: 80, window: 'last_week' },
        { metric: 'avgROM', operator: '>=', value: 85, window: 'last_week' },
      ],
      action: {
        type: 'increase_reps',
        amount: 2,
        notification: {
          ar: 'تقدم رائع! تم زيادة التكرارات بمقدار 2.',
          en: 'Amazing progress! Reps increased by 2.',
        },
      },
      priority: 5,
      isActive: true,
    },
    {
      name: 'Deload Safety',
      scope: 'global',
      trigger: 'session_completed',
      conditions: [
        { metric: 'avgFormScore', operator: '<', value: 60, window: 'last_2_sessions' },
      ],
      action: {
        type: 'decrease_weight',
        amount: 2.5,
        notification: {
          ar: 'تم تخفيف الوزن 2.5 كجم للحفاظ على سلامتك. ركز على الأداء الصحيح.',
          en: 'Weight reduced by 2.5kg for your safety. Focus on proper form.',
        },
      },
      priority: 20,
      isActive: true,
    },
  ];

  for (const rule of rules) {
    const existing = await prisma.progressionRule.findFirst({
      where: { name: rule.name },
    });

    if (!existing) {
      await prisma.progressionRule.create({ data: rule });
    }
  }

  console.log(`  ✅ Seeded ${rules.length} progression rules`);
}
