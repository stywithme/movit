/**
 * Mobile audio manifest — shared by full sync and per-entity prefetch endpoints.
 */

import { getPrisma } from '@/lib/prisma/client';
import { buildExerciseConfig, exerciseFullInclude } from '@/modules/exercises/json-builder';
import type {
  AudioFileInfo,
  AudioManifest,
  ExerciseConfigWithMeta,
  MessageTemplate,
  SystemMessageTemplate,
} from './mobile-sync.types';
import * as fs from 'fs/promises';
import * as path from 'path';
import { getGcsBucket, parseObjectNameFromUrl } from '@/lib/storage';

function toLocalizedTextWithAudioFromDb(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return { ar: '', en: '' };
  }
  const v = value as Record<string, unknown>;
  const ar = typeof v.ar === 'string' ? v.ar : '';
  const en = typeof v.en === 'string' ? v.en : '';
  const audioAr = typeof v.audioAr === 'string' ? v.audioAr : undefined;
  const audioEn = typeof v.audioEn === 'string' ? v.audioEn : undefined;
  const legacyAr = typeof v.audio_ar === 'string' ? v.audio_ar : undefined;
  const legacyEn = typeof v.audio_en === 'string' ? v.audio_en : undefined;
  return {
    ar,
    en,
    ...(audioAr || legacyAr ? { audioAr: audioAr || legacyAr } : {}),
    ...(audioEn || legacyEn ? { audioEn: audioEn || legacyEn } : {}),
  };
}

async function addUrlsFromLocalizedLike(
  value: unknown,
  addUrl: (url: string) => Promise<void>
): Promise<void> {
  if (!value || typeof value !== 'object') return;
  const o = value as Record<string, unknown>;
  if (typeof o.audioAr === 'string') await addUrl(o.audioAr);
  if (typeof o.audioEn === 'string') await addUrl(o.audioEn);
}

async function scanStateMessagesForAudio(
  stateMessages: unknown,
  addUrl: (url: string) => Promise<void>
): Promise<void> {
  if (!stateMessages || typeof stateMessages !== 'object') return;
  const sm = stateMessages as Record<string, unknown>;
  const keys = ['perfect', 'normal', 'pad', 'warning', 'danger'] as const;
  for (const k of keys) {
    const val = sm[k];
    if (!val || typeof val !== 'object') continue;
    const obj = val as Record<string, unknown>;
    if ('up' in obj || 'down' in obj) {
      await addUrlsFromLocalizedLike(obj.up, addUrl);
      await addUrlsFromLocalizedLike(obj.down, addUrl);
    } else {
      await addUrlsFromLocalizedLike(obj, addUrl);
    }
  }
}

async function scanTrackedJointsForAudio(
  trackedJoints: unknown,
  addUrl: (url: string) => Promise<void>
): Promise<void> {
  if (!Array.isArray(trackedJoints)) return;
  for (const j of trackedJoints) {
    if (!j || typeof j !== 'object') continue;
    const joint = j as Record<string, unknown>;
    await scanStateMessagesForAudio(joint.stateMessages, addUrl);
  }
}

export async function getAudioFileSize(audioPath: string): Promise<number | undefined> {
  try {
    const objectName = parseObjectNameFromUrl(audioPath);
    if (objectName) {
      const bucket = getGcsBucket();
      const [metadata] = await bucket.file(objectName).getMetadata();
      const size = metadata.size ? Number(metadata.size) : undefined;
      return Number.isFinite(size) ? size : undefined;
    }

    const relativePath = audioPath.startsWith('/') ? audioPath.slice(1) : audioPath;
    const fullPath = path.join(process.cwd(), 'public', relativePath);
    const stats = await fs.stat(fullPath);
    return stats.size;
  } catch {
    return undefined;
  }
}

/**
 * Build message library from exercise assignments (deduplicated)
 */
export function buildMessageLibrary(
  exercises: Array<Parameters<typeof buildExerciseConfig>[0]>
): MessageTemplate[] {
  const library = new Map<string, MessageTemplate>();

  const toLocalizedText = (value: Record<string, unknown> | null | undefined) => {
    const ar = typeof value?.ar === 'string' ? value.ar : '';
    const en = typeof value?.en === 'string' ? value.en : '';
    const audioAr = typeof value?.audioAr === 'string' ? value.audioAr : undefined;
    const audioEn = typeof value?.audioEn === 'string' ? value.audioEn : undefined;
    return {
      ar,
      en,
      ...(audioAr ? { audioAr } : {}),
      ...(audioEn ? { audioEn } : {}),
    };
  };

  for (const exercise of exercises) {
    for (const variant of exercise.poseVariants || []) {
      for (const assignment of variant.messageAssignments || []) {
        const message = assignment?.message;
        if (!message?.id || library.has(message.id)) continue;
        library.set(message.id, {
          id: message.id,
          code: message.code,
          category: message.category,
          context: message.context ?? null,
          content: toLocalizedText(message.content),
        });
      }
    }
  }

  const entries = Array.from(library.values());
  const withAudio = entries.filter(m => m.content.audioAr || m.content.audioEn).length;
  console.log(
    `[MobileSync] messageLibrary: total=${entries.length}, withAudio=${withAudio}, withoutAudio=${entries.length - withAudio}`
  );
  return entries;
}

