import { useState, type Dispatch, type SetStateAction } from 'react';
import { requestJson, type ApiResponse } from '../api';
import { ADMIN_FORM_DEFAULTS, useAdminForms } from '../adminForms';
import { provinceToRegion } from '../provinces';
import { adminErrorMessage, adminSuccessMessage } from '../adminFeedback';
import {
  admissionPlanPayload, admissionResultPayload, adjustmentInfoPayload, examSubjectPayload,
  hasEvidenceSource, referenceBookPayload, retestRulePayload, scoreLinePayload,
  validOptionalNumber, validRetestWeights, validYear
} from '../adminPayloads';
import { canSaveSourceDocumentDraft, formatSourceDocumentQualitySummary, parseSourceDocumentBatch, webCaptureDraftPatch } from '../sourceDocumentBatch';
import type { AdminFeedback } from '../components/AdminSession';
import type {
  AdmissionPlan, AdmissionResult, AdjustmentInfo, AdminLoginResponse, AdminRole, Catalog408Status, College,
  DataCollectionTargetRequest, DataCollectionTask, DataCollectionTaskUpdate, DataCoverageReport, DocumentChunk, DocumentParseTask, DocumentPublicationBatch, DocumentPublicationBatchResult, DocumentSource, ExamSubject, Major, OfficialLinkCandidate, ParsedSourceDocumentDraft,
  ReferenceBook, RetestRule, School, ScoreLine, SourceDocument, SourceDocumentBatchImportResult,
  SourceDocumentRollbackResult, SourceDocumentVersion,
  SourceDocumentQualityReport, WebCaptureChange, WebCaptureChangeSummary, WebCaptureDraft, WebCaptureMonitorRunResult, WebCaptureSchedule, WebCaptureTask
} from '../types';

type AdminWorkspaceOptions = {
  schools: School[];
  reloadSchools: () => void;
  setDataWarning: Dispatch<SetStateAction<string>>;
};

async function readResponseJson<T>(response: Response): Promise<ApiResponse<T>> {
  const text = await response.text();
  if (!text) throw new Error(`空响应: ${response.status}`);
  try {
    return JSON.parse(text) as ApiResponse<T>;
  } catch {
    throw new Error(`响应格式错误: ${response.status}`);
  }
}

