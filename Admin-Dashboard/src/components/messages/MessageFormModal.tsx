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

import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogBody,
  DialogFooter,
  Button,
  Label,
  Input,
  Select,
  Checkbox,
} from '@/components/ui';
import { Copy, Hash, Languages, Settings2, Shield, Sparkles, Tags } from 'lucide-react';
import { SmartLocalizedInput } from '@/components/forms/SmartLocalizedInput';
import { useTtsDefaults } from '@/hooks/useTtsDefaults';
import type { LocalizedTextWithAudio } from '@/lib/types/localized';
import {
  buildAutoMessageCodePreview,
  getDefaultContextForCategory,
  getMessageCategoryHint,
  getMessageContextOptions,
} from '@/lib/utils/messageCode';

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

export function MessageFormModal({
  open,
  onOpenChange,
  editMessage,
  onSaved,
  defaults,
}: MessageFormModalProps) {
  const isEditing = !!editMessage?.id;
  const { defaults: ttsUserDefaults } = useTtsDefaults();

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copiedCode, setCopiedCode] = useState(false);

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
  const copyTimeoutRef = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (copyTimeoutRef.current) {
        window.clearTimeout(copyTimeoutRef.current);
      }
    };
  }, []);

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
        const nextCategory = defaults?.isSystem ? 'system' : defaults?.category || 'state';
        setCode(defaults?.code || '');
        setCategory(nextCategory);
        setContext(defaults?.context || getDefaultContextForCategory(nextCategory));
        setTagsInput(defaults?.tags?.join(', ') || '');
        setContent({ ar: defaults?.content?.ar || '', en: defaults?.content?.en || '' });
        setAudio({ ar: defaults?.content?.audioAr, en: defaults?.content?.audioEn });
        setIsSystem(defaults?.isSystem || nextCategory === 'system');
        setIsActive(defaults?.isActive ?? true);
        setDescription(defaults?.description ?? null);
      }
      setError(null);
      setCopiedCode(false);
    }
    prevOpenRef.current = open;
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  const contextOptions = useMemo(() => getMessageContextOptions(category, context), [category, context]);
  const tagList = useMemo(
    () =>
      tagsInput
        .split(',')
        .map((tag) => tag.trim())
        .filter(Boolean),
    [tagsInput]
  );
  const hasCustomTtsDefaults = useMemo(
    () => Object.values(ttsUserDefaults).some((value) => value.trim()),
    [ttsUserDefaults]
  );
  const displayedCode = isEditing ? code : buildAutoMessageCodePreview(category, context);
  const codeHelpText = isEditing
    ? 'Stable message identifier. It stays read-only here to keep library references predictable.'
    : 'Preview only. The final numeric suffix is assigned automatically on save to keep codes unique.';
  const categoryHint = getMessageCategoryHint(category);

  const handleSubmit = async () => {
    if (!content.en?.trim() && !content.ar?.trim()) {
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
        ar: content.ar.trim(),
        en: content.en.trim(),
        audioAr: audio.ar,
        audioEn: audio.en,
      };

      const isLockedSystem = isEditing && editMessage?.isSystem;

      const payload = isLockedSystem
        ? { content: contentPayload }
        : {
            category,
            context: context || null,
            tags,
            isSystem,
            isActive,
            content: contentPayload,
            ...(!isEditing && code.trim() ? { code: code.trim() } : {}),
          };

      const url = isEditing ? `/api/messages/${editMessage!.id}` : '/api/messages';
      const method = isEditing ? 'PUT' : 'POST';

      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await res.json().catch(() => ({
        success: false,
        error: `Failed to save message (${res.status})`,
      }));
      if (res.ok && data.success) {
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

  const canSubmit = !!(content.en?.trim() || content.ar?.trim());

  const lockSystemFields = isEditing && !!editMessage?.isSystem;

  const handleCategoryChange = (nextCategory: string) => {
    if (lockSystemFields) return;
    setCategory(nextCategory);
    if (nextCategory === 'system') {
      setIsSystem(true);
    } else if (isSystem) {
      setIsSystem(false);
    }

    const nextOptions = getMessageContextOptions(nextCategory, context).map((option) => option.value);
    if (!nextOptions.includes(context)) {
      setContext(getDefaultContextForCategory(nextCategory));
    }
  };

  const handleSystemToggle = (checked: boolean) => {
    if (lockSystemFields) return;
    const next = !!checked;
    setIsSystem(next);
    if (next) {
      setCategory('system');
      setContext(getDefaultContextForCategory('system'));
      return;
    }
    if (category === 'system') {
      setCategory('state');
      setContext(getDefaultContextForCategory('state'));
    }
  };

  const handleCopyCode = async () => {
    if (!code.trim() || !navigator.clipboard) return;
    try {
      await navigator.clipboard.writeText(code.trim());
      setCopiedCode(true);
      if (copyTimeoutRef.current) {
        window.clearTimeout(copyTimeoutRef.current);
      }
      copyTimeoutRef.current = window.setTimeout(() => setCopiedCode(false), 1500);
    } catch (copyError) {
      console.error('Failed to copy message code:', copyError);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent size="xl">
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Edit Message' : 'New Message'}</DialogTitle>
          <DialogDescription>
            {isEditing
              ? 'Update the library message content, audio, and metadata.'
              : 'Create a reusable feedback message. The code is generated automatically.'}
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

            {!isEditing && (
              <div className="rounded-xl border border-blue-100 bg-blue-50 p-4">
                <div className="flex items-start gap-3">
                  <Sparkles className="w-5 h-5 text-blue-600 mt-0.5" />
                  <div>
                    <p className="text-sm font-medium text-blue-900">Faster create flow</p>
                    <p className="text-sm text-blue-700 mt-1">
                      Choose the category and context, then write the message. The final code is generated
                      automatically when you save.
                    </p>
                  </div>
                </div>
              </div>
            )}

            <div className="grid grid-cols-1 xl:grid-cols-[1.2fr_0.8fr] gap-5">
              <div className="rounded-xl border border-gray-200 bg-white p-4 space-y-4">
                <div className="flex items-start gap-3">
                  <Sparkles className="w-5 h-5 text-blue-600 mt-0.5" />
                  <div>
                    <h3 className="text-sm font-semibold text-gray-900">Message setup</h3>
                    <p className="text-sm text-gray-500 mt-1">{categoryHint}</p>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <Label>Category</Label>
                    <Select
                      value={category}
                      onChange={(e) => handleCategoryChange(e.target.value)}
                      options={CATEGORY_OPTIONS}
                      disabled={lockSystemFields}
                    />
                  </div>
                  <div>
                    <Label>Context</Label>
                    <Select
                      value={context}
                      onChange={(e) => setContext(e.target.value)}
                      options={contextOptions}
                      disabled={lockSystemFields}
                    />
                    <p className="mt-1 text-xs text-gray-500">
                      Context is used for filtering and also shapes the generated code.
                    </p>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <label className="flex items-start gap-3 rounded-xl border border-gray-200 bg-gray-50 p-3">
                    <Checkbox
                      checked={isSystem}
                      onCheckedChange={(checked) => handleSystemToggle(!!checked)}
                      disabled={isEditing && editMessage?.isSystem}
                    />
                    <div className="space-y-1">
                      <span className="text-sm font-medium text-gray-900">System message</span>
                      <p className="text-xs text-gray-500">
                        Fixed app/training message. Turning this on switches the category to `system`.
                      </p>
                    </div>
                  </label>

                  <label className="flex items-start gap-3 rounded-xl border border-gray-200 bg-gray-50 p-3">
                    <Checkbox
                      checked={isActive}
                      onCheckedChange={(checked) => setIsActive(!!checked)}
                      disabled={lockSystemFields}
                    />
                    <div className="space-y-1">
                      <span className="text-sm font-medium text-gray-900">Active</span>
                      <p className="text-xs text-gray-500">
                        Inactive messages stay in the library but are easier to exclude from selection.
                      </p>
                    </div>
                  </label>
                </div>

                {isSystem && !lockSystemFields && (
                  <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
                    New system messages should be used carefully because their keys often become long-lived references.
                  </div>
                )}
              </div>

              <div className="rounded-xl border border-gray-200 bg-white p-4 space-y-4">
                <div className="flex items-start gap-3">
                  <Hash className="w-5 h-5 text-gray-700 mt-0.5" />
                  <div>
                    <h3 className="text-sm font-semibold text-gray-900">Code and tags</h3>
                    <p className="text-sm text-gray-500 mt-1">
                      Keep the message searchable and consistent across the library.
                    </p>
                  </div>
                </div>

                <div>
                  <Label>{isEditing ? 'Message code' : 'Auto-generated code'}</Label>
                  <div className="mt-1 rounded-xl border border-gray-200 bg-gray-50 px-4 py-3">
                    <div className="flex items-center justify-between gap-3">
                      <span className="font-mono text-sm text-gray-900 break-all">{displayedCode}</span>
                      {isEditing && code.trim() && (
                        <Button type="button" variant="ghost" size="sm" onClick={handleCopyCode} icon={<Copy className="w-4 h-4" />}>
                          {copiedCode ? 'Copied' : 'Copy'}
                        </Button>
                      )}
                    </div>
                    <p className="mt-2 text-xs text-gray-500">{codeHelpText}</p>
                  </div>
                </div>

                <div>
                  <Label>Tags</Label>
                  <Input
                    value={tagsInput}
                    onChange={(e) => setTagsInput(e.target.value)}
                    placeholder="knee, squat, form, lower-body"
                    disabled={lockSystemFields}
                  />
                  <p className="mt-1 text-xs text-gray-500">
                    Add comma-separated keywords to make the message easier to find later.
                  </p>
                  {tagList.length > 0 && (
                    <div className="mt-3 flex flex-wrap gap-2">
                      {tagList.map((tag) => (
                        <span
                          key={tag}
                          className="inline-flex items-center gap-1 rounded-full border border-gray-200 bg-gray-50 px-2.5 py-1 text-xs font-medium text-gray-700"
                        >
                          <Tags className="w-3 h-3" />
                          {tag}
                        </span>
                      ))}
                    </div>
                  )}
                </div>

                <div className="rounded-xl border border-blue-100 bg-blue-50 p-3">
                  <div className="flex items-start gap-2">
                    <Settings2 className="w-4 h-4 text-blue-600 mt-0.5" />
                    <div>
                      <p className="text-sm font-medium text-blue-900">TTS defaults</p>
                      <p className="text-xs text-blue-700 mt-1">
                        {hasCustomTtsDefaults
                          ? 'Single-message audio uses your saved default TTS settings from the messages page gear icon.'
                          : 'Single-message audio currently uses the server defaults. You can customize them from the messages page gear icon.'}
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="rounded-xl border border-gray-200 bg-white p-4 space-y-4">
              <div className="flex items-start gap-3">
                <Languages className="w-5 h-5 text-emerald-600 mt-0.5" />
                <div>
                  <h3 className="text-sm font-semibold text-gray-900">Bilingual content</h3>
                  <p className="text-sm text-gray-500 mt-1">
                    Write the English and Arabic versions side by side. Use the inline translate and audio actions as needed.
                  </p>
                </div>
              </div>

              <SmartLocalizedInput
                label="Message Content"
                value={content}
                onChange={setContent}
                audioValue={audio}
                onAudioChange={setAudio}
                multiline
                rows={4}
                required
                enableTranslation
                enableTTS
                ttsUserDefaults={ttsUserDefaults}
                aiRouteBase="/api/messages/ai"
                variant="compact"
              />
            </div>
          </div>
        </DialogBody>

        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={!canSubmit} loading={saving} icon={!saving ? <Shield className="w-4 h-4" /> : undefined}>
            {isEditing ? 'Save Changes' : 'Create Message'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
