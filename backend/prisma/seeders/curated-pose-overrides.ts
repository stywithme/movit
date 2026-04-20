import type { CuratedExtensionExercise } from './curated-catalog-extension';
import type { SeedPoseVariantJson } from './pose-variant-seed-helper';
import {
  hipPhasedMessages_bridgeHamstringSlide,
  hipPhasedMessages_squat,
  hipPhasedRanges_bridgeHamstringSlide,
  hipPhasedRanges_squat,
  hipSecondaryTemplateRange,
  kneePhasedMessages_pushPress,
  kneePhasedRanges_pushPress,
  kneeSecondaryTemplateRange,
  shoulderPhasedMessages_benchPress,
  shoulderPhasedRanges_benchPress,
  shoulderSecondaryTemplateRange,
  spinePhasedMessages_compoundCore,
  spinePhasedMessages_dynamic,
  spinePhasedRanges_compoundCore,
  spinePhasedRanges_dynamic,
  spineSecondaryTemplateRange,
} from './phase-range-builders';

const SIDE = { ar: 'زاوية جانبية', en: 'Side view' };
const FRONT = { ar: 'زاوية أمامية', en: 'Front view' };
const SUPINE = { ar: 'مستلقٍ على الظهر', en: 'Lying on back (side angle)' };
const PRONE = { ar: 'مستلقٍ على الوجه', en: 'Lying face down (side angle)' };
const SIDE_LYING = { ar: 'مستلقٍ على الجانب', en: 'Side lying' };
const SEATED_SIDE = { ar: 'جالس - جانبي', en: 'Seated side' };
const SEATED_UPPER = { ar: 'جالس - علوي', en: 'Seated upper body' };

/**
 * Camera / pose position codes (must match backend `pose-positions.ts`).
 * Use the explicit code so the seeder bypasses the legacy view-name mapping
 * and the dashboard stores the correct posture (standing vs supine vs prone).
 */
const POSE_STANDING_SIDE = 'standing_side';
const POSE_STANDING_FRONT = 'standing_front';
const POSE_SUPINE_SIDE = 'supine_side';
const POSE_PRONE_SIDE = 'prone_side';
const POSE_SIDE_LYING = 'side_lying';
const POSE_SITTING_SIDE = 'sitting_side';
const POSE_SITTING_SIDE_UPPER = 'sitting_side_upper';

const FEEDBACK_STRENGTH = {
  motivational: [
    { ar: 'أداء قوي ومنظم', en: 'Strong and controlled work' },
    { ar: 'حافظ على الشكل قبل السرعة', en: 'Keep form before speed' },
  ],
  tips: [
    { ar: 'خذ نفساً تحضيرياً قبل كل عدة وازفر مع الجهد.', en: 'Take a setup breath before each rep and exhale on effort.' },
    { ar: 'إذا فقدت التحكم الميكانيكي، قلل المدى أو الحمل.', en: 'If mechanics break down, reduce range or load.' },
  ],
};

const FEEDBACK_CORE = {
  motivational: [
    { ar: 'شد جذع ممتاز', en: 'Excellent trunk tension' },
    { ar: 'ثباتك يتحسن عدة بعد عدة', en: 'Your stability improves rep by rep' },
  ],
  tips: [
    { ar: 'شد السرة برفق ولا تحبس النفس طويلاً.', en: 'Brace gently and avoid long breath-holds.' },
    { ar: 'حرّك الأطراف دون أن ينهار الجذع.', en: 'Move the limbs without letting the trunk collapse.' },
  ],
};

const FEEDBACK_MOBILITY = {
  motivational: [
    { ar: 'مدى حركة جيد وتحكم أفضل', en: 'Good range and better control' },
    { ar: 'تحرك بسلاسة داخل نطاق مريح', en: 'Move smoothly inside a comfortable range' },
  ],
  tips: [
    { ar: 'ابقِ الحركة نظيفة وهادئة بدون ارتداد.', en: 'Keep the motion smooth and bounce-free.' },
    { ar: 'أي ألم حاد يعني تقليل المدى فوراً.', en: 'Any sharp pain means reduce range immediately.' },
  ],
};

const FEEDBACK_UPPER = {
  motivational: [
    { ar: 'تحكّم رائع في المدى', en: 'Excellent range control' },
    { ar: 'استمر — الجودة أهم من العدد', en: 'Keep going — quality beats reps' },
  ],
  tips: [
    { ar: 'كتفاك للأسفل والخلف قليلاً (تثبيت لوح الكتف).', en: 'Keep shoulders down and slightly back (scapular set).' },
    { ar: 'لا تفرط في تمديد الرقبة؛ نظرة أمامية قصيرة.', en: 'Avoid craning the neck; keep a short forward gaze.' },
  ],
};

const SET_DEEP_SQUAT_HOLD = new Set(['lib_deep_squat_hold']);
const SET_SPLIT_SQUAT_HOLD = new Set(['lib_isometric_split_squat']);
const SET_LATERAL_SQUATS = new Set(['lib_side_lunge', 'lib_cossack_squat_bw', 'lib_goblet_cossack']);
const SET_OVERHEAD_SQUAT = new Set(['lib_overhead_squat_pvc']);
const SET_THRUSTERS = new Set(['lib_db_thruster']);
const SET_WALL_SIT_PRESS = new Set(['lib_wall_sit_overhead_press']);
const SET_BENCH_PRESS = new Set(['lib_dumbbell_press', 'lib_barbell_bench']);
const SET_SCAP_PUSHUP = new Set(['lib_scap_pushup']);
const SET_TRICEP_PUSHDOWN = new Set(['lib_tricep_pushdown_band']);
const SET_PIKE_PUSHUP = new Set(['lib_pike_pushup']);
const SET_LANDMINE_PRESS = new Set(['lib_landmine_press']);
const SET_OVERHEAD_TRICEP = new Set(['lib_overhead_tricep_ext']);
const SET_BRIDGES = new Set(['lib_bench_hip_thrust', 'lib_glute_bridge_march', 'lib_glute_activation_bridge']);
const SET_NORDIC_CURL = new Set(['lib_nordic_curl_eccentric']);
const SET_HAMSTRING_SLIDE = new Set(['lib_hamstring_slide']);
const SET_SINGLE_LEG_HINGE = new Set(['lib_single_leg_rdl', 'lib_staggered_rdl']);
const SET_MOBILITY_HINGE = new Set(['lib_hip_hinge_drill', 'lib_seated_good_morning', 'lib_jefferson_curl_light']);
const SET_BALLISTIC_HINGE = new Set(['lib_kettlebell_swing']);
const SET_KB_GOBLET_CLEAN = new Set(['lib_kb_goblet_clean']);
const SET_PUSH_PRESS = new Set(['lib_push_press']);
const SET_DEAD_BUG = new Set(['lib_dead_bug']);
const SET_HEEL_TAPS = new Set(['lib_heel_taps']);
const SET_BIRD_DOG = new Set(['lib_bird_dog']);
const SET_PALLOF = new Set(['lib_pallof_press']);
const SET_ROTATION_HOLD = new Set(['lib_cable_rotation_anti']);
const SET_BACK_EXTENSION_REPS = new Set(['lib_roman_chair_back_ext']);
const SET_REVERSE_CRUNCH = new Set(['lib_reverse_crunch']);
const SET_V_UP = new Set(['lib_v_up']);
const SET_BICYCLE = new Set(['lib_bicycle_crunch']);
const SET_SCAPULAR_DEPRESSION = new Set(['lib_scapular_depression']);
const SET_PRONE_Y = new Set(['lib_prone_y_raise']);
const SET_CALF_RAISE = new Set(['lib_calf_raise_seated']);
const SET_TIBIALIS = new Set(['lib_tibialis_raise']);
const SET_TURKISH_GETUP = new Set(['lib_turkish_getup_half']);
const SET_FARMER_CARRY = new Set(['lib_farmer_carry']);
const SET_WAITER_WALK = new Set(['lib_waiter_walk']);
/** Misclassified as PULL_HORIZONTAL in catalog — use curl mechanics */
const SET_ARM_CURL_PULL_HORIZONTAL = new Set(['lib_hammer_curl', 'lib_ez_bar_curl']);
/** Rotation drills (not anti-rotation Pallof-style) */
const SET_ROTATION_MOBILITY = new Set(['lib_thoracic_open_book', 'lib_split_stance_rotation', 'lib_half_kneeling_lift']);
const SET_BEAR_CRAWL = new Set(['lib_bear_crawl']);
const SET_SPIDERMAN_LUNGE = new Set(['lib_spiderman_lunge']);
const SET_RENEGADE_ROW = new Set(['lib_prone_plank_row']);
const SET_SNATCH_BALANCE = new Set(['lib_one_arm_snatch_balance']);

const SET_HIP_MOBILITY_DRILL = new Set([
  'lib_worlds_greatest_stretch',
  'lib_hip_9090',
  'lib_quadruped_rocking',
  'lib_adductor_rockback',
  'lib_elevated_pigeon',
  'lib_clamshell',
  'lib_terminal_knee_extension',
  'lib_standing_hip_circle',
  'lib_prone_hip_internal_rotation',
]);
const SET_ANKLE_MOBILITY_DRILL = new Set([
  'lib_ankle_inversion_eversion',
  'lib_standing_calf_mobility',
  'lib_ankle_df_knee_wall',
]);
const SET_SPINE_MOBILITY_DRILL = new Set([
  'lib_cat_camel',
  'lib_inchworm',
  'lib_thoracic_open_book',
]);
const SET_STANDING_MOBILITY_DRILL = new Set([
  'lib_windmill_stretch',
  'lib_wall_posture_reset',
  'lib_split_stance_rotation',
]);
const SET_YOGA_PRONE_FLOW = new Set(['lib_yoga_down_dog', 'lib_yoga_up_dog']);
const SET_HOLLOW_BODY = new Set(['lib_hollow_body_hold']);
const SET_MOUNTAIN_CLIMBER = new Set(['lib_mountain_climber_slow']);
const SET_SUPINE_SHOULDER_FLEX = new Set(['lib_supine_shoulder_flexion']);
const SET_SEATED_HINGE = new Set(['lib_seated_good_morning']);
const SET_SEATED_CALF = new Set(['lib_calf_raise_seated']);
const SET_SEATED_PRESS = new Set(['lib_z_press']);
const SET_BILATERAL_GAIT_FRONT = new Set(['lib_jumping_jack_low_impact']);
const SET_SHOULDER_MOBILITY_DRILL = new Set([
  'lib_wall_slides',
  'lib_elbow_out_rotations',
  'lib_wall_angels',
  'lib_shoulder_dislocates_band',
  'lib_forearm_wall_slide',
]);
const SET_PASSIVE_SHOULDER_STRETCH = new Set([
  'lib_sleeper_stretch',
  'lib_cross_body_stretch',
  'lib_pec_doorway_stretch',
]);
const SET_NECK_MOBILITY_DRILL = new Set(['lib_neck_retraction', 'lib_seated_neck_rotation']);

function hipMobilityDrillPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: PRONE,
      cameraPosition: POSE_PRONE_SIDE,
      positionChecks: [
        {
          id: 'hip_mob_smooth',
          type: 'forward_comparison',
          landmarks: { primary: 'left_hip', secondary: 'left_knee' },
          condition: { operator: 'should_exceed', threshold: 0.02 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'افتح الورك ببطء — لا تتجاوز الألم الحاد.',
            en: 'Open the hip slowly — stay below sharp pain.',
          },
          severity: 'tip',
          cooldownMs: 3200,
          minErrorFrames: 6,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_hip',
          role: 'primary',
          startPose: { min: 120, max: 180 },
          range: {
            perfect: { min: 140, max: 175 },
            normal: { min: 125, max: 180 },
            pad: { min: 110, max: 180 },
            warning: { min: 95, max: 110 },
          },
          stateMessages: {
            perfect: { ar: 'مدى ورك ممتاز', en: 'Great hip range' },
            warning: { ar: 'قلل المدى إن الركبة تضايقك', en: 'Ease range if knee complains' },
          },
        },
        {
          joint: 'left_knee',
          role: 'secondary',
          startPose: { min: 85, max: 130 },
          range: {
            perfect: { min: 92, max: 118 },
            normal: { min: 85, max: 125 },
          },
          stateMessages: {
            perfect: { ar: 'ركبة مستقرة', en: 'Knee steady' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

function ankleMobilityDrillPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [],
      trackedJoints: [
        {
          joint: 'left_ankle',
          role: 'primary',
          startPose: { min: 70, max: 110 },
          upRange: { perfect: { min: 120, max: 148 }, normal: { min: 115, max: 155 }, pad: { min: 110, max: 160 } },
          downRange: {
            perfect: { min: 78, max: 92 },
            normal: { min: 72, max: 98 },
            pad: { min: 65, max: 102 },
            warning: { min: 55, max: 65 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! حركة كاحل واضحة', en: 'Perfect! Clear ankle motion' },
              down: { ar: 'ممتاز! عودة متحكم بها', en: 'Perfect! Controlled return' },
            },
            normal: {
              up: { ar: 'جيد، زد المدى تدريجياً', en: 'Good, build ROM gradually' },
              down: { ar: 'جيد، بدون ارتداد', en: 'Good, no bouncing' },
            },
          },
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

function spineMobilityDrillPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: PRONE,
      cameraPosition: POSE_PRONE_SIDE,
      positionChecks: [
        {
          id: 'spine_seg_mob',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.14 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'تحرّك بسلاسة داخل نطاق مريح.',
            en: 'Move smoothly within a comfortable range.',
          },
          severity: 'tip',
          cooldownMs: 3200,
          minErrorFrames: 6,
        },
      ],
      trackedJoints: [
        {
          joint: 'spine',
          role: 'primary',
          startPose: { min: 0, max: 40 },
          range: {
            perfect: { min: 8, max: 35 },
            normal: { min: 0, max: 42 },
            pad: { min: 0, max: 50 },
            warning: { min: 45, max: 62 },
          },
          stateMessages: {
            perfect: { ar: 'تحكم قطني جيد', en: 'Good segmental control' },
            warning: { ar: 'قلل المدى إن الظهر يضيق', en: 'Ease range if back pinches' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

function shoulderMobilityDrillPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: FRONT,
      cameraPosition: POSE_STANDING_FRONT,
      positionChecks: [
        {
          id: 'shoulder_mob_window',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_elbow' },
          condition: { operator: 'approximately_equal', threshold: 0.14 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'تحرّك ضمن نطاق مريح — لا تتجاوز الألم الحاد.',
            en: 'Stay in a comfortable window — no sharp pain.',
          },
          severity: 'tip',
          cooldownMs: 3500,
          minErrorFrames: 6,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_shoulder',
          role: 'primary',
          startPose: { min: 25, max: 85 },
          range: {
            perfect: { min: 40, max: 72 },
            normal: { min: 30, max: 85 },
            pad: { min: 20, max: 95 },
            warning: { min: 85, max: 105 },
          },
          stateMessages: {
            perfect: { ar: 'مدى كتف ممتاز', en: 'Great shoulder range' },
            warning: { ar: 'قلل المدى إن شعرت بقرصة', en: 'Ease range if you feel pinching' },
          },
        },
        {
          joint: 'left_elbow',
          role: 'secondary',
          startPose: { min: 140, max: 180 },
          range: {
            perfect: { min: 155, max: 180 },
            normal: { min: 145, max: 180 },
          },
          stateMessages: {
            perfect: { ar: 'مرفق مرتخٍ قليلاً', en: 'Soft elbow' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

function passiveShoulderStretchPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [],
      trackedJoints: [
        {
          joint: 'left_shoulder',
          role: 'primary',
          startPose: { min: 20, max: 110 },
          range: {
            perfect: { min: 35, max: 95 },
            normal: { min: 28, max: 105 },
            pad: { min: 20, max: 115 },
            warning: { min: 110, max: 125 },
          },
          stateMessages: {
            perfect: { ar: 'تمدد كتف مريح', en: 'Comfortable shoulder stretch' },
            warning: { ar: 'لا تتجاوز الألم الحاد', en: 'Do not push through sharp pain' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

function neckMobilityDrillPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SEATED_UPPER,
      cameraPosition: POSE_SITTING_SIDE_UPPER,
      positionChecks: [],
      trackedJoints: [
        {
          joint: 'spine',
          role: 'primary',
          startPose: { min: 0, max: 25 },
          range: {
            perfect: { min: 0, max: 12 },
            normal: { min: 0, max: 18 },
            pad: { min: 0, max: 24 },
            warning: { min: 22, max: 32 },
          },
          stateMessages: {
            perfect: { ar: 'رقبة وجذع علوي هادئان', en: 'Quiet neck and upper trunk' },
            warning: { ar: 'تجنب الاندفاع السريع', en: 'Avoid snapping the head' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

/** Standing mobility drills (windmill, wall posture reset, split stance rotation) */
function standingMobilityDrillPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: FRONT,
      cameraPosition: POSE_STANDING_FRONT,
      positionChecks: [],
      trackedJoints: [
        {
          joint: 'spine',
          role: 'primary',
          startPose: { min: 0, max: 30 },
          range: {
            perfect: { min: 0, max: 18 },
            normal: { min: 0, max: 26 },
            pad: { min: 0, max: 35 },
            warning: { min: 32, max: 50 },
          },
          stateMessages: {
            perfect: { ar: 'حركة سلسة من الجذع', en: 'Smooth movement from the trunk' },
            warning: { ar: 'قلل المدى إن انهار الجذع', en: 'Reduce range if trunk collapses' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

/** Yoga prone flow (Down Dog, Up Dog) — face-down sequences */
function yogaProneFlowPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: PRONE,
      cameraPosition: POSE_PRONE_SIDE,
      positionChecks: [
        {
          id: 'yoga_long_breath',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.2 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'تنفس بعمق وحافظ على وضع متمدد دون اندفاع.',
            en: 'Breathe deeply and keep the shape long without forcing.',
          },
          severity: 'tip',
          cooldownMs: 3500,
          minErrorFrames: 6,
        },
      ],
      trackedJoints: [
        {
          joint: 'spine',
          role: 'primary',
          startPose: { min: 0, max: 80 },
          range: {
            perfect: { min: 5, max: 45 },
            normal: { min: 0, max: 55 },
            pad: { min: 0, max: 70 },
            warning: { min: 65, max: 85 },
          },
          stateMessages: {
            perfect: { ar: 'وضعية يوغا مفتوحة وثابتة', en: 'Open, stable yoga shape' },
            warning: { ar: 'لا تفرط في التمدد القطني', en: 'Avoid excessive lumbar extension' },
          },
        },
        {
          joint: 'left_shoulder',
          role: 'secondary',
          startPose: { min: 80, max: 180 },
          range: {
            perfect: { min: 130, max: 180 },
            normal: { min: 115, max: 180 },
            pad: { min: 100, max: 180 },
            warning: { min: 80, max: 100 },
          },
          stateMessages: {
            perfect: { ar: 'كتفان طويلان ومنفتحان', en: 'Shoulders long and open' },
            warning: { ar: 'لا تترك الكتفين يطفوان نحو الأذنين', en: 'Do not let shoulders shrug to the ears' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

/** Hollow body hold — supine isometric anti-extension */
function hollowBodyHoldPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SUPINE,
      cameraPosition: POSE_SUPINE_SIDE,
      positionChecks: [
        {
          id: 'hollow_low_back_anchor',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.18 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'ابقِ أسفل الظهر ملتصقاً بالأرض طوال الثبات.',
            en: 'Keep the low back glued to the floor throughout the hold.',
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
          startPose: { min: 0, max: 30 },
          range: {
            perfect: { min: 0, max: 12 },
            normal: { min: 0, max: 18 },
            pad: { min: 0, max: 24 },
            warning: { min: 24, max: 38 },
            danger: { min: 38, max: 70 },
          },
          stateMessages: {
            perfect: { ar: 'جذع مجوف وثابت', en: 'Hollow, anchored trunk' },
            warning: { ar: 'لا تدع أسفل الظهر يقوّس', en: 'Do not let the low back arch' },
            danger: { ar: 'توقف — فقد التحكم في القاع', en: 'Stop — lost control of the hollow position' },
          },
        },
        {
          joint: 'left_hip',
          role: 'secondary',
          startPose: { min: 90, max: 160 },
          range: {
            perfect: { min: 115, max: 145 },
            normal: { min: 105, max: 155 },
            pad: { min: 95, max: 165 },
            warning: { min: 80, max: 95 },
          },
          stateMessages: {
            perfect: { ar: 'ساقان مرفوعتان بزاوية مناسبة', en: 'Legs lifted at the right angle' },
            warning: { ar: 'ارفع الساقين أعلى لتقليل ضغط القطن', en: 'Lift legs higher to ease low-back stress' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_CORE,
    },
  ];
}

/** Slow mountain climber — prone plank with alternating knee drive */
function mountainClimberPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: PRONE,
      cameraPosition: POSE_PRONE_SIDE,
      positionChecks: [
        {
          id: 'mc_flat_back',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.14 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'حافظ على ظهر مستوٍ كالـ plank — لا ترفع الورك أو يهبط.',
            en: 'Keep a flat plank back — do not pike up or sag down.',
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
          startPose: { min: 80, max: 180 },
          upRange: {
            perfect: { min: 145, max: 180 },
            normal: { min: 138, max: 180 },
            pad: { min: 130, max: 180 },
          },
          downRange: {
            perfect: { min: 80, max: 110 },
            normal: { min: 75, max: 118 },
            pad: { min: 70, max: 125 },
            warning: { min: 60, max: 70 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! plank ثابت بين العدات', en: 'Perfect! Stable plank between reps' },
              down: { ar: 'ممتاز! ركبة قريبة من الصدر', en: 'Perfect! Knee driven to the chest' },
            },
            normal: {
              up: { ar: 'جيد، ابقِ الورك مستوياً', en: 'Good, keep the hip level' },
              down: { ar: 'جيد، اقترب أكثر بالركبة', en: 'Good, draw the knee in further' },
            },
            pad: { down: { ar: 'مقبول، لكن المدى ما زال صغيراً', en: 'Acceptable, but knee drive is still short' } },
            warning: { down: { ar: 'لا تهز الورك للأعلى مع الركبة', en: 'Do not let the hip pike up with the knee' } },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 22 },
          range: {
            perfect: { min: 0, max: 14 },
            normal: { min: 0, max: 20 },
            pad: { min: 0, max: 26 },
            warning: { min: 26, max: 40 },
          },
          stateMessages: {
            perfect: { ar: 'جذع منظم في وضع plank', en: 'Trunk organized in plank' },
            warning: { ar: 'قلل السرعة إذا فقد الظهر الحياد', en: 'Slow down if spine loses neutrality' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_CORE,
    },
  ];
}

/** Seated good-morning — sitting hip hinge with light load */
function seatedHingePattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SEATED_SIDE,
      cameraPosition: POSE_SITTING_SIDE,
      positionChecks: [
        {
          id: 'seated_neutral_spine',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.12 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'حافظ على ظهر محايد أثناء الانحناء من الورك.',
            en: 'Keep a neutral spine while hinging from the hip.',
          },
          severity: 'warning',
          cooldownMs: 2400,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'spine',
          role: 'primary',
          startPose: { min: 0, max: 70 },
          upRange: {
            perfect: { min: 0, max: 18 },
            normal: { min: 0, max: 24 },
            pad: { min: 0, max: 30 },
          },
          downRange: {
            perfect: { min: 35, max: 60 },
            normal: { min: 30, max: 70 },
            pad: { min: 25, max: 80 },
            warning: { min: 80, max: 95 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! وقفة جالسة منظمة', en: 'Perfect! Seated tall position' },
              down: { ar: 'ممتاز! انحناء ورك متحكم', en: 'Perfect! Controlled hip hinge' },
            },
            normal: {
              up: { ar: 'جيد، اجلس مرفوعاً قليلاً', en: 'Good, sit a bit taller' },
              down: { ar: 'جيد، انحنِ من الورك أكثر', en: 'Good, hinge more from the hips' },
            },
            warning: { down: { ar: 'لا تتجاوز نطاقك المريح', en: 'Do not push past your comfortable range' } },
          },
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

/** Seated calf raise — sitting position, ankle plantarflexion */
function seatedCalfRaisePattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SEATED_SIDE,
      cameraPosition: POSE_SITTING_SIDE,
      positionChecks: [],
      trackedJoints: [
        {
          joint: 'left_ankle',
          role: 'primary',
          startPose: { min: 75, max: 110 },
          upRange: {
            perfect: { min: 125, max: 150 },
            normal: { min: 118, max: 155 },
            pad: { min: 110, max: 158 },
          },
          downRange: {
            perfect: { min: 78, max: 95 },
            normal: { min: 72, max: 100 },
            pad: { min: 65, max: 105 },
            warning: { min: 50, max: 65 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! ارتفاع كامل على أصابع القدم', en: 'Perfect! Full rise onto the toes' },
              down: { ar: 'ممتاز! عودة متحكم بها', en: 'Perfect! Controlled return' },
            },
            normal: {
              up: { ar: 'جيد، ارفع الكعب أكثر', en: 'Good, lift the heel a bit higher' },
              down: { ar: 'جيد، لا ترتد', en: 'Good, no bouncing' },
            },
          },
          pairedWith: 'right_ankle',
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

/** Z-press — seated overhead press from the floor */
function seatedPressPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SEATED_SIDE,
      cameraPosition: POSE_SITTING_SIDE,
      positionChecks: [
        {
          id: 'zpress_tall_seated',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.1 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'اجلس مرفوعاً مع جذع منظم أثناء الضغط فوق الرأس.',
            en: 'Sit tall with an organized trunk during the overhead press.',
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
          startPose: { min: 70, max: 115 },
          upRange: { perfect: { min: 165, max: 180 }, normal: { min: 158, max: 180 }, pad: { min: 150, max: 180 } },
          downRange: {
            perfect: { min: 82, max: 100 },
            normal: { min: 74, max: 108 },
            pad: { min: 66, max: 115 },
            warning: { min: 54, max: 66 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! تمدد علوي كامل', en: 'Perfect! Full overhead lockout' },
              down: { ar: 'ممتاز! استقبال نظيف عند الكتف', en: 'Perfect! Clean catch at the shoulder' },
            },
            normal: {
              up: { ar: 'جيد، اضغط أكثر للأعلى', en: 'Good, press higher' },
              down: { ar: 'جيد، لا تترك المرفق ينزل أكثر', en: 'Good, do not let the elbow drop further' },
            },
          },
          pairedWith: 'right_elbow',
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

/** Bilateral gait drills (jumping jacks) — front view captures symmetry */
function bilateralGaitFrontPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: FRONT,
      cameraPosition: POSE_STANDING_FRONT,
      positionChecks: [
        {
          id: 'jack_arms_legs_sync',
          type: 'horizontal_alignment',
          landmarks: { primary: 'left_wrist', secondary: 'right_wrist' },
          condition: { operator: 'approximately_equal', threshold: 0.12 },
          activePhases: ['up'],
          errorMessage: {
            ar: 'حرك الذراعين والساقين معاً بتناسق.',
            en: 'Move arms and legs together with rhythm.',
          },
          severity: 'tip',
          cooldownMs: 2200,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_shoulder',
          role: 'primary',
          startPose: { min: 0, max: 90 },
          upRange: {
            perfect: { min: 130, max: 175 },
            normal: { min: 115, max: 180 },
            pad: { min: 100, max: 180 },
          },
          downRange: {
            perfect: { min: 0, max: 25 },
            normal: { min: 0, max: 35 },
            pad: { min: 0, max: 50 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! ذراعان فوق الرأس', en: 'Perfect! Arms overhead' },
              down: { ar: 'ممتاز! عودة بجانب الجسم', en: 'Perfect! Return beside the body' },
            },
            normal: {
              up: { ar: 'جيد، ارفع أعلى', en: 'Good, lift a bit higher' },
              down: { ar: 'جيد، لا تتوقف نصف العدة', en: 'Good, do not stop mid-rep' },
            },
          },
          pairedWith: 'right_shoulder',
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

/** Supine shoulder flexion slide — lying on back, arms slide overhead */
function supineShoulderFlexionPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SUPINE,
      cameraPosition: POSE_SUPINE_SIDE,
      positionChecks: [
        {
          id: 'supine_low_back_quiet',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.16 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'ابقِ أسفل الظهر على الأرض أثناء الانزلاق.',
            en: 'Keep the low back on the floor during the slide.',
          },
          severity: 'warning',
          cooldownMs: 2400,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_shoulder',
          role: 'primary',
          startPose: { min: 0, max: 100 },
          upRange: {
            perfect: { min: 155, max: 180 },
            normal: { min: 145, max: 180 },
            pad: { min: 135, max: 180 },
          },
          downRange: {
            perfect: { min: 30, max: 70 },
            normal: { min: 22, max: 85 },
            pad: { min: 15, max: 100 },
            warning: { min: 100, max: 120 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! ذراعان فوق الرأس بانزلاق نظيف', en: 'Perfect! Arms slide overhead cleanly' },
              down: { ar: 'ممتاز! بداية ثابتة بجانب الجسم', en: 'Perfect! Stable start beside the body' },
            },
            normal: {
              up: { ar: 'جيد، صل لأقصى مدى مريح', en: 'Good, reach as far as comfortable' },
              down: { ar: 'جيد، ارجع ببطء', en: 'Good, return slowly' },
            },
          },
          pairedWith: 'right_shoulder',
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

function deepSquatHold(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'deep_squat_knee_track',
          type: 'forward_comparison',
          landmarks: { primary: 'left_knee', secondary: 'left_foot_index' },
          condition: { operator: 'should_not_exceed', threshold: 0.12 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'دع الركبة تتبع اتجاه القدم دون انهيار أو انحراف زائد.',
            en: 'Let the knee track with the foot without collapsing or drifting excessively.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 5,
        },
        {
          id: 'deep_squat_chest',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.18 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'ارفع الصدر قليلاً ولا تنكمش للأمام.',
            en: 'Keep the chest slightly proud and avoid collapsing forward.',
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
          startPose: { min: 60, max: 120 },
          range: {
            perfect: { min: 70, max: 100 },
            normal: { min: 62, max: 108 },
            pad: { min: 55, max: 118 },
            warning: { min: 45, max: 55 },
            danger: { min: 0, max: 45 },
          },
          stateMessages: {
            perfect: { ar: 'عمق جلوس ممتاز', en: 'Excellent squat depth' },
            normal: { ar: 'جيد، ثبت الوضع أكثر', en: 'Good, settle the position more' },
            pad: { ar: 'مقبول، عمّق الجلسة تدريجياً', en: 'Acceptable, deepen gradually' },
            warning: { ar: 'لم تصل بعد لعمق جلوس حقيقي', en: 'You have not reached a true deep hold yet' },
            danger: { ar: 'عمق غير ثابت أو غير آمن', en: 'Depth is unstable or unsafe' },
          },
        },
        {
          joint: 'left_hip',
          role: 'primary',
          startPose: { min: 55, max: 140 },
          range: {
            perfect: { min: 55, max: 90 },
            normal: { min: 50, max: 100 },
            pad: { min: 45, max: 112 },
            warning: { min: 35, max: 45 },
            danger: { min: 0, max: 35 },
          },
          stateMessages: {
            perfect: { ar: 'ورك منخفض ومتمكن', en: 'Hips are low and well-set' },
            normal: { ar: 'جيد، اجلس بين الوركين أكثر', en: 'Good, sit between the hips more' },
            pad: { ar: 'مقبول، لكن ما زال العمق ناقصاً', en: 'Acceptable, but depth is still limited' },
            warning: { ar: 'الورك مرتفع أكثر من المطلوب', en: 'Hips are still too high for a deep hold' },
            danger: { ar: 'الوضع بعيد جداً عن deep squat hold', en: 'Position is far from a true deep squat hold' },
          },
        },
        {
          joint: 'left_ankle',
          role: 'secondary',
          startPose: { min: 70, max: 120 },
          range: {
            perfect: { min: 78, max: 110 },
            normal: { min: 72, max: 116 },
            pad: { min: 65, max: 122 },
            warning: { min: 55, max: 65 },
          },
          stateMessages: {
            perfect: { ar: 'كاحل متحرك جيداً', en: 'Ankle mobility looks good' },
            warning: { ar: 'كعبك قد يرتفع أو الكاحل محدود', en: 'Heel lift or ankle restriction is showing' },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 30 },
          range: {
            perfect: { min: 0, max: 18 },
            normal: { min: 0, max: 24 },
            pad: { min: 0, max: 30 },
            warning: { min: 30, max: 42 },
            danger: { min: 42, max: 80 },
          },
          stateMessages: {
            perfect: { ar: 'ظهر محايد ومتماسك', en: 'Neutral, organized spine' },
            warning: { ar: 'لا تنهَر قطنيّاً في القاع', en: 'Avoid collapsing into lumbar flexion at the bottom' },
            danger: { ar: 'توقف، الظهر يفقد الحياد بوضوح', en: 'Stop, spinal neutrality is clearly lost' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

function splitSquatHold(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'split_squat_front_knee',
          type: 'forward_comparison',
          landmarks: { primary: 'left_knee', secondary: 'left_ankle' },
          condition: { operator: 'should_not_exceed', threshold: 0.08 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'اثبت على رجل أمامية قوية فوق القدم.',
            en: 'Stack the front knee over the foot with control.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 5,
        },
        {
          id: 'split_squat_torso',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.14 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'حافظ على الجذع مرفوعاً فوق الورك.',
            en: 'Keep the torso tall over the hip.',
          },
          severity: 'tip',
          cooldownMs: 2600,
          minErrorFrames: 5,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_knee',
          role: 'primary',
          startPose: { min: 65, max: 125 },
          range: {
            perfect: { min: 78, max: 100 },
            normal: { min: 72, max: 108 },
            pad: { min: 65, max: 118 },
            warning: { min: 55, max: 65 },
            danger: { min: 0, max: 55 },
          },
          stateMessages: {
            perfect: { ar: 'زاوية ركبة ثابتة ومناسبة', en: 'Front knee angle is strong and stable' },
            normal: { ar: 'جيد، اضبط العمق النهائي', en: 'Good, fine-tune the hold depth' },
            pad: { ar: 'مقبول، لكن الثبات ما زال متوسطاً', en: 'Acceptable, but stability is still average' },
            warning: { ar: 'لا تهبط أقل من قدرتك الحالية', en: 'Do not sink deeper than you can control' },
            danger: { ar: 'توقف، الركبة فقدت الثبات', en: 'Stop, the knee has lost stability' },
          },
        },
        {
          joint: 'left_hip',
          role: 'primary',
          startPose: { min: 75, max: 150 },
          range: {
            perfect: { min: 85, max: 120 },
            normal: { min: 80, max: 128 },
            pad: { min: 75, max: 138 },
            warning: { min: 60, max: 75 },
            danger: { min: 0, max: 60 },
          },
          stateMessages: {
            perfect: { ar: 'ورك مضبوط فوق قاعدة ثابتة', en: 'Hip is set over a stable base' },
            warning: { ar: 'قلل الميل الأمامي واضبط الحوض', en: 'Reduce forward lean and tidy the pelvis' },
            danger: { ar: 'الوضعية منهارة عند الورك', en: 'Hip position is collapsing' },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 28 },
          range: {
            perfect: { min: 0, max: 16 },
            normal: { min: 0, max: 22 },
            pad: { min: 0, max: 28 },
            warning: { min: 28, max: 40 },
          },
          stateMessages: {
            perfect: { ar: 'جذع مرفوع ومحايد', en: 'Tall, neutral trunk' },
            warning: { ar: 'لا تنهَر للأمام في الثبات', en: 'Do not collapse forward in the hold' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function lateralSquatPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: FRONT,
      cameraPosition: POSE_STANDING_FRONT,
      positionChecks: [
        {
          id: 'lateral_squat_knee_track',
          type: 'horizontal_alignment',
          landmarks: { primary: 'left_knee', secondary: 'left_ankle' },
          condition: { operator: 'approximately_equal', threshold: 0.12 },
          activePhases: ['down'],
          errorMessage: {
            ar: 'اجعل الركبة المنثنية تتبع اتجاه القدم في النزول الجانبي.',
            en: 'Let the bending knee track with the foot in the lateral descent.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 5,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_knee',
          role: 'primary',
          startPose: { min: 130, max: 180 },
          upRange: {
            perfect: { min: 155, max: 180 },
            normal: { min: 145, max: 180 },
            pad: { min: 135, max: 180 },
          },
          downRange: {
            perfect: { min: 75, max: 110 },
            normal: { min: 68, max: 118 },
            pad: { min: 60, max: 125 },
            warning: { min: 48, max: 60 },
            danger: { min: 0, max: 48 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! عودة قوية للمركز', en: 'Perfect! Strong return to center' },
              down: { ar: 'ممتاز! نزول جانبي متحكم به', en: 'Perfect! Controlled lateral descent' },
            },
            normal: {
              up: { ar: 'جيد، ادفع الأرض بقوة للعودة', en: 'Good, drive the floor to return' },
              down: { ar: 'جيد، ابقِ الجذع مرفوعاً أكثر', en: 'Good, keep the torso a bit taller' },
            },
            pad: { down: { ar: 'مقبول، زد المدى تدريجياً', en: 'Acceptable, increase range gradually' } },
            warning: { down: { ar: 'لا تدع الركبة تنهار للداخل', en: 'Do not let the knee cave inward' } },
            danger: { down: { ar: 'توقف، الوضع الجانبي فقد الاستقرار', en: 'Stop, the lateral position has lost stability' } },
          },
        },
        {
          joint: 'right_knee',
          role: 'primary',
          startPose: { min: 130, max: 180 },
          upRange: {
            perfect: { min: 155, max: 180 },
            normal: { min: 145, max: 180 },
            pad: { min: 135, max: 180 },
          },
          downRange: {
            perfect: { min: 75, max: 110 },
            normal: { min: 68, max: 118 },
            pad: { min: 60, max: 125 },
            warning: { min: 48, max: 60 },
            danger: { min: 0, max: 48 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! عودة قوية للمركز', en: 'Perfect! Strong return to center' },
              down: { ar: 'ممتاز! نزول جانبي متحكم به', en: 'Perfect! Controlled lateral descent' },
            },
            normal: {
              up: { ar: 'جيد، ادفع الأرض بقوة للعودة', en: 'Good, drive the floor to return' },
              down: { ar: 'جيد، ابقِ الجذع مرفوعاً أكثر', en: 'Good, keep the torso a bit taller' },
            },
            pad: { down: { ar: 'مقبول، زد المدى تدريجياً', en: 'Acceptable, increase range gradually' } },
            warning: { down: { ar: 'لا تدع الركبة تنهار للداخل', en: 'Do not let the knee cave inward' } },
            danger: { down: { ar: 'توقف، الوضع الجانبي فقد الاستقرار', en: 'Stop, the lateral position has lost stability' } },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 28 },
          range: {
            perfect: { min: 0, max: 18 },
            normal: { min: 0, max: 24 },
            pad: { min: 0, max: 30 },
            warning: { min: 30, max: 42 },
          },
          stateMessages: {
            perfect: { ar: 'جذع متماسك ومرفوع', en: 'Torso organized and upright' },
            warning: { ar: 'لا تنهَر للأمام أو للدوران', en: 'Avoid folding forward or rotating' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function overheadSquatPvc(): SeedPoseVariantJson[] {
  return [
    {
      name: FRONT,
      cameraPosition: POSE_STANDING_FRONT,
      positionChecks: [
        {
          id: 'ohs_wrists_level',
          type: 'horizontal_alignment',
          landmarks: { primary: 'left_wrist', secondary: 'right_wrist' },
          condition: { operator: 'approximately_equal', threshold: 0.08 },
          activePhases: ['down', 'up'],
          errorMessage: {
            ar: 'أبقِ الذراعين متوازيتين فوق الرأس.',
            en: 'Keep both arms level overhead.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 4,
        },
        {
          id: 'ohs_knee_track',
          type: 'horizontal_alignment',
          landmarks: { primary: 'left_knee', secondary: 'left_ankle' },
          condition: { operator: 'approximately_equal', threshold: 0.12 },
          activePhases: ['down'],
          errorMessage: {
            ar: 'دع الركبة تتبع اتجاه القدم مع بقاء الصدر مفتوحاً.',
            en: 'Let the knee track the foot while keeping the chest open.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 5,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_knee',
          role: 'primary',
          startPose: { min: 120, max: 180 },
          upRange: {
            perfect: { min: 150, max: 180 },
            normal: { min: 138, max: 180 },
            pad: { min: 128, max: 180 },
          },
          downRange: {
            perfect: { min: 65, max: 98 },
            normal: { min: 58, max: 108 },
            pad: { min: 50, max: 118 },
            warning: { min: 38, max: 50 },
            danger: { min: 0, max: 38 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! قف مستقيمًا تحت العصا', en: 'Perfect! Stand tall under the stick' },
              down: { ar: 'ممتاز! سكوات علوي مضبوط', en: 'Perfect! Well-organized overhead squat' },
            },
            normal: {
              up: { ar: 'جيد، ثبت الجذع في الصعود', en: 'Good, stabilize the trunk on the way up' },
              down: { ar: 'جيد، افتح الصدر أكثر في القاع', en: 'Good, open the chest more at the bottom' },
            },
            pad: { down: { ar: 'مقبول، لكن ما زالت الوضعية تحتاج ضبطاً', en: 'Acceptable, but the position still needs tidying' } },
            warning: { down: { ar: 'لا تدع الصدر يسقط أو الركبتين تنهاران', en: 'Do not let the chest drop or knees cave' } },
            danger: { down: { ar: 'توقف، الشكل العلوي غير آمن', en: 'Stop, the overhead position is not safe' } },
          },
          pairedWith: 'right_knee',
        },
        {
          joint: 'right_knee',
          role: 'primary',
          startPose: { min: 120, max: 180 },
          upRange: {
            perfect: { min: 150, max: 180 },
            normal: { min: 138, max: 180 },
            pad: { min: 128, max: 180 },
          },
          downRange: {
            perfect: { min: 65, max: 98 },
            normal: { min: 58, max: 108 },
            pad: { min: 50, max: 118 },
            warning: { min: 38, max: 50 },
            danger: { min: 0, max: 38 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! قف مستقيمًا تحت العصا', en: 'Perfect! Stand tall under the stick' },
              down: { ar: 'ممتاز! سكوات علوي مضبوط', en: 'Perfect! Well-organized overhead squat' },
            },
            normal: {
              up: { ar: 'جيد، ثبت الجذع في الصعود', en: 'Good, stabilize the trunk on the way up' },
              down: { ar: 'جيد، افتح الصدر أكثر في القاع', en: 'Good, open the chest more at the bottom' },
            },
            pad: { down: { ar: 'مقبول، لكن ما زالت الوضعية تحتاج ضبطاً', en: 'Acceptable, but the position still needs tidying' } },
            warning: { down: { ar: 'لا تدع الصدر يسقط أو الركبتين تنهاران', en: 'Do not let the chest drop or knees cave' } },
            danger: { down: { ar: 'توقف، الشكل العلوي غير آمن', en: 'Stop, the overhead position is not safe' } },
          },
          pairedWith: 'left_knee',
        },
        {
          joint: 'left_shoulder',
          role: 'secondary',
          startPose: { min: 135, max: 180 },
          range: {
            perfect: { min: 155, max: 180 },
            normal: { min: 145, max: 180 },
            pad: { min: 138, max: 180 },
            warning: { min: 120, max: 138 },
          },
          stateMessages: {
            perfect: { ar: 'ذراع ثابتة فوق الرأس', en: 'Arm stable overhead' },
            warning: { ar: 'لا تدع الذراع تسقط للأمام', en: 'Do not let the arm fall forward' },
          },
        },
        {
          joint: 'left_hip',
          role: 'secondary',
          startPose: { min: 55, max: 180 },
          range: hipSecondaryTemplateRange(),
          phaseRanges: hipPhasedRanges_squat(),
          phaseStateMessages: hipPhasedMessages_squat(),
          stateMessages: {
            perfect: { ar: 'ورك يتبع عمق السكوات تحت الحمل العلوي', en: 'Hip tracks squat depth under overhead load' },
            warning: { ar: 'لا ترفع الورك مبكراً عن الركبة', en: 'Avoid shooting the hips early' },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 25 },
          range: spineSecondaryTemplateRange(),
          phaseRanges: spinePhasedRanges_dynamic(),
          phaseStateMessages: spinePhasedMessages_dynamic(),
          stateMessages: {
            perfect: { ar: 'جذع منظم تحت الحمل العلوي', en: 'Trunk organized under the overhead load' },
            warning: { ar: 'لا تنهَر في الصدر أو الظهر السفلي', en: 'Avoid collapsing through the chest or low back' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

function thrusterPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: FRONT,
      cameraPosition: POSE_STANDING_FRONT,
      positionChecks: [
        {
          id: 'thruster_wrists_level',
          type: 'horizontal_alignment',
          landmarks: { primary: 'left_wrist', secondary: 'right_wrist' },
          condition: { operator: 'approximately_equal', threshold: 0.09 },
          activePhases: ['up', 'top'],
          errorMessage: {
            ar: 'اضغط بذراعين متوازيتين في الأعلى.',
            en: 'Press with both arms reaching the top together.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_knee',
          role: 'primary',
          startPose: { min: 120, max: 180 },
          upRange: {
            perfect: { min: 150, max: 180 },
            normal: { min: 138, max: 180 },
            pad: { min: 128, max: 180 },
          },
          downRange: {
            perfect: { min: 68, max: 100 },
            normal: { min: 60, max: 110 },
            pad: { min: 50, max: 120 },
            warning: { min: 38, max: 50 },
            danger: { min: 0, max: 38 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! خروج قوي من السكوات', en: 'Perfect! Strong drive out of the squat' },
              down: { ar: 'ممتاز! قاع متحكم به', en: 'Perfect! Controlled squat catch' },
            },
            normal: {
              up: { ar: 'جيد، اضغط بالأرض بقوة أكبر', en: 'Good, drive the floor harder' },
              down: { ar: 'جيد، اضبط عمق السكوات أولاً', en: 'Good, organize the squat depth first' },
            },
            pad: { down: { ar: 'مقبول، لكن الربط بين السكوات والضغط يحتاج ضبطاً', en: 'Acceptable, but the squat-to-press link needs work' } },
            warning: { down: { ar: 'لا تفقد محاذاة الركبة عند النزول', en: 'Do not lose knee tracking in the descent' } },
            danger: { down: { ar: 'توقف، الاستقبال السفلي غير آمن', en: 'Stop, the lower catch is unsafe' } },
          },
          pairedWith: 'right_knee',
        },
        {
          joint: 'right_knee',
          role: 'primary',
          startPose: { min: 120, max: 180 },
          upRange: {
            perfect: { min: 150, max: 180 },
            normal: { min: 138, max: 180 },
            pad: { min: 128, max: 180 },
          },
          downRange: {
            perfect: { min: 68, max: 100 },
            normal: { min: 60, max: 110 },
            pad: { min: 50, max: 120 },
            warning: { min: 38, max: 50 },
            danger: { min: 0, max: 38 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! خروج قوي من السكوات', en: 'Perfect! Strong drive out of the squat' },
              down: { ar: 'ممتاز! قاع متحكم به', en: 'Perfect! Controlled squat catch' },
            },
            normal: {
              up: { ar: 'جيد، اضغط بالأرض بقوة أكبر', en: 'Good, drive the floor harder' },
              down: { ar: 'جيد، اضبط عمق السكوات أولاً', en: 'Good, organize the squat depth first' },
            },
            pad: { down: { ar: 'مقبول، لكن الربط بين السكوات والضغط يحتاج ضبطاً', en: 'Acceptable, but the squat-to-press link needs work' } },
            warning: { down: { ar: 'لا تفقد محاذاة الركبة عند النزول', en: 'Do not lose knee tracking in the descent' } },
            danger: { down: { ar: 'توقف، الاستقبال السفلي غير آمن', en: 'Stop, the lower catch is unsafe' } },
          },
          pairedWith: 'left_knee',
        },
        {
          joint: 'left_elbow',
          role: 'primary',
          startPose: { min: 70, max: 115 },
          upRange: {
            perfect: { min: 165, max: 180 },
            normal: { min: 155, max: 180 },
            pad: { min: 145, max: 180 },
          },
          downRange: {
            perfect: { min: 80, max: 98 },
            normal: { min: 72, max: 106 },
            pad: { min: 65, max: 115 },
            warning: { min: 52, max: 65 },
            danger: { min: 0, max: 52 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! إنهاء علوي كامل', en: 'Perfect! Full overhead finish' },
              down: { ar: 'ممتاز! استقبال أمامي متزن', en: 'Perfect! Balanced front-rack catch' },
            },
            normal: {
              up: { ar: 'جيد، أكمل التمدد في الأعلى', en: 'Good, finish the lockout at the top' },
              down: { ar: 'جيد، ارفع المرفق قليلاً في الاستقبال', en: 'Good, keep the elbow a bit higher in the catch' },
            },
            pad: { up: { ar: 'مقبول، لكن الدفع العلوي ما زال ناقصاً', en: 'Acceptable, but the top drive is still limited' } },
            warning: { up: { ar: 'لا تضغط والظهر يفقد الحياد', en: 'Do not press while losing spinal neutrality' } },
          },
          pairedWith: 'right_elbow',
        },
        {
          joint: 'right_elbow',
          role: 'primary',
          startPose: { min: 70, max: 115 },
          upRange: {
            perfect: { min: 165, max: 180 },
            normal: { min: 155, max: 180 },
            pad: { min: 145, max: 180 },
          },
          downRange: {
            perfect: { min: 80, max: 98 },
            normal: { min: 72, max: 106 },
            pad: { min: 65, max: 115 },
            warning: { min: 52, max: 65 },
            danger: { min: 0, max: 52 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! إنهاء علوي كامل', en: 'Perfect! Full overhead finish' },
              down: { ar: 'ممتاز! استقبال أمامي متزن', en: 'Perfect! Balanced front-rack catch' },
            },
            normal: {
              up: { ar: 'جيد، أكمل التمدد في الأعلى', en: 'Good, finish the lockout at the top' },
              down: { ar: 'جيد، ارفع المرفق قليلاً في الاستقبال', en: 'Good, keep the elbow a bit higher in the catch' },
            },
            pad: { up: { ar: 'مقبول، لكن الدفع العلوي ما زال ناقصاً', en: 'Acceptable, but the top drive is still limited' } },
            warning: { up: { ar: 'لا تضغط والظهر يفقد الحياد', en: 'Do not press while losing spinal neutrality' } },
          },
          pairedWith: 'left_elbow',
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 24 },
          range: spineSecondaryTemplateRange(),
          phaseRanges: spinePhasedRanges_dynamic(),
          phaseStateMessages: spinePhasedMessages_dynamic(),
          stateMessages: {
            perfect: { ar: 'جذع منظم طوال الربط', en: 'Trunk stays organized through the transition' },
            warning: { ar: 'لا تحوّل الحركة إلى back bend', en: 'Do not turn the rep into a back bend' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function wallSitPressPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: FRONT,
      cameraPosition: POSE_STANDING_FRONT,
      positionChecks: [
        {
          id: 'wall_sit_knee_level',
          type: 'horizontal_alignment',
          landmarks: { primary: 'left_knee', secondary: 'right_knee' },
          condition: { operator: 'approximately_equal', threshold: 0.08 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'اثبت على مستوى واحد في الجلوس الحائطي.',
            en: 'Stay level through the wall-sit position.',
          },
          severity: 'warning',
          cooldownMs: 2500,
          minErrorFrames: 5,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_elbow',
          role: 'primary',
          startPose: { min: 70, max: 115 },
          upRange: {
            perfect: { min: 165, max: 180 },
            normal: { min: 155, max: 180 },
            pad: { min: 145, max: 180 },
          },
          downRange: {
            perfect: { min: 80, max: 98 },
            normal: { min: 72, max: 108 },
            pad: { min: 65, max: 118 },
            warning: { min: 52, max: 65 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ضغط علوي كامل مع ثبات ممتاز', en: 'Full press with excellent lower-body stability' },
              down: { ar: 'استقبال متزن فوق الكتفين', en: 'Balanced catch over the shoulders' },
            },
            normal: {
              up: { ar: 'جيد، أكمل التمدد في الأعلى', en: 'Good, finish the top lockout' },
              down: { ar: 'جيد، تحكم في العودة للمستوى السفلي', en: 'Good, control the return to shoulder level' },
            },
          },
          pairedWith: 'right_elbow',
        },
        {
          joint: 'right_elbow',
          role: 'primary',
          startPose: { min: 70, max: 115 },
          upRange: {
            perfect: { min: 165, max: 180 },
            normal: { min: 155, max: 180 },
            pad: { min: 145, max: 180 },
          },
          downRange: {
            perfect: { min: 80, max: 98 },
            normal: { min: 72, max: 108 },
            pad: { min: 65, max: 118 },
            warning: { min: 52, max: 65 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ضغط علوي كامل مع ثبات ممتاز', en: 'Full press with excellent lower-body stability' },
              down: { ar: 'استقبال متزن فوق الكتفين', en: 'Balanced catch over the shoulders' },
            },
            normal: {
              up: { ar: 'جيد، أكمل التمدد في الأعلى', en: 'Good, finish the top lockout' },
              down: { ar: 'جيد، تحكم في العودة للمستوى السفلي', en: 'Good, control the return to shoulder level' },
            },
          },
          pairedWith: 'left_elbow',
        },
        {
          joint: 'left_knee',
          role: 'secondary',
          startPose: { min: 70, max: 110 },
          range: {
            perfect: { min: 80, max: 100 },
            normal: { min: 75, max: 105 },
            pad: { min: 70, max: 110 },
            warning: { min: 60, max: 70 },
          },
          stateMessages: {
            perfect: { ar: 'زاوية جلوس حائطي جيدة', en: 'Wall-sit depth is solid' },
            warning: { ar: 'لا ترتفع كثيراً أثناء الضغط', en: 'Do not drift too high while pressing' },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 24 },
          range: {
            perfect: { min: 0, max: 14 },
            normal: { min: 0, max: 20 },
            pad: { min: 0, max: 26 },
            warning: { min: 26, max: 40 },
          },
          stateMessages: {
            perfect: { ar: 'ظهر محايد على الحائط', en: 'Back stays neutral against the wall' },
            warning: { ar: 'لا تعوض بالظهر السفلي', en: 'Do not compensate through the low back' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function benchPressPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SUPINE,
      cameraPosition: POSE_SUPINE_SIDE,
      positionChecks: [
        {
          id: 'bench_wrist_elbow_stack',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_wrist', secondary: 'left_elbow' },
          condition: { operator: 'approximately_equal', threshold: 0.08 },
          activePhases: ['down'],
          errorMessage: {
            ar: 'حافظ على المعصم فوق المرفق في أسفل الضغط.',
            en: 'Keep the wrist stacked over the elbow at the bottom.',
          },
          severity: 'tip',
          cooldownMs: 2600,
          minErrorFrames: 5,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_elbow',
          role: 'primary',
          startPose: { min: 80, max: 180 },
          upRange: {
            perfect: { min: 165, max: 180 },
            normal: { min: 155, max: 180 },
            pad: { min: 145, max: 180 },
          },
          downRange: {
            perfect: { min: 72, max: 95 },
            normal: { min: 65, max: 105 },
            pad: { min: 58, max: 115 },
            warning: { min: 45, max: 58 },
            danger: { min: 0, max: 45 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! إنهاء قوي في الأعلى', en: 'Perfect! Strong top finish' },
              down: { ar: 'ممتاز! عمق ضغط مناسب', en: 'Perfect! Good bench depth' },
            },
            normal: {
              up: { ar: 'جيد، أكمل التمدد في الأعلى', en: 'Good, finish the lockout' },
              down: { ar: 'جيد، اقترب أكثر من مستوى الصدر', en: 'Good, get slightly closer to chest level' },
            },
            pad: { down: { ar: 'مقبول، لكن ما زال المدى ناقصاً', en: 'Acceptable, but range is still limited' } },
            warning: { down: { ar: 'لا تهبط أقل من تحكم الكتف', en: 'Do not lower beyond shoulder control' } },
            danger: { down: { ar: 'توقف، العمق أصبح مزعجاً للكتف', en: 'Stop, the depth is irritating the shoulder' } },
          },
          pairedWith: 'right_elbow',
        },
        {
          joint: 'right_elbow',
          role: 'primary',
          startPose: { min: 80, max: 180 },
          upRange: {
            perfect: { min: 165, max: 180 },
            normal: { min: 155, max: 180 },
            pad: { min: 145, max: 180 },
          },
          downRange: {
            perfect: { min: 72, max: 95 },
            normal: { min: 65, max: 105 },
            pad: { min: 58, max: 115 },
            warning: { min: 45, max: 58 },
            danger: { min: 0, max: 45 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! إنهاء قوي في الأعلى', en: 'Perfect! Strong top finish' },
              down: { ar: 'ممتاز! عمق ضغط مناسب', en: 'Perfect! Good bench depth' },
            },
            normal: {
              up: { ar: 'جيد، أكمل التمدد في الأعلى', en: 'Good, finish the lockout' },
              down: { ar: 'جيد، اقترب أكثر من مستوى الصدر', en: 'Good, get slightly closer to chest level' },
            },
            pad: { down: { ar: 'مقبول، لكن ما زال المدى ناقصاً', en: 'Acceptable, but range is still limited' } },
            warning: { down: { ar: 'لا تهبط أقل من تحكم الكتف', en: 'Do not lower beyond shoulder control' } },
            danger: { down: { ar: 'توقف، العمق أصبح مزعجاً للكتف', en: 'Stop, the depth is irritating the shoulder' } },
          },
          pairedWith: 'left_elbow',
        },
        {
          joint: 'left_shoulder',
          role: 'secondary',
          startPose: { min: 50, max: 100 },
          range: shoulderSecondaryTemplateRange(),
          phaseRanges: shoulderPhasedRanges_benchPress(),
          phaseStateMessages: shoulderPhasedMessages_benchPress(),
          stateMessages: {
            perfect: { ar: 'كتف منظم فوق لوح كتف ثابت', en: 'Shoulder stays organized on a stable scapula' },
            warning: { ar: 'لا تترك الكتف يندفع للأمام', en: 'Do not let the shoulder drift forward' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function scapPushupPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: PRONE,
      cameraPosition: POSE_PRONE_SIDE,
      positionChecks: [
        {
          id: 'scap_pushup_elbows_straight',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_elbow' },
          condition: { operator: 'approximately_equal', threshold: 0.12 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'التمرين من لوح الكتف لا من ثني المرفق.',
            en: 'This drill comes from the shoulder blades, not from bending the elbow.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_shoulder',
          role: 'primary',
          startPose: { min: 45, max: 95 },
          upRange: {
            perfect: { min: 78, max: 95 },
            normal: { min: 75, max: 98 },
            pad: { min: 72, max: 100 },
          },
          downRange: {
            perfect: { min: 45, max: 62 },
            normal: { min: 40, max: 66 },
            pad: { min: 35, max: 70 },
            warning: { min: 28, max: 35 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! بروز جيد للوحي الكتف', en: 'Perfect! Good scapular protraction' },
              down: { ar: 'ممتاز! رجوع متحكم به', en: 'Perfect! Controlled retraction' },
            },
            normal: {
              up: { ar: 'جيد، ادفع الأرض أكثر', en: 'Good, push the floor away more' },
              down: { ar: 'جيد، لا تسقط في الكتفين', en: 'Good, do not sink into the shoulders' },
            },
            pad: { up: { ar: 'مقبول، لكن المدى الكتفي ما زال قصيراً', en: 'Acceptable, but scapular range is still short' } },
            warning: { down: { ar: 'لا تثن المرفقين لتسرق الحركة', en: 'Do not bend the elbows to cheat the movement' } },
          },
        },
        {
          joint: 'left_elbow',
          role: 'secondary',
          startPose: { min: 155, max: 180 },
          range: {
            perfect: { min: 165, max: 180 },
            normal: { min: 158, max: 180 },
            warning: { min: 145, max: 158 },
          },
          stateMessages: {
            perfect: { ar: 'مرفق شبه مفرود طوال التمرين', en: 'Elbow stays nearly straight throughout' },
            warning: { ar: 'لا تحوّلها إلى push-up عادي', en: 'Do not turn it into a regular push-up' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

function tricepPushdownPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'pushdown_upper_arm_fixed',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_elbow' },
          condition: { operator: 'approximately_equal', threshold: 0.1 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'ثبّت العضد بجانب الجذع أثناء الدفع.',
            en: 'Keep the upper arm fixed beside the torso during the pushdown.',
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
          startPose: { min: 75, max: 110 },
          upRange: {
            perfect: { min: 160, max: 180 },
            normal: { min: 150, max: 180 },
            pad: { min: 140, max: 180 },
          },
          downRange: {
            perfect: { min: 78, max: 96 },
            normal: { min: 70, max: 104 },
            pad: { min: 62, max: 112 },
            warning: { min: 50, max: 62 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! تمدد ثلاثي كامل', en: 'Perfect! Full triceps extension' },
              down: { ar: 'ممتاز! عودة متحكم بها', en: 'Perfect! Controlled return' },
            },
            normal: {
              up: { ar: 'جيد، أكمل فرد المرفق', en: 'Good, finish the elbow extension' },
              down: { ar: 'جيد، لا تدع الحبل يسحبك بسرعة', en: 'Good, do not let the band yank you back' },
            },
            pad: { up: { ar: 'مقبول، لكن التمدد ما زال ناقصاً', en: 'Acceptable, but lockout is still incomplete' } },
            warning: { down: { ar: 'لا تحرك الكتف للأمام والخلف', en: 'Do not sway the shoulder forward and back' } },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function pikePushupPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: PRONE,
      cameraPosition: POSE_PRONE_SIDE,
      positionChecks: [
        {
          id: 'pike_hips_high',
          type: 'vertical_comparison',
          landmarks: { primary: 'left_hip', secondary: 'left_shoulder' },
          condition: { operator: 'should_not_exceed', threshold: 0.02 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'حافظ على شكل الـ V ولا تتحول إلى push-up أفقي.',
            en: 'Keep the inverted V and avoid turning it into a horizontal push-up.',
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
          startPose: { min: 90, max: 180 },
          upRange: {
            perfect: { min: 165, max: 180 },
            normal: { min: 155, max: 180 },
            pad: { min: 145, max: 180 },
          },
          downRange: {
            perfect: { min: 55, max: 82 },
            normal: { min: 48, max: 92 },
            pad: { min: 40, max: 102 },
            warning: { min: 30, max: 40 },
            danger: { min: 0, max: 30 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! دفع قوي للأعلى', en: 'Perfect! Strong overhead-style push' },
              down: { ar: 'ممتاز! رأسك يهبط بين اليدين', en: 'Perfect! Head drops cleanly between the hands' },
            },
            normal: {
              up: { ar: 'جيد، أكمل التمدد في الأعلى', en: 'Good, finish the top extension' },
              down: { ar: 'جيد، انزل عمودياً أكثر', en: 'Good, descend more vertically' },
            },
            pad: { down: { ar: 'مقبول، لكن المسار ما زال أمامياً', en: 'Acceptable, but the path is still too far forward' } },
            warning: { down: { ar: 'لا تدع المرفقين يبتعدان بشكل زائد', en: 'Do not let the elbows flare excessively' } },
            danger: { down: { ar: 'توقف، الكتف غير مرتاح في هذا المدى', en: 'Stop, the shoulder is not tolerating this range' } },
          },
          pairedWith: 'right_elbow',
        },
        {
          joint: 'left_hip',
          role: 'secondary',
          startPose: { min: 75, max: 135 },
          range: {
            perfect: { min: 85, max: 115 },
            normal: { min: 80, max: 122 },
            pad: { min: 75, max: 130 },
            warning: { min: 65, max: 75 },
          },
          stateMessages: {
            perfect: { ar: 'قمة الورك مرتفعة بالشكل الصحيح', en: 'Hip peak is set correctly' },
            warning: { ar: 'الورك منخفض أكثر من المطلوب', en: 'Hips are dropping too low' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function landminePressPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'landmine_ribs_down',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.12 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'حافظ على الأضلاع لأسفل أثناء الدفع القطري.',
            en: 'Keep the ribs down during the diagonal press.',
          },
          severity: 'warning',
          cooldownMs: 2300,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_elbow',
          role: 'primary',
          startPose: { min: 70, max: 115 },
          upRange: {
            perfect: { min: 150, max: 175 },
            normal: { min: 140, max: 180 },
            pad: { min: 130, max: 180 },
          },
          downRange: {
            perfect: { min: 82, max: 98 },
            normal: { min: 75, max: 105 },
            pad: { min: 68, max: 112 },
            warning: { min: 55, max: 68 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! ضغط قطري نظيف', en: 'Perfect! Clean diagonal press' },
              down: { ar: 'ممتاز! استقبال ثابت', en: 'Perfect! Stable catch position' },
            },
            normal: {
              up: { ar: 'جيد، أمدد اليد أكثر للأمام ولأعلى', en: 'Good, reach a little farther up and forward' },
              down: { ar: 'جيد، استقبل فوق الكتف بثبات', en: 'Good, catch over the shoulder with control' },
            },
            pad: { up: { ar: 'مقبول، لكن الذراع لا تزال قصيرة في النهاية', en: 'Acceptable, but the finishing reach is still short' } },
            warning: { up: { ar: 'لا تعوض بالظهر أو القفص الصدري', en: 'Do not compensate with the back or rib cage' } },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 22 },
          range: spineSecondaryTemplateRange(),
          phaseRanges: spinePhasedRanges_dynamic(),
          phaseStateMessages: spinePhasedMessages_dynamic(),
          stateMessages: {
            perfect: { ar: 'جذع ثابت في نصف الركبة', en: 'Trunk steady in the half-kneeling base' },
            warning: { ar: 'لا تفقد الحوض أو الجذع أثناء الضغط', en: 'Do not lose the pelvis or trunk during the press' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function overheadTricepPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'oh_tricep_upper_arm_fixed',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_elbow' },
          condition: { operator: 'approximately_equal', threshold: 0.12 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'اجعل العضد ثابتاً قرب الأذن طوال التمرين.',
            en: 'Keep the upper arm fixed near the ear throughout the rep.',
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
          startPose: { min: 60, max: 115 },
          upRange: {
            perfect: { min: 160, max: 180 },
            normal: { min: 150, max: 180 },
            pad: { min: 140, max: 180 },
          },
          downRange: {
            perfect: { min: 62, max: 92 },
            normal: { min: 55, max: 100 },
            pad: { min: 48, max: 110 },
            warning: { min: 38, max: 48 },
            danger: { min: 0, max: 38 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! تمدد علوي كامل', en: 'Perfect! Full overhead extension' },
              down: { ar: 'ممتاز! نزول متحكم به خلف الرأس', en: 'Perfect! Controlled lowering behind the head' },
            },
            normal: {
              up: { ar: 'جيد، أكمل فرد المرفق', en: 'Good, finish the elbow extension' },
              down: { ar: 'جيد، تحكم في النزول أكثر', en: 'Good, control the lowering more' },
            },
            pad: { down: { ar: 'مقبول، لكن المدى ما زال ناقصاً', en: 'Acceptable, but the range is still short' } },
            warning: { down: { ar: 'لا تفرط في النزول إن الكوع لا يحتمل', en: 'Do not force depth if the elbow does not tolerate it' } },
            danger: { down: { ar: 'توقف، يوجد ضغط حاد على الكوع أو الكتف', en: 'Stop, there is sharp elbow or shoulder stress' } },
          },
        },
        {
          joint: 'left_shoulder',
          role: 'secondary',
          startPose: { min: 145, max: 180 },
          range: {
            perfect: { min: 155, max: 180 },
            normal: { min: 148, max: 180 },
            warning: { min: 130, max: 148 },
          },
          stateMessages: {
            perfect: { ar: 'كتف ثابت فوق الرأس', en: 'Shoulder stays stacked overhead' },
            warning: { ar: 'لا تدع العضد يهرب للأمام', en: 'Do not let the upper arm drift forward' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function bridgePattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SUPINE,
      cameraPosition: POSE_SUPINE_SIDE,
      positionChecks: [
        {
          id: 'bridge_ribs_down',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.12 },
          activePhases: ['up', 'down', 'top'],
          errorMessage: {
            ar: 'ارفع الحوض دون تقوس قطني زائد.',
            en: 'Lift the hips without turning the rep into a low-back arch.',
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
          startPose: { min: 80, max: 180 },
          upRange: {
            perfect: { min: 165, max: 180 },
            normal: { min: 155, max: 180 },
            pad: { min: 145, max: 180 },
          },
          downRange: {
            perfect: { min: 90, max: 118 },
            normal: { min: 82, max: 125 },
            pad: { min: 75, max: 132 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! قفل ورك قوي', en: 'Perfect! Strong hip lockout' },
              down: { ar: 'ممتاز! عودة متحكم بها', en: 'Perfect! Controlled return' },
            },
            normal: {
              up: { ar: 'جيد، ادفع الورك أعلى', en: 'Good, drive the hips higher' },
              down: { ar: 'جيد، انزل ببطء أكثر', en: 'Good, lower more slowly' },
            },
            pad: { up: { ar: 'مقبول، لكن الرفع ما زال ناقصاً', en: 'Acceptable, but the lift is still short' } },
            warning: { up: { ar: 'لا تعوض من أسفل الظهر', en: 'Do not compensate through the low back' } },
            danger: { up: { ar: 'توقف، يوجد ضغط قطني غير مريح', en: 'Stop, there is uncomfortable lumbar loading' } },
          },
          pairedWith: 'right_hip',
        },
        {
          joint: 'left_knee',
          role: 'secondary',
          startPose: { min: 70, max: 120 },
          range: {
            perfect: { min: 80, max: 105 },
            normal: { min: 75, max: 110 },
            pad: { min: 70, max: 116 },
            warning: { min: 60, max: 70 },
          },
          stateMessages: {
            perfect: { ar: 'زاوية الركبة ثابتة', en: 'Knee angle remains stable' },
            warning: { ar: 'لا تدع القدمين يبتعدان كثيراً أو تقتربان جداً', en: 'Do not let the feet drift too far or too close' },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 24 },
          range: {
            perfect: { min: 0, max: 14 },
            normal: { min: 0, max: 20 },
            pad: { min: 0, max: 26 },
            warning: { min: 26, max: 40 },
          },
          stateMessages: {
            perfect: { ar: 'ظهر منظم مع شد جذع جيد', en: 'Spine stays organized with good brace' },
            warning: { ar: 'لا تحوّل الجسر إلى انحناء قطني', en: 'Do not turn the bridge into a lumbar arch' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_CORE,
    },
  ];
}

/** Kneeling Nordic eccentric — tall line from knees; controlled knee flexion on lowering */
function nordicCurlPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'nordic_hip_shoulder_line',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.12 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'حافظ على خط مستقيم من الركبة للكتف — لا تطوّي من الورك فقط.',
            en: 'Keep a straight line from knee to shoulder — don’t break at the hips only.',
          },
          severity: 'warning',
          cooldownMs: 2400,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_knee',
          role: 'primary',
          invertIndicator: true,
          startPose: { min: 130, max: 180 },
          upRange: {
            perfect: { min: 165, max: 180 },
            normal: { min: 158, max: 180 },
            pad: { min: 150, max: 180 },
          },
          downRange: {
            perfect: { min: 95, max: 125 },
            normal: { min: 88, max: 135 },
            pad: { min: 80, max: 145 },
            warning: { min: 68, max: 80 },
            danger: { min: 0, max: 68 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! بداية مستقيمة فوق الركبة', en: 'Perfect! Tall start over the knees' },
              down: { ar: 'ممتاز! نزول إحصائي متحكم به', en: 'Perfect! Controlled eccentric lowering' },
            },
            normal: {
              up: { ar: 'جيد، ارجع للخط المستقيم بالكامل', en: 'Good, return fully to the tall line' },
              down: { ar: 'جيد، قلل السرعة في النصف السفلي', en: 'Good, slow down in the bottom half' },
            },
            pad: { down: { ar: 'مقبول، لكن المدى ما زال محافظاً', en: 'Acceptable, but range is still conservative' } },
            warning: { down: { ar: 'لا تطوّي الظهر لتعويض الهمسترنج', en: 'Do not fold the back to cheat the hamstrings' } },
            danger: { down: { ar: 'توقف — ضغط حاد خلف الركبة', en: 'Stop — sharp strain behind the knee' } },
          },
        },
        {
          joint: 'left_hip',
          role: 'secondary',
          startPose: { min: 150, max: 180 },
          range: {
            perfect: { min: 165, max: 180 },
            normal: { min: 158, max: 180 },
            pad: { min: 150, max: 180 },
            warning: { min: 135, max: 150 },
          },
          stateMessages: {
            perfect: { ar: 'ورك ممدود في خط الركبة', en: 'Hip extended in line with the knee' },
            warning: { ar: 'لا تطوّي الورك مبكراً عن الصدر', en: 'Do not break at the hip early into the chest' },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 25 },
          range: spineSecondaryTemplateRange(),
          phaseRanges: spinePhasedRanges_dynamic(),
          phaseStateMessages: spinePhasedMessages_dynamic(),
          stateMessages: {
            perfect: { ar: 'عمود فقري منظم في النزول', en: 'Spine stays organized through the lowering' },
            warning: { ar: 'قلل المدى إذا انهار القطن', en: 'Reduce range if the low back collapses' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

/** Bridge / slider curl — knee flexes while hips stay elevated */
function hamstringSlidePattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SUPINE,
      cameraPosition: POSE_SUPINE_SIDE,
      positionChecks: [
        {
          id: 'ham_curl_hips_up',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.14 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'أبقِ الحوض مرتفعاً أثناء ثني الركبة.',
            en: 'Keep the pelvis elevated while flexing the knee.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_knee',
          role: 'primary',
          invertIndicator: true,
          startPose: { min: 70, max: 180 },
          upRange: {
            perfect: { min: 160, max: 180 },
            normal: { min: 150, max: 180 },
            pad: { min: 140, max: 180 },
          },
          downRange: {
            perfect: { min: 60, max: 95 },
            normal: { min: 55, max: 105 },
            pad: { min: 50, max: 115 },
            warning: { min: 40, max: 50 },
            danger: { min: 0, max: 40 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! فرد منظم للبداية', en: 'Perfect! Organized reset to the long position' },
              down: { ar: 'ممتاز! ثني ركبة قوي للهمسترنج', en: 'Perfect! Strong hamstring-driven knee flexion' },
            },
            normal: {
              up: { ar: 'جيد، تحكم أكثر في العودة', en: 'Good, control the return more' },
              down: { ar: 'جيد، اسحب الكعب أقرب أكثر', en: 'Good, pull the heel in a bit more' },
            },
            pad: { down: { ar: 'مقبول، لكن المدى الخلفي ما زال متوسطاً', en: 'Acceptable, but the curl range is still moderate' } },
            warning: { down: { ar: 'لا تفقد ارتفاع الحوض أثناء السحب', en: 'Do not lose hip height during the curl' } },
            danger: { down: { ar: 'توقف، الشد الخلفي حاد جداً', en: 'Stop, posterior-chain strain is too sharp' } },
          },
          pairedWith: 'right_knee',
        },
        {
          joint: 'left_hip',
          role: 'secondary',
          startPose: { min: 130, max: 180 },
          range: hipSecondaryTemplateRange(),
          phaseRanges: hipPhasedRanges_bridgeHamstringSlide(),
          phaseStateMessages: hipPhasedMessages_bridgeHamstringSlide(),
          stateMessages: {
            perfect: { ar: 'حوض مرتفع وثابت', en: 'Pelvis stays lifted and stable' },
            warning: { ar: 'لا تدع الحوض يهبط في الأسفل', en: 'Do not let the pelvis sag down' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function singleLegHingePattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'single_leg_rdl_spine',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.1 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'حافظ على ظهر محايد أثناء مفصل الورك الأحادي.',
            en: 'Keep a neutral spine during the single-leg hinge.',
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
          startPose: { min: 120, max: 180 },
          upRange: {
            perfect: { min: 168, max: 180 },
            normal: { min: 160, max: 180 },
            pad: { min: 152, max: 180 },
          },
          downRange: {
            perfect: { min: 72, max: 98 },
            normal: { min: 65, max: 108 },
            pad: { min: 58, max: 118 },
            warning: { min: 45, max: 58 },
            danger: { min: 0, max: 45 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! قفل ورك قوي', en: 'Perfect! Strong hip lockout' },
              down: { ar: 'ممتاز! مفصل ورك عميق ومتزن', en: 'Perfect! Deep, balanced hinge' },
            },
            normal: {
              up: { ar: 'جيد، أنهِ الوقوف بالكامل', en: 'Good, finish the stand completely' },
              down: { ar: 'جيد، تحكم أكثر في الاتزان', en: 'Good, control the balance more' },
            },
            pad: { down: { ar: 'مقبول، لكن الاتزان ما زال ضعيفاً', en: 'Acceptable, but balance is still limited' } },
            warning: { down: { ar: 'لا تدع الحوض يلتف أو يفتح', en: 'Do not let the pelvis rotate open' } },
            danger: { down: { ar: 'توقف، فقدت السيطرة الأحادية', en: 'Stop, single-leg control is lost' } },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 24 },
          range: spineSecondaryTemplateRange(),
          phaseRanges: spinePhasedRanges_dynamic(),
          phaseStateMessages: spinePhasedMessages_dynamic(),
          stateMessages: {
            perfect: { ar: 'جذع ثابت طوال التوازن', en: 'Torso remains stable through balance' },
            warning: { ar: 'لا تنهَر بالظهر لتعويض الاتزان', en: 'Do not fold the spine to save balance' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function mobilityHingePattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'mobility_hinge_neutral',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.12 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'تحرك من الورك ببطء مع بقاء العمود الفقري منظماً.',
            en: 'Move slowly from the hips while keeping the spine organized.',
          },
          severity: 'tip',
          cooldownMs: 2600,
          minErrorFrames: 5,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_hip',
          role: 'primary',
          startPose: { min: 90, max: 180 },
          upRange: {
            perfect: { min: 165, max: 180 },
            normal: { min: 155, max: 180 },
            pad: { min: 145, max: 180 },
          },
          downRange: {
            perfect: { min: 60, max: 95 },
            normal: { min: 52, max: 105 },
            pad: { min: 45, max: 115 },
            warning: { min: 35, max: 45 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! عودة من الورك بدون اندفاع', en: 'Perfect! Controlled return from the hips' },
              down: { ar: 'ممتاز! مفصل ورك نظيف ومريح', en: 'Perfect! Clean, comfortable hinge' },
            },
            normal: {
              up: { ar: 'جيد، عد ببطء أكثر', en: 'Good, return a little more slowly' },
              down: { ar: 'جيد، زد المدى فقط إذا بقي الظهر منظماً', en: 'Good, only increase range if the spine stays organized' },
            },
            pad: { down: { ar: 'مقبول، ما زال المدى المحافظ مناسباً', en: 'Acceptable, but a conservative range is still appropriate' } },
            warning: { down: { ar: 'لا تطارد العمق على حساب الراحة', en: 'Do not chase depth at the expense of comfort' } },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 28 },
          range: spineSecondaryTemplateRange(),
          phaseRanges: spinePhasedRanges_dynamic(),
          phaseStateMessages: spinePhasedMessages_dynamic(),
          stateMessages: {
            perfect: { ar: 'الظهر منظم طوال الحركة', en: 'The spine stays organized throughout' },
            warning: { ar: 'خفف المدى إذا بدأ الظهر ينهار', en: 'Reduce range if the back starts collapsing' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

function ballisticHingePattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'ballistic_hinge_not_squat',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.12 },
          activePhases: ['down'],
          errorMessage: {
            ar: 'التحميل من مفصل الورك، وليس سكواتاً عمودياً.',
            en: 'Load through the hip hinge, not a vertical squat.',
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
          startPose: { min: 90, max: 180 },
          upRange: {
            perfect: { min: 168, max: 180 },
            normal: { min: 160, max: 180 },
            pad: { min: 150, max: 180 },
          },
          downRange: {
            perfect: { min: 82, max: 105 },
            normal: { min: 75, max: 115 },
            pad: { min: 68, max: 122 },
            warning: { min: 55, max: 68 },
            danger: { min: 0, max: 55 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! قفل ورك انفجاري ونظيف', en: 'Perfect! Crisp, explosive hip lockout' },
              down: { ar: 'ممتاز! تحميل خلفي مضبوط', en: 'Perfect! Clean back-swing load' },
            },
            normal: {
              up: { ar: 'جيد، اضرب الورك للأمام أسرع', en: 'Good, snap the hips forward faster' },
              down: { ar: 'جيد، اجلس في الورك لا في الركبة', en: 'Good, sit into the hips, not the knees' },
            },
            pad: { up: { ar: 'مقبول، لكن القوة الانفجارية ما زالت محدودة', en: 'Acceptable, but explosive intent is still limited' } },
            warning: { down: { ar: 'لا ترفع بالذراعين بدلاً من الورك', en: 'Do not lift with the arms instead of the hips' } },
            danger: { down: { ar: 'توقف، الظهر يفقد الحياد أثناء التحميل', en: 'Stop, the back is losing neutrality during the load' } },
          },
          pairedWith: 'right_hip',
        },
        {
          joint: 'left_knee',
          role: 'secondary',
          startPose: { min: 135, max: 180 },
          range: {
            perfect: { min: 145, max: 175 },
            normal: { min: 138, max: 180 },
            pad: { min: 130, max: 180 },
            warning: { min: 118, max: 130 },
          },
          stateMessages: {
            perfect: { ar: 'الركبة تنثني قليلاً فقط', en: 'The knees are only softly bent' },
            warning: { ar: 'لا تحوّلها إلى سكوات عميق', en: 'Do not turn it into a deep squat' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function pushPressPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: FRONT,
      cameraPosition: POSE_STANDING_FRONT,
      positionChecks: [
        {
          id: 'push_press_sync',
          type: 'horizontal_alignment',
          landmarks: { primary: 'left_wrist', secondary: 'right_wrist' },
          condition: { operator: 'approximately_equal', threshold: 0.09 },
          activePhases: ['up', 'top'],
          errorMessage: {
            ar: 'اضغط بذراعين متزامنتين بعد dip قصير ونظيف.',
            en: 'Press with both arms together after a short, clean dip.',
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
          startPose: { min: 70, max: 115 },
          upRange: {
            perfect: { min: 165, max: 180 },
            normal: { min: 155, max: 180 },
            pad: { min: 145, max: 180 },
          },
          downRange: {
            perfect: { min: 82, max: 98 },
            normal: { min: 74, max: 106 },
            pad: { min: 66, max: 114 },
            warning: { min: 54, max: 66 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! إنهاء علوي قوي بعد الدفع', en: 'Perfect! Strong overhead finish after the drive' },
              down: { ar: 'ممتاز! استقبال قصير ونظيف', en: 'Perfect! Short, clean dip position' },
            },
            normal: {
              up: { ar: 'جيد، أنهِ التمدد بالكامل', en: 'Good, finish the lockout fully' },
              down: { ar: 'جيد، dip أقصر وأكثر عمودية', en: 'Good, keep the dip shorter and more vertical' },
            },
            pad: { up: { ar: 'مقبول، لكن النقل من الرجلين لليدين ما زال ضعيفاً', en: 'Acceptable, but the leg-to-arm transfer is still weak' } },
            warning: { down: { ar: 'لا تحول الـ dip إلى سكوات عميق', en: 'Do not turn the dip into a deep squat' } },
          },
          pairedWith: 'right_elbow',
        },
        {
          joint: 'left_knee',
          role: 'secondary',
          startPose: { min: 120, max: 180 },
          range: kneeSecondaryTemplateRange(),
          phaseRanges: kneePhasedRanges_pushPress(),
          phaseStateMessages: kneePhasedMessages_pushPress(),
          stateMessages: {
            perfect: { ar: 'Dip قصير وعمودي', en: 'Short, vertical dip' },
            warning: { ar: 'لا تغُص بالركبة أكثر من اللازم', en: 'Do not dive too deep at the knees' },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 22 },
          range: spineSecondaryTemplateRange(),
          phaseRanges: spinePhasedRanges_dynamic(),
          phaseStateMessages: spinePhasedMessages_dynamic(),
          stateMessages: {
            perfect: { ar: 'الجذع يبقى منظماً أثناء النقل الانفجاري', en: 'The trunk stays organized through the explosive transfer' },
            warning: { ar: 'لا تعوض بالظهر السفلي أثناء الدفع', en: 'Do not compensate with the low back in the drive' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function farmerCarryPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'farmer_ribs_stack',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.1 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'سر بخط مستقيم دون ميل جانبي أو بروز للأضلاع.',
            en: 'Walk tall without side-bending or rib flare.',
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
          startPose: { min: 160, max: 180 },
          range: {
            perfect: { min: 170, max: 180 },
            normal: { min: 162, max: 180 },
            pad: { min: 152, max: 180 },
            warning: { min: 138, max: 152 },
            danger: { min: 0, max: 138 },
          },
          stateMessages: {
            perfect: { ar: 'مشية مستقرة تحت الحمل', en: 'Stable gait under load' },
            warning: { ar: 'لا تمِل بالجذع أو الحوض', en: 'Do not tilt the trunk or pelvis' },
            danger: { ar: 'توقف، فقدت وضعية الحمل', en: 'Stop, loaded posture is lost' },
          },
        },
        {
          joint: 'left_shoulder',
          role: 'secondary',
          startPose: { min: 15, max: 60 },
          range: {
            perfect: { min: 22, max: 42 },
            normal: { min: 18, max: 48 },
            pad: { min: 15, max: 54 },
            warning: { min: 54, max: 70 },
          },
          stateMessages: {
            perfect: { ar: 'كتف منخفض ومتماسك بجانب الجسم', en: 'Shoulder stays packed down by the side' },
            warning: { ar: 'لا ترفع الكتف تحت الحمل', en: 'Do not shrug under load' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function waiterWalkPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'waiter_arm_stack',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_wrist', secondary: 'left_shoulder' },
          condition: { operator: 'approximately_equal', threshold: 0.1 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'أبقِ الحمل فوق منتصف الكتف أثناء المشي.',
            en: 'Keep the load stacked over the mid-shoulder while walking.',
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
          startPose: { min: 160, max: 180 },
          range: {
            perfect: { min: 170, max: 180 },
            normal: { min: 162, max: 180 },
            pad: { min: 152, max: 180 },
            warning: { min: 138, max: 152 },
            danger: { min: 0, max: 138 },
          },
          stateMessages: {
            perfect: { ar: 'مشية ثابتة مع جذع منظم', en: 'Stable gait with an organized trunk' },
            warning: { ar: 'لا تمِل تحت الحمل العلوي', en: 'Do not lean under the overhead load' },
            danger: { ar: 'توقف، الحمل يخرجك من الخط', en: 'Stop, the load is pulling you out of line' },
          },
        },
        {
          joint: 'left_shoulder',
          role: 'secondary',
          startPose: { min: 145, max: 180 },
          range: {
            perfect: { min: 160, max: 180 },
            normal: { min: 152, max: 180 },
            pad: { min: 145, max: 180 },
            warning: { min: 130, max: 145 },
          },
          stateMessages: {
            perfect: { ar: 'كتف مكدس جيداً فوق الرأس', en: 'Shoulder is stacked well overhead' },
            warning: { ar: 'لا تسمح للذراع أن تسقط للأمام', en: 'Do not let the arm drift forward' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function deadBugPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SUPINE,
      cameraPosition: POSE_SUPINE_SIDE,
      positionChecks: [
        {
          id: 'deadbug_low_back',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.16 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'حافظ على أسفل الظهر ثابتاً أثناء تحريك الرجل.',
            en: 'Keep the low back steady while moving the leg.',
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
          startPose: { min: 75, max: 180 },
          upRange: {
            perfect: { min: 140, max: 175 },
            normal: { min: 130, max: 180 },
            pad: { min: 120, max: 180 },
          },
          downRange: {
            perfect: { min: 78, max: 102 },
            normal: { min: 72, max: 110 },
            pad: { min: 65, max: 118 },
            warning: { min: 55, max: 65 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! رجل طويلة مع جذع ثابت', en: 'Perfect! Long leg with stable trunk' },
              down: { ar: 'ممتاز! عودة دقيقة لموضع البداية', en: 'Perfect! Precise return to start' },
            },
            normal: {
              up: { ar: 'جيد، أمدد الرجل أكثر دون فقدان الجذع', en: 'Good, reach longer without losing the trunk' },
              down: { ar: 'جيد، أعد الركبة فوق الورك بدقة', en: 'Good, return the knee over the hip precisely' },
            },
            pad: { up: { ar: 'مقبول، لكن التمدد ما زال قصيراً', en: 'Acceptable, but the reach is still short' } },
            warning: { up: { ar: 'لا تدع أسفل الظهر يرتفع عن الأرض', en: 'Do not let the low back peel off the floor' } },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 20 },
          range: {
            perfect: { min: 0, max: 12 },
            normal: { min: 0, max: 18 },
            pad: { min: 0, max: 24 },
            warning: { min: 24, max: 36 },
          },
          stateMessages: {
            perfect: { ar: 'جذع محايد ومثبت', en: 'Neutral, anchored trunk' },
            warning: { ar: 'خفف المدى حتى يعود الظهر للاستقرار', en: 'Reduce range until the back is stable again' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_CORE,
    },
  ];
}

/** Alternating heel taps — keep low back anchored while reaching heels toward floor */
function heelTapsPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SUPINE,
      cameraPosition: POSE_SUPINE_SIDE,
      positionChecks: [
        {
          id: 'heel_taps_low_back',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.16 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'لا ترفع أسفل الظهر عن الأرض عند مد الكعب.',
            en: 'Do not peel the low back off the floor as you reach the heel.',
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
          startPose: { min: 85, max: 180 },
          upRange: {
            perfect: { min: 138, max: 170 },
            normal: { min: 130, max: 180 },
            pad: { min: 125, max: 180 },
          },
          downRange: {
            perfect: { min: 88, max: 108 },
            normal: { min: 82, max: 115 },
            pad: { min: 75, max: 120 },
            warning: { min: 62, max: 75 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! فخذ مرتفع مع ظهر ملتصق', en: 'Perfect! Thigh lifts while back stays down' },
              down: { ar: 'ممتاز! كعب يقترب من الأرض بتحكم', en: 'Perfect! Heel approaches the floor with control' },
            },
            normal: {
              up: { ar: 'جيد، أبقِ الركبة فوق الورك أكثر', en: 'Good, keep the knee stacked over the hip more' },
              down: { ar: 'جيد، مد الكعب دون فقدان الجذع', en: 'Good, reach the heel without losing the trunk' },
            },
            pad: { down: { ar: 'مقبول، لكن المدى ما زال قصيراً', en: 'Acceptable, but the reach is still short' } },
            warning: { down: { ar: 'لا تدع أسفل الظهر يرتفع', en: 'Do not let the low back lift off' } },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 20 },
          range: {
            perfect: { min: 0, max: 12 },
            normal: { min: 0, max: 18 },
            pad: { min: 0, max: 24 },
            warning: { min: 24, max: 36 },
          },
          stateMessages: {
            perfect: { ar: 'جذع محايد ومثبت', en: 'Neutral, anchored trunk' },
            warning: { ar: 'خفف المدى حتى يعود الظهر للاستقرار', en: 'Reduce range until the back is stable again' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_CORE,
    },
  ];
}

function birdDogPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: PRONE,
      cameraPosition: POSE_PRONE_SIDE,
      positionChecks: [
        {
          id: 'bird_dog_trunk_quiet',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.14 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'لا تسمح للجذع بالدوران أو الهبوط أثناء المد.',
            en: 'Do not let the trunk rotate or sag during the reach.',
          },
          severity: 'warning',
          cooldownMs: 2400,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_hip',
          role: 'primary',
          startPose: { min: 75, max: 180 },
          upRange: {
            perfect: { min: 160, max: 180 },
            normal: { min: 150, max: 180 },
            pad: { min: 140, max: 180 },
          },
          downRange: {
            perfect: { min: 85, max: 110 },
            normal: { min: 78, max: 118 },
            pad: { min: 70, max: 126 },
            warning: { min: 58, max: 70 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! مد رجل قوي مع ثبات الجذع', en: 'Perfect! Strong leg reach with a stable trunk' },
              down: { ar: 'ممتاز! عودة محكمة تحت الورك', en: 'Perfect! Controlled reset under the hip' },
            },
            normal: {
              up: { ar: 'جيد، مد الرجل أكثر للخلف', en: 'Good, reach the leg farther back' },
              down: { ar: 'جيد، عد ببطء أكثر', en: 'Good, return more slowly' },
            },
            pad: { up: { ar: 'مقبول، لكن المدى ما زال قصيراً', en: 'Acceptable, but the reach is still short' } },
            warning: { up: { ar: 'لا تفتح الحوض جانبياً', en: 'Do not open the pelvis sideways' } },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 20 },
          range: {
            perfect: { min: 0, max: 12 },
            normal: { min: 0, max: 18 },
            pad: { min: 0, max: 24 },
            warning: { min: 24, max: 36 },
          },
          stateMessages: {
            perfect: { ar: 'جذع ثابت ومحايد', en: 'Stable, neutral trunk' },
            warning: { ar: 'قلل مدى الرجل حتى يعود الجذع للثبات', en: 'Shorten the reach until trunk control returns' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_CORE,
    },
  ];
}

function pallofPressPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: FRONT,
      cameraPosition: POSE_STANDING_FRONT,
      positionChecks: [
        {
          id: 'pallof_square',
          type: 'horizontal_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'right_shoulder' },
          condition: { operator: 'approximately_equal', threshold: 0.08 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'أبقِ الكتفين والحوض في مواجهة واحدة — لا تستدر مع المقاومة.',
            en: 'Keep shoulders and pelvis square — do not rotate with the resistance.',
          },
          severity: 'warning',
          cooldownMs: 2400,
          minErrorFrames: 5,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_elbow',
          role: 'primary',
          startPose: { min: 75, max: 180 },
          upRange: {
            perfect: { min: 160, max: 180 },
            normal: { min: 150, max: 180 },
            pad: { min: 140, max: 180 },
          },
          downRange: {
            perfect: { min: 78, max: 98 },
            normal: { min: 70, max: 106 },
            pad: { min: 62, max: 114 },
            warning: { min: 50, max: 62 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! ذراع ممتدة دون دوران جذع', en: 'Perfect! Full reach without trunk rotation' },
              down: { ar: 'ممتاز! عودة متحكم بها للصدر', en: 'Perfect! Controlled return to the chest' },
            },
            normal: {
              up: { ar: 'جيد، امدد الذراع أكثر مع ثبات الجذع', en: 'Good, reach farther while keeping the trunk still' },
              down: { ar: 'جيد، عد ببطء أكثر', en: 'Good, return more slowly' },
            },
            pad: { up: { ar: 'مقبول، لكن المقاومة ما زالت تغلب الجذع', en: 'Acceptable, but the resistance is still winning the trunk' } },
            warning: { up: { ar: 'خفف المقاومة إذا بدأ الجذع يلتف', en: 'Reduce resistance if the trunk starts rotating' } },
          },
          pairedWith: 'right_elbow',
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 16 },
          range: {
            perfect: { min: 0, max: 10 },
            normal: { min: 0, max: 14 },
            pad: { min: 0, max: 18 },
            warning: { min: 18, max: 30 },
          },
          stateMessages: {
            perfect: { ar: 'مقاومة ممتازة للدوران', en: 'Excellent anti-rotation control' },
            warning: { ar: 'الجذع يلف مع الحبل', en: 'The trunk is rotating with the band' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_CORE,
    },
  ];
}

function antiRotationHoldPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: FRONT,
      cameraPosition: POSE_STANDING_FRONT,
      positionChecks: [
        {
          id: 'anti_rotation_hold_square',
          type: 'horizontal_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'right_shoulder' },
          condition: { operator: 'approximately_equal', threshold: 0.08 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'اثبت أمام المقاومة بدون دوران جذع.',
            en: 'Stay square to the resistance without trunk rotation.',
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
          startPose: { min: 0, max: 16 },
          range: {
            perfect: { min: 0, max: 10 },
            normal: { min: 0, max: 14 },
            pad: { min: 0, max: 18 },
            warning: { min: 18, max: 30 },
            danger: { min: 30, max: 60 },
          },
          stateMessages: {
            perfect: { ar: 'ثبات ممتاز ضد الدوران', en: 'Excellent anti-rotation brace' },
            normal: { ar: 'جيد، أبقِ الأضلاع والحوض ثابتين', en: 'Good, keep the ribs and pelvis quiet' },
            pad: { ar: 'مقبول، لكن الجذع يتحرك قليلاً', en: 'Acceptable, but the trunk is drifting' },
            warning: { ar: 'خفف المقاومة أو قرّب اليدين', en: 'Reduce resistance or bring the hands closer' },
            danger: { ar: 'توقف، الجذع لم يعد ثابتاً', en: 'Stop, the trunk is no longer stable' },
          },
        },
        {
          joint: 'left_shoulder',
          role: 'secondary',
          startPose: { min: 70, max: 120 },
          range: {
            perfect: { min: 82, max: 105 },
            normal: { min: 76, max: 112 },
            pad: { min: 70, max: 118 },
          },
          stateMessages: {
            perfect: { ar: 'ذراعان ثابتتان أمام الصدر', en: 'Arms stay stable in front of the chest' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_CORE,
    },
  ];
}

function backExtensionRepPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: PRONE,
      cameraPosition: POSE_PRONE_SIDE,
      positionChecks: [
        {
          id: 'back_ext_no_hyper',
          type: 'vertical_comparison',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'should_not_exceed', threshold: 0.12 },
          activePhases: ['up'],
          errorMessage: {
            ar: 'ارفع الجذع حتى الحياد أو أعلى قليلاً فقط — لا تفرط في التمدد.',
            en: 'Raise the trunk to neutral or slightly above only — do not hyperextend.',
          },
          severity: 'warning',
          cooldownMs: 2400,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'spine',
          role: 'primary',
          startPose: { min: 0, max: 40 },
          upRange: {
            perfect: { min: 22, max: 34 },
            normal: { min: 20, max: 38 },
            pad: { min: 18, max: 42 },
          },
          downRange: {
            perfect: { min: 0, max: 12 },
            normal: { min: 0, max: 14 },
            pad: { min: 0, max: 16 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! تمدد ظهري مضبوط', en: 'Perfect! Controlled back extension' },
              down: { ar: 'ممتاز! عودة محايدة', en: 'Perfect! Neutral return' },
            },
            normal: {
              up: { ar: 'جيد، ارفع حتى الحياد ثم توقف', en: 'Good, lift to neutral and stop there' },
              down: { ar: 'جيد، انزل ببطء أكثر', en: 'Good, lower more slowly' },
            },
            pad: { up: { ar: 'مقبول، لكن المدى ما زال ضعيفاً', en: 'Acceptable, but the extension range is still limited' } },
            warning: { up: { ar: 'لا تجعل الحركة من أسفل الظهر فقط', en: 'Do not make the movement low-back only' } },
          },
        },
        {
          joint: 'left_hip',
          role: 'secondary',
          startPose: { min: 145, max: 180 },
          range: {
            perfect: { min: 160, max: 180 },
            normal: { min: 152, max: 180 },
            pad: { min: 145, max: 180 },
            warning: { min: 130, max: 145 },
          },
          stateMessages: {
            perfect: { ar: 'ورك ثابت على المقعد', en: 'Hips stay stable on the pad/bench' },
            warning: { ar: 'لا تفقد ثبات الحوض أثناء الرفع', en: 'Do not lose pelvic stability during the lift' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function reverseCrunchPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SUPINE,
      cameraPosition: POSE_SUPINE_SIDE,
      positionChecks: [
        {
          id: 'reverse_crunch_pelvis_roll',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_hip', secondary: 'left_shoulder' },
          condition: { operator: 'approximately_equal', threshold: 0.16 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'لف الحوض لأعلى بتحكم بدل التأرجح بالساقين.',
            en: 'Roll the pelvis up under control instead of swinging the legs.',
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
          invertIndicator: true,
          startPose: { min: 50, max: 170 },
          upRange: {
            perfect: { min: 130, max: 165 },
            normal: { min: 122, max: 170 },
            pad: { min: 115, max: 175 },
          },
          downRange: {
            perfect: { min: 55, max: 90 },
            normal: { min: 50, max: 100 },
            pad: { min: 45, max: 110 },
            warning: { min: 35, max: 45 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! تمدد منظم في البداية', en: 'Perfect! Organized start position' },
              down: { ar: 'ممتاز! لف حوض قوي للأعلى', en: 'Perfect! Strong pelvic roll-up' },
            },
            normal: {
              up: { ar: 'جيد، ابدأ من وضع أطول قليلاً', en: 'Good, start from a slightly longer position' },
              down: { ar: 'جيد، قرّب الركبتين أكثر للأعلى', en: 'Good, draw the knees higher up' },
            },
            pad: { down: { ar: 'مقبول، لكن الرفع من الحوض ما زال محدوداً', en: 'Acceptable, but pelvic lift is still limited' } },
            warning: { down: { ar: 'لا تتأرجح بالوركين لتكسب عدة', en: 'Do not swing the hips to steal the rep' } },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 24 },
          range: {
            perfect: { min: 0, max: 16 },
            normal: { min: 0, max: 22 },
            pad: { min: 0, max: 28 },
            warning: { min: 28, max: 40 },
          },
          stateMessages: {
            perfect: { ar: 'جذع ثابت والرفع من أسفل البطن', en: 'Trunk stays set and the lift comes from the lower abs' },
            warning: { ar: 'لا تترك أسفل الظهر ينهار بعنف', en: 'Do not let the low back crash down' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_CORE,
    },
  ];
}

function vUpPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SUPINE,
      cameraPosition: POSE_SUPINE_SIDE,
      positionChecks: [
        {
          id: 'vup_balance',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.16 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'اجمع الصدر والساقين بتحكم بدل الرمي السريع.',
            en: 'Bring the chest and legs together under control instead of throwing them fast.',
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
          invertIndicator: true,
          startPose: { min: 35, max: 170 },
          upRange: {
            perfect: { min: 125, max: 165 },
            normal: { min: 115, max: 170 },
            pad: { min: 105, max: 170 },
          },
          downRange: {
            perfect: { min: 40, max: 72 },
            normal: { min: 35, max: 82 },
            pad: { min: 30, max: 92 },
            warning: { min: 22, max: 30 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! وضع البداية طويل ومنظم', en: 'Perfect! Long and organized start' },
              down: { ar: 'ممتاز! تجميع قوي نحو شكل V', en: 'Perfect! Strong compression into the V' },
            },
            normal: {
              up: { ar: 'جيد، حافظ على امتداد أنظف في الأسفل', en: 'Good, keep a cleaner extension at the bottom' },
              down: { ar: 'جيد، اجمع الصدر والساقين أكثر', en: 'Good, compress the chest and legs more' },
            },
            pad: { down: { ar: 'مقبول، لكن شكل الـ V ما زال ناقصاً', en: 'Acceptable, but the V position is still incomplete' } },
            warning: { down: { ar: 'لا تستخدم اندفاعاً سريعاً بدلاً من التحكم', en: 'Do not replace control with momentum' } },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 65 },
          range: {
            perfect: { min: 0, max: 75 },
            normal: { min: 0, max: 78 },
            pad: { min: 0, max: 82 },
            warning: { min: 60, max: 85 },
            danger: { min: 80, max: 95 },
          },
          phaseRanges: spinePhasedRanges_compoundCore(),
          phaseStateMessages: spinePhasedMessages_compoundCore(),
          stateMessages: {
            perfect: { ar: 'جذع قوي ومتماسك في التجميع', en: 'Strong trunk through the compression' },
            warning: { ar: 'لا تسحب الرقبة أو تنهار في العودة', en: 'Do not yank the neck or collapse on the return' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_CORE,
    },
  ];
}

function bicycleCrunchPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SUPINE,
      cameraPosition: POSE_SUPINE_SIDE,
      positionChecks: [
        {
          id: 'bicycle_no_yank',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.18 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'لف الجذع بتحكم ولا تسحب الرقبة بالمرفق.',
            en: 'Rotate the trunk under control and do not yank the neck with the elbow.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_knee',
          role: 'primary',
          invertIndicator: true,
          startPose: { min: 40, max: 180 },
          upRange: {
            perfect: { min: 130, max: 180 },
            normal: { min: 120, max: 180 },
            pad: { min: 110, max: 180 },
          },
          downRange: {
            perfect: { min: 45, max: 85 },
            normal: { min: 40, max: 95 },
            pad: { min: 35, max: 105 },
            warning: { min: 25, max: 35 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! ساق ممتدة بوضوح', en: 'Perfect! Clear long-leg extension' },
              down: { ar: 'ممتاز! ركبة مقربة بقوة مع اللف', en: 'Perfect! Strong knee drive with rotation' },
            },
            normal: {
              up: { ar: 'جيد، مد الساق أكثر', en: 'Good, lengthen the leg more' },
              down: { ar: 'جيد، قرّب الركبة أكثر نحو الصدر', en: 'Good, drive the knee closer to the chest' },
            },
            pad: { down: { ar: 'مقبول، لكن التنسيق ما زال متوسطاً', en: 'Acceptable, but coordination is still average' } },
            warning: { down: { ar: 'لا تسرع على حساب اللف والتحكم', en: 'Do not rush at the expense of rotation and control' } },
          },
          pairedWith: 'right_knee',
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 60 },
          range: {
            perfect: { min: 0, max: 75 },
            normal: { min: 0, max: 78 },
            pad: { min: 0, max: 82 },
            warning: { min: 60, max: 85 },
            danger: { min: 80, max: 95 },
          },
          phaseRanges: spinePhasedRanges_compoundCore(),
          phaseStateMessages: spinePhasedMessages_compoundCore(),
          stateMessages: {
            perfect: { ar: 'لف جذع جيد مع ثبات البطن', en: 'Good trunk rotation with stable abs' },
            warning: { ar: 'لا ترفع الرقبة لتعويض نقص اللف', en: 'Do not crane the neck to fake the rotation' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_CORE,
    },
  ];
}

function scapularDepressionHoldPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: FRONT,
      cameraPosition: POSE_STANDING_FRONT,
      positionChecks: [
        {
          id: 'scap_dep_long_neck',
          type: 'horizontal_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'right_shoulder' },
          condition: { operator: 'approximately_equal', threshold: 0.08 },
          activePhases: ['all'],
          errorMessage: {
            ar: 'اسحب لوحي الكتف لأسفل مع رقبة طويلة وهادئة.',
            en: 'Pull the shoulder blades down with a long, quiet neck.',
          },
          severity: 'warning',
          cooldownMs: 2600,
          minErrorFrames: 5,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_shoulder',
          role: 'primary',
          startPose: { min: 140, max: 180 },
          range: {
            perfect: { min: 165, max: 180 },
            normal: { min: 158, max: 180 },
            pad: { min: 150, max: 180 },
            warning: { min: 130, max: 150 },
          },
          stateMessages: {
            perfect: { ar: 'ذراع ممتدة — شد لوحي لأسفل (dead hang)', en: 'Arms long — scapular depression in dead hang' },
            warning: { ar: 'لا ترفع الكتفين نحو الأذنين', en: 'Do not shrug the shoulders toward the ears' },
          },
        },
        {
          joint: 'left_elbow',
          role: 'secondary',
          startPose: { min: 160, max: 180 },
          range: {
            perfect: { min: 168, max: 180 },
            normal: { min: 160, max: 180 },
            pad: { min: 152, max: 180 },
          },
          stateMessages: {
            perfect: { ar: 'ذراعان طويلتان بدون شد زائد', en: 'Arms stay long without excess tension' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

function proneYRaisePattern(): SeedPoseVariantJson[] {
  return [
    {
      name: PRONE,
      cameraPosition: POSE_PRONE_SIDE,
      positionChecks: [
        {
          id: 'prone_y_ribs',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.14 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'ارفع الذراع من الكتف مع بقاء الأضلاع هادئة على المقعد/الأرض.',
            en: 'Lift from the shoulder while keeping the ribs quiet on the bench/floor.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_shoulder',
          role: 'primary',
          startPose: { min: 15, max: 95 },
          upRange: {
            perfect: { min: 82, max: 120 },
            normal: { min: 75, max: 128 },
            pad: { min: 68, max: 135 },
          },
          downRange: {
            perfect: { min: 18, max: 38 },
            normal: { min: 15, max: 45 },
            pad: { min: 12, max: 52 },
            warning: { min: 8, max: 12 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! رفع Y من الكتف الخلفي', en: 'Perfect! Clean Y raise from the posterior shoulder' },
              down: { ar: 'ممتاز! رجوع متحكم به', en: 'Perfect! Controlled lowering' },
            },
            normal: {
              up: { ar: 'جيد، ارفع الذراع أكثر قليلاً', en: 'Good, lift the arm slightly higher' },
              down: { ar: 'جيد، لا تسقط الذراع بسرعة', en: 'Good, do not drop the arm quickly' },
            },
            pad: { up: { ar: 'مقبول، لكن مدى الكتف ما زال قصيراً', en: 'Acceptable, but shoulder range is still short' } },
            warning: { up: { ar: 'لا تعوض برفع الصدر والرقبة', en: 'Do not compensate by lifting the chest and neck' } },
          },
        },
        {
          joint: 'left_elbow',
          role: 'secondary',
          startPose: { min: 145, max: 180 },
          range: {
            perfect: { min: 155, max: 180 },
            normal: { min: 145, max: 180 },
            pad: { min: 138, max: 180 },
          },
          stateMessages: {
            perfect: { ar: 'مرفق طويل بدون قفل عدواني', en: 'Long elbow without aggressive locking' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

function calfRaisePattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'calf_body_straight',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.07 },
          activePhases: ['up', 'top'],
          errorMessage: {
            ar: 'ابقَ طويلاً ومستقيماً أثناء الرفع.',
            en: 'Stay tall and straight during the raise.',
          },
          severity: 'warning',
          cooldownMs: 2000,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_ankle',
          role: 'primary',
          startPose: { min: 70, max: 100 },
          upRange: {
            perfect: { min: 125, max: 150 },
            normal: { min: 120, max: 145 },
            pad: { min: 110, max: 140 },
          },
          downRange: {
            perfect: { min: 80, max: 90 },
            normal: { min: 75, max: 95 },
            pad: { min: 70, max: 100 },
            warning: { min: 60, max: 70 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! ارتفاع كامل على أصابع القدم', en: 'Perfect! Full rise onto the toes' },
              down: { ar: 'ممتاز! عودة متحكم بها', en: 'Perfect! Controlled return' },
            },
            normal: {
              up: { ar: 'جيد، ارفع الكعب أكثر', en: 'Good, rise a bit higher' },
              down: { ar: 'جيد، انزل ببطء', en: 'Good, lower with control' },
            },
            pad: { up: { ar: 'مقبول، لكن مدى الرفع ما زال متوسطاً', en: 'Acceptable, but the raise is still moderate' } },
            warning: { up: { ar: 'لا ترتد بسرعة في أعلى الحركة', en: 'Do not bounce through the top' } },
          },
          pairedWith: 'right_ankle',
        },
        {
          joint: 'left_knee',
          role: 'secondary',
          startPose: { min: 165, max: 180 },
          range: {
            perfect: { min: 170, max: 180 },
            normal: { min: 165, max: 180 },
            pad: { min: 160, max: 180 },
            warning: { min: 145, max: 160 },
          },
          stateMessages: {
            perfect: { ar: 'ركبة ثابتة في الرفع', en: 'Knee stays steady during the raise' },
            warning: { ar: 'لا تثنِ الركبة لتعويض الكاحل', en: 'Do not bend the knee to cheat the ankle' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function tibialisRaisePattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'tibialis_body_straight',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.08 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'ارفع الأصابع من الكاحل دون تأرجح الجسم للخلف.',
            en: 'Lift the toes from the ankle without rocking the body backward.',
          },
          severity: 'warning',
          cooldownMs: 2000,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_ankle',
          role: 'primary',
          invertIndicator: true,
          startPose: { min: 45, max: 105 },
          upRange: {
            perfect: { min: 82, max: 96 },
            normal: { min: 80, max: 100 },
            pad: { min: 78, max: 104 },
          },
          downRange: {
            perfect: { min: 50, max: 70 },
            normal: { min: 46, max: 72 },
            pad: { min: 42, max: 75 },
            warning: { min: 35, max: 42 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! بداية محايدة متزنة', en: 'Perfect! Balanced neutral start' },
              down: { ar: 'ممتاز! رفع واضح لأصابع القدم', en: 'Perfect! Clear toe lift' },
            },
            normal: {
              up: { ar: 'جيد، لا ترتد للأسفل سريعاً', en: 'Good, do not drop quickly' },
              down: { ar: 'جيد، ارفع الأصابع أكثر', en: 'Good, lift the toes a little higher' },
            },
            pad: { down: { ar: 'مقبول، لكن مدى الرفع ما زال صغيراً', en: 'Acceptable, but the toe-lift range is still small' } },
            warning: { down: { ar: 'لا تعوض بثني الركبة أو الميل للخلف', en: 'Do not compensate by bending the knee or rocking back' } },
          },
          pairedWith: 'right_ankle',
        },
        {
          joint: 'left_knee',
          role: 'secondary',
          startPose: { min: 160, max: 180 },
          range: {
            perfect: { min: 168, max: 180 },
            normal: { min: 160, max: 180 },
            warning: { min: 145, max: 160 },
          },
          stateMessages: {
            perfect: { ar: 'ركبة هادئة ومثبتة', en: 'Knee stays quiet and set' },
            warning: { ar: 'لا تثنِ الركبة لتسرق الرفع', en: 'Do not bend the knee to cheat the raise' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function turkishGetupHalfPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SUPINE,
      cameraPosition: POSE_SUPINE_SIDE,
      positionChecks: [
        {
          id: 'tgu_half_arm_stack',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_wrist', secondary: 'left_shoulder' },
          condition: { operator: 'approximately_equal', threshold: 0.1 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'ابقِ الذراع الحاملة عمودية فوق الكتف أثناء النهوض النصفي.',
            en: 'Keep the loaded arm stacked vertically over the shoulder during the half get-up.',
          },
          severity: 'warning',
          cooldownMs: 2400,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_shoulder',
          role: 'primary',
          startPose: { min: 65, max: 180 },
          upRange: {
            perfect: { min: 160, max: 180 },
            normal: { min: 150, max: 180 },
            pad: { min: 140, max: 180 },
          },
          downRange: {
            perfect: { min: 78, max: 110 },
            normal: { min: 70, max: 118 },
            pad: { min: 62, max: 126 },
            warning: { min: 50, max: 62 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! ذراع ثابتة فوق الكتف', en: 'Perfect! Arm stacked over the shoulder' },
              down: { ar: 'ممتاز! عودة آمنة للقاعدة', en: 'Perfect! Safe return to the base position' },
            },
            normal: {
              up: { ar: 'جيد، حافظ على عمودية الذراع أكثر', en: 'Good, keep the arm more vertical' },
              down: { ar: 'جيد، عد ببطء وبترتيب', en: 'Good, return slowly and in order' },
            },
            pad: { up: { ar: 'مقبول، لكن الذراع ما زالت تتمايل', en: 'Acceptable, but the arm is still drifting' } },
            warning: { up: { ar: 'لا تسرع في المراحل الانتقالية', en: 'Do not rush the transitions' } },
          },
        },
        {
          joint: 'left_hip',
          role: 'secondary',
          startPose: { min: 70, max: 180 },
          range: {
            perfect: { min: 95, max: 150 },
            normal: { min: 85, max: 158 },
            pad: { min: 75, max: 166 },
            warning: { min: 60, max: 75 },
          },
          stateMessages: {
            perfect: { ar: 'ورك متحكم به في النهوض النصفي', en: 'Hip stays organized through the half get-up' },
            warning: { ar: 'لا تفقد الحوض أو الجذع أثناء الانتقال', en: 'Do not lose the pelvis or trunk in the transition' },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 30 },
          range: {
            perfect: { min: 0, max: 18 },
            normal: { min: 0, max: 24 },
            pad: { min: 0, max: 30 },
            warning: { min: 30, max: 45 },
          },
          stateMessages: {
            perfect: { ar: 'جذع منظم أثناء المراحل الانتقالية', en: 'Trunk remains organized through the transitions' },
            warning: { ar: 'لا تلتف أو تنهَر في الظهر أثناء الصعود', en: 'Avoid twisting or collapsing through the back on the ascent' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

/** Hammer / EZ curl — same mechanics as `variantArmCurl` (catalog uses PULL_HORIZONTAL). */
function armCurlPullHorizontalPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'curl_elbow_stays_home',
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
      feedbackMessages: FEEDBACK_UPPER,
    },
  ];
}

/** Active rotation / chop mobility — allow controlled trunk rotation (not anti-rotation). */
function rotationMobilityPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'rotation_smooth_hips',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.18 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'أدر الجذع بسلاسة دون انهيار أو اندفاع سريع.',
            en: 'Rotate smoothly without collapsing or snapping.',
          },
          severity: 'tip',
          cooldownMs: 2600,
          minErrorFrames: 5,
        },
      ],
      trackedJoints: [
        {
          joint: 'spine',
          role: 'primary',
          startPose: { min: 0, max: 45 },
          upRange: {
            perfect: { min: 35, max: 60 },
            normal: { min: 28, max: 70 },
            pad: { min: 22, max: 80 },
            warning: { min: 80, max: 100 },
            danger: { min: 100, max: 130 },
          },
          downRange: {
            perfect: { min: 0, max: 14 },
            normal: { min: 0, max: 18 },
            pad: { min: 0, max: 20 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! دوران صدري واضح', en: 'Perfect! Clear thoracic rotation' },
              down: { ar: 'ممتاز! بداية محايدة منظمة', en: 'Perfect! Organized neutral start' },
            },
            normal: {
              up: { ar: 'جيد، زد اللف قليلاً إن كان مريحاً', en: 'Good, add a bit more rotation if comfortable' },
              down: { ar: 'جيد، ارجع للمحايد ببطء', en: 'Good, return to neutral slowly' },
            },
            pad: { up: { ar: 'مقبول — ابنِ المدى تدريجياً', en: 'Acceptable — build ROM gradually' } },
            warning: { up: { ar: 'لا تلتف من أسفل الظهر فقط', en: 'Do not twist only from the low back' } },
            danger: { up: { ar: 'توقف — ألم حاد', en: 'Stop — sharp pain' } },
          },
        },
        {
          joint: 'left_shoulder',
          role: 'secondary',
          startPose: { min: 40, max: 120 },
          range: {
            perfect: { min: 55, max: 95 },
            normal: { min: 48, max: 105 },
            pad: { min: 40, max: 115 },
            warning: { min: 115, max: 130 },
          },
          stateMessages: {
            perfect: { ar: 'كتف يتحرك مع اللف بسلاسة', en: 'Shoulder moves smoothly with the rotation' },
            warning: { ar: 'لا تشد الرقبة أو المرفق بعنف', en: 'Do not yank the neck or elbow' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

function bearCrawlPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: PRONE,
      cameraPosition: POSE_PRONE_SIDE,
      positionChecks: [
        {
          id: 'bear_flat_back',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.14 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'حافظ على ظهر مستوٍ — لا تهبط الورك أو ترفع القفص زائداً.',
            en: 'Keep a flat back — don’t sag the hips or over-arch.',
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
          startPose: { min: 70, max: 160 },
          upRange: {
            perfect: { min: 130, max: 160 },
            normal: { min: 122, max: 170 },
            pad: { min: 115, max: 175 },
          },
          downRange: {
            perfect: { min: 78, max: 102 },
            normal: { min: 72, max: 110 },
            pad: { min: 65, max: 112 },
            warning: { min: 55, max: 65 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! خطوة ثابتة دون هبوط', en: 'Perfect! Stable step without sag' },
              down: { ar: 'ممتاز! ركبة قريبة للصدر بتحكم', en: 'Perfect! Knee driven to chest with control' },
            },
            normal: {
              up: { ar: 'جيد، ابقِ الورك بمستوى الكتف', en: 'Good, keep the hip in line with the shoulder' },
              down: { ar: 'جيد، ارفع الركبة نحو الصدر أكثر', en: 'Good, drive the knee toward the chest more' },
            },
            pad: { down: { ar: 'مقبول — قصر المدى قليلاً', en: 'Acceptable — keep the range a bit shorter' } },
            warning: { down: { ar: 'الزحف تحول إلى هبوط في الورك', en: 'The crawl is turning into a hip sag' } },
          },
        },
        {
          joint: 'left_shoulder',
          role: 'secondary',
          startPose: { min: 55, max: 110 },
          range: {
            perfect: { min: 70, max: 98 },
            normal: { min: 62, max: 105 },
            pad: { min: 55, max: 112 },
            warning: { min: 45, max: 55 },
          },
          stateMessages: {
            perfect: { ar: 'كتف فوق المعصم تقريباً', en: 'Shoulder stacked over the wrist' },
            warning: { ar: 'لا تدع الكتفين يزحفان للأذنين', en: 'Don’t let the shoulders creep to the ears' },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 22 },
          range: {
            perfect: { min: 0, max: 14 },
            normal: { min: 0, max: 20 },
            pad: { min: 0, max: 26 },
            warning: { min: 26, max: 38 },
          },
          stateMessages: {
            perfect: { ar: 'جذع منظم في الرباعي', en: 'Trunk organized in quadruped' },
            warning: { ar: 'قلل السرعة إذا فقد الظهر الحياد', en: 'Slow down if spinal neutrality is lost' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function spidermanLungePattern(): SeedPoseVariantJson[] {
  return [
    {
      name: PRONE,
      cameraPosition: POSE_PRONE_SIDE,
      positionChecks: [
        {
          id: 'spidey_front_knee',
          type: 'forward_comparison',
          landmarks: { primary: 'left_knee', secondary: 'left_foot_index' },
          condition: { operator: 'should_not_exceed', threshold: 0.12 },
          activePhases: ['down', 'bottom'],
          errorMessage: {
            ar: 'اثبت الركبة الأمامية فوق القدم أثناء المد.',
            en: 'Stack the front knee over the foot during the reach.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_knee',
          role: 'primary',
          startPose: { min: 70, max: 140 },
          upRange: {
            perfect: { min: 155, max: 180 },
            normal: { min: 148, max: 180 },
            pad: { min: 140, max: 180 },
          },
          downRange: {
            perfect: { min: 85, max: 115 },
            normal: { min: 78, max: 125 },
            pad: { min: 70, max: 132 },
            warning: { min: 58, max: 70 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! عودة طويلة من الانبساط', en: 'Perfect! Long return from the stretch' },
              down: { ar: 'ممتاز! ركبة أمامية عميقة مع مد', en: 'Perfect! Deep front knee with reach' },
            },
            normal: {
              up: { ar: 'جيد، افرد الرجل الخلفية أكثر', en: 'Good, lengthen the back leg more' },
              down: { ar: 'جيد، ثبّت القدم الأمامية', en: 'Good, anchor the front foot' },
            },
            pad: { down: { ar: 'مقبول — زد المدى تدريجياً', en: 'Acceptable — build reach gradually' } },
            warning: { down: { ar: 'لا تنهار على الركبة الأمامية', en: 'Don’t collapse into the front knee' } },
          },
        },
        {
          joint: 'right_hip',
          role: 'secondary',
          startPose: { min: 120, max: 180 },
          range: {
            perfect: { min: 155, max: 180 },
            normal: { min: 148, max: 180 },
            pad: { min: 138, max: 180 },
            warning: { min: 120, max: 138 },
          },
          stateMessages: {
            perfect: { ar: 'ورك خلفي في امتداد جيد', en: 'Rear hip shows good extension' },
            warning: { ar: 'لا تطوي الورك الخلفي مبكراً', en: 'Don’t fold the rear hip early' },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 28 },
          range: {
            perfect: { min: 0, max: 18 },
            normal: { min: 0, max: 24 },
            pad: { min: 0, max: 30 },
            warning: { min: 30, max: 45 },
          },
          stateMessages: {
            perfect: { ar: 'جذع مرفوع فوق الورك', en: 'Torso tall over the hips' },
            warning: { ar: 'لا تنهَر قطنيّاً في المد', en: 'Avoid collapsing into lumbar flexion in the reach' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_MOBILITY,
    },
  ];
}

function renegadeRowPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: PRONE,
      cameraPosition: POSE_PRONE_SIDE,
      positionChecks: [
        {
          id: 'renegade_plank_line',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.12 },
          activePhases: ['up', 'down'],
          errorMessage: {
            ar: 'حافظ على خط لوحي — لا تلف الجذع أثناء السحب.',
            en: 'Keep a plank line — don’t rotate the trunk while rowing.',
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
            perfect: { min: 38, max: 62 },
            normal: { min: 30, max: 72 },
            pad: { min: 22, max: 82 },
            warning: { min: 12, max: 22 },
            danger: { min: 0, max: 12 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! لوح مستقر مع ذراع ممتدة', en: 'Perfect! Stable plank with long arm' },
              down: { ar: 'ممتاز! صف للورك بدون لف', en: 'Perfect! Row to hip without twisting' },
            },
            normal: {
              up: { ar: 'جيد، اضبط الكتف فوق المعصم', en: 'Good, set the shoulder over the wrist' },
              down: { ar: 'جيد، اسحب دون فتح القفص', en: 'Good, pull without opening the chest' },
            },
            pad: { down: { ar: 'مقبول — قلل الحمل أو المدى', en: 'Acceptable — reduce load or range' } },
            warning: { down: { ar: 'الجذع يلتف — شدّ البطن أكثر', en: 'Trunk is rotating — brace harder' } },
          },
        },
        {
          joint: 'spine',
          role: 'secondary',
          startPose: { min: 0, max: 20 },
          range: {
            perfect: { min: 0, max: 12 },
            normal: { min: 0, max: 18 },
            pad: { min: 0, max: 24 },
            warning: { min: 24, max: 38 },
          },
          stateMessages: {
            perfect: { ar: 'عمود فقري محايد في اللوح', en: 'Neutral spine in the plank' },
            warning: { ar: 'لا تسمح للحوض بالدوران مع الصف', en: 'Don’t let the pelvis turn with the row' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function kbGobletCleanPattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'goblet_clean_stack',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
          condition: { operator: 'approximately_equal', threshold: 0.13 },
          activePhases: ['down'],
          errorMessage: {
            ar: 'امتدد من الورك ثم «اقفز» الوزن للأعلى — ليس سكواتاً عمودياً.',
            en: 'Extend from the hips then pop the weight up — not a vertical squat.',
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
          startPose: { min: 95, max: 180 },
          upRange: {
            perfect: { min: 168, max: 180 },
            normal: { min: 160, max: 180 },
            pad: { min: 150, max: 180 },
          },
          downRange: {
            perfect: { min: 88, max: 115 },
            normal: { min: 80, max: 125 },
            pad: { min: 72, max: 135 },
            warning: { min: 58, max: 72 },
            danger: { min: 0, max: 58 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! قفل ورك مع قبضة عالية', en: 'Perfect! Hip lockout with a tall rack' },
              down: { ar: 'ممتاز! تحميل سريع وقصير', en: 'Perfect! Short, snappy load' },
            },
            normal: {
              up: { ar: 'جيد، أكمل التمدد في الأعلى', en: 'Good, finish the extension at the top' },
              down: { ar: 'جيد، اجلس في الورك لا في الركبة فقط', en: 'Good, sit into the hips, not only the knees' },
            },
            pad: { up: { ar: 'مقبول — التوقيت ما زال يحتاج ضبطاً', en: 'Acceptable — timing still needs tuning' } },
            warning: { down: { ar: 'لا تسحب بالذراعين من الأسفل', en: 'Don’t arm-pull from the bottom' } },
          },
        },
        {
          joint: 'left_knee',
          role: 'secondary',
          startPose: { min: 130, max: 180 },
          range: {
            perfect: { min: 145, max: 172 },
            normal: { min: 138, max: 180 },
            pad: { min: 128, max: 180 },
            warning: { min: 115, max: 128 },
          },
          stateMessages: {
            perfect: { ar: 'ركبة ناعمة — ليست سكواتاً عميقاً', en: 'Soft knees — not a deep squat' },
            warning: { ar: 'لا تغوص زائداً في الـ dip', en: 'Don’t dive too deep in the dip' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

function snatchBalancePattern(): SeedPoseVariantJson[] {
  return [
    {
      name: SIDE,
      cameraPosition: POSE_STANDING_SIDE,
      positionChecks: [
        {
          id: 'snatch_balance_stack',
          type: 'vertical_alignment',
          landmarks: { primary: 'left_wrist', secondary: 'left_shoulder' },
          condition: { operator: 'approximately_equal', threshold: 0.1 },
          activePhases: ['up', 'top'],
          errorMessage: {
            ar: 'أبقِ الوزن فوق الكتف في الاستقبال.',
            en: 'Receive the load stacked over the shoulder.',
          },
          severity: 'warning',
          cooldownMs: 2200,
          minErrorFrames: 4,
        },
      ],
      trackedJoints: [
        {
          joint: 'left_shoulder',
          role: 'primary',
          startPose: { min: 60, max: 180 },
          upRange: {
            perfect: { min: 160, max: 180 },
            normal: { min: 152, max: 180 },
            pad: { min: 148, max: 180 },
          },
          downRange: {
            perfect: { min: 95, max: 130 },
            normal: { min: 88, max: 140 },
            pad: { min: 78, max: 145 },
            warning: { min: 65, max: 78 },
          },
          stateMessages: {
            perfect: {
              up: { ar: 'ممتاز! استقبال علوي ثابت', en: 'Perfect! Stable overhead catch' },
              down: { ar: 'ممتاز! نزول متحكم من الرف', en: 'Perfect! Controlled lowering from rack' },
            },
            normal: {
              up: { ar: 'جيد، وسّع الصدر تحت الوزن', en: 'Good, open the chest under the load' },
              down: { ar: 'جيد، اثنِ الركبة والورك معاً في الاستقبال', en: 'Good, flex hip and knee together in the receive' },
            },
            pad: { up: { ar: 'مقبول — ما زال الوزن أمام الجسم', en: 'Acceptable — load still slightly forward' } },
            warning: { up: { ar: 'لا تفقد الوزن خلف الرأس', en: 'Don’t lose the weight behind you' } },
          },
        },
        {
          joint: 'left_hip',
          role: 'secondary',
          startPose: { min: 100, max: 180 },
          range: {
            perfect: { min: 130, max: 165 },
            normal: { min: 120, max: 175 },
            pad: { min: 110, max: 180 },
            warning: { min: 95, max: 110 },
          },
          stateMessages: {
            perfect: { ar: 'ورك في استقبال متوازن', en: 'Hip in a balanced receive' },
            warning: { ar: 'لا تطوي الورك فقط دون الركبة', en: 'Don’t fold only at the hip' },
          },
        },
      ],
      feedbackMessages: FEEDBACK_STRENGTH,
    },
  ];
}

export function buildCuratedPoseOverride(ex: CuratedExtensionExercise): SeedPoseVariantJson[] | null {
  const s = ex.slug;

  if (SET_ARM_CURL_PULL_HORIZONTAL.has(s)) return armCurlPullHorizontalPattern();
  if (SET_ROTATION_MOBILITY.has(s)) return rotationMobilityPattern();
  if (SET_BEAR_CRAWL.has(s)) return bearCrawlPattern();
  if (SET_SPIDERMAN_LUNGE.has(s)) return spidermanLungePattern();
  if (SET_RENEGADE_ROW.has(s)) return renegadeRowPattern();
  if (SET_KB_GOBLET_CLEAN.has(s)) return kbGobletCleanPattern();
  if (SET_SNATCH_BALANCE.has(s)) return snatchBalancePattern();
  if (SET_HIP_MOBILITY_DRILL.has(s)) return hipMobilityDrillPattern();
  if (SET_ANKLE_MOBILITY_DRILL.has(s)) return ankleMobilityDrillPattern();
  if (SET_SPINE_MOBILITY_DRILL.has(s)) return spineMobilityDrillPattern();
  if (SET_STANDING_MOBILITY_DRILL.has(s)) return standingMobilityDrillPattern();
  if (SET_SHOULDER_MOBILITY_DRILL.has(s)) return shoulderMobilityDrillPattern();
  if (SET_PASSIVE_SHOULDER_STRETCH.has(s)) return passiveShoulderStretchPattern();
  if (SET_NECK_MOBILITY_DRILL.has(s)) return neckMobilityDrillPattern();
  if (SET_YOGA_PRONE_FLOW.has(s)) return yogaProneFlowPattern();
  if (SET_HOLLOW_BODY.has(s)) return hollowBodyHoldPattern();
  if (SET_MOUNTAIN_CLIMBER.has(s)) return mountainClimberPattern();
  if (SET_SUPINE_SHOULDER_FLEX.has(s)) return supineShoulderFlexionPattern();
  if (SET_SEATED_HINGE.has(s)) return seatedHingePattern();
  if (SET_SEATED_CALF.has(s)) return seatedCalfRaisePattern();
  if (SET_SEATED_PRESS.has(s)) return seatedPressPattern();
  if (SET_BILATERAL_GAIT_FRONT.has(s)) return bilateralGaitFrontPattern();
  if (SET_DEEP_SQUAT_HOLD.has(s)) return deepSquatHold();
  if (SET_SPLIT_SQUAT_HOLD.has(s)) return splitSquatHold();
  if (SET_LATERAL_SQUATS.has(s)) return lateralSquatPattern();
  if (SET_OVERHEAD_SQUAT.has(s)) return overheadSquatPvc();
  if (SET_THRUSTERS.has(s)) return thrusterPattern();
  if (SET_WALL_SIT_PRESS.has(s)) return wallSitPressPattern();
  if (SET_BENCH_PRESS.has(s)) return benchPressPattern();
  if (SET_SCAP_PUSHUP.has(s)) return scapPushupPattern();
  if (SET_TRICEP_PUSHDOWN.has(s)) return tricepPushdownPattern();
  if (SET_PIKE_PUSHUP.has(s)) return pikePushupPattern();
  if (SET_LANDMINE_PRESS.has(s)) return landminePressPattern();
  if (SET_OVERHEAD_TRICEP.has(s)) return overheadTricepPattern();
  if (SET_BRIDGES.has(s)) return bridgePattern();
  if (SET_NORDIC_CURL.has(s)) return nordicCurlPattern();
  if (SET_HAMSTRING_SLIDE.has(s)) return hamstringSlidePattern();
  if (SET_SINGLE_LEG_HINGE.has(s)) return singleLegHingePattern();
  if (SET_MOBILITY_HINGE.has(s)) return mobilityHingePattern();
  if (SET_BALLISTIC_HINGE.has(s)) return ballisticHingePattern();
  if (SET_PUSH_PRESS.has(s)) return pushPressPattern();
  if (SET_DEAD_BUG.has(s)) return deadBugPattern();
  if (SET_HEEL_TAPS.has(s)) return heelTapsPattern();
  if (SET_BIRD_DOG.has(s)) return birdDogPattern();
  if (SET_PALLOF.has(s)) return pallofPressPattern();
  if (SET_ROTATION_HOLD.has(s)) return antiRotationHoldPattern();
  if (SET_BACK_EXTENSION_REPS.has(s)) return backExtensionRepPattern();
  if (SET_REVERSE_CRUNCH.has(s)) return reverseCrunchPattern();
  if (SET_V_UP.has(s)) return vUpPattern();
  if (SET_BICYCLE.has(s)) return bicycleCrunchPattern();
  if (SET_SCAPULAR_DEPRESSION.has(s)) return scapularDepressionHoldPattern();
  if (SET_PRONE_Y.has(s)) return proneYRaisePattern();
  if (SET_CALF_RAISE.has(s)) return calfRaisePattern();
  if (SET_TIBIALIS.has(s)) return tibialisRaisePattern();
  if (SET_TURKISH_GETUP.has(s)) return turkishGetupHalfPattern();
  if (SET_FARMER_CARRY.has(s)) return farmerCarryPattern();
  if (SET_WAITER_WALK.has(s)) return waiterWalkPattern();

  return null;
}
