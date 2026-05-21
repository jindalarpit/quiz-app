// Feature: quiz-enhancements-ui, Property 4: File validation rejects invalid files
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { validateImageFile } from '../validateImageFile';

/**
 * Property-based tests for file validation (Property 4).
 *
 * **Validates: Requirements 2.3, 2.4, 2.5**
 *
 * Property 4: File validation rejects invalid files — For any file with a MIME type
 * not in ['image/jpeg', 'image/png', 'image/gif', 'image/webp'] OR a size exceeding
 * 5,242,880 bytes, the validation function SHALL return a rejection result. Conversely,
 * for any file with an accepted MIME type AND size ≤ 5,242,880 bytes, the validation
 * function SHALL return an acceptance result.
 */

const ACCEPTED_MIME_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'] as const;
const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5,242,880 bytes

// Generator for valid MIME types
const validMimeTypeArb = fc.constantFrom(...ACCEPTED_MIME_TYPES);

// Generator for invalid MIME types
const invalidMimeTypeArb = fc.oneof(
  fc.constantFrom(
    'application/pdf',
    'text/plain',
    'image/svg+xml',
    'image/bmp',
    'image/tiff',
    'video/mp4',
    'audio/mpeg',
    'application/json',
    'application/octet-stream',
    'text/html'
  ),
  fc.string({ minLength: 1, maxLength: 50 }).filter(
    (s) => !ACCEPTED_MIME_TYPES.includes(s as (typeof ACCEPTED_MIME_TYPES)[number])
  )
);

// Generator for valid file sizes (1 byte to 5MB inclusive)
const validFileSizeArb = fc.integer({ min: 1, max: MAX_FILE_SIZE });

// Generator for invalid file sizes (exceeding 5MB, up to 10MB)
const invalidFileSizeArb = fc.integer({ min: MAX_FILE_SIZE + 1, max: 10 * 1024 * 1024 });

/**
 * Helper to create a mock File object with a given MIME type and size.
 */
function createMockFile(type: string, size: number): File {
  const buffer = new ArrayBuffer(size);
  return new File([buffer], 'test-file', { type });
}

describe('File Validation Properties (Property 4)', () => {
  it('P4.1: Valid MIME type AND valid size → acceptance', () => {
    fc.assert(
      fc.property(validMimeTypeArb, validFileSizeArb, (mimeType, size) => {
        const file = createMockFile(mimeType, size);
        const result = validateImageFile(file);

        expect(result.valid).toBe(true);
        expect(result.error).toBeUndefined();
      }),
      { numRuns: 200 }
    );
  });

  it('P4.2: Invalid MIME type → rejection with type error', () => {
    fc.assert(
      fc.property(
        invalidMimeTypeArb,
        fc.integer({ min: 1, max: 10 * 1024 * 1024 }),
        (mimeType, size) => {
          const file = createMockFile(mimeType, size);
          const result = validateImageFile(file);

          expect(result.valid).toBe(false);
          expect(result.error).toBeDefined();
          expect(result.error).toContain('Invalid file type');
        }
      ),
      { numRuns: 200 }
    );
  });

  it('P4.3: Valid MIME type AND size exceeding 5MB → rejection with size error', () => {
    fc.assert(
      fc.property(validMimeTypeArb, invalidFileSizeArb, (mimeType, size) => {
        const file = createMockFile(mimeType, size);
        const result = validateImageFile(file);

        expect(result.valid).toBe(false);
        expect(result.error).toBeDefined();
        expect(result.error).toContain('5MB');
      }),
      { numRuns: 200 }
    );
  });

  it('P4.4: Any file with invalid type OR oversized → rejection (combined)', () => {
    const invalidFileArb = fc.oneof(
      // Invalid type, any size
      fc.tuple(invalidMimeTypeArb, fc.integer({ min: 1, max: 10 * 1024 * 1024 })),
      // Valid type, oversized
      fc.tuple(validMimeTypeArb, invalidFileSizeArb)
    );

    fc.assert(
      fc.property(invalidFileArb, ([mimeType, size]) => {
        const file = createMockFile(mimeType, size);
        const result = validateImageFile(file);

        expect(result.valid).toBe(false);
        expect(result.error).toBeDefined();
      }),
      { numRuns: 200 }
    );
  });

  it('P4.5: Boundary — file exactly at 5MB with valid type is accepted', () => {
    fc.assert(
      fc.property(validMimeTypeArb, (mimeType) => {
        const file = createMockFile(mimeType, MAX_FILE_SIZE);
        const result = validateImageFile(file);

        expect(result.valid).toBe(true);
        expect(result.error).toBeUndefined();
      }),
      { numRuns: 100 }
    );
  });

  it('P4.6: Boundary — file at 5MB + 1 byte with valid type is rejected', () => {
    fc.assert(
      fc.property(validMimeTypeArb, (mimeType) => {
        const file = createMockFile(mimeType, MAX_FILE_SIZE + 1);
        const result = validateImageFile(file);

        expect(result.valid).toBe(false);
        expect(result.error).toContain('5MB');
      }),
      { numRuns: 100 }
    );
  });
});
