/**
 * JSON Builder - DB to Android Contract Mapper
 * =============================================
 * 
 * This module is the SINGLE point of transformation from
 * database models to the Android JSON schema.
 * 
 * Updated: State-based system (no difficulty levels)
 * 
 * IMPORTANT: Any change to the Android contract should ONLY be reflected here.
 */

import type {
  ExerciseConfig,
  PoseVariantConfig,
  TrackedJoint,
  PrimaryTrackedJoint,
  SecondaryTrackedJoint,
  PositionCheck,
  FeedbackMessages,
  LocalizedText,
  CountingMethod,
  CameraPosition,
  FacingDirection,
  PhaseName,
  AngleRange,
  StateRanges,
  StateMessages,
  RepCountingConfig,
  ReportMetricsConfig,
  MetricCode,
} from '@/lib/types/android-schema';

import { validateExerciseConfig } from '@/lib/types/android-schema';

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
  media?: Array<{
    url: string;
    type: string;
    isPrimary: boolean;
  }>;
  repCountingConfig?: unknown;
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
  
  // Weight & Metrics configuration
  supportsWeight?: boolean | null;
  minWeight?: number | null;
  maxWeight?: number | null;
  defaultWeight?: number | null;
  reportMetrics?: unknown | null;
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
  
  // Build rep counting config
  const repCountingConfig = parseRepCountingConfig(dbExercise.repCountingConfig, countingMethod);
  
  // Build pose variants
  const poseVariants = dbExercise.poseVariants.map(pv => 
    buildPoseVariantConfig(pv)
  );
  
  // Auto-detect bilateral (has paired joints)
  const isBilateral = detectBilateral(poseVariants);
  
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
    repCountingConfig,
    poseVariants,
  };

  const primaryImage = dbExercise.media?.find(m => m.type === 'image' && m.isPrimary);
  if (primaryImage?.url) {
    config.imageUrl = primaryImage.url;
  }
  
  // Only include optional fields if they have values
  if (dbExercise.description) {
    config.description = toLocalizedText(dbExercise.description);
  }
  if (dbExercise.instructions) {
    config.instructions = toLocalizedText(dbExercise.instructions);
  }
  
  // ═══════════════════════════════════════════════════════════════
  // WEIGHT & METRICS CONFIGURATION
  // ═══════════════════════════════════════════════════════════════
  
  // Weight configuration
  if (dbExercise.supportsWeight) {
    config.supportsWeight = true;
    if (dbExercise.minWeight != null) {
      config.minWeight = dbExercise.minWeight;
    }
    if (dbExercise.maxWeight != null) {
      config.maxWeight = dbExercise.maxWeight;
    }
    if (dbExercise.defaultWeight != null) {
      config.defaultWeight = dbExercise.defaultWeight;
    }
  }
  
  // Check for position checks in any pose variant
  const hasPositionChecks = poseVariants.some(pv => 
    pv.positionChecks && pv.positionChecks.length > 0
  );
  
  // Report metrics configuration
  // Always merge user-excluded with auto-excluded (disabled) metrics
  const autoExcluded = getAutoExcludedMetricCodes({
    countingMethod,
    isBilateral,
    supportsWeight: !!dbExercise.supportsWeight,
    hasPositionChecks,
  });
  
  if (dbExercise.reportMetrics) {
    config.reportMetrics = parseReportMetrics(dbExercise.reportMetrics, autoExcluded);
  } else {
    // Generate default based on exercise type
    config.reportMetrics = generateDefaultMetrics(
      countingMethod, 
      isBilateral, 
      !!dbExercise.supportsWeight,
      hasPositionChecks
    );
  }
  
  // Include bilateral flag
  if (isBilateral) {
    config.isBilateral = true;
  }
  
  // Include position checks flag
  if (hasPositionChecks) {
    config.hasPositionChecks = true;
  }
  
  return config;
}

/**
 * Build PoseVariantConfig from database pose variant
 */
