/**
 * Assessment Types - Body Scan
 * ============================
 *
 * Type definitions for the Body Scan assessment system.
 */

// ============================================
// ENUMS / UNION TYPES
// ============================================

export type AssessmentType = 'initial' | 'periodic' | 'post_program' | 'progression' | 'level_specific';

export type FitnessLevel = 'excellent' | 'good' | 'average' | 'limited' | 'needs_rehab';

export type ConfidenceLevel = 'high' | 'medium' | 'low';

export type RegionStatus = 'excellent' | 'good' | 'average' | 'limited' | 'weak';

// ============================================
// REGIONAL ASSESSMENT
// ============================================

export interface AssessmentRegion {
  region: string;        // shoulder, hip, knee, ankle, spine, core, balance
  side: 'left' | 'right' | 'center';
  absoluteRom: number;   // Degrees
  errorBand: number;     // ± degrees
  referenceNorm: number; // Reference norm degrees
  romPercentage: number; // ROM as % of norm (with range)
  romPercentageMin: number;
  romPercentageMax: number;
  movementQualityGrade: 1 | 2 | 3;  // 1=fail, 2=compensation, 3=clean
  stabilityScore: number;  // 0-100
  regionalScore: number;   // 0-100
  confidence: ConfidenceLevel;
  status: RegionStatus;
}

// ============================================
// HYPOTHESIS CARDS
// ============================================

export interface HypothesisCard {
  observation: { ar: string; en: string };
  possibleCauses: Array<{
    cause: { ar: string; en: string };
    status: 'confirmed' | 'possible' | 'ruled_out';
    evidence?: string;
  }>;
  recommendations: Array<{
    type: 'stretch' | 'strengthen' | 'mobilize';
    priority: 'high' | 'medium' | 'low';
    description: { ar: string; en: string };
  }>;
  confidence: ConfidenceLevel;
}

// ============================================
// SAFETY GATES
// ============================================

export interface SafetyGate {
  region: string;
  reason: { ar: string; en: string };
  blockedExerciseTypes: string[];
  allowedAlternatives: string[];
  resolveCondition: string;  // e.g., "region score > 50%"
}

// ============================================
// DOMAIN SCORES
// ============================================

export interface DomainScores {
  mobility: number;   // 0-100
  control: number;    // 0-100
  symmetry: number | null; // 0-100 or null
  safety: number;     // 0-100
}

// ============================================
// CREATE INPUT
// ============================================

export interface BodyScanResultCreate {
  userId: string;
  type: AssessmentType;
  bodyScore: number;
  mobilityScore: number;
  controlScore: number;
  symmetryScore?: number;
  safetyScore: number;
  /** Preferred Level reference. */
  levelId?: string | null;
  /** Preferred level number — resolved to levelId when levelId is absent. */
  levelNumber?: number;
  /** @deprecated Legacy client value; ignored when levelId or levelNumber is provided. */
  fitnessLevel?: FitnessLevel;
  regions: AssessmentRegion[];
  symmetryData?: Record<string, number>;
  hypotheses?: HypothesisCard[];
  recommendations?: Array<{ priority: number; type: string; exercises: string[] }>;
  rawReportIds?: string[];
  previousId?: string;
  durationMs?: number;
  movementCount: number;
  /** Optional — links result to the assessment template used on the client. */
  templateId?: string | null;
}

// ============================================
// PROGRESS COMPARISON
// ============================================

export interface BodyScanProgress {
  current: {
    bodyScore: number;
    domainScores: DomainScores;
    completedAt: string;
  };
  previous?: {
    bodyScore: number;
    domainScores: DomainScores;
    completedAt: string;
  };
  changes?: {
    bodyScoreDelta: number;
    mobilityDelta: number;
    controlDelta: number;
    symmetryDelta: number | null;
    safetyDelta: number;
    isRealImprovement: boolean; // > MDC threshold
  };
}
