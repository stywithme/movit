import type { FeedbackMessageTemplate } from '@prisma/client';
import { deleteAudioFile, generateSpeech } from '@/lib/gemini';
import type { LocalizedTextWithAudio } from '@/lib/types/localized';
import { getPrisma } from '@/lib/prisma/client';
import type {
  AudioMissingFilter,
  BulkGenerateAudioInput,
  BulkGenerateAudioLanguage,
  BulkGenerateAudioMode,
  BulkGenerateAudioPreview,
  BulkGenerateAudioResult,
  CreateMessageInput,
  MessageCategory,
  MessageStatusFilter,
  UpdateMessageInput,
} from './messages.types';

/**
 * Normalize library `content` JSON. Audio may be stored as camelCase (editor) or legacy snake_case.
 * Only non-empty trimmed strings count as "has audio" for skip logic.
 */
function parseMessageContent(raw: unknown): LocalizedTextWithAudio {
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) {
    return { ar: '', en: '' };
  }
  const c = raw as Record<string, unknown>;
  const text = (v: unknown) => (typeof v === 'string' ? v : '');
  const audioUrl = (camel: string, snake: string): string | undefined => {
    const v = c[camel] ?? c[snake];
    if (typeof v === 'string' && v.trim()) return v.trim();
    return undefined;
  };
  return {
    ar: text(c.ar),
    en: text(c.en),
    audioAr: audioUrl('audioAr', 'audio_ar'),
    audioEn: audioUrl('audioEn', 'audio_en'),
  };
}

function getTextForLanguage(content: LocalizedTextWithAudio, lang: BulkGenerateAudioLanguage): string {
  return lang === 'ar' ? content.ar ?? '' : content.en ?? '';
}

function getAudioForLanguage(content: LocalizedTextWithAudio, lang: BulkGenerateAudioLanguage): string | undefined {
  return lang === 'ar' ? content.audioAr : content.audioEn;
}

