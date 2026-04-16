export type MessageCategoryValue = 'state' | 'position' | 'motivational' | 'tip' | 'system';

const CATEGORY_PREFIX: Record<MessageCategoryValue, string> = {
  state: 'STATE',
  position: 'POS',
  motivational: 'MOT',
  tip: 'TIP',
  system: 'SYS',
};

const CONTEXT_LABELS: Record<string, string> = {
  '': 'None',
  perfect: 'Perfect',
  normal: 'Normal',
  pad: 'Pad',
  warning: 'Warning',
  danger: 'Danger',
  general: 'General',
  motivational: 'Motivational',
  tip: 'Tip',
  error: 'Error',
};

const CONTEXTS_BY_CATEGORY: Record<MessageCategoryValue, string[]> = {
  state: ['', 'perfect', 'normal', 'pad', 'warning', 'danger'],
  position: ['', 'error', 'warning', 'general'],
  motivational: ['', 'general', 'motivational'],
  tip: ['', 'general', 'tip', 'warning'],
  system: ['', 'general', 'warning', 'error'],
};

const DEFAULT_CONTEXT_BY_CATEGORY: Record<MessageCategoryValue, string> = {
  state: '',
  position: 'error',
  motivational: 'general',
  tip: 'general',
  system: '',
};

const CATEGORY_HINTS: Record<MessageCategoryValue, string> = {
  state: 'Use for pose/joint state feedback like perfect, warning, or danger.',
  position: 'Use for specific position mistakes and correction prompts.',
  motivational: 'Use for encouragement, streak, or pacing messages.',
  tip: 'Use for quick coaching tips and form reminders.',
  system: 'Use only for fixed training/app messages that behave like system keys.',
};

function sanitizeToken(value?: string | null): string {
  return (value || '')
    .trim()
    .replace(/[^a-zA-Z0-9]+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_+|_+$/g, '')
    .toUpperCase();
}

function resolveContextToken(category: MessageCategoryValue, context?: string | null): string {
  const token = sanitizeToken(context);
  if (!token) return 'GENERAL';
  if (
    (category === 'motivational' && token === 'MOTIVATIONAL') ||
    (category === 'tip' && token === 'TIP') ||
    (category === 'system' && token === 'SYSTEM')
  ) {
    return 'GENERAL';
  }
  return token;
}

export function getDefaultContextForCategory(category: string): string {
  const key = (category as MessageCategoryValue) || 'state';
  return DEFAULT_CONTEXT_BY_CATEGORY[key] ?? '';
}

export function getMessageContextOptions(category: string, currentContext?: string | null) {
  const key = (category as MessageCategoryValue) || 'state';
  const values = [...(CONTEXTS_BY_CATEGORY[key] ?? CONTEXTS_BY_CATEGORY.state)];
  if (currentContext && !values.includes(currentContext)) {
    values.push(currentContext);
  }

  return values.map((value) => ({
    value,
    label: CONTEXT_LABELS[value] || value,
  }));
}

export function getMessageCategoryHint(category: string): string {
  const key = (category as MessageCategoryValue) || 'state';
  return CATEGORY_HINTS[key] ?? CATEGORY_HINTS.state;
}

export function buildAutoMessageCodePreview(category: string, context?: string | null): string {
  const key = (category as MessageCategoryValue) || 'state';
  const prefix = CATEGORY_PREFIX[key] ?? 'MSG';
  return `${prefix}_${resolveContextToken(key, context)}_###`;
}
