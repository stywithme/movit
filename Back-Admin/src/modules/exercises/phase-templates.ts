import { LocalizedText, CountingMethodCode, PhaseName } from '@/lib/types/localized';

/**
 * Phase template for auto-generating phases based on counting method
 */
export type PhaseTemplate = {
  code: PhaseName;
  name: LocalizedText;
  sortOrder: number;
};

/**
 * Phase templates for each counting method
 * 
 * ALIGNED WITH ANDROID CONTRACT:
 * 1. up_down - Eccentric (down) and Concentric (up) phases (Squat, Lunge, etc.)
 * 2. push_pull - Push and Pull phases (Push-up, Pull-up, etc.)
 * 3. hold - Isometric exercises (Plank, Wall Sit, etc.)
 */
export const phaseTemplates: Record<CountingMethodCode, PhaseTemplate[]> = {
  // Up and Down: Like squat - going down (eccentric) and up (concentric)
  up_down: [
    { code: 'start', name: { ar: 'وضعية البداية', en: 'Starting Position' }, sortOrder: 1 },
    { code: 'down', name: { ar: 'النزول', en: 'Going Down' }, sortOrder: 2 },
    { code: 'bottom', name: { ar: 'أسفل نقطة', en: 'Bottom Position' }, sortOrder: 3 },
    { code: 'up', name: { ar: 'الصعود', en: 'Going Up' }, sortOrder: 4 },
  ],
  
  // Push and Pull: Like push-up - pushing and pulling
  push_pull: [
    { code: 'start', name: { ar: 'وضعية البداية', en: 'Starting Position' }, sortOrder: 1 },
    { code: 'push', name: { ar: 'الدفع', en: 'Push' }, sortOrder: 2 },
    { code: 'extended', name: { ar: 'الامتداد', en: 'Extended Position' }, sortOrder: 3 },
    { code: 'pull', name: { ar: 'السحب', en: 'Pull' }, sortOrder: 4 },
  ],
  
  // Hold: Isometric exercises - single phase for holding position
  // Android engine uses 'count' phase internally for HOLD timing
  hold: [
    { code: 'hold', name: { ar: 'الثبات', en: 'Hold' }, sortOrder: 1 },
  ],
};

/**
 * Get phase codes (simple string array) for a counting method
 * This is what gets stored in the database and sent to Android
 */
export function getPhaseCodesForCountingMethod(countingMethodCode: CountingMethodCode): PhaseName[] {
  const templates = phaseTemplates[countingMethodCode] || phaseTemplates.up_down;
  return templates.map(t => t.code);
}

/**
 * Get phase templates for a counting method (full objects with names)
 */
export function getPhasesForCountingMethod(countingMethodCode: CountingMethodCode): PhaseTemplate[] {
  return phaseTemplates[countingMethodCode] || phaseTemplates.up_down;
}

/**
 * Get counting method description
 */
export const countingMethodDescriptions: Record<CountingMethodCode, LocalizedText> = {
  up_down: {
    ar: 'أعلى وأسفل - يعد التكرارات عند النزول والصعود (مثل السكوات)',
    en: 'Up & Down - counts reps on down and up movement (like squat)',
  },
  push_pull: {
    ar: 'دفع وسحب - يعد التكرارات عند الدفع والسحب (مثل تمارين الضغط)',
    en: 'Push & Pull - counts reps on push and pull movement (like push-ups)',
  },
  hold: {
    ar: 'ثبات - تمارين الثبات تحسب الوقت بدلاً من التكرارات (مثل البلانك)',
    en: 'Hold - isometric exercises count time instead of reps (like plank)',
  },
};

/**
 * Get default rep/duration values per difficulty for a counting method
 */
export function getDefaultRepConfig(countingMethodCode: CountingMethodCode): {
  beginner: { reps?: number; duration?: number };
  normal: { reps?: number; duration?: number };
  advanced: { reps?: number; duration?: number };
} {
  if (countingMethodCode === 'hold') {
    return {
      beginner: { duration: 15 },
      normal: { duration: 30 },
      advanced: { duration: 60 },
    };
  }
  
  return {
    beginner: { reps: 8 },
    normal: { reps: 12 },
    advanced: { reps: 16 },
  };
}
