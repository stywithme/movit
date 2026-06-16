/**
 * Converts exercise spreadsheet CSV rows (Exercises, Joint ROM, Checks)
 * into Android-compatible exercise JSON (same shape as exercise-group-3).
 */
import type {
  AngleRange,
  CountingMethod,
  ExerciseConfig,
  MetricCode,
  PhaseName,
  PosePosition,
  PositionCheck,
  Severity,
  StateRanges,
  TrackedJoint,
} from '../../src/lib/types/android-schema';

export type CsvRow = Record<string, string>;

const PRIMARY_UP_MESSAGES = {
  perfect: {
    up: {
      ar: 'ممتاز! وصلت لنهاية الحركة بتحكم',
      en: 'Perfect! You reached the end range with control',
    },
    down: {
      ar: 'ممتاز! رجوع متحكم به للوضع الأساسي',
      en: 'Perfect! Controlled return to the start position',
    },
  },
  warning: {
    up: {
      ar: 'حافظ على المدى الكامل بدون تعويض',
      en: 'Keep the full range without compensating',
    },
  },
};

const SECONDARY_MESSAGES = {
  perfect: {
    ar: 'المفصل ثابت وفي مدى مناسب',
    en: 'The joint stays stable in a good range',
  },
  warning: {
    ar: 'راجع وضع هذا المفصل أثناء الحركة',
    en: 'Check this joint position during the movement',
  },
};

const HOLD_PRIMARY_MESSAGES = {
  perfect: {
    ar: 'ثبات ممتاز في الوضع المطلوب',
    en: 'Excellent hold in the target position',
  },
  warning: {
    ar: 'حافظ على الوضع ولا تدع الجسم ينهار',
    en: 'Keep the position and do not let the body collapse',
  },
};

/** Slugs already seeded in the canonical exercises-from-db folder. */
export const CANONICAL_EXISTING_SLUGS = new Set([
  'ex011_barbell_hip_thrust',
  'ex014_standing_calf_raise',
  'ex015_push_up',
  'ex016_dumbbell_bench_press',
  'ex017_dumbbell_shoulder_press',
  'ex018_arnold_press',
]);

/** Exercise IDs that exist in canonical DB even when CSV slug spelling differs. */
export const CANONICAL_EXISTING_IDS = new Set(['EX011', 'EX014', 'EX015', 'EX016', 'EX017', 'EX018']);

function slugifyName(name: string): string {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .replace(/_+/g, '_');
}

export function buildSlug(exerciseId: string, exerciseName: string): string {
  const id = exerciseId.trim().toLowerCase();
  const namePart = slugifyName(exerciseName);
  return `${id}_${namePart}`;
}

function parseRange(raw: string | undefined): AngleRange | null {
  if (!raw || raw.trim() === '' || raw.trim() === '_') return null;
  const cleaned = raw
    .trim()
    .replace(/[()]/g, '')
    .replace(/\s+/g, '');

  const negativeMatch = cleaned.match(/^(-?\d+)\s*-\s*\(?(-?\d+)\)?$/);
  if (negativeMatch) {
    const a = Number(negativeMatch[1]);
    const b = Number(negativeMatch[2]);
    if (a < 0 || b < 0) {
      // Hip extension values in spreadsheet are negative; map to a small positive ROM window.
      const magnitude = Math.max(Math.abs(a), Math.abs(b));
      return { min: 0, max: Math.min(50, magnitude + 10) };
    }
    return { min: Math.min(a, b), max: Math.max(a, b) };
  }

  const single = Number(cleaned.replace(/[^\d.-]/g, ''));
  if (!Number.isNaN(single) && !cleaned.includes('-')) {
    if (single <= 20) return { min: 0, max: Math.max(10, single + 10) };
    return { min: Math.max(0, single - 5), max: Math.min(180, single + 5) };
  }

  const parts = cleaned.split('-').map((p) => Number(p.replace(/[^\d.-]/g, '')));
  if (parts.length !== 2 || parts.some((n) => Number.isNaN(n))) return null;
  return { min: Math.min(parts[0], parts[1]), max: Math.max(parts[0], parts[1]) };
}

type AssessmentTier = { label: string; range: AngleRange };

function parseAssessmentTiers(raw: string | undefined): AssessmentTier[] {
  if (!raw || raw.trim() === '' || raw.toLowerCase() === 'perfect') return [];
  const tiers: AssessmentTier[] = [];
  const regex = /\(?\s*(-?\d+(?:\.\d+)?)\s*-\s*(-?\d+(?:\.\d+)?)\s*([^)]*)\)?/gi;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(raw)) !== null) {
    const min = Number(match[1]);
    const max = Number(match[2]);
    const label = (match[3] || '').trim().toLowerCase();
    if (!Number.isNaN(min) && !Number.isNaN(max)) {
      tiers.push({ label, range: { min: Math.min(min, max), max: Math.max(min, max) } });
    }
  }
  return tiers;
}

function expandRange(base: AngleRange, padding = 8): StateRanges {
  return {
    perfect: { ...base },
    normal: { min: Math.max(0, base.min - padding), max: base.max },
    pad: { min: Math.max(0, base.min - padding * 2), max: base.max },
  };
}

