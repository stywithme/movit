'use client';

/**
 * MessageFormModal - Create or Edit a feedback message
 * ====================================================
 * 
 * Reusable modal for creating/editing messages from anywhere:
 * - Messages Library page
 * - Exercise Wizard (quick-create inline)
 * - Any future integration
 * 
 * Props:
 * - open: boolean
 * - onOpenChange: (open: boolean) => void
 * - editMessage?: existing message data to edit
 * - onSaved: (message) => void - called after save
 * - defaults?: partial defaults for new messages
 */

import { useState, useEffect, useRef } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogBody,
  DialogFooter,
  Button,
  Input,
  Select,
  Checkbox,
} from '@/components/ui';
import { SmartLocalizedInput } from '@/components/forms/SmartLocalizedInput';
import type { LocalizedTextWithAudio } from '@/lib/types/localized';

export interface MessageFormData {
  id?: string;
  code: string;
  category: string;
  context: string | null;
  /** Read-only for seeded system messages */
  description?: string | null;
  content: LocalizedTextWithAudio;
  tags: string[];
  isSystem: boolean;
  isActive: boolean;
}

interface MessageFormModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  editMessage?: MessageFormData | null;
  onSaved?: (message: MessageFormData) => void;
  defaults?: Partial<MessageFormData>;
}

const CATEGORY_OPTIONS = [
  { value: 'state', label: 'State' },
  { value: 'position', label: 'Position' },
  { value: 'motivational', label: 'Motivational' },
  { value: 'tip', label: 'Tip' },
  { value: 'system', label: 'System' },
];

const CONTEXT_OPTIONS = [
  { value: '', label: 'None' },
  { value: 'perfect', label: 'Perfect' },
  { value: 'normal', label: 'Normal' },
  { value: 'pad', label: 'Pad' },
  { value: 'warning', label: 'Warning' },
  { value: 'danger', label: 'Danger' },
  { value: 'general', label: 'General' },
  { value: 'motivational', label: 'Motivational' },
  { value: 'tip', label: 'Tip' },
  { value: 'error', label: 'Error' },
];

