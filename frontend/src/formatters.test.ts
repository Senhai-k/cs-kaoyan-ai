import { describe, expect, it } from 'vitest';
import { formatAdmissionPlan, formatDateTime, formatExamType, formatRegionLabel, recommendationConfidence, recommendationConfidencePercent, riskLabel } from './formatters';
import type { RecommendationItem, School } from './types';

const school: School = {
  id: 1,
  name: '测试院校',
  province: '北京',
  city: '北京',
  schoolLevel: '普通院校',
  is985: false,
  is211: false,
  isDoubleFirstClass: false,
  primarySubject: null,
  is408: null,
  latestQuota: null,
  latestScoreLine: null
};

function recommendation(patch: Partial<RecommendationItem> = {}): RecommendationItem {
  return {
    school,
    matchScore: 30,
    groupTag: '待核验',
    riskLevel: 'UNKNOWN',
    scoreGap: null,
    benchmarkScore: null,
    officialSourceCount: 0,
    reasons: [],
    ...patch
  };
}

describe('display formatters', () => {
  it('keeps unknown exam types explicit', () => {
    expect(formatExamType(true)).toBe('408');
    expect(formatExamType(false)).toBe('自命题');
    expect(formatExamType(null)).toBe('待核验');
  });

  it('formats API timestamps without changing missing values', () => {
    expect(formatDateTime('2026-07-13T09:30:45.000Z')).toBe('2026-07-13 09:30:45');
    expect(formatDateTime()).toBe('-');
  });

  it('does not render null as part of a region label', () => {
    expect(formatRegionLabel('北京', null)).toBe('北京');
    expect(formatRegionLabel(null, null)).toBe('地区待核验');
  });

  it('does not report high confidence when evidence is absent', () => {
    const empty = recommendation();
    const complete = recommendation({
      school: { ...school, primarySubject: '408', is408: true, latestQuota: 70 },
      benchmarkScore: 340,
      officialSourceCount: 2
    });

    expect(recommendationConfidencePercent(empty)).toBe(0);
    expect(recommendationConfidence(empty)).toBe('低');
    expect(recommendationConfidencePercent(complete)).toBe(100);
    expect(recommendationConfidence(complete)).toBe('高');
    expect(riskLabel('UNKNOWN')).toBe('待核验');
  });

  it('keeps annual and unified-exam plan scopes separate', () => {
    expect(formatAdmissionPlan({ year: 2026, totalQuota: null, recommendedQuota: null, unifiedQuota: 9, hasAdjustment: null, sourceId: 1, remark: '' }))
      .toBe('统考/复试阶段 9');
    expect(formatAdmissionPlan({ year: 2026, totalQuota: 66, recommendedQuota: null, unifiedQuota: null, hasAdjustment: null, sourceId: 1, remark: '' }))
      .toBe('总计划 66');
  });
});
