/**
 * Phase-specific angle ranges for secondary tracked joints (seed + JSON exercises).
 *
 * Conventions:
 * - Keep `range` on every secondary joint (required by Admin Zod schema as UI template).
 * - Add `phaseRanges` when acceptable angles change by movement phase; Android evaluates
 *   only phases present in `phaseRanges` when it is set.
 * - Phases without a `phaseRanges` entry are skipped for that joint on the client.
 * - Prefer tighter `perfect` in bottom for spine/hip on squats and hinges; looser at top.
 *
 * Comments in English per project rules.
 */

export type AngleRange = { min: number; max: number };

/** State bucket used across seed JSON and dashboard */
export type StateRanges = {
  perfect: AngleRange;
  normal?: AngleRange;
  pad?: AngleRange;
  warning?: AngleRange;
  danger?: AngleRange;
};

export type MovementPhase = 'top' | 'down' | 'bottom' | 'up';

export type PhaseRangesMap = Partial<Record<MovementPhase, StateRanges>>;

/** Simple perfect-only helper */
function p(min: number, max: number): StateRanges {
  return { perfect: { min, max } };
}

function pn(min: number, max: number, nmin: number, nmax: number): StateRanges {
  return { perfect: { min, max }, normal: { min: nmin, max: nmax } };
}

/** Hip flexion: standing (top) vs deep squat (bottom) */
export function hipPhasedRanges_squat(): PhaseRangesMap {
  return {
    top: pn(165, 180, 158, 180),
    down: pn(95, 140, 85, 155),
    bottom: pn(70, 100, 60, 115),
    up: pn(95, 140, 85, 155),
  };
}

/** Hip hinge: near-extended lockout vs bottom hinge */
export function hipPhasedRanges_hinge(): PhaseRangesMap {
  return {
    top: pn(170, 180, 162, 180),
    down: pn(100, 145, 92, 155),
    bottom: p(75, 110),
    up: pn(100, 145, 92, 155),
  };
}

/** Split / lunge: rear hip extension at top vs flexion in split */
export function hipPhasedRanges_lunge(): PhaseRangesMap {
  return {
    top: pn(170, 180, 162, 180),
    down: pn(120, 165, 110, 172),
    bottom: pn(135, 175, 125, 180),
    up: pn(120, 165, 110, 172),
  };
}

/** Bridge hamstring slide — hip high at knee extension, slightly less flex in curl */
export function hipPhasedRanges_bridgeHamstringSlide(): PhaseRangesMap {
  return {
    top: pn(165, 180, 155, 180),
    down: pn(145, 172, 135, 178),
    bottom: pn(138, 168, 128, 175),
    up: pn(145, 172, 135, 178),
  };
}

/** Trunk flexion angle: more flexion allowed at bottom of squat/hinge */
export function spinePhasedRanges_dynamic(): PhaseRangesMap {
  return {
    top: p(0, 12),
    down: pn(0, 18, 0, 24),
    bottom: pn(8, 28, 5, 35),
    up: pn(0, 18, 0, 24),
  };
}

/** V-up / crunch: hollow bottom vs flexed “up” */
export function spinePhasedRanges_compoundCore(): PhaseRangesMap {
  return {
    bottom: p(0, 15),
    up: pn(45, 72, 38, 78),
    down: pn(12, 45, 8, 52),
    top: p(0, 18),
  };
}

/** Overhead lockout vs rack / shoulder level */
export function shoulderPhasedRanges_press(): PhaseRangesMap {
  return {
    top: pn(165, 180, 155, 180),
    down: pn(85, 115, 78, 125),
    bottom: pn(75, 105, 68, 118),
    up: pn(140, 175, 130, 180),
  };
}

/** Dip bottom vs support top */
export function shoulderPhasedRanges_dip(): PhaseRangesMap {
  return {
    top: p(0, 22),
    down: pn(15, 45, 10, 52),
    bottom: pn(25, 55, 18, 62),
    up: pn(15, 45, 10, 52),
  };
}