function buildStateRanges(base: AngleRange, assessmentRaw?: string): StateRanges {
  const tiers = parseAssessmentTiers(assessmentRaw);
  const result: StateRanges = { perfect: { ...base } };

  for (const tier of tiers) {
    const label = tier.label;
    if (label.includes('good')) {
      result.normal = { ...tier.range };
    } else if (label.includes('acceptable')) {
      result.pad = { ...tier.range };
    } else if (label.includes('warning') || label.includes('error')) {
      result.warning = { ...tier.range };
    }
  }

  if (!result.normal) {
    result.normal = { min: Math.max(0, base.min - 8), max: base.max };
  }
  if (!result.pad) {
    result.pad = { min: Math.max(0, (result.normal?.min ?? base.min) - 7), max: base.max };
  }

  return result;
}

function ensureTransitionGap(upRange: StateRanges, downRange: StateRanges): void {
  const upMin = upRange.pad?.min ?? upRange.normal?.min ?? upRange.perfect.min;
  const downMax = downRange.pad?.max ?? downRange.normal?.max ?? downRange.perfect.max;
  if (upMin <= downMax) {
    const targetUpMin = downMax + 5;
    upRange.perfect.min = Math.max(upRange.perfect.min, targetUpMin);
    if (upRange.normal) upRange.normal.min = Math.max(upRange.normal.min, targetUpMin);
    if (upRange.pad) upRange.pad.min = Math.max(upRange.pad.min, targetUpMin);

    const targetDownMax = Math.max(downRange.perfect.min, upRange.perfect.min - 5);
    if (targetDownMax > downRange.perfect.min) {
      downRange.perfect.max = Math.min(downRange.perfect.max, targetDownMax);
      if (downRange.normal) downRange.normal.max = Math.min(downRange.normal.max, targetDownMax);
      if (downRange.pad) downRange.pad.max = Math.min(downRange.pad.max, targetDownMax);
    }
  }
}

function normalizeJointSide(raw: string | undefined): 'bilateral' | 'left' | 'right' {
  const value = (raw || '').trim().toLowerCase();
  if (value.startsWith('uni')) return value.includes('rt') || value.includes('right') ? 'right' : 'left';
  if (value.includes('rt') || value.includes('right')) return 'right';
  if (value.includes('lt') || value.includes('left')) return 'left';
  return 'bilateral';
}

function resolveJointCode(rawJoint: string, side: 'bilateral' | 'left' | 'right'): string {
  const joint = rawJoint.trim().toLowerCase().replace(/\s+/g, '_');

  const map: Record<string, { left: string; right: string; bilateral?: string }> = {
    both_shoulder: { left: 'left_shoulder', right: 'right_shoulder' },
    shoulder: { left: 'left_shoulder', right: 'right_shoulder' },
    'shoulder/shoulder_cross': { left: 'left_shoulder_cross', right: 'right_shoulder_cross' },
    shoulder_cross: { left: 'left_shoulder_cross', right: 'right_shoulder_cross' },
    both_elbow: { left: 'left_elbow', right: 'right_elbow' },
    elbow: { left: 'left_elbow', right: 'right_elbow' },
    both_hip: { left: 'left_hip', right: 'right_hip' },
    hip: { left: 'left_hip', right: 'right_hip' },
    rt_hip_cross: { left: 'left_hip_cross', right: 'right_hip_cross' },
    lt_hip_cross: { left: 'left_hip_cross', right: 'right_hip_cross' },
    both_knees: { left: 'left_knee', right: 'right_knee' },
    both_knee: { left: 'left_knee', right: 'right_knee' },
    knee: { left: 'left_knee', right: 'right_knee' },
    rt_knee: { left: 'left_knee', right: 'right_knee' },
    lt_knee: { left: 'left_knee', right: 'right_knee' },
    both_ankle: { left: 'left_ankle', right: 'right_ankle' },
    ankle: { left: 'left_ankle', right: 'right_ankle' },
    rt_ankle: { left: 'left_ankle', right: 'right_ankle' },
    rt__ankle: { left: 'left_ankle', right: 'right_ankle' },
    wrist: { left: 'left_wrist', right: 'right_wrist' },
    spine: { left: 'spine', right: 'spine', bilateral: 'spine' },
    rt_shoulder_cross: { left: 'left_shoulder_cross', right: 'right_shoulder_cross' },
    rt_hip: { left: 'left_hip', right: 'right_hip' },
    rt_shoulder: { left: 'left_shoulder', right: 'right_shoulder' },
    rt_elbow: { left: 'left_elbow', right: 'right_elbow' },
    rt_ankle_mover: { left: 'left_ankle', right: 'right_ankle' },
  };

  const normalized = joint
    .replace(/^both_/, 'both_')
    .replace(/^rt_/, 'rt_')
    .replace(/^lt_/, 'lt_');

  const entry = map[normalized] || map[joint.replace(/[^a-z_]/g, '')];
  if (!entry) {
    if (joint.includes('cross') && joint.includes('shoulder')) {
      return side === 'right' ? 'right_shoulder_cross' : 'left_shoulder_cross';
    }
    if (joint.includes('cross') && joint.includes('hip')) {
      return side === 'right' ? 'right_hip_cross' : 'left_hip_cross';
    }
    if (joint.includes('shoulder')) return side === 'right' ? 'right_shoulder' : 'left_shoulder';
    if (joint.includes('elbow')) return side === 'right' ? 'right_elbow' : 'left_elbow';
    if (joint.includes('hip')) return side === 'right' ? 'right_hip' : 'left_hip';
    if (joint.includes('knee')) return side === 'right' ? 'right_knee' : 'left_knee';
    if (joint.includes('ankle')) return side === 'right' ? 'right_ankle' : 'left_ankle';
    if (joint.includes('wrist')) return side === 'right' ? 'right_wrist' : 'left_wrist';
    return 'spine';
  }

  if (entry.bilateral && side === 'bilateral') return entry.bilateral;
  return side === 'right' ? entry.right : entry.left;
}

