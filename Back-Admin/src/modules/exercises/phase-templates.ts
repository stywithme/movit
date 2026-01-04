import { LocalizedText, CountingMethodCode } from '@/lib/types/localized';

/**
 * Phase template for auto-generating phases based on counting method
 */
export type PhaseTemplate = {
  code: string;
  name: LocalizedText;
  sortOrder: number;
};

/**
 * Phase templates for each counting method
 * 
 * 1. Counter - Simple counting without phases (just start/end)
 * 2. Up and Down - Eccentric (down) and Concentric (up) phases
 * 3. Push and Pull - Push and Pull phases
 */
export const phaseTemplates: Record<CountingMethodCode, PhaseTemplate[]> = {
  // Counter: Simple counting - no intermediate phases
  counter: [
    { code: 'start', name: { ar: 'وضعية البداية', en: 'Starting Position' }, sortOrder: 1 },
    { code: 'count', name: { ar: 'العد', en: 'Counting' }, sortOrder: 2 },
    { code: 'end', name: { ar: 'النهاية', en: 'End Position' }, sortOrder: 3 },
  ],
  
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
};

/**
 * Get phase templates for a counting method
 */
export function getPhasesForCountingMethod(countingMethodCode: CountingMethodCode): PhaseTemplate[] {
  return phaseTemplates[countingMethodCode] || phaseTemplates.counter;
}

/**
 * Get counting method description
 */
export const countingMethodDescriptions: Record<CountingMethodCode, LocalizedText> = {
  counter: {
    ar: 'عداد بسيط - يعد التكرارات بناءً على حركة واحدة',
    en: 'Simple counter - counts reps based on a single movement',
  },
  up_down: {
    ar: 'أعلى وأسفل - يعد التكرارات عند النزول والصعود (مثل السكوات)',
    en: 'Up & Down - counts reps on down and up movement (like squat)',
  },
  push_pull: {
    ar: 'دفع وسحب - يعد التكرارات عند الدفع والسحب (مثل تمارين الضغط)',
    en: 'Push & Pull - counts reps on push and pull movement (like push-ups)',
  },
};
