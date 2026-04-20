/**
 * Professional pose variants (joints, position checks, feedback) for curated library exercises (`lib_*`).
 * Shapes match `prisma/Exercise-json` so `applyPoseVariantsForExercise` wires templates + assignments like JSON imports.
 */
import type { CuratedExtensionExercise } from './curated-catalog-extension';
import type { SeedPoseVariantJson } from './pose-variant-seed-helper';
import { buildCuratedPoseOverride } from './curated-pose-overrides';
import {
  hipPhasedMessages_squat,
  hipPhasedRanges_squat,
  hipSecondaryTemplateRange,
  kneePhasedMessages_hinge,
  kneePhasedRanges_hinge,
  kneeSecondaryTemplateRange,
  shoulderPhasedMessages_press,
  shoulderPhasedRanges_press,
  shoulderSecondaryTemplateRange,
  spinePhasedMessages_dynamic,
  spinePhasedRanges_dynamic,
  spineSecondaryTemplateRange,
} from './phase-range-builders';

const SIDE: { ar: string; en: string } = { ar: 'زاوية جانبية', en: 'Side view' };
const FRONT: { ar: string; en: string } = { ar: 'زاوية أمامية', en: 'Front view' };
const SUPINE: { ar: string; en: string } = { ar: 'مستلقٍ على الظهر', en: 'Lying on back (side angle)' };
const PRONE: { ar: string; en: string } = { ar: 'مستلقٍ على الوجه', en: 'Lying face down (side angle)' };
const SIDE_LYING: { ar: string; en: string } = { ar: 'مستلقٍ على الجانب', en: 'Side lying' };

/** Pose position codes — must match `pose-positions.ts`. */
const POSE_STANDING_SIDE = 'standing_side';
const POSE_STANDING_FRONT = 'standing_front';
const POSE_SUPINE_SIDE = 'supine_side';
const POSE_PRONE_SIDE = 'prone_side';
const POSE_SIDE_LYING = 'side_lying';

const FB_LIFT = {
  motivational: [
    { ar: 'أداء ممتاز — حافظ على الإيقاع', en: 'Strong work — keep the rhythm' },
    { ar: 'قوي! لا تضحّي بالشكل من أجل السرعة', en: 'Solid! Don’t sacrifice form for speed' },
  ],
  tips: [
    { ar: 'ثبّت القدمين ووزّع الوزن على كعب ووسط القدم.', en: 'Root the feet; load heel and mid-foot evenly.' },
    { ar: 'أي ألم حاد في الركبة أو الظهر — توقف فوراً.', en: 'Stop immediately if you feel sharp knee or back pain.' },
  ],
};

const FB_UPPER = {
  motivational: [
    { ar: 'تحكّم رائع في المدى', en: 'Excellent range control' },
    { ar: 'استمر — الجودة أهم من العدد', en: 'Keep going — quality beats reps' },
  ],
  tips: [
    { ar: 'كتفاك للأسفل والخلف قليلاً (تثبيت لوح الكتف).', en: 'Keep shoulders down and slightly back (scapular set).' },
    { ar: 'لا تفرط في تمديد الرقبة؛ نظرة أمامية قصيرة.', en: 'Avoid craning the neck; keep a short forward gaze.' },
  ],
};

const FB_CORE = {
  motivational: [
    { ar: 'جذع ثابت — ممتاز!', en: 'Stable core — great!' },
    { ar: 'حافظ على الشد دون حبس أنفاس طويل', en: 'Stay braced without long breath holds' },
  ],
  tips: [
    { ar: 'شدّ السرة برفق نحو العمود الفقري.', en: 'Gently draw the navel toward the spine.' },
    { ar: 'زفير على الجهد، شهيق على التحضير.', en: 'Exhale on effort, inhale to reset.' },
  ],
};

const FB_MOBILITY = {
  motivational: [
    { ar: 'تحرّك ببطء داخل نطاق مريح', en: 'Move slowly within a comfortable window' },
    { ar: 'تنفس عميقاً — لا تتجاوز الألم الحاد', en: 'Breathe deeply — stay below sharp pain' },
  ],
  tips: [
    { ar: 'لا تتجاوز 6–8/10 شدة إحساس — ركّز على التمدد وليس القفز.', en: 'Stay around 6–8/10 intensity — smooth stretch, no bouncing.' },
    { ar: 'أبقِ الفك مرتخياً والرقبة جزءاً من الحركة.', en: 'Keep the jaw soft; let the neck move as one unit.' },
  ],
};

function isTricepSlug(ex: CuratedExtensionExercise): boolean {
  return /tricep|pushdown|skull|overhead_tricep/i.test(ex.slug);
}

function isFacePullFamily(ex: CuratedExtensionExercise): boolean {
  return /face_pull|pull_apart|band_pull|rear_delt|prone_y|prone_t|scap/i.test(ex.slug);
}

/** Bilateral squat / squat-dominant (goblet, front squat, box, tempo, overhead squat PVC, etc.) */
function variantSquatPattern(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'knee_over_toe',
          type: 'forward_comparison',
          landmarks: { primary: 'left_knee', secondary: 'left_foot_index' },
          condition: { operator: 'should_not_exceed', threshold: 0.1 },
          activePhases: ['down', 'bottom'],
          errorMessage: {
            ar: 'لا تدع الركبة تتقدم بعيداً عن أصابع القدم.',
            en: 'Don’t let the knee drift far past the toes.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 5,
        },
        {
          id: 'hip_behind_knee',
          type: 'forward_comparison',
          landmarks: { primary: 'left_hip', secondary: 'left_knee' },
          condition: { operator: 'should_exceed', threshold: 0.03 },
          activePhases: ['bottom'],
          errorMessage: {
            ar: 'ادفع الورك للخلف أكثر في أسفل الحركة.',
            en: 'Sit the hips back more at the bottom.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 4,
        },
        {
          id: 'torso_tall',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.11 },
          activePhases: ['down', 'bottom', 'up'],
          errorMessage: {
            ar: 'حافظ على صدر مرتفع وعمود فقري محايد.',
            en: 'Keep chest tall and spine neutral.',
          },
          severity: 'tip',
          cooldownMs: 3000,
          minErrorFrames: 6,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_knee',
          role: 'primary',
          startPose: { min: 120, max: 180 },
          upRange: {
            perfect: { min: 150, max: 180 },
            normal: { min: 130, max: 180 },
          },
          downRange: {
            perfect: { min: 60, max: 92 },
            normal: { min: 50, max: 115 },
            pad: { min: 42, max: 105 },
            warning: { min: 30, max: 42 },
            danger: { min: 0, max: 30 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! وقفة كاملة', en: 'Perfect! Full stand' },
              down: { ar: 'ممتاز! عمق مثالي', en: 'Perfect! Ideal depth' },
            },
            normal: {
              up: { ar: 'جيد — اضبط الاستقامة في الأعلى', en: 'Good — tidy the finish' },
              down: { ar: 'جيد — يمكنك النزول قليلاً أكثر بأمان', en: 'Good — you can go slightly deeper if comfortable' },
            },
            pad: { down: { ar: 'مقبول — زد العمق تدريجياً', en: 'Acceptable — build depth gradually' } },
            warning: { down: { ar: 'تجنب النزول الزائد إن ركبتك تضايقك', en: 'Avoid excess depth if the knee complains' } },
            danger: { down: { ar: 'توقف — عمق غير آمن', en: 'Stop — unsafe depth' } },
          },
          pairedWith: 'right_knee',
        },
        {
          joint: 'right_knee',
          role: 'primary',
          startPose: { min: 120, max: 180 },
          upRange: { perfect: { min: 150, max: 180 }, normal: { min: 130, max: 180 } },
          downRange: {
            perfect: { min: 60, max: 92 },
            normal: { min: 50, max: 115 },
            pad: { min: 42, max: 105 },
            warning: { min: 30, max: 42 },
            danger: { min: 0, max: 30 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! وقفة كاملة', en: 'Perfect! Full stand' },
              down: { ar: 'ممتاز! عمق مثالي', en: 'Perfect! Ideal depth' },
            },
            normal: {
              up: { ar: 'جيد — اضبط الاستقامة في الأعلى', en: 'Good — tidy the finish' },
              down: { ar: 'جيد — يمكنك النزول قليلاً أكثر بأمان', en: 'Good — slightly deeper if comfortable' },
            },
            pad: { down: { ar: 'مقبول — زد العمق تدريجياً', en: 'Acceptable — build depth gradually' } },
            warning: { down: { ar: 'تجنب النزول الزائد إن ركبتك تضايقك', en: 'Avoid excess depth if the knee complains' } },
            danger: { down: { ar: 'توقف — عمق غير آمن', en: 'Stop — unsafe depth' } },
          },
          pairedWith: 'left_knee',
        },
        {
          joint: 'left_hip',
          role: 'secondary',
          startPose: { min: 55, max: 180 },
          range: hipSecondaryTemplateRange(),
          phaseRanges: hipPhasedRanges_squat(),
          phaseStateMessages: hipPhasedMessages_squat(),
          stateMessages: {
            perfect: { ar: 'ورك متزامن مع عمق السكوات', en: 'Hip tracks well with squat depth' },
            warning: { ar: 'لا ترفع الورك مبكراً عن الركبة', en: 'Avoid shooting the hips up early' },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 18 },
          range: spineSecondaryTemplateRange(),
          phaseRanges: spinePhasedRanges_dynamic(),
          phaseStateMessages: spinePhasedMessages_dynamic(),
          stateMessages: {
            perfect: { ar: 'عمود فقري محايد — ممتاز', en: 'Neutral spine — excellent' },
            warning: { ar: 'قلل الانحناء القطني الزائد', en: 'Reduce excessive lumbar flexion' },
            danger: { ar: 'توقف — ظهر مقوّس بشكل خطير', en: 'Stop — severely rounded back' },
          },
        },
      ],
      feedbackMessages: FB_LIFT,
    },
  ];
}

