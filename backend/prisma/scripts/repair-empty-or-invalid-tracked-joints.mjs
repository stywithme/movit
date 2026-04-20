/**
 * Fix exercises-from-db JSON that became invalid after spine removal:
 * - empty trackedJoints → add minimal hold/secondary hip + primary where needed
 * - crunch: restore primary up/down on left_hip (proxy for trunk curl ROM)
 * - assessment_shoulder_mobility: fix overlapping shoulder up/down ranges
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DIR = path.resolve(__dirname, '../Exercise-json/exercises-from-db');

/** Small up/down on left hip for mobility drills (replaces spine-as-primary). */
const MOBILITY_UPDOWN_PRIMARY_LEFT_HIP = {
  joint: 'left_hip',
  role: 'primary',
  startPose: { max: 180, min: 130 },
  pairedWith: 'right_hip',
  stateMessages: {
    pad: {
      down: { ar: 'مقبول — مدى أصغر', en: 'Acceptable — smaller range' },
    },
    normal: {
      up: { ar: 'جيد — حركة أنعم', en: 'Good — smoother motion' },
      down: { ar: 'جيد — تحكم أكبر', en: 'Good — more control' },
    },
    perfect: {
      up: { ar: 'ممتاز! حركة متزنة', en: 'Perfect! Balanced motion' },
      down: { ar: 'ممتاز! تحكم في الحوض', en: 'Perfect! Hip control' },
    },
    warning: {
      down: { ar: 'قلل المدى إن الظهر يضيق', en: 'Ease range if your back pinches' },
    },
  },
  upRange: {
    normal: { max: 180, min: 158 },
    perfect: { max: 180, min: 168 },
  },
  downRange: {
    pad: { max: 145, min: 105 },
    normal: { max: 138, min: 112 },
    perfect: { max: 128, min: 118 },
    warning: { max: 112, min: 98 },
  },
};

const HOLD_HIP_SECONDARY = {
  joint: 'left_hip',
  role: 'secondary',
  startPose: { max: 180, min: 95 },
  stateMessages: {
    perfect: {
      ar: 'حوض ثابت أثناء الحركة',
      en: 'Pelvis stays quiet during the drill',
    },
    warning: {
      ar: 'قلل التمايل أو الدوران في الحوض',
      en: 'Reduce rocking or twisting at the hips',
    },
  },
  range: {
    pad: { max: 180, min: 88 },
    normal: { max: 180, min: 95 },
    perfect: { max: 180, min: 102 },
    warning: { max: 95, min: 78 },
  },
};

const CRUNCH_PRIMARY_LEFT_HIP = {
  joint: 'left_hip',
  role: 'primary',
  startPose: { max: 20, min: 0 },
  stateMessages: {
    pad: {
      up: { ar: 'مقبول، لكن يمكنك التحسن', en: 'Acceptable, but you can improve' },
    },
    danger: {
      up: { ar: 'توقف! ترفع أكثر من اللازم', en: 'Stop! You are going too high' },
    },
    normal: {
      up: { ar: 'جيد، حاول رفع الكتفين أكثر', en: 'Good, try lifting shoulders more' },
      down: { ar: 'جيد، حاول النزول للأسفل', en: 'Good, try lowering more' },
    },
    perfect: {
      up: { ar: 'ممتاز! كرانش مثالي', en: 'Perfect! Great crunch' },
      down: { ar: 'ممتاز! عودة مثالية', en: 'Perfect! Great return' },
    },
    warning: {
      up: {
        ar: 'لا ترفع كثيراً - هذا كرانش وليس sit-up',
        en: "Don't go too high - this is a crunch, not sit-up",
      },
    },
  },
  upRange: {
    pad: { max: 50, min: 30 },
    danger: { max: 90, min: 75 },
    normal: { max: 55, min: 35 },
    perfect: { max: 60, min: 40 },
    warning: { max: 75, min: 60 },
  },
  downRange: {
    pad: { max: 25, min: 0 },
    normal: { max: 20, min: 0 },
    perfect: { max: 15, min: 0 },
  },
};