export function MessageFormModal({
  open,
  onOpenChange,
  editMessage,
  onSaved,
  defaults,
}: MessageFormModalProps) {
  const isEditing = !!editMessage?.id;

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [code, setCode] = useState('');
  const [category, setCategory] = useState('state');
  const [context, setContext] = useState('');
  const [tagsInput, setTagsInput] = useState('');
  const [content, setContent] = useState<{ ar: string; en: string }>({ ar: '', en: '' });
  const [audio, setAudio] = useState<{ ar?: string; en?: string }>({});
  const [isSystem, setIsSystem] = useState(false);
  const [isActive, setIsActive] = useState(true);
  const [description, setDescription] = useState<string | null>(null);

  // Track previous open state to only reset on open transition
  const prevOpenRef = useRef(false);

  // Reset form only when modal opens (not on every prop change)
  useEffect(() => {
    if (open && !prevOpenRef.current) {
      if (editMessage) {
        setCode(editMessage.code);
        setCategory(editMessage.category);
        setContext(editMessage.context || '');
        setTagsInput(editMessage.tags.join(', '));
        setContent({ ar: editMessage.content.ar || '', en: editMessage.content.en || '' });
        setAudio({ ar: editMessage.content.audioAr, en: editMessage.content.audioEn });
        setIsSystem(editMessage.isSystem);
        setIsActive(editMessage.isActive);
        setDescription(editMessage.description ?? null);
      } else {
        setCode(defaults?.code || '');
        setCategory(defaults?.category || 'state');
        setContext(defaults?.context || '');
        setTagsInput(defaults?.tags?.join(', ') || '');
        setContent({ ar: defaults?.content?.ar || '', en: defaults?.content?.en || '' });
        setAudio({ ar: defaults?.content?.audioAr, en: defaults?.content?.audioEn });
        setIsSystem(defaults?.isSystem || false);
        setIsActive(defaults?.isActive ?? true);
        setDescription(defaults?.description ?? null);
      }
      setError(null);
    }
    prevOpenRef.current = open;
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleSubmit = async () => {
    if (!code.trim()) {
      setError('Code is required');
      return;
    }
    if (!content.en && !content.ar) {
      setError('At least one language is required');
      return;
    }

    setSaving(true);
    setError(null);

    try {
      const tags = tagsInput
        .split(',')
        .map((t) => t.trim())
        .filter(Boolean);

      const contentPayload = {
        ...content,
        audioAr: audio.ar,
        audioEn: audio.en,
      };

      const isLockedSystem = isEditing && editMessage?.isSystem;

      const payload = isLockedSystem
        ? { content: contentPayload }
        : {
            code: code.trim(),
            category,
            context: context || null,
            tags,
            isSystem,
            isActive,
            content: contentPayload,
          };

      const url = isEditing ? `/api/messages/${editMessage!.id}` : '/api/messages';
      const method = isEditing ? 'PUT' : 'POST';

      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await res.json();
      if (data.success) {
        onSaved?.({
          id: data.data.id,
          code: data.data.code,
          category: data.data.category,
          context: data.data.context,
          description: data.data.description ?? null,
          content: data.data.content,
          tags: data.data.tags,
          isSystem: data.data.isSystem,
          isActive: data.data.isActive,
        });
        onOpenChange(false);
      } else {
        setError(data.error || 'Failed to save message');
      }
    } catch (err) {
      console.error('Error saving message:', err);
      setError('Failed to save message');
    } finally {
      setSaving(false);
    }
  };

  const canSubmit =
    (isEditing && editMessage?.isSystem ? true : code.trim()) && (content.en || content.ar);

  const lockSystemFields = isEditing && !!editMessage?.isSystem;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent size="lg">
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Edit Message' : 'New Message'}</DialogTitle>
          <DialogDescription>
            {isEditing
              ? 'Update the message details below.'
              : 'Create a reusable feedback message for exercises.'}
          </DialogDescription>
        </DialogHeader>

        <DialogBody>
          <div className="space-y-5">
            {error && (
              <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
                {error}
              </div>
            )}

            {lockSystemFields && (
              <div className="p-3 bg-slate-50 border border-slate-200 rounded-lg text-sm text-slate-800">
                <p className="font-medium text-slate-900 mb-1">System message (read-only)</p>
                <p className="text-slate-600">
                  {description?.trim() || 'Fixed key for the mobile app; only text and audio below can be changed.'}
                </p>
              </div>
            )}

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Code <span className="text-red-500">*</span>
              </label>
              <Input
                value={code}
                onChange={(e) => setCode(e.target.value)}
                placeholder="STATE_PERFECT_001"
                disabled={lockSystemFields}
              />
              <p className="mt-1 text-xs text-gray-500">
                Unique identifier (e.g. STATE_PERFECT_001, MOT_GENERAL_001)
              </p>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Category</label>
                <Select
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  options={CATEGORY_OPTIONS}
                  disabled={lockSystemFields}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Context</label>
                <Select
                  value={context}
                  onChange={(e) => setContext(e.target.value)}
                  options={CONTEXT_OPTIONS}
                  disabled={lockSystemFields}
                />
              </div>
            </div>

            <SmartLocalizedInput
              label="Message Content"
              value={content}
              onChange={setContent}
              audioValue={audio}
              onAudioChange={setAudio}
              multiline
              rows={3}
              required
              enableTranslation
              enableTTS
            />

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Tags</label>
              <Input
                value={tagsInput}
                onChange={(e) => setTagsInput(e.target.value)}
                placeholder="knee, squat, form, lower-body"
                disabled={lockSystemFields}
              />
              <p className="mt-1 text-xs text-gray-500">Comma-separated tags for filtering</p>
            </div>

            <div className="flex items-center gap-6">
              <label className="flex items-center gap-2 text-sm text-gray-700">
                <Checkbox
                  checked={isSystem}
                  onCheckedChange={(checked) => setIsSystem(!!checked)}
                  disabled={isEditing && editMessage?.isSystem}
                />
                System Message
              </label>
              <label className="flex items-center gap-2 text-sm text-gray-700">
                <Checkbox
                  checked={isActive}
                  onCheckedChange={(checked) => setIsActive(!!checked)}
                  disabled={lockSystemFields}
                />
                Active
              </label>
            </div>
          </div>
        </DialogBody>

        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={!canSubmit || saving}>
            {saving ? 'Saving...' : isEditing ? 'Save Changes' : 'Create Message'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