/** Forward lunge / split squat / walking lunge patterns — side view, lead knee + trunk */
function variantLungePattern(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'lead_knee_tracking',
          type: 'forward_comparison',
          landmarks: { primary: 'left_knee', secondary: 'left_ankle' },
          condition: { operator: 'should_not_exceed', threshold: 0.07 },
          activePhases: ['down', 'bottom'],
          errorMessage: {
            ar: 'ركبة الأمام لا تنهار للداخل.',
            en: 'Keep the front knee tracking over the foot.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 5,
        },
        {
          id: 'torso_upright_lunge',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.12 },
          activePhases: ['down', 'up'],
          errorMessage: {
            ar: 'حافظ على الجذع العمودي فوق الورك.',
            en: 'Stay tall over the hips — avoid collapsing forward.',
          },
          severity: 'warning',
          cooldownMs: 2500,
          minErrorFrames: 5,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_knee',
          role: 'primary',
          startPose: { min: 100, max: 180 },
          upRange: { perfect: { min: 150, max: 180 }, normal: { min: 135, max: 180 } },
          downRange: {
            perfect: { min: 70, max: 100 },
            normal: { min: 60, max: 110 },
            pad: { min: 50, max: 120 },
            warning: { min: 35, max: 50 },
            danger: { min: 0, max: 35 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! وقفة مستقيمة', en: 'Perfect! Clean stand' },
              down: { ar: 'ممتاز! زاوية ركبة مثالية', en: 'Perfect! Ideal knee angle' },
            },
            normal: {
              down: { ar: 'جيد — يمكنك النزول قليلاً أكثر إن كان آمناً', en: 'Good — slightly deeper if stable' },
              up: { ar: 'جيد — اضغط بالكامل للأعلى', en: 'Good — drive fully to stand' },
            },
            pad: { down: { ar: 'مقبول — زد العمق تدريجياً', en: 'Acceptable — build depth slowly' } },
            warning: { down: { ar: 'لا تفرط في المدى إن الركبة تتعب', en: 'Ease range if the knee tires' } },
            danger: { down: { ar: 'توقف — ركبة أمامية غير مستقرة', en: 'Stop — unstable front knee' } },
          },
          pairedWith: 'right_knee',
        },
        {
          joint: 'right_knee',
          role: 'primary',
          startPose: { min: 100, max: 180 },
          upRange: { perfect: { min: 150, max: 180 }, normal: { min: 135, max: 180 } },
          downRange: {
            perfect: { min: 70, max: 100 },
            normal: { min: 60, max: 110 },
            pad: { min: 50, max: 120 },
            warning: { min: 35, max: 50 },
            danger: { min: 0, max: 35 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! وقفة مستقيمة', en: 'Perfect! Clean stand' },
              down: { ar: 'ممتاز! زاوية ركبة مثالية', en: 'Perfect! Ideal knee angle' },
            },
            normal: {
              down: { ar: 'جيد — يمكنك النزول قليلاً أكثر إن كان آمناً', en: 'Good — slightly deeper if stable' },
              up: { ar: 'جيد — اضغط بالكامل للأعلى', en: 'Good — drive fully to stand' },
            },
            pad: { down: { ar: 'مقبول — زد العمق تدريجياً', en: 'Acceptable — build depth slowly' } },
            warning: { down: { ar: 'لا تفرط في المدى إن الركبة تتعب', en: 'Ease range if the knee tires' } },
            danger: { down: { ar: 'توقف — ركبة أمامية غير مستقرة', en: 'Stop — unstable front knee' } },
          },
          pairedWith: 'left_knee',
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 18 },
          range: spineSecondaryTemplateRange(),
          phaseRanges: spinePhasedRanges_dynamic(),
          phaseStateMessages: spinePhasedMessages_dynamic(),
          stateMessages: {
            perfect: { ar: 'جذع ثابت ومرفوع', en: 'Torso stable and tall' },
            warning: { ar: 'لا تنهَر للأمام أثناء الاندفاع', en: 'Avoid collapsing forward in the lunge' },
          },
        },
      ],
      feedbackMessages: FB_LIFT,
    },
  ];
}

/** RDL / deadlift / hip hinge — side view, hip hinge ROM + neutral spine check */
function variantHingePattern(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'hinge_neutral_spine',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.09 },
          activePhases: ['up', 'down', 'bottom'],
          errorMessage: {
            ar: 'حافظ على استقامة الظهر — المفصل عند الورك وليس قطني حاد.',
            en: 'Keep the back neutral — hinge at the hips, not a sharp lumbar round.',
          },
          severity: 'error',
          cooldownMs: 2000,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_hip',
          role: 'primary',
          startPose: { min: 160, max: 180 },
          upRange: { perfect: { min: 170, max: 180 }, normal: { min: 165, max: 180 }, pad: { min: 155, max: 180 } },
          downRange: {
            perfect: { min: 78, max: 98 },
            normal: { min: 72, max: 105 },
            pad: { min: 65, max: 115 },
            warning: { min: 48, max: 65 },
            danger: { min: 0, max: 48 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! وقفة كاملة', en: 'Perfect! Full lockout' },
              down: { ar: 'ممتاز! زاوية ورك مثالية', en: 'Perfect! Ideal hinge depth' },
            },
            normal: {
              up: { ar: 'جيد — اضبط الاستقامة في الأعلى', en: 'Good — tidy the top' },
              down: { ar: 'جيد — نزل أكثر إن كان آمناً للأوتار', en: 'Good — go deeper if hamstrings allow' },
            },
            pad: { down: { ar: 'مقبول — زد العمق تدريجياً', en: 'Acceptable — build range slowly' } },
            warning: { down: { ar: 'قلل العمق إن الظهر يفقد الحياد', en: 'Reduce depth if neutrality is lost' } },
            danger: { down: { ar: 'توقف — خطر على أسفل الظهر', en: 'Stop — low-back risk' } },
          },
          pairedWith: 'right_hip',
        },
        {
          joint: 'right_hip',
          role: 'primary',
          startPose: { min: 160, max: 180 },
          upRange: { perfect: { min: 170, max: 180 }, normal: { min: 165, max: 180 }, pad: { min: 155, max: 180 } },
          downRange: {
            perfect: { min: 78, max: 98 },
            normal: { min: 72, max: 105 },
            pad: { min: 65, max: 115 },
            warning: { min: 48, max: 65 },
            danger: { min: 0, max: 48 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! وقفة كاملة', en: 'Perfect! Full lockout' },
              down: { ar: 'ممتاز! زاوية ورك مثالية', en: 'Perfect! Ideal hinge depth' },
            },
            normal: {
              up: { ar: 'جيد — اضبط الاستقامة في الأعلى', en: 'Good — tidy the top' },
              down: { ar: 'جيد — نزل أكثر إن كان آمناً للأوتار', en: 'Good — go deeper if hamstrings allow' },
            },
            pad: { down: { ar: 'مقبول — زد العمق تدريجياً', en: 'Acceptable — build range slowly' } },
            warning: { down: { ar: 'قلل العمق إن الظهر يفقد الحياد', en: 'Reduce depth if neutrality is lost' } },
            danger: { down: { ar: 'توقف — خطر على أسفل الظهر', en: 'Stop — low-back risk' } },
          },
          pairedWith: 'left_hip',
        },
        {
          joint: 'left_knee',
          role: 'secondary',
          startPose: { min: 150, max: 180 },
          range: kneeSecondaryTemplateRange(),
          phaseRanges: kneePhasedRanges_hinge(),
          phaseStateMessages: kneePhasedMessages_hinge(),
          stateMessages: {
            perfect: { ar: 'ركبة ناعمة — ممتاز', en: 'Soft knee — great' },
            warning: { ar: 'تجنب قفل الركبة بالكامل في الروماني', en: 'Avoid fully locking knees in RDL-style hinges' },
          },
        },
      ],
      feedbackMessages: FB_LIFT,
    },
  ];
}