function buildPoseVariantConfig(dbVariant: DbPoseVariant): PoseVariantConfig {
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
  
  const config: PoseVariantConfig = {
    name: toLocalizedText(dbVariant.name),
    cameraPosition,
    trackedJoints,
  };
  
  // Only include optional fields if they have values
  if (expectedFacingDirection !== 'auto_detect') {
    config.expectedFacingDirection = expectedFacingDirection;
  }
  
  if (positionChecks.length > 0) {
    config.positionChecks = positionChecks;
  }
  
  if (feedbackMessages.motivational.length > 0 || feedbackMessages.tips.length > 0) {
    config.feedbackMessages = feedbackMessages;
  }
  
  return config;
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
    };
    
    // Add optional fields
    if (joint.stateMessages) {
      Object.assign(baseJoint, { stateMessages: joint.stateMessages as StateMessages });
    }
    if (joint.pairedWith) {
      Object.assign(baseJoint, { pairedWith: joint.pairedWith as string });
    }
    if (joint.invertIndicator) {
      Object.assign(baseJoint, { invertIndicator: joint.invertIndicator as boolean });
    }
    
    if (joint.role === 'primary') {
      return {
        ...baseJoint,
        role: 'primary' as const,
        upRange: joint.upRange as StateRanges,
        downRange: joint.downRange as StateRanges,
      } as PrimaryTrackedJoint;
    } else {
      return {
        ...baseJoint,
        role: 'secondary' as const,
        range: joint.range as StateRanges,
      } as SecondaryTrackedJoint;
    }
  });
}

/**
 * Build PositionCheck from database record
 */
function buildPositionCheck(dbCheck: DbPositionCheck): PositionCheck {
  const condition = dbCheck.condition as { operator: string; threshold?: number; thresholds?: Record<string, number> };
  
  // Support both old (thresholds) and new (threshold) format
  let threshold = condition.threshold;
  if (threshold === undefined && condition.thresholds) {
    // Use normal threshold as default for backwards compatibility
    threshold = condition.thresholds.normal ?? condition.thresholds.beginner ?? 0.05;
  }
  
  return {
    id: dbCheck.checkId,
    type: dbCheck.type as PositionCheck['type'],
    landmarks: dbCheck.landmarks as PositionCheck['landmarks'],
    condition: {
      operator: condition.operator as PositionCheck['condition']['operator'],
      threshold: threshold ?? 0.05,
    },
    activePhases: dbCheck.activePhases as PhaseName[],
    errorMessage: toLocalizedText(dbCheck.errorMessage),
    severity: dbCheck.severity as PositionCheck['severity'],
    cooldownMs: dbCheck.cooldownMs,
    minErrorFrames: dbCheck.minErrorFrames,
  };
}

/**
 * Group feedback messages by type (simplified - only motivational and tips)
 */
function buildFeedbackMessages(dbMessages: DbFeedbackMessage[]): FeedbackMessages {
  const result: FeedbackMessages = {
    motivational: [],
    tips: [],
  };
  
  for (const msg of dbMessages) {
    if (msg.type === 'motivational') {
      result.motivational.push(toLocalizedText(msg.message));
    } else if (msg.type === 'tip' || msg.type === 'tips') {
      result.tips.push(toLocalizedText(msg.message));
    }
    // Ignore common_mistake (deprecated)
  }
  
  return result;
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
      gracePeriodMs: parsed?.gracePeriodMs ?? 2500,
    };
  }
  
  return {
    reps: parsed?.reps ?? 12,
    minRepIntervalMs: parsed?.minRepIntervalMs ?? 1500,
    maxRepIntervalMs: parsed?.maxRepIntervalMs ?? 5000,
  };
}

// ============================================
// HELPER FUNCTIONS
// ============================================

/**
 * Detect if exercise is bilateral (has paired joints)
 */
function detectBilateral(poseVariants: PoseVariantConfig[]): boolean {
  for (const variant of poseVariants) {
    for (const joint of variant.trackedJoints) {
      if ('pairedWith' in joint && joint.pairedWith) {
        return true;
      }
    }
  }
  return false;
}

/**
 * Convert metric code from DB format (lowercase) to Android format (uppercase)
 */
function toAndroidMetricCode(code: string): MetricCode {
  return code.toUpperCase() as MetricCode;
}

/**
 * Get auto-excluded (disabled) metrics based on exercise type.
 * Returns uppercase metric codes for Android compatibility.
 * 
 * These are metrics that are NOT APPLICABLE for this exercise type.
 */
function getAutoExcludedMetricCodes(options: {
  countingMethod: CountingMethod;
  isBilateral: boolean;
  supportsWeight: boolean;
  hasPositionChecks: boolean;
}): MetricCode[] {
  const { countingMethod, isBilateral, supportsWeight, hasPositionChecks } = options;
  const isHold = countingMethod === 'hold';
  
  const excluded: MetricCode[] = [];
  
  // Hold exercise restrictions
  if (isHold) {
    excluded.push('REP_COUNT', 'TEMPO', 'TUT', 'ROM', 'FORM_CONSISTENCY', 'FATIGUE_INDEX', 'VELOCITY');
  } else {
    // Rep-based exercise restrictions
    excluded.push('HOLD_DURATION');
  }
  
  // Weight restrictions
  if (!supportsWeight) {
    excluded.push('WEIGHT', 'VOLUME', 'EST_1RM');
  }
  
  // Bilateral restrictions
  if (!isBilateral) {
    excluded.push('SYMMETRY');
  }
  
  // Position Checks restrictions (Alignment)
  if (!hasPositionChecks) {
    excluded.push('ALIGNMENT');
  }
  
  return excluded;
}