/** Bench: bar on chest vs lockout */
export function shoulderPhasedRanges_benchPress(): PhaseRangesMap {
  return {
    top: pn(50, 78, 45, 85),
    down: pn(78, 105, 72, 112),
    bottom: pn(82, 108, 75, 115),
    up: pn(55, 88, 48, 95),
  };
}

/** Knee: straighter at lockout, more bend in hole */
export function kneePhasedRanges_hinge(): PhaseRangesMap {
  return {
    top: pn(168, 180, 160, 180),
    down: pn(140, 172, 132, 178),
    bottom: p(130, 165),
    up: pn(140, 172, 132, 178),
  };
}

/** Dip then drive: more knee flex in bottom of dip */
export function kneePhasedRanges_pushPress(): PhaseRangesMap {
  return {
    top: pn(168, 180, 158, 180),
    down: pn(150, 172, 142, 178),
    bottom: pn(145, 168, 138, 175),
    up: pn(155, 178, 148, 180),
  };
}

/** Template `range` spanning all phases for Admin (secondary joint) */
export function spineSecondaryTemplateRange(): StateRanges {
  return {
    perfect: { min: 0, max: 18 },
    normal: { min: 0, max: 26 },
    pad: { min: 0, max: 35 },
    warning: { min: 28, max: 45 },
    danger: { min: 42, max: 85 },
  };
}

export function hipSecondaryTemplateRange(): StateRanges {
  return {
    perfect: { min: 70, max: 180 },
    normal: { min: 55, max: 180 },
    pad: { min: 45, max: 180 },
    warning: { min: 35, max: 55 },
    danger: { min: 0, max: 35 },
  };
}

export function kneeSecondaryTemplateRange(): StateRanges {
  return {
    perfect: { min: 135, max: 180 },
    normal: { min: 125, max: 180 },
    pad: { min: 115, max: 180 },
    warning: { min: 100, max: 118 },
  };
}

export function shoulderSecondaryTemplateRange(): StateRanges {
  return {
    perfect: { min: 75, max: 180 },
    normal: { min: 65, max: 180 },
    pad: { min: 55, max: 180 },
    warning: { min: 40, max: 60 },
  };
}

type Localized = { ar: string; en: string };

/** Optional per-phase state messages (subset of states per phase) */
export type PhaseStateMessagesMap = Partial<
  Record<
    MovementPhase,
    Partial<{
      perfect: Localized | { up: Localized; down: Localized };
      normal: Localized | { up: Localized; down: Localized };
      pad: Localized | { up: Localized; down: Localized };
      warning: Localized | { up: Localized; down: Localized };
    }>
  >
>;

export function spinePhasedMessages_dynamic(): PhaseStateMessagesMap {
  return {
    top: {
      perfect: { ar: 'ظهر محايد في الوقفة', en: 'Neutral spine at the top' },
      warning: { ar: 'لا تميل القطن للأمام في الأعلى', en: 'Avoid excessive lumbar lean at the top' },
    },
    bottom: {
      perfect: { ar: 'انحناء مقبول في القاع مع تحكم', en: 'Acceptable flexion at bottom with control' },
      warning: { ar: 'قلل الانحناء القطني الزائد', en: 'Reduce excessive lumbar flexion' },
    },
  };
}

export function hipPhasedMessages_squat(): PhaseStateMessagesMap {
  return {
    top: {
      perfect: { ar: 'وقفة كاملة — ورك ممتد', en: 'Full stand — hips extended' },
      pad: { ar: 'أكمل الوقفة قبل العد التالي', en: 'Finish the stand before the next rep' },
    },
    bottom: {
      perfect: { ar: 'عمق جيد بين الوركين', en: 'Good depth between the hips' },
      warning: { ar: 'اجلس بين الوركين أكثر إن أمكن بأمان', en: 'Sit between the hips more if safe' },
    },
  };
}