function pairedJoint(joint: string): string | undefined {
  const pairs: Record<string, string> = {
    left_shoulder: 'right_shoulder',
    right_shoulder: 'left_shoulder',
    left_elbow: 'right_elbow',
    right_elbow: 'left_elbow',
    left_hip: 'right_hip',
    right_hip: 'left_hip',
    left_knee: 'right_knee',
    right_knee: 'left_knee',
    left_ankle: 'right_ankle',
    right_ankle: 'left_ankle',
    left_wrist: 'right_wrist',
    right_wrist: 'left_wrist',
    left_shoulder_cross: 'right_shoulder_cross',
    right_shoulder_cross: 'left_shoulder_cross',
    left_hip_cross: 'right_hip_cross',
    right_hip_cross: 'left_hip_cross',
  };
  return pairs[joint];
}

function buildPrimaryUpDownJoint(
  jointCode: string,
  startRange: AngleRange,
  endRange: AngleRange,
  invertIndicator?: boolean,
  side: 'bilateral' | 'left' | 'right' = 'bilateral',
): TrackedJoint {
  let upRangeBase: AngleRange;
  let downRangeBase: AngleRange;

  const overlaps = startRange.max > endRange.min && endRange.max >= startRange.min;
  if (overlaps) {
    downRangeBase = {
      min: startRange.min,
      max: Math.max(startRange.min + 5, Math.min(startRange.max, endRange.min)),
    };
    upRangeBase = {
      min: Math.min(endRange.max - 5, Math.max(endRange.min, downRangeBase.max + 10)),
      max: endRange.max,
    };
    if (upRangeBase.min >= upRangeBase.max) {
      upRangeBase = { min: Math.max(0, endRange.max - 10), max: endRange.max };
    }
  } else {
    const startIsHigher = startRange.min > endRange.min;
    upRangeBase = startIsHigher ? startRange : endRange;
    downRangeBase = startIsHigher ? endRange : startRange;
  }

  const joint: TrackedJoint = {
    joint: jointCode,
    role: 'primary',
    startPose: { ...startRange },
    upRange: buildStateRanges(upRangeBase),
    downRange: buildStateRanges(downRangeBase),
    stateMessages: PRIMARY_UP_MESSAGES,
  };

  ensureTransitionGap(joint.upRange as StateRanges, joint.downRange as StateRanges);

  if (invertIndicator) joint.invertIndicator = true;
  if (side === 'bilateral' && pairedJoint(jointCode)) {
    joint.pairedWith = pairedJoint(jointCode);
  }

  return joint;
}

function buildPrimaryHoldJoint(jointCode: string, range: AngleRange, side: 'bilateral' | 'left' | 'right'): TrackedJoint {
  const joint: TrackedJoint = {
    joint: jointCode,
    role: 'primary',
    startPose: { ...range },
    range: expandRange(range),
    stateMessages: HOLD_PRIMARY_MESSAGES,
  };
  if (side === 'bilateral' && pairedJoint(jointCode)) {
    joint.pairedWith = pairedJoint(jointCode);
  }
  return joint;
}

function buildSecondaryJoint(
  jointCode: string,
  startRange: AngleRange | null,
  endRange: AngleRange | null,
  isMover: boolean,
  side: 'bilateral' | 'left' | 'right',
): TrackedJoint | null {
  if (!startRange && !endRange) return null;

  let range: AngleRange;
  if (isMover && startRange && endRange) {
    range = {
      min: Math.min(startRange.min, endRange.min),
      max: Math.max(startRange.max, endRange.max),
    };
  } else {
    range = startRange || endRange!;
  }

  const joint: TrackedJoint = {
    joint: jointCode,
    role: 'secondary',
    startPose: startRange ? { ...startRange } : { ...range },
    range: buildStateRanges(range),
    stateMessages: SECONDARY_MESSAGES,
  };

  if (side === 'bilateral' && pairedJoint(jointCode)) {
    joint.pairedWith = pairedJoint(jointCode);
  }

  return joint;
}

function parseRepIntervalMs(raw: string | undefined, fallbackMs: number): number {
  if (!raw) return fallbackMs;
  const match = raw.match(/([\d.]+)/);
  if (!match) return fallbackMs;
  return Math.round(Number(match[1]) * 1000);
}

function resolveCountingMethod(typeRaw: string, nameRaw: string): CountingMethod {
  const type = typeRaw.toLowerCase();
  const name = nameRaw.toLowerCase();
  if (type.includes('isometric') && (name.includes('plank') || name.includes('wall sit'))) {
    return 'hold';
  }
  return 'up_down';
}

