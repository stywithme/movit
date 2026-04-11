/**
 * Mobile Sync Service
 * ====================
 * 
 * Handles synchronization logic for mobile apps.
 * Supports both full sync and incremental sync based on timestamp.
 */

import { getPrisma } from '@/lib/prisma/client';
import { buildExerciseConfig, exerciseFullInclude } from '@/modules/exercises/json-builder';
import { programService } from '@/modules/programs/programs.service';
import { workoutService } from '@/modules/workouts/workouts.service';
import type { WorkoutExport } from '@/modules/workouts/workouts.types';
import type {
  SyncRequestParams,
  ExploreRequestParams,
  MobileSyncResponse,
  MobileExploreResponse,
  SyncData,
  ExerciseConfigWithMeta,
  AudioManifest,
  AudioFileInfo,
  MessageTemplate,
  SystemMessageTemplate,
  UserProgramExport,
  SessionReportExport,
  ExploreData,
} from './mobile-sync.types';
import * as fs from 'fs/promises';
import * as path from 'path';
import { getGcsBucket, parseObjectNameFromUrl } from '@/lib/storage';

// Server version for API compatibility
const SERVER_VERSION = '1.0.0';

function toSyncLocalizedText(value: Record<string, unknown> | null | undefined) {
  const ar = typeof value?.ar === 'string' ? value.ar : '';
  const en = typeof value?.en === 'string' ? value.en : '';
  return { ar, en };
}

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

/** Collect audioAr/audioEn from a LocalizedText-like object (inline JSON). */
async function addUrlsFromLocalizedLike(
  value: unknown,
  addUrl: (url: string) => Promise<void>
): Promise<void> {
  if (!value || typeof value !== 'object') return;
  const o = value as Record<string, unknown>;
  if (typeof o.audioAr === 'string') await addUrl(o.audioAr);
  if (typeof o.audioEn === 'string') await addUrl(o.audioEn);
}

/** Walk stateMessages (simple or zone up/down) for audio URLs. */
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

