export type ProgramOwnership = 'SYSTEM' | 'COACH' | 'CUSTOM';
export type ProgramDomainEnum = 'TRAINING' | 'MOBILITY' | 'THERAPEUTIC';
export type AutoAssignmentStatus = 'ready' | 'incomplete' | 'manual_only';

export interface AutoAssignmentReadinessInput {
  programType?: string | null;
  programDomain?: string | null;
  trainingGoal?: string | null;
  autoAssignable?: boolean | null;
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
  entersAutoAssignment: boolean;
  missingFields: string[];
  status: AutoAssignmentStatus;
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

export function getAutoAssignmentReadiness(
  input: AutoAssignmentReadinessInput,
): AutoAssignmentReadiness {
  const missingFields: string[] = [];

  if (!input.programType) missingFields.push('programType');
  if (!input.programDomain) missingFields.push('programDomain');
  if (input.programDomain === 'TRAINING' && !input.trainingGoal) {
    missingFields.push('trainingGoal');
  }
  if (!hasNumber(input.levelRangeMin)) missingFields.push('levelRangeMin');
  if (!hasNumber(input.levelRangeMax)) missingFields.push('levelRangeMax');
  if (!hasStructuredValue(input.contraindications)) missingFields.push('contraindications');
  if (!hasStructuredValue(input.targetEquipment)) missingFields.push('targetEquipment');
  if (!hasNonBlankString(input.targetDomain)) missingFields.push('targetDomain');
  if (!hasStructuredValue(input.targetRegions)) missingFields.push('targetRegions');
  if (!hasNumber(input.prescriptionPriority)) missingFields.push('prescriptionPriority');

  const entersAutoAssignment =
    input.programType === 'SYSTEM' ||
    (input.programType === 'COACH' && input.autoAssignable === true);
  const ready = entersAutoAssignment && missingFields.length === 0;
  const status: AutoAssignmentStatus = !entersAutoAssignment
    ? 'manual_only'
    : ready
      ? 'ready'
      : 'incomplete';

  return {
    ready,
    entersAutoAssignment,
    missingFields,
    status,
  };
}