function resolvePosePosition(
  startingPosition: string,
  bodyPart: string,
  viewCamera: string,
): {
  posePosition: PosePosition;
  expectedPostures: string[];
  expectedDirections: string[];
  expectedRegions: string[];
  variantName: { ar: string; en: string };
} {
  const start = startingPosition.toLowerCase();
  const body = bodyPart.toLowerCase();
  const view = viewCamera.toLowerCase();

  const isUpper = body.includes('upper') || body.includes('trunk');
  const isLower = body.includes('lower');
  const isSitting = start.includes('sit');
  const isSupine = (start.includes('lay') && start.includes('back')) || start.includes('bench') || start.includes('back support');
  const isProne = start.includes('plank') || (start.includes('forearm') && start.includes('toe')) || start.includes('forearms and toes');
  const isSideLying = start.includes('side-lying') || start.includes('side lying') || start.includes('supported on forearm');

  if (isSideLying) {
    return {
      posePosition: 'side_lying',
      expectedPostures: ['lying_side'],
      expectedDirections: ['side'],
      expectedRegions: ['full_body'],
      variantName: { ar: 'استلقاء جانبي', en: 'Side-lying view' },
    };
  }

  if (isSupine && view.includes('front')) {
    return {
      posePosition: 'supine_front',
      expectedPostures: ['lying_supine'],
      expectedDirections: ['front'],
      expectedRegions: ['upper_body'],
      variantName: { ar: 'أمامي - مستلقي', en: 'Supine front view' },
    };
  }

  if (isProne) {
    return {
      posePosition: 'prone_side',
      expectedPostures: ['lying_prone'],
      expectedDirections: ['side'],
      expectedRegions: ['full_body'],
      variantName: { ar: 'جانبي - وضع انبطاح', en: 'Prone side view' },
    };
  }

  if (isSitting && view.includes('rear')) {
    return {
      posePosition: 'sitting_front_upper',
      expectedPostures: ['sitting'],
      expectedDirections: ['front'],
      expectedRegions: ['upper_body'],
      variantName: { ar: 'أمامي - جالس', en: 'Seated front view' },
    };
  }

  if (isSitting) {
    return {
      posePosition: view.includes('side') ? 'sitting_side_upper' : 'sitting_front_upper',
      expectedPostures: ['sitting'],
      expectedDirections: [view.includes('side') ? 'side' : 'front'],
      expectedRegions: ['upper_body'],
      variantName: { ar: 'جالس', en: 'Seated view' },
    };
  }

  if (view.includes('rear') || view.includes('back')) {
    return {
      posePosition: isLower ? 'standing_back_lower' : 'standing_back_upper',
      expectedPostures: ['standing'],
      expectedDirections: ['back'],
      expectedRegions: [isLower ? 'lower_body' : 'upper_body'],
      variantName: { ar: 'خلفي', en: 'Back view' },
    };
  }

  if (view.includes('front')) {
    return {
      posePosition: isLower ? 'standing_front_lower' : 'standing_front_upper',
      expectedPostures: ['standing'],
      expectedDirections: ['front'],
      expectedRegions: [isLower ? 'lower_body' : 'upper_body'],
      variantName: { ar: 'أمامي', en: 'Front view' },
    };
  }

  return {
    posePosition: isLower ? 'standing_side_lower' : 'standing_side_upper',
    expectedPostures: ['standing'],
    expectedDirections: ['side'],
    expectedRegions: [isLower ? 'lower_body' : 'upper_body'],
    variantName: { ar: 'جانبي', en: 'Side view' },
  };
}

function normalizeLandmark(raw: string | undefined): string | null {
  if (!raw || !raw.trim()) return null;
  const value = raw.trim().toLowerCase().replace(/\s+/g, '_');
  const map: Record<string, string> = {
    rt_elbow: 'right_elbow',
    rt_elboe: 'right_elbow',
    lt_elbow: 'left_elbow',
    lt_elboe: 'left_elbow',
    rt_shoulder: 'right_shoulder',
    rt__shoulder: 'right_shoulder',
    lt_shoulder: 'left_shoulder',
    lt_shouler: 'left_shoulder',
    shoulder: 'left_shoulder',
    hip: 'left_hip',
    knee: 'left_knee',
    ankle: 'left_ankle',
    wrist: 'left_wrist',
    spine: 'spine',
    neck: 'neck',
    elbow: 'left_elbow',
  };
  return map[value] || value;
}

function parseActivePhases(raw: string | undefined): PhaseName[] {
  if (!raw || !raw.trim()) return ['all'];
  const phases = raw
    .toLowerCase()
    .split(/[,/]+/)
    .map((p) => p.trim())
    .filter(Boolean)
    .map((p) => {
      if (p === 'start') return 'top';
      if (p === 'hold') return 'count';
      if (p === 'push' || p === 'pull') return 'up';
      if (p === 'uo') return 'up';
      if (p === 'tio' || p === 'tip') return 'up';
      return p;
    })
    .filter((p): p is PhaseName =>
      ['all', 'top', 'down', 'bottom', 'up', 'count'].includes(p),
    );
  return phases.length > 0 ? [...new Set(phases)] : ['all'];
}

function parseSeverity(raw: string | undefined): Severity {
  const value = (raw || '').toLowerCase();
  if (value.includes('error')) return 'error';
  if (value.includes('warn')) return 'warning';
  return 'tip';
}

