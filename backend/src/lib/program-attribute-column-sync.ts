import type { ProgramAttributeMode, ProgramDomain, TrainingGoal } from '@prisma/client';

type AttrRow = {
  mode: ProgramAttributeMode;
  attributeValue: { code: string; attribute: { code: string } };
};

const DOMAIN_CODE_TO_ENUM: Record<string, ProgramDomain> = {
  pd_training: 'TRAINING',
  pd_mobility: 'MOBILITY',
  pd_therapeutic: 'THERAPEUTIC',
};

const GOAL_CODE_TO_ENUM: Record<string, TrainingGoal> = {
  pg_strength: 'STRENGTH',
  pg_hypertrophy: 'HYPERTROPHY',
  pg_power: 'POWER',
  pg_general_health: 'GENERAL_HEALTH',
};

const FOCUS_CODE_TO_TARGET_DOMAIN: Record<string, string> = {
  pf_symmetry: 'symmetry',
  pf_mobility: 'mobility',
  pf_strength: 'strength',
  pf_control: 'control',
  pf_upper_body: 'upper_body',
  pf_lower_body: 'lower_body',
  pf_full_body: 'full_body',
};

const BODY_REGION_CODE_TO_LABEL: Record<string, string> = {
  pbr_shoulder: 'shoulder',
  pbr_hip: 'hip',
  pbr_spine: 'spine',
  pbr_knee: 'knee',
  pbr_core: 'core',
  pbr_balance: 'balance',
};

/**
 * Derive persisted Program scalar columns from ProgramAttribute rows
 * (keeps DB columns aligned for exports / older clients).
 */
export function matchingColumnsFromProgramAttributeRows(rows: AttrRow[]): {
  programDomain: ProgramDomain;
  trainingGoal: TrainingGoal | null;
  targetEquipment: string[];
  targetDomain: string | null;
  targetRegions: string[];
  contraindications: string[];
} {
  let programDomain: ProgramDomain = 'TRAINING';
  let trainingGoal: TrainingGoal | null = null;
  const targetEquipment: string[] = [];
  let targetDomain: string | null = null;
  const targetRegions: string[] = [];
  const contraindications: string[] = [];

  for (const r of rows) {
    const { code } = r.attributeValue;
    const attrCode = r.attributeValue.attribute.code;

    if (attrCode === 'domain' && r.mode !== 'EXCLUDED') {
      const d = DOMAIN_CODE_TO_ENUM[code];
      if (d) programDomain = d;
    }
    if (attrCode === 'goal' && r.mode === 'REQUIRED') {
      const g = GOAL_CODE_TO_ENUM[code];
      if (g) trainingGoal = g;
    }
    if (attrCode === 'equipment' && r.mode === 'REQUIRED') {
      if (!targetEquipment.includes(code)) targetEquipment.push(code);
    }
    if (attrCode === 'focus' && r.mode === 'REQUIRED') {
      const td = FOCUS_CODE_TO_TARGET_DOMAIN[code];
      if (td) targetDomain = td;
    }
    if (attrCode === 'body_region') {
      const reg = BODY_REGION_CODE_TO_LABEL[code];
      if (!reg) continue;
      if (r.mode === 'REQUIRED' || r.mode === 'OPTIONAL') {
        if (!targetRegions.includes(reg)) targetRegions.push(reg);
      }
      if (r.mode === 'EXCLUDED' && !contraindications.includes(reg)) {
        contraindications.push(reg);
      }
    }
  }

  return {
    programDomain,
    trainingGoal,
    targetEquipment,
    targetDomain,
    targetRegions,
    contraindications,
  };
}
