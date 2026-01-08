/**
 * JSON Builder - DB to Android Contract Mapper
 * =============================================
 * 
 * This module is the SINGLE point of transformation from
 * database models to the Android JSON schema.
 * 
 * IMPORTANT: Any change to the Android contract should ONLY be reflected here.
 */

import type {
  ExerciseConfig,
  PoseVariantConfig,
  DifficultyLevelConfig,
  TrackedJoint,
  PrimaryTrackedJoint,
  SecondaryTrackedJoint,
  PositionCheck,
  FeedbackMessages,
  LocalizedText,
  CountingMethod,
  CameraPosition,
  DifficultyLevel,
  FacingDirection,
  PhaseName,
  AngleRange,
  DifficultyRanges,
  RepCountingConfig,
} from '@/lib/types/android-schema';

import { getPhasesForCountingMethod, validateExerciseConfig } from '@/lib/types/android-schema';

// ============================================
// DATABASE TYPES (from Prisma include)
// ============================================

/**
 * Expected shape of exercise from Prisma query with full includes
 */
interface DbExercise {
  id: string;
  name: Record<string, string>;
  description?: Record<string, string> | null;
  instructions?: Record<string, string> | null;
  category: {
    code: string;
    name: Record<string, string>;
  };
  countingMethod: {
    code: string;
  };
  attributes: Array<{
    attributeValue: {
      code: string;
      attribute: {
        code: string;
      };
    };
  }>;
  poseVariants: DbPoseVariant[];
}

interface DbPoseVariant {
  id: string;
  name: Record<string, string>;
  description?: Record<string, string> | null;
  expectedFacingDirection?: string | null;
  trackedJointsConfig?: unknown;
  cameraPosition: {
    code: string;
    schemaCode?: string | null;
  };
  positionChecks: DbPositionCheck[];
  feedbackMessages: DbFeedbackMessage[];
  difficultyLevels: DbDifficultyLevel[];
}

interface DbPositionCheck {
  id: string;
  checkId: string;
  type: string;
  landmarks: unknown;
  condition: unknown;
  activePhases: unknown;
  errorMessage: Record<string, string>;
  severity: string;
  cooldownMs: number;
  minErrorFrames: number;
}

interface DbFeedbackMessage {
  type: string;
  message: Record<string, string>;
}

interface DbDifficultyLevel {
  id: string;
  difficultyType: {
    code: string;
  };
  repCountingConfig?: unknown;
  phases?: unknown;
}

// ============================================
// BUILDER FUNCTIONS
// ============================================

/**
 * Build complete Android-compatible ExerciseConfig from database exercise
 */
export function buildExerciseConfig(dbExercise: DbExercise): ExerciseConfig {
  const countingMethod = dbExercise.countingMethod.code as CountingMethod;
  
  // Extract attribute codes by type
  const muscles: string[] = [];
  const equipment: string[] = [];
  const tags: string[] = [];
  
  for (const attr of dbExercise.attributes) {
    const attrCode = attr.attributeValue.attribute.code;
    const valueCode = attr.attributeValue.code;
    
    if (attrCode === 'muscle') muscles.push(valueCode);
    else if (attrCode === 'equipment') equipment.push(valueCode);
    else if (attrCode === 'tag') tags.push(valueCode);
  }
  
  const config: ExerciseConfig = {
    name: toLocalizedText(dbExercise.name),
    category: {
      code: dbExercise.category.code,
      name: toLocalizedText(dbExercise.category.name),
    },
    countingMethod,
    muscles,
    equipment,
    tags,
    poseVariants: dbExercise.poseVariants.map(pv => 
      buildPoseVariantConfig(pv, countingMethod)
    ),
  };
  
  // Only include optional fields if they have values
  if (dbExercise.description) {
    config.description = toLocalizedText(dbExercise.description);
  }
  if (dbExercise.instructions) {
    config.instructions = toLocalizedText(dbExercise.instructions);
  }
  
  return config;
}

/**
 * Build PoseVariantConfig from database pose variant
 */
