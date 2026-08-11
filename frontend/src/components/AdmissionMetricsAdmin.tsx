import { Plus } from 'lucide-react';
import type { AdmissionPlanForm, AdmissionResultForm, ExamSubjectForm, ScoreLineForm } from '../adminForms';
import { hasEvidenceSource, validOptionalNumber, validYear } from '../adminPayloads';
import type { AdmissionPlan, AdmissionResult, DocumentSource, ExamSubject, Major, ScoreLine } from '../types';
import { AdminBlock, MajorSelect, SourceSelect } from './AdminComponents';

type FormSetter<T> = (next: T) => void;

export function AdmissionMetricsAdmin({ majors, sources, plans, exams, scores, results, plan, exam, score, result, editingPlanId, editingExamId, editingScoreId, editingResultId, setPlan, setExam, setScore, setResult, onCreatePlan, onCreateExam, onCreateScore, onCreateResult, onEditPlan, onEditExam, onEditScore, onEditResult, onDelete, onCancel }: {
  majors: Major[]; sources: DocumentSource[]; plans: AdmissionPlan[]; exams: ExamSubject[]; scores: ScoreLine[]; results: AdmissionResult[];
  plan: AdmissionPlanForm; exam: ExamSubjectForm; score: ScoreLineForm; result: AdmissionResultForm;
  editingPlanId: number | null; editingExamId: number | null; editingScoreId: number | null; editingResultId: number | null;
  setPlan: FormSetter<AdmissionPlanForm>; setExam: FormSetter<ExamSubjectForm>; setScore: FormSetter<ScoreLineForm>; setResult: FormSetter<AdmissionResultForm>;
  onCreatePlan: () => void; onCreateExam: () => void; onCreateScore: () => void; onCreateResult: () => void;
  onEditPlan: (item: AdmissionPlan) => void; onEditExam: (item: ExamSubject) => void; onEditScore: (item: ScoreLine) => void; onEditResult: (item: AdmissionResult) => void;
  onDelete: (path: string, id: number) => void; onCancel: () => void;
}) {
  const schoolIdFor = (majorId: string) => majors.find((major) => major.id === Number(majorId))?.schoolId;
  const validPlan = validYear(plan.year) && [plan.totalQuota, plan.recommendedQuota, plan.unifiedQuota].every((value) => validOptionalNumber(value, 0, 100000));
  const validExam = validYear(exam.year);
  const validScore = validYear(score.year) && validOptionalNumber(score.totalScore, 0, 500);
  const validResult = validYear(result.year) && validOptionalNumber(result.admittedCount, 0, 100000)
    && [result.lowestScore, result.averageScore, result.highestScore].every((value) => validOptionalNumber(value, 0, 500))
    && validOptionalNumber(result.retestRatio, 0, 10);
  return <>
    <AdminBlock id="admin-admissions" title="招生计划" items={plans.map((item) => ({ id: item.id, label: `${item.year} 招生 ${item.totalQuota ?? '-'}`, onEdit: () => onEditPlan(item) }))} deletePath="admission-plans" onDelete={onDelete} editing={Boolean(editingPlanId)} onCancelEditor={onCancel}>
      <MajorSelect majors={majors} value={plan.majorId} onChange={(majorId) => setPlan({ ...plan, majorId, sourceId: '' })} />
      <SourceSelect sources={sources} schoolId={schoolIdFor(plan.majorId)} value={plan.sourceId} onChange={(sourceId) => setPlan({ ...plan, sourceId })} />
      <input type="number" min="2000" max="2100" placeholder="年份" value={plan.year} onChange={(event) => setPlan({ ...plan, year: event.target.value })} />
      <input type="number" min="0" placeholder="总招生人数" value={plan.totalQuota} onChange={(event) => setPlan({ ...plan, totalQuota: event.target.value })} />
      <input type="number" min="0" placeholder="推免人数" value={plan.recommendedQuota} onChange={(event) => setPlan({ ...plan, recommendedQuota: event.target.value })} />
      <input type="number" min="0" placeholder="统考人数" value={plan.unifiedQuota} onChange={(event) => setPlan({ ...plan, unifiedQuota: event.target.value })} />
      <input placeholder="计划口径备注" value={plan.remark} onChange={(event) => setPlan({ ...plan, remark: event.target.value })} />
      <button type="button" disabled={!plan.majorId || !hasEvidenceSource(plan) || !validPlan} onClick={onCreatePlan}><Plus size={16} />{editingPlanId ? '保存修改' : '新增招生计划'}</button>
      {editingPlanId && <button type="button" className="secondary-button" onClick={onCancel}>取消编辑</button>}
    </AdminBlock>
    <AdminBlock title="考试科目" items={exams.map((item) => ({ id: item.id, label: `${item.year} ${item.professionalSubject}`, onEdit: () => onEditExam(item) }))} deletePath="exam-subjects" onDelete={onDelete} editing={Boolean(editingExamId)} onCancelEditor={onCancel}>
      <MajorSelect majors={majors} value={exam.majorId} onChange={(majorId) => setExam({ ...exam, majorId, sourceId: '' })} />
      <SourceSelect sources={sources} schoolId={schoolIdFor(exam.majorId)} value={exam.sourceId} onChange={(sourceId) => setExam({ ...exam, sourceId })} />
      <input type="number" min="2000" max="2100" placeholder="年份" value={exam.year} onChange={(event) => setExam({ ...exam, year: event.target.value })} />
      <input placeholder="专业课科目" value={exam.professionalSubject} onChange={(event) => setExam({ ...exam, professionalSubject: event.target.value })} />
      <label className="check-line"><input type="checkbox" checked={exam.is408} onChange={(event) => setExam({ ...exam, is408: event.target.checked })} />408</label>
      <button type="button" disabled={!exam.majorId || !exam.professionalSubject.trim() || !hasEvidenceSource(exam) || !validExam} onClick={onCreateExam}><Plus size={16} />{editingExamId ? '保存修改' : '新增考试科目'}</button>
      {editingExamId && <button type="button" className="secondary-button" onClick={onCancel}>取消编辑</button>}
    </AdminBlock>
    <AdminBlock title="复试线" items={scores.map((item) => ({ id: item.id, label: `${item.year} 复试线 ${item.totalScore ?? '-'}`, onEdit: () => onEditScore(item) }))} deletePath="score-lines" onDelete={onDelete} editing={Boolean(editingScoreId)} onCancelEditor={onCancel}>
      <MajorSelect majors={majors} value={score.majorId} onChange={(majorId) => setScore({ ...score, majorId, sourceId: '' })} />
      <SourceSelect sources={sources} schoolId={schoolIdFor(score.majorId)} value={score.sourceId} onChange={(sourceId) => setScore({ ...score, sourceId })} />
      <input type="number" min="2000" max="2100" placeholder="年份" value={score.year} onChange={(event) => setScore({ ...score, year: event.target.value })} />
      <input type="number" min="0" max="500" placeholder="总分线" value={score.totalScore} onChange={(event) => setScore({ ...score, totalScore: event.target.value })} />
      <input placeholder="分数线口径备注" value={score.remark} onChange={(event) => setScore({ ...score, remark: event.target.value })} />
      <button type="button" disabled={!score.majorId || !score.totalScore || !hasEvidenceSource(score) || !validScore} onClick={onCreateScore}><Plus size={16} />{editingScoreId ? '保存修改' : '新增复试线'}</button>
      {editingScoreId && <button type="button" className="secondary-button" onClick={onCancel}>取消编辑</button>}
    </AdminBlock>
    <AdminBlock title="录取结果" items={results.map((item) => ({ id: item.id, label: `${item.year} 最低分 ${item.lowestScore ?? '-'} / 平均分 ${item.averageScore ?? '-'}`, onEdit: () => onEditResult(item) }))} deletePath="admission-results" onDelete={onDelete} editing={Boolean(editingResultId)} onCancelEditor={onCancel}>
      <MajorSelect majors={majors} value={result.majorId} onChange={(majorId) => setResult({ ...result, majorId, sourceId: '' })} />
      <SourceSelect sources={sources} schoolId={schoolIdFor(result.majorId)} value={result.sourceId} onChange={(sourceId) => setResult({ ...result, sourceId })} />
      <input type="number" min="2000" max="2100" placeholder="年份" value={result.year} onChange={(event) => setResult({ ...result, year: event.target.value })} />
      <input type="number" min="0" placeholder="录取人数" value={result.admittedCount} onChange={(event) => setResult({ ...result, admittedCount: event.target.value })} />
      <input type="number" min="0" max="500" placeholder="最低分" value={result.lowestScore} onChange={(event) => setResult({ ...result, lowestScore: event.target.value })} />
      <input type="number" min="0" max="500" step="0.01" placeholder="平均分" value={result.averageScore} onChange={(event) => setResult({ ...result, averageScore: event.target.value })} />
      <input type="number" min="0" max="500" placeholder="最高分" value={result.highestScore} onChange={(event) => setResult({ ...result, highestScore: event.target.value })} />
      <input type="number" min="0" max="10" step="0.01" placeholder="复试比例，如 1.25" value={result.retestRatio} onChange={(event) => setResult({ ...result, retestRatio: event.target.value })} />
      <input placeholder="录取结果备注" value={result.remark} onChange={(event) => setResult({ ...result, remark: event.target.value })} />
      <button type="button" disabled={!result.majorId || !hasEvidenceSource(result) || !validResult} onClick={onCreateResult}><Plus size={16} />{editingResultId ? '保存修改' : '新增录取结果'}</button>
      {editingResultId && <button type="button" className="secondary-button" onClick={onCancel}>取消编辑</button>}
    </AdminBlock>
  </>;
}