function buildCheckFromRow(
  slug: string,
  index: number,
  typeRaw: string,
  landmarks: string[],
  activePhaseRaw: string | undefined,
  severityRaw: string | undefined,
): PositionCheck | null {
  const typeText = typeRaw.toLowerCase();

  if (typeText.includes('knee over toe')) {
    return {
      id: `${slug}_check_${index}`,
      type: 'forward_comparison',
      landmarks: { primary: 'right_knee', secondary: 'right_foot_index' },
      condition: { operator: 'should_not_exceed', threshold: 0.1 },
      activePhases: ['down', 'bottom'],
      errorMessage: {
        ar: 'لا تدع الركبة تتجاوز أصابع القدم.',
        en: "Don't let the knee go past the toe.",
      },
      severity: 'warning',
      cooldownMs: 2200,
      minErrorFrames: 5,
    };
  }

  if (!typeText.trim()) return null;

  let type: PositionCheck['type'] = 'vertical_alignment';
  let operator: PositionCheck['condition']['operator'] = 'approximately_equal';

  if (typeText.includes('horizontal')) {
    type = 'horizontal_alignment';
    operator = 'approximately_equal';
  } else if (typeText.includes('vertical')) {
    type = 'vertical_alignment';
    operator = 'approximately_equal';
  } else if (typeText.includes('depth')) {
    type = 'depth_alignment';
    operator = typeText.includes('not exceed') ? 'should_not_exceed' : 'should_exceed';
  } else if (typeText.includes('distance ratio')) {
    type = 'distance_ratio';
    operator = typeText.includes('not exceed') ? 'should_not_exceed' : 'should_exceed';
  }

  const [primary, secondary, tertiary, quaternary] = landmarks
    .map((l) => normalizeLandmark(l))
    .filter((l): l is string => Boolean(l));

  if (!primary || !secondary) return null;

  return {
    id: `${slug}_check_${index}`,
    type,
    landmarks: {
      primary,
      secondary,
      ...(tertiary ? { tertiary } : {}),
      ...(quaternary ? { quaternary } : {}),
    },
    condition: { operator, threshold: 0.08 },
    activePhases: parseActivePhases(activePhaseRaw),
    errorMessage: {
      ar: 'حافظ على المحاذاة المطلوبة أثناء التمرين.',
      en: 'Keep the required alignment during the exercise.',
    },
    severity: parseSeverity(severityRaw),
    cooldownMs: 2200,
    minErrorFrames: 4,
  };
}

/** Checks sheet repeats headers; parse by column index from raw values. */
export function normalizeChecksRow(values: string[]): CsvRow {
  return {
    Exercise_ID: values[0] || '',
    check_0_type: values[1] || '',
    check_0_lm1: values[2] || '',
    check_0_lm2: values[3] || '',
    check_0_lm3: values[4] || '',
    check_0_lm4: values[5] || '',
    check_0_active: values[7] || '',
    check_0_severity: values[8] || '',
    check_1_type: values[9] || '',
    check_1_lm1: values[10] || '',
    check_1_lm2: values[11] || '',
    check_1_lm3: values[12] || '',
    check_1_lm4: values[13] || '',
    check_1_active: values[14] || '',
    check_1_severity: values[15] || '',
    check_2_type: values[16] || '',
    check_2_lm1: values[17] || '',
    check_2_lm2: values[18] || '',
    check_2_lm3: values[19] || '',
    check_2_lm4: values[20] || '',
    check_2_active: values[21] || '',
    check_2_severity: values[22] || '',
  };
}

function buildPositionChecks(slug: string, checksRow: CsvRow): PositionCheck[] {
  const checks: PositionCheck[] = [];
  for (let i = 0; i < 3; i++) {
    const typeRaw = checksRow[`check_${i}_type`] || '';
    const landmarks = [
      checksRow[`check_${i}_lm1`],
      checksRow[`check_${i}_lm2`],
      checksRow[`check_${i}_lm3`],
      checksRow[`check_${i}_lm4`],
    ];
    const check = buildCheckFromRow(
      slug,
      i + 1,
      typeRaw,
      landmarks,
      checksRow[`check_${i}_active`],
      checksRow[`check_${i}_severity`],
    );
    if (check) checks.push(check);
  }
  return checks;
}

function parseMuscles(raw: string | undefined): string[] {
  if (!raw) return [];
  const tokens = raw
    .split(/[,–—-]+/)
    .map((t) => t.trim().toLowerCase())
    .filter(Boolean);

  const mapToken = (token: string): string => {
    if (token.includes('quad')) return 'quadriceps';
    if (token.includes('gluteus medius') || token.includes('gluteus minimus')) return 'glutes';
    if (token.includes('glute')) return 'glutes';
    if (token.includes('hamstring')) return 'hamstrings';
    if (token.includes('bicep')) return 'biceps';
    if (token.includes('tricep')) return 'triceps';
    if (token.includes('pectoral') || token.includes('pec')) return 'chest_muscle';
    if (token.includes('deltoid') || token.includes('delt')) return 'front_delts';
    if (token.includes('trapezius') || token.includes('trapez')) return 'traps';
    if (token.includes('rhomboid') || token.includes('teres') || token.includes('latissimus') || token.includes('lat')) return 'lats';
    if (token.includes('erector') || token.includes('spinae')) return 'lower_back';
    if (token.includes('calf') || token.includes('gastrocnemius') || token.includes('soleus')) return 'calves';
    if (token.includes('adductor')) return 'adductors';
    if (token.includes('brachialis') || token.includes('brachioradialis')) return 'biceps';
    if (token.includes('posterior deltoid')) return 'rear_delts';
    return slugifyName(token);
  };

  return [...new Set(tokens.map(mapToken))];
}

