import {
  buildAssignmentReason,
  getAutoAssignmentMissingFields,
  getAutoAssignmentReadiness,
  isProgramEligibleForAutoAssignment,
} from './program-assignment';

describe('program-assignment', () => {
  it('marks a complete SYSTEM training program as ready', () => {
    const readiness = getAutoAssignmentReadiness({
      isPublished: true,
      programType: 'SYSTEM',
      programDomain: 'TRAINING',
      trainingGoal: 'STRENGTH',
      levelRangeMin: 1,
      levelRangeMax: 3,
      contraindications: [],
      targetEquipment: ['bodyweight'],
      targetDomain: 'strength',
      targetRegions: [],
      prescriptionPriority: 50,
    });

    expect(readiness.ready).toBe(true);
    expect(readiness.missingFields).toEqual([]);
  });

  it('requires trainingGoal for TRAINING auto-assignment programs', () => {
    const missing = getAutoAssignmentMissingFields({
      isPublished: true,
      programType: 'SYSTEM',
      programDomain: 'TRAINING',
      levelRangeMin: 1,
      levelRangeMax: 3,
      contraindications: [],
      targetEquipment: ['bodyweight'],
      targetDomain: 'strength',
      targetRegions: [],
      prescriptionPriority: 50,
    });

    expect(missing).toContain('trainingGoal');
  });

  it('allows manual-only programs to stay ineligible', () => {
    const eligible = isProgramEligibleForAutoAssignment({
      isPublished: true,
      programType: 'CUSTOM',
      programDomain: 'TRAINING',
      trainingGoal: 'GENERAL_HEALTH',
      levelRangeMin: 1,
      levelRangeMax: 2,
      contraindications: [],
      targetEquipment: [],
      targetDomain: 'strength',
      targetRegions: [],
      prescriptionPriority: 50,
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
