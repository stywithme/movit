"use client";

import { useRef, useState } from 'react';
import { Button } from '@/components/ui';
import type { UploadCategory } from '@/lib/storage';

interface FileUploadProps {
  label: string;
  value?: string;
  onChange: (url: string) => void;
  uploadType: UploadCategory;
  accept?: string;
  helperText?: string;
  previewType?: 'image' | 'audio';
  disabled?: boolean;
}

export function FileUpload({
  label,
  value,
  onChange,
  uploadType,
  accept,
  helperText,
  previewType = 'image',
  disabled = false,
}: FileUploadProps) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handlePick = () => {
    inputRef.current?.click();
  };

  const handleUpload = async (file: File) => {
    setUploading(true);
    setError(null);

    try {
      const formData = new FormData();
      formData.append('file', file);

      const response = await fetch(`/api/uploads?type=${uploadType}`, {
        method: 'POST',
        body: formData,
      });

      const data = await response.json();
      if (!data.success) {
        throw new Error(data.error || 'Upload failed');
      }

      onChange(data.data.url);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setUploading(false);
    }
  };

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    void handleUpload(file);
  };

  const handleRemove = async () => {
    if (!value) return;
    setUploading(true);
    setError(null);
    try {
      await fetch('/api/uploads', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: value }),
      });
      onChange('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="space-y-2">
      <label className="block text-sm font-medium text-gray-700">{label}</label>
      <div className="flex items-center gap-2">
        <input
          ref={inputRef}
          type="file"
          accept={accept}
          onChange={handleFileChange}
          className="hidden"
          disabled={disabled || uploading}
        />
        <Button
          type="button"
          variant="secondary"
          size="sm"
          loading={uploading}
          onClick={handlePick}
          disabled={disabled}
        >
          Upload
        </Button>
        {value && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={handleRemove}
            disabled={disabled || uploading}
          >
            Remove
          </Button>
        )}
      </div>

      {helperText && <p className="text-xs text-gray-500">{helperText}</p>}
      {error && <p className="text-sm text-red-500">{error}</p>}

      {value && previewType === 'image' && (
        <div className="mt-2">
          <img
            src={value}
            alt="Preview"
            className="h-32 w-auto rounded-lg border border-gray-200"
            onError={(e) => (e.currentTarget.style.display = 'none')}
          />
        </div>
      )}

      {value && previewType === 'audio' && (
        <div className="mt-2">
          <audio controls src={value} className="w-full" />
        </div>
      )}
    </div>
  );
}