function buildPoseVariantConfig(
  dbVariant: DbPoseVariant,
  countingMethod: CountingMethod
): PoseVariantConfig {
  // Use schemaCode for Android, fallback to code if not set
  const cameraPosition = (dbVariant.cameraPosition.schemaCode || 
    mapCameraCodeToSchema(dbVariant.cameraPosition.code)) as CameraPosition;
  
  const expectedFacingDirection = (dbVariant.expectedFacingDirection || 
    'auto_detect') as FacingDirection;
  
  // Parse trackedJointsConfig from JSON
  const trackedJoints = parseTrackedJoints(dbVariant.trackedJointsConfig);
  
  // Parse position checks
  const positionChecks = dbVariant.positionChecks.map(buildPositionCheck);
  
  // Group feedback messages by type
  const feedbackMessages = buildFeedbackMessages(dbVariant.feedbackMessages);
  
  // Build difficulty levels
  const difficultyLevels = buildDifficultyLevels(
    dbVariant.difficultyLevels,
    countingMethod
  );
  
  return {
    name: toLocalizedText(dbVariant.name),
    cameraPosition,
    expectedFacingDirection,
    trackedJoints,
    positionChecks,
    feedbackMessages,
    difficultyLevels,
  };
}

/**
 * Parse trackedJointsConfig JSON to TrackedJoint array
 */
function parseTrackedJoints(config: unknown): TrackedJoint[] {
  if (!config || !Array.isArray(config)) {
    return [];
  }
  
  return config.map((joint: Record<string, unknown>) => {
    const baseJoint = {
      joint: joint.joint as string,
      role: joint.role as 'primary' | 'secondary',
      startPose: joint.startPose as AngleRange,
      errorMessages: joint.errorMessages as {
        tooLow: LocalizedText;
        tooHigh: LocalizedText;
      },
    };
    
    if (joint.pairedWith) {
      Object.assign(baseJoint, { pairedWith: joint.pairedWith as string });
    }
    
    if (joint.role === 'primary') {
      return {
        ...baseJoint,
        role: 'primary' as const,
        upRange: joint.upRange as DifficultyRanges,
        downRange: joint.downRange as DifficultyRanges,
      } as PrimaryTrackedJoint;
    } else {
      return {
        ...baseJoint,
        role: 'secondary' as const,
        Range: joint.Range as DifficultyRanges, // Capital R!
      } as SecondaryTrackedJoint;
    }
  });
}

/**
 * Build PositionCheck from database record
 */
function buildPositionCheck(dbCheck: DbPositionCheck): PositionCheck {
  return {
    id: dbCheck.checkId,
    type: dbCheck.type as PositionCheck['type'],
    landmarks: dbCheck.landmarks as PositionCheck['landmarks'],
    condition: dbCheck.condition as PositionCheck['condition'],
    activePhases: dbCheck.activePhases as PhaseName[],
    errorMessage: toLocalizedText(dbCheck.errorMessage),
    severity: dbCheck.severity as PositionCheck['severity'],
    cooldownMs: dbCheck.cooldownMs,
    minErrorFrames: dbCheck.minErrorFrames,
  };
}

/**
 * Group feedback messages by type
 */
function buildFeedbackMessages(dbMessages: DbFeedbackMessage[]): FeedbackMessages {
  const result: FeedbackMessages = {
    motivational: [],
    common_mistake: [],
    tip: [],
  };
  
  for (const msg of dbMessages) {
    const type = msg.type as keyof FeedbackMessages;
    if (type in result) {
      result[type].push(toLocalizedText(msg.message));
    }
  }
  
  return result;
}

/**
 * Build DifficultyLevelConfig array from database difficulty levels
 */
function buildDifficultyLevels(
  dbLevels: DbDifficultyLevel[],
  countingMethod: CountingMethod
): DifficultyLevelConfig[] {
  const phases = getPhasesForCountingMethod(countingMethod);
  
  // Sort by level order: beginner, normal, advanced
  const levelOrder = ['beginner', 'normal', 'advanced'];
  const sorted = [...dbLevels].sort((a, b) => {
    return levelOrder.indexOf(a.difficultyType.code) - 
           levelOrder.indexOf(b.difficultyType.code);
  });
  
  return sorted.map(dl => {
    const level = dl.difficultyType.code as DifficultyLevel;
    
    // Parse repCountingConfig, use phases from DB or default
    const repCountingConfig = parseRepCountingConfig(
      dl.repCountingConfig,
      countingMethod
    );
    
    // Use phases from DB if available, otherwise use template
    const dlPhases = Array.isArray(dl.phases) ? 
      (dl.phases as PhaseName[]) : phases;
    
    return {
      level,
      repCountingConfig,
      phases: dlPhases,
    };
  });
}

