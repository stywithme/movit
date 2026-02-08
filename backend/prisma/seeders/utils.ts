export const categoryCodeMap: Record<string, string> = {
  glutes: 'legs',
};

export const muscleCodeMap: Record<string, string> = {
  chest: 'chest_muscle',
  shoulders: 'front_delts',
  arms: 'biceps',
  back: 'lats',
  core: 'abs_muscle',
  abs: 'abs_muscle',
  deltoids: 'side_delts',
  trapezius: 'traps',
  upper_chest: 'chest_muscle',
  soleus: 'calves',
};

export const muscleNameMap: Record<string, { ar: string; en: string }> = {
  chest: { ar: 'الصدر', en: 'Chest' },
  shoulders: { ar: 'الأكتاف', en: 'Shoulders' },
  arms: { ar: 'الذراعين', en: 'Arms' },
  back: { ar: 'الظهر', en: 'Back' },
  core: { ar: 'الجذع', en: 'Core' },
  abs: { ar: 'البطن', en: 'Abs' },
  deltoids: { ar: 'الدالية', en: 'Deltoids' },
  trapezius: { ar: 'شبه المنحرفة', en: 'Trapezius' },
  upper_chest: { ar: 'الصدر العلوي', en: 'Upper Chest' },
  soleus: { ar: 'عضلة النعلية', en: 'Soleus' },
};

export const equipmentNameMap: Record<string, { ar: string; en: string }> = {
  cable_machine: { ar: 'جهاز الكابلات', en: 'Cable Machine' },
  chair: { ar: 'كرسي', en: 'Chair' },
  parallel_bars: { ar: 'متوازي', en: 'Parallel Bars' },
  smith_machine: { ar: 'جهاز سميث', en: 'Smith Machine' },
};

export const tagNameMap: Record<string, { ar: string; en: string }> = {
  compound: { ar: 'مركب', en: 'Compound' },
  upper_body: { ar: 'الجزء العلوي', en: 'Upper Body' },
  lower_body: { ar: 'الجزء السفلي', en: 'Lower Body' },
  full_body: { ar: 'الجسم بالكامل', en: 'Full Body' },
  no_equipment: { ar: 'بدون معدات', en: 'No Equipment' },
  bodyweight: { ar: 'وزن الجسم', en: 'Bodyweight' },
  isolation: { ar: 'عزل', en: 'Isolation' },
  isometric: { ar: 'ثابت', en: 'Isometric' },
  endurance: { ar: 'تحمل', en: 'Endurance' },
  strength: { ar: 'قوة', en: 'Strength' },
  functional: { ar: 'وظيفي', en: 'Functional' },
  balance: { ar: 'توازن', en: 'Balance' },
  unilateral: { ar: 'أحادي', en: 'Unilateral' },
  left: { ar: 'يسار', en: 'Left' },
  right: { ar: 'يمين', en: 'Right' },
  beginner_friendly: { ar: 'مناسب للمبتدئين', en: 'Beginner Friendly' },
  easy: { ar: 'سهل', en: 'Easy' },
  rehab: { ar: 'تأهيل', en: 'Rehab' },
  desk: { ar: 'مكتب', en: 'Desk' },
  test: { ar: 'اختبار', en: 'Test' },
  hold: { ar: 'ثبات', en: 'Hold' },
  hold_tag: { ar: 'ثبات', en: 'Hold' },
  core: { ar: 'جذع', en: 'Core' },
  shoulders: { ar: 'أكتاف', en: 'Shoulders' },
  back: { ar: 'ظهر', en: 'Back' },
};

export function toTitleCase(value: string): string {
  return value
    .replace(/[_-]+/g, ' ')
    .replace(/\b\w/g, (char) => char.toUpperCase())
    .trim();
}

export function buildLocalizedName(
  code: string,
  map?: Record<string, { ar: string; en: string }>
) {
  const normalized = code.replace(/-/g, '_');
  if (map && map[normalized]) return map[normalized];
  const title = toTitleCase(normalized);
  return { ar: title, en: title };
}

export function normalizeMessageContent(content: {
  ar?: string;
  en?: string;
  audioAr?: string;
  audioEn?: string;
}) {
  return {
    ar: content.ar || '',
    en: content.en || '',
    audioAr: content.audioAr,
    audioEn: content.audioEn,
  };
}
