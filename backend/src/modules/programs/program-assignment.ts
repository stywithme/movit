export type AssignmentSource =
  | 'selection_algorithm'
  | 'fallback_selection'
  | 'manual_selection'
  | 'admin_assignment';

type ProgramTypeValue = 'SYSTEM' | 'COACH' | 'CUSTOM';
type ProgramDomainValue = 'TRAINING' | 'MOBILITY' | 'THERAPEUTIC';

export type ProgramAttributeRowForReadiness = {
  mode: string;
  attributeValue?: {
    code: string;
    attribute?: { code: string | null } | null;
  } | null;
};

export interface ProgramAssignmentReason {
  source: AssignmentSource;
  matchedFactors: string[];
  limitingFactor: string | null;
}

export interface AutoAssignmentProgramShape {
  isPublished?: boolean | null;
  programType?: ProgramTypeValue | null;
  autoAssignable?: boolean | null;
  levelRangeMin?: number | null;
  levelRangeMax?: number | null;
  prescriptionPriority?: number | null;
  programAttributes?: ProgramAttributeRowForReadiness[];
}

export interface AutoAssignmentReadiness {
  ready: boolean;
  missingFields: string[];
}

function hasNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function programAttrRows(program: AutoAssignmentProgramShape): ProgramAttributeRowForReadiness[] {
  const pa = program.programAttributes;
  return Array.isArray(pa) ? pa : [];
}

function hasDomainRequired(program: AutoAssignmentProgramShape): boolean {
  return programAttrRows(program).some(
    (r) => r.mode === 'REQUIRED' && r.attributeValue?.attribute?.code === 'domain',
  );
}

function hasGoalRequired(program: AutoAssignmentProgramShape): boolean {
  return programAttrRows(program).some(
    (r) => r.mode === 'REQUIRED' && r.attributeValue?.attribute?.code === 'goal',
  );
}

/** Effective domain from `domain` attribute rows (non-EXCLUDED). */
export function getEffectiveProgramDomain(program: AutoAssignmentProgramShape): ProgramDomainValue | null {
  for (const r of programAttrRows(program)) {
    if (r.mode === 'EXCLUDED') continue;
    const c = r.attributeValue?.code;
    if (c === 'pd_training') return 'TRAINING';
    if (c === 'pd_mobility') return 'MOBILITY';
    if (c === 'pd_therapeutic') return 'THERAPEUTIC';
  }
  return null;
}

export function getAutoAssignmentMissingFields(program: AutoAssignmentProgramShape): string[] {
  const missing: string[] = [];

  if (!program.programType) missing.push('programType');
  if (!hasNumber(program.levelRangeMin)) missing.push('levelRangeMin');
  if (!hasNumber(program.levelRangeMax)) missing.push('levelRangeMax');
  if (!hasNumber(program.prescriptionPriority)) missing.push('prescriptionPriority');

  const rows = programAttrRows(program);
  if (rows.length === 0) {
    missing.push('programAttributes');
    return missing;
  }

  if (!hasDomainRequired(program)) missing.push('domainAttribute');
  const eff = getEffectiveProgramDomain(program);
  if (eff === 'TRAINING' && !hasGoalRequired(program)) {
    missing.push('goalAttribute');
  }

  return missing;
}

export function getAutoAssignmentReadiness(program: AutoAssignmentProgramShape): AutoAssignmentReadiness {
  const missingFields = getAutoAssignmentMissingFields(program);
  return {
    ready: missingFields.length === 0,
    missingFields,
  };
}

export function isProgramEligibleForAutoAssignment(program: AutoAssignmentProgramShape): boolean {
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
