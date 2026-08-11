import { describe, expect, it } from 'vitest';
import { parseStoredComparedIds, parseStoredFavorites } from './useShortlist';

describe('parseStoredFavorites', () => {
  it('returns an empty list for invalid persisted state', () => {
    expect(parseStoredFavorites('{broken')).toEqual([]);
    expect(parseStoredFavorites('{"schoolId":1}')).toEqual([]);
  });

  it('accepts persisted favorite arrays', () => {
    expect(parseStoredFavorites('[{"schoolId":9}]')).toEqual([expect.objectContaining({
      schoolId: 9, name: '院校 9', groupTag: '稳妥', note: ''
    })]);
  });

  it('migrates old notes and caps unsafe persisted content', () => {
    const stored = JSON.stringify([{ schoolId: 9, name: '西安电子科技大学', note: 'a'.repeat(320), savedAt: 'saved' }]);
    expect(parseStoredFavorites(stored)[0]).toMatchObject({ note: 'a'.repeat(300), noteUpdatedAt: 'saved' });
  });
});

describe('parseStoredComparedIds', () => {
  it('keeps unique positive integer ids and caps the list at four', () => {
    expect(parseStoredComparedIds('[9,9,-1,"8",7,6,5,4.5]')).toEqual([9, 7, 6, 5]);
  });

  it('rejects malformed or non-array values', () => {
    expect(parseStoredComparedIds('{broken')).toEqual([]);
    expect(parseStoredComparedIds('{"id":9}')).toEqual([]);
  });
});
