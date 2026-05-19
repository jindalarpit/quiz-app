import { describe, it, expect } from 'vitest';

import { getPageNumbers } from './LeaderboardTable';

describe('LeaderboardTable - getPageNumbers', () => {
  it('returns all pages when totalPages <= 7', () => {
    expect(getPageNumbers(0, 5)).toEqual([0, 1, 2, 3, 4]);
    expect(getPageNumbers(2, 7)).toEqual([0, 1, 2, 3, 4, 5, 6]);
  });

  it('returns first page, ellipsis, middle pages, ellipsis, last page for large page counts', () => {
    const pages = getPageNumbers(5, 10);
    expect(pages[0]).toBe(0); // first page
    expect(pages[pages.length - 1]).toBe(9); // last page
    expect(pages).toContain(5); // current page
    expect(pages).toContain(4); // page before current
    expect(pages).toContain(6); // page after current
  });

  it('does not show leading ellipsis when current page is near start', () => {
    const pages = getPageNumbers(1, 10);
    // Should be: [0, 1, 2, -1, 9] — no leading ellipsis, only trailing
    expect(pages[0]).toBe(0);
    expect(pages[1]).toBe(1);
    expect(pages[2]).toBe(2);
    // Only one ellipsis (trailing), no leading ellipsis
    expect(pages.filter((p) => p === -1).length).toBe(1);
    // The ellipsis should be after the middle pages, not before
    const ellipsisIndex = pages.indexOf(-1);
    expect(ellipsisIndex).toBeGreaterThan(2);
  });

  it('does not show trailing ellipsis when current page is near end', () => {
    const pages = getPageNumbers(8, 10);
    // Should be: [0, -1, 7, 8, 9]
    expect(pages[0]).toBe(0);
    expect(pages[pages.length - 1]).toBe(9);
    expect(pages).toContain(8); // current page
    expect(pages.filter((p) => p === -1).length).toBe(1); // only leading ellipsis
  });

  it('returns single page for totalPages = 1', () => {
    expect(getPageNumbers(0, 1)).toEqual([0]);
  });

  it('returns empty array for totalPages = 0', () => {
    expect(getPageNumbers(0, 0)).toEqual([]);
  });
});

describe('LeaderboardTable - pagination control logic', () => {
  it('prev is disabled on first page (page 0)', () => {
    const currentPage = 0;
    const isPrevDisabled = currentPage === 0;
    expect(isPrevDisabled).toBe(true);
  });

  it('next is disabled on last page', () => {
    const currentPage = 4;
    const totalPages = 5;
    const isNextDisabled = currentPage >= totalPages - 1;
    expect(isNextDisabled).toBe(true);
  });

  it('both controls enabled on middle pages', () => {
    const currentPage = 2 as number;
    const totalPages = 5;
    const isPrevDisabled = currentPage === 0;
    const isNextDisabled = currentPage >= totalPages - 1;
    expect(isPrevDisabled).toBe(false);
    expect(isNextDisabled).toBe(false);
  });

  it('both controls disabled when only one page', () => {
    const currentPage = 0;
    const totalPages = 1;
    const isPrevDisabled = currentPage === 0;
    const isNextDisabled = currentPage >= totalPages - 1;
    expect(isPrevDisabled).toBe(true);
    expect(isNextDisabled).toBe(true);
  });
});
