'use client';

import { useCallback, useRef, useState } from 'react';

import { cn } from '@/lib/utils';
import { validateImageFile } from '@/lib/validateImageFile';

const ACCEPTED_FORMATS = '.jpg,.jpeg,.png,.gif,.webp';

interface MediaUploadWidgetProps {
  currentMediaUrl?: string;
  onMediaChange: (url: string | undefined) => void;
  onFileSelect?: (file: File | undefined) => void;
  onUploadError?: (error: string) => void;
  isUploading?: boolean;
}

export function MediaUploadWidget({
  currentMediaUrl,
  onMediaChange,
  onFileSelect,
  onUploadError,
  isUploading: externalIsUploading,
}: MediaUploadWidgetProps) {
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [isDragOver, setIsDragOver] = useState(false);
  const [internalIsUploading, setInternalIsUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const isUploading = externalIsUploading ?? internalIsUploading;

  const handleFileSelect = useCallback(
    (file: File) => {
      setValidationError(null);

      const result = validateImageFile(file);
      if (!result.valid) {
        setValidationError(result.error ?? 'Invalid file.');
        return;
      }

      // Generate a local preview URL
      const objectUrl = URL.createObjectURL(file);
      setPreviewUrl(objectUrl);

      // If parent handles file selection (deferred upload), notify parent
      if (onFileSelect) {
        onFileSelect(file);
        return;
      }

      // Legacy behavior: simulate upload internally
      setInternalIsUploading(true);
      setTimeout(() => {
        setInternalIsUploading(false);
        onMediaChange(objectUrl);
      }, 100);
    },
    [onMediaChange, onFileSelect]
  );

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (file) {
        handleFileSelect(file);
      }
      // Reset input so the same file can be re-selected
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    },
    [handleFileSelect]
  );

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setIsDragOver(false);

      const file = e.dataTransfer.files?.[0];
      if (file) {
        handleFileSelect(file);
      }
    },
    [handleFileSelect]
  );

  const handleClickUpload = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  const handleRemove = useCallback(() => {
    setPreviewUrl(null);
    setValidationError(null);
    onMediaChange(undefined);
    if (onFileSelect) {
      onFileSelect(undefined);
    }
  }, [onMediaChange, onFileSelect]);

  const handleReplace = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  const displayUrl = previewUrl || currentMediaUrl;

  // If there's a current image (either from preview or existing URL), show it with actions
  if (displayUrl) {
    return (
      <div className="space-y-2">
        <div className="relative rounded-lg border border-slate-200 bg-slate-50 p-2">
          <img
            src={displayUrl}
            alt="Question image preview"
            className="mx-auto max-h-[200px] w-full rounded object-contain"
          />
          {isUploading && (
            <div className="absolute inset-0 flex items-center justify-center rounded-lg bg-white/70">
              <span className="text-sm text-slate-600">Uploading...</span>
            </div>
          )}
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={handleReplace}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-100"
          >
            Replace
          </button>
          <button
            type="button"
            onClick={handleRemove}
            className="rounded-md border border-red-300 px-3 py-1.5 text-sm font-medium text-red-600 transition-colors hover:bg-red-50"
          >
            Remove
          </button>
        </div>
        <input
          ref={fileInputRef}
          type="file"
          accept={ACCEPTED_FORMATS}
          onChange={handleInputChange}
          className="hidden"
          aria-label="Upload image file"
        />
        {validationError && (
          <p className="text-sm text-red-600" role="alert">
            {validationError}
          </p>
        )}
      </div>
    );
  }

  // Drop zone / click-to-upload area
  return (
    <div className="space-y-2">
      <div
        role="button"
        tabIndex={0}
        onClick={handleClickUpload}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            handleClickUpload();
          }
        }}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        className={cn(
          'flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed p-6 transition-colors',
          isDragOver
            ? 'border-blue-400 bg-blue-50'
            : 'border-slate-300 bg-slate-50 hover:border-slate-400 hover:bg-slate-100'
        )}
        aria-label="Drop image here or click to upload"
      >
        <svg
          className="mb-2 h-8 w-8 text-slate-400"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
          />
        </svg>
        <p className="text-sm font-medium text-slate-600">
          Drop an image here or click to upload
        </p>
        <p className="mt-1 text-xs text-slate-400">
          JPEG, PNG, GIF, WebP — max 5MB
        </p>
      </div>
      <input
        ref={fileInputRef}
        type="file"
        accept={ACCEPTED_FORMATS}
        onChange={handleInputChange}
        className="hidden"
        aria-label="Upload image file"
      />
      {validationError && (
        <p className="text-sm text-red-600" role="alert">
          {validationError}
        </p>
      )}
    </div>
  );
}