const ROTATION_PRIMARY_LEFT_HIP = {
  joint: 'left_hip',
  role: 'primary',
  startPose: { max: 180, min: 120 },
  pairedWith: 'right_hip',
  stateMessages: {
    pad: {
      down: { ar: 'مقبول — ثبّت الحوض أكثر', en: 'Acceptable — anchor the hips more' },
    },
    normal: {
      up: { ar: 'جيد — أكمل الوقفة', en: 'Good — finish the stand' },
      down: { ar: 'جيد — دوران أنظف', en: 'Good — cleaner rotation' },
    },
    perfect: {
      up: { ar: 'ممتاز! حوض ثابت في الأعلى', en: 'Perfect! Stable hips at the top' },
      down: { ar: 'ممتاز! دوران من الصدر والورك', en: 'Perfect! Rotation from thorax and hips' },
    },
    warning: {
      down: { ar: 'لا تلتف الحوض كاملاً مع الكتف', en: 'Do not spin the whole pelvis with the shoulder' },
    },
  },
  upRange: {
    normal: { max: 180, min: 158 },
    perfect: { max: 180, min: 168 },
  },
  downRange: {
    pad: { max: 135, min: 85 },
    normal: { max: 125, min: 92 },
    perfect: { max: 115, min: 100 },
    warning: { max: 92, min: 78 },
  },
};

const PRONE_EXTENSION_PRIMARY_LEFT_HIP = {
  joint: 'left_hip',
  role: 'primary',
  startPose: { max: 180, min: 140 },
  pairedWith: 'right_hip',
  stateMessages: {
    pad: {
      down: { ar: 'مقبول — مدى أقصر', en: 'Acceptable — shorter range' },
    },
    normal: {
      up: { ar: 'جيد — امتداد أكثر من الورك', en: 'Good — more hip-driven extension' },
      down: { ar: 'جيد — تحكم في النزول', en: 'Good — control the lowering' },
    },
    perfect: {
      up: { ar: 'ممتاز! امتداد متزن', en: 'Perfect! Balanced extension' },
      down: { ar: 'ممتاز! عودة دون انهيار', en: 'Perfect! Return without collapsing' },
    },
    warning: {
      up: { ar: 'لا تفرط في تمديد القطن', en: 'Do not hyperextend the low back' },
    },
  },
  upRange: {
    normal: { max: 180, min: 150 },
    perfect: { max: 180, min: 158 },
  },
  downRange: {
    pad: { max: 145, min: 120 },
    normal: { max: 138, min: 128 },
    perfect: { max: 132, min: 125 },
    warning: { max: 125, min: 110 },
  },
};

const YOGA_HOLD_PRIMARY_LEFT_HIP = {
  joint: 'left_hip',
  role: 'primary',
  startPose: { max: 180, min: 120 },
  stateMessages: {
    perfect: {
      ar: 'وركين مفتوحان مع إطالة ظهرية',
      en: 'Hips open with a long back line',
    },
    warning: {
      ar: 'قلل الترهل في الكتف أو الانهيار في القطن',
      en: 'Ease shoulder shrug or low-back collapse',
    },
  },
  range: {
    pad: { max: 180, min: 105 },
    normal: { max: 180, min: 118 },
    perfect: { max: 180, min: 132 },
    warning: { max: 105, min: 88 },
  },
};

const ANTI_ROT_PRIMARY_LEFT_HIP = {
  joint: 'left_hip',
  role: 'primary',
  startPose: { max: 180, min: 150 },
  stateMessages: {
    perfect: {
      ar: 'حوض مربع على القدمين',
      en: 'Hips square to your stance',
    },
    warning: {
      ar: 'لا تدع الحبل يسحبك للدوران',
      en: 'Do not let the band twist your hips',
    },
  },
  range: {
    pad: { max: 180, min: 145 },
    normal: { max: 180, min: 155 },
    perfect: { max: 180, min: 165 },
    warning: { max: 145, min: 130 },
  },
};

const HOLLOW_HOLD_PRIMARY_LEFT_HIP = {
  joint: 'left_hip',
  role: 'primary',
  startPose: { max: 160, min: 90 },
  stateMessages: {
    perfect: {
      ar: 'زاوية ثابتة للساقين مع ظهر ملتصق بالأرض',
      en: 'Steady leg angle with low back pressed to the floor',
    },
    warning: {
      ar: 'لا تدع أسفل الظهر يرتفع عن الأرض',
      en: 'Do not let the low back peel off the floor',
    },
  },
  range: {
    pad: { max: 165, min: 95 },
    normal: { max: 155, min: 105 },
    perfect: { max: 145, min: 115 },
    warning: { max: 95, min: 80 },
  },
};

