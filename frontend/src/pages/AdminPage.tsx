import { Plus } from 'lucide-react';
import { PROVINCE_OPTIONS } from '../provinces';
import { useState, type Dispatch, type ReactNode, type SetStateAction } from 'react';
import type { useAdminForms } from '../adminForms';
import type { AdminFeedback } from '../components/AdminSession';
import { AdminActionFeedback, AdminSession } from '../components/AdminSession';
import { AdminBlock, AdminSectionNav, CollegeSelect, SchoolSelect, type AdminSectionKey } from '../components/AdminComponents';
import { DataCoveragePanel } from '../components/DataCoveragePanel';
import { SourceDocumentAdmin } from '../components/SourceDocumentAdmin';
import { AdmissionMetricsAdmin } from '../components/AdmissionMetricsAdmin';
import { AdmissionResultImportAdmin } from '../components/AdmissionResultImportAdmin';
import { RetestEvidenceAdmin } from '../components/RetestEvidenceAdmin';
import { DataCollectionTasks } from '../components/DataCollectionTasks';
import type {
  AdmissionPlan, AdmissionResult, AdjustmentInfo, AdminRole, College, DataCollectionTarget, DataCollectionTargetRequest, DataCollectionTask, DataCollectionTaskUpdate, DataCoverageReport, DocumentChunk, DocumentParseTask, DocumentPublicationBatch, DocumentSource, OfficialLinkCandidate,
  ExamSubject, Major, ReferenceBook, RetestRule, School, ScoreLine, SourceDocument, SourceDocumentVersion,
  SourceDocumentQualityReport, WebCaptureChange, WebCaptureChangeSummary, WebCaptureSchedule, WebCaptureTask
} from '../types';

type AdminForms = ReturnType<typeof useAdminForms>;

type AdminData = {
  schools: School[];
  colleges: College[];
  majors: Major[];
  admissionPlans: AdmissionPlan[];
  examSubjects: ExamSubject[];
  scoreLines: ScoreLine[];
  admissionResults: AdmissionResult[];
  retestRules: RetestRule[];
  referenceBooks: ReferenceBook[];
  adjustmentInfos: AdjustmentInfo[];
  sources: DocumentSource[];
  sourceDocuments: SourceDocument[];
  documentChunks: DocumentChunk[];
  documentParseTasks: DocumentParseTask[];
  webCaptureTasks: WebCaptureTask[];
  webCaptureChanges: WebCaptureChange[];
  webCaptureChangeSummary: WebCaptureChangeSummary | null;
  webCaptureSchedules: WebCaptureSchedule[];
  publicationBatches: DocumentPublicationBatch[];
  chunkSearchResults: DocumentChunk[];
  collectionTasks: DataCollectionTask[];
  coverageReport: DataCoverageReport | null;
};

type AdminDocumentState = {
  parseMessage: string;
  webCaptureTargetId: string;
  webCaptureMessage: string;
  webChangeReviewNote: string;
  webChangeMessage: string;
  webCaptureScheduleMessage: string;
  batchImportText: string;
  batchImportMessage: string;
  batchQualityReport: SourceDocumentQualityReport | null;
  chunkKeyword: string;
  documentVersions: SourceDocumentVersion[];
  versionDocumentId: number | null;
  versionMessage: string;
  selectedPublicationIds: number[];
  publicationReason: string;
  publicationMessage: string;
  setBatchImportText: Dispatch<SetStateAction<string>>;
  setBatchQualityReport: Dispatch<SetStateAction<SourceDocumentQualityReport | null>>;
  setChunkKeyword: Dispatch<SetStateAction<string>>;
  setWebCaptureTargetId: Dispatch<SetStateAction<string>>;
  setWebChangeReviewNote: Dispatch<SetStateAction<string>>;
  setPublicationReason: Dispatch<SetStateAction<string>>;
};