export async function buildAudioManifest(
  messageLibrary: MessageTemplate[],
  systemMessages: SystemMessageTemplate[],
  exercises: ExerciseConfigWithMeta[],
  baseUrl: string
): Promise<AudioManifest> {
  const audioFiles: AudioFileInfo[] = [];
  const seenUrls = new Set<string>();

  const addUrl = async (url: string) => {
    if (!url || seenUrls.has(url)) return;
    seenUrls.add(url);
    const filename = path.basename(url);
    const language = filename.includes('_ar_') ? 'ar' : 'en';
    const fileSize = await getAudioFileSize(url);
    audioFiles.push({
      filename,
      url,
      size: fileSize,
      language: language as 'ar' | 'en',
    });
  };

  for (const message of messageLibrary) {
    const { audioAr, audioEn } = message.content;
    if (audioAr) await addUrl(audioAr);
    if (audioEn) await addUrl(audioEn);
  }

  for (const sm of systemMessages) {
    const { audioAr, audioEn } = sm.content;
    if (audioAr) await addUrl(audioAr);
    if (audioEn) await addUrl(audioEn);
  }

  for (const exercise of exercises) {
    for (const variant of exercise.poseVariants || []) {
      for (const pc of variant.positionChecks || []) {
        await addUrlsFromLocalizedLike(pc.errorMessage, addUrl);
      }
      await scanTrackedJointsForAudio(variant.trackedJoints, addUrl);
    }
  }

  return {
    baseUrl,
    files: audioFiles,
  };
}

export async function loadSystemMessages(): Promise<SystemMessageTemplate[]> {
  const prisma = await getPrisma();
  const systemMessageRows = await prisma.feedbackMessageTemplate.findMany({
    where: { isSystem: true, isActive: true, category: 'system' },
    orderBy: { code: 'asc' },
  });
  return systemMessageRows.map(m => ({
    code: m.code,
    content: toLocalizedTextWithAudioFromDb(m.content),
    updatedAt: m.updatedAt.toISOString(),
  }));
}

type ExerciseRowForConfig = Parameters<typeof buildExerciseConfig>[0] & {
  slug: string;
  updatedAt: Date;
};

function toExerciseConfigWithMeta(exercise: ExerciseRowForConfig): ExerciseConfigWithMeta {
  const config = buildExerciseConfig(exercise, {
    includeMessages: false,
    includeAssignments: true,
  });
  return {
    ...config,
    id: exercise.id,
    slug: exercise.slug,
    updatedAt: exercise.updatedAt.toISOString(),
  };
}

/**
 * Audio manifest for one published exercise (library + inline audio + system messages).
 */
export async function buildAudioManifestForExerciseSlug(
  slug: string,
  baseUrl: string
): Promise<AudioManifest | null> {
  const prisma = await getPrisma();
  const exercise = await prisma.exercise.findFirst({
    where: { slug: slug.trim(), status: 'published', deletedAt: null },
    include: exerciseFullInclude,
  });
  if (!exercise) return null;

  const exercisesWithMeta = [toExerciseConfigWithMeta(exercise as ExerciseRowForConfig)];
  const messageLibrary = buildMessageLibrary([exercise as ExerciseRowForConfig]);
  const systemMessages = await loadSystemMessages();
  return buildAudioManifest(messageLibrary, systemMessages, exercisesWithMeta, baseUrl);
}

/**
 * Audio manifest for all exercises referenced by one published workout (deduped URLs).
 */
export async function buildAudioManifestForWorkoutSlug(
  slug: string,
  baseUrl: string
): Promise<AudioManifest | null> {
  const prisma = await getPrisma();
  const workout = await prisma.workout.findFirst({
    where: { slug: slug.trim(), status: 'published', deletedAt: null },
    include: {
      exercises: {
        orderBy: { sortOrder: 'asc' },
        include: {
          exercise: { select: { slug: true } },
        },
      },
    },
  });
  if (!workout) return null;

  const slugs = [...new Set(workout.exercises.map(we => we.exercise.slug))];
  if (slugs.length === 0) {
    const systemMessages = await loadSystemMessages();
    return buildAudioManifest([], systemMessages, [], baseUrl);
  }

  const dbExercises = await prisma.exercise.findMany({
    where: { slug: { in: slugs }, status: 'published', deletedAt: null },
    include: exerciseFullInclude,
  });

  const exercisesWithMeta = dbExercises.map(ex => toExerciseConfigWithMeta(ex as ExerciseRowForConfig));
  const messageLibrary = buildMessageLibrary(dbExercises as ExerciseRowForConfig[]);
  const systemMessages = await loadSystemMessages();
  return buildAudioManifest(messageLibrary, systemMessages, exercisesWithMeta, baseUrl);
}