/** Push-up / bench-style horizontal push — side view, elbows + hip line */
function variantPushHorizontalChest(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: PRONE,
      cameraPosition: POSE_PRONE_SIDE,
      positionChecks: [
        {
          id: 'hip_line_push',
          type: 'vertical_comparison',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.09 },
          activePhases: ['down', 'up'],
          errorMessage: {
            ar: 'حافظ على خط مستقيم من الرأس للكعب.',
            en: 'Keep a straight line from head to heels.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_elbow',
          role: 'primary',
          startPose: { min: 150, max: 180 },
          upRange: { perfect: { min: 165, max: 180 }, normal: { min: 158, max: 180 }, pad: { min: 150, max: 180 } },
          downRange: {
            perfect: { min: 48, max: 82 },
            normal: { min: 42, max: 95 },
            pad: { min: 35, max: 110 },
            warning: { min: 28, max: 38 },
            danger: { min: 0, max: 28 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! تمدد كامل في الأعلى', en: 'Perfect! Full extension' },
              down: { ar: 'ممتاز! عمق ضغط مثالي', en: 'Perfect! Ideal press depth' },
            },
            normal: {
              up: { ar: 'جيد — فرد المرفق أكثر', en: 'Good — extend the elbow more' },
              down: { ar: 'جيد — انزل أكثر إن كان آمناً للكتف', en: 'Good — lower more if shoulders tolerate' },
            },
            pad: { down: { ar: 'مقبول — زد العمق تدريجياً', en: 'Acceptable — build depth gradually' } },
            warning: { down: { ar: 'لا تفرط في المدى إن الكتف يضيق', en: 'Ease depth if the shoulder pinches' } },
            danger: { down: { ar: 'توقف — مدى غير آمن', en: 'Stop — unsafe range' } },
          },
          pairedWith: 'right_elbow',
        },
        {
          joint: 'right_elbow',
          role: 'primary',
          startPose: { min: 150, max: 180 },
          upRange: { perfect: { min: 165, max: 180 }, normal: { min: 158, max: 180 }, pad: { min: 150, max: 180 } },
          downRange: {
            perfect: { min: 48, max: 82 },
            normal: { min: 42, max: 95 },
            pad: { min: 35, max: 110 },
            warning: { min: 28, max: 38 },
            danger: { min: 0, max: 28 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! تمدد كامل في الأعلى', en: 'Perfect! Full extension' },
              down: { ar: 'ممتاز! عمق ضغط مثالي', en: 'Perfect! Ideal press depth' },
            },
            normal: {
              up: { ar: 'جيد — فرد المرفق أكثر', en: 'Good — extend the elbow more' },
              down: { ar: 'جيد — انزل أكثر إن كان آمناً للكتف', en: 'Good — lower more if shoulders tolerate' },
            },
            pad: { down: { ar: 'مقبول — زد العمق تدريجياً', en: 'Acceptable — build depth gradually' } },
            warning: { down: { ar: 'لا تفرط في المدى إن الكتف يضيق', en: 'Ease depth if the shoulder pinches' } },
            danger: { down: { ar: 'توقف — مدى غير آمن', en: 'Stop — unsafe range' } },
          },
          pairedWith: 'left_elbow',
        },
        {
          joint: 'left_hip',
          role: 'secondary',
          startPose: { min: 158, max: 180 },
          range: {
            perfect: { min: 170, max: 180 },
            normal: { min: 162, max: 180 },
            warning: { min: 138, max: 162 },
            danger: { min: 0, max: 138 },
          },
          stateMessages: {
            perfect: { ar: 'جذع ثابت — ممتاز', en: 'Stable trunk — great' },
            warning: { ar: 'لا ترفع الوسط للأعلى (pike)', en: 'Don’t pike the hips' },
            danger: { ar: 'وسط منخفض جداً — شد البطن', en: 'Hips sagging — brace harder' },
          },
        },
        {
          joint: 'right_hip',
          role: 'secondary',
          startPose: { min: 158, max: 180 },
          range: {
            perfect: { min: 170, max: 180 },
            normal: { min: 162, max: 180 },
            warning: { min: 138, max: 162 },
            danger: { min: 0, max: 138 },
          },
          stateMessages: {
            perfect: { ar: 'خط الوركين متوازن', en: 'Hip line stays even' },
            warning: { ar: 'لا تدع وركاً يرتفع أكثر من الآخر', en: 'Do not let one hip hike higher than the other' },
          },
        },
      ],
      feedbackMessages: FB_UPPER,
    },
  ];
}

function variantPullHorizontalRow(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'elbow_tucked_row',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_elbow', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.1 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'اسحب المرفق نحو الورك — لا تفتح الذراع للجانب.',
            en: 'Pull elbow toward the hip — avoid flaring the arm.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_elbow',
          role: 'primary',
          invertIndicator: true,
          startPose: { min: 150, max: 180 },
          upRange: { perfect: { min: 165, max: 180 }, normal: { min: 158, max: 180 }, pad: { min: 148, max: 180 } },
          downRange: {
            perfect: { min: 35, max: 58 },
            normal: { min: 28, max: 68 },
            pad: { min: 18, max: 78 },
            warning: { min: 10, max: 22 },
            danger: { min: 0, max: 10 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! ذراع ممتدة', en: 'Perfect! Arm long' },
              down: { ar: 'ممتاز! شد قوي للصف', en: 'Perfect! Strong pull finish' },
            },
            normal: {
              up: { ar: 'جيد — فرد أكثر في الأسفل', en: 'Good — reach longer at bottom' },
              down: { ar: 'جيد — اقترب من الورك أكثر', en: 'Good — pull closer to the hip' },
            },
            pad: { down: { ar: 'مقبول — زد المدى تدريجياً', en: 'Acceptable — build range slowly' } },
            warning: { down: { ar: 'لا تدور الجذع للحصول على مدى زائف', en: 'Don’t rotate the trunk for fake range' } },
          },
          pairedWith: 'right_elbow',
        },
        {
          joint: 'right_elbow',
          role: 'primary',
          invertIndicator: true,
          startPose: { min: 150, max: 180 },
          upRange: { perfect: { min: 165, max: 180 }, normal: { min: 158, max: 180 }, pad: { min: 148, max: 180 } },
          downRange: {
            perfect: { min: 35, max: 58 },
            normal: { min: 28, max: 68 },
            pad: { min: 18, max: 78 },
            warning: { min: 10, max: 22 },
            danger: { min: 0, max: 10 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! ذراع ممتدة', en: 'Perfect! Arm long' },
              down: { ar: 'ممتاز! شد قوي للصف', en: 'Perfect! Strong pull finish' },
            },
            normal: {
              up: { ar: 'جيد — فرد أكثر في الأسفل', en: 'Good — reach longer at bottom' },
              down: { ar: 'جيد — اقترب من الورك أكثر', en: 'Good — pull closer to the hip' },
            },
            pad: { down: { ar: 'مقبول — زد المدى تدريجياً', en: 'Acceptable — build range slowly' } },
            warning: { down: { ar: 'لا تدور الجذع للحصول على مدى زائف', en: 'Don’t rotate the trunk for fake range' } },
          },
          pairedWith: 'left_elbow',
        },
        {
          joint: 'left_shoulder',
          role: 'secondary',
          startPose: { min: 20, max: 55 },
          range: {
            perfect: { min: 28, max: 48 },
            normal: { min: 22, max: 55 },
            warning: { min: 55, max: 70 },
          },
          stateMessages: {
            perfect: { ar: 'كتف منخفض ومستقر', en: 'Shoulder low and stable' },
            warning: { ar: 'تجنب رفع الكتف نحو الأذن', en: 'Avoid hiking the shoulder to the ear' },
          },
        },
        {
          joint: 'right_shoulder',
          role: 'secondary',
          startPose: { min: 20, max: 55 },
          range: {
            perfect: { min: 28, max: 48 },
            normal: { min: 22, max: 55 },
            warning: { min: 55, max: 70 },
          },
          stateMessages: {
            perfect: { ar: 'كتف أيمن منخفض ومستقر', en: 'Right shoulder low and stable' },
            warning: { ar: 'تجنب رفع الكتف نحو الأذن', en: 'Avoid hiking the shoulder to the ear' },
          },
        },
      ],
      feedbackMessages: FB_UPPER,
    },
  ];
}

