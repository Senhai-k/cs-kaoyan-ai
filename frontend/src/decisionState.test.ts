import { describe, expect, it } from 'vitest';
import { addComparedSchool, createFavoriteSchool, sanitizeComparedSchools, toggleComparedSchool, toggleFavorite, updateFavorite } from './decisionState';
import type { School } from './types';

const school: School = {
  id: 9,
  name: '西安电子科技大学',
  province: '陕西',
  city: '西安',
  schoolLevel: '211/双一流',
  is985: false,
  is211: true,
  isDoubleFirstClass: true,
  primarySubject: '408 计算机学科专业基础',
  is408: true,
  latestQuota: 70,
  latestScoreLine: null
};

describe('comparison state', () => {
  it('toggles a school without mutating the existing selection', () => {
    const current = [6];
    expect(toggleComparedSchool(current, 9)).toEqual([6, 9]);
    expect(current).toEqual([6]);
    expect(toggleComparedSchool([6, 9], 6)).toEqual([9]);
  });

  it('keeps additions idempotent and enforces the four-school limit', () => {
    expect(addComparedSchool([6, 9], 9)).toEqual([6, 9]);
    expect(addComparedSchool([1, 2, 3, 4], 5)).toEqual([1, 2, 3, 4]);
  });

  it('removes invalid ids when the school dataset changes', () => {
    expect(sanitizeComparedSchools([1, 6, 9], new Set([6, 9]))).toEqual([6, 9]);
  });
});

describe('favorite state', () => {
  it('creates a traceable favorite snapshot', () => {
    expect(createFavoriteSchool(school, '2026-07-13T10:00:00.000Z')).toMatchObject({
      schoolId: 9,
      groupTag: '稳妥',
      latestQuota: 70,
      savedAt: '2026-07-13T10:00:00.000Z'
    });
  });

  it('toggles favorites and preserves unrelated entries', () => {
    const first = createFavoriteSchool({ ...school, id: 6, name: '华中科技大学' }, 'first');
    const added = toggleFavorite([first], school, 'second');
    expect(added.map((item) => item.schoolId)).toEqual([9, 6]);
    expect(toggleFavorite(added, school)).toEqual([first]);
  });

  it('updates only the selected favorite', () => {
    const favorite = createFavoriteSchool(school, 'saved');
    expect(updateFavorite([favorite], 9, { groupTag: '冲刺', note: '重点核验复试线' })[0]).toMatchObject({
      groupTag: '冲刺',
      note: '重点核验复试线'
    });
  });

  it('caps personal notes at 300 characters', () => {
    const favorite = createFavoriteSchool(school, 'saved');
    expect(updateFavorite([favorite], 9, { note: 'a'.repeat(320) })[0].note).toHaveLength(300);
  });
});
