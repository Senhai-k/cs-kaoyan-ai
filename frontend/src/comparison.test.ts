import { describe, expect, it } from 'vitest';
import { buildComparisonRows } from './comparison';
import type { CompareSchoolItem } from './types';

const base: CompareSchoolItem = {
  id: 6,
  name: '华中科技大学',
  regionLabel: '湖北 武汉',
  schoolLevel: '985/211/双一流',
  collegeName: '计算机科学与技术学院',
  majorName: '计算机科学与技术',
  degreeType: '学硕',
  primarySubject: null,
  is408: null,
  latestQuota: null,
  latestScoreLine: null,
  quotaHistory: [],
  scoreLineHistory: [],
  officialSourceCount: 2,
  latestSourceUpdatedAt: '2026-07-10T14:54:16'
};

describe('comparison rows', () => {
  it('keeps all supported dimensions by default', () => {
    expect(buildComparisonRows([base], false)).toHaveLength(13);
  });

  it('keeps only dimensions that differ across schools', () => {
    const xidian: CompareSchoolItem = {
      ...base,
      id: 9,
      name: '西安电子科技大学',
      regionLabel: '陕西 西安',
      schoolLevel: '211/双一流',
      primarySubject: '408 计算机学科专业基础',
      is408: true,
      latestQuota: 70,
      quotaHistory: [{ year: 2025, value: 60, sourceId: 1 }, { year: 2026, value: 70, sourceId: 2 }]
    };
    const labels = buildComparisonRows([base, xidian], true).map((row) => row.label);

    expect(labels).toEqual(['地区', '学校层次', '初试专业课', '是否 408', '最近目录总计划', '招生计划趋势']);
    expect(labels).not.toContain('学院');
    expect(labels).not.toContain('官方来源数量');
  });

  it('returns no rows when selected schools have identical data', () => {
    expect(buildComparisonRows([base, { ...base, id: 7, name: '另一所院校' }], true)).toEqual([]);
  });
});