function setAudioForLanguage(
  content: LocalizedTextWithAudio,
  lang: BulkGenerateAudioLanguage,
  audioUrl: string
): LocalizedTextWithAudio {
  return lang === 'ar'
    ? { ...content, audioAr: audioUrl }
    : { ...content, audioEn: audioUrl };
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

function buildListWhere(options: {
  includeInactive?: boolean;
  category?: string;
  status?: MessageStatusFilter;
}): Record<string, unknown> {
  const where: Record<string, unknown> = {};
  if (!options.includeInactive) {
    where.isActive = true;
  } else {
    if (options.status === 'active') where.isActive = true;
    else if (options.status === 'inactive') where.isActive = false;
  }
  if (options.category) {
    where.category = options.category;
  }
  return where;
}

function filterMessagesBySearch<
  T extends { code: string; tags: string[]; content: unknown },
>(rows: T[], search: string): T[] {
  const q = search.trim().toLowerCase();
  if (!q) return rows;
  return rows.filter((m) => {
    const c = parseMessageContent(m.content);
    return (
      m.code.toLowerCase().includes(q) ||
      m.tags.some((t) => t.toLowerCase().includes(q)) ||
      (c.en || '').toLowerCase().includes(q) ||
      (c.ar || '').toLowerCase().includes(q)
    );
  });
}

function buildBulkSlotKey(messageId: string, language: BulkGenerateAudioLanguage): string {
  return `${messageId}:${language}`;
}

function normalizeOptionalString(value?: string): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

interface NormalizedBulkGenerateAudioInput {
  includeInactive?: boolean;
  category?: MessageCategory;
  status?: MessageStatusFilter;
  search?: string;
  audioMissing?: AudioMissingFilter;
  languages: BulkGenerateAudioLanguage[];
  mode: BulkGenerateAudioMode;
  maxGenerations: number;
  delayMsBetweenCalls: number;
  excludeSlots: Set<string>;
  model?: string;
  voiceNameAr?: string;
  voiceNameEn?: string;
  languageCodeAr?: string;
  languageCodeEn?: string;
  sharedStylePrompt?: string;
  stylePromptAr?: string;
  stylePromptEn?: string;
  systemInstruction?: string;
  temperature?: number;
  seed?: number;
}

interface BulkCandidateSlot {
  key: string;
  messageId: string;
  code: string;
  language: BulkGenerateAudioLanguage;
  text: string;
  existingAudio?: string;
}

interface BulkCandidateSummary {
  matchedMessages: number;
  eligibleMessages: number;
  plannedGenerations: number;
  missingAudioSlots: number;
  existingAudioSlots: number;
  byLanguage: Record<BulkGenerateAudioLanguage, number>;
  skippedAlreadyPresent: number;
  slots: BulkCandidateSlot[];
}

function normalizeBulkGenerateAudioInput(input?: BulkGenerateAudioInput): NormalizedBulkGenerateAudioInput {
  const rawLanguages = (input?.languages ?? []).filter(
    (lang): lang is BulkGenerateAudioLanguage => lang === 'ar' || lang === 'en'
  );
  const languages: BulkGenerateAudioLanguage[] =
    rawLanguages.length > 0 ? Array.from(new Set(rawLanguages)) : ['ar', 'en'];
  const rawTemperature = input?.temperature;
  const rawSeed = input?.seed;

  return {
    includeInactive: input?.includeInactive,
    category: input?.category,
    status: input?.status,
    search: normalizeOptionalString(input?.search),
    audioMissing: input?.audioMissing,
    languages,
    mode: input?.mode === 'regenerate_selected' ? 'regenerate_selected' : 'missing_only',
    maxGenerations: Math.min(200, Math.max(1, Math.trunc(input?.maxGenerations ?? 50))),
    delayMsBetweenCalls: Math.min(5000, Math.max(0, Math.trunc(input?.delayMsBetweenCalls ?? 350))),
    excludeSlots: new Set((input?.excludeSlots ?? []).filter((slot) => typeof slot === 'string' && slot.trim())),
    model: normalizeOptionalString(input?.model),
    voiceNameAr: normalizeOptionalString(input?.voiceNameAr),
    voiceNameEn: normalizeOptionalString(input?.voiceNameEn),
    languageCodeAr: normalizeOptionalString(input?.languageCodeAr),
    languageCodeEn: normalizeOptionalString(input?.languageCodeEn),
    sharedStylePrompt: normalizeOptionalString(input?.sharedStylePrompt),
    stylePromptAr: normalizeOptionalString(input?.stylePromptAr),
    stylePromptEn: normalizeOptionalString(input?.stylePromptEn),
    systemInstruction: normalizeOptionalString(input?.systemInstruction),
    temperature:
      typeof rawTemperature === 'number' && Number.isFinite(rawTemperature)
        ? Math.min(2, Math.max(0, rawTemperature))
        : undefined,
    seed:
      typeof rawSeed === 'number' && Number.isFinite(rawSeed)
        ? Math.trunc(rawSeed)
        : undefined,
  };
}

async function listBulkScopeMessages(
  prisma: Awaited<ReturnType<typeof getPrisma>>,
  input: NormalizedBulkGenerateAudioInput
): Promise<FeedbackMessageTemplate[]> {
  const where = buildListWhere({
    includeInactive: input.includeInactive,
    category: input.category,
    status: input.status,
  });

  let rows = await prisma.feedbackMessageTemplate.findMany({
    where,
    orderBy: { code: 'asc' },
  });

  if (input.audioMissing) {
    rows = rows.filter((row) => matchesAudioMissingFilter(parseMessageContent(row.content), input.audioMissing!));
  }
  if (input.search) {
    rows = filterMessagesBySearch(rows, input.search);
  }

  return rows;
}

function collectBulkCandidates(
  messages: FeedbackMessageTemplate[],
  input: NormalizedBulkGenerateAudioInput
): BulkCandidateSummary {
  const byLanguage: Record<BulkGenerateAudioLanguage, number> = { ar: 0, en: 0 };
  const eligibleMessageIds = new Set<string>();
  const slots: BulkCandidateSlot[] = [];
  let skippedAlreadyPresent = 0;
  let missingAudioSlots = 0;
  let existingAudioSlots = 0;

  for (const message of messages) {
    const content = parseMessageContent(message.content);
    let messageHasEligibleSlot = false;

    for (const language of input.languages) {
      const text = getTextForLanguage(content, language).trim();
      if (!text) continue;

      const key = buildBulkSlotKey(message.id, language);
      if (input.excludeSlots.has(key)) continue;

      const existingAudio = getAudioForLanguage(content, language);
      if (input.mode === 'missing_only' && existingAudio) {
        skippedAlreadyPresent++;
        continue;
      }

      slots.push({
        key,
        messageId: message.id,
        code: message.code,
        language,
        text,
        existingAudio,
      });

      if (existingAudio) {
        existingAudioSlots++;
      } else {
        missingAudioSlots++;
      }

      byLanguage[language]++;
      messageHasEligibleSlot = true;
    }

    if (messageHasEligibleSlot) {
      eligibleMessageIds.add(message.id);
    }
  }

  return {
    matchedMessages: messages.length,
    eligibleMessages: eligibleMessageIds.size,
    plannedGenerations: slots.length,
    missingAudioSlots,
    existingAudioSlots,
    byLanguage,
    skippedAlreadyPresent,
    slots,
  };
}

/** Bump exercise.updatedAt so incremental mobile sync picks up new library audio. */
async function touchExercisesForMessageIds(
  prisma: Awaited<ReturnType<typeof getPrisma>>,
  messageIds: Iterable<string>
): Promise<void> {
  const ids = [...new Set(messageIds)];
  if (ids.length === 0) return;
  await prisma.exercise.updateMany({
    where: {
      poseVariants: {
        some: {
          messageAssignments: {
            some: { messageId: { in: ids } },
          },
        },
      },
    },
    data: { updatedAt: new Date() },
  });
}

export const messagesService = {
  async list(options?: {
    includeInactive?: boolean;
    category?: string;
    audioMissing?: AudioMissingFilter;
  }) {
    const prisma = await getPrisma();
    const where = buildListWhere({
      includeInactive: options?.includeInactive,
      category: options?.category,
      status: undefined,
    });
    let rows = await prisma.feedbackMessageTemplate.findMany({
      where,
      orderBy: { createdAt: 'desc' },
    });
    if (options?.audioMissing) {
      rows = rows.filter((row) => matchesAudioMissingFilter(parseMessageContent(row.content), options.audioMissing!));
    }
    return rows;
  },

  async listPaged(options: {
    page: number;
    limit: number;
    includeInactive?: boolean;
    category?: string;
    audioMissing?: AudioMissingFilter;
    status?: 'active' | 'inactive';
    search?: string;
  }) {
    const prisma = await getPrisma();
    const limit = Math.min(100, Math.max(1, options.limit));
    let page = Math.max(1, options.page);

    const where = buildListWhere({
      includeInactive: options.includeInactive,
      category: options.category,
      status: options.status,
    });

    const needsMemoryFilter = !!(options.audioMissing || options.search?.trim());

    if (!needsMemoryFilter) {
      const total = await prisma.feedbackMessageTemplate.count({ where });
      const totalPages = Math.max(1, Math.ceil(total / limit) || 1);
      page = Math.min(page, totalPages);
      const skip = (page - 1) * limit;
      const items = await prisma.feedbackMessageTemplate.findMany({
        where,
        orderBy: { createdAt: 'desc' },
        skip,
        take: limit,
      });
      return { items, total, page, limit, totalPages };
    }

    let rows = await prisma.feedbackMessageTemplate.findMany({
      where,
      orderBy: { createdAt: 'desc' },
    });
    if (options.audioMissing) {
      rows = rows.filter((row) => matchesAudioMissingFilter(parseMessageContent(row.content), options.audioMissing!));
    }
    if (options.search?.trim()) {
      rows = filterMessagesBySearch(rows, options.search);
    }

    const total = rows.length;
    const totalPages = Math.max(1, Math.ceil(total / limit) || 1);
    page = Math.min(page, totalPages);
    const skip = (page - 1) * limit;
    const items = rows.slice(skip, skip + limit);
    return { items, total, page, limit, totalPages };
  },

  async previewBulkAudio(input?: BulkGenerateAudioInput): Promise<BulkGenerateAudioPreview> {
    const prisma = await getPrisma();
    const normalized = normalizeBulkGenerateAudioInput(input);
    const messages = await listBulkScopeMessages(prisma, normalized);
    const summary = collectBulkCandidates(messages, normalized);

    return {
      matchedMessages: summary.matchedMessages,
      eligibleMessages: summary.eligibleMessages,
      plannedGenerations: summary.plannedGenerations,
      missingAudioSlots: summary.missingAudioSlots,
      existingAudioSlots: summary.existingAudioSlots,
      byLanguage: summary.byLanguage,
    };
  },

  async bulkGenerateAudio(input?: BulkGenerateAudioInput): Promise<BulkGenerateAudioResult> {
    const prisma = await getPrisma();
    const normalized = normalizeBulkGenerateAudioInput(input);
    const messages = await listBulkScopeMessages(prisma, normalized);
    const summary = collectBulkCandidates(messages, normalized);
    const eligibleSlotKeys = new Set(summary.slots.map((slot) => slot.key));

    const failed: BulkGenerateAudioResult['failed'] = [];
    let completedGenerations = 0;
    const completedSlots: string[] = [];
    const touchedMessageIds = new Set<string>();

    const persistMessageContent = async (
      messageId: string,
      content: LocalizedTextWithAudio,
      replacedAudioPaths: string[]
    ) => {
      await prisma.feedbackMessageTemplate.update({
        where: { id: messageId },
        data: {
          content: content as object,
          updatedAt: new Date(),
        },
      });
      touchedMessageIds.add(messageId);

      for (const audioPath of [...new Set(replacedAudioPaths.filter(Boolean))]) {
        const deleted = await deleteAudioFile(audioPath);
        if (!deleted) {
          console.warn('[TTS] Failed to delete replaced audio:', audioPath);
        }
      }
    };

    outer: for (const message of messages) {
      let content = parseMessageContent(message.content);
      let changed = false;
      const replacedAudioPaths: string[] = [];

      for (const language of normalized.languages) {
        if (completedGenerations >= normalized.maxGenerations) {
          if (changed) {
            await persistMessageContent(message.id, content, replacedAudioPaths);
          }
          break outer;
        }

        const slotKey = buildBulkSlotKey(message.id, language);
        if (!eligibleSlotKeys.has(slotKey)) {
          continue;
        }

        const text = getTextForLanguage(content, language).trim();
        const previousAudio = getAudioForLanguage(content, language);
        const result = await generateSpeech({
          text,
          language,
          model: normalized.model,
          voiceName: language === 'ar' ? normalized.voiceNameAr : normalized.voiceNameEn,
          languageCode: language === 'ar' ? normalized.languageCodeAr : normalized.languageCodeEn,
          sharedStylePrompt: normalized.sharedStylePrompt,
          stylePrompt: language === 'ar' ? normalized.stylePromptAr : normalized.stylePromptEn,
          systemInstruction: normalized.systemInstruction,
          temperature: normalized.temperature,
          seed: normalized.seed,
        });

        if (!result.success || !result.audioPath) {
          failed.push({
            slotKey,
            messageId: message.id,
            code: message.code,
            language,
            error: result.error || 'TTS failed',
          });
          await delay(normalized.delayMsBetweenCalls);
          continue;
        }

        if (previousAudio && previousAudio !== result.audioPath) {
          replacedAudioPaths.push(previousAudio);
        }

        content = setAudioForLanguage(content, language, result.audioPath);
        changed = true;
        completedGenerations++;
        completedSlots.push(slotKey);
        await delay(normalized.delayMsBetweenCalls);
      }

      if (changed) {
        await persistMessageContent(message.id, content, replacedAudioPaths);
      }
    }

    if (touchedMessageIds.size > 0) {
      await touchExercisesForMessageIds(prisma, touchedMessageIds);
    }

    const remainingGenerations = Math.max(0, summary.plannedGenerations - completedSlots.length);

    return {
      matchedMessages: summary.matchedMessages,
      eligibleMessages: summary.eligibleMessages,
      plannedGenerations: summary.plannedGenerations,
      missingAudioSlots: summary.missingAudioSlots,
      existingAudioSlots: summary.existingAudioSlots,
      completedGenerations,
      skippedAlreadyPresent: summary.skippedAlreadyPresent,
      failed,
      stoppedDueToLimit: completedGenerations >= normalized.maxGenerations && remainingGenerations > 0,
      remainingGenerations,
      completedSlots,
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

    const row = await prisma.feedbackMessageTemplate.update({
      where: { id },
      data: updateData,
    });

    if (data.content !== undefined) {
      await touchExercisesForMessageIds(prisma, [id]);
    }

    return row;
  },

  async delete(id: string) {
    const prisma = await getPrisma();
    return prisma.feedbackMessageTemplate.update({
      where: { id },
      data: { isActive: false },
    });
  },
};