/** Vertical press — lockout vs rack / shoulder line */
export function shoulderPhasedMessages_press(): PhaseStateMessagesMap {
  return {
    top: {
      perfect: { ar: 'تمدد كامل فوق الرأس', en: 'Full overhead lockout' },
      pad: { ar: 'أكمل التمدد قبل العد التالي', en: 'Finish lockout before the next rep' },
    },
    bottom: {
      perfect: { ar: 'مستوى كتف جيد', en: 'Good shoulder-line position' },
      warning: { ar: 'اقترب لمستوى الكتف بسلاسة', en: 'Lower smoothly to shoulder level' },
    },
  };
}

/** Deadlift-style knee — straighter at lockout, more flex in the hole */
export function kneePhasedMessages_hinge(): PhaseStateMessagesMap {
  return {
    top: {
      perfect: { ar: 'ورك وركبة ممتدان — قفل جيد', en: 'Hip and knee extended — good lockout' },
      warning: { ar: 'اضغط لقفل الورك', en: 'Drive hips through to lock out' },
    },
    bottom: {
      perfect: { ar: 'ثني ركبة مناسب في القاع', en: 'Appropriate knee bend at the bottom' },
      warning: { ar: 'اثنِ الركبة قليلاً إن كانت الساقان شبه مفرودتين', en: 'Add a little knee bend if legs are too straight' },
    },
  };
}

/** Push-press style: short dip then drive — knee bends only in the dip */
export function kneePhasedMessages_pushPress(): PhaseStateMessagesMap {
  return {
    top: {
      perfect: { ar: 'إنهاء قفل الركبة بعد الدفع', en: 'Knees locked after the drive' },
      warning: { ar: 'لا تترك الركبة منثنية في الإنهاء', en: 'Do not leave the knees bent at lockout' },
    },
    bottom: {
      perfect: { ar: 'Dip قصير ومتزن', en: 'Short, balanced dip' },
      warning: { ar: 'لا تحوّل الـ dip إلى سكوات عميق', en: 'Do not turn the dip into a deep squat' },
    },
  };
}

/** Bench press shoulder — flexion only between rack and lockout, not full overhead */
export function shoulderPhasedMessages_benchPress(): PhaseStateMessagesMap {
  return {
    top: {
      perfect: { ar: 'كتف منظم في القفل بدون شد بالأذن', en: 'Shoulder organized at lockout without ear-shrug' },
      warning: { ar: 'لا ترفع الكتف عن المقعد في القفل', en: 'Keep the shoulder seated at lockout' },
    },
    bottom: {
      perfect: { ar: 'كتف ثابت ولوح كتف مضغوط', en: 'Stable shoulder with packed scapula' },
      warning: { ar: 'لا تترك الكتف يتدحرج للأمام', en: 'Do not let the shoulder roll forward' },
    },
  };
}

/** Compound core (V-up / bicycle): trunk flexion drive vs hollow-bottom */
export function spinePhasedMessages_compoundCore(): PhaseStateMessagesMap {
  return {
    bottom: {
      perfect: { ar: 'قاع مجوف وثابت — تحكم جيد', en: 'Quiet, hollow bottom — good control' },
      warning: { ar: 'لا تدع الظهر السفلي يتقوس عن الأرض', en: 'Do not let the low back arch off the floor' },
    },
    up: {
      perfect: { ar: 'انقباض جذع نظيف للأعلى', en: 'Clean trunk flexion at the top' },
      warning: { ar: 'لا تشد بالرقبة لتعويض البطن', en: 'Do not pull with the neck to cheat the abs' },
    },
  };
}

/** Bridge / hamstring slide hip — high lockout vs partial flexion in the slide */
export function hipPhasedMessages_bridgeHamstringSlide(): PhaseStateMessagesMap {
  return {
    top: {
      perfect: { ar: 'قفل ورك مرتفع وقوي', en: 'Strong, high hip lockout' },
      warning: { ar: 'ادفع الورك أعلى دون تقوس قطني', en: 'Drive the hips higher without lumbar arching' },
    },
    bottom: {
      perfect: { ar: 'انثناء ورك مناسب في الانزلاق', en: 'Appropriate hip flex during the slide' },
      warning: { ar: 'لا تنزل الورك للأرض في منتصف العد', en: 'Do not drop the hips to the floor mid-rep' },
    },
  };
}
