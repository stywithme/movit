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
import { workoutService } from '@/modules/workout-templates/workout-templates.service';
import type { WorkoutExport } from '@/modules/workout-templates/workout-templates.types';
import { listUserExercisePreferences } from '@/modules/user-exercise-preferences/user-exercise-preferences.service';
import type {
  SyncRequestParams,
  ExploreRequestParams,
  MobileSyncResponse,
  MobileExploreResponse,
  SyncData,
  ExerciseConfigWithMeta,
  AudioFileInfo,
  UserProgramExport,
  PlannedWorkoutReportExport,
  ExploreData,
} from './mobile-sync.types';
import * as fs from 'fs/promises';
import * as path from 'path';
import {
  buildAudioManifest,
  buildMessageLibrary,
  loadSystemMessages,
} from './mobile-audio-manifest.service';

// Server version for API compatibility
const SERVER_VERSION = '1.0.0';

/** Legacy mobile sync keys (pre workoutTemplates rename). */
function withLegacyWorkoutSyncAliases<T>(workouts: T[]) {
  return {
    workoutTemplates: workouts,
    workouts,
  };
}

function withLegacyWorkoutDeleteAliases(ids: string[]) {
  return {
    deletedWorkoutTemplateIds: ids,
    deletedWorkoutIds: ids,
  };
}

function withLegacyWorkoutMetaAliases(
  totalWorkoutTemplates: number,
  workoutTemplatesInResponse: number
) {
  return {
    totalWorkoutTemplates,
    totalWorkouts: totalWorkoutTemplates,
    workoutTemplatesInResponse,
    workoutsInResponse: workoutTemplatesInResponse,
  };
}

