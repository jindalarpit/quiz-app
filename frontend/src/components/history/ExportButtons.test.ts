/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

import { extractFilename } from './ExportButtons';

describe('ExportButtons - extractFilename', () => {
  it('extracts filename from Content-Disposition with quotes', () => {
    const header = 'attachment; filename="quiz-results-ABC123-20240315.csv"';
    expect(extractFilename(header, 'csv', 'session-1')).toBe(
      'quiz-results-ABC123-20240315.csv'
    );
  });

  it('extracts filename from Content-Disposition without quotes', () => {
    const header = 'attachment; filename=quiz-results-ABC123-2024-03-15.pdf';
    expect(extractFilename(header, 'pdf', 'session-1')).toBe(
      'quiz-results-ABC123-2024-03-15.pdf'
    );
  });

  it('returns fallback filename when Content-Disposition is null', () => {
    expect(extractFilename(null, 'csv', 'session-123')).toBe(
      'quiz-results-session-123.csv'
    );
  });

  it('returns fallback filename when Content-Disposition has no filename', () => {
    const header = 'attachment';
    expect(extractFilename(header, 'pdf', 'session-456')).toBe(
      'quiz-results-session-456.pdf'
    );
  });

  it('returns fallback filename when Content-Disposition is malformed', () => {
    const header = 'inline; something=else';
    expect(extractFilename(header, 'csv', 'abc')).toBe('quiz-results-abc.csv');
  });
});

describe('ExportButtons - downloadExport', () => {
  const originalFetch = global.fetch;
  const originalLocalStorage = global.localStorage;

  beforeEach(() => {
    // Mock localStorage
    const store: Record<string, string> = {
      'auth-storage': JSON.stringify({
        state: {
          tokens: { accessToken: 'test-token', refreshToken: 'refresh-token' },
          user: { id: 'user-123' },
        },
      }),
    };
    Object.defineProperty(global, 'localStorage', {
      value: {
        getItem: (key: string) => store[key] || null,
        setItem: (key: string, value: string) => { store[key] = value; },
        removeItem: (key: string) => { delete store[key]; },
      },
      writable: true,
    });
  });

  afterEach(() => {
    global.fetch = originalFetch;
    Object.defineProperty(global, 'localStorage', {
      value: originalLocalStorage,
      writable: true,
    });
  });

  it('calls fetch with correct URL and headers for CSV export', async () => {
    const mockBlob = new Blob(['csv,data'], { type: 'text/csv' });
    const mockResponse = {
      ok: true,
      headers: new Headers({
        'Content-Disposition': 'attachment; filename="quiz-results-PIN123-20240315.csv"',
      }),
      blob: () => Promise.resolve(mockBlob),
    };

    global.fetch = vi.fn().mockResolvedValue(mockResponse);

    // Mock DOM methods for download trigger
    const mockLink = {
      href: '',
      download: '',
      click: vi.fn(),
    };
    const createElementSpy = vi.spyOn(document, 'createElement').mockReturnValue(mockLink as unknown as HTMLElement);
    const appendChildSpy = vi.spyOn(document.body, 'appendChild').mockImplementation(() => mockLink as unknown as Node);
    const removeChildSpy = vi.spyOn(document.body, 'removeChild').mockImplementation(() => mockLink as unknown as Node);

    // jsdom doesn't have URL.createObjectURL/revokeObjectURL, so define them
    URL.createObjectURL = vi.fn().mockReturnValue('blob:http://localhost/fake-url');
    URL.revokeObjectURL = vi.fn();

    const { downloadExport } = await import('./ExportButtons');
    await downloadExport('session-abc', 'csv');

    expect(global.fetch).toHaveBeenCalledWith(
      '/api/export/session-abc/csv',
      expect.objectContaining({
        method: 'GET',
        headers: expect.objectContaining({
          Authorization: 'Bearer test-token',
          'X-User-Id': 'user-123',
        }),
      })
    );

    expect(mockLink.download).toBe('quiz-results-PIN123-20240315.csv');
    expect(mockLink.click).toHaveBeenCalled();

    createElementSpy.mockRestore();
    appendChildSpy.mockRestore();
    removeChildSpy.mockRestore();
  });

  it('throws error when response is not ok', async () => {
    const mockResponse = {
      ok: false,
      status: 404,
      json: () => Promise.resolve({ message: 'Session not found' }),
    };

    global.fetch = vi.fn().mockResolvedValue(mockResponse);

    const { downloadExport } = await import('./ExportButtons');

    await expect(downloadExport('nonexistent', 'csv')).rejects.toThrow(
      'Session not found'
    );
  });

  it('throws generic error when response body cannot be parsed', async () => {
    const mockResponse = {
      ok: false,
      status: 500,
      json: () => Promise.reject(new Error('parse error')),
    };

    global.fetch = vi.fn().mockResolvedValue(mockResponse);

    const { downloadExport } = await import('./ExportButtons');

    await expect(downloadExport('session-1', 'pdf')).rejects.toThrow(
      'Export failed with status 500'
    );
  });
});