function variantFacePullStyle(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'high_elbow_line',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_elbow', secondary: 'left_shoulder' },
          condition: { operator: 'approximately_equal', threshold: 0.14 },
          activePhases: ['down'],
          errorMessage: {
            ar: 'ارفع المرفق قليلاً فوق خط المعصم — سحب وجهي حقيقي.',
            en: 'Finish with elbow above wrist line — classic face-pull finish.',
          },
          severity: 'tip',
          cooldownMs: 2800,
          minErrorFrames: 5,
        },
        {
          id: 'wrist_follows_elbow',
          type: 'forward_comparison',
          landmarks: { primary: 'left_wrist', secondary: 'left_elbow' },
          condition: { operator: 'should_exceed', threshold: 0.03 },
          activePhases: ['down'],
          errorMessage: {
            ar: 'اسحب نحو الأنف/الجبهة بخط مستقيم.',
            en: 'Pull toward nose/forehead in a straight line.',
          },
          severity: 'warning',
          cooldownMs: 2400,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_elbow',
          role: 'primary',
          invertIndicator: true,
          startPose: { min: 140, max: 180 },
          upRange: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 }, pad: { min: 140, max: 180 } },
          downRange: {
            perfect: { min: 40, max: 72 },
            normal: { min: 32, max: 85 },
            pad: { min: 22, max: 95 },
            warning: { min: 12, max: 28 },
            danger: { min: 0, max: 12 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! بداية طويلة', en: 'Perfect! Long start' },
              down: { ar: 'ممتاز! شد خلفي مثالي', en: 'Perfect! Strong rear-delt finish' },
            },
            normal: {
              up: { ar: 'جيد — ابدأ بذراع أطول', en: 'Good — start longer' },
              down: { ar: 'جيد — اسحب أقرب للوجه', en: 'Good — pull closer to face' },
            },
            pad: { down: { ar: 'مقبول — زد المدى تدريجياً', en: 'Acceptable — build ROM slowly' } },
            warning: { down: { ar: 'لا تدور الجذع للخلف', en: 'Don’t yank with the torso' } },
          },
          pairedWith: 'right_elbow',
        },
        {
          joint: 'right_elbow',
          role: 'primary',
          invertIndicator: true,
          startPose: { min: 140, max: 180 },
          upRange: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 }, pad: { min: 140, max: 180 } },
          downRange: {
            perfect: { min: 40, max: 72 },
            normal: { min: 32, max: 85 },
            pad: { min: 22, max: 95 },
            warning: { min: 12, max: 28 },
            danger: { min: 0, max: 12 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! بداية طويلة', en: 'Perfect! Long start' },
              down: { ar: 'ممتاز! شد خلفي مثالي', en: 'Perfect! Strong rear-delt finish' },
            },
            normal: {
              up: { ar: 'جيد — ابدأ بذراع أطول', en: 'Good — start longer' },
              down: { ar: 'جيد — اسحب أقرب للوجه', en: 'Good — pull closer to face' },
            },
            pad: { down: { ar: 'مقبول — زد المدى تدريجياً', en: 'Acceptable — build ROM slowly' } },
            warning: { down: { ar: 'لا تدور الجذع للخلف', en: 'Don’t yank with the torso' } },
          },
          pairedWith: 'left_elbow',
        },
      ],
      feedbackMessages: FB_UPPER,
    },
  ];
}

function variantArmCurl(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'elbow_stays_home',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_elbow', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.09 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'ثبّت المرفق بجانب الجذع.',
            en: 'Anchor the elbow near the torso.',
          },
          severity: 'warning',
          cooldownMs: 2000,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_elbow',
          role: 'primary',
          invertIndicator: true,
          startPose: { min: 150, max: 180 },
          upRange: { perfect: { min: 165, max: 180 }, normal: { min: 158, max: 180 }, pad: { min: 148, max: 180 } },
          downRange: {
            perfect: { min: 22, max: 42 },
            normal: { min: 15, max: 52 },
            pad: { min: 10, max: 62 },
            warning: { min: 5, max: 18 },
            danger: { min: 0, max: 5 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! ذراع مفرودة', en: 'Perfect! Arm long' },
              down: { ar: 'ممتاز! ثني مثالي', en: 'Perfect! Strong curl' },
            },
            normal: {
              up: { ar: 'جيد — فرد أكثر في الأسفل', en: 'Good — extend more at bottom' },
              down: { ar: 'جيد — ارفع أكثر نحو الكتف', en: 'Good — lift higher toward shoulder' },
            },
            pad: { down: { ar: 'مقبول — زد المدى تدريجياً', en: 'Acceptable — build ROM slowly' } },
            warning: { down: { ar: 'لا تتأرجح بالظهر', en: 'Don’t swing the torso' } },
          },
          pairedWith: 'right_elbow',
        },
        {
          joint: 'right_elbow',
          role: 'primary',
          invertIndicator: true,
          startPose: { min: 150, max: 180 },
          upRange: { perfect: { min: 165, max: 180 }, normal: { min: 158, max: 180 }, pad: { min: 148, max: 180 } },
          downRange: {
            perfect: { min: 22, max: 42 },
            normal: { min: 15, max: 52 },
            pad: { min: 10, max: 62 },
            warning: { min: 5, max: 18 },
            danger: { min: 0, max: 5 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! ذراع مفرودة', en: 'Perfect! Arm long' },
              down: { ar: 'ممتاز! ثني مثالي', en: 'Perfect! Strong curl' },
            },
            normal: {
              up: { ar: 'جيد — فرد أكثر في الأسفل', en: 'Good — extend more at bottom' },
              down: { ar: 'جيد — ارفع أكثر نحو الكتف', en: 'Good — lift higher toward shoulder' },
            },
            pad: { down: { ar: 'مقبول — زد المدى تدريجياً', en: 'Acceptable — build ROM slowly' } },
            warning: { down: { ar: 'لا تتأرجح بالظهر', en: 'Don’t swing the torso' } },
          },
          pairedWith: 'left_elbow',
        },
      ],
      feedbackMessages: FB_UPPER,
    },
  ];
}

function variantArmTricepExtension(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'upper_arm_stack',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_elbow' },
          condition: { operator: 'approximately_equal', threshold: 0.11 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'ثبّت العضد — الحركة من المرفق فقط.',
            en: 'Fix the upper arm — move only at the elbow.',
          },
          severity: 'warning',
          cooldownMs: 2000,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_elbow',
          role: 'primary',
          startPose: { min: 85, max: 110 },
          upRange: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 }, pad: { min: 140, max: 180 } },
          downRange: {
            perfect: { min: 78, max: 98 },
            normal: { min: 70, max: 105 },
            pad: { min: 60, max: 115 },
            warning: { min: 45, max: 60 },
            danger: { min: 0, max: 45 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! تمدد كامل', en: 'Perfect! Full extension' },
              down: { ar: 'ممتاز! تحكم مثالي', en: 'Perfect! Ideal control' },
            },
            normal: {
              up: { ar: 'جيد — فرد المرفق أكثر', en: 'Good — extend more' },
              down: { ar: 'جيد — اضبط النزول', en: 'Good — tidy the lowering' },
            },
            pad: { down: { ar: 'مقبول — زد المدى تدريجياً', en: 'Acceptable — build ROM slowly' } },
            warning: { down: { ar: 'لا تفرط إن الكوع يؤلم', en: 'Ease ROM if elbow aches' } },
            danger: { down: { ar: 'توقف — ألم حاد', en: 'Stop — sharp pain' } },
          },
          pairedWith: 'right_elbow',
        },
        {
          joint: 'right_elbow',
          role: 'primary',
          startPose: { min: 85, max: 110 },
          upRange: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 }, pad: { min: 140, max: 180 } },
          downRange: {
            perfect: { min: 78, max: 98 },
            normal: { min: 70, max: 105 },
            pad: { min: 60, max: 115 },
            warning: { min: 45, max: 60 },
            danger: { min: 0, max: 45 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! تمدد كامل', en: 'Perfect! Full extension' },
              down: { ar: 'ممتاز! تحكم مثالي', en: 'Perfect! Ideal control' },
            },
            normal: {
              up: { ar: 'جيد — فرد المرفق أكثر', en: 'Good — extend more' },
              down: { ar: 'جيد — اضبط النزول', en: 'Good — tidy the lowering' },
            },
            pad: { down: { ar: 'مقبول — زد المدى تدريجياً', en: 'Acceptable — build ROM slowly' } },
            warning: { down: { ar: 'لا تفرط إن الكوع يؤلم', en: 'Ease ROM if elbow aches' } },
            danger: { down: { ar: 'توقف — ألم حاد', en: 'Stop — sharp pain' } },
          },
          pairedWith: 'left_elbow',
        },
      ],
      feedbackMessages: FB_UPPER,
    },
  ];
}

