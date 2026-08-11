import { formatDateTime, formatExamType } from './formatters';
import { formatTrendForComparison } from './decisionTrends';
import type { CompareSchoolItem } from './types';

export type ComparisonRow = { label: string; values: Array<string | number> };

export function buildComparisonRows(schools: CompareSchoolItem[], onlyDifferences: boolean): ComparisonRow[] {
  const dimensions: Array<{ label: string; value: (school: CompareSchoolItem) => string | number }> = [
    { label: '地区', value: (school) => school.regionLabel || '-' },
    { label: '学校层次', value: (school) => school.schoolLevel || '-' },
    { label: '学院', value: (school) => school.collegeName || '-' },
    { label: '专业', value: (school) => school.majorName || '-' },
    { label: '专业类型', value: (school) => school.degreeType || '-' },
    { label: '初试专业课', value: (school) => school.primarySubject || '-' },
    { label: '是否 408', value: (school) => formatExamType(school.is408) },
    { label: '最近目录总计划', value: (school) => school.latestQuota ?? '-' },
    { label: '招生计划趋势', value: (school) => formatTrendForComparison(school.quotaHistory, '招生计划') },
    { label: '最近一年复试线', value: (school) => school.latestScoreLine ?? '-' },
    { label: '复试线趋势', value: (school) => formatTrendForComparison(school.scoreLineHistory, '复试线') },
    { label: '官方来源数量', value: (school) => school.officialSourceCount },
    { label: '最近来源更新', value: (school) => formatDateTime(school.latestSourceUpdatedAt ?? undefined) }
  ];
  const rows = dimensions.map((dimension) => ({
    label: dimension.label,
    values: schools.map(dimension.value)
  }));
  return onlyDifferences
    ? rows.filter((row) => new Set(row.values.map(String)).size > 1)
    : rows;
}