function parseEquipment(raw: string | undefined): string[] {
  if (!raw || !raw.trim()) return ['bodyweight'];
  const value = raw.trim().toLowerCase();
  if (value.includes('dumbbell')) return ['dumbbell'];
  if (value.includes('bar')) return ['barbell'];
  if (value.includes('cable')) return ['cable_machine'];
  if (value.includes('machine')) return ['machine'];
  if (value.includes('box')) return ['box'];
  if (value.includes('bensh') || value.includes('bench')) return ['bench'];
  return [slugifyName(value)];
}

function resolveCategory(bodyPart: string, exerciseName: string): { code: string; name: { ar: string; en: string } } {
  const body = bodyPart.toLowerCase();
  const name = exerciseName.toLowerCase();
  if (name.includes('curl') || name.includes('press') && name.includes('bench')) {
    return { code: 'chest', name: { ar: 'تمارين الصدر', en: 'Chest' } };
  }
  if (name.includes('curl') || name.includes('triceps') || name.includes('hammer')) {
    return { code: 'arms', name: { ar: 'تمارين الذراعين', en: 'Arms' } };
  }
  if (name.includes('row') || name.includes('pulldown') || name.includes('pull')) {
    return { code: 'back', name: { ar: 'تمارين الظهر', en: 'Back' } };
  }
  if (name.includes('shoulder') || name.includes('press') || name.includes('abduction')) {
    return { code: 'shoulders', name: { ar: 'تمارين الأكتاف', en: 'Shoulders' } };
  }
  if (body.includes('trunk') || name.includes('extension') && name.includes('back')) {
    return { code: 'back', name: { ar: 'تمارين الظهر', en: 'Back' } };
  }
  if (body.includes('upper')) {
    return { code: 'shoulders', name: { ar: 'تمارين الأكتاف', en: 'Shoulders' } };
  }
  if (name.includes('plank') || name.includes('copenhagen')) {
    return { code: 'abs', name: { ar: 'تمارين البطن', en: 'Abs' } };
  }
  return { code: 'legs', name: { ar: 'تمارين الأرجل', en: 'Legs' } };
}

function inferBlueprint(slug: string, categoryCode: string, countingMethod: CountingMethod, equipment: string[]) {
  const s = slug.toLowerCase();
  let movementPattern = 'OTHER';
  if (s.includes('squat') || s.includes('lunge') || s.includes('step')) movementPattern = 'SQUAT';
  else if (s.includes('deadlift') || s.includes('rdl') || s.includes('dealift')) movementPattern = 'HINGE';
  else if (s.includes('push_up') || s.includes('pushup') || s.includes('bench')) movementPattern = 'PUSH_HORIZONTAL';
  else if (s.includes('press') || s.includes('extension') && s.includes('triceps')) movementPattern = 'PUSH_VERTICAL';
  else if (s.includes('row') || s.includes('pulldown') || s.includes('pull')) movementPattern = 'PULL_VERTICAL';
  else if (s.includes('curl')) movementPattern = 'OTHER';
  else if (s.includes('calf')) movementPattern = 'GAIT';
  else if (s.includes('plank') || s.includes('copenhagen')) movementPattern = 'CORE_BRACE';
  else if (categoryCode === 'legs') movementPattern = 'LUNGE';
  else if (categoryCode === 'back') movementPattern = 'PULL_VERTICAL';

  const hasBarbell = equipment.includes('barbell');
  const hasLoad = equipment.some((e) => e !== 'bodyweight');
  let loadCapability = 'BODYWEIGHT_ONLY';
  if (hasBarbell) loadCapability = 'EXTERNAL_LOAD_REQUIRED';
  else if (hasLoad) loadCapability = 'EXTERNAL_LOAD_OPTIONAL';

  const exerciseNum = Number((s.match(/^ex(\d+)/) || [])[1] || 200);
  return {
    movementPattern,
    loadCapability,
    familyKey: `${movementPattern.toLowerCase()}_family`,
    familyOrder: exerciseNum,
  };
}

/** Fix ROM row when CSV has duplicate column headers (secondary joints). */
export function normalizeRomRow(headers: string[], values: string[]): CsvRow {
  const row: CsvRow = { Exercise_ID: values[0] || '' };
  row['Primary Joint'] = values[1] || '';
  row.Side = values[2] || '';
  row.Start = values[3] || '';
  row.End = values[4] || '';
  row.Assessment = values[5] || '';
  row['Assessment +'] = values[6] || '';

  // Secondary blocks start at index 7, pattern: joint, stable/mover, start, end × N
  let idx = 7;
  let block = 0;
  while (idx + 3 < values.length) {
    const joint = values[idx];
    const role = values[idx + 1];
    const start = values[idx + 2];
    const end = values[idx + 3];
    if (!joint && !role && !start && !end) break;
    row[`secondary_${block}_joint`] = joint;
    row[`secondary_${block}_role`] = role;
    row[`secondary_${block}_start`] = start;
    row[`secondary_${block}_end`] = end;
    block += 1;
    idx += 4;
  }

  return row;
}