/**
 * Parse report metrics from DB JSON
 * Converts lowercase metric codes to uppercase for Android compatibility
 * Merges user-excluded with auto-excluded (disabled) metrics
 */
function parseReportMetrics(config: unknown, autoExcluded: MetricCode[] = []): ReportMetricsConfig {
  const parsed = config as Record<string, unknown> | null;
  if (!parsed) {
    return { 
      primary: ['FORM_SCORE'],
      excluded: autoExcluded.length > 0 ? autoExcluded : undefined,
    };
  }
  
  // Convert all metric codes to uppercase
  const primaryCodes = (parsed.primary as string[]) || ['form_score'];
  const optionalCodes = (parsed.optional as string[]) || [];
  const userExcludedCodes = (parsed.excluded as string[]) || [];
  
  // Merge user-excluded with auto-excluded (disabled) metrics
  const userExcludedUpper = userExcludedCodes.map(toAndroidMetricCode);
  const allExcluded = [...new Set([...userExcludedUpper, ...autoExcluded])];
  
  return {
    primary: primaryCodes.map(toAndroidMetricCode),
    optional: optionalCodes.length > 0 ? optionalCodes.map(toAndroidMetricCode) : undefined,
    excluded: allExcluded.length > 0 ? allExcluded : undefined,
  };
}

/**
 * Generate default report metrics based on exercise type
 * Includes auto-excluded metrics for mobile compatibility
 */
function generateDefaultMetrics(
  countingMethod: CountingMethod,
  isBilateral: boolean,
  supportsWeight: boolean,
  hasPositionChecks: boolean
): ReportMetricsConfig {
  const isHold = countingMethod === 'hold';
  const primary: MetricCode[] = ['FORM_SCORE'];
  const optional: MetricCode[] = [];
  
  if (isHold) {
    primary.push('HOLD_DURATION');
    optional.push('STABILITY');
  } else {
    primary.push('ROM');
    optional.push('TEMPO', 'TUT');
  }
  
  if (isBilateral && !isHold) {
    primary.push('SYMMETRY');
  }
  
  if (supportsWeight) {
    primary.push('WEIGHT');
    optional.push('VOLUME', 'EST_1RM');
  }
  
  // Only add ALIGNMENT if exercise has position checks
  if (hasPositionChecks) {
    optional.push('ALIGNMENT');
  }
  
  // Get auto-excluded (disabled) metrics
  const excluded = getAutoExcludedMetricCodes({
    countingMethod,
    isBilateral,
    supportsWeight,
    hasPositionChecks,
  });
  
  return {
    primary: primary.slice(0, 3), // Max 3 primary
    optional,
    excluded: excluded.length > 0 ? excluded : undefined,
  };
}

/**
 * Convert DB JSON to LocalizedText
 * Preserves audio URLs if present
 */
function toLocalizedText(json: Record<string, string> | null | undefined): LocalizedText {
  const result: LocalizedText = {
    ar: json?.ar ?? '',
    en: json?.en ?? '',
  };
  
  // Include audio URLs if present
  if (json?.audioAr) {
    result.audioAr = json.audioAr;
  }
  if (json?.audioEn) {
    result.audioEn = json.audioEn;
  }
  
  return result;
}

/**
 * Map internal camera code to Android schema code
 */
function mapCameraCodeToSchema(code: string): CameraPosition {
  const mapping: Record<string, CameraPosition> = {
    'side_left': 'side_view',
    'side_right': 'side_view',
    'side_view': 'side_view',
    'front': 'front_view',
    'front_view': 'front_view',
    'back': 'back_view',
    'back_view': 'back_view',
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
  media: {
    where: {
      isPrimary: true,
      type: 'image',
    },
    select: {
      url: true,
      type: true,
      isPrimary: true,
    },
    take: 1,
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
    },
    orderBy: {
      sortOrder: 'asc' as const,
    },
  },
} as const;

/**
 * Fields to select for weight & metrics configuration
 * These are direct fields on the Exercise model
 */
export const exerciseConfigFields = {
  supportsWeight: true,
  minWeight: true,
  maxWeight: true,
  defaultWeight: true,
  reportMetrics: true,
} as const;
