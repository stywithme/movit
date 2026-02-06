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