function toSyncLocalizedText(value: Record<string, unknown> | null | undefined) {
  const ar = typeof value?.ar === 'string' ? value.ar : '';
  const en = typeof value?.en === 'string' ? value.en : '';
  return { ar, en };
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
      prisma.workoutTemplate.findMany({
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
    let deletedWorkoutTemplateIds: string[] = [];
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
          prisma.workoutTemplate.findMany({
            where: { deletedAt: { gt: updatedAfterDate } },
            select: { id: true },
          }),
          prisma.workoutTemplate.findMany({
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
      deletedWorkoutTemplateIds = [...deletedWorkouts.map((w) => w.id), ...unpublishedWorkouts.map((w) => w.id)];
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
        levelRangeMin: p.levelRangeMin,
        levelRangeMax: p.levelRangeMax,
        durationWeeks: p.durationWeeks,
        coverImageUrl: p.coverImageUrl,
        updatedAt: p.updatedAt.toISOString(),
      })),
      ...withLegacyWorkoutSyncAliases(
        workouts.map((w) => ({
          id: w.id,
          slug: w.slug,
          name: toSyncLocalizedText(w.name as Record<string, unknown>),
          difficulty: w.difficulty,
          estimatedDurationMin: w.estimatedDurationMin,
          coverImageUrl: w.coverImageUrl,
          exerciseCount: w.exercises.length,
          updatedAt: w.updatedAt.toISOString(),
        }))
      ),
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
      ...withLegacyWorkoutDeleteAliases(deletedWorkoutTemplateIds),
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
        workoutTemplatesInResponse: data.workoutTemplates.length,
        workoutsInResponse: data.workoutTemplates.length,
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
    const messageLibrary = buildMessageLibrary(exercises as Parameters<typeof buildExerciseConfig>[0][]);

    const systemMessages = await loadSystemMessages();

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
    
    const workouts = await prisma.workoutTemplate.findMany({
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
    const totalWorkoutTemplates = await prisma.workoutTemplate.count({
      where: {
        status: 'published',
        deletedAt: null,
      },
    });
    
    // Get deleted workout IDs (for incremental sync)
    let deletedWorkoutTemplateIds: string[] = [];
    if (updatedAfterDate) {
      const deletedWorkouts = await prisma.workoutTemplate.findMany({
        where: {
          deletedAt: { gt: updatedAfterDate },
        },
        select: { id: true },
      });
      
      const unpublishedWorkouts = await prisma.workoutTemplate.findMany({
        where: {
          status: 'draft',
          deletedAt: null,
          updatedAt: { gt: updatedAfterDate },
        },
        select: { id: true },
      });
      
      deletedWorkoutTemplateIds = [
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
    const audioManifest = await buildAudioManifest(
      messageLibrary,
      systemMessages,
      exercisesWithMeta,
      baseUrl
    );
    
    let userPrograms: UserProgramExport[] | undefined;
    let plannedWorkoutReports: PlannedWorkoutReportExport[] | undefined;
    let userExercisePreferences: Awaited<ReturnType<typeof listUserExercisePreferences>> | undefined;
    if (userId) {
      userExercisePreferences = await listUserExercisePreferences(userId);

      const [userProgramRows, trainingProfile] = await Promise.all([
        prisma.userProgram.findMany({
          where: { userId },
          orderBy: { updatedAt: 'desc' },
        }),
        prisma.trainingProfile.findUnique({
          where: { userId },
          select: { trainingWeekdays: true },
        }),
      ]);
      const trainingWeekdays = trainingProfile?.trainingWeekdays ?? [];
      userPrograms = userProgramRows.map((row) => ({
        id: row.id,
        programId: row.programId,
        name: row.name ? toSyncLocalizedText(row.name as Record<string, unknown>) : undefined,
        startDate: row.startDate.toISOString(),
        isActive: row.isActive,
        customizations: (row.customizations as Record<string, unknown>) || null,
        updatedAt: row.updatedAt.toISOString(),
        customizationsUpdatedAt: row.customizationsUpdatedAt?.toISOString() ?? null,
        trainingWeekdays,
      }));

      // Fetch completed planned workout reports for this user
      const reportRows = await prisma.plannedWorkoutReport.findMany({
        where: {
          userId,
          status: 'completed',
        },
        orderBy: [{ weekNumber: 'asc' }, { dayNumber: 'asc' }],
      });
      plannedWorkoutReports = reportRows.map((r) => ({
        id: r.id,
        plannedWorkoutId: r.plannedWorkoutId,
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
        rpe: r.rpe ?? undefined,
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
        ...withLegacyWorkoutSyncAliases(workoutsExport),
        ...withLegacyWorkoutDeleteAliases(deletedWorkoutTemplateIds),
        programs: filteredPrograms,
        deletedProgramIds,
        userPrograms,
        userExercisePreferences,
        plannedWorkoutReports,
        audioManifest,
      },
      meta: {
        totalExercises,
        totalPrograms,
        isFullSync,
        serverVersion: SERVER_VERSION,
        exercisesInResponse: exercisesWithMeta.length,
        ...withLegacyWorkoutMetaAliases(totalWorkoutTemplates, workoutsExport.length),
        programsInResponse: filteredPrograms.length,
        messageLibraryStats,
      },
    };
    
    return response;
  },

  /**
   * Get global message stats across ALL published exercises.
   * Used by mobile to detect stale message caches.
   */
  async getGlobalMessageStats(
    prisma: Awaited<ReturnType<typeof getPrisma>>
  ): Promise<{
    totalMessages: number;
    totalWithAudio: number;
    totalAssignments: number;
    fingerprint: string;
  }> {
    const pubEx = { status: 'published' as const, deletedAt: null };

    const [
      totalAssignments,
      distinctMessages,
      linkedTplMax,
      assignCreatedMax,
      systemTplMax,
    ] = await Promise.all([
      prisma.feedbackMessageAssignment.count({
        where: {
          poseVariant: {
            exercise: pubEx,
          },
        },
      }),
      prisma.feedbackMessageAssignment.findMany({
        where: {
          poseVariant: {
            exercise: pubEx,
          },
        },
        select: { messageId: true },
        distinct: ['messageId'],
      }),
      prisma.feedbackMessageTemplate.aggregate({
        _max: { updatedAt: true },
        where: {
          assignments: {
            some: {
              poseVariant: {
                exercise: pubEx,
              },
            },
          },
        },
      }),
      prisma.feedbackMessageAssignment.aggregate({
        _max: { createdAt: true },
        where: {
          poseVariant: {
            exercise: pubEx,
          },
        },
      }),
      prisma.feedbackMessageTemplate.aggregate({
        _max: { updatedAt: true },
        where: { isSystem: true, isActive: true, category: 'system' },
      }),
    ]);

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

    const fingerprint = [
      linkedTplMax._max.updatedAt?.getTime() ?? 0,
      systemTplMax._max.updatedAt?.getTime() ?? 0,
      assignCreatedMax._max.createdAt?.getTime() ?? 0,
      totalAssignments,
      messageIds.length,
      totalWithAudio,
    ].join(':');

    return {
      totalMessages: messageIds.length,
      totalWithAudio,
      totalAssignments,
      fingerprint,
    };
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