type AdminActions = {
  createSchool: () => void;
  createCollege: () => void;
  createMajor: () => void;
  createSource: () => void;
  saveSourceDocument: () => void;
  importSourceDocumentFile: (file: File | null) => void;
  captureSourceDocumentWeb: () => void;
  qualityCheckSourceDocuments: () => void;
  batchImportSourceDocuments: () => void;
  searchDocumentChunks: () => void;
  createAdmissionPlan: () => void;
  createExamSubject: () => void;
  createScoreLine: () => void;
  createAdmissionResult: () => void;
  createRetestRule: () => void;
  createReferenceBook: () => void;
  createAdjustmentInfo: () => void;
  editSchool: (item: School) => void;
  editCollege: (item: College) => void;
  editMajor: (item: Major) => void;
  editSource: (item: DocumentSource) => void;
  editSourceDocument: (item: SourceDocument) => void;
  loadDocumentVersions: (documentId: number) => void;
  rollbackSourceDocument: (documentId: number, versionNo: number) => void;
  editPlan: (item: AdmissionPlan) => void;
  editExam: (item: ExamSubject) => void;
  editScore: (item: ScoreLine) => void;
  editAdmissionResult: (item: AdmissionResult) => void;
  editRetestRule: (item: RetestRule) => void;
  editReferenceBook: (item: ReferenceBook) => void;
  editAdjustmentInfo: (item: AdjustmentInfo) => void;
  deleteResource: (path: string, id: number) => void;
  cancelEditing: () => void;
  updateCollectionTask: (schoolId: number, patch: DataCollectionTaskUpdate) => Promise<void>;
  createCollectionTarget: (schoolId: number, patch: DataCollectionTargetRequest) => Promise<void>;
  updateCollectionTarget: (schoolId: number, targetId: number, patch: DataCollectionTargetRequest) => Promise<void>;
  deleteCollectionTarget: (schoolId: number, targetId: number) => Promise<void>;
  discoverCollectionTargetLinks: (schoolId: number, targetId: number) => Promise<OfficialLinkCandidate[]>;
  acceptCollectionTargetLink: (schoolId: number, targetId: number, sourceUrl: string) => Promise<void>;
  togglePublicationDocument: (documentId: number) => void;
  publishDocumentBatch: () => void;
  rollbackDocumentBatch: (batchId: number) => void;
  reviewWebCaptureChange: (changeId: number, status: 'ACKNOWLEDGED' | 'IGNORED') => void;
  configureWebCaptureSchedule: (targetId: number, enabled: boolean, intervalHours: number) => Promise<void>;
  runDueWebCaptureSchedules: () => Promise<void>;
};