function resolveSideFromJoint(jointRaw: string, fallback: 'bilateral' | 'left' | 'right'): 'bilateral' | 'left' | 'right' {
  const joint = jointRaw.toLowerCase();
  if (joint.includes('rt ') || joint.startsWith('rt_') || joint.includes('right')) return 'right';
  if (joint.includes('lt ') || joint.startsWith('lt_') || joint.includes('left')) return 'left';
  return fallback;
}

function buildTrackedJointsFromNormalizedRom(romRow: CsvRow, countingMethod: CountingMethod, invert: boolean): TrackedJoint[] {
  const joints: TrackedJoint[] = [];
  const primarySide = resolveSideFromJoint(romRow['Primary Joint'] || '', normalizeJointSide(romRow.Side));
  const primaryStart = parseRange(romRow.Start);
  const primaryEnd = parseRange(romRow.End);

  if (romRow['Primary Joint'] && primaryStart && primaryEnd) {
    const jointCode = resolveJointCode(romRow['Primary Joint'], primarySide);
    if (countingMethod === 'hold') {
      joints.push(buildPrimaryHoldJoint(jointCode, primaryEnd, primarySide));
    } else {
      joints.push(buildPrimaryUpDownJoint(jointCode, primaryStart, primaryEnd, invert, primarySide));
    }
  }

  for (let i = 0; i < 8; i++) {
    const jointRaw = romRow[`secondary_${i}_joint`];
    if (!jointRaw || !jointRaw.trim() || jointRaw === '_') continue;
    const roleRaw = romRow[`secondary_${i}_role`] || '';
    const isMover = roleRaw.toLowerCase().includes('mover');
    const start = parseRange(romRow[`secondary_${i}_start`]);
    const end = parseRange(romRow[`secondary_${i}_end`]);

    const side =
      jointRaw.toLowerCase().includes('rt') || jointRaw.toLowerCase().includes('right')
        ? 'right'
        : jointRaw.toLowerCase().includes('lt') || jointRaw.toLowerCase().includes('left')
          ? 'left'
          : primarySide;

    const jointCode = resolveJointCode(jointRaw, side);
    const secondary = buildSecondaryJoint(jointCode, start, end, isMover, side);
    if (secondary) joints.push(secondary);
  }

  return joints;
}

export function buildExerciseJsonFromRows(
  exerciseRow: CsvRow,
  romRow: CsvRow,
  checksRow: CsvRow,
  options: { groupTag: string },
): ExerciseConfig & {
  slug: string;
  status: string;
  movementPattern: string;
  loadCapability: string;
  familyKey: string;
  familyOrder: number;
  hasPositionChecks?: boolean;
} {
  const exerciseId = exerciseRow.Exercise_ID?.trim();
  const exerciseName = exerciseRow['Exercise Name']?.trim() || exerciseId;
  const slug = buildSlug(exerciseId, exerciseName);

  const startingPosition = exerciseRow['Starting Postion'] || exerciseRow['Starting Position'] || 'Standing';
  const bodyPart = exerciseRow['Body Part'] || exerciseRow.Body_Part || 'Full Body';
  const viewCamera = exerciseRow['View Camera'] || exerciseRow.View_Camera || 'side view';
  const type = exerciseRow.Type || 'ups and down';
  const invert = (exerciseRow['Invert Indicator'] || '').toLowerCase() === 'yes';

  const countingMethod = resolveCountingMethod(type, exerciseName);
  const minMs = parseRepIntervalMs(exerciseRow['Rep Minimum Time'], 1500);
  const maxMs = parseRepIntervalMs(exerciseRow['Rep Maximum Time'], 3000);
  const reps = Number(exerciseRow.Repetion || exerciseRow.Repetition || 10) || 10;

  const muscles = parseMuscles(exerciseRow.Muscles || exerciseRow.muscles);
  const equipment = parseEquipment(exerciseRow.Equipment);
  const category = resolveCategory(bodyPart, exerciseName);
  const pose = resolvePosePosition(startingPosition, bodyPart, viewCamera);
  const positionChecks = buildPositionChecks(slug, checksRow);
  const trackedJoints = buildTrackedJointsFromNormalizedRom(romRow, countingMethod, invert);

  const blueprint = inferBlueprint(slug, category.code, countingMethod, equipment);
  const supportsWeight = equipment.some((e) => e !== 'bodyweight' && e !== 'machine');

  const isUnilateral =
    normalizeJointSide(romRow.Side) !== 'bilateral' ||
    (romRow['Primary Joint'] || '').toLowerCase().includes('rt') ||
    (romRow['Primary Joint'] || '').toLowerCase().includes('lt');

  const tags = [
    options.groupTag,
    bodyPart.toLowerCase().includes('lower') ? 'lower_body' : 'upper_body',
    'strength',
    ...(countingMethod === 'hold' ? ['isometric'] : []),
    ...(isUnilateral ? ['unilateral'] : []),
  ];

  const repCountingConfig =
    countingMethod === 'hold'
      ? { duration: Math.max(15, Math.round((minMs + maxMs) / 2000)), gracePeriodMs: 2500 }
      : { reps, minRepIntervalMs: minMs, maxRepIntervalMs: maxMs };

  const exerciseJson = {
    slug,
    status: 'published',
    movementPattern: blueprint.movementPattern,
    loadCapability: blueprint.loadCapability,
    familyKey: blueprint.familyKey,
    familyOrder: blueprint.familyOrder,
    name: { ar: exerciseName, en: exerciseName },
    category,
    countingMethod,
    muscles: muscles.length > 0 ? muscles : ['abs_muscle'],
    equipment,
    tags: [...new Set(tags)],
    repCountingConfig,
    supportsWeight,
    poseVariants: [
      {
        name: pose.variantName,
        posePosition: pose.posePosition,
        expectedPostures: pose.expectedPostures,
        expectedDirections: pose.expectedDirections,
        expectedRegions: pose.expectedRegions,
        trackedJoints,
        ...(positionChecks.length > 0 ? { positionChecks } : {}),
        feedbackMessages: {
          motivational: [
            {
              ar: `أداء ممتاز في ${exerciseName}`,
              en: `Excellent controlled ${exerciseName}`,
            },
          ],
          tips: [
            {
              ar: 'حافظ على التحكم في الحركة والتنفس الهادئ.',
              en: 'Keep the movement controlled and breathe steadily.',
            },
          ],
        },
      },
    ],
    description: {
      ar: `${exerciseName} — تمرين ضمن مجموعة التمارين المفقودة، تم تحويله من ملف الإكسل مع ربط المدى الحركي والفحوصات.`,
      en: `${exerciseName} from the missing exercises group, converted from the spreadsheet with ROM and checks linked by Exercise_ID.`,
    },
    instructions: {
      ar: `1. جهز وضع البداية: ${startingPosition}.\n2. تحرك بسلاسة خلال المدى المطلوب.\n3. حافظ على المفاصل الثانوية تحت التحكم وتجنب التعويض.\n4. توقف إذا ظهر ألم حاد أو فقدت التحكم.`,
      en: `1. Set up in the listed start position: ${startingPosition}.\n2. Move smoothly through the target range.\n3. Keep the secondary joints controlled and avoid compensation.\n4. Stop if sharp pain appears or control is lost.`,
    },
    reportMetrics: {
      primary: ['FORM_SCORE'] as MetricCode[],
      excluded: (countingMethod === 'hold'
        ? ['WEIGHT', 'VOLUME', 'EST_1RM']
        : ['HOLD_DURATION']) as MetricCode[],
    },
    ...(positionChecks.length > 0 ? { hasPositionChecks: true } : {}),
  };

  return exerciseJson;
}

