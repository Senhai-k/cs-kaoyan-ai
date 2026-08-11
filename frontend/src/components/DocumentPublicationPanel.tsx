import { PackageCheck, RotateCcw } from 'lucide-react';
import { auditLabel, formatDateTime } from '../formatters';
import type { DocumentPublicationBatch, SourceDocument } from '../types';

export function DocumentPublicationPanel({ documents, batches, selectedIds, reason, message, canManage, onToggle, onReasonChange, onPublish, onRollback }: {
  documents: SourceDocument[];
  batches: DocumentPublicationBatch[];
  selectedIds: number[];
  reason: string;
  message: string;
  canManage: boolean;
  onToggle: (documentId: number) => void;
  onReasonChange: (reason: string) => void;
  onPublish: () => void;
  onRollback: (batchId: number) => void;
}) {
  const candidates = documents.filter((document) => document.auditStatus === 'DRAFT' || document.auditStatus === 'PENDING');
  return <section className="publication-batch-panel">
    <div className="publication-panel-head">
      <span><PackageCheck size={17} /><strong>审核发布批次</strong></span>
      <em>{selectedIds.length} / {candidates.length} 已选择</em>
    </div>
    <div className="publication-candidates">
      {candidates.length === 0 ? <p>当前没有待发布草稿</p> : candidates.map((document) => <label key={document.id}>
        <input type="checkbox" checked={selectedIds.includes(document.id)} disabled={!canManage} onChange={() => onToggle(document.id)} />
        <span><strong>{document.title}</strong><em>{document.year ?? '-'} · {auditLabel(document.auditStatus)} · {document.sourceReliability}</em></span>
      </label>)}
    </div>
    <div className="publication-actions">
      <input aria-label="发布或回滚说明" placeholder="填写发布依据或回滚原因" value={reason} disabled={!canManage} onChange={(event) => onReasonChange(event.target.value)} />
      <button type="button" disabled={!canManage || selectedIds.length === 0 || !reason.trim()} onClick={onPublish}><PackageCheck size={15} />原子发布</button>
    </div>
    {message && <p className="form-hint">{message}</p>}
    <div className="publication-history">
      <div><strong>最近批次</strong><span>{batches.length} 条</span></div>
      {batches.length === 0 ? <p>尚无发布批次</p> : batches.map((batch) => <article key={batch.id}>
        <span className={`publication-status status-${batch.status.toLowerCase()}`}>{batch.status === 'PUBLISHED' ? '已发布' : '已回滚'}</span>
        <div><strong>批次 #{batch.id} · {batch.documentCount} 份</strong><em>{batch.chunkCount} 个发布切片 · {batch.operator}</em></div>
        <time>{formatDateTime(batch.completedAt ?? batch.createdAt)}</time>
        <p>{batch.reason ?? '-'}</p>
        {batch.status === 'PUBLISHED' && <button type="button" title="回滚整个发布批次" disabled={!canManage || !reason.trim()} onClick={() => onRollback(batch.id)}><RotateCcw size={14} /></button>}
        {batch.status === 'ROLLED_BACK' && <small>{batch.rollbackOperator} · {batch.rollbackChunkCount ?? 0} 个恢复切片 · {batch.rollbackReason}</small>}
      </article>)}
    </div>
  </section>;
}