export function useAdminWorkspace({ schools, reloadSchools, setDataWarning }: AdminWorkspaceOptions) {
  const forms = useAdminForms();
  const {
    newSchool, setNewSchool, newCollege, setNewCollege, newMajor, setNewMajor, newSource, setNewSource,
    newSourceDocument, setNewSourceDocument, newPlan, setNewPlan, newExam, setNewExam, newScore, setNewScore,
    newAdmissionResult, setNewAdmissionResult, newRetestRule, setNewRetestRule, newReferenceBook, setNewReferenceBook,
    newAdjustmentInfo, setNewAdjustmentInfo, editingSchoolId, setEditingSchoolId, editingCollegeId, setEditingCollegeId,
    editingMajorId, setEditingMajorId, editingSourceId, setEditingSourceId, editingSourceDocumentId, setEditingSourceDocumentId,
    editingPlanId, setEditingPlanId, editingExamId, setEditingExamId, editingScoreId, setEditingScoreId,
    editingAdmissionResultId, setEditingAdmissionResultId, editingRetestRuleId, setEditingRetestRuleId,
    editingReferenceBookId, setEditingReferenceBookId, editingAdjustmentInfoId, setEditingAdjustmentInfoId, resetAdminForms
  } = forms;
  const [colleges, setColleges] = useState<College[]>([]);
  const [majors, setMajors] = useState<Major[]>([]);
  const [admissionPlans, setAdmissionPlans] = useState<AdmissionPlan[]>([]);
  const [examSubjects, setExamSubjects] = useState<ExamSubject[]>([]);
  const [scoreLines, setScoreLines] = useState<ScoreLine[]>([]);
  const [admissionResults, setAdmissionResults] = useState<AdmissionResult[]>([]);
  const [retestRules, setRetestRules] = useState<RetestRule[]>([]);
  const [referenceBooks, setReferenceBooks] = useState<ReferenceBook[]>([]);
  const [adjustmentInfos, setAdjustmentInfos] = useState<AdjustmentInfo[]>([]);
  const [sources, setSources] = useState<DocumentSource[]>([]);
  const [sourceDocuments, setSourceDocuments] = useState<SourceDocument[]>([]);
  const [coverageReport, setCoverageReport] = useState<DataCoverageReport | null>(null);
  const [collectionTasks, setCollectionTasks] = useState<DataCollectionTask[]>([]);
  const [catalog408Status, setCatalog408Status] = useState<Catalog408Status | null>(null);
  const [documentChunks, setDocumentChunks] = useState<DocumentChunk[]>([]);
  const [chunkSearchResults, setChunkSearchResults] = useState<DocumentChunk[]>([]);
  const [chunkKeyword, setChunkKeyword] = useState('408');
  const [parseMessage, setParseMessage] = useState('');
  const [batchImportText, setBatchImportText] = useState('');
  const [batchImportMessage, setBatchImportMessage] = useState('');
  const [batchQualityReport, setBatchQualityReport] = useState<SourceDocumentQualityReport | null>(null);
  const [documentVersions, setDocumentVersions] = useState<SourceDocumentVersion[]>([]);
  const [documentParseTasks, setDocumentParseTasks] = useState<DocumentParseTask[]>([]);
  const [webCaptureTasks, setWebCaptureTasks] = useState<WebCaptureTask[]>([]);
  const [webCaptureTargetId, setWebCaptureTargetId] = useState('');
  const [webCaptureMessage, setWebCaptureMessage] = useState('');
  const [webCaptureChanges, setWebCaptureChanges] = useState<WebCaptureChange[]>([]);
  const [webCaptureChangeSummary, setWebCaptureChangeSummary] = useState<WebCaptureChangeSummary | null>(null);
  const [webCaptureSchedules, setWebCaptureSchedules] = useState<WebCaptureSchedule[]>([]);
  const [webCaptureScheduleMessage, setWebCaptureScheduleMessage] = useState('');
  const [webChangeReviewNote, setWebChangeReviewNote] = useState('');
  const [webChangeMessage, setWebChangeMessage] = useState('');
  const [publicationBatches, setPublicationBatches] = useState<DocumentPublicationBatch[]>([]);
  const [selectedPublicationIds, setSelectedPublicationIds] = useState<number[]>([]);
  const [publicationReason, setPublicationReason] = useState('');
  const [publicationMessage, setPublicationMessage] = useState('');
  const [versionDocumentId, setVersionDocumentId] = useState<number | null>(null);
  const [versionMessage, setVersionMessage] = useState('');
  const [adminToken, setAdminToken] = useState(() => localStorage.getItem('adminToken') ?? '');
  const [adminName, setAdminName] = useState(() => localStorage.getItem('adminName') ?? '');
  const [adminRole, setAdminRole] = useState<AdminRole | ''>(() => {
    const stored = localStorage.getItem('adminRole');
    if (stored === 'ADMIN' || stored === 'DATA_EDITOR' || stored === 'AUDITOR') return stored;
    return localStorage.getItem('adminToken') ? 'ADMIN' : '';
  });
  const [loginForm, setLoginForm] = useState({ username: 'admin', password: '' });
  const [loginError, setLoginError] = useState('');
  const [passwordForm, setPasswordForm] = useState({ currentPassword: '', newPassword: '' });
  const [passwordError, setPasswordError] = useState('');
  const [adminFeedback, setAdminFeedback] = useState<AdminFeedback | null>(null);
  const isAdminLoggedIn = Boolean(adminToken);

  const selectedMajor = (majorId: string) => majors.find((major) => major.id === Number(majorId));

  const loadSources = (includeAll = false) => {
    const params = new URLSearchParams();
    if (includeAll) params.set('auditStatus', 'ALL');
    const headers = adminToken ? { Authorization: `Bearer ${adminToken}` } : undefined;
    requestJson<DocumentSource[]>(`/api/sources${params.size ? `?${params.toString()}` : ''}`, { headers })
      .then((payload) => setSources(payload.data ?? []))
      .catch((requestError: Error) => {
        setDataWarning(`官方来源加载失败：${requestError.message}`);
        setSources([]);
      });
  };

  const loadSourceDocuments = (includeAll = false) => {
    const params = new URLSearchParams();
    if (includeAll) params.set('auditStatus', 'ALL');
    const headers = adminToken ? { Authorization: `Bearer ${adminToken}` } : undefined;
    requestJson<SourceDocument[]>(`/api/source-documents${params.size ? `?${params.toString()}` : ''}`, { headers })
      .then((payload) => setSourceDocuments(payload.data ?? []))
      .catch((requestError: Error) => {
        setDataWarning(`资料文档加载失败：${requestError.message}`);
        setSourceDocuments([]);
      });
  };

  const loadPublicData = () => {
    setDataWarning('');
    loadSources(false);
    loadSourceDocuments(false);
    requestJson<DataCoverageReport>('/api/data-coverage')
      .then((payload) => setCoverageReport(payload.data))
      .catch(() => setCoverageReport(null));
    requestJson<Catalog408Status | null>('/api/catalog-imports/408/status')
      .then((payload) => setCatalog408Status(payload.data))
      .catch(() => setCatalog408Status(null));
  };

  const loadCollectionTasks = () => requestJson<DataCollectionTask[]>('/api/data-coverage/tasks?limit=100&status=ALL')
    .then((payload) => setCollectionTasks(payload.data ?? []))
    .catch(() => setCollectionTasks([]));

  const loadAdminData = () => {
    loadParseTasks();
    loadWebCaptureTasks();
    loadWebCaptureChanges();
    loadWebCaptureSchedules();
    loadPublicationBatches();
    loadCollectionTasks();
    requestJson<College[]>('/api/colleges').then((payload) => setColleges(payload.data ?? [])).catch(() => setColleges([]));
    requestJson<Major[]>('/api/majors').then((payload) => setMajors(payload.data ?? [])).catch(() => setMajors([]));
    requestJson<AdmissionPlan[]>('/api/admission-plans').then((payload) => setAdmissionPlans(payload.data ?? [])).catch(() => setAdmissionPlans([]));
    requestJson<ExamSubject[]>('/api/exam-subjects').then((payload) => setExamSubjects(payload.data ?? [])).catch(() => setExamSubjects([]));
    requestJson<ScoreLine[]>('/api/score-lines').then((payload) => setScoreLines(payload.data ?? [])).catch(() => setScoreLines([]));
    requestJson<AdmissionResult[]>('/api/admission-results').then((payload) => setAdmissionResults(payload.data ?? [])).catch(() => setAdmissionResults([]));
    requestJson<RetestRule[]>('/api/retest-rules').then((payload) => setRetestRules(payload.data ?? [])).catch(() => setRetestRules([]));
    requestJson<ReferenceBook[]>('/api/reference-books').then((payload) => setReferenceBooks(payload.data ?? [])).catch(() => setReferenceBooks([]));
    requestJson<AdjustmentInfo[]>('/api/adjustment-infos').then((payload) => setAdjustmentInfos(payload.data ?? [])).catch(() => setAdjustmentInfos([]));
    loadSources(true);
    loadSourceDocuments(true);
  };

  const refreshAdminData = () => {
    reloadSchools();
    loadAdminData();
  };

  const handleAdminUnauthorized = () => {
    localStorage.removeItem('adminToken');
    localStorage.removeItem('adminName');
    localStorage.removeItem('adminRole');
    setAdminToken('');
    setAdminName('');
    setAdminRole('');
    setLoginError('登录已失效，请重新登录');
  };

  const reportAdminError = (requestError: Error) => setAdminFeedback({ kind: 'error', message: requestError.message });

  const adminFetch = (url: string, init: RequestInit) => {
    const method = (init.method ?? 'GET').toUpperCase();
    const requiresAdministrator = method === 'DELETE' || url.includes('/rollback') || url.includes('/publication-batches')
      || url.startsWith('/api/catalog-imports') || url.startsWith('/api/ai/agent/operations');
    if (method !== 'GET' && adminRole === 'AUDITOR') {
      const error = new Error('审计员为只读角色，不能修改数据');
      reportAdminError(error);
      return Promise.reject(error);
    }
    if (requiresAdministrator && adminRole !== 'ADMIN') {
      const error = new Error('该操作仅系统管理员可执行');
      reportAdminError(error);
      return Promise.reject(error);
    }
    const reportsMutation = method !== 'GET' && !url.includes('/quality-check');
    if (reportsMutation) setAdminFeedback(null);
    return fetch(url, {
      ...init,
      headers: { ...(init.headers ?? {}), Authorization: `Bearer ${adminToken}` }
    }).then(async (response) => {
      if (response.status === 401) {
        handleAdminUnauthorized();
        throw new Error('请先登录管理端');
      }
      if (!response.ok) {
        const payload = await response.clone().json().catch(() => null) as unknown;
        throw new Error(adminErrorMessage(response.status, payload));
      }
      if (reportsMutation) setAdminFeedback({ kind: 'success', message: adminSuccessMessage(method, url) });
      return response;
    }).catch((requestError: Error) => {
      reportAdminError(requestError);
      throw requestError;
    });
  };

  const loadParseTasks = () => adminFetch('/api/source-documents/parse-tasks?limit=20', { method: 'GET' })
    .then((response) => readResponseJson<DocumentParseTask[]>(response))
    .then((payload) => setDocumentParseTasks(payload.data ?? []))
    .catch(() => setDocumentParseTasks([]));

  const loadWebCaptureTasks = () => adminFetch('/api/source-documents/web-captures?limit=20', { method: 'GET' })
    .then((response) => readResponseJson<WebCaptureTask[]>(response))
    .then((payload) => setWebCaptureTasks(payload.data ?? []))
    .catch(() => setWebCaptureTasks([]));

  const loadWebCaptureChanges = () => Promise.all([
    adminFetch('/api/source-documents/web-capture-changes?status=ALL&limit=20', { method: 'GET' })
      .then((response) => readResponseJson<WebCaptureChange[]>(response)),
    adminFetch('/api/source-documents/web-capture-changes/summary', { method: 'GET' })
      .then((response) => readResponseJson<WebCaptureChangeSummary>(response))
  ]).then(([changes, summary]) => {
    setWebCaptureChanges(changes.data ?? []);
    setWebCaptureChangeSummary(summary.data);
  }).catch(() => {
    setWebCaptureChanges([]);
    setWebCaptureChangeSummary(null);
  });

  const loadWebCaptureSchedules = () => adminFetch('/api/source-documents/web-capture-schedules', { method: 'GET' })
    .then((response) => readResponseJson<WebCaptureSchedule[]>(response))
    .then((payload) => setWebCaptureSchedules(payload.data ?? []))
    .catch(() => setWebCaptureSchedules([]));

  const loadPublicationBatches = () => adminFetch('/api/source-documents/publication-batches?limit=20', { method: 'GET' })
    .then((response) => readResponseJson<DocumentPublicationBatch[]>(response))
    .then((payload) => setPublicationBatches(payload.data ?? []))
    .catch(() => setPublicationBatches([]));

  const loginAdmin = () => {
    setLoginError('');
    requestJson<AdminLoginResponse>('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(loginForm)
    })
      .then((payload) => {
        if (payload.code !== 200) throw new Error(payload.message);
        localStorage.setItem('adminToken', payload.data.token);
        localStorage.setItem('adminName', payload.data.username);
        localStorage.setItem('adminRole', payload.data.role);
        setAdminToken(payload.data.token);
        setAdminName(payload.data.username);
        setAdminRole(payload.data.role);
        setLoginForm((current) => ({ ...current, password: '' }));
        setLoginError('');
        setAdminFeedback(null);
      })
      .catch((requestError: Error) => setLoginError(requestError.message));
  };

  const updateCollectionTask = (schoolId: number, patch: DataCollectionTaskUpdate) => adminFetch(`/api/data-coverage/tasks/${schoolId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(patch)
  })
    .then((response) => readResponseJson<DataCollectionTask>(response))
    .then((payload) => {
      setCollectionTasks((current) => current.map((task) => task.schoolId === schoolId ? payload.data : task));
    });

  const createCollectionTarget = (schoolId: number, patch: DataCollectionTargetRequest) => adminFetch(`/api/data-coverage/tasks/${schoolId}/targets`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(patch)
  }).then(() => loadCollectionTasks());

  const updateCollectionTarget = (schoolId: number, targetId: number, patch: DataCollectionTargetRequest) => adminFetch(`/api/data-coverage/tasks/${schoolId}/targets/${targetId}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(patch)
  }).then(() => loadCollectionTasks());

  const deleteCollectionTarget = (schoolId: number, targetId: number) => adminFetch(`/api/data-coverage/tasks/${schoolId}/targets/${targetId}`, {
    method: 'DELETE'
  }).then(() => loadCollectionTasks());

  const discoverCollectionTargetLinks = (schoolId: number, targetId: number) => adminFetch(`/api/data-coverage/tasks/${schoolId}/targets/${targetId}/discover-links`, {
    method: 'POST'
  }).then((response) => readResponseJson<OfficialLinkCandidate[]>(response))
    .then((payload) => payload.data ?? []);

  const acceptCollectionTargetLink = (schoolId: number, targetId: number, sourceUrl: string) => adminFetch(`/api/data-coverage/tasks/${schoolId}/targets/${targetId}/accept-link`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sourceUrl })
  }).then(() => loadCollectionTasks());

  const logoutAdmin = () => {
    if (adminToken) {
      fetch('/api/auth/logout', { method: 'POST', headers: { Authorization: `Bearer ${adminToken}` } }).catch(() => undefined);
    }
    localStorage.removeItem('adminToken');
    localStorage.removeItem('adminName');
    localStorage.removeItem('adminRole');
    setAdminToken('');
    setAdminName('');
    setAdminRole('');
    setAdminFeedback(null);
  };

  const changeAdminPassword = () => {
    setPasswordError('');
    requestJson<void>('/api/auth/password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${adminToken}` },
      body: JSON.stringify(passwordForm)
    }).then(() => {
      localStorage.removeItem('adminToken');
      localStorage.removeItem('adminName');
      localStorage.removeItem('adminRole');
      setAdminToken('');
      setAdminName('');
      setAdminRole('');
      setPasswordForm({ currentPassword: '', newPassword: '' });
      setLoginError('密码已更新，请使用新密码重新登录');
      setAdminFeedback(null);
    }).catch((requestError: Error) => setPasswordError(requestError.message));
  };

  const createSchool = () => {
    if (!newSchool.name.trim()) return;
    adminFetch(editingSchoolId ? `/api/schools/${editingSchoolId}` : '/api/schools', {
      method: editingSchoolId ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...newSchool, region: provinceToRegion(newSchool.province), is985: newSchool.schoolLevel.includes('985'), is211: newSchool.schoolLevel.includes('211'), isDoubleFirstClass: newSchool.schoolLevel.includes('双一流') })
    }).then(() => {
      setNewSchool({ ...ADMIN_FORM_DEFAULTS.school });
      setEditingSchoolId(null);
      refreshAdminData();
    }).catch(reportAdminError);
  };

  const createCollege = () => {
    if (!newCollege.schoolId || !newCollege.name.trim()) return;
    adminFetch(editingCollegeId ? `/api/colleges/${editingCollegeId}` : '/api/colleges', {
      method: editingCollegeId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...newCollege, schoolId: Number(newCollege.schoolId) })
    }).then(() => {
      setNewCollege({ ...ADMIN_FORM_DEFAULTS.college });
      setEditingCollegeId(null);
      refreshAdminData();
    }).catch(reportAdminError);
  };

  const createMajor = () => {
    if (!newMajor.schoolId || !newMajor.collegeId || !newMajor.name.trim()) return;
    adminFetch(editingMajorId ? `/api/majors/${editingMajorId}` : '/api/majors', {
      method: editingMajorId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...newMajor, schoolId: Number(newMajor.schoolId), collegeId: Number(newMajor.collegeId) })
    }).then(() => {
      setNewMajor({ ...ADMIN_FORM_DEFAULTS.major });
      setEditingMajorId(null);
      refreshAdminData();
    }).catch(reportAdminError);
  };

  const createSource = () => {
    if (!newSource.schoolId || !newSource.title.trim()) return;
    adminFetch(editingSourceId ? `/api/sources/${editingSourceId}` : '/api/sources', {
      method: editingSourceId ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ...newSource,
        schoolId: Number(newSource.schoolId),
        collegeId: newSource.collegeId ? Number(newSource.collegeId) : null,
        publishDate: newSource.publishDate || null,
        year: newSource.year ? Number(newSource.year) : null,
        official: newSource.official
      })
    }).then(() => {
      setNewSource({ ...ADMIN_FORM_DEFAULTS.source });
      setEditingSourceId(null);
      refreshAdminData();
    }).catch(reportAdminError);
  };

  const generateDocumentChunks = (documentId: number) => adminFetch(`/api/source-documents/${documentId}/chunks`, { method: 'POST' })
    .then((response) => readResponseJson<DocumentChunk[]>(response))
    .then((payload) => {
      setDocumentChunks(payload.data ?? []);
      return payload.data ?? [];
    });

  const loadDocumentVersions = (documentId: number, clearMessage = true) => {
    setVersionDocumentId(documentId);
    if (clearMessage) setVersionMessage('');
    adminFetch(`/api/source-documents/${documentId}/versions`, { method: 'GET' })
      .then((response) => readResponseJson<SourceDocumentVersion[]>(response))
      .then((payload) => setDocumentVersions(payload.data ?? []))
      .catch(() => setDocumentVersions([]));
  };

  const saveSourceDocument = () => {
    if (!canSaveSourceDocumentDraft(newSourceDocument)) return;
    adminFetch(editingSourceDocumentId ? `/api/source-documents/${editingSourceDocumentId}` : '/api/source-documents', {
      method: editingSourceDocumentId ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: newSourceDocument.title,
        documentType: newSourceDocument.documentType,
        sourceUrl: newSourceDocument.sourceUrl,
        schoolId: newSourceDocument.schoolId ? Number(newSourceDocument.schoolId) : null,
        collegeId: newSourceDocument.collegeId ? Number(newSourceDocument.collegeId) : null,
        majorId: newSourceDocument.majorId ? Number(newSourceDocument.majorId) : null,
        year: newSourceDocument.year ? Number(newSourceDocument.year) : null,
        auditStatus: newSourceDocument.auditStatus,
        sourceReliability: newSourceDocument.sourceReliability,
        rawText: newSourceDocument.rawText,
        remark: newSourceDocument.remark
      })
    })
      .then((response) => readResponseJson<SourceDocument>(response))
      .then((payload) => {
        setEditingSourceDocumentId(payload.data.id);
        loadSourceDocuments(true);
        return generateDocumentChunks(payload.data.id)
          .then(() => loadDocumentVersions(payload.data.id));
      })
      .catch(reportAdminError);
  };

  const previewDocumentChunks = (documentId: number) => {
    requestJson<DocumentChunk[]>(`/api/source-documents/${documentId}/chunks`)
      .then((payload) => setDocumentChunks(payload.data ?? []))
      .catch(() => setDocumentChunks([]));
  };

  const searchDocumentChunks = () => {
    const params = new URLSearchParams();
    if (chunkKeyword.trim()) params.set('keyword', chunkKeyword.trim());
    if (newSourceDocument.schoolId) params.set('schoolId', newSourceDocument.schoolId);
    params.set('limit', '8');
    requestJson<DocumentChunk[]>(`/api/source-documents/chunks/search?${params.toString()}`)
      .then((payload) => setChunkSearchResults(payload.data ?? []))
      .catch(() => setChunkSearchResults([]));
  };

  const importSourceDocumentFile = (file: File | null) => {
    if (!file) return;
    const formData = new FormData();
    formData.append('file', file);
    if (newSourceDocument.documentType) formData.append('documentType', newSourceDocument.documentType);
    setParseMessage('正在解析文件...');
    adminFetch('/api/source-documents/parse', { method: 'POST', body: formData })
      .then((response) => readResponseJson<ParsedSourceDocumentDraft>(response))
      .then((payload) => {
        setNewSourceDocument((current) => ({
          ...current,
          title: payload.data.title,
          documentType: payload.data.documentType,
          rawText: payload.data.rawText,
          remark: payload.data.remark,
          auditStatus: 'DRAFT'
        }));
        setParseMessage(payload.data.duplicate
          ? `已复用解析任务 #${payload.data.parseTaskId} · SHA ${payload.data.fileSha256.slice(0, 10)}`
          : `解析任务 #${payload.data.parseTaskId} 已完成 · ${payload.data.parserVersion} · SHA ${payload.data.fileSha256.slice(0, 10)}`);
        loadParseTasks();
      })
      .catch((requestError: Error) => setParseMessage(requestError.message));
  };

  const captureSourceDocumentWeb = () => {
    if (!webCaptureTargetId) return;
    setWebCaptureMessage('正在受控抓取官方页面...');
    adminFetch('/api/source-documents/web-captures', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ targetId: Number(webCaptureTargetId) })
    })
      .then((response) => readResponseJson<WebCaptureDraft>(response))
      .then((payload) => {
        setNewSourceDocument((current) => ({ ...current, ...webCaptureDraftPatch(payload.data) }));
        setWebCaptureMessage(payload.data.changeDetected
          ? `检测到官网正文变化，已生成待复核事件 #${payload.data.changeId}`
          : payload.data.duplicate
            ? `内容未变化，已复用采集任务 #${payload.data.captureTaskId}`
            : `采集任务 #${payload.data.captureTaskId} 已建立内容基线`);
        loadWebCaptureTasks();
        loadWebCaptureChanges();
      })
      .catch((requestError: Error) => {
        setWebCaptureMessage(requestError.message);
        loadWebCaptureTasks();
        loadWebCaptureChanges();
      });
  };

  const reviewWebCaptureChange = (changeId: number, status: 'ACKNOWLEDGED' | 'IGNORED') => {
    if (!webChangeReviewNote.trim()) return setWebChangeMessage('请填写复核说明');
    adminFetch(`/api/source-documents/web-capture-changes/${changeId}/review`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status, note: webChangeReviewNote.trim() })
    })
      .then((response) => readResponseJson<WebCaptureChange>(response))
      .then((payload) => {
        setWebChangeMessage(payload.data.status === 'ACKNOWLEDGED' ? `变更 #${changeId} 已确认` : `变更 #${changeId} 已忽略`);
        setWebChangeReviewNote('');
        loadWebCaptureChanges();
      })
      .catch((requestError: Error) => setWebChangeMessage(requestError.message));
  };

  const configureWebCaptureSchedule = (targetId: number, enabled: boolean, intervalHours: number) => adminFetch(`/api/source-documents/web-capture-schedules/${targetId}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled, intervalHours })
  }).then((response) => readResponseJson<WebCaptureSchedule>(response)).then(() => {
    setWebCaptureScheduleMessage(enabled ? `目标 #${targetId} 已启用定时监测` : `目标 #${targetId} 已停止定时监测`);
    return loadWebCaptureSchedules();
  }).catch((requestError: Error) => {
    setWebCaptureScheduleMessage(requestError.message);
    throw requestError;
  });

  const runDueWebCaptureSchedules = () => adminFetch('/api/source-documents/web-capture-schedules/run-due?limit=2', { method: 'POST' })
    .then((response) => readResponseJson<WebCaptureMonitorRunResult>(response)).then((payload) => {
      const result = payload.data;
      setWebCaptureScheduleMessage(`领取 ${result.claimedCount} 个 · 成功 ${result.completedCount} 个 · 失败 ${result.failedCount} 个 · 变化 ${result.changesDetected} 个`);
      loadWebCaptureTasks();
      loadWebCaptureChanges();
      return loadWebCaptureSchedules();
    }).catch((requestError: Error) => {
      setWebCaptureScheduleMessage(requestError.message);
      throw requestError;
    });

  const batchImportSourceDocuments = () => {
    const parsed = parseSourceDocumentBatch(batchImportText);
    setBatchQualityReport(null);
    if (!parsed.ok) return setBatchImportMessage(parsed.message);
    setBatchImportMessage('正在导入...');
    adminFetch('/api/source-documents/batch?generateChunks=true', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(parsed.documents)
    })
      .then((response) => readResponseJson<SourceDocumentBatchImportResult>(response))
      .then((payload) => {
        setBatchImportMessage(`已导入 ${payload.data.importedCount} 份资料，生成 ${payload.data.chunkCount} 个切片`);
        setBatchImportText('');
        setBatchQualityReport(null);
        loadSourceDocuments(true);
      })
      .catch((requestError: Error) => setBatchImportMessage(requestError.message));
  };

  const togglePublicationDocument = (documentId: number) => {
    setSelectedPublicationIds((current) => current.includes(documentId)
      ? current.filter((id) => id !== documentId)
      : [...current, documentId]);
  };

  const publishDocumentBatch = () => {
    if (selectedPublicationIds.length === 0) return setPublicationMessage('至少选择一份待发布资料');
    if (!publicationReason.trim()) return setPublicationMessage('请填写本次发布说明');
    setPublicationMessage('正在执行原子发布...');
    adminFetch('/api/source-documents/publication-batches', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ documentIds: selectedPublicationIds, reason: publicationReason.trim() })
    })
      .then((response) => readResponseJson<DocumentPublicationBatchResult>(response))
      .then((payload) => {
        setPublicationMessage(`批次 #${payload.data.batch.id} 已发布 ${payload.data.batch.documentCount} 份资料，生成 ${payload.data.batch.chunkCount} 个切片`);
        setSelectedPublicationIds([]);
        setPublicationReason('');
        loadSourceDocuments(true);
        loadPublicationBatches();
      })
      .catch((requestError: Error) => setPublicationMessage(requestError.message));
  };

  const rollbackDocumentBatch = (batchId: number) => {
    if (!publicationReason.trim()) return setPublicationMessage('回滚前请填写原因');
    if (!window.confirm(`确定回滚发布批次 #${batchId}？系统会恢复该批次全部资料的发布前版本。`)) return;
    setPublicationMessage(`正在回滚批次 #${batchId}...`);
    adminFetch(`/api/source-documents/publication-batches/${batchId}/rollback`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason: publicationReason.trim() })
    })
      .then((response) => readResponseJson<DocumentPublicationBatchResult>(response))
      .then((payload) => {
        setPublicationMessage(`批次 #${batchId} 已回滚 ${payload.data.documentIds.length} 份资料`);
        setPublicationReason('');
        loadSourceDocuments(true);
        loadPublicationBatches();
      })
      .catch((requestError: Error) => setPublicationMessage(requestError.message));
  };

  const qualityCheckSourceDocuments = () => {
    const parsed = parseSourceDocumentBatch(batchImportText);
    setBatchQualityReport(null);
    if (!parsed.ok) return setBatchImportMessage(parsed.message);
    setBatchImportMessage('正在预检...');
    adminFetch('/api/source-documents/quality-check', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(parsed.documents)
    })
      .then((response) => readResponseJson<SourceDocumentQualityReport>(response))
      .then((payload) => {
        setBatchQualityReport(payload.data);
        setBatchImportMessage(formatSourceDocumentQualitySummary(payload.data));
      })
      .catch((requestError: Error) => setBatchImportMessage(requestError.message));
  };

  const createAdmissionPlan = () => {
    const major = selectedMajor(newPlan.majorId);
    if (!major || !hasEvidenceSource(newPlan) || !validYear(newPlan.year)
      || ![newPlan.totalQuota, newPlan.recommendedQuota, newPlan.unifiedQuota].every((value) => validOptionalNumber(value, 0, 100000))) return;
    adminFetch(editingPlanId ? `/api/admission-plans/${editingPlanId}` : '/api/admission-plans', {
      method: editingPlanId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(admissionPlanPayload(major, newPlan))
    }).then(() => {
      setNewPlan({ ...ADMIN_FORM_DEFAULTS.plan }); setEditingPlanId(null); refreshAdminData();
    }).catch(reportAdminError);
  };

  const createExamSubject = () => {
    const major = selectedMajor(newExam.majorId);
    if (!major || !newExam.professionalSubject.trim() || !hasEvidenceSource(newExam) || !validYear(newExam.year)) return;
    adminFetch(editingExamId ? `/api/exam-subjects/${editingExamId}` : '/api/exam-subjects', {
      method: editingExamId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(examSubjectPayload(major, newExam))
    }).then(() => {
      setNewExam({ ...ADMIN_FORM_DEFAULTS.exam }); setEditingExamId(null); refreshAdminData();
    }).catch(reportAdminError);
  };

  const createScoreLine = () => {
    const major = selectedMajor(newScore.majorId);
    if (!major || !newScore.totalScore || !hasEvidenceSource(newScore) || !validYear(newScore.year) || !validOptionalNumber(newScore.totalScore, 0, 500)) return;
    adminFetch(editingScoreId ? `/api/score-lines/${editingScoreId}` : '/api/score-lines', {
      method: editingScoreId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(scoreLinePayload(major, newScore))
    }).then(() => {
      setNewScore({ ...ADMIN_FORM_DEFAULTS.score }); setEditingScoreId(null); refreshAdminData();
    }).catch(reportAdminError);
  };

  const createAdmissionResult = () => {
    const major = selectedMajor(newAdmissionResult.majorId);
    if (!major || !hasEvidenceSource(newAdmissionResult) || !validYear(newAdmissionResult.year)
      || !validOptionalNumber(newAdmissionResult.admittedCount, 0, 100000)
      || ![newAdmissionResult.lowestScore, newAdmissionResult.averageScore, newAdmissionResult.highestScore].every((value) => validOptionalNumber(value, 0, 500))
      || !validOptionalNumber(newAdmissionResult.retestRatio, 0, 10)) return;
    adminFetch(editingAdmissionResultId ? `/api/admission-results/${editingAdmissionResultId}` : '/api/admission-results', {
      method: editingAdmissionResultId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(admissionResultPayload(major, newAdmissionResult))
    }).then(() => {
      setNewAdmissionResult({ ...ADMIN_FORM_DEFAULTS.admissionResult }); setEditingAdmissionResultId(null); refreshAdminData();
    }).catch(reportAdminError);
  };

  const createRetestRule = () => {
    const major = newRetestRule.scopeType === 'MAJOR' ? selectedMajor(newRetestRule.majorId) ?? null : null;
    const schoolId = Number(newRetestRule.schoolId) || major?.schoolId;
    if (!schoolId || (newRetestRule.scopeType === 'MAJOR' && !major)
      || !hasEvidenceSource(newRetestRule) || !validYear(newRetestRule.year)
      || !validOptionalNumber(newRetestRule.retestRatio, 0, 10)
      || !validRetestWeights(newRetestRule.initialScoreWeight, newRetestRule.retestScoreWeight)) return;
    adminFetch(editingRetestRuleId ? `/api/retest-rules/${editingRetestRuleId}` : '/api/retest-rules', {
      method: editingRetestRuleId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(retestRulePayload(major, newRetestRule))
    }).then(() => {
      setNewRetestRule({ ...ADMIN_FORM_DEFAULTS.retestRule }); setEditingRetestRuleId(null); refreshAdminData();
    }).catch(reportAdminError);
  };

  const createReferenceBook = () => {
    const major = selectedMajor(newReferenceBook.majorId);
    if (!major || !newReferenceBook.bookTitle.trim() || !hasEvidenceSource(newReferenceBook) || !validYear(newReferenceBook.year)) return;
    adminFetch(editingReferenceBookId ? `/api/reference-books/${editingReferenceBookId}` : '/api/reference-books', {
      method: editingReferenceBookId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(referenceBookPayload(major, newReferenceBook))
    }).then(() => {
      setNewReferenceBook({ ...ADMIN_FORM_DEFAULTS.referenceBook }); setEditingReferenceBookId(null); refreshAdminData();
    }).catch(reportAdminError);
  };

  const createAdjustmentInfo = () => {
    const major = selectedMajor(newAdjustmentInfo.majorId);
    if (!major || !newAdjustmentInfo.title.trim() || !hasEvidenceSource(newAdjustmentInfo)
      || !validYear(newAdjustmentInfo.year) || !validOptionalNumber(newAdjustmentInfo.vacancyCount, 0, 100000)) return;
    adminFetch(editingAdjustmentInfoId ? `/api/adjustment-infos/${editingAdjustmentInfoId}` : '/api/adjustment-infos', {
      method: editingAdjustmentInfoId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(adjustmentInfoPayload(major, newAdjustmentInfo))
    }).then(() => {
      setNewAdjustmentInfo({ ...ADMIN_FORM_DEFAULTS.adjustmentInfo }); setEditingAdjustmentInfoId(null); refreshAdminData();
    }).catch(reportAdminError);
  };

  const cancelEditing = () => {
    resetAdminForms();
    setDocumentChunks([]);
    setChunkSearchResults([]);
    setDocumentVersions([]);
    setVersionDocumentId(null);
    setVersionMessage('');
  };

  const deleteResource = (path: string, id: number) => {
    if (!window.confirm('确定删除这条数据？')) return;
    adminFetch(`/api/${path}/${id}`, { method: 'DELETE' }).then(refreshAdminData).catch(reportAdminError);
  };

  const editSchool = (school: School) => {
    cancelEditing();
    setEditingSchoolId(school.id);
    setNewSchool({ name: school.name, province: school.province ?? '', city: school.city ?? '', region: provinceToRegion(school.province ?? ''), schoolLevel: school.schoolLevel ?? '普通院校' });
  };

  const editCollege = (college: College) => {
    cancelEditing();
    setEditingCollegeId(college.id);
    setNewCollege({ schoolId: String(college.schoolId), name: college.name ?? '', officialSite: college.officialSite ?? '' });
  };

  const editMajor = (major: Major) => {
    cancelEditing();
    setEditingMajorId(major.id);
    setNewMajor({
      schoolId: String(major.schoolId), collegeId: String(major.collegeId), name: major.name ?? '',
      majorCode: major.majorCode ?? '', degreeType: major.degreeType ?? '专硕',
      researchDirection: major.researchDirection ?? '', studyMode: major.studyMode ?? '全日制'
    });
  };

  const editSource = (source: DocumentSource) => {
    cancelEditing();
    setEditingSourceId(source.id);
    setNewSource({
      schoolId: source.schoolId ? String(source.schoolId) : '', collegeId: source.collegeId ? String(source.collegeId) : '',
      title: source.title ?? '', sourceType: source.sourceType ?? '招生专业目录', sourceUrl: source.sourceUrl ?? '',
      publishDate: source.publishDate ?? '', year: source.year == null ? '' : String(source.year), official: source.official ?? true,
      auditStatus: source.auditStatus ?? 'PUBLISHED', remark: source.remark ?? ''
    });
  };

  const editSourceDocument = (document: SourceDocument) => {
    cancelEditing();
    setEditingSourceDocumentId(document.id);
    adminFetch(`/api/source-documents/${document.id}`, {})
      .then((response) => readResponseJson<SourceDocument>(response))
      .then((payload) => {
        const detail = payload.data;
        setNewSourceDocument({
          schoolId: detail.schoolId ? String(detail.schoolId) : '', collegeId: detail.collegeId ? String(detail.collegeId) : '',
          majorId: detail.majorId ? String(detail.majorId) : '', title: detail.title ?? '',
          documentType: detail.documentType ?? '招生专业目录', sourceUrl: detail.sourceUrl ?? '',
          year: detail.year == null ? '' : String(detail.year), auditStatus: detail.auditStatus === 'PUBLISHED' ? 'PENDING' : detail.auditStatus ?? 'DRAFT',
          sourceReliability: detail.sourceReliability ?? 'UNKNOWN', rawText: detail.rawText ?? '', remark: detail.remark ?? ''
        });
      })
      .catch(reportAdminError);
    previewDocumentChunks(document.id);
    loadDocumentVersions(document.id);
  };

  const rollbackSourceDocument = (documentId: number, versionNo: number) => {
    if (!window.confirm(`确定恢复到 v${versionNo}？当前内容会作为新的回滚版本保留。`)) return;
    adminFetch(`/api/source-documents/${documentId}/versions/${versionNo}/rollback`, { method: 'POST' })
      .then((response) => readResponseJson<SourceDocumentRollbackResult>(response))
      .then((payload) => {
        setVersionMessage(`已恢复 v${payload.data.restoredVersionNo}，生成 v${payload.data.createdVersionNo}，重建 ${payload.data.chunkCount} 个切片`);
        loadSourceDocuments(true);
        previewDocumentChunks(documentId);
        loadDocumentVersions(documentId, false);
        setEditingSourceDocumentId(documentId);
        const document = payload.data.document;
        setNewSourceDocument({
          schoolId: document.schoolId ? String(document.schoolId) : '', collegeId: document.collegeId ? String(document.collegeId) : '',
          majorId: document.majorId ? String(document.majorId) : '', title: document.title ?? '',
          documentType: document.documentType ?? '招生专业目录', sourceUrl: document.sourceUrl ?? '',
          year: document.year == null ? '' : String(document.year), auditStatus: document.auditStatus ?? 'DRAFT',
          sourceReliability: document.sourceReliability ?? 'UNKNOWN', rawText: document.rawText ?? '', remark: document.remark ?? ''
        });
      })
      .catch(reportAdminError);
  };

  const editPlan = (plan: AdmissionPlan) => {
    cancelEditing(); setEditingPlanId(plan.id);
    setNewPlan({ majorId: String(plan.majorId), sourceId: String(plan.sourceId ?? ''), year: String(plan.year ?? 2026), totalQuota: String(plan.totalQuota ?? ''), recommendedQuota: String(plan.recommendedQuota ?? ''), unifiedQuota: String(plan.unifiedQuota ?? ''), remark: plan.remark ?? '' });
  };

  const editExam = (exam: ExamSubject) => {
    cancelEditing(); setEditingExamId(exam.id);
    setNewExam({ majorId: String(exam.majorId), sourceId: String(exam.sourceId ?? ''), year: String(exam.year ?? 2026), professionalSubject: exam.professionalSubject ?? '', is408: exam.is408 });
  };

  const editScore = (score: ScoreLine) => {
    cancelEditing(); setEditingScoreId(score.id);
    setNewScore({ majorId: String(score.majorId), sourceId: String(score.sourceId ?? ''), year: String(score.year ?? 2026), totalScore: String(score.totalScore ?? ''), remark: score.remark ?? '' });
  };

  const editAdmissionResult = (result: AdmissionResult) => {
    cancelEditing(); setEditingAdmissionResultId(result.id);
    setNewAdmissionResult({
      majorId: String(result.majorId), sourceId: String(result.sourceId ?? ''), year: String(result.year ?? 2026),
      admittedCount: String(result.admittedCount ?? ''), lowestScore: String(result.lowestScore ?? ''),
      averageScore: String(result.averageScore ?? ''), highestScore: String(result.highestScore ?? ''),
      retestRatio: String(result.retestRatio ?? ''), remark: result.remark ?? ''
    });
  };

  const editRetestRule = (rule: RetestRule) => {
    cancelEditing(); setEditingRetestRuleId(rule.id);
    setNewRetestRule({
      scopeType: rule.scopeType === 'MAJOR' ? 'MAJOR' : 'SCHOOL', schoolId: String(rule.schoolId),
      majorId: rule.majorId == null ? '' : String(rule.majorId), sourceId: String(rule.sourceId ?? ''), year: String(rule.year ?? 2026),
      retestTime: rule.retestTime ?? '', retestMethod: rule.retestMethod ?? '', retestRatio: String(rule.retestRatio ?? ''),
      initialScoreWeight: String(rule.initialScoreWeight ?? ''), retestScoreWeight: String(rule.retestScoreWeight ?? ''),
      qualificationLine: rule.qualificationLine ?? '', materials: rule.materials ?? '', remark: rule.remark ?? ''
    });
  };

  const editReferenceBook = (book: ReferenceBook) => {
    cancelEditing(); setEditingReferenceBookId(book.id);
    setNewReferenceBook({
      majorId: String(book.majorId), sourceId: String(book.sourceId ?? ''), year: String(book.year ?? 2026),
      subjectName: book.subjectName ?? '', bookTitle: book.bookTitle ?? '', author: book.author ?? '',
      edition: book.edition ?? '', publisher: book.publisher ?? '', remark: book.remark ?? ''
    });
  };

  const editAdjustmentInfo = (item: AdjustmentInfo) => {
    cancelEditing(); setEditingAdjustmentInfoId(item.id);
    setNewAdjustmentInfo({
      majorId: String(item.majorId), sourceId: String(item.sourceId ?? ''), year: String(item.year ?? 2026),
      title: item.title ?? '', open: item.open, vacancyCount: String(item.vacancyCount ?? ''),
      applicationWindow: item.applicationWindow ?? '', requirements: item.requirements ?? '',
      noticeUrl: item.noticeUrl ?? '', remark: item.remark ?? ''
    });
  };

  return {
    adminToken,
    adminRole,
    isAdminLoggedIn,
    sources,
    sourceDocuments,
    coverageReport,
    catalog408Status,
    loadPublicData,
    loadAdminData,
    pageProps: {
      loggedIn: isAdminLoggedIn,
      adminToken,
      adminName,
      adminRole,
      loginForm,
      loginError,
      passwordForm,
      passwordError,
      feedback: adminFeedback,
      onLoginFormChange: (patch: Partial<typeof loginForm>) => setLoginForm((current) => ({ ...current, ...patch })),
      onPasswordFormChange: (patch: Partial<typeof passwordForm>) => setPasswordForm((current) => ({ ...current, ...patch })),
      onLogin: loginAdmin,
      onLogout: logoutAdmin,
      onChangePassword: changeAdminPassword,
      onDismissFeedback: () => setAdminFeedback(null),
      data: {
        schools, colleges, majors, admissionPlans, examSubjects, scoreLines, admissionResults, collectionTasks,
        retestRules, referenceBooks, adjustmentInfos, sources, sourceDocuments, documentChunks, chunkSearchResults,
        documentParseTasks, webCaptureTasks, webCaptureChanges, webCaptureChangeSummary, webCaptureSchedules, publicationBatches,
        coverageReport
      },
      forms,
      documentState: {
        parseMessage, batchImportText, batchImportMessage, batchQualityReport, chunkKeyword,
        webCaptureTargetId, webCaptureMessage, webChangeReviewNote, webChangeMessage, webCaptureScheduleMessage,
        selectedPublicationIds, publicationReason, publicationMessage,
        documentVersions, versionDocumentId, versionMessage,
        setBatchImportText, setBatchQualityReport, setChunkKeyword, setWebCaptureTargetId,
        setWebChangeReviewNote, setPublicationReason
      },
      actions: {
        createSchool, createCollege, createMajor, createSource, saveSourceDocument, importSourceDocumentFile, captureSourceDocumentWeb,
        qualityCheckSourceDocuments, batchImportSourceDocuments, searchDocumentChunks, createAdmissionPlan,
        createExamSubject, createScoreLine, createAdmissionResult, createRetestRule, createReferenceBook,
        createAdjustmentInfo, editSchool, editCollege, editMajor, editSource, editSourceDocument, editPlan,
        editExam, editScore, editAdmissionResult, editRetestRule, editReferenceBook, editAdjustmentInfo,
        deleteResource, cancelEditing, loadDocumentVersions, rollbackSourceDocument, updateCollectionTask,
        createCollectionTarget, updateCollectionTarget, deleteCollectionTarget,
        discoverCollectionTargetLinks, acceptCollectionTargetLink,
        togglePublicationDocument, publishDocumentBatch, rollbackDocumentBatch, reviewWebCaptureChange,
        configureWebCaptureSchedule, runDueWebCaptureSchedules
      }
    }
  };
}
