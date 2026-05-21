const ACCEPTED_IMAGE_TYPES = [
  'image/jpeg',
  'image/png',
  'image/gif',
  'image/webp',
] as const;

const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB = 5,242,880 bytes

export interface FileValidationResult {
  valid: boolean;
  error?: string;
}

/**
 * Validates an image file for upload.
 * Checks MIME type against accepted formats and file size against the 5MB limit.
 */
export function validateImageFile(file: File): FileValidationResult {
  if (!ACCEPTED_IMAGE_TYPES.includes(file.type as (typeof ACCEPTED_IMAGE_TYPES)[number])) {
    return {
      valid: false,
      error: `Invalid file type "${file.type}". Accepted formats: JPEG, PNG, GIF, WebP.`,
    };
  }

  if (file.size > MAX_FILE_SIZE_BYTES) {
    return {
      valid: false,
      error: 'Maximum file size is 5MB.',
    };
  }

  return { valid: true };
}
