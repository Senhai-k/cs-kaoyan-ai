import { Check, FileDiff, X } from 'lucide-react';
import { formatDateTime } from '../formatters';
import type { WebCaptureChange, WebCaptureChangeSummary } from '../types';

const statusLabel = (status: WebCaptureChange['status']) => status === 'PENDING_REVIEW' ? '待复核' : status === 'ACKNOWLEDGED' ? '已确认' : '已忽略';
const waitLabel = (seconds: number) => {
  if (seconds < 3600) return seconds === 0 ? '无积压' : '不足 1 小时';
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} 小时`;
  return `${Math.floor(seconds / 86400)} 天`;
};

export function WebCaptureChangePanel({ changes, summary, reviewNote, message, canReview, onReviewNoteChange, onReview }: {
  changes: WebCaptureChange[];
  summary: WebCaptureChangeSummary | null;
  reviewNote: string;
  message: string;
  canReview: boolean;
  onReviewNoteChange: (note: string) => void;
  onReview: (changeId: number, status: 'ACKNOWLEDGED' | 'IGNORED') => void;
}) {
  const pendingCount = summary?.pendingCount ?? changes.filter((item) => item.status === 'PENDING_REVIEW').length;
  const reviewedCount = summary ? summary.acknowledgedCount + summary.ignoredCount : changes.length - pendingCount;
  return <section className="web-change-panel">
    <div className="web-change-head"><span><FileDiff size={17} /><strong>官网内容变化</strong></span><em>{pendingCount} 条待复核</em></div>
    <div className="web-change-metrics" aria-label="官网变化运营摘要">
      <span><strong>{summary?.totalCount ?? changes.length}</strong><em>累计变化</em></span>
      <span><strong>{reviewedCount}</strong><em>已处理</em></span>
      <span><strong>{Math.round((summary?.maxChangeRatio ?? 0) * 100)}%</strong><em>最大变化</em></span>
      <span><strong>{waitLabel(summary?.oldestPendingAgeSeconds ?? 0)}</strong><em>最久等待</em></span>
    </div>
    <input aria-label="网页变更复核说明" placeholder="填写确认依据或忽略原因" value={reviewNote} disabled={!canReview} onChange={(event) => onReviewNoteChange(event.target.value)} />
    {message && <p className="form-hint">{message}</p>}
    <div className="web-change-list">
      {changes.length === 0 ? <p>尚未检测到正文变化</p> : changes.map((change) => <article key={change.id}>
        <div className="web-change-summary">
          <span className={`web-change-status status-${change.status.toLowerCase()}`}>{statusLabel(change.status)}</span>
          <strong>目标 #{change.targetId} · 变化 {Math.round(change.changeRatio * 100)}%</strong>
          <em>新增 {change.addedLineCount} 段 · 删除 {change.removedLineCount} 段 · {formatDateTime(change.detectedAt)}</em>
        </div>
        <details>
          <summary>查看正文差异</summary>
          <div className="web-change-diff">
            <section><strong>原内容</strong><p>{change.previousExcerpt || '无可展示删除片段'}</p></section>
            <section><strong>新内容</strong><p>{change.currentExcerpt || '无可展示新增片段'}</p></section>
          </div>
        </details>
        {change.status === 'PENDING_REVIEW' ? <div className="web-change-actions">
          <button type="button" disabled={!canReview || !reviewNote.trim()} onClick={() => onReview(change.id, 'ACKNOWLEDGED')}><Check size={14} />确认变化</button>
          <button type="button" className="secondary-button" disabled={!canReview || !reviewNote.trim()} onClick={() => onReview(change.id, 'IGNORED')}><X size={14} />忽略噪声</button>
        </div> : <small>{change.reviewer} · {change.reviewNote} · {formatDateTime(change.reviewedAt ?? undefined)}</small>}
      </article>)}
    </div>
  </section>;
}