/**
 * Parse repCountingConfig from JSON
 */
function parseRepCountingConfig(
  config: unknown,
  countingMethod: CountingMethod
): RepCountingConfig {
  const parsed = config as Record<string, number> | null;
  
  if (countingMethod === 'hold') {
    return {
      duration: parsed?.duration ?? 30,
      ...(parsed?.gracePeriodMs && { gracePeriodMs: parsed.gracePeriodMs }),
    };
  } else {
    const result: RepCountingConfig = {
      reps: parsed?.reps ?? 10,
    };
    
    // Only include timing fields if they have values
    if (parsed?.minRepIntervalMs) {
      (result as { reps: number; minRepIntervalMs?: number }).minRepIntervalMs = 
        parsed.minRepIntervalMs;
    }
    if (parsed?.maxRepIntervalMs) {
      (result as { reps: number; maxRepIntervalMs?: number }).maxRepIntervalMs = 
        parsed.maxRepIntervalMs;
    }
    
    return result;
  }
}

// ============================================
// HELPER FUNCTIONS
// ============================================

/**
 * Convert database JSON to LocalizedText
 */
function toLocalizedText(json: Record<string, string> | null | undefined): LocalizedText {
  return {
    ar: json?.ar ?? '',
    en: json?.en ?? '',
  };
}

/**
 * Map internal camera code to Android schema code
 */
function mapCameraCodeToSchema(code: string): CameraPosition {
  const mapping: Record<string, CameraPosition> = {
    'side_left': 'side_view',
    'side_right': 'side_view',
    'front': 'front_view',
    'back': 'back_view',
  };
  
  return mapping[code] ?? 'side_view';
}

// ============================================
// VALIDATION & EXPORT
// ============================================

/**
 * Build and validate exercise config for export
 * Throws error if validation fails
 */
export function buildAndValidateExerciseConfig(dbExercise: DbExercise): ExerciseConfig {
  const config = buildExerciseConfig(dbExercise);
  const errors = validateExerciseConfig(config);
  
  if (errors.length > 0) {
    throw new Error(`Exercise validation failed:\n${errors.join('\n')}`);
  }
  
  return config;
}

/**
 * Build exercise config for export (without throwing on validation errors)
 * Returns config and validation errors
 */
export function buildExerciseConfigWithValidation(
  dbExercise: DbExercise
): { config: ExerciseConfig; errors: string[] } {
  const config = buildExerciseConfig(dbExercise);
  const errors = validateExerciseConfig(config);
  
  return { config, errors };
}

// ============================================
// PRISMA INCLUDE HELPER
// ============================================

/**
 * Prisma include object for fetching complete exercise data
 * Use this when querying exercises for JSON export
 */
export const exerciseFullInclude = {
  category: {
    select: {
      code: true,
      name: true,
    },
  },
  countingMethod: {
    select: {
      code: true,
    },
  },
  attributes: {
    include: {
      attributeValue: {
        include: {
          attribute: {
            select: {
              code: true,
            },
          },
        },
      },
    },
  },
  poseVariants: {
    include: {
      cameraPosition: {
        select: {
          code: true,
          schemaCode: true,
        },
      },
      positionChecks: {
        orderBy: {
          sortOrder: 'asc' as const,
        },
      },
      feedbackMessages: {
        orderBy: {
          sortOrder: 'asc' as const,
        },
      },
      difficultyLevels: {
        include: {
          difficultyType: {
            select: {
              code: true,
            },
          },
        },
        orderBy: {
          sortOrder: 'asc' as const,
        },
      },
    },
    orderBy: {
      sortOrder: 'asc' as const,
    },
  },
} as const;
