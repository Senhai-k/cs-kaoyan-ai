import { Plus } from 'lucide-react';
import type { AdjustmentInfoForm, ReferenceBookForm, RetestRuleForm } from '../adminForms';
import { hasEvidenceSource, validOptionalNumber, validRetestWeights, validYear } from '../adminPayloads';
import type { AdjustmentInfo, DocumentSource, Major, ReferenceBook, RetestRule, School } from '../types';
import { AdminBlock, MajorSelect, SchoolSelect, SourceSelect } from './AdminComponents';

type Setter<T> = (next: T) => void;

export function RetestEvidenceAdmin({ schools, majors, sources, rules, books, adjustments, rule, book, adjustment, editingRuleId, editingBookId, editingAdjustmentId, setRule, setBook, setAdjustment, onCreateRule, onCreateBook, onCreateAdjustment, onEditRule, onEditBook, onEditAdjustment, onDelete, onCancel }: {
  schools: School[]; majors: Major[]; sources: DocumentSource[]; rules: RetestRule[]; books: ReferenceBook[]; adjustments: AdjustmentInfo[];
  rule: RetestRuleForm; book: ReferenceBookForm; adjustment: AdjustmentInfoForm;
  editingRuleId: number | null; editingBookId: number | null; editingAdjustmentId: number | null;
  setRule: Setter<RetestRuleForm>; setBook: Setter<ReferenceBookForm>; setAdjustment: Setter<AdjustmentInfoForm>;
  onCreateRule: () => void; onCreateBook: () => void; onCreateAdjustment: () => void;
  onEditRule: (item: RetestRule) => void; onEditBook: (item: ReferenceBook) => void; onEditAdjustment: (item: AdjustmentInfo) => void;
  onDelete: (path: string, id: number) => void; onCancel: () => void;
}) {
  const schoolIdFor = (majorId: string) => majors.find((major) => major.id === Number(majorId))?.schoolId;
  const ruleSchoolId = Number(rule.schoolId) || schoolIdFor(rule.majorId);
  const scopeLabel = (scope: RetestRule['scopeType']) => scope === 'SCHOOL' ? '学校规则' : scope === 'COLLEGE' ? '学院规则' : '专业规则';
  const validRule = validYear(rule.year) && validOptionalNumber(rule.retestRatio, 0, 10) && validRetestWeights(rule.initialScoreWeight, rule.retestScoreWeight);
  const validBook = validYear(book.year);
  const validAdjustment = validYear(adjustment.year) && validOptionalNumber(adjustment.vacancyCount, 0, 100000);
  return <>
    <AdminBlock id="admin-retest" title="复试细则" items={rules.map((item) => ({ id: item.id, label: `${item.year} ${scopeLabel(item.scopeType)} / ${item.retestMethod || '复试规则'} / 比例 ${item.retestRatio ?? '-'}`, onEdit: () => onEditRule(item) }))} deletePath="retest-rules" onDelete={onDelete} editing={Boolean(editingRuleId)} onCancelEditor={onCancel}>
      <select aria-label="复试规则作用域" value={rule.scopeType} onChange={(event) => setRule({ ...rule, scopeType: event.target.value, majorId: '', sourceId: '' })}>
        <option value="SCHOOL">学校级通用规则</option><option value="MAJOR">专业级规则</option>
      </select>
      <SchoolSelect schools={schools} value={rule.schoolId} onChange={(schoolId) => setRule({ ...rule, schoolId, majorId: '', sourceId: '' })} />
      {rule.scopeType === 'MAJOR' && <MajorSelect majors={majors.filter((major) => !ruleSchoolId || major.schoolId === ruleSchoolId)} value={rule.majorId} onChange={(majorId) => setRule({ ...rule, majorId, sourceId: '' })} />}
      <SourceSelect sources={sources} schoolId={ruleSchoolId} value={rule.sourceId} onChange={(sourceId) => setRule({ ...rule, sourceId })} />
      <input type="number" min="2000" max="2100" placeholder="年份" value={rule.year} onChange={(event) => setRule({ ...rule, year: event.target.value })} />
      <input placeholder="复试时间，如 2026-03-22 至 2026-03-24" value={rule.retestTime} onChange={(event) => setRule({ ...rule, retestTime: event.target.value })} />
      <input placeholder="复试方式，如 机试 + 面试" value={rule.retestMethod} onChange={(event) => setRule({ ...rule, retestMethod: event.target.value })} />
      <input type="number" min="0" max="10" step="0.01" placeholder="差额比例，如 1.25" value={rule.retestRatio} onChange={(event) => setRule({ ...rule, retestRatio: event.target.value })} />
      <input type="number" min="0" max="100" placeholder="初试权重，如 60" value={rule.initialScoreWeight} onChange={(event) => setRule({ ...rule, initialScoreWeight: event.target.value })} />
      <input type="number" min="0" max="100" placeholder="复试权重，如 40" value={rule.retestScoreWeight} onChange={(event) => setRule({ ...rule, retestScoreWeight: event.target.value })} />
      <textarea placeholder="合格线或淘汰规则" value={rule.qualificationLine} onChange={(event) => setRule({ ...rule, qualificationLine: event.target.value })} />
      <textarea placeholder="材料要求" value={rule.materials} onChange={(event) => setRule({ ...rule, materials: event.target.value })} />
      <input placeholder="备注" value={rule.remark} onChange={(event) => setRule({ ...rule, remark: event.target.value })} />
      <button type="button" disabled={!ruleSchoolId || (rule.scopeType === 'MAJOR' && !rule.majorId) || !hasEvidenceSource(rule) || !validRule} onClick={onCreateRule}><Plus size={16} />{editingRuleId ? '保存修改' : '新增复试细则'}</button>
      {editingRuleId && <button type="button" className="secondary-button" onClick={onCancel}>取消编辑</button>}
    </AdminBlock>
    <AdminBlock title="参考书目" items={books.map((item) => ({ id: item.id, label: `${item.year} ${item.bookTitle} / ${item.author || '-'}`, onEdit: () => onEditBook(item) }))} deletePath="reference-books" onDelete={onDelete} editing={Boolean(editingBookId)} onCancelEditor={onCancel}>
      <MajorSelect majors={majors} value={book.majorId} onChange={(majorId) => setBook({ ...book, majorId, sourceId: '' })} />
      <SourceSelect sources={sources} schoolId={schoolIdFor(book.majorId)} value={book.sourceId} onChange={(sourceId) => setBook({ ...book, sourceId })} />
      <input type="number" min="2000" max="2100" placeholder="年份" value={book.year} onChange={(event) => setBook({ ...book, year: event.target.value })} />
      <input placeholder="适用科目" value={book.subjectName} onChange={(event) => setBook({ ...book, subjectName: event.target.value })} />
      <input placeholder="书名" value={book.bookTitle} onChange={(event) => setBook({ ...book, bookTitle: event.target.value })} />
      <input placeholder="作者" value={book.author} onChange={(event) => setBook({ ...book, author: event.target.value })} />
      <input placeholder="版本" value={book.edition} onChange={(event) => setBook({ ...book, edition: event.target.value })} />
      <input placeholder="出版社" value={book.publisher} onChange={(event) => setBook({ ...book, publisher: event.target.value })} />
      <input placeholder="备注" value={book.remark} onChange={(event) => setBook({ ...book, remark: event.target.value })} />
      <button type="button" disabled={!book.majorId || !book.bookTitle.trim() || !hasEvidenceSource(book) || !validBook} onClick={onCreateBook}><Plus size={16} />{editingBookId ? '保存修改' : '新增参考书目'}</button>
      {editingBookId && <button type="button" className="secondary-button" onClick={onCancel}>取消编辑</button>}
    </AdminBlock>
    <AdminBlock title="调剂信息" items={adjustments.map((item) => ({ id: item.id, label: `${item.year} ${item.title} / ${item.open ? '开放' : '关闭'}`, onEdit: () => onEditAdjustment(item) }))} deletePath="adjustment-infos" onDelete={onDelete} editing={Boolean(editingAdjustmentId)} onCancelEditor={onCancel}>
      <MajorSelect majors={majors} value={adjustment.majorId} onChange={(majorId) => setAdjustment({ ...adjustment, majorId, sourceId: '' })} />
      <SourceSelect sources={sources} schoolId={schoolIdFor(adjustment.majorId)} value={adjustment.sourceId} onChange={(sourceId) => setAdjustment({ ...adjustment, sourceId })} />
      <input type="number" min="2000" max="2100" placeholder="年份" value={adjustment.year} onChange={(event) => setAdjustment({ ...adjustment, year: event.target.value })} />
      <input placeholder="公告标题" value={adjustment.title} onChange={(event) => setAdjustment({ ...adjustment, title: event.target.value })} />
      <label className="check-line"><input type="checkbox" checked={adjustment.open} onChange={(event) => setAdjustment({ ...adjustment, open: event.target.checked })} />当前开放</label>
      <input type="number" min="0" placeholder="缺额人数" value={adjustment.vacancyCount} onChange={(event) => setAdjustment({ ...adjustment, vacancyCount: event.target.value })} />
      <input placeholder="申请时间窗口" value={adjustment.applicationWindow} onChange={(event) => setAdjustment({ ...adjustment, applicationWindow: event.target.value })} />
      <textarea placeholder="基本条件" value={adjustment.requirements} onChange={(event) => setAdjustment({ ...adjustment, requirements: event.target.value })} />
      <input placeholder="公告链接" value={adjustment.noticeUrl} onChange={(event) => setAdjustment({ ...adjustment, noticeUrl: event.target.value })} />
      <input placeholder="备注" value={adjustment.remark} onChange={(event) => setAdjustment({ ...adjustment, remark: event.target.value })} />
      <button type="button" disabled={!adjustment.majorId || !adjustment.title.trim() || !hasEvidenceSource(adjustment) || !validAdjustment} onClick={onCreateAdjustment}><Plus size={16} />{editingAdjustmentId ? '保存修改' : '新增调剂信息'}</button>
      {editingAdjustmentId && <button type="button" className="secondary-button" onClick={onCancel}>取消编辑</button>}
    </AdminBlock>
  </>;
}
