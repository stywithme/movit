import { LocalizedTextWithAudio } from '@/lib/types/localized';

export type MessageCategory = 'state' | 'position' | 'motivational' | 'tip' | 'system';

export type MessageContext =
  | 'perfect'
  | 'normal'
  | 'pad'
  | 'warning'
  | 'danger'
  | 'general'
  | 'motivational'
  | 'tip'
  | 'error';

export interface CreateMessageInput {
  code: string;
  category: MessageCategory;
  context?: MessageContext;
  content: LocalizedTextWithAudio;
  tags?: string[];
  isSystem?: boolean;
  isActive?: boolean;
}

export interface UpdateMessageInput {
  code?: string;
  category?: MessageCategory;
  context?: MessageContext | null;
  content?: LocalizedTextWithAudio;
  tags?: string[];
  isSystem?: boolean;
  isActive?: boolean;
}

/** Filter messages by whether TTS audio exists for text fields */
export type AudioMissingFilter = 'any' | 'ar' | 'en' | 'complete';

export interface BulkGenerateAudioInput {
  includeInactive?: boolean;
  category?: MessageCategory;
  /** Defaults to both ar and en */
  languages?: ('ar' | 'en')[];
  /** Max TTS generations in this request (default 50) */
  maxGenerations?: number;
  delayMsBetweenCalls?: number;
}

export interface BulkGenerateAudioResultItem {
  messageId: string;
  code: string;
  language: 'ar' | 'en';
  error: string;
}

export interface BulkGenerateAudioResult {
  plannedGenerations: number;
  completedGenerations: number;
  skippedAlreadyPresent: number;
  failed: BulkGenerateAudioResultItem[];
  stoppedDueToLimit: boolean;
}
