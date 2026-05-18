/**
 * Phase Templates
 * ================
 * 
 * Phase templates for auto-generating phases based on counting method.
 */

import type { LocalizedText, CountingMethodCode, PhaseName } from '@/lib/types/localized';

/**
 * Phase template
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
 * 1. up_down - Eccentric (down) and Concentric (up) phases (Squat, Push-up, etc.)
 * 2. hold - Isometric exercises (Plank, Wall Sit, etc.)
 */
export const phaseTemplates: Record<CountingMethodCode, PhaseTemplate[]> = {
  up_down: [
    { code: 'top', name: { ar: 'أعلى نقطة', en: 'Top Position' }, sortOrder: 1 },
    { code: 'down', name: { ar: 'النزول', en: 'Going Down' }, sortOrder: 2 },
    { code: 'bottom', name: { ar: 'أسفل نقطة', en: 'Bottom Position' }, sortOrder: 3 },
    { code: 'up', name: { ar: 'الصعود', en: 'Going Up' }, sortOrder: 4 },
  ],
  
  hold: [
    { code: 'count', name: { ar: 'مرحلة العدّ (الثبات)', en: 'Hold timer (count phase)' }, sortOrder: 1 },
  ],
};

/**
 * Get phase codes (simple string array) for a counting method
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
  hold: {
    ar: 'ثبات - تمارين الثبات تحسب الوقت بدلاً من التكرارات (مثل البلانك)',
    en: 'Hold - isometric exercises count time instead of reps (like plank)',
  },
};
