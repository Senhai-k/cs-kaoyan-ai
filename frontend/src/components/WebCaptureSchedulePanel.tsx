import { Clock3, Play, Save } from 'lucide-react';
import { useEffect, useState } from 'react';
import { formatDateTime } from '../formatters';
import type { DataCollectionTarget, WebCaptureSchedule } from '../types';

export function WebCaptureSchedulePanel({ schedules, targets, message, canManage, onSave, onRunDue }: {
  schedules: WebCaptureSchedule[];
  targets: DataCollectionTarget[];
  message: string;
  canManage: boolean;
  onSave: (targetId: number, enabled: boolean, intervalHours: number) => Promise<void>;
  onRunDue: () => Promise<void>;
}) {
  const [targetId, setTargetId] = useState('');
  const [enabled, setEnabled] = useState(false);
  const [intervalHours, setIntervalHours] = useState(24);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const current = schedules.find((item) => item.targetId === Number(targetId));
    setEnabled(current?.enabled ?? false);
    setIntervalHours(current?.intervalHours ?? 24);
  }, [targetId, schedules]);

  const save = () => {
    if (!targetId || intervalHours < 6 || intervalHours > 720) return;
    setSaving(true);
    onSave(Number(targetId), enabled, intervalHours).finally(() => setSaving(false));
  };

  return <section className="web-monitor-panel">
    <div className="web-monitor-head"><span><Clock3 size={17} /><strong>官网定时监测</strong></span><em>{schedules.filter((item) => item.enabled).length} 个启用</em></div>
    <div className="web-monitor-controls">
      <select aria-label="官网监测目标" value={targetId} disabled={!canManage} onChange={(event) => setTargetId(event.target.value)}>
        <option value="">选择已登记精确公告</option>
        {targets.map((target) => <option key={target.id} value={target.id}>{target.targetYear} · {target.title}</option>)}
      </select>
      <label><span>间隔</span><input aria-label="监测间隔小时" type="number" min="6" max="720" value={intervalHours} disabled={!canManage} onChange={(event) => setIntervalHours(Number(event.target.value))} /><em>小时</em></label>
      <label className="web-monitor-toggle"><input type="checkbox" checked={enabled} disabled={!canManage || !targetId} onChange={(event) => setEnabled(event.target.checked)} /><span>启用监测</span></label>
      <button type="button" disabled={!canManage || !targetId || saving || intervalHours < 6 || intervalHours > 720} onClick={save}><Save size={14} />{saving ? '保存中' : '保存配置'}</button>
      <button type="button" className="secondary-button" disabled={!canManage} onClick={onRunDue}><Play size={14} />执行到期任务</button>
    </div>
    {message && <p className="form-hint">{message}</p>}
    <div className="web-monitor-list">
      {schedules.length === 0 ? <p>尚未配置定时监测</p> : schedules.map((item) => <article key={item.targetId}>
        <span className={`web-monitor-state ${item.enabled ? 'is-enabled' : ''}`}>{item.enabled ? '监测中' : '已停用'}</span>
        <div><strong>{item.targetTitle}</strong><em>每 {item.intervalHours} 小时 · 下次 {formatDateTime(item.nextRunAt)}</em></div>
        <div><span>{item.lastStatus ?? '尚未执行'}{item.consecutiveFailures ? ` · 连续失败 ${item.consecutiveFailures}` : ''}</span><time>{formatDateTime(item.lastFinishedAt ?? undefined)}</time></div>
        {item.lastError && <p>{item.lastError}</p>}
      </article>)}
    </div>
  </section>;
}
