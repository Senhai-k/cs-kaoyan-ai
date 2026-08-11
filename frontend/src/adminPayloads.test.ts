import { describe, expect, it } from 'vitest';
import { admissionResultPayload, adjustmentInfoPayload, hasEvidenceSource, optionalNumber, retestRulePayload, validOptionalNumber, validRetestWeights, validYear } from './adminPayloads';
import { ADMIN_FORM_DEFAULTS } from './adminForms';
import type { Major } from './types';

const major: Major = { id: 8, schoolId: 8, collegeId: 8, name: '计算机科学与技术', majorCode: '081200', degreeType: '学硕', researchDirection: '', studyMode: '全日制', remark: '' };

describe('admin payloads', () => {
  it('keeps missing numeric evidence as null instead of zero', () => {
    expect(optionalNumber('')).toBeNull();
    expect(optionalNumber('  ')).toBeNull();
    expect(optionalNumber('0')).toBe(0);
    expect(admissionResultPayload(major, { ...ADMIN_FORM_DEFAULTS.admissionResult }).lowestScore).toBeNull();
    expect(adjustmentInfoPayload(major, { ...ADMIN_FORM_DEFAULTS.adjustmentInfo }).vacancyCount).toBeNull();
  });

  it('preserves valid values and official source links', () => {
    const payload = retestRulePayload(major, { ...ADMIN_FORM_DEFAULTS.retestRule, scopeType: 'MAJOR', sourceId: '13', retestRatio: '1.2' });
    expect(payload.sourceId).toBe(13);
    expect(payload.retestRatio).toBe(1.2);
    expect(payload.initialScoreWeight).toBe(60);
    expect(payload.majorId).toBe(8);
  });

  it('keeps school rules unbound from colleges and majors', () => {
    const payload = retestRulePayload(null, { ...ADMIN_FORM_DEFAULTS.retestRule, schoolId: '8', sourceId: '13' });
    expect(payload.schoolId).toBe(8);
    expect(payload.collegeId).toBeNull();
    expect(payload.majorId).toBeNull();
  });

  it('requires an evidence source for structured facts', () => {
    expect(hasEvidenceSource({ sourceId: '' })).toBe(false);
    expect(hasEvidenceSource({ sourceId: '14' })).toBe(true);
  });

  it('validates years, scores and retest weights', () => {
    expect(validYear('2026')).toBe(true);
    expect(validYear('1999')).toBe(false);
    expect(validOptionalNumber('', 0, 500)).toBe(true);
    expect(validOptionalNumber('501', 0, 500)).toBe(false);
    expect(validRetestWeights('60', '40')).toBe(true);
    expect(validRetestWeights('60', '30')).toBe(false);
  });
});
