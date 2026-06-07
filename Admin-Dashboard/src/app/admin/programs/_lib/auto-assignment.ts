/**
 * Client-side approximation of auto-assignment readiness for the admin form.
 *
 * Source of truth: backend `program-assignment.ts` — mirror rule changes there.
 */
export type ProgramOwnership = 'SYSTEM' | 'COACH' | 'CUSTOM';
export type ProgramDomainEnum = 'TRAINING' | 'MOBILITY' | 'THERAPEUTIC';
export type AutoAssignmentStatus = 'ready' | 'incomplete' | 'manual_only';

export type ProgramAttributeRowInput = {
  mode: string;
  attributeValue?: {
    code: string;
    attribute?: { code: string | null } | null;
  } | null;
};

export interface AutoAssignmentReadinessInput {
  programType?: string | null;
  autoAssignable?: boolean | null;
  levelRangeMin?: number | null;
  levelRangeMax?: number | null;
  programAttributes?: ProgramAttributeRowInput[];
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

function programAttrRows(input: AutoAssignmentReadinessInput): ProgramAttributeRowInput[] {
  const pa = input.programAttributes;
  return Array.isArray(pa) ? pa : [];
}

function hasDomainRequired(input: AutoAssignmentReadinessInput): boolean {
  return programAttrRows(input).some(
    (r) => r.mode === 'REQUIRED' && r.attributeValue?.attribute?.code === 'domain',
  );
}

function hasGoalRequired(input: AutoAssignmentReadinessInput): boolean {
  return programAttrRows(input).some(
    (r) => r.mode === 'REQUIRED' && r.attributeValue?.attribute?.code === 'goal',
  );
}

function getEffectiveProgramDomain(input: AutoAssignmentReadinessInput): ProgramDomainEnum | null {
  for (const r of programAttrRows(input)) {
    if (r.mode === 'EXCLUDED') continue;
    const c = r.attributeValue?.code;
    if (c === 'pd_training') return 'TRAINING';
    if (c === 'pd_mobility') return 'MOBILITY';
    if (c === 'pd_therapeutic') return 'THERAPEUTIC';
  }
  return null;
}

export function getAutoAssignmentReadiness(input: AutoAssignmentReadinessInput): AutoAssignmentReadiness {
  const missingFields: string[] = [];

  if (!input.programType) missingFields.push('programType');
  if (!hasNumber(input.levelRangeMin)) missingFields.push('levelRangeMin');
  if (!hasNumber(input.levelRangeMax)) missingFields.push('levelRangeMax');

  const rows = programAttrRows(input);
  if (rows.length === 0) {
    missingFields.push('programAttributes');
  } else {
    if (!hasDomainRequired(input)) missingFields.push('domainAttribute');
    const eff = getEffectiveProgramDomain(input);
    if (eff === 'TRAINING' && !hasGoalRequired(input)) {
      missingFields.push('goalAttribute');
    }
  }

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