const SEATED_HINGE_PRIMARY_LEFT_HIP = {
  joint: 'left_hip',
  role: 'primary',
  startPose: { max: 180, min: 120 },
  pairedWith: 'right_hip',
  stateMessages: {
    pad: {
      down: { ar: 'مقبول — مدى أقصر', en: 'Acceptable — shorter hinge' },
    },
    normal: {
      up: { ar: 'جيد — ارجع للجلوس باستقامة', en: 'Good — return tall' },
      down: { ar: 'جيد — انحنِ من الورك', en: 'Good — hinge from the hip' },
    },
    perfect: {
      up: { ar: 'ممتاز! جذع منتصب', en: 'Perfect! Upright torso' },
      down: { ar: 'ممتاز! مفصل ورك نظيف', en: 'Perfect! Clean hip hinge' },
    },
    warning: {
      down: { ar: 'لا تطوِ الجذع من الوسط فقط', en: 'Do not fold only through the mid-back' },
    },
  },
  upRange: {
    normal: { max: 180, min: 158 },
    perfect: { max: 180, min: 168 },
  },
  downRange: {
    pad: { max: 135, min: 88 },
    normal: { max: 125, min: 95 },
    perfect: { max: 115, min: 102 },
    warning: { max: 95, min: 82 },
  },
};

function deepClone(x) {
  return JSON.parse(JSON.stringify(x));
}

function insertPrimaryFirst(trackedJoints, primary) {
  const rest = (trackedJoints || []).filter((j) => j?.joint !== primary.joint);
  return [deepClone(primary), ...rest];
}

function ensureNonEmpty(trackedJoints, filler) {
  if (!Array.isArray(trackedJoints) || trackedJoints.length === 0) {
    return [deepClone(filler)];
  }
  return trackedJoints;
}

function patchFile(slug, mutator) {
  const fp = path.join(DIR, `${slug}.json`);
  if (!fs.existsSync(fp)) return;
  const data = JSON.parse(fs.readFileSync(fp, 'utf8'));
  mutator(data);
  fs.writeFileSync(fp, JSON.stringify(data, null, 2) + '\n', 'utf8');
  console.log('Patched', slug);
}

function mirrorDbTrackedConfig(data, pvIndex, trackedJoints) {
  const dbPv = data._database?.poseVariants?.[pvIndex];
  if (!dbPv) return;
  dbPv.trackedJointsConfig = deepClone(trackedJoints);
}

