import { generateSpeech } from '@/lib/gemini';
import type { LocalizedTextWithAudio } from '@/lib/types/localized';
import { getPrisma } from '@/lib/prisma/client';
import type {
  AudioMissingFilter,
  BulkGenerateAudioInput,
  BulkGenerateAudioResult,
  CreateMessageInput,
  MessageCategory,
  UpdateMessageInput,
} from './messages.types';

function parseMessageContent(raw: unknown): LocalizedTextWithAudio {
  const c = raw as Record<string, string | undefined>;
  return {
    ar: c?.ar ?? '',
    en: c?.en ?? '',
    audioAr: c.audioAr,
    audioEn: c.audioEn,
  };
}

function matchesAudioMissingFilter(content: LocalizedTextWithAudio, filter: AudioMissingFilter): boolean {
  const hasArText = !!content.ar?.trim();
  const hasEnText = !!content.en?.trim();
  const missAr = hasArText && !content.audioAr;
  const missEn = hasEnText && !content.audioEn;
  switch (filter) {
    case 'any':
      return missAr || missEn;
    case 'ar':
      return missAr;
    case 'en':
      return missEn;
    case 'complete': {
      const arOk = !hasArText || !!content.audioAr;
      const enOk = !hasEnText || !!content.audioEn;
      return arOk && enOk;
    }
    default:
      return true;
  }
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export const messagesService = {
  async list(options?: {
    includeInactive?: boolean;
    category?: string;
    audioMissing?: AudioMissingFilter;
  }) {
    const prisma = await getPrisma();
    const where: Record<string, unknown> = {};
    if (!options?.includeInactive) {
      where.isActive = true;
    }
    if (options?.category) {
      where.category = options.category;
    }
    let rows = await prisma.feedbackMessageTemplate.findMany({
      where,
      orderBy: { createdAt: 'desc' },
    });
    if (options?.audioMissing) {
      rows = rows.filter((row) => matchesAudioMissingFilter(parseMessageContent(row.content), options.audioMissing!));
    }
    return rows;
  },

  async bulkGenerateMissingAudio(input: BulkGenerateAudioInput): Promise<BulkGenerateAudioResult> {
    const prisma = await getPrisma();
    const langs: ('ar' | 'en')[] =
      input.languages && input.languages.length > 0 ? input.languages : ['ar', 'en'];
    const maxGen = input.maxGenerations ?? 50;
    const delayMs = input.delayMsBetweenCalls ?? 350;

    const where: Record<string, unknown> = {};
    if (input.includeInactive === false) {
      where.isActive = true;
    }
    if (input.category) {
      where.category = input.category as MessageCategory;
    }

    const messages = await prisma.feedbackMessageTemplate.findMany({
      where,
      orderBy: { code: 'asc' },
    });

    let plannedGenerations = 0;
    for (const msg of messages) {
      const content = parseMessageContent(msg.content);
      for (const lang of langs) {
        const text = (lang === 'ar' ? content.ar : content.en)?.trim();
        const hasAudio = lang === 'ar' ? !!content.audioAr : !!content.audioEn;
        if (text && !hasAudio) plannedGenerations++;
      }
    }

    const failed: BulkGenerateAudioResult['failed'] = [];
    let completedGenerations = 0;
    let skippedAlreadyPresent = 0;

    outer: for (const msg of messages) {
      let content = parseMessageContent(msg.content);
      let changed = false;

      for (const lang of langs) {
        if (completedGenerations >= maxGen) {
          if (changed) {
            await prisma.feedbackMessageTemplate.update({
              where: { id: msg.id },
              data: {
                content: content as object,
                updatedAt: new Date(),
              },
            });
          }
          break outer;
        }

        const text = (lang === 'ar' ? content.ar : content.en)?.trim();
        const hasAudio = lang === 'ar' ? !!content.audioAr : !!content.audioEn;
        if (!text || hasAudio) {
          if (text && hasAudio) skippedAlreadyPresent++;
          continue;
        }

        const result = await generateSpeech({ text, language: lang });
        if (!result.success || !result.audioPath) {
          failed.push({
            messageId: msg.id,
            code: msg.code,
            language: lang,
            error: result.error || 'TTS failed',
          });
          await delay(delayMs);
          continue;
        }

        if (lang === 'ar') {
          content = { ...content, audioAr: result.audioPath };
        } else {
          content = { ...content, audioEn: result.audioPath };
        }
        changed = true;
        completedGenerations++;
        await delay(delayMs);
      }

      if (changed) {
        await prisma.feedbackMessageTemplate.update({
          where: { id: msg.id },
          data: {
            content: content as object,
            updatedAt: new Date(),
          },
        });
      }
    }

    return {
      plannedGenerations,
      completedGenerations,
      skippedAlreadyPresent,
      failed,
      stoppedDueToLimit: completedGenerations >= maxGen && plannedGenerations > completedGenerations,
    };
  },

  async getById(id: string) {
    const prisma = await getPrisma();
    return prisma.feedbackMessageTemplate.findUnique({
      where: { id },
    });
  },

  async getByCode(code: string) {
    const prisma = await getPrisma();
    return prisma.feedbackMessageTemplate.findUnique({
      where: { code },
    });
  },

  async create(data: CreateMessageInput) {
    const prisma = await getPrisma();
    return prisma.feedbackMessageTemplate.create({
      data: {
        code: data.code,
        category: data.category,
        context: data.context || null,
        content: data.content as object,
        tags: data.tags || [],
        isSystem: data.isSystem ?? false,
        isActive: data.isActive ?? true,
      },
    });
  },

  async update(id: string, data: UpdateMessageInput) {
    const prisma = await getPrisma();
    const updateData: Record<string, unknown> = {
      updatedAt: new Date(),
    };

    if (data.code !== undefined) updateData.code = data.code;
    if (data.category !== undefined) updateData.category = data.category;
    if (data.context !== undefined) updateData.context = data.context || null;
    if (data.content !== undefined) updateData.content = data.content as object;
    if (data.tags !== undefined) updateData.tags = data.tags;
    if (data.isSystem !== undefined) updateData.isSystem = data.isSystem;
    if (data.isActive !== undefined) updateData.isActive = data.isActive;

    return prisma.feedbackMessageTemplate.update({
      where: { id },
      data: updateData,
    });
  },

  async delete(id: string) {
    const prisma = await getPrisma();
    return prisma.feedbackMessageTemplate.update({
      where: { id },
      data: { isActive: false },
    });
  },
};