export function parseChecksCsv(content: string): CsvRow[] {
  const rows: string[][] = [];
  let current: string[] = [];
  let field = '';
  let inQuotes = false;

  for (let i = 0; i < content.length; i++) {
    const char = content[i];
    if (char === '"') {
      inQuotes = !inQuotes;
      continue;
    }
    if (char === ',' && !inQuotes) {
      current.push(field);
      field = '';
      continue;
    }
    if ((char === '\n' || char === '\r') && !inQuotes) {
      if (char === '\r' && content[i + 1] === '\n') i++;
      current.push(field);
      rows.push(current);
      current = [];
      field = '';
      continue;
    }
    field += char;
  }
  if (field.length > 0 || current.length > 0) {
    current.push(field);
    rows.push(current);
  }

  return rows.slice(1).filter((r) => r[0]?.trim()).map((values) => normalizeChecksRow(values));
}

export function parseCsv(content: string): CsvRow[] {
  const rows: string[][] = [];
  let current: string[] = [];
  let field = '';
  let inQuotes = false;

  for (let i = 0; i < content.length; i++) {
    const char = content[i];
    if (char === '"') {
      inQuotes = !inQuotes;
      continue;
    }
    if (char === ',' && !inQuotes) {
      current.push(field);
      field = '';
      continue;
    }
    if ((char === '\n' || char === '\r') && !inQuotes) {
      if (char === '\r' && content[i + 1] === '\n') i++;
      current.push(field);
      rows.push(current);
      current = [];
      field = '';
      continue;
    }
    field += char;
  }
  if (field.length > 0 || current.length > 0) {
    current.push(field);
    rows.push(current);
  }

  if (rows.length === 0) return [];
  const headers = rows[0];
  return rows.slice(1).filter((r) => r.some((c) => c.trim())).map((values) => {
    const row: CsvRow = {};
    headers.forEach((header, index) => {
      row[header.trim()] = (values[index] || '').trim();
    });
    return row;
  });
}

export function parseRomCsv(content: string): CsvRow[] {
  const rows: string[][] = [];
  let current: string[] = [];
  let field = '';
  let inQuotes = false;

  for (let i = 0; i < content.length; i++) {
    const char = content[i];
    if (char === '"') {
      inQuotes = !inQuotes;
      continue;
    }
    if (char === ',' && !inQuotes) {
      current.push(field);
      field = '';
      continue;
    }
    if ((char === '\n' || char === '\r') && !inQuotes) {
      if (char === '\r' && content[i + 1] === '\n') i++;
      current.push(field);
      rows.push(current);
      current = [];
      field = '';
      continue;
    }
    field += char;
  }
  if (field.length > 0 || current.length > 0) {
    current.push(field);
    rows.push(current);
  }

  const headers = rows[0] || [];
  return rows.slice(1).filter((r) => r[0]?.trim()).map((values) => normalizeRomRow(headers, values));
}
