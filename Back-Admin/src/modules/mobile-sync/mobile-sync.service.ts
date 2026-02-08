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
  MobileSyncResponse,
  SyncData,
  ExerciseConfigWithMeta,
  AudioManifest,
  AudioFileInfo,
  MessageTemplate,
} from './mobile-sync.types';
import * as fs from 'fs/promises';
import * as path from 'path';
import { getGcsBucket, parseObjectNameFromUrl } from '@/lib/storage';

// Server version for API compatibility
const SERVER_VERSION = '1.0.0';

export const mobileSyncService = {
  /**
   * Perform sync operation
   * 
   * @param params Sync request parameters
   * @param baseUrl Base URL for audio files
   * @returns MobileSyncResponse
   */
  async sync(params: SyncRequestParams, baseUrl: string): Promise<MobileSyncResponse> {
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
    
    // Build message library (deduplicated)
    const messageLibrary = this.buildMessageLibrary(exercises as Parameters<typeof this.buildMessageLibrary>[0]);

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
    const programsExport = await programService.getPublishedForMobile();
    const filteredPrograms = updatedAfterDate
      ? programsExport.filter((program) => new Date(program.updatedAt) > updatedAfterDate)
      : programsExport;

    const totalPrograms = await prisma.program.count({
      where: { isPublished: true },
    });

    const deletedProgramIds: string[] = [];

    // Build audio manifest from message library
    const audioManifest = await this.buildAudioManifest(messageLibrary, baseUrl);
    
    // Build response
    const response: MobileSyncResponse = {
      success: true,
      timestamp: now.toISOString(),
      data: {
        exercises: exercisesWithMeta,
        messageLibrary,
        deletedExerciseIds,
        workouts: workoutsExport,
        deletedWorkoutIds,
        programs: filteredPrograms,
        deletedProgramIds,
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
    
    return Array.from(library.values());
  },
  
  /**
   * Build audio manifest from message library
   * Extracts all audio URLs from messages and validates they exist
   */
  async buildAudioManifest(
    messageLibrary: MessageTemplate[],
    baseUrl: string
  ): Promise<AudioManifest> {
    const audioFiles: AudioFileInfo[] = [];
    const seenUrls = new Set<string>();
    
    // Extract audio URLs from message templates
    for (const message of messageLibrary) {
      const { audioAr, audioEn } = message.content;
      const audioUrls = [audioAr, audioEn].filter((url): url is string => !!url);
      
      for (const url of audioUrls) {
        if (seenUrls.has(url)) continue;
        seenUrls.add(url);
        
        const filename = path.basename(url);
        const language = filename.includes('_ar_') ? 'ar' : 'en';
        
        // Get file size if available
        const fileSize = await this.getAudioFileSize(url);
        
        audioFiles.push({
          filename,
          url,
          size: fileSize,
          language: language as 'ar' | 'en',
        });
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
