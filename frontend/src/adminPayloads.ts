import type { AdmissionPlanForm, AdmissionResultForm, AdjustmentInfoForm, ExamSubjectForm, ReferenceBookForm, RetestRuleForm, ScoreLineForm } from './adminForms';
import type { Major } from './types';

export function optionalNumber(value: string) {
  return value.trim() === '' ? null : Number(value);
}

export function hasEvidenceSource(form: { sourceId: string }) {
  return Number(form.sourceId) > 0;
}

export function validYear(value: string) {
  const year = Number(value);
  return Number.isInteger(year) && year >= 2000 && year <= 2100;
}

export function validOptionalNumber(value: string, min: number, max: number) {
  if (!value.trim()) return true;
  const number = Number(value);
  return Number.isFinite(number) && number >= min && number <= max;
}

export function validRetestWeights(initial: string, retest: string) {
  if (!initial.trim() || !retest.trim()) return true;
  return validOptionalNumber(initial, 0, 100) && validOptionalNumber(retest, 0, 100)
    && Number(initial) + Number(retest) === 100;
}

const base = (major: Major, year: string, sourceId: string) => ({
  schoolId: major.schoolId,
  collegeId: major.collegeId,
  majorId: major.id,
  year: Number(year),
  sourceId: optionalNumber(sourceId)
});

export const admissionPlanPayload = (major: Major, form: AdmissionPlanForm) => ({ ...base(major, form.year, form.sourceId), totalQuota: optionalNumber(form.totalQuota), recommendedQuota: optionalNumber(form.recommendedQuota), unifiedQuota: optionalNumber(form.unifiedQuota), hasAdjustment: false, remark: form.remark });
export const examSubjectPayload = (major: Major, form: ExamSubjectForm) => ({ ...base(major, form.year, form.sourceId), politics: '101 思想政治理论', foreignLanguage: '201 英语一', mathSubject: '301 数学一', professionalSubject: form.professionalSubject.trim(), is408: form.is408 });
export const scoreLinePayload = (major: Major, form: ScoreLineForm) => ({ ...base(major, form.year, form.sourceId), totalScore: optionalNumber(form.totalScore), politicsScore: null, foreignLanguageScore: null, mathScore: null, professionalScore: null, remark: form.remark });
export const admissionResultPayload = (major: Major, form: AdmissionResultForm) => ({ ...base(major, form.year, form.sourceId), admittedCount: optionalNumber(form.admittedCount), lowestScore: optionalNumber(form.lowestScore), averageScore: optionalNumber(form.averageScore), highestScore: optionalNumber(form.highestScore), retestRatio: optionalNumber(form.retestRatio), remark: form.remark });
export const retestRulePayload = (major: Major | null, form: RetestRuleForm) => ({
  schoolId: Number(form.schoolId) || major?.schoolId,
  collegeId: form.scopeType === 'MAJOR' ? major?.collegeId : null,
  majorId: form.scopeType === 'MAJOR' ? major?.id : null,
  year: Number(form.year),
  sourceId: optionalNumber(form.sourceId),
  retestTime: form.retestTime,
  retestMethod: form.retestMethod,
  retestRatio: optionalNumber(form.retestRatio),
  initialScoreWeight: optionalNumber(form.initialScoreWeight),
  retestScoreWeight: optionalNumber(form.retestScoreWeight),
  qualificationLine: form.qualificationLine,
  materials: form.materials,
  remark: form.remark
});
export const referenceBookPayload = (major: Major, form: ReferenceBookForm) => ({ ...base(major, form.year, form.sourceId), subjectName: form.subjectName, bookTitle: form.bookTitle.trim(), author: form.author, edition: form.edition, publisher: form.publisher, remark: form.remark });
export const adjustmentInfoPayload = (major: Major, form: AdjustmentInfoForm) => ({ ...base(major, form.year, form.sourceId), title: form.title.trim(), open: form.open, vacancyCount: optionalNumber(form.vacancyCount), applicationWindow: form.applicationWindow, requirements: form.requirements, noticeUrl: form.noticeUrl, remark: form.remark });
