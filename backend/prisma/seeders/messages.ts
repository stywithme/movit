import type { PrismaClient } from '@prisma/client';
import { normalizeMessageContent } from './utils';

type MessageContent = { ar?: string; en?: string; audioAr?: string; audioEn?: string };

/** Merge seed text with existing row; keep stored audio when seed omits it. */
export function mergeMessageContentPreserveAudio(existingJson: unknown, incoming: MessageContent): MessageContent {
  const ex = (existingJson && typeof existingJson === 'object' ? existingJson : {}) as MessageContent;
  return normalizeMessageContent({
    ar: incoming.ar !== undefined && incoming.ar !== '' ? incoming.ar : ex.ar,
    en: incoming.en !== undefined && incoming.en !== '' ? incoming.en : ex.en,
    audioAr: incoming.audioAr ?? ex.audioAr,
    audioEn: incoming.audioEn ?? ex.audioEn,
  });
}

export type EnsureMessageTemplate = (params: {
  /** Stable key; links exercises and dashboard rows across re-seeds. */
  code: string;
  category: string;
  context?: string | null;
  content: MessageContent;
  tags?: string[];
  isSystem?: boolean;
  description?: string | null;
}) => Promise<string>;

export function createMessageTemplateHelper(prisma: PrismaClient) {
  const ensureMessageTemplate: EnsureMessageTemplate = async (params) => {
    const existing = await prisma.feedbackMessageTemplate.findUnique({
      where: { code: params.code },
    });

    const merged = existing
      ? mergeMessageContentPreserveAudio(existing.content, params.content)
      : normalizeMessageContent(params.content);

    const row = await prisma.feedbackMessageTemplate.upsert({
      where: { code: params.code },
      create: {
        code: params.code,
        category: params.category,
        context: params.context ?? null,
        description: params.description ?? null,
        content: merged as object,
        tags: params.tags || [],
        isSystem: params.isSystem ?? false,
        isActive: true,
      },
      update: {
        category: params.category,
        context: params.context ?? null,
        description: params.description ?? undefined,
        content: merged as object,
        tags: params.tags || [],
        isSystem: params.isSystem ?? undefined,
        isActive: true,
      },
    });

    return row.id;
  };

  return { ensureMessageTemplate };
}

export async function seedBaseMessageTemplates(ensureMessageTemplate: EnsureMessageTemplate) {
  const baseMessageTemplates: Array<{
    code: string;
    category: string;
    context?: string | null;
    content: MessageContent;
    tags: string[];
  }> = [
    { code: 'base_state_perfect', category: 'state', context: 'perfect', content: { ar: 'ممتاز!', en: 'Perfect!' }, tags: ['state', 'perfect'] },
    { code: 'base_state_normal', category: 'state', context: 'normal', content: { ar: 'جيد', en: 'Good' }, tags: ['state', 'normal'] },
    { code: 'base_state_pad', category: 'state', context: 'pad', content: { ar: 'مقبول', en: 'Acceptable' }, tags: ['state', 'pad'] },
    { code: 'base_state_warning', category: 'state', context: 'warning', content: { ar: 'تحقق من وضعك', en: 'Check your position' }, tags: ['state', 'warning'] },
    { code: 'base_state_danger', category: 'state', context: 'danger', content: { ar: 'توقف! وضع خطير', en: 'Stop! Dangerous position' }, tags: ['state', 'danger'] },
    { code: 'base_motivational_1', category: 'motivational', context: 'motivational', content: { ar: 'استمر!', en: 'Keep going!' }, tags: ['motivational'] },
    { code: 'base_motivational_2', category: 'motivational', context: 'motivational', content: { ar: 'أداء ممتاز!', en: 'Great job!' }, tags: ['motivational'] },
    { code: 'base_tip_form', category: 'tip', context: 'tip', content: { ar: 'حافظ على الوضع الصحيح', en: 'Maintain proper form' }, tags: ['tip'] },
    { code: 'base_position_error', category: 'position', context: 'error', content: { ar: 'ضعية غير صحيحة', en: 'Incorrect position' }, tags: ['position', 'error'] },
  ];

  for (const template of baseMessageTemplates) {
    await ensureMessageTemplate({
      code: template.code,
      category: template.category,
      context: template.context,
      content: template.content,
      tags: template.tags,
    });
  }

  console.log('✅ Base message templates upserted');
}
