import {
  buildAssignmentReason,
  getAutoAssignmentMissingFields,
  getAutoAssignmentReadiness,
  isProgramEligibleForAutoAssignment,
} from './program-assignment';

describe('program-assignment', () => {
  const domainTraining = {
    mode: 'REQUIRED',
    attributeValue: { code: 'pd_training', attribute: { code: 'domain' } },
  };
  const goalStrength = {
    mode: 'REQUIRED',
    attributeValue: { code: 'pg_strength', attribute: { code: 'goal' } },
  };

  it('requires programAttributes for auto-assignment readiness', () => {
    const missing = getAutoAssignmentMissingFields({
      programType: 'SYSTEM',
      levelRangeMin: 1,
      levelRangeMax: 3,
      prescriptionPriority: 50,
      programAttributes: [],
    });
    expect(missing).toContain('programAttributes');
  });

  it('marks attribute-backed SYSTEM program as ready when domain + goal + levels set', () => {
    const readiness = getAutoAssignmentReadiness({
      isPublished: true,
      programType: 'SYSTEM',
      levelRangeMin: 1,
      levelRangeMax: 3,
      prescriptionPriority: 50,
      programAttributes: [domainTraining, goalStrength],
    });
    expect(readiness.ready).toBe(true);
    expect(readiness.missingFields).toEqual([]);
  });

  it('requires domain in REQUIRED mode', () => {
    const missing = getAutoAssignmentMissingFields({
      programType: 'SYSTEM',
      levelRangeMin: 1,
      levelRangeMax: 3,
      prescriptionPriority: 50,
      programAttributes: [
        { mode: 'OPTIONAL', attributeValue: { code: 'pd_training', attribute: { code: 'domain' } } },
        goalStrength,
      ],
    });
    expect(missing).toContain('domainAttribute');
  });

  it('requires goal REQUIRED for TRAINING domain', () => {
    const missing = getAutoAssignmentMissingFields({
      programType: 'SYSTEM',
      levelRangeMin: 1,
      levelRangeMax: 3,
      prescriptionPriority: 50,
      programAttributes: [
        domainTraining,
        { mode: 'OPTIONAL', attributeValue: { code: 'pg_strength', attribute: { code: 'goal' } } },
      ],
    });
    expect(missing).toContain('goalAttribute');
  });

  it('allows manual-only programs to stay ineligible', () => {
    const eligible = isProgramEligibleForAutoAssignment({
      isPublished: true,
      programType: 'CUSTOM',
      levelRangeMin: 1,
      levelRangeMax: 2,
      prescriptionPriority: 50,
      programAttributes: [domainTraining, goalStrength],
    });
    expect(eligible).toBe(false);
  });

  it('normalizes duplicate matched factors in assignment reason', () => {
    const reason = buildAssignmentReason(
      'selection_algorithm',
      ['levelRange', 'equipment', 'levelRange'],
      'mobility',
    );
    expect(reason).toEqual({
      source: 'selection_algorithm',
      matchedFactors: ['levelRange', 'equipment'],
      limitingFactor: 'mobility',
    });
  });
});
