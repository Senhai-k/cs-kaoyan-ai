import { describe, expect, it } from 'vitest';
import { activeSchoolFilterLabels, applySchoolFilterPreset, DEFAULT_SCHOOL_FILTERS, resetSchoolFilters } from './schoolFilters';

describe('school filters', () => {
  it('returns a fresh empty filter state', () => {
    const state = resetSchoolFilters();
    expect(state).toEqual(DEFAULT_SCHOOL_FILTERS);
    expect(state).not.toBe(DEFAULT_SCHOOL_FILTERS);
  });

  it('applies a preset without retaining old values', () => {
    expect(applySchoolFilterPreset({ is408: true, minScore: '320' })).toEqual({
      ...DEFAULT_SCHOOL_FILTERS,
      is408: true,
      minScore: '320'
    });
  });

  it('trims text for labels but preserves input state', () => {
    const labels = activeSchoolFilterLabels({ ...DEFAULT_SCHOOL_FILTERS, keyword: '  西电  ', professionalKeyword: ' 408 ' });
    expect(labels).toEqual([
      { key: 'keyword', label: '关键词：西电' },
      { key: 'professionalKeyword', label: '专业课：408' }
    ]);
  });

  it('describes 408 and numeric bounds consistently', () => {
    const labels = activeSchoolFilterLabels({ ...DEFAULT_SCHOOL_FILTERS, is408: false, minQuota: '60', maxScore: '360' });
    expect(labels.map((item) => item.label)).toEqual(['自命题', '招生 >= 60', '复试线 <= 360']);
  });

  it('describes the selected province explicitly', () => {
    expect(activeSchoolFilterLabels({ ...DEFAULT_SCHOOL_FILTERS, province: '江苏' }))
      .toContainEqual({ key: 'province', label: '省份：江苏' });
  });
});
