/**
 * Mobile Sync Types
 * =================
 * 
 * Types for the mobile sync API that enables incremental and full
 * synchronization of exercises and workouts with the Android app.
 */

import type { ExerciseConfig } from '@/lib/types/android-schema';
import type { WorkoutExport } from '@/modules/workouts/workouts.types';

// ============================================
// REQUEST TYPES
// ============================================

export interface SyncRequestParams {
  /**
   * ISO timestamp for incremental sync.
   * If provided, only exercises updated after this time are returned.
   * If omitted, a full sync is performed.
   */
  updatedAfter?: string;
  
  /**
   * Force a full refresh, ignoring updatedAfter.
   */
  forceRefresh?: boolean;
}

// ============================================
// RESPONSE TYPES
// ============================================

export interface MobileSyncResponse {
  success: boolean;
  timestamp: string;  // Current server time (ISO)
  data: SyncData;
  meta: SyncMeta;
}

export interface SyncData {
  /**
   * List of exercises (full config for mobile app)
   */
  exercises: ExerciseConfigWithMeta[];
  
  /**
   * IDs of exercises that were deleted since last sync
   */
  deletedExerciseIds: string[];
  
  /**
   * List of workouts (Super Sets / Circuits)
   */
  workouts: WorkoutExport[];
  
  /**
   * IDs of workouts that were deleted since last sync
   */
  deletedWorkoutIds: string[];
  
  /**
   * Audio files manifest for download
   */
  audioManifest: AudioManifest;
}

export interface ExerciseConfigWithMeta extends ExerciseConfig {
  /**
   * Unique exercise ID from database
   */
  id: string;
  
  /**
   * Slug for URL-friendly identification
   */
  slug: string;
  
  /**
   * Last update timestamp
   */
  updatedAt: string;
}

export interface SyncMeta {
  /**
   * Total number of published exercises (not just in this response)
   */
  totalExercises: number;
  
  /**
   * Total number of published workouts
   */
  totalWorkouts: number;
  
  /**
   * Whether this is a full sync or incremental
   */
  isFullSync: boolean;
  
  /**
   * Server/API version for compatibility checks
   */
  serverVersion: string;
  
  /**
   * Number of exercises in this response
   */
  exercisesInResponse: number;
  
  /**
   * Number of workouts in this response
   */
  workoutsInResponse: number;
}

// ============================================
// AUDIO MANIFEST
// ============================================

export interface AudioManifest {
  /**
   * Base URL for audio files
   */
  baseUrl: string;
  
  /**
   * List of audio files with metadata
   */
  files: AudioFileInfo[];
}

export interface AudioFileInfo {
  /**
   * Filename (e.g., "tts_ar_123.wav")
   */
  filename: string;
  
  /**
   * Full URL path (relative or absolute)
   */
  url: string;
  
  /**
   * File size in bytes (for download progress)
   */
  size?: number;
  
  /**
   * Language code
   */
  language: 'ar' | 'en';
  
  /**
   * Associated exercise ID (for cache invalidation)
   */
  exerciseId?: string;
}

// ============================================
// INTERNAL TYPES
// ============================================

export interface ExerciseWithAudioUrls {
  id: string;
  slug: string;
  updatedAt: Date;
  audioUrls: Set<string>;
}