function variantVerticalPush(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: FRONT,
      cameraPosition: POSE_STANDING_FRONT,
      positionChecks: [
        {
          id: 'wrist_level_press',
          type: 'horizontal_alignment',
          landmarks: { primary: 'left_wrist', secondary: 'right_wrist' },
          condition: { operator: 'approximately_equal', threshold: 0.09 },
          activePhases: ['up', 'top'],
          errorMessage: {
            ar: 'اجعل المعصمين في مستوى واحد فوق الرأس.',
            en: 'Keep wrists level overhead.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 4,
        },
        {
          id: 'stack_over_shoulder',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_wrist', secondary: 'left_shoulder' },
          condition: { operator: 'approximately_equal', threshold: 0.11 },
          activePhases: ['top'],
          errorMessage: {
            ar: 'اضغط فوق منتصف الكتف.',
            en: 'Stack over the mid-shoulder.',
          },
          severity: 'tip',
          cooldownMs: 2800,
          minErrorFrames: 5,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_elbow',
          role: 'primary',
          startPose: { min: 70, max: 115 },
          upRange: { perfect: { min: 165, max: 180 }, normal: { min: 155, max: 180 }, pad: { min: 145, max: 180 } },
          downRange: {
            perfect: { min: 82, max: 98 },
            normal: { min: 75, max: 105 },
            pad: { min: 65, max: 115 },
            warning: { min: 55, max: 72 },
            danger: { min: 0, max: 55 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! تمدد كامل فوق الرأس', en: 'Perfect! Full lockout' },
              down: { ar: 'ممتاز! مستوى كتف مثالي', en: 'Perfect! Clean shoulder height' },
            },
            normal: {
              up: { ar: 'جيد — فرد أكثر في الأعلى', en: 'Good — extend more at top' },
              down: { ar: 'جيد — انزل لمستوى الكتف', en: 'Good — lower to shoulder level' },
            },
            pad: { up: { ar: 'مقبول — زد التمدد تدريجياً', en: 'Acceptable — build lockout slowly' } },
            warning: { up: { ar: 'لا تفرط في تمديد قطني', en: 'Avoid excessive lumbar extension' } },
          },
          pairedWith: 'right_elbow',
        },
        {
          joint: 'right_elbow',
          role: 'primary',
          startPose: { min: 70, max: 115 },
          upRange: { perfect: { min: 165, max: 180 }, normal: { min: 155, max: 180 }, pad: { min: 145, max: 180 } },
          downRange: {
            perfect: { min: 82, max: 98 },
            normal: { min: 75, max: 105 },
            pad: { min: 65, max: 115 },
            warning: { min: 55, max: 72 },
            danger: { min: 0, max: 55 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! تمدد كامل فوق الرأس', en: 'Perfect! Full lockout' },
              down: { ar: 'ممتاز! مستوى كتف مثالي', en: 'Perfect! Clean shoulder height' },
            },
            normal: {
              up: { ar: 'جيد — فرد أكثر في الأعلى', en: 'Good — extend more at top' },
              down: { ar: 'جيد — انزل لمستوى الكتف', en: 'Good — lower to shoulder level' },
            },
            pad: { up: { ar: 'مقبول — زد التمدد تدريجياً', en: 'Acceptable — build lockout slowly' } },
            warning: { up: { ar: 'لا تفرط في تمديد قطني', en: 'Avoid excessive lumbar extension' } },
          },
          pairedWith: 'left_elbow',
        },
        {
          joint: 'left_shoulder',
          role: 'secondary',
          startPose: { min: 70, max: 180 },
          range: shoulderSecondaryTemplateRange(),
          phaseRanges: shoulderPhasedRanges_press(),
          phaseStateMessages: shoulderPhasedMessages_press(),
          stateMessages: {
            perfect: { ar: 'كتف يتبع خط الضغط العمودي', en: 'Shoulder tracks the vertical press line' },
            warning: { ar: 'لا تدع الكتفين يرتفعان نحو الأذنين', en: 'Do not shrug the shoulders toward the ears' },
          },
        },
      ],
      feedbackMessages: FB_UPPER,
    },
  ];
}

function variantVerticalPull(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: FRONT,
      cameraPosition: POSE_STANDING_FRONT,
      positionChecks: [
        {
          id: 'shoulder_depression',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_elbow' },
          condition: { operator: 'approximately_equal', threshold: 0.12 },
          activePhases: ['down'],
          errorMessage: {
            ar: 'ابدأ بكتفين منخفضين.',
            en: 'Start with shoulders down.',
          },
          severity: 'warning',
          cooldownMs: 2400,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_elbow',
          role: 'primary',
          invertIndicator: true,
          startPose: { min: 155, max: 180 },
          upRange: { perfect: { min: 165, max: 180 }, normal: { min: 155, max: 180 }, pad: { min: 145, max: 180 } },
          downRange: {
            perfect: { min: 35, max: 62 },
            normal: { min: 28, max: 75 },
            pad: { min: 18, max: 88 },
            warning: { min: 10, max: 22 },
            danger: { min: 0, max: 10 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! تمدد كامل', en: 'Perfect! Long hang' },
              down: { ar: 'ممتاز! صدر للعارضة', en: 'Perfect! Chest-to-bar quality' },
            },
            normal: {
              up: { ar: 'جيد — فرد أكثر في الأسفل', en: 'Good — reach longer at bottom' },
              down: { ar: 'جيد — اسحب أعلى إن أمكن', en: 'Good — pull higher if able' },
            },
            pad: { down: { ar: 'مقبول — زد المدى تدريجياً', en: 'Acceptable — build height slowly' } },
            warning: { down: { ar: 'لا تدور الكتفين للأمام زائداً', en: 'Don’t roll shoulders too far forward' } },
          },
          pairedWith: 'right_elbow',
        },
        {
          joint: 'right_elbow',
          role: 'primary',
          invertIndicator: true,
          startPose: { min: 155, max: 180 },
          upRange: { perfect: { min: 165, max: 180 }, normal: { min: 155, max: 180 }, pad: { min: 145, max: 180 } },
          downRange: {
            perfect: { min: 35, max: 62 },
            normal: { min: 28, max: 75 },
            pad: { min: 18, max: 88 },
            warning: { min: 10, max: 22 },
            danger: { min: 0, max: 10 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! تمدد كامل', en: 'Perfect! Long hang' },
              down: { ar: 'ممتاز! صدر للعارضة', en: 'Perfect! Chest-to-bar quality' },
            },
            normal: {
              up: { ar: 'جيد — فرد أكثر في الأسفل', en: 'Good — reach longer at bottom' },
              down: { ar: 'جيد — اسحب أعلى إن أمكن', en: 'Good — pull higher if able' },
            },
            pad: { down: { ar: 'مقبول — زد المدى تدريجياً', en: 'Acceptable — build height slowly' } },
            warning: { down: { ar: 'لا تدور الكتفين للأمام زائداً', en: 'Don’t roll shoulders too far forward' } },
          },
          pairedWith: 'left_elbow',
        },
      ],
      feedbackMessages: FB_UPPER,
    },
  ];
}

/** Plank / hollow / anti-extension holds */
function variantCoreHoldAntiExtension(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: PRONE,
      cameraPosition: POSE_PRONE_SIDE,
      positionChecks: [
        {
          id: 'shoulder_hip_line',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.08 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'حافظ على خط مستقيم من الكتف للورك.',
            en: 'Keep shoulder-to-hip line straight.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 5,
        },
        {
          id: 'hip_sag',
          type: 'vertical_comparison',
          landmarks: { primary: 'left_hip', secondary: 'left_ankle' },
          condition: { operator: 'approximately_equal', threshold: 0.1 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'لا تدع الوسط ينخفض — شد البطن.',
            en: 'Don’t let hips sag — brace the core.',
          },
          severity: 'error',
          cooldownMs: 2200,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_hip',
          role: 'primary',
          startPose: { min: 150, max: 180 },
          range: {
            perfect: { min: 165, max: 180 },
            normal: { min: 158, max: 180 },
            pad: { min: 148, max: 180 },
            warning: { min: 138, max: 148 },
            danger: { min: 0, max: 138 },
          },
          stateMessages: {
            perfect: { ar: 'جذع ثابت — ممتاز', en: 'Stable line — excellent' },
            normal: { ar: 'جيد — راقب الوسط قليلاً', en: 'Good — watch hip height' },
            pad: { ar: 'مقبول — شدّ أكثر', en: 'Acceptable — brace harder' },
            warning: { ar: 'وسط منخفض — شد البطن', en: 'Hips sag — engage core' },
            danger: { ar: 'توقف — انهيار وضعية', en: 'Stop — form collapse' },
          },
        },
        {
          joint: 'left_shoulder',
          role: 'secondary',
          startPose: { min: 70, max: 110 },
          range: {
            perfect: { min: 80, max: 100 },
            normal: { min: 72, max: 110 },
            warning: { min: 110, max: 125 },
          },
          stateMessages: {
            perfect: { ar: 'كتف فوق المرفق — ممتاز', en: 'Shoulder stacked over elbow' },
            warning: { ar: 'لا ترفع الأرداف (pike)', en: 'Avoid piking the hips' },
          },
        },
        {
          joint: 'left_knee',
          role: 'secondary',
          startPose: { min: 160, max: 180 },
          range: {
            perfect: { min: 170, max: 180 },
            normal: { min: 162, max: 180 },
            warning: { min: 140, max: 162 },
          },
          stateMessages: {
            perfect: { ar: 'ركبتان مفرودتان تقريباً', en: 'Legs nearly straight' },
            warning: { ar: 'افرد الركبتين أكثر في البلانك', en: 'Straighten knees more in plank' },
          },
        },
      ],
      feedbackMessages: FB_CORE,
    },
  ];
}