export function AdminPage({
  loggedIn,
  adminToken,
  adminName,
  adminRole,
  loginForm,
  loginError,
  passwordForm,
  passwordError,
  feedback,
  onLoginFormChange,
  onPasswordFormChange,
  onLogin,
  onLogout,
  onChangePassword,
  onDismissFeedback,
  data,
  forms,
  documentState,
  actions,
  agentWorkspace
}: {
  loggedIn: boolean;
  adminToken: string;
  adminName: string;
  adminRole: AdminRole | '';
  loginForm: { username: string; password: string };
  loginError: string;
  passwordForm: { currentPassword: string; newPassword: string };
  passwordError: string;
  feedback: AdminFeedback | null;
  onLoginFormChange: (patch: Partial<{ username: string; password: string }>) => void;
  onPasswordFormChange: (patch: Partial<{ currentPassword: string; newPassword: string }>) => void;
  onLogin: () => void;
  onLogout: () => void;
  onChangePassword: () => void;
  onDismissFeedback: () => void;
  data: AdminData;
  forms: AdminForms;
  documentState: AdminDocumentState;
  actions: AdminActions;
  agentWorkspace: ReactNode;
}) {
  const {
    schools, colleges, majors, admissionPlans, examSubjects, scoreLines, admissionResults,
    retestRules, referenceBooks, adjustmentInfos, sources, sourceDocuments, documentChunks,
    chunkSearchResults, collectionTasks, coverageReport, documentParseTasks, webCaptureTasks, webCaptureChanges, webCaptureChangeSummary, webCaptureSchedules, publicationBatches
  } = data;
  const {
    newSchool, setNewSchool, newCollege, setNewCollege, newMajor, setNewMajor, newSource, setNewSource,
    newSourceDocument, setNewSourceDocument, newPlan, setNewPlan, newExam, setNewExam, newScore, setNewScore,
    newAdmissionResult, setNewAdmissionResult, newRetestRule, setNewRetestRule, newReferenceBook, setNewReferenceBook,
    newAdjustmentInfo, setNewAdjustmentInfo, editingSchoolId, editingCollegeId, editingMajorId, editingSourceId,
    editingSourceDocumentId, editingPlanId, editingExamId, editingScoreId, editingAdmissionResultId,
    editingRetestRuleId, editingReferenceBookId, editingAdjustmentInfoId
  } = forms;
  const [adminSection, setAdminSection] = useState<AdminSectionKey>('overview');

  return <>
    <AdminSession loggedIn={loggedIn} adminName={adminName} adminRole={adminRole} loginForm={loginForm} loginError={loginError} passwordForm={passwordForm} passwordError={passwordError} onLoginFormChange={onLoginFormChange} onPasswordFormChange={onPasswordFormChange} onLogin={onLogin} onLogout={onLogout} onChangePassword={onChangePassword} />
    {loggedIn && <AdminSectionNav active={adminSection} onChange={setAdminSection} />}
    {loggedIn && <AdminActionFeedback feedback={feedback} onDismiss={onDismissFeedback} />}

    {!loggedIn ? (
      <section className="panel"><div className="empty-state">请登录管理端</div></section>
    ) : (
      <section className="admin-layout">
        {adminSection === 'overview' && <div className="admin-section-stack">
          <DataCoveragePanel report={coverageReport} />
          <DataCollectionTasks tasks={collectionTasks} onUpdate={actions.updateCollectionTask} onCreateTarget={actions.createCollectionTarget} onUpdateTarget={actions.updateCollectionTarget} onDeleteTarget={actions.deleteCollectionTarget} onDiscoverLinks={actions.discoverCollectionTargetLinks} onAcceptLink={actions.acceptCollectionTargetLink} />
        </div>}

        {adminSection === 'catalog' && <div className="admin-section-stack">
        <AdminBlock id="admin-schools" title="学校管理" items={schools.map((item) => ({ id: item.id, label: item.name, onEdit: () => actions.editSchool(item) }))} deletePath="schools" onDelete={actions.deleteResource} editing={Boolean(editingSchoolId)} onCancelEditor={actions.cancelEditing}>
          <input placeholder="学校名称" value={newSchool.name} onChange={(event) => setNewSchool({ ...newSchool, name: event.target.value })} />
          <select aria-label="省份" value={newSchool.province} onChange={(event) => setNewSchool({ ...newSchool, province: event.target.value })}><option value="">选择省份</option>{PROVINCE_OPTIONS.map((province) => <option key={province}>{province}</option>)}</select>
          <input placeholder="城市" value={newSchool.city} onChange={(event) => setNewSchool({ ...newSchool, city: event.target.value })} />
          <select value={newSchool.schoolLevel} onChange={(event) => setNewSchool({ ...newSchool, schoolLevel: event.target.value })}><option>普通院校</option><option>211/双一流</option><option>985/211/双一流</option></select>
          <button type="button" onClick={actions.createSchool}><Plus size={16} />{editingSchoolId ? '保存修改' : '新增学校'}</button>
          {editingSchoolId && <button type="button" className="secondary-button" onClick={actions.cancelEditing}>取消编辑</button>}
        </AdminBlock>

        <AdminBlock title="学院管理" items={colleges.map((item) => ({ id: item.id, label: item.name, onEdit: () => actions.editCollege(item) }))} deletePath="colleges" onDelete={actions.deleteResource} editing={Boolean(editingCollegeId)} onCancelEditor={actions.cancelEditing}>
          <SchoolSelect schools={schools} value={newCollege.schoolId} onChange={(value) => setNewCollege({ ...newCollege, schoolId: value })} />
          <input placeholder="学院名称" value={newCollege.name} onChange={(event) => setNewCollege({ ...newCollege, name: event.target.value })} />
          <input placeholder="学院官网" value={newCollege.officialSite} onChange={(event) => setNewCollege({ ...newCollege, officialSite: event.target.value })} />
          <button type="button" onClick={actions.createCollege}><Plus size={16} />{editingCollegeId ? '保存修改' : '新增学院'}</button>
          {editingCollegeId && <button type="button" className="secondary-button" onClick={actions.cancelEditing}>取消编辑</button>}
        </AdminBlock>

        <AdminBlock title="专业管理" items={majors.map((item) => ({ id: item.id, label: `${item.name} ${item.degreeType ?? ''}`, onEdit: () => actions.editMajor(item) }))} deletePath="majors" onDelete={actions.deleteResource} editing={Boolean(editingMajorId)} onCancelEditor={actions.cancelEditing}>
          <SchoolSelect schools={schools} value={newMajor.schoolId} onChange={(value) => setNewMajor({ ...newMajor, schoolId: value, collegeId: '' })} />
          <CollegeSelect colleges={colleges} schoolId={newMajor.schoolId} value={newMajor.collegeId} onChange={(value) => setNewMajor({ ...newMajor, collegeId: value })} />
          <input placeholder="专业名称" value={newMajor.name} onChange={(event) => setNewMajor({ ...newMajor, name: event.target.value })} />
          <input placeholder="专业代码" value={newMajor.majorCode} onChange={(event) => setNewMajor({ ...newMajor, majorCode: event.target.value })} />
          <select value={newMajor.degreeType} onChange={(event) => setNewMajor({ ...newMajor, degreeType: event.target.value })}><option>专硕</option><option>学硕</option></select>
          <input placeholder="研究方向" value={newMajor.researchDirection} onChange={(event) => setNewMajor({ ...newMajor, researchDirection: event.target.value })} />
          <button type="button" onClick={actions.createMajor}><Plus size={16} />{editingMajorId ? '保存修改' : '新增专业'}</button>
          {editingMajorId && <button type="button" className="secondary-button" onClick={actions.cancelEditing}>取消编辑</button>}
        </AdminBlock>
        </div>}

        {adminSection === 'knowledge' && <div className="admin-section-stack">
        <AdminBlock id="admin-sources" title="资料来源" items={sources.map((item) => ({ id: item.id, label: `${item.year ?? '常设'} ${item.title}`, onEdit: () => actions.editSource(item) }))} deletePath="sources" onDelete={actions.deleteResource} editing={Boolean(editingSourceId)} onCancelEditor={actions.cancelEditing}>
          <SchoolSelect schools={schools} value={newSource.schoolId} onChange={(value) => setNewSource({ ...newSource, schoolId: value, collegeId: '' })} />
          <CollegeSelect colleges={colleges} schoolId={newSource.schoolId} value={newSource.collegeId} onChange={(value) => setNewSource({ ...newSource, collegeId: value })} />
          <input placeholder="资料标题" value={newSource.title} onChange={(event) => setNewSource({ ...newSource, title: event.target.value })} />
          <input placeholder="来源类型" value={newSource.sourceType} onChange={(event) => setNewSource({ ...newSource, sourceType: event.target.value })} />
          <input placeholder="资料链接" value={newSource.sourceUrl} onChange={(event) => setNewSource({ ...newSource, sourceUrl: event.target.value })} />
          <input type="date" value={newSource.publishDate} onChange={(event) => setNewSource({ ...newSource, publishDate: event.target.value })} />
          <input placeholder="年份" value={newSource.year} onChange={(event) => setNewSource({ ...newSource, year: event.target.value })} />
          <label className="check-line"><input type="checkbox" checked={newSource.official} onChange={(event) => setNewSource({ ...newSource, official: event.target.checked })} />官方来源</label>
          <select value={newSource.auditStatus} onChange={(event) => setNewSource({ ...newSource, auditStatus: event.target.value })}><option value="PUBLISHED">已发布</option><option value="PENDING">待审核</option><option value="DRAFT">草稿</option></select>
          <input placeholder="备注" value={newSource.remark} onChange={(event) => setNewSource({ ...newSource, remark: event.target.value })} />
          <button type="button" onClick={actions.createSource}><Plus size={16} />{editingSourceId ? '保存修改' : '新增来源'}</button>
          {editingSourceId && <button type="button" className="secondary-button" onClick={actions.cancelEditing}>取消编辑</button>}
        </AdminBlock>

        <SourceDocumentAdmin
          documents={sourceDocuments} chunks={documentChunks} searchResults={chunkSearchResults}
          parseTasks={documentParseTasks}
          webCaptureTasks={webCaptureTasks}
          webCaptureChanges={webCaptureChanges}
          webCaptureChangeSummary={webCaptureChangeSummary}
          webCaptureSchedules={webCaptureSchedules}
          publicationBatches={publicationBatches}
          captureTargets={collectionTasks.flatMap((task) => task.targets) as DataCollectionTarget[]}
          schools={schools} colleges={colleges} majors={majors} form={newSourceDocument} editingId={editingSourceDocumentId}
          parseMessage={documentState.parseMessage} batchText={documentState.batchImportText} batchMessage={documentState.batchImportMessage} qualityReport={documentState.batchQualityReport} chunkKeyword={documentState.chunkKeyword}
          webCaptureTargetId={documentState.webCaptureTargetId} webCaptureMessage={documentState.webCaptureMessage}
          webChangeReviewNote={documentState.webChangeReviewNote} webChangeMessage={documentState.webChangeMessage}
          webCaptureScheduleMessage={documentState.webCaptureScheduleMessage}
          canReviewWebChanges={adminRole !== 'AUDITOR'}
          canManageWebSchedules={adminRole === 'ADMIN'}
          selectedPublicationIds={documentState.selectedPublicationIds} publicationReason={documentState.publicationReason}
          publicationMessage={documentState.publicationMessage} canManagePublications={adminRole === 'ADMIN'}
          versions={documentState.documentVersions} versionDocumentId={documentState.versionDocumentId} versionMessage={documentState.versionMessage}
          onFormChange={(patch) => setNewSourceDocument((current) => ({ ...current, ...patch }))}
          onImportFile={actions.importSourceDocumentFile}
          onWebCaptureTargetChange={documentState.setWebCaptureTargetId} onCaptureWeb={actions.captureSourceDocumentWeb}
          onWebChangeReviewNoteChange={documentState.setWebChangeReviewNote}
          onReviewWebChange={actions.reviewWebCaptureChange}
          onConfigureWebSchedule={actions.configureWebCaptureSchedule} onRunDueWebSchedules={actions.runDueWebCaptureSchedules}
          onTogglePublication={actions.togglePublicationDocument}
          onPublicationReasonChange={documentState.setPublicationReason}
          onPublishBatch={actions.publishDocumentBatch} onRollbackBatch={actions.rollbackDocumentBatch}
          onBatchTextChange={(value) => { documentState.setBatchImportText(value); documentState.setBatchQualityReport(null); }}
          onQualityCheck={actions.qualityCheckSourceDocuments} onBatchImport={actions.batchImportSourceDocuments}
          onSave={actions.saveSourceDocument} onCancel={actions.cancelEditing} onEdit={actions.editSourceDocument}
          onDelete={(id) => actions.deleteResource('source-documents', id)}
          onViewVersions={actions.loadDocumentVersions} onRollback={actions.rollbackSourceDocument}
          onChunkKeywordChange={documentState.setChunkKeyword} onSearchChunks={actions.searchDocumentChunks}
        />
        </div>}

        {adminSection === 'admissions' && <div className="admin-section-stack">
        <AdmissionResultImportAdmin token={adminToken} role={adminRole} schools={schools} />
        <AdmissionMetricsAdmin
          majors={majors} sources={sources} plans={admissionPlans} exams={examSubjects} scores={scoreLines} results={admissionResults}
          plan={newPlan} exam={newExam} score={newScore} result={newAdmissionResult}
          editingPlanId={editingPlanId} editingExamId={editingExamId} editingScoreId={editingScoreId} editingResultId={editingAdmissionResultId}
          setPlan={setNewPlan} setExam={setNewExam} setScore={setNewScore} setResult={setNewAdmissionResult}
          onCreatePlan={actions.createAdmissionPlan} onCreateExam={actions.createExamSubject} onCreateScore={actions.createScoreLine} onCreateResult={actions.createAdmissionResult}
          onEditPlan={actions.editPlan} onEditExam={actions.editExam} onEditScore={actions.editScore} onEditResult={actions.editAdmissionResult}
          onDelete={actions.deleteResource} onCancel={actions.cancelEditing}
        />
        </div>}

        {adminSection === 'retest' && <div className="admin-section-stack">
        <RetestEvidenceAdmin
          schools={schools} majors={majors} sources={sources} rules={retestRules} books={referenceBooks} adjustments={adjustmentInfos}
          rule={newRetestRule} book={newReferenceBook} adjustment={newAdjustmentInfo}
          editingRuleId={editingRetestRuleId} editingBookId={editingReferenceBookId} editingAdjustmentId={editingAdjustmentInfoId}
          setRule={setNewRetestRule} setBook={setNewReferenceBook} setAdjustment={setNewAdjustmentInfo}
          onCreateRule={actions.createRetestRule} onCreateBook={actions.createReferenceBook} onCreateAdjustment={actions.createAdjustmentInfo}
          onEditRule={actions.editRetestRule} onEditBook={actions.editReferenceBook} onEditAdjustment={actions.editAdjustmentInfo}
          onDelete={actions.deleteResource} onCancel={actions.cancelEditing}
        />
        </div>}

        {adminSection === 'agent' && <div className="admin-section-stack">
          {agentWorkspace}
        </div>}
      </section>
    )}
  </>;
}
