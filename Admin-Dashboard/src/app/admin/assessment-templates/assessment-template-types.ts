/**
 * Shared assessment template type labels and filter options (admin UI).
 * Keep in sync with backend matching: initial vs progression family (progression | post_program | level_specific).
 */

export const ASSESSMENT_TEMPLATE_TYPE_VALUES = ['initial', 'progression', 'post_program', 'level_specific'] as const;

export type AssessmentTemplateTypeValue = (typeof ASSESSMENT_TEMPLATE_TYPE_VALUES)[number];

export const ASSESSMENT_TEMPLATE_TYPE_OPTIONS: { value: AssessmentTemplateTypeValue; label: string }[] = [
  { value: 'initial', label: 'Initial Assessment' },
  { value: 'progression', label: 'Progression (exit exam)' },
  { value: 'post_program', label: 'Post Program Assessment' },
  { value: 'level_specific', label: 'Level Specific Assessment' },
];

/** List / filter dropdown (short labels). */
export const ASSESSMENT_TEMPLATE_TYPE_FILTER_OPTIONS: { value: string; label: string }[] = [
  { value: '', label: 'All Types' },
  { value: 'initial', label: 'Initial' },
  { value: 'progression', label: 'Progression' },
  { value: 'post_program', label: 'Post Program' },
  { value: 'level_specific', label: 'Level Specific' },
];

export const ASSESSMENT_TEMPLATE_TYPE_BADGE_VARIANT: Record<
  string,
  'primary' | 'purple' | 'orange' | 'teal' | 'default'
> = {
  initial: 'primary',
  progression: 'purple',
  post_program: 'orange',
  level_specific: 'teal',
  periodic: 'default',
};

export const ASSESSMENT_TEMPLATE_TYPE_SHORT_LABEL: Record<string, string> = {
  initial: 'Initial',
  progression: 'Progression',
  post_program: 'Post Program',
  level_specific: 'Level Specific',
  periodic: 'Periodic',
};