/** Side plank / Copenhagen — anti-lateral */
function variantCoreHoldAntiLateral(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE_LYING,
      cameraPosition: POSE_SIDE_LYING,
      positionChecks: [
        {
          id: 'hip_stack_side',
          type: 'horizontal_alignment',
          landmarks: { primary: 'left_hip', secondary: 'right_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.12 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'حافظ على الوركين في خط جانبي واحد.',
            en: 'Keep hips stacked in one vertical line.',
          },
          severity: 'warning',
          cooldownMs: 2500,
          minErrorFrames: 5,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_hip',
          role: 'primary',
          startPose: { min: 150, max: 180 },
          range: {
            perfect: { min: 165, max: 180 },
            normal: { min: 155, max: 180 },
            pad: { min: 145, max: 180 },
            warning: { min: 130, max: 145 },
            danger: { min: 0, max: 130 },
          },
          stateMessages: {
            perfect: { ar: 'خط جانبي ثابت — ممتاز', en: 'Side line locked — great' },
            warning: { ar: 'لا تدع الوركين ينزلقان للأمام/الخلف', en: 'Don’t let hips drift forward/back' },
            danger: { ar: 'توقف — فقدان الاستقرار', en: 'Stop — stability lost' },
          },
        },
        {
          joint: 'left_shoulder',
          role: 'secondary',
          startPose: { min: 70, max: 115 },
          range: {
            perfect: { min: 85, max: 105 },
            normal: { min: 78, max: 115 },
            warning: { min: 115, max: 130 },
          },
          stateMessages: {
            perfect: { ar: 'كتف فوق المرفق', en: 'Shoulder over elbow' },
            warning: { ar: 'ادفع الأرض بقوة بالساعد', en: 'Press the floor firmly through forearm' },
          },
        },
      ],
      feedbackMessages: FB_CORE,
    },
  ];
}

/** Crunch / trunk flexion up-down */
function variantCoreTrunkFlexion(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: SUPINE,
      cameraPosition: POSE_SUPINE_SIDE,
      positionChecks: [
        {
          id: 'neck_neutral_crunch',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.14 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'ارفع الصدر والكتفين — لا تسحب الرقبة باليدين.',
            en: 'Lift chest/shoulders — don’t yank the neck with hands.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'spine',
          role: 'primary',
          startPose: { min: 0, max: 22 },
          upRange: {
            perfect: { min: 38, max: 62 },
            normal: { min: 32, max: 65 },
            pad: { min: 30, max: 70 },
            warning: { min: 70, max: 85 },
            danger: { min: 85, max: 100 },
          },
          downRange: {
            perfect: { min: 0, max: 16 },
            normal: { min: 0, max: 22 },
            pad: { min: 0, max: 26 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! كرانش مضبوط', en: 'Perfect! Controlled crunch' },
              down: { ar: 'ممتاز! عودة محايدة', en: 'Perfect! Clean return' },
            },
            normal: {
              up: { ar: 'جيد — ارفع الكتفين أكثر قليلاً', en: 'Good — lift shoulders a bit more' },
              down: { ar: 'جيد — تحكم في النزول', en: 'Good — control the lowering' },
            },
            pad: { up: { ar: 'مقبول — زد المدى تدريجياً', en: 'Acceptable — build ROM slowly' } },
            warning: { up: { ar: 'هذا كرانش وليس sit-up كامل', en: 'Crunch range, not a full sit-up' } },
            danger: { up: { ar: 'توقف — شد على الرقبة', en: 'Stop — pulling on neck' } },
          },
        },
        {
          joint: 'left_knee',
          role: 'secondary',
          startPose: { min: 80, max: 115 },
          range: {
            perfect: { min: 85, max: 108 },
            normal: { min: 78, max: 115 },
            warning: { min: 115, max: 130 },
          },
          stateMessages: {
            perfect: { ar: 'ركبتان مثنيتان بثبات', en: 'Knees bent and steady' },
            warning: { ar: 'لا تدفع الركبتين للأمام والخلف', en: 'Don’t rock knees forward/back' },
          },
        },
      ],
      feedbackMessages: FB_CORE,
    },
  ];
}

/** Back extension hold (superman-style) */
function variantBackExtensionHold(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: PRONE,
      cameraPosition: POSE_PRONE_SIDE,
      positionChecks: [
        {
          id: 'spine_extension_cap',
          type: 'vertical_comparison',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'should_not_exceed', threshold: 0.12 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'ارفع الصدر قليلاً فقط — لا تفرط في انحناء الظهر القطني.',
            en: 'Lift chest modestly — avoid excessive lumbar hyperextension.',
          },
          severity: 'warning',
          cooldownMs: 2600,
          minErrorFrames: 5,
        },
      ],
      trackedJoints: [
        {
          joint: 'spine',
          role: 'primary',
          startPose: { min: 0, max: 25 },
          range: {
            perfect: { min: 18, max: 42 },
            normal: { min: 12, max: 48 },
            pad: { min: 8, max: 55 },
            warning: { min: 48, max: 62 },
            danger: { min: 62, max: 90 },
          },
          stateMessages: {
            perfect: { ar: 'تمدد ظهري مضبوط', en: 'Controlled back extension' },
            warning: { ar: 'قلل الارتفاع إن الظهر يضيق', en: 'Lower height if back pinches' },
            danger: { ar: 'توقف — ألم حاد', en: 'Stop — sharp pain' },
          },
        },
        {
          joint: 'left_hip',
          role: 'secondary',
          startPose: { min: 150, max: 180 },
          range: {
            perfect: { min: 165, max: 180 },
            normal: { min: 158, max: 180 },
            warning: { min: 140, max: 158 },
          },
          stateMessages: {
            perfect: { ar: 'وركان مرفوعان عن الأرض', en: 'Thighs lifted off floor' },
            warning: { ar: 'لا تضغط الرقبة للخلف', en: 'Don’t crank the neck back' },
          },
        },
      ],
      feedbackMessages: FB_CORE,
    },
  ];
}

/**
 * Final fallback for MOBILITY_DRILL when no curated override matches.
 * Intentionally neutral — specific drills use overrides in `curated-pose-overrides.ts`.
 */
function variantMobilityDrill(ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  // Generic neutral fallback. Spine becomes the (loose) primary so the Admin schema
  // requirement of "at least one primary" is satisfied; behaviour mirrors a hold.
  const isHold = ex.countingMethodCode === 'hold';
  const primary = isHold
    ? {
        joint: 'spine',
        role: 'primary' as const,
        startPose: { min: 0, max: 40 },
        range: {
          perfect: { min: 0, max: 22 },
          normal: { min: 0, max: 30 },
          pad: { min: 0, max: 40 },
          warning: { min: 40, max: 60 },
        },
        stateMessages: {
          perfect: { ar: 'وضع ثابت داخل نطاق مريح', en: 'Hold within a comfortable range' },
          warning: { ar: 'قلل المدى إن ظهرك لا يتحمل', en: 'Reduce range if your back does not tolerate it' },
        },
      }
    : {
        joint: 'spine',
        role: 'primary' as const,
        startPose: { min: 0, max: 40 },
        upRange: {
          perfect: { min: 28, max: 60 },
          normal: { min: 22, max: 70 },
          pad: { min: 18, max: 80 },
        },
        downRange: {
          perfect: { min: 0, max: 14 },
          normal: { min: 0, max: 16 },
          pad: { min: 0, max: 16 },
        },
        stateMessages: {
          perfect: {
            up: { ar: 'وصول جيد داخل نطاق مريح', en: 'Good reach within a comfortable range' },
            down: { ar: 'عودة محايدة منظمة', en: 'Organized neutral return' },
          },
          warning: {
            up: { ar: 'قلل المدى إن ظهرك لا يتحمل', en: 'Reduce range if your back does not tolerate it' },
          },
        },
      };
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [],
      trackedJoints: [primary],
      feedbackMessages: FB_MOBILITY,
    },
  ];
}

