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
export type MessageStatusFilter = 'active' | 'inactive';
export type BulkGenerateAudioLanguage = 'ar' | 'en';
export type BulkGenerateAudioMode = 'missing_only' | 'regenerate_selected';

export interface BulkGenerateAudioInput {
  includeInactive?: boolean;
  category?: MessageCategory;
  status?: MessageStatusFilter;
  search?: string;
  audioMissing?: AudioMissingFilter;
  /** Defaults to both ar and en */
  languages?: BulkGenerateAudioLanguage[];
  /** Only missing audio, or regenerate selected languages even if audio exists */
  mode?: BulkGenerateAudioMode;
  /** Max TTS generations in this request (default 50) */
  maxGenerations?: number;
  delayMsBetweenCalls?: number;
  /** Used by the frontend batching flow when regenerating existing audio */
  excludeSlots?: string[];
  model?: string;
  voiceNameAr?: string;
  voiceNameEn?: string;
  languageCodeAr?: string;
  languageCodeEn?: string;
  sharedStylePrompt?: string;
  stylePromptAr?: string;
  stylePromptEn?: string;
  systemInstruction?: string;
  temperature?: number;
  seed?: number;
}

export interface BulkGenerateAudioResultItem {
  slotKey: string;
  messageId: string;
  code: string;
  language: BulkGenerateAudioLanguage;
  error: string;
}

export interface BulkGenerateAudioPreview {
  matchedMessages: number;
  eligibleMessages: number;
  plannedGenerations: number;
  missingAudioSlots: number;
  existingAudioSlots: number;
  byLanguage: Record<BulkGenerateAudioLanguage, number>;
}

export interface BulkGenerateAudioResult {
  matchedMessages: number;
  eligibleMessages: number;
  plannedGenerations: number;
  missingAudioSlots: number;
  existingAudioSlots: number;
  completedGenerations: number;
  skippedAlreadyPresent: number;
  failed: BulkGenerateAudioResultItem[];
  stoppedDueToLimit: boolean;
  remainingGenerations: number;
  completedSlots: string[];
}

/** Paginated list response (GET /messages?page=&limit=) */
export interface MessagesListPagination {
  page: number;
  limit: number;
  total: number;
  totalPages: number;
}
