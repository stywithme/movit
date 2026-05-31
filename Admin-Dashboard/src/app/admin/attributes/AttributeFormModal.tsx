'use client';

import { useEffect, useState } from 'react';
import LocalizedInput from '@/components/forms/LocalizedInput';
import {
  Button,
  Dialog,
  DialogBody,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Input,
  Textarea,
} from '@/components/ui';
import type { Attribute, AttributeFormData } from './types';

interface AttributeFormModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  editAttribute?: Attribute | null;
  onSaved?: (attribute: Attribute) => void;
}

const EMPTY_FORM: AttributeFormData = {
  code: '',
  name: { ar: '', en: '' },
  description: '',
};

function normalizeCode(value: string) {
  return value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9\s_]/g, '')
    .replace(/\s+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '');
}

export function AttributeFormModal({
  open,
  onOpenChange,
  editAttribute,
  onSaved,
}: AttributeFormModalProps) {
  const isEditing = !!editAttribute?.id;

  const [formData, setFormData] = useState<AttributeFormData>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [codeManuallyEdited, setCodeManuallyEdited] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }

    if (editAttribute) {
      setFormData({
        code: editAttribute.code,
        name: editAttribute.name || { ar: '', en: '' },
        description: editAttribute.description || '',
      });
      setCodeManuallyEdited(true);
    } else {
      setFormData(EMPTY_FORM);
      setCodeManuallyEdited(false);
    }

    setError(null);
  }, [open, editAttribute]);

  useEffect(() => {
    if (!open || isEditing || codeManuallyEdited) {
      return;
    }

    const generatedCode = normalizeCode(formData.name.en || '');
    if (!generatedCode) {
      return;
    }

    setFormData((prev) => ({
      ...prev,
      code: generatedCode,
    }));
  }, [codeManuallyEdited, formData.name.en, isEditing, open]);

  const handleSubmit = async () => {
    if (!formData.code.trim()) {
      setError('Code is required');
      return;
    }

    if (!formData.name.en.trim() && !formData.name.ar.trim()) {
      setError('Name must have at least English or Arabic value');
      return;
    }

    setSaving(true);
    setError(null);

    try {
      const payload = {
        code: formData.code.trim(),
        name: formData.name,
        description: formData.description.trim() || null,
      };

      const url = isEditing ? `/api/attributes/${editAttribute!.id}` : '/api/attributes';
      const method = isEditing ? 'PUT' : 'POST';

      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await res.json();

      if (data.success) {
        onSaved?.(data.data);
        onOpenChange(false);
        return;
      }

      setError(data.error || 'Failed to save attribute');
    } catch (submitError) {
      console.error('Error saving attribute:', submitError);
      setError('Failed to save attribute');
    } finally {
      setSaving(false);
    }
  };

  const canSubmit =
    formData.code.trim().length > 0 &&
    (formData.name.en.trim().length > 0 || formData.name.ar.trim().length > 0);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent size="lg">
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Edit Attribute' : 'New Attribute'}</DialogTitle>
          <DialogDescription>
            {isEditing
              ? 'Update the attribute type details below.'
              : 'Create a new custom attribute type.'}
          </DialogDescription>
        </DialogHeader>

        <DialogBody>
          <div className="space-y-5">
            {error && (
              <div className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
                {error}
              </div>
            )}

            <div>
              <label className="mb-1 block text-sm font-medium">
                Code <span className="text-destructive">*</span>
              </label>
              <Input
                value={formData.code}
                onChange={(event) => {
                  setCodeManuallyEdited(true);
                  setFormData((prev) => ({
                    ...prev,
                    code: normalizeCode(event.target.value),
                  }));
                }}
                placeholder="custom_tag"
              />
              <p className="mt-1 text-xs text-muted-foreground">
                Unique key used internally. Lowercase letters, numbers, and underscores only.
              </p>
            </div>

            <LocalizedInput
              label="Name"
              value={formData.name}
              onChange={(name) => setFormData((prev) => ({ ...prev, name }))}
              required
            />

            <div>
              <label className="mb-1 block text-sm font-medium">
                Description
              </label>
              <Textarea
                value={formData.description}
                onChange={(event) =>
                  setFormData((prev) => ({ ...prev, description: event.target.value }))
                }
                rows={4}
                placeholder="Describe what this attribute is used for"
              />
            </div>
          </div>
        </DialogBody>

        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={!canSubmit} loading={saving}>
            {isEditing ? 'Save Changes' : 'Create Attribute'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