export const mobileSyncService = {
  async getExplore(
    params: ExploreRequestParams = {}
  ): Promise<MobileExploreResponse> {
    const prisma = await getPrisma();
    const now = new Date();
    const limit = Math.min(Math.max(params.limit ?? 6, 1), 20);
    const isFullSync = !params.updatedAfter;

    let updatedAfterDate: Date | null = null;
    if (params.updatedAfter) {
      const parsed = new Date(params.updatedAfter);
      if (!isNaN(parsed.getTime())) {
        updatedAfterDate = parsed;
      }
    }

    const programWhere: Record<string, unknown> = {
      isPublished: true,
      deletedAt: null,
    };
    const workoutWhere: Record<string, unknown> = {
      status: 'published',
      deletedAt: null,
    };
    const exerciseWhere: Record<string, unknown> = {
      status: 'published',
      deletedAt: null,
    };
    const levelWhere: Record<string, unknown> = {};

    if (updatedAfterDate) {
      programWhere.updatedAt = { gt: updatedAfterDate };
      workoutWhere.updatedAt = { gt: updatedAfterDate };
      exerciseWhere.updatedAt = { gt: updatedAfterDate };
      levelWhere.updatedAt = { gt: updatedAfterDate };
    }

    const [levels, programs, workouts, exercises] = await Promise.all([
      prisma.level.findMany({
        where: levelWhere,
        orderBy: { number: 'asc' },
        take: limit,
      }),
      prisma.program.findMany({
        where: programWhere,
        orderBy: [
          { isFeatured: 'desc' },
          { updatedAt: 'desc' },
        ],
        take: limit,
      }),
      prisma.workout.findMany({
        where: workoutWhere,
        include: {
          exercises: {
            select: { id: true },
          },
        },
        orderBy: [
          { isFeatured: 'desc' },
          { updatedAt: 'desc' },
        ],
        take: limit,
      }),
      prisma.exercise.findMany({
        where: exerciseWhere,
        include: {
          category: {
            select: {
              code: true,
              name: true,
            },
          },
          attributes: {
            select: {
              attributeValue: {
                select: {
                  attribute: {
                    select: { code: true },
                  },
                },
              },
            },
          },
        },
        orderBy: [
          { isFeatured: 'desc' },
          { updatedAt: 'desc' },
        ],
        take: limit,
      }),
    ]);

    let deletedProgramIds: string[] = [];
    let deletedWorkoutIds: string[] = [];
    let deletedExerciseIds: string[] = [];
    if (updatedAfterDate) {
      const [deletedPrograms, unpublishedPrograms, deletedWorkouts, unpublishedWorkouts, deletedExercises, unpublishedExercises] =
        await Promise.all([
          prisma.program.findMany({
            where: { deletedAt: { gt: updatedAfterDate } },
            select: { id: true },
          }),
          prisma.program.findMany({
            where: { isPublished: false, deletedAt: null, updatedAt: { gt: updatedAfterDate } },
            select: { id: true },
          }),
          prisma.workout.findMany({
            where: { deletedAt: { gt: updatedAfterDate } },
            select: { id: true },
          }),
          prisma.workout.findMany({
            where: { status: 'draft', deletedAt: null, updatedAt: { gt: updatedAfterDate } },
            select: { id: true },
          }),
          prisma.exercise.findMany({
            where: { deletedAt: { gt: updatedAfterDate } },
            select: { id: true },
          }),
          prisma.exercise.findMany({
            where: { status: 'draft', deletedAt: null, updatedAt: { gt: updatedAfterDate } },
            select: { id: true },
          }),
        ]);

      deletedProgramIds = [...deletedPrograms.map((p) => p.id), ...unpublishedPrograms.map((p) => p.id)];
      deletedWorkoutIds = [...deletedWorkouts.map((w) => w.id), ...unpublishedWorkouts.map((w) => w.id)];
      deletedExerciseIds = [...deletedExercises.map((e) => e.id), ...unpublishedExercises.map((e) => e.id)];
    }

    const data: ExploreData = {
      levels: levels.map((l) => ({
        number: l.number,
        code: l.code,
        name: toSyncLocalizedText(l.name as Record<string, unknown>),
        description: l.description ? toSyncLocalizedText(l.description as Record<string, unknown>) : null,
        color: l.color,
        updatedAt: l.updatedAt?.toISOString?.() ?? now.toISOString(),
      })),
      programs: programs.map((p) => ({
        id: p.id,
        slug: p.slug,
        name: toSyncLocalizedText(p.name as Record<string, unknown>),
        difficulty: p.difficulty,
        durationWeeks: p.durationWeeks,
        coverImageUrl: p.coverImageUrl,
        updatedAt: p.updatedAt.toISOString(),
      })),
      workouts: workouts.map((w) => ({
        id: w.id,
        slug: w.slug,
        name: toSyncLocalizedText(w.name as Record<string, unknown>),
        difficulty: w.difficulty,
        estimatedDurationMin: w.estimatedDurationMin,
        coverImageUrl: w.coverImageUrl,
        exerciseCount: w.exercises.length,
        updatedAt: w.updatedAt.toISOString(),
      })),
      exercises: exercises.map((e) => ({
        id: e.id,
        slug: e.slug,
        name: toSyncLocalizedText(e.name as Record<string, unknown>),
        categoryCode: e.category?.code ?? null,
        categoryName: e.category?.name ? toSyncLocalizedText(e.category.name as Record<string, unknown>) : null,
        musclesCount: e.attributes.filter((a) => a.attributeValue?.attribute?.code === 'muscle').length,
        updatedAt: e.updatedAt.toISOString(),
      })),
      deletedProgramIds,
      deletedWorkoutIds,
      deletedExerciseIds,
    };

    return {
      success: true,
      timestamp: now.toISOString(),
      data,
      meta: {
        isFullSync,
        serverVersion: SERVER_VERSION,
        levelsInResponse: data.levels.length,
        programsInResponse: data.programs.length,
        workoutsInResponse: data.workouts.length,
        exercisesInResponse: data.exercises.length,
      },
    };
  },

  /**
   * Perform sync operation
   * 
   * @param params Sync request parameters
   * @param baseUrl Base URL for audio files
   * @returns MobileSyncResponse
   */
  async sync(params: SyncRequestParams, baseUrl: string, userId?: string | null): Promise<MobileSyncResponse> {
    const prisma = await getPrisma();
    const now = new Date();
    // Explicit boolean for type safety
    const isFullSync: boolean = !params.updatedAfter || params.forceRefresh === true;
    
    // Parse the updatedAfter timestamp if provided
    let updatedAfterDate: Date | null = null;
    if (params.updatedAfter && !params.forceRefresh) {
      try {
        updatedAfterDate = new Date(params.updatedAfter);
        if (isNaN(updatedAfterDate.getTime())) {
          updatedAfterDate = null;
        }
      } catch {
        updatedAfterDate = null;
      }
    }
    
    // Build query conditions
    const whereCondition: Record<string, unknown> = {
      status: 'published',
      deletedAt: null,
    };
    
    // For incremental sync, filter by updatedAt
    if (updatedAfterDate) {
      whereCondition.updatedAt = {
        gt: updatedAfterDate,
      };
    }
    
    // Fetch exercises
    const exercises = await prisma.exercise.findMany({
      where: whereCondition,
      include: exerciseFullInclude,
      orderBy: { updatedAt: 'desc' },
    });
    
    // Get total count of published exercises
    const totalExercises = await prisma.exercise.count({
      where: {
        status: 'published',
        deletedAt: null,
      },
    });
    
    // Get removed exercise IDs (for incremental sync)
    // This includes both:
    // 1. Deleted exercises (deletedAt > updatedAfter)
    // 2. Unpublished exercises (status changed from 'published' to 'draft' after updatedAfter)
    let deletedExerciseIds: string[] = [];
    if (updatedAfterDate) {
      // Deleted exercises
      const deletedExercises = await prisma.exercise.findMany({
        where: {
          deletedAt: {
            gt: updatedAfterDate,
          },
        },
        select: { id: true },
      });
      
      // Unpublished exercises (were updated after timestamp and are now draft)
      // These should be removed from mobile cache
      const unpublishedExercises = await prisma.exercise.findMany({
        where: {
          status: 'draft',
          deletedAt: null,
          updatedAt: {
            gt: updatedAfterDate,
          },
        },
        select: { id: true },
      });
      
      deletedExerciseIds = [
        ...deletedExercises.map(e => e.id),
        ...unpublishedExercises.map(e => e.id),
      ];
    }
    
    // Build message library (deduplicated) from exercises in this sync batch
    const messageLibrary = this.buildMessageLibrary(exercises as Parameters<typeof this.buildMessageLibrary>[0]);

    const systemMessageRows = await prisma.feedbackMessageTemplate.findMany({
      where: { isSystem: true, isActive: true, category: 'system' },
      orderBy: { code: 'asc' },
    });
    const systemMessages: SystemMessageTemplate[] = systemMessageRows.map((m) => ({
      code: m.code,
      content: toLocalizedTextWithAudioFromDb(m.content),
      updatedAt: m.updatedAt.toISOString(),
    }));

    // Always compute global message stats (across ALL published exercises)
    // so mobile can detect stale caches even during incremental syncs
    const messageLibraryStats = await this.getGlobalMessageStats(prisma);

    // Transform exercises to mobile format with metadata (message assignments only)
    const exercisesWithMeta: ExerciseConfigWithMeta[] = exercises.map(exercise => {
      const config = buildExerciseConfig(exercise as Parameters<typeof buildExerciseConfig>[0], {
        includeMessages: false,
        includeAssignments: true,
      });
      return {
        ...config,
        id: exercise.id,
        slug: exercise.slug,
        updatedAt: exercise.updatedAt.toISOString(),
      };
    });
    
    // Fetch workouts
    const workoutWhereCondition: Record<string, unknown> = {
      status: 'published',
      deletedAt: null,
    };
    
    if (updatedAfterDate) {
      workoutWhereCondition.updatedAt = {
        gt: updatedAfterDate,
      };
    }
    
    const workouts = await prisma.workout.findMany({
      where: workoutWhereCondition,
      include: {
        exercises: {
          orderBy: { sortOrder: 'asc' },
          include: {
            exercise: {
              select: {
                id: true,
                slug: true,
                name: true,
                countingMethod: { select: { code: true } },
              },
            },
          },
        },
      },
      orderBy: { updatedAt: 'desc' },
    });
    
    // Get total count of published workouts
    const totalWorkouts = await prisma.workout.count({
      where: {
        status: 'published',
        deletedAt: null,
      },
    });
    
    // Get deleted workout IDs (for incremental sync)
    let deletedWorkoutIds: string[] = [];
    if (updatedAfterDate) {
      const deletedWorkouts = await prisma.workout.findMany({
        where: {
          deletedAt: { gt: updatedAfterDate },
        },
        select: { id: true },
      });
      
      const unpublishedWorkouts = await prisma.workout.findMany({
        where: {
          status: 'draft',
          deletedAt: null,
          updatedAt: { gt: updatedAfterDate },
        },
        select: { id: true },
      });
      
      deletedWorkoutIds = [
        ...deletedWorkouts.map(w => w.id),
        ...unpublishedWorkouts.map(w => w.id),
      ];
    }
    
    // Transform workouts to mobile format
    const workoutsExport: WorkoutExport[] = workouts.map(workout => {
      const result = workoutService.buildWorkoutExport(workout as Parameters<typeof workoutService.buildWorkoutExport>[0]);
      return result!;
    }).filter((w): w is WorkoutExport => w !== null);
    
    // Fetch programs
    const filteredPrograms = await programService.getPublishedForMobile(updatedAfterDate);

    const totalPrograms = await prisma.program.count({
      where: { isPublished: true, deletedAt: null },
    });

    let deletedProgramIds: string[] = [];
    if (updatedAfterDate) {
      const deletedPrograms = await prisma.program.findMany({
        where: {
          deletedAt: { gt: updatedAfterDate },
        },
        select: { id: true },
      });

      const unpublishedPrograms = await prisma.program.findMany({
        where: {
          isPublished: false,
          deletedAt: null,
          updatedAt: { gt: updatedAfterDate },
        },
        select: { id: true },
      });

      deletedProgramIds = [
        ...deletedPrograms.map((p) => p.id),
        ...unpublishedPrograms.map((p) => p.id),
      ];
    }

    // Build audio manifest from message library + system messages + inline exercise audio URLs
    const audioManifest = await this.buildAudioManifest(
      messageLibrary,
      systemMessages,
      exercisesWithMeta,
      baseUrl
    );
    
    let userPrograms: UserProgramExport[] | undefined;
    let sessionReports: SessionReportExport[] | undefined;
    if (userId) {
      const userProgramRows = await prisma.userProgram.findMany({
        where: { userId },
        orderBy: { updatedAt: 'desc' },
      });
      userPrograms = userProgramRows.map((row) => ({
        id: row.id,
        programId: row.programId,
        name: row.name ? toSyncLocalizedText(row.name as Record<string, unknown>) : undefined,
        startDate: row.startDate.toISOString(),
        isActive: row.isActive,
        customizations: (row.customizations as Record<string, unknown>) || null,
        updatedAt: row.updatedAt.toISOString(),
      }));

      // Fetch completed session reports for this user
      const reportRows = await prisma.programSessionReport.findMany({
        where: {
          userId,
          status: 'completed',
        },
        orderBy: [{ weekNumber: 'asc' }, { dayNumber: 'asc' }],
      });
      sessionReports = reportRows.map((r) => ({
        id: r.id,
        sessionId: r.programSessionId,
        programId: r.programId ?? '',
        weekNumber: r.weekNumber,
        dayNumber: r.dayNumber,
        startedAt: r.startedAt.toISOString(),
        completedAt: r.completedAt?.toISOString() ?? r.startedAt.toISOString(),
        status: r.status,
        totalDurationMs: r.totalDurationMs ?? 0,
        totalExercises: r.totalExercises ?? 0,
        totalSets: r.totalSets ?? 0,
        completedSets: r.completedSets ?? 0,
        totalReps: r.totalReps ?? 0,
        avgAccuracy: r.avgAccuracy ?? 0,
        avgFormScore: r.avgFormScore ?? undefined,
        report: r.report ?? undefined,
      }));
    }

    // Build response
    const response: MobileSyncResponse = {
      success: true,
      timestamp: now.toISOString(),
      data: {
        exercises: exercisesWithMeta,
        messageLibrary,
        systemMessages,
        deletedExerciseIds,
        workouts: workoutsExport,
        deletedWorkoutIds,
        programs: filteredPrograms,
        deletedProgramIds,
        userPrograms,
        sessionReports,
        audioManifest,
      },
      meta: {
        totalExercises,
        totalWorkouts,
        totalPrograms,
        isFullSync,
        serverVersion: SERVER_VERSION,
        exercisesInResponse: exercisesWithMeta.length,
        workoutsInResponse: workoutsExport.length,
        programsInResponse: filteredPrograms.length,
        messageLibraryStats,
      },
    };
    
    return response;
  },

  /**
   * Build message library from exercise assignments (deduplicated)
   */
  buildMessageLibrary(
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
  },
  
  /**
   * Get global message stats across ALL published exercises.
   * Used by mobile to detect stale message caches.
   */
  async getGlobalMessageStats(
    prisma: Awaited<ReturnType<typeof getPrisma>>
  ): Promise<{ totalMessages: number; totalWithAudio: number; totalAssignments: number }> {
    const totalAssignments = await prisma.feedbackMessageAssignment.count({
      where: {
        poseVariant: {
          exercise: { status: 'published', deletedAt: null },
        },
      },
    });

    const distinctMessages = await prisma.feedbackMessageAssignment.findMany({
      where: {
        poseVariant: {
          exercise: { status: 'published', deletedAt: null },
        },
      },
      select: { messageId: true },
      distinct: ['messageId'],
    });

    const messageIds = distinctMessages.map(m => m.messageId);
    let totalWithAudio = 0;
    if (messageIds.length > 0) {
      const messages = await prisma.feedbackMessageTemplate.findMany({
        where: { id: { in: messageIds } },
        select: { content: true },
      });
      totalWithAudio = messages.filter(m => {
        const c = m.content as Record<string, unknown> | null;
        return c && (typeof c.audioAr === 'string' || typeof c.audioEn === 'string');
      }).length;
    }

    return {
      totalMessages: messageIds.length,
      totalWithAudio,
      totalAssignments,
    };
  },

  /**
   * Build audio manifest from message library and inline exercise content
   * (positionChecks.errorMessage, trackedJoints.stateMessages).
   */
  async buildAudioManifest(
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
      const fileSize = await this.getAudioFileSize(url);
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
  },
  
  /**
   * Get audio file size from disk
   */
  async getAudioFileSize(audioPath: string): Promise<number | undefined> {
    try {
      const objectName = parseObjectNameFromUrl(audioPath);
      if (objectName) {
        const bucket = getGcsBucket();
        const [metadata] = await bucket.file(objectName).getMetadata();
        const size = metadata.size ? Number(metadata.size) : undefined;
        return Number.isFinite(size) ? size : undefined;
      }

      // Fallback for local files (legacy)
      const relativePath = audioPath.startsWith('/') ? audioPath.slice(1) : audioPath;
      const fullPath = path.join(process.cwd(), 'public', relativePath);
      const stats = await fs.stat(fullPath);
      return stats.size;
    } catch {
      return undefined;
    }
  },
  
  /**
   * Get list of all audio files in the TTS directory
   */
  async getAllAudioFiles(baseUrl: string): Promise<AudioFileInfo[]> {
    const audioDir = path.join(process.cwd(), 'public', 'audio', 'tts');
    const files: AudioFileInfo[] = [];
    
    try {
      const entries = await fs.readdir(audioDir);
      
      for (const filename of entries) {
        if (!filename.endsWith('.wav')) continue;
        
        const fullPath = path.join(audioDir, filename);
        const stats = await fs.stat(fullPath);
        const language = filename.includes('_ar_') ? 'ar' : 'en';
        
        files.push({
          filename,
          url: `/audio/tts/${filename}`,
          size: stats.size,
          language: language as 'ar' | 'en',
        });
      }
    } catch (error) {
      console.error('Error reading audio directory:', error);
    }
    
    return files;
  },
};
