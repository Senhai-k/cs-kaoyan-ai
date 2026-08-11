import { Globe2, History, Plus, RotateCcw, Search } from 'lucide-react';
import { useDeferredValue, useEffect, useState } from 'react';
import type { SourceDocumentForm } from '../adminForms';
import { auditLabel, formatDateTime } from '../formatters';
import { canSaveSourceDocumentDraft } from '../sourceDocumentBatch';
import type { College, DataCollectionTarget, DocumentChunk, DocumentParseTask, DocumentPublicationBatch, Major, School, SourceDocument, SourceDocumentQualityReport, SourceDocumentVersion, WebCaptureChange, WebCaptureChangeSummary, WebCaptureSchedule, WebCaptureTask } from '../types';
import { ChunkList, CollegeSelect, MajorSelect, SchoolSelect } from './AdminComponents';
import { DocumentPublicationPanel } from './DocumentPublicationPanel';
import { WebCaptureChangePanel } from './WebCaptureChangePanel';
import { WebCaptureSchedulePanel } from './WebCaptureSchedulePanel';

export function SourceDocumentAdmin({ documents, chunks, searchResults, parseTasks, webCaptureTasks, webCaptureChanges, webCaptureChangeSummary, webCaptureSchedules, publicationBatches, captureTargets, schools, colleges, majors, form, editingId, parseMessage, webCaptureTargetId, webCaptureMessage, webChangeReviewNote, webChangeMessage, webCaptureScheduleMessage, canReviewWebChanges, canManageWebSchedules, selectedPublicationIds, publicationReason, publicationMessage, canManagePublications, batchText, batchMessage, qualityReport, chunkKeyword, versions, versionDocumentId, versionMessage, onFormChange, onImportFile, onWebCaptureTargetChange, onCaptureWeb, onWebChangeReviewNoteChange, onReviewWebChange, onConfigureWebSchedule, onRunDueWebSchedules, onTogglePublication, onPublicationReasonChange, onPublishBatch, onRollbackBatch, onBatchTextChange, onQualityCheck, onBatchImport, onSave, onCancel, onEdit, onDelete, onViewVersions, onRollback, onChunkKeywordChange, onSearchChunks }: {
  documents: SourceDocument[];
  chunks: DocumentChunk[];
  searchResults: DocumentChunk[];
  parseTasks: DocumentParseTask[];
  webCaptureTasks: WebCaptureTask[];
  webCaptureChanges: WebCaptureChange[];
  webCaptureChangeSummary: WebCaptureChangeSummary | null;
  webCaptureSchedules: WebCaptureSchedule[];
  publicationBatches: DocumentPublicationBatch[];
  captureTargets: DataCollectionTarget[];
  schools: School[];
  colleges: College[];
  majors: Major[];
  form: SourceDocumentForm;
  editingId: number | null;
  parseMessage: string;
  webCaptureTargetId: string;
  webCaptureMessage: string;
  webChangeReviewNote: string;
  webChangeMessage: string;
  webCaptureScheduleMessage: string;
  canReviewWebChanges: boolean;
  canManageWebSchedules: boolean;
  selectedPublicationIds: number[];
  publicationReason: string;
  publicationMessage: string;
  canManagePublications: boolean;
  batchText: string;
  batchMessage: string;
  qualityReport: SourceDocumentQualityReport | null;
  chunkKeyword: string;
  versions: SourceDocumentVersion[];
  versionDocumentId: number | null;
  versionMessage: string;
  onFormChange: (patch: Partial<SourceDocumentForm>) => void;
  onImportFile: (file: File | null) => void;
  onWebCaptureTargetChange: (targetId: string) => void;
  onCaptureWeb: () => void;
  onWebChangeReviewNoteChange: (note: string) => void;
  onReviewWebChange: (changeId: number, status: 'ACKNOWLEDGED' | 'IGNORED') => void;
  onConfigureWebSchedule: (targetId: number, enabled: boolean, intervalHours: number) => Promise<void>;
  onRunDueWebSchedules: () => Promise<void>;
  onTogglePublication: (documentId: number) => void;
  onPublicationReasonChange: (reason: string) => void;
  onPublishBatch: () => void;
  onRollbackBatch: (batchId: number) => void;
  onBatchTextChange: (value: string) => void;
  onQualityCheck: () => void;
  onBatchImport: () => void;
  onSave: () => void;
  onCancel: () => void;
  onEdit: (document: SourceDocument) => void;
  onDelete: (id: number) => void;
  onViewVersions: (documentId: number) => void;
  onRollback: (documentId: number, versionNo: number) => void;
  onChunkKeywordChange: (value: string) => void;
  onSearchChunks: () => void;
}) {
  const update = (patch: Partial<SourceDocumentForm>) => onFormChange(patch);
  const [documentKeyword, setDocumentKeyword] = useState('');
  const [documentLimit, setDocumentLimit] = useState(50);
  const [workspaceView, setWorkspaceView] = useState<'library' | 'editor' | 'operations' | 'search'>('library');
  const deferredDocumentKeyword = useDeferredValue(documentKeyword.trim().toLocaleLowerCase());
  const filteredDocuments = deferredDocumentKeyword
    ? documents.filter((document) => `${document.year ?? ''} ${document.title}`.toLocaleLowerCase().includes(deferredDocumentKeyword))
    : documents;
  const visibleDocuments = filteredDocuments.slice(0, documentLimit);
  useEffect(() => {
    if (editingId) setWorkspaceView('editor');
  }, [editingId]);

  return <section id="admin-documents" className="panel admin-block rag-block">
    <div className="panel-header"><h2>资料文档知识库</h2><span>{documents.length} 份资料</span></div>
    <nav className="admin-workspace-tabs" aria-label="知识资料工作区">
      <button type="button" className={workspaceView === 'library' ? 'active' : ''} onClick={() => setWorkspaceView('library')}>文档列表</button>
      <button type="button" className={workspaceView === 'editor' ? 'active' : ''} onClick={() => setWorkspaceView('editor')}>导入与编辑</button>
      <button type="button" className={workspaceView === 'operations' ? 'active' : ''} onClick={() => setWorkspaceView('operations')}>采集与发布</button>
      <button type="button" className={workspaceView === 'search' ? 'active' : ''} onClick={() => setWorkspaceView('search')}>切片检索</button>
    </nav>

    {workspaceView === 'editor' && <div className="rag-editor rag-editor-single">
      <div className="admin-form">
        <label className="file-import"><span>导入文本资料</span><input type="file" accept=".pdf,.txt,.md,.csv,application/pdf,text/plain,text/markdown,text/csv" onChange={(event) => onImportFile(event.target.files?.[0] ?? null)} /></label>
        {parseMessage && <p className="form-hint">{parseMessage}</p>}
        <div className="batch-import">
          <textarea placeholder="批量 JSON 导入，多份资料使用数组格式" value={batchText} onChange={(event) => onBatchTextChange(event.target.value)} />
          <button type="button" disabled={!batchText.trim()} onClick={onQualityCheck}>预检 JSON</button>
          <button type="button" disabled={!batchText.trim() || qualityReport?.importable === false} onClick={onBatchImport}>批量导入并生成切片</button>
          {batchMessage && <p className="form-hint">{batchMessage}</p>}
          {qualityReport && <div className={`quality-report ${qualityReport.errorCount > 0 ? 'has-errors' : qualityReport.warningCount > 0 ? 'has-warnings' : 'is-clean'}`}>
            <div className="quality-report-head"><strong>{qualityReport.publishable ? '可发布' : qualityReport.importable ? '可导入，待核验' : '不可导入'}</strong><span>{qualityReport.totalCount} 份资料 · {qualityReport.errorCount} 个错误 · {qualityReport.warningCount} 个警告</span></div>
            {qualityReport.issues.length > 0 && <ul>{qualityReport.issues.map((issue, index) => <li key={`${issue.index}-${issue.field}-${index}`}><strong>第 {issue.index + 1} 项 · {issue.level}</strong><span>{issue.field}：{issue.message}</span></li>)}</ul>}
          </div>}
        </div>
        <SchoolSelect schools={schools} value={form.schoolId} onChange={(schoolId) => update({ schoolId, collegeId: '', majorId: '' })} />
        <CollegeSelect colleges={colleges} schoolId={form.schoolId} value={form.collegeId} onChange={(collegeId) => update({ collegeId })} />
        <MajorSelect majors={form.schoolId ? majors.filter((major) => major.schoolId === Number(form.schoolId)) : []} value={form.majorId} onChange={(majorId) => update({ majorId })} />
        <input placeholder="资料标题" value={form.title} onChange={(event) => update({ title: event.target.value })} />
        <input placeholder="资料类型" value={form.documentType} onChange={(event) => update({ documentType: event.target.value })} />
        <input placeholder="资料链接" value={form.sourceUrl} onChange={(event) => update({ sourceUrl: event.target.value })} />
        <input placeholder="年份" value={form.year} onChange={(event) => update({ year: event.target.value })} />
        <select value={form.auditStatus} onChange={(event) => update({ auditStatus: event.target.value })}><option value="DRAFT">草稿</option><option value="PENDING">待审核</option></select>
        <select value={form.sourceReliability} onChange={(event) => update({ sourceReliability: event.target.value })}><option value="UNKNOWN">来源待核验</option><option value="OFFICIAL">官方来源</option><option value="VERIFIED">人工核验</option><option value="THIRD_PARTY">第三方整理</option></select>
        <textarea placeholder="粘贴招生简章、专业目录、复试细则等原文" value={form.rawText} onChange={(event) => update({ rawText: event.target.value })} />
        <input placeholder="备注" value={form.remark} onChange={(event) => update({ remark: event.target.value })} />
        <button type="button" disabled={!canSaveSourceDocumentDraft(form)} onClick={onSave}><Plus size={16} />{editingId ? '保存并重建切片' : '新增资料并生成切片'}</button>
        {editingId && <button type="button" className="secondary-button" onClick={onCancel}>取消编辑</button>}
      </div>
    </div>}

    {workspaceView === 'operations' && <div className="rag-side rag-operations-grid">
        <div className="web-capture-operation">
          <div className="web-capture-control">
            <select aria-label="官方网页采集目标" value={webCaptureTargetId} onChange={(event) => onWebCaptureTargetChange(event.target.value)}>
              <option value="">选择官方 URL 待办</option>
              {captureTargets.map((target) => <option key={target.id} value={target.id}>{target.targetYear} · {target.title} · {target.status}</option>)}
            </select>
            <button type="button" disabled={!webCaptureTargetId} onClick={onCaptureWeb}><Globe2 size={16} />抓取网页草稿</button>
          </div>
          {webCaptureMessage && <p className="form-hint">{webCaptureMessage}</p>}
        </div>
        <DocumentPublicationPanel documents={documents} batches={publicationBatches} selectedIds={selectedPublicationIds}
          reason={publicationReason} message={publicationMessage} canManage={canManagePublications}
          onToggle={onTogglePublication} onReasonChange={onPublicationReasonChange}
          onPublish={onPublishBatch} onRollback={onRollbackBatch} />
        <div className="parse-task-list web-capture-list">
          <div className="parse-task-head"><strong>最近网页采集</strong><span>{webCaptureTasks.length} 条</span></div>
          {webCaptureTasks.length === 0 ? <div className="version-empty">尚无网页采集记录</div> : webCaptureTasks.map((task) => <article key={task.id}>
            <span className={`parse-task-status status-${task.status.toLowerCase()}`}>{task.status === 'COMPLETED' ? '已完成' : '失败'}</span>
            <div><strong>#{task.id} 目标 {task.targetId}</strong><em>{task.extractorVersion} · HTTP {task.httpStatus ?? '-'} · {task.extractedLength} 字</em></div>
            <div><span>复用 {task.reuseCount}</span><time>{formatDateTime(task.updatedAt)}</time></div>
            <a href={task.finalUrl ?? task.requestedUrl} target="_blank" rel="noreferrer">{task.finalUrl ?? task.requestedUrl}</a>
            {task.errorMessage && <p>{task.errorMessage}</p>}
          </article>)}
        </div>
        <WebCaptureChangePanel changes={webCaptureChanges} summary={webCaptureChangeSummary} reviewNote={webChangeReviewNote}
          message={webChangeMessage} canReview={canReviewWebChanges}
          onReviewNoteChange={onWebChangeReviewNoteChange} onReview={onReviewWebChange} />
        <WebCaptureSchedulePanel schedules={webCaptureSchedules} targets={captureTargets}
          message={webCaptureScheduleMessage} canManage={canManageWebSchedules}
          onSave={onConfigureWebSchedule} onRunDue={onRunDueWebSchedules} />
        <div className="parse-task-list">
          <div className="parse-task-head"><strong>最近解析任务</strong><span>{parseTasks.length} 条</span></div>
          {parseTasks.length === 0 ? <div className="version-empty">尚无文件解析记录</div> : parseTasks.map((task) => <article key={task.id}>
            <span className={`parse-task-status status-${task.status.toLowerCase()}`}>{task.status === 'COMPLETED' ? '已完成' : '失败'}</span>
            <div><strong>#{task.id} {task.originalFilename}</strong><em>{task.parserVersion} · SHA {task.fileSha256.slice(0, 10)} · {task.extractedLength} 字</em></div>
            <div><span>复用 {task.reuseCount}</span><time>{formatDateTime(task.updatedAt)}</time></div>
            {task.errorMessage && <p>{task.errorMessage}</p>}
          </article>)}
        </div>
    </div>}

    {workspaceView === 'library' && <div className="rag-library-view">
        <div className="admin-list-toolbar"><input aria-label="资料文档列表筛选" placeholder="筛选资料标题" value={documentKeyword} onChange={(event) => { setDocumentKeyword(event.target.value); setDocumentLimit(50); }} /><span>显示 {visibleDocuments.length} / {filteredDocuments.length}</span></div>
        <div className="admin-list">{visibleDocuments.map((document) => <div key={document.id}><span>{document.year ?? '-'} {document.title} / {auditLabel(document.auditStatus)}</span><button type="button" title="查看版本历史" onClick={() => onViewVersions(document.id)}><History size={14} /></button><button type="button" onClick={() => { setWorkspaceView('editor'); onEdit(document); }}>编辑</button><button type="button" onClick={() => onDelete(document.id)}>删除</button></div>)}</div>
        {visibleDocuments.length < filteredDocuments.length && <button type="button" className="secondary-button admin-list-more" onClick={() => setDocumentLimit((current) => current + 50)}>显示更多资料</button>}
        {versionDocumentId && <div className="document-version-history">
          <div className="version-history-head"><strong>版本历史</strong><span>资料 #{versionDocumentId} · {versions.length} 个快照</span></div>
          {versionMessage && <p>{versionMessage}</p>}
          {versions.length === 0 ? <div className="version-empty">当前资料尚无版本快照，首次保存后自动建立基线。</div> : <div>{versions.map((version, index) => <article key={version.id}>
            <span><strong>v{version.versionNo}</strong><em>{version.operation} · {version.operator}</em></span>
            <span><strong>{version.title}</strong><em>{version.year ?? '-'} · {auditLabel(version.auditStatus)}</em></span>
            <time>{version.createdAt}</time>
            {index > 0 && <button type="button" title={`恢复到 v${version.versionNo}`} onClick={() => onRollback(version.documentId, version.versionNo)}><RotateCcw size={14} /></button>}
          </article>)}</div>}
        </div>}
    </div>}

    {workspaceView === 'search' && <div className="rag-search-view">
        <div className="chunk-search"><input placeholder="检索切片关键词" value={chunkKeyword} onChange={(event) => onChunkKeywordChange(event.target.value)} /><button type="button" onClick={onSearchChunks}><Search size={16} />检索</button></div>
        <ChunkList title="当前资料切片" chunks={chunks} />
        <ChunkList title="检索结果" chunks={searchResults} />
    </div>}
  </section>;
}
