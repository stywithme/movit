/**
 * Mobile Sync Types
 * =================
 * 
 * Types for the mobile sync API that enables incremental and full
 * synchronization of exercises and workouts with the Android app.
 */

import type { ExerciseConfig, LocalizedText } from '@/lib/types/android-schema';
import type { ProgramExport } from '@/modules/programs/programs.types';
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

export interface ExploreRequestParams {
  /**
   * ISO timestamp for incremental sync.
   * If provided, only items updated after this time are returned.
   */
  updatedAfter?: string;

  /**
   * Max items per section.
   */
  limit?: number;
}

export interface MobileExploreResponse {
  success: boolean;
  timestamp: string;
  data: ExploreData;
  meta: ExploreMeta;
}

export interface ExploreData {
  levels: ExploreLevelItem[];
  programs: ExploreProgramItem[];
  workouts: ExploreWorkoutItem[];
  exercises: ExploreExerciseItem[];
  deletedProgramIds: string[];
  deletedWorkoutIds: string[];
  deletedExerciseIds: string[];
}

export interface ExploreLevelItem {
  number: number;
  code: string;
  name: LocalizedText;
  description?: LocalizedText | null;
  color?: string | null;
  updatedAt?: string;
}

export interface ExploreProgramItem {
  id: string;
  slug: string;
  name: LocalizedText;
  levelRangeMin: number;
  levelRangeMax: number;
  durationWeeks: number;
  coverImageUrl?: string | null;
  updatedAt: string;
}

export interface ExploreWorkoutItem {
  id: string;
  slug: string;
  name: LocalizedText;
  difficulty: string;
  estimatedDurationMin?: number | null;
  coverImageUrl?: string | null;
  exerciseCount: number;
  updatedAt: string;
}

export interface ExploreExerciseItem {
  id: string;
  slug: string;
  name: LocalizedText;
  categoryCode?: string | null;
  categoryName?: LocalizedText | null;
  musclesCount: number;
  updatedAt: string;
}

export interface ExploreMeta {
  isFullSync: boolean;
  serverVersion: string;
  levelsInResponse: number;
  programsInResponse: number;
  workoutsInResponse: number;
  exercisesInResponse: number;
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
   * Message library for feedback/state/position messages
   */
  messageLibrary: MessageTemplate[];

  /**
   * Fixed-key system messages (training UI / TTS); editable text/audio from dashboard
   */
  systemMessages: SystemMessageTemplate[];
  
  /**
   * IDs of exercises that were deleted since last sync
   */
  deletedExerciseIds: string[];
  
  /**
   * List of workouts (Super Sets / Circuits)
   */
  workouts: WorkoutExport[];

  /**
   * List of programs (Weeks / Days / Sessions)
   */
  programs: ProgramExport[];
  
  /**
   * IDs of workouts that were deleted since last sync
   */
  deletedWorkoutIds: string[];

  /**
   * IDs of programs that were deleted since last sync
   */
  deletedProgramIds: string[];

  /**
   * User programs (enrollments/customizations)
   */
  userPrograms?: UserProgramExport[];

  /**
   * Per-user exercise targets (reps, hold duration, weight) for standalone training
   */
  userExercisePreferences?: UserExercisePreferenceExport[];

  /**
   * Completed session reports for the user (backend → mobile sync).
   * Ensures reports survive app reinstall and are consistent across devices.
   */
  sessionReports?: SessionReportExport[];
  
  /**
   * Audio files manifest for download
   */
  audioManifest: AudioManifest;
}

export interface UserProgramExport {
  id: string;
  programId?: string | null;
  name?: LocalizedText;
  startDate: string;
  isActive: boolean;
  customizations?: Record<string, unknown> | null;
  updatedAt: string;
  customizationsUpdatedAt?: string | null;
  /** Copy of user's onboarding training weekdays (0=Sun … 6=Sat). */
  trainingWeekdays?: number[];
}

/** User overrides for a single exercise (mobile standalone training) */
export interface UserExercisePreferenceExport {
  exerciseId: string;
  exerciseSlug: string;
  customReps?: number;
  customDurationSec?: number;
  customWeightKg?: number;
  updatedAt: string;
}

export interface SessionReportExport {
  id: string;
  sessionId: string;       // programSessionId
  programId: string;
  weekNumber: number;
  dayNumber: number;
  startedAt: string;       // ISO
  completedAt: string;     // ISO
  status: string;
  totalDurationMs: number;
  totalExercises: number;
  totalSets: number;
  completedSets: number;
  totalReps: number;
  avgAccuracy: number;
  avgFormScore?: number;
  rpe?: number | null;
  report?: unknown;        // Full JSON report (ExerciseReports, SetMetrics, RepDetails)
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
   * Total number of published programs
   */
  totalPrograms: number;
  
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

  /**
   * Number of programs in this response
   */
  programsInResponse: number;

  /**
   * Global message library stats (across ALL published exercises).
   * Mobile uses this to detect stale message caches.
   */
  messageLibraryStats: {
    totalMessages: number;
    totalWithAudio: number;
    totalAssignments: number;
    /** Stable-ish signal for message/audio/assignment changes beyond raw counts */
    fingerprint: string;
  };
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
// MESSAGE LIBRARY
// ============================================

export interface MessageTemplate {
  id: string;
  code: string;
  category: string;
  context?: string | null;
  content: LocalizedText;
}

/** System message (mobile looks up by code); no id in sync payload — code is the key */
export interface SystemMessageTemplate {
  code: string;
  content: LocalizedText;
  updatedAt: string;
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
