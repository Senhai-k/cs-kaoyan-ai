import { useState } from 'react';

export const ADMIN_FORM_DEFAULTS = {
  school: { name: '', province: '', city: '', region: '', schoolLevel: '普通院校' },
  college: { schoolId: '', name: '', officialSite: '' },
  major: { schoolId: '', collegeId: '', name: '', majorCode: '', degreeType: '专硕', researchDirection: '', studyMode: '全日制' },
  source: { schoolId: '', collegeId: '', title: '', sourceType: '招生专业目录', sourceUrl: '', publishDate: '', year: '2026', official: true, auditStatus: 'PUBLISHED', remark: '' },
  sourceDocument: { schoolId: '', collegeId: '', majorId: '', title: '', documentType: '招生专业目录', sourceUrl: '', year: '2026', auditStatus: 'DRAFT', sourceReliability: 'UNKNOWN', rawText: '', remark: '' },
  plan: { majorId: '', sourceId: '', year: '2026', totalQuota: '', recommendedQuota: '', unifiedQuota: '', remark: '' },
  exam: { majorId: '', sourceId: '', year: '2026', professionalSubject: '', is408: false },
  score: { majorId: '', sourceId: '', year: '2026', totalScore: '', remark: '' },
  admissionResult: { majorId: '', sourceId: '', year: '2026', admittedCount: '', lowestScore: '', averageScore: '', highestScore: '', retestRatio: '', remark: '' },
  retestRule: { scopeType: 'SCHOOL', schoolId: '', majorId: '', sourceId: '', year: '2026', retestTime: '', retestMethod: '', retestRatio: '', initialScoreWeight: '60', retestScoreWeight: '40', qualificationLine: '', materials: '', remark: '' },
  referenceBook: { majorId: '', sourceId: '', year: '2026', subjectName: '', bookTitle: '', author: '', edition: '', publisher: '', remark: '' },
  adjustmentInfo: { majorId: '', sourceId: '', year: '2026', title: '', open: true, vacancyCount: '', applicationWindow: '', requirements: '', noticeUrl: '', remark: '' }
};

export type SourceDocumentForm = typeof ADMIN_FORM_DEFAULTS.sourceDocument;
export type AdmissionPlanForm = typeof ADMIN_FORM_DEFAULTS.plan;
export type ExamSubjectForm = typeof ADMIN_FORM_DEFAULTS.exam;
export type ScoreLineForm = typeof ADMIN_FORM_DEFAULTS.score;
export type AdmissionResultForm = typeof ADMIN_FORM_DEFAULTS.admissionResult;
export type RetestRuleForm = typeof ADMIN_FORM_DEFAULTS.retestRule;
export type ReferenceBookForm = typeof ADMIN_FORM_DEFAULTS.referenceBook;
export type AdjustmentInfoForm = typeof ADMIN_FORM_DEFAULTS.adjustmentInfo;

export function createAdminFormDefaults() {
  return Object.fromEntries(Object.entries(ADMIN_FORM_DEFAULTS).map(([key, value]) => [key, { ...value }]));
}

export function useAdminForms() {
  const defaults = createAdminFormDefaults() as { [K in keyof typeof ADMIN_FORM_DEFAULTS]: { -readonly [P in keyof typeof ADMIN_FORM_DEFAULTS[K]]: typeof ADMIN_FORM_DEFAULTS[K][P] } };
  const [newSchool, setNewSchool] = useState(defaults.school);
  const [newCollege, setNewCollege] = useState(defaults.college);
  const [newMajor, setNewMajor] = useState(defaults.major);
  const [newSource, setNewSource] = useState(defaults.source);
  const [newSourceDocument, setNewSourceDocument] = useState(defaults.sourceDocument);
  const [newPlan, setNewPlan] = useState(defaults.plan);
  const [newExam, setNewExam] = useState(defaults.exam);
  const [newScore, setNewScore] = useState(defaults.score);
  const [newAdmissionResult, setNewAdmissionResult] = useState(defaults.admissionResult);
  const [newRetestRule, setNewRetestRule] = useState(defaults.retestRule);
  const [newReferenceBook, setNewReferenceBook] = useState(defaults.referenceBook);
  const [newAdjustmentInfo, setNewAdjustmentInfo] = useState(defaults.adjustmentInfo);
  const [editingSchoolId, setEditingSchoolId] = useState<number | null>(null);
  const [editingCollegeId, setEditingCollegeId] = useState<number | null>(null);
  const [editingMajorId, setEditingMajorId] = useState<number | null>(null);
  const [editingSourceId, setEditingSourceId] = useState<number | null>(null);
  const [editingSourceDocumentId, setEditingSourceDocumentId] = useState<number | null>(null);
  const [editingPlanId, setEditingPlanId] = useState<number | null>(null);
  const [editingExamId, setEditingExamId] = useState<number | null>(null);
  const [editingScoreId, setEditingScoreId] = useState<number | null>(null);
  const [editingAdmissionResultId, setEditingAdmissionResultId] = useState<number | null>(null);
  const [editingRetestRuleId, setEditingRetestRuleId] = useState<number | null>(null);
  const [editingReferenceBookId, setEditingReferenceBookId] = useState<number | null>(null);
  const [editingAdjustmentInfoId, setEditingAdjustmentInfoId] = useState<number | null>(null);

  const resetAdminForms = () => {
    const next = createAdminFormDefaults() as typeof defaults;
    setNewSchool(next.school); setNewCollege(next.college); setNewMajor(next.major); setNewSource(next.source);
    setNewSourceDocument(next.sourceDocument); setNewPlan(next.plan); setNewExam(next.exam); setNewScore(next.score);
    setNewAdmissionResult(next.admissionResult); setNewRetestRule(next.retestRule); setNewReferenceBook(next.referenceBook); setNewAdjustmentInfo(next.adjustmentInfo);
    setEditingSchoolId(null); setEditingCollegeId(null); setEditingMajorId(null); setEditingSourceId(null); setEditingSourceDocumentId(null); setEditingPlanId(null);
    setEditingExamId(null); setEditingScoreId(null); setEditingAdmissionResultId(null); setEditingRetestRuleId(null); setEditingReferenceBookId(null); setEditingAdjustmentInfoId(null);
  };

  return {
    newSchool, setNewSchool, newCollege, setNewCollege, newMajor, setNewMajor, newSource, setNewSource,
    newSourceDocument, setNewSourceDocument, newPlan, setNewPlan, newExam, setNewExam, newScore, setNewScore,
    newAdmissionResult, setNewAdmissionResult, newRetestRule, setNewRetestRule, newReferenceBook, setNewReferenceBook,
    newAdjustmentInfo, setNewAdjustmentInfo, editingSchoolId, setEditingSchoolId, editingCollegeId, setEditingCollegeId,
    editingMajorId, setEditingMajorId, editingSourceId, setEditingSourceId, editingSourceDocumentId, setEditingSourceDocumentId,
    editingPlanId, setEditingPlanId, editingExamId, setEditingExamId, editingScoreId, setEditingScoreId,
    editingAdmissionResultId, setEditingAdmissionResultId, editingRetestRuleId, setEditingRetestRuleId,
    editingReferenceBookId, setEditingReferenceBookId, editingAdjustmentInfoId, setEditingAdjustmentInfoId, resetAdminForms
  };
}
