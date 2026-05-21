import { describe, it, expect } from 'vitest';
import { validateImageFile } from '../validateImageFile';

function createMockFile(type: string, size: number): File {
  const buffer = new ArrayBuffer(size);
  return new File([buffer], 'test-file', { type });
}

describe('validateImageFile', () => {
  it('accepts a valid JPEG file under 5MB', () => {
    const file = createMockFile('image/jpeg', 1024);
    const result = validateImageFile(file);
    expect(result).toEqual({ valid: true });
  });

  it('accepts a valid PNG file under 5MB', () => {
    const file = createMockFile('image/png', 2 * 1024 * 1024);
    const result = validateImageFile(file);
    expect(result).toEqual({ valid: true });
  });

  it('accepts a valid GIF file under 5MB', () => {
    const file = createMockFile('image/gif', 100);
    const result = validateImageFile(file);
    expect(result).toEqual({ valid: true });
  });

  it('accepts a valid WebP file under 5MB', () => {
    const file = createMockFile('image/webp', 4 * 1024 * 1024);
    const result = validateImageFile(file);
    expect(result).toEqual({ valid: true });
  });

  it('accepts a file exactly at 5MB', () => {
    const file = createMockFile('image/png', 5 * 1024 * 1024);
    const result = validateImageFile(file);
    expect(result).toEqual({ valid: true });
  });

  it('rejects a file exceeding 5MB with size error', () => {
    const file = createMockFile('image/png', 5 * 1024 * 1024 + 1);
    const result = validateImageFile(file);
    expect(result.valid).toBe(false);
    expect(result.error).toBe('Maximum file size is 5MB.');
  });

  it('rejects an unsupported MIME type with type error', () => {
    const file = createMockFile('application/pdf', 1024);
    const result = validateImageFile(file);
    expect(result.valid).toBe(false);
    expect(result.error).toContain('Invalid file type');
    expect(result.error).toContain('application/pdf');
  });

  it('rejects text/plain MIME type', () => {
    const file = createMockFile('text/plain', 100);
    const result = validateImageFile(file);
    expect(result.valid).toBe(false);
    expect(result.error).toContain('Invalid file type');
  });

  it('rejects image/svg+xml (not in accepted list)', () => {
    const file = createMockFile('image/svg+xml', 1024);
    const result = validateImageFile(file);
    expect(result.valid).toBe(false);
    expect(result.error).toContain('Invalid file type');
  });

  it('checks type before size (type error takes priority)', () => {
    const file = createMockFile('application/pdf', 10 * 1024 * 1024);
    const result = validateImageFile(file);
    expect(result.valid).toBe(false);
    expect(result.error).toContain('Invalid file type');
  });
});
