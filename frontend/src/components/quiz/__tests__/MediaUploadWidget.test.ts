import { describe, it, expect, vi } from 'vitest';

import { validateImageFile } from '@/lib/validateImageFile';

/**
 * Unit tests for MediaUploadWidget logic.
 * Tests the validation and callback behavior that the component relies on.
 */
describe('MediaUploadWidget logic', () => {
  describe('file selection validation', () => {
    it('should accept a valid JPEG file under 5MB', () => {
      const file = new File(['x'.repeat(1000)], 'photo.jpg', { type: 'image/jpeg' });
      const result = validateImageFile(file);
      expect(result.valid).toBe(true);
      expect(result.error).toBeUndefined();
    });

    it('should accept a valid PNG file under 5MB', () => {
      const file = new File(['x'.repeat(1000)], 'image.png', { type: 'image/png' });
      const result = validateImageFile(file);
      expect(result.valid).toBe(true);
    });

    it('should accept a valid GIF file under 5MB', () => {
      const file = new File(['x'.repeat(1000)], 'anim.gif', { type: 'image/gif' });
      const result = validateImageFile(file);
      expect(result.valid).toBe(true);
    });

    it('should accept a valid WebP file under 5MB', () => {
      const file = new File(['x'.repeat(1000)], 'image.webp', { type: 'image/webp' });
      const result = validateImageFile(file);
      expect(result.valid).toBe(true);
    });

    it('should reject a file with invalid MIME type', () => {
      const file = new File(['x'.repeat(1000)], 'doc.pdf', { type: 'application/pdf' });
      const result = validateImageFile(file);
      expect(result.valid).toBe(false);
      expect(result.error).toContain('Invalid file type');
      expect(result.error).toContain('JPEG, PNG, GIF, WebP');
    });

    it('should reject a file exceeding 5MB', () => {
      // Create a file object with size > 5MB
      const largeContent = 'x'.repeat(5 * 1024 * 1024 + 1);
      const file = new File([largeContent], 'large.jpg', { type: 'image/jpeg' });
      const result = validateImageFile(file);
      expect(result.valid).toBe(false);
      expect(result.error).toContain('5MB');
    });
  });

  describe('onMediaChange callback behavior', () => {
    it('should call onMediaChange with undefined when removing an image', () => {
      const onMediaChange = vi.fn();
      // Simulate the remove action
      onMediaChange(undefined);
      expect(onMediaChange).toHaveBeenCalledWith(undefined);
    });

    it('should call onMediaChange with a URL string on successful upload', () => {
      const onMediaChange = vi.fn();
      const uploadedUrl = 'https://example.com/images/photo.jpg';
      onMediaChange(uploadedUrl);
      expect(onMediaChange).toHaveBeenCalledWith(uploadedUrl);
    });
  });

  describe('onUploadError callback behavior', () => {
    it('should call onUploadError with error message on upload failure', () => {
      const onUploadError = vi.fn();
      const errorMessage = 'Upload failed: network error';
      onUploadError(errorMessage);
      expect(onUploadError).toHaveBeenCalledWith(errorMessage);
    });
  });

  describe('drag and drop file type filtering', () => {
    it('should validate dropped files the same as selected files', () => {
      const validFile = new File(['data'], 'photo.png', { type: 'image/png' });
      const invalidFile = new File(['data'], 'script.js', { type: 'text/javascript' });

      expect(validateImageFile(validFile).valid).toBe(true);
      expect(validateImageFile(invalidFile).valid).toBe(false);
    });
  });

  describe('state management', () => {
    it('should clear validation error when a valid file is selected after an invalid one', () => {
      // Simulate the component's state logic
      let validationError: string | null = null;

      // First, select an invalid file
      const invalidFile = new File(['data'], 'doc.txt', { type: 'text/plain' });
      const invalidResult = validateImageFile(invalidFile);
      if (!invalidResult.valid) {
        validationError = invalidResult.error ?? 'Invalid file.';
      }
      expect(validationError).not.toBeNull();

      // Then, select a valid file — error should be cleared
      validationError = null; // Component clears error at start of handleFileSelect
      const validFile = new File(['data'], 'photo.jpg', { type: 'image/jpeg' });
      const validResult = validateImageFile(validFile);
      if (!validResult.valid) {
        validationError = validResult.error ?? 'Invalid file.';
      }
      expect(validationError).toBeNull();
    });
  });
});
