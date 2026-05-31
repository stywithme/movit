'use client';

import { useEffect, useState } from 'react';
import LocalizedInput from '@/components/forms/LocalizedInput';
import {
  Button,
  Checkbox,
  Dialog,
  DialogBody,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Input,
} from '@/components/ui';
import type { Attribute, AttributeValue, AttributeValueFormData } from './types';

interface AttributeValueFormModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  attribute: Attribute | null;
  editValue?: AttributeValue | null;
  onSaved?: (value: AttributeValue) => void;
}

const EMPTY_FORM: AttributeValueFormData = {
  code: '',
  name: { ar: '', en: '' },
  description: { ar: '', en: '' },
  icon: '',
  color: '',
  isActive: true,
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

export function AttributeValueFormModal({
  open,
  onOpenChange,
  attribute,
  editValue,
  onSaved,
}: AttributeValueFormModalProps) {
  const isEditing = !!editValue?.id;

  const [formData, setFormData] = useState<AttributeValueFormData>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [codeManuallyEdited, setCodeManuallyEdited] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }

    if (editValue) {
      setFormData({
        code: editValue.code,
        name: editValue.name || { ar: '', en: '' },
        description: editValue.description || { ar: '', en: '' },
        icon: editValue.icon || '',
        color: editValue.color || '',
        isActive: editValue.isActive,
      });
      setCodeManuallyEdited(true);
    } else {
      setFormData(EMPTY_FORM);
      setCodeManuallyEdited(false);
    }

    setError(null);
  }, [open, editValue]);

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
    if (!attribute) {
      setError('Attribute is required');
      return;
    }

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
      const hasDescription =
        formData.description.en.trim().length > 0 || formData.description.ar.trim().length > 0;

      const payload = {
        code: formData.code.trim(),
        name: formData.name,
        description: hasDescription ? formData.description : null,
        icon: formData.icon.trim() || null,
        color: formData.color.trim() || null,
        isActive: formData.isActive,
      };

      const url = isEditing
        ? `/api/attributes/values/${editValue!.id}`
        : `/api/attributes/${attribute.code}/values`;
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

      setError(data.error || 'Failed to save attribute value');
    } catch (submitError) {
      console.error('Error saving attribute value:', submitError);
      setError('Failed to save attribute value');
    } finally {
      setSaving(false);
    }
  };

  if (!attribute) {
    return null;
  }

  const canSubmit =
    formData.code.trim().length > 0 &&
    (formData.name.en.trim().length > 0 || formData.name.ar.trim().length > 0);

  const attributeLabel = attribute.name.en || attribute.name.ar || attribute.code;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent size="lg">
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Edit Value' : 'New Value'}</DialogTitle>
          <DialogDescription>
            {isEditing
              ? `Update a value inside ${attributeLabel}.`
              : `Create a new value inside ${attributeLabel}.`}
          </DialogDescription>
        </DialogHeader>

        <DialogBody>
          <div className="space-y-5">
            {error && (
              <div className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
                {error}
              </div>
            )}

            <div className="rounded-lg border bg-muted/50 px-4 py-3 text-sm text-muted-foreground">
              Attribute Type: <span className="font-medium text-foreground">{attributeLabel}</span>
              <span className="ml-2 font-mono text-xs text-muted-foreground">({attribute.code})</span>
            </div>

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
                placeholder="front_squat"
              />
            </div>

            <LocalizedInput
              label="Name"
              value={formData.name}
              onChange={(name) => setFormData((prev) => ({ ...prev, name }))}
              required
            />

            <LocalizedInput
              label="Description"
              value={formData.description}
              onChange={(description) => setFormData((prev) => ({ ...prev, description }))}
              multiline
              rows={3}
            />

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label className="mb-1 block text-sm font-medium">Icon</label>
                <Input
                  value={formData.icon}
                  onChange={(event) =>
                    setFormData((prev) => ({ ...prev, icon: event.target.value }))
                  }
                  placeholder="Optional emoji or short icon"
                />
              </div>

              <div>
                <label className="mb-1 block text-sm font-medium">Color</label>
                <div className="flex items-center gap-3">
                  <Input
                    value={formData.color}
                    onChange={(event) =>
                      setFormData((prev) => ({ ...prev, color: event.target.value }))
                    }
                    placeholder="#3b82f6"
                  />
                  <div
                    className="size-10 rounded-lg border"
                    style={{ backgroundColor: formData.color || 'transparent' }}
                    title={formData.color || 'No color'}
                  />
                </div>
              </div>
            </div>

            <label className="flex items-center gap-3 text-sm">
              <Checkbox
                checked={formData.isActive}
                onCheckedChange={(checked) =>
                  setFormData((prev) => ({ ...prev, isActive: !!checked }))
                }
              />
              Active
            </label>
          </div>
        </DialogBody>

        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={!canSubmit} loading={saving}>
            {isEditing ? 'Save Changes' : 'Create Value'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
