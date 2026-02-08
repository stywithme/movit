import type { PrismaClient } from '@prisma/client';
import { normalizeMessageContent } from './utils';

type MessageContent = { ar?: string; en?: string; audioAr?: string; audioEn?: string };

export type EnsureMessageTemplate = (params: {
  category: string;
  context?: string | null;
  content: MessageContent;
  tags?: string[];
  isSystem?: boolean;
}) => Promise<string>;

export function createMessageTemplateHelper(prisma: PrismaClient) {
  const messageIdByKey = new Map<string, string>();
  const messageCounters = new Map<string, number>();

  const nextMessageCode = (category: string) => {
    const current = messageCounters.get(category) || 0;
    const next = current + 1;
    messageCounters.set(category, next);
    return `MSG_${category.toUpperCase()}_${String(next).padStart(4, '0')}`;
  };

  const ensureMessageTemplate: EnsureMessageTemplate = async (params) => {
    const normalized = normalizeMessageContent(params.content);
    const key = [
      params.category,
      params.context || '',
      normalized.ar,
      normalized.en,
      normalized.audioAr || '',
      normalized.audioEn || '',
    ].join('|');

    const existingId = messageIdByKey.get(key);
    if (existingId) return existingId;

    const created = await prisma.feedbackMessageTemplate.create({
      data: {
        code: nextMessageCode(params.category),
        category: params.category,
        context: params.context || null,
        content: normalized as object,
        tags: params.tags || [],
        isSystem: params.isSystem ?? false,
        isActive: true,
      },
    });

    messageIdByKey.set(key, created.id);
    return created.id;
  };

  return { ensureMessageTemplate };
}

export async function seedBaseMessageTemplates(ensureMessageTemplate: EnsureMessageTemplate) {
  const baseMessageTemplates = [
    { category: 'state', context: 'perfect', content: { ar: 'ممتاز!', en: 'Perfect!' }, tags: ['state', 'perfect'] },
    { category: 'state', context: 'normal', content: { ar: 'جيد', en: 'Good' }, tags: ['state', 'normal'] },
    { category: 'state', context: 'pad', content: { ar: 'مقبول', en: 'Acceptable' }, tags: ['state', 'pad'] },
    { category: 'state', context: 'warning', content: { ar: 'تحقق من وضعك', en: 'Check your position' }, tags: ['state', 'warning'] },
    { category: 'state', context: 'danger', content: { ar: 'توقف! وضع خطير', en: 'Stop! Dangerous position' }, tags: ['state', 'danger'] },
    { category: 'motivational', context: 'motivational', content: { ar: 'استمر!', en: 'Keep going!' }, tags: ['motivational'] },
    { category: 'motivational', context: 'motivational', content: { ar: 'أداء ممتاز!', en: 'Great job!' }, tags: ['motivational'] },
    { category: 'tip', context: 'tip', content: { ar: 'حافظ على الوضع الصحيح', en: 'Maintain proper form' }, tags: ['tip'] },
    { category: 'position', context: 'error', content: { ar: 'ضعية غير صحيحة', en: 'Incorrect position' }, tags: ['position', 'error'] },
  ];

  for (const template of baseMessageTemplates) {
    await ensureMessageTemplate(template);
  }

  console.log('✅ Base message templates created');
}