function main() {
  // Crunch: primary left_hip + secondary knee
  patchFile('crunch', (data) => {
    const pv = data.poseVariants[0];
    const knee = pv.trackedJoints.find((j) => j.joint === 'left_knee');
    pv.trackedJoints = [deepClone(CRUNCH_PRIMARY_LEFT_HIP), knee].filter(Boolean);
    mirrorDbTrackedConfig(data, 0, pv.trackedJoints);
  });

  // Hold drills with empty joints
  const upDownEmptySlugs = ['lib_cat_camel', 'lib_inchworm'];
  for (const slug of upDownEmptySlugs) {
    patchFile(slug, (data) => {
      for (let i = 0; i < data.poseVariants.length; i++) {
        const pv = data.poseVariants[i];
        if (!Array.isArray(pv.trackedJoints) || pv.trackedJoints.length === 0) {
          pv.trackedJoints = [deepClone(MOBILITY_UPDOWN_PRIMARY_LEFT_HIP)];
          mirrorDbTrackedConfig(data, i, pv.trackedJoints);
        }
      }
    });
  }

  const holdEmptySlugs = ['lib_neck_retraction', 'lib_seated_neck_rotation', 'lib_wall_posture_reset', 'lib_windmill_stretch'];
  for (const slug of holdEmptySlugs) {
    patchFile(slug, (data) => {
      for (let i = 0; i < data.poseVariants.length; i++) {
        const pv = data.poseVariants[i];
        if (!Array.isArray(pv.trackedJoints) || pv.trackedJoints.length === 0) {
          const j = deepClone(HOLD_HIP_SECONDARY);
          j.role = 'primary';
          pv.trackedJoints = [j];
          mirrorDbTrackedConfig(data, i, pv.trackedJoints);
        }
      }
    });
  }

  patchFile('lib_seated_good_morning', (data) => {
    const pv = data.poseVariants[0];
    pv.trackedJoints = [deepClone(SEATED_HINGE_PRIMARY_LEFT_HIP)];
    mirrorDbTrackedConfig(data, 0, pv.trackedJoints);
  });

  // Rotation side view: add primary left_hip before shoulder secondary
  const rotSlugs = ['lib_half_kneeling_lift', 'lib_split_stance_rotation', 'lib_thoracic_open_book'];
  for (const slug of rotSlugs) {
    patchFile(slug, (data) => {
      for (let i = 0; i < data.poseVariants.length; i++) {
        const pv = data.poseVariants[i];
        const hasPrimary = pv.trackedJoints?.some((j) => j.role === 'primary');
        if (!hasPrimary) {
          pv.trackedJoints = insertPrimaryFirst(pv.trackedJoints, ROTATION_PRIMARY_LEFT_HIP);
          mirrorDbTrackedConfig(data, i, pv.trackedJoints);
        }
      }
    });
  }

  // Hold anti-rotation — primary uses single range (hold mode)
  patchFile('lib_cable_rotation_anti', (data) => {
    const pv = data.poseVariants[0];
    pv.trackedJoints = [deepClone(ANTI_ROT_PRIMARY_LEFT_HIP), ...pv.trackedJoints.filter((j) => j.role !== 'primary')];
    mirrorDbTrackedConfig(data, 0, pv.trackedJoints);
  });

  // Hollow body: hold with only secondary → primary hip flexion
  patchFile('lib_hollow_body_hold', (data) => {
    const pv = data.poseVariants[0];
    pv.trackedJoints = [deepClone(HOLLOW_HOLD_PRIMARY_LEFT_HIP)];
    mirrorDbTrackedConfig(data, 0, pv.trackedJoints);
  });

  // Roman chair: add primary
  patchFile('lib_roman_chair_back_ext', (data) => {
    const pv = data.poseVariants[0];
    const hasPrimary = pv.trackedJoints?.some((j) => j.role === 'primary');
    if (!hasPrimary) {
      pv.trackedJoints = insertPrimaryFirst(pv.trackedJoints, PRONE_EXTENSION_PRIMARY_LEFT_HIP);
      mirrorDbTrackedConfig(data, 0, pv.trackedJoints);
    }
  });

  // Yoga holds: shoulder secondary only → add primary hip line
  for (const slug of ['lib_yoga_down_dog', 'lib_yoga_up_dog']) {
    patchFile(slug, (data) => {
      const pv = data.poseVariants[0];
      const hasPrimary = pv.trackedJoints?.some((j) => j.role === 'primary');
      if (!hasPrimary) {
        pv.trackedJoints = insertPrimaryFirst(pv.trackedJoints, YOGA_HOLD_PRIMARY_LEFT_HIP);
        mirrorDbTrackedConfig(data, 0, pv.trackedJoints);
      }
    });
  }

  // Shoulder assessment: widen transition zone
  patchFile('assessment_shoulder_mobility', (data) => {
    const pv = data.poseVariants[0];
    const shoulder = pv.trackedJoints.find((j) => j.joint === 'right_shoulder');
    if (shoulder?.downRange?.normal) {
      shoulder.downRange.normal.max = 85;
      shoulder.downRange.normal.min = 35;
    }
    if (shoulder?.downRange?.perfect) {
      shoulder.downRange.perfect.max = 72;
      shoulder.downRange.perfect.min = 42;
    }
    mirrorDbTrackedConfig(data, 0, pv.trackedJoints);
  });

  // Hold-mode variants: ensure primary (not secondary-only placeholder)
  const holdPrimarySlugs = ['lib_neck_retraction', 'lib_seated_neck_rotation', 'lib_wall_posture_reset', 'lib_windmill_stretch'];
  for (const slug of holdPrimarySlugs) {
    patchFile(slug, (data) => {
      for (let i = 0; i < data.poseVariants.length; i++) {
        const pv = data.poseVariants[i];
        const tj = pv.trackedJoints;
        if (!Array.isArray(tj) || tj.length !== 1) continue;
        const j = tj[0];
        if (j?.joint === 'left_hip' && j.role === 'secondary' && j.range && !j.upRange) {
          j.role = 'primary';
          delete j.pairedWith;
          mirrorDbTrackedConfig(data, i, pv.trackedJoints);
        }
      }
    });
  }
}

main();