/** March / skater / gait */
function variantGait(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'trunk_quiet_gait',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.11 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'حافظ على جذع مستقر — الحركة من الورك.',
            en: 'Keep trunk quiet — move from the hip.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_hip',
          role: 'primary',
          startPose: { min: 140, max: 180 },
          upRange: { perfect: { min: 160, max: 180 }, normal: { min: 152, max: 180 }, pad: { min: 148, max: 180 } },
          downRange: {
            perfect: { min: 95, max: 125 },
            normal: { min: 85, max: 135 },
            pad: { min: 75, max: 142 },
            warning: { min: 60, max: 75 },
            danger: { min: 0, max: 60 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! ورك مرتفع', en: 'Perfect! High hip drive' },
              down: { ar: 'ممتاز! خطوة نظيفة', en: 'Perfect! Clean step' },
            },
            normal: {
              up: { ar: 'جيد — ارفع الركبة أكثر', en: 'Good — lift knee higher' },
              down: { ar: 'جيد — اضبط الهبوط', en: 'Good — control landing' },
            },
            pad: { down: { ar: 'مقبول — زد السرعة تدريجياً', en: 'Acceptable — build speed slowly' } },
            warning: { down: { ar: 'لا تهبط بقسوة على الركبة', en: 'Don’t crash down on the knee' } },
          },
          pairedWith: 'right_hip',
        },
        {
          joint: 'right_hip',
          role: 'primary',
          startPose: { min: 140, max: 180 },
          upRange: { perfect: { min: 160, max: 180 }, normal: { min: 152, max: 180 }, pad: { min: 148, max: 180 } },
          downRange: {
            perfect: { min: 95, max: 125 },
            normal: { min: 85, max: 135 },
            pad: { min: 75, max: 142 },
            warning: { min: 60, max: 75 },
            danger: { min: 0, max: 60 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! ورك مرتفع', en: 'Perfect! High hip drive' },
              down: { ar: 'ممتاز! خطوة نظيفة', en: 'Perfect! Clean step' },
            },
            normal: {
              up: { ar: 'جيد — ارفع الركبة أكثر', en: 'Good — lift knee higher' },
              down: { ar: 'جيد — اضبط الهبوط', en: 'Good — control landing' },
            },
            pad: { down: { ar: 'مقبول — زد السرعة تدريجياً', en: 'Acceptable — build speed slowly' } },
            warning: { down: { ar: 'لا تهبط بقسوة على الركبة', en: 'Don’t crash down on the knee' } },
          },
          pairedWith: 'left_hip',
        },
      ],
      feedbackMessages: FB_LIFT,
    },
  ];
}

/** Box jump / squat jump / bounds */
function variantJumpLand(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: FRONT,
      cameraPosition: POSE_STANDING_FRONT,
      positionChecks: [
        {
          id: 'soft_knee_landing',
          type: 'forward_comparison',
          landmarks: { primary: 'left_knee', secondary: 'left_ankle' },
          condition: { operator: 'should_not_exceed', threshold: 0.08 },
          activePhases: ['down', 'bottom'],
          errorMessage: {
            ar: 'اهبط بركبة تتبع القدم — لا تنهار للداخل.',
            en: 'Land with knee tracking over the foot — no collapse inward.',
          },
          severity: 'error',
          cooldownMs: 2000,
          minErrorFrames: 4,
        },
        {
          id: 'hip_flex_landing',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.12 },
          activePhases: ['bottom'],
          errorMessage: {
            ar: 'امتصاص الهبوط بالورك — لا تبقَ منتصباً بالكامل.',
            en: 'Absorb landing with hips — don’t stay fully upright if depth is needed.',
          },
          severity: 'tip',
          cooldownMs: 2800,
          minErrorFrames: 5,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_knee',
          role: 'primary',
          startPose: { min: 140, max: 180 },
          upRange: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 }, pad: { min: 140, max: 180 } },
          downRange: {
            perfect: { min: 55, max: 95 },
            normal: { min: 45, max: 105 },
            pad: { min: 35, max: 115 },
            warning: { min: 25, max: 38 },
            danger: { min: 0, max: 25 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! انطلاقة قوية', en: 'Perfect! Powerful takeoff' },
              down: { ar: 'ممتاز! هبوط ممتص', en: 'Perfect! Soft landing' },
            },
            normal: {
              up: { ar: 'جيد — اضغط أكثر في الانطلاق', en: 'Good — drive harder on takeoff' },
              down: { ar: 'جيد — ثنِ الركبتين أكثر عند الهبوط', en: 'Good — bend knees more on landing' },
            },
            pad: { down: { ar: 'مقبول — قلل الارتفاع تدريجياً', en: 'Acceptable — lower box height gradually' } },
            warning: { down: { ar: 'هبوط صلب — خفف الارتفاع', en: 'Hard landing — reduce height' } },
            danger: { down: { ar: 'توقف — خطر على الركبة', en: 'Stop — knee risk' } },
          },
          pairedWith: 'right_knee',
        },
        {
          joint: 'right_knee',
          role: 'primary',
          startPose: { min: 140, max: 180 },
          upRange: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 }, pad: { min: 140, max: 180 } },
          downRange: {
            perfect: { min: 55, max: 95 },
            normal: { min: 45, max: 105 },
            pad: { min: 35, max: 115 },
            warning: { min: 25, max: 38 },
            danger: { min: 0, max: 25 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! انطلاقة قوية', en: 'Perfect! Powerful takeoff' },
              down: { ar: 'ممتاز! هبوط ممتص', en: 'Perfect! Soft landing' },
            },
            normal: {
              up: { ar: 'جيد — اضغط أكثر في الانطلاق', en: 'Good — drive harder on takeoff' },
              down: { ar: 'جيد — ثنِ الركبتين أكثر عند الهبوط', en: 'Good — bend knees more on landing' },
            },
            pad: { down: { ar: 'مقبول — قلل الارتفاع تدريجياً', en: 'Acceptable — lower box height gradually' } },
            warning: { down: { ar: 'هبوط صلب — خفف الارتفاع', en: 'Hard landing — reduce height' } },
            danger: { down: { ar: 'توقف — خطر على الركبة', en: 'Stop — knee risk' } },
          },
          pairedWith: 'left_knee',
        },
      ],
      feedbackMessages: FB_LIFT,
    },
  ];
}

/** Farmer carry / waiter walk */
function variantCarry(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'ribs_down_carry',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.1 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'لا تسمح للأضلاع بالانفجار للأمام.',
            en: 'Don’t let ribs flare forward.',
          },
          severity: 'warning',
          cooldownMs: 2400,
          minErrorFrames: 5,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_hip',
          role: 'primary',
          startPose: { min: 165, max: 180 },
          range: {
            perfect: { min: 172, max: 180 },
            normal: { min: 165, max: 180 },
            pad: { min: 155, max: 180 },
            warning: { min: 140, max: 155 },
            danger: { min: 0, max: 140 },
          },
          stateMessages: {
            perfect: { ar: 'مشي ثابت — ممتاز', en: 'Stable gait — great' },
            warning: { ar: 'لا تميل الجذع جانباً زائداً', en: 'Don’t lean the trunk sideways' },
            danger: { ar: 'توقف — فقدان التوازن', en: 'Stop — balance lost' },
          },
        },
        {
          joint: 'left_shoulder',
          role: 'secondary',
          startPose: { min: 160, max: 180 },
          range: {
            perfect: { min: 168, max: 180 },
            normal: { min: 158, max: 180 },
            warning: { min: 140, max: 158 },
          },
          stateMessages: {
            perfect: { ar: 'كتف منخفض', en: 'Shoulder packed down' },
            warning: { ar: 'لا ترفع الكتف نحو الأذن', en: 'Don’t hike shoulder to ear' },
          },
        },
      ],
      feedbackMessages: FB_LIFT,
    },
  ];
}

