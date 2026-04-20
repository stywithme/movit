export type AssignmentSource =
  | 'selection_algorithm'
  | 'fallback_selection'
  | 'manual_selection'
  | 'admin_assignment';

type ProgramTypeValue = 'SYSTEM' | 'COACH' | 'CUSTOM';
type ProgramDomainValue = 'TRAINING' | 'MOBILITY' | 'THERAPEUTIC';

export interface ProgramAssignmentReason {
  source: AssignmentSource;
  matchedFactors: string[];
  limitingFactor: string | null;
}

export interface AutoAssignmentProgramShape {
  isPublished?: boolean | null;
  programType?: ProgramTypeValue | null;
  autoAssignable?: boolean | null;
  programDomain?: ProgramDomainValue | null;
  trainingGoal?: string | null;
  levelRangeMin?: number | null;
  levelRangeMax?: number | null;
  contraindications?: unknown;
  targetEquipment?: unknown;
  targetDomain?: string | null;
  targetRegions?: unknown;
  prescriptionPriority?: number | null;
}

export interface AutoAssignmentReadiness {
  ready: boolean;
  missingFields: string[];
}

function hasNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function hasStructuredValue(value: unknown): boolean {
  return value !== null && value !== undefined;
}

function hasNonBlankString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

export function getAutoAssignmentMissingFields(
  program: AutoAssignmentProgramShape,
): string[] {
  const missing: string[] = [];

  if (!program.programType) missing.push('programType');
  if (!program.programDomain) missing.push('programDomain');
  if (program.programDomain === 'TRAINING' && !program.trainingGoal) {
    missing.push('trainingGoal');
  }
  if (!hasNumber(program.levelRangeMin)) missing.push('levelRangeMin');
  if (!hasNumber(program.levelRangeMax)) missing.push('levelRangeMax');
  if (!hasStructuredValue(program.contraindications)) {
    missing.push('contraindications');
  }
  if (!hasStructuredValue(program.targetEquipment)) {
    missing.push('targetEquipment');
  }
  if (!hasNonBlankString(program.targetDomain)) {
    missing.push('targetDomain');
  }
  if (!hasStructuredValue(program.targetRegions)) {
    missing.push('targetRegions');
  }
  if (!hasNumber(program.prescriptionPriority)) {
    missing.push('prescriptionPriority');
  }

  return missing;
}

export function getAutoAssignmentReadiness(
  program: AutoAssignmentProgramShape,
): AutoAssignmentReadiness {
  const missingFields = getAutoAssignmentMissingFields(program);
  return {
    ready: missingFields.length === 0,
    missingFields,
  };
}

export function isProgramEligibleForAutoAssignment(
  program: AutoAssignmentProgramShape,
): boolean {
  if (!program.isPublished) return false;

  const typeEligible =
    program.programType === 'SYSTEM' ||
    (program.programType === 'COACH' && program.autoAssignable === true);

  if (!typeEligible) return false;
  return getAutoAssignmentReadiness(program).ready;
}

export function buildAssignmentReason(
  source: AssignmentSource,
  matchedFactors: string[],
  limitingFactor: string | null,
): ProgramAssignmentReason {
  return {
    source,
    matchedFactors: [...new Set(matchedFactors.filter(Boolean))],
    limitingFactor: limitingFactor ?? null,
  };
}
