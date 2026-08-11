import { describe, expect, it } from 'vitest';
import { parseRoute, routePath } from './navigation';

describe('navigation', () => {
  it('parses direct school detail links', () => {
    expect(parseRoute('/schools/9/')).toEqual({ view: 'detail', schoolId: 9 });
  });

  it('keeps only valid comparison ids and caps the list at four', () => {
    expect(parseRoute('/compare', '?ids=6,invalid,-1,9,8,7,5')).toEqual({
      view: 'compare',
      compareIds: [6, 9, 8, 7]
    });
  });

  it('falls back to the decision overview for unknown paths', () => {
    expect(parseRoute('/not-a-page')).toEqual({ view: 'home' });
  });

  it('creates stable detail and comparison urls', () => {
    expect(routePath('detail', { schoolId: 9 })).toBe('/schools/9');
    expect(routePath('compare', { compareIds: [6, 9] })).toBe('/compare?ids=6,9');
  });
});
