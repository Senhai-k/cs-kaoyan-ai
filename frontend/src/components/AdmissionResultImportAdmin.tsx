import { Eye, FileCheck2, Send } from 'lucide-react';
import { useEffect, useState } from 'react';
import { parseAdmissionResultImport } from '../admissionResultImport';
import { requestJson } from '../api';
import { formatDateTime } from '../formatters';
import type { AdminRole, AdmissionResultImportBatch, AdmissionResultImportDraft, AdmissionResultImportPreview, AdmissionResultImportPublishResult, AdmissionResultImportRequest, School } from '../types';

export function AdmissionResultImportAdmin({ token, role, schools }: { token: string; role: AdminRole | ''; schools: School[] }) {
  const [request, setRequest] = useState<AdmissionResultImportRequest | null>(null);
  const [filename, setFilename] = useState('');
  const [preview, setPreview] = useState<AdmissionResultImportPreview | null>(null);
  const [batches, setBatches] = useState<AdmissionResultImportBatch[]>([]);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const headers = { Authorization: `Bearer ${token}` };
  const canDraft = role === 'ADMIN' || role === 'DATA_EDITOR';

  const loadBatches = () => requestJson<AdmissionResultImportBatch[]>('/api/admission-result-imports', { headers })
    .then((response) => setBatches(response.data ?? []))
    .catch((error: Error) => setMessage(error.message));

  useEffect(() => { if (token) loadBatches(); }, [token]);

  const chooseFile = async (file: File | null) => {
    setPreview(null);
    setMessage('');
    setRequest(null);
    setFilename(file?.name ?? '');
    if (!file) return;
    try {
      const parsed = parseAdmissionResultImport(await file.text());
      setRequest(parsed);
      setMessage(`已读取 ${parsed.records.length} 条匿名记录`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '匿名批次读取失败');
    }
  };

  const submit = async (mode: 'preview' | 'draft') => {
    if (!request) return;
    setBusy(true);
    setMessage('');
    try {
      if (mode === 'preview') {
        const response = await requestJson<AdmissionResultImportPreview>('/api/admission-result-imports/preview', {
          method: 'POST', headers: { ...headers, 'Content-Type': 'application/json' }, body: JSON.stringify(request)
        });
        setPreview(response.data);
        setMessage(response.data.publishable ? '预览通过，可建立草稿' : '预览完成，存在不可发布分组');
      } else {
        const response = await requestJson<AdmissionResultImportDraft>('/api/admission-result-imports', {
          method: 'POST', headers: { ...headers, 'Content-Type': 'application/json' }, body: JSON.stringify(request)
        });
        setPreview(response.data.preview);
        setMessage(response.data.existing ? '该批次草稿已存在' : '草稿已建立');
        await loadBatches();
      }
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '导入请求失败');
    } finally {
      setBusy(false);
    }
  };

  const publish = async (batchId: number) => {
    setBusy(true);
    setMessage('');
    try {
      const response = await requestJson<AdmissionResultImportPublishResult>(`/api/admission-result-imports/${batchId}/publish`, {
        method: 'POST', headers
      });
      setMessage(`已发布 ${response.data.admissionResultsCreated} 条专业录取结果`);
      await loadBatches();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '发布失败');
    } finally {
      setBusy(false);
    }
  };

  const schoolName = (schoolId: number) => schools.find((school) => school.id === schoolId)?.name ?? `学校 ${schoolId}`;
  return <section id="admin-admission-import" className="panel admin-block admission-import-panel">
    <div className="admission-import-heading">
      <div><h3>拟录取名单导入</h3><span>{batches.filter((batch) => batch.status === 'DRAFT').length} 个待发布草稿</span></div>
      <button type="button" className="secondary-button" onClick={loadBatches}>刷新批次</button>
    </div>

    {canDraft && <div className="file-import admission-import-file">
      <label>匿名批次 JSON<input aria-label="匿名批次 JSON" type="file" accept="application/json,.json" onChange={(event) => chooseFile(event.target.files?.[0] ?? null)} /></label>
      {filename && <span>{filename}</span>}
      <div className="admission-import-actions">
        <button type="button" disabled={!request || busy} onClick={() => submit('preview')}><Eye size={16} />预览映射</button>
        <button type="button" disabled={!request || busy} onClick={() => submit('draft')}><FileCheck2 size={16} />建立草稿</button>
      </div>
    </div>}

    {message && <p className="admission-import-message" role="status">{message}</p>}
    {preview && <div className="admission-import-preview">
      <div className="admission-import-summary">
        <strong>{preview.inputRecords}</strong><span>匿名记录</span>
        <strong>{preview.mappedGroupCount}/{preview.groupCount}</strong><span>专业已匹配</span>
        <strong>{preview.publishable ? '可发布' : '需处理'}</strong><span>批次状态</span>
      </div>
      <div className="admission-import-groups">{preview.groups.map((group) => <div key={group.groupKey} className="admission-import-group">
        <div><strong>{group.collegeName} · {group.majorCode}</strong><span className={`mapping-status is-${group.mappingStatus.toLowerCase()}`}>{group.mappingStatus === 'MATCHED' ? '已匹配' : '待处理'}</span></div>
        <p>{group.degreeType} · {group.studyMode} · 录取 {group.admittedCount} 人 · 分数覆盖 {group.scoreCoverageCount}/{group.admittedCount}</p>
        <small>{group.mappingMessage}</small>
      </div>)}</div>
    </div>}

    <div className="admission-import-batches">{batches.length === 0 ? <div className="empty-state">暂无导入批次</div> : batches.map((batch) => <div key={batch.id} className="admission-import-row">
      <div><strong>{schoolName(batch.schoolId)} · {batch.year}</strong><span>{batch.inputRecords} 条 / {batch.mappedGroupCount} of {batch.groupCount} 组已匹配</span></div>
      <div><span className={`mapping-status is-${batch.status.toLowerCase()}`}>{batch.status === 'PUBLISHED' ? '已发布' : '草稿'}</span><time>{formatDateTime(batch.createdAt)}</time></div>
      {role === 'ADMIN' && batch.status === 'DRAFT' && <button type="button" disabled={busy || batch.mappedGroupCount !== batch.groupCount} onClick={() => publish(batch.id)}><Send size={15} />发布结果</button>}
    </div>)}</div>
  </section>;
}