/** Pallof / rotation drills — anti-rotation emphasis */
function variantRotationAnti(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: FRONT,
      cameraPosition: POSE_STANDING_FRONT,
      positionChecks: [
        {
          id: 'shoulder_hip_square',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.1 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'اجعل الصدر والورك يواجهان الكاميرا — لا تلتف مع الحبل.',
            en: 'Keep chest and hips square — don’t rotate with the band.',
          },
          severity: 'warning',
          cooldownMs: 2400,
          minErrorFrames: 5,
        },
      ],
      trackedJoints: [
        {
          joint: 'spine',
          role: 'primary',
          startPose: { min: 0, max: 18 },
          range: {
            perfect: { min: 0, max: 10 },
            normal: { min: 0, max: 16 },
            pad: { min: 0, max: 22 },
            warning: { min: 18, max: 32 },
            danger: { min: 32, max: 70 },
          },
          stateMessages: {
            perfect: { ar: 'جذع ثابت — ممتاز', en: 'Torque resisted — great' },
            warning: { ar: 'قلل شدّ الحبل إن الجذع يلتف', en: 'Reduce band tension if trunk twists' },
            danger: { ar: 'توقف — ألم في الظهر', en: 'Stop — back discomfort' },
          },
        },
        {
          joint: 'left_shoulder',
          role: 'secondary',
          startPose: { min: 70, max: 115 },
          range: {
            perfect: { min: 82, max: 105 },
            normal: { min: 75, max: 115 },
          },
          stateMessages: {
            perfect: { ar: 'ذراع التمديد مستقرة', en: 'Reach arm steady' },
          },
        },
      ],
      feedbackMessages: FB_CORE,
    },
  ];
}

/** Calf / tibialis — ankle line */
function variantAnkleDominant(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'ankle_stack',
          type: 'vertical_comparison',
          landmarks: { primary: 'left_knee', secondary: 'left_ankle' },
          condition: { operator: 'approximately_equal', threshold: 0.1 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'تحرّك من الكاحل — الركبة ثابتة تقريباً.',
            en: 'Move through the ankle — keep the knee almost still.',
          },
          severity: 'tip',
          cooldownMs: 2200,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_ankle',
          role: 'primary',
          startPose: { min: 75, max: 110 },
          upRange: { perfect: { min: 125, max: 150 }, normal: { min: 120, max: 145 }, pad: { min: 110, max: 140 } },
          downRange: {
            perfect: { min: 80, max: 90 },
            normal: { min: 75, max: 95 },
            pad: { min: 70, max: 100 },
            warning: { min: 30, max: 42 },
            danger: { min: 0, max: 30 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! ارتفاع كامل على أصابع القدم', en: 'Perfect! Full rise onto the toes' },
              down: { ar: 'ممتاز! عودة متحكم بها', en: 'Perfect! Controlled return' },
            },
            normal: {
              up: { ar: 'جيد — ارفع الكعب أكثر', en: 'Good — rise higher onto the toes' },
              down: { ar: 'جيد — انزل ببطء', en: 'Good — lower with control' },
            },
            pad: { down: { ar: 'مقبول — زد المدى تدريجياً', en: 'Acceptable — build ROM slowly' } },
            warning: { down: { ar: 'لا ترتد من الأسفل', en: 'Don’t bounce at bottom' } },
          },
          pairedWith: 'right_ankle',
        },
        {
          joint: 'right_ankle',
          role: 'primary',
          startPose: { min: 75, max: 110 },
          upRange: { perfect: { min: 125, max: 150 }, normal: { min: 120, max: 145 }, pad: { min: 110, max: 140 } },
          downRange: {
            perfect: { min: 80, max: 90 },
            normal: { min: 75, max: 95 },
            pad: { min: 70, max: 100 },
            warning: { min: 30, max: 42 },
            danger: { min: 0, max: 30 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! ارتفاع كامل على أصابع القدم', en: 'Perfect! Full rise onto the toes' },
              down: { ar: 'ممتاز! عودة متحكم بها', en: 'Perfect! Controlled return' },
            },
            normal: {
              up: { ar: 'جيد — ارفع الكعب أكثر', en: 'Good — rise higher onto the toes' },
              down: { ar: 'جيد — انزل ببطء', en: 'Good — lower with control' },
            },
            pad: { down: { ar: 'مقبول — زد المدى تدريجياً', en: 'Acceptable — build ROM slowly' } },
            warning: { down: { ar: 'لا ترتد من الأسفل', en: 'Don’t bounce at bottom' } },
          },
          pairedWith: 'left_ankle',
        },
        {
          joint: 'left_knee',
          role: 'secondary',
          startPose: { min: 160, max: 180 },
          range: {
            perfect: { min: 168, max: 180 },
            normal: { min: 158, max: 180 },
            warning: { min: 140, max: 158 },
          },
          stateMessages: {
            perfect: { ar: 'ركبة ثابتة', en: 'Knee steady' },
            warning: { ar: 'لا تثنِ الركبة لتعويض الكاحل', en: 'Don’t bend knee to cheat ankle ROM' },
          },
        },
      ],
      feedbackMessages: FB_LIFT,
    },
  ];
}

/** Wrist / fine motor — gentle elbow reference */
function variantMinimalElbow(_ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [],
      trackedJoints: [
        {
          joint: 'left_elbow',
          role: 'primary',
          startPose: { min: 90, max: 180 },
          upRange: { perfect: { min: 165, max: 180 }, normal: { min: 160, max: 180 }, pad: { min: 155, max: 180 } },
          downRange: {
            perfect: { min: 110, max: 145 },
            normal: { min: 100, max: 150 },
            pad: { min: 90, max: 152 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'مدى مريح', en: 'Comfortable range' },
              down: { ar: 'تحكم جيد', en: 'Good control' },
            },
            normal: {
              up: { ar: 'جيد — قلل السرعة', en: 'Good — slow down' },
              down: { ar: 'جيد — لا تفرط', en: 'Good — stay submax' },
            },
          },
          pairedWith: 'right_elbow',
        },
        {
          joint: 'right_elbow',
          role: 'primary',
          startPose: { min: 90, max: 180 },
          upRange: { perfect: { min: 165, max: 180 }, normal: { min: 160, max: 180 }, pad: { min: 155, max: 180 } },
          downRange: {
            perfect: { min: 110, max: 145 },
            normal: { min: 100, max: 150 },
            pad: { min: 90, max: 152 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'مدى مريح', en: 'Comfortable range' },
              down: { ar: 'تحكم جيد', en: 'Good control' },
            },
            normal: {
              up: { ar: 'جيد — قلل السرعة', en: 'Good — slow down' },
              down: { ar: 'جيد — لا تفرط', en: 'Good — stay submax' },
            },
          },
          pairedWith: 'left_elbow',
        },
      ],
      feedbackMessages: FB_MOBILITY,
    },
  ];
}

/**
 * Full curated pose blueprint: joints + checks + feedback wired like JSON exercises.
 */
export function buildCuratedPoseVariants(ex: CuratedExtensionExercise): SeedPoseVariantJson[] {
  const override = buildCuratedPoseOverride(ex);
  if (override) return override;

  const s = ex.slug;
  if (s.includes('wrist')) return variantMinimalElbow(ex);
  if (s.includes('calf') || s.includes('tibialis') || s.includes('ankle_df')) return variantAnkleDominant(ex);
  if (isFacePullFamily(ex)) return variantFacePullStyle(ex);
  /** Pike push-up: inverted pressing pattern — side view elbow + trunk like horizontal push */
  if (s === 'lib_pike_pushup') return variantPushHorizontalChest(ex);

  switch (ex.movementPattern) {
    case 'SQUAT':
      return variantSquatPattern(ex);
    case 'LUNGE':
      return variantLungePattern(ex);
    case 'HINGE':
      return variantHingePattern(ex);
    case 'PUSH_HORIZONTAL':
      if (ex.categoryCode === 'arms') {
        return isTricepSlug(ex) ? variantArmTricepExtension(ex) : variantArmCurl(ex);
      }
      return variantPushHorizontalChest(ex);
    case 'PULL_HORIZONTAL':
      return variantPullHorizontalRow(ex);
    case 'PUSH_VERTICAL':
      return variantVerticalPush(ex);
    case 'PULL_VERTICAL':
      return variantVerticalPull(ex);
    case 'CORE_BRACE':
      if (ex.countingMethodCode === 'hold') {
        if (ex.familyKey.includes('lateral')) return variantCoreHoldAntiLateral(ex);
        if (ex.familyKey === 'back_extension_family') return variantBackExtensionHold(ex);
        if (ex.familyKey.includes('rotation')) return variantRotationAnti(ex);
        return variantCoreHoldAntiExtension(ex);
      }
      if (ex.familyKey === 'trunk_flexion_family') return variantCoreTrunkFlexion(ex);
      if (ex.slug.includes('mountain') || ex.slug.includes('climber')) return variantGait(ex);
      return variantCoreTrunkFlexion(ex);
    case 'MOBILITY_DRILL':
      return variantMobilityDrill(ex);
    case 'GAIT':
      return variantGait(ex);
    case 'JUMP_LAND':
      return variantJumpLand(ex);
    case 'CARRY':
      return variantCarry(ex);
    case 'ROTATION':
      return variantRotationAnti(ex);
    default:
      return variantMobilityDrill(ex);
  }
}
