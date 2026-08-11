import { CalendarDays, Check, ChevronRight, ClipboardList, ExternalLink, LoaderCircle, Plus, Save, Search, Trash2, UserRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { filterCollectionTasks, TARGET_STATUS_LABELS, TASK_HISTORY_LABELS, TASK_STATUS_LABELS, type CollectionTaskFilter } from '../collectionTasks';
import type { DataCollectionTarget, DataCollectionTargetRequest, DataCollectionTask, DataCollectionTaskUpdate, OfficialLinkCandidate } from '../types';

const FILTERS: Array<{ value: CollectionTaskFilter; label: string }> = [
  { value: 'ACTIVE', label: '进行队列' },
  { value: 'OPEN', label: '待处理' },
  { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'BLOCKED', label: '受阻' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'ALL', label: '全部' }
];

function TargetRow({ target, canDiscover, onUpdate, onDelete, onDiscover, onAccept }: {
  target: DataCollectionTarget;
  canDiscover: boolean;
  onUpdate: (targetId: number, patch: DataCollectionTargetRequest) => Promise<void>;
  onDelete: (targetId: number) => Promise<void>;
  onDiscover: (targetId: number) => Promise<OfficialLinkCandidate[]>;
  onAccept: (targetId: number, sourceUrl: string) => Promise<void>;
}) {
  const [draft, setDraft] = useState<DataCollectionTargetRequest>({
    title: target.title, documentType: target.documentType, targetYear: target.targetYear,
    sourceUrl: target.sourceUrl, status: target.status, note: target.note
  });
  const [saving, setSaving] = useState(false);
  const [discovering, setDiscovering] = useState(false);
  const [acceptingUrl, setAcceptingUrl] = useState('');
  const [candidates, setCandidates] = useState<OfficialLinkCandidate[] | null>(null);
  const [discoveryError, setDiscoveryError] = useState('');

  useEffect(() => {
    setDraft({ title: target.title, documentType: target.documentType, targetYear: target.targetYear, sourceUrl: target.sourceUrl, status: target.status, note: target.note });
  }, [target]);

  const save = () => {
    setSaving(true);
    onUpdate(target.id, draft).finally(() => setSaving(false));
  };

  const discover = () => {
    setDiscovering(true);
    setDiscoveryError('');
    onDiscover(target.id)
      .then(setCandidates)
      .catch((error: Error) => setDiscoveryError(error.message))
      .finally(() => setDiscovering(false));
  };

  const accept = (candidate: OfficialLinkCandidate) => {
    setAcceptingUrl(candidate.sourceUrl);
    setDiscoveryError('');
    onAccept(target.id, candidate.sourceUrl)
      .then(() => setCandidates(null))
      .catch((error: Error) => setDiscoveryError(error.message))
      .finally(() => setAcceptingUrl(''));
  };

  return <div className="task-target-row">
    <div className="target-heading"><span className={`target-status ${target.status.toLowerCase()}`}>{TARGET_STATUS_LABELS[target.status]}</span>{target.systemGenerated && <i>系统</i>}<a href={target.sourceUrl} target="_blank" rel="noreferrer" aria-label={`打开 ${target.title}`}><ExternalLink size={14} /></a></div>
    <input aria-label="资料标题" value={draft.title} onChange={(event) => setDraft({ ...draft, title: event.target.value })} />
    <div className="target-fields"><input aria-label="资料类型" value={draft.documentType} onChange={(event) => setDraft({ ...draft, documentType: event.target.value })} /><input aria-label="目标年份" type="number" min="2000" max="2100" value={draft.targetYear} onChange={(event) => setDraft({ ...draft, targetYear: Number(event.target.value) })} /><select aria-label="采集状态" value={draft.status} onChange={(event) => setDraft({ ...draft, status: event.target.value as DataCollectionTarget['status'] })}><option value="PENDING">待采集</option><option value="COLLECTED">已采集</option><option value="VERIFIED">已核验</option></select></div>
    <input aria-label="资料 URL" value={draft.sourceUrl} onChange={(event) => setDraft({ ...draft, sourceUrl: event.target.value })} />
    <div className="target-actions"><button type="button" className="discover-action" onClick={discover} disabled={!canDiscover || discovering} title={canDiscover ? '从已登记的官方研招入口发现候选链接' : '院校尚未登记官方研招入口'}>{discovering ? <LoaderCircle className="spin" size={14} /> : <Search size={14} />}{discovering ? '发现中' : '发现候选'}</button><button type="button" onClick={save} disabled={saving || !draft.title.trim() || !draft.sourceUrl.trim()}><Save size={14} />{saving ? '保存中' : '保存'}</button>{!target.systemGenerated && <button type="button" className="danger-action" aria-label={`删除 ${target.title}`} onClick={() => onDelete(target.id)}><Trash2 size={14} /></button>}</div>
    {(candidates !== null || discoveryError) && <div className="link-discovery-results">
      {discoveryError && <p role="alert">{discoveryError}</p>}
      {candidates?.length === 0 && !discoveryError && <p>未找到匹配候选</p>}
      {candidates && candidates.length > 0 && <ol>{candidates.map((candidate) => <li key={candidate.sourceUrl}>
        <div><strong>{candidate.title}</strong><span>匹配 {candidate.score} · {candidate.matchedKeywords.join(' / ')}</span></div>
        <a href={candidate.sourceUrl} target="_blank" rel="noreferrer" aria-label={`核对 ${candidate.title}`}><ExternalLink size={14} /></a>
        <button type="button" onClick={() => accept(candidate)} disabled={Boolean(acceptingUrl)}><Check size={14} />{acceptingUrl === candidate.sourceUrl ? '采用中' : '采用'}</button>
      </li>)}</ol>}
    </div>}
  </div>;
}

function TaskTargets({ task, onCreate, onUpdate, onDelete, onDiscover, onAccept }: {
  task: DataCollectionTask;
  onCreate: (patch: DataCollectionTargetRequest) => Promise<void>;
  onUpdate: (targetId: number, patch: DataCollectionTargetRequest) => Promise<void>;
  onDelete: (targetId: number) => Promise<void>;
  onDiscover: (targetId: number) => Promise<OfficialLinkCandidate[]>;
  onAccept: (targetId: number, sourceUrl: string) => Promise<void>;
}) {
  const [draft, setDraft] = useState<DataCollectionTargetRequest>({
    title: '', documentType: task.recommendedDocumentTypes[0] ?? '官方招生公告',
    targetYear: new Date().getFullYear(), sourceUrl: task.officialEntryUrl ?? '', status: 'PENDING', note: null
  });
  const [saving, setSaving] = useState(false);

  const create = () => {
    setSaving(true);
    onCreate(draft).then(() => setDraft((current) => ({ ...current, title: '', note: null }))).finally(() => setSaving(false));
  };

  return <section className="task-targets"><div className="task-subheading"><strong>官方 URL 待办</strong><span>{task.targets.length} 条</span></div>
    <div className="task-target-list">{task.targets.map((target) => <TargetRow key={target.id} target={target} canDiscover={Boolean(task.officialEntryUrl)} onUpdate={onUpdate} onDelete={onDelete} onDiscover={onDiscover} onAccept={onAccept} />)}</div>
    <div className="target-create"><input aria-label="新增资料标题" placeholder="资料标题" value={draft.title} onChange={(event) => setDraft({ ...draft, title: event.target.value })} /><input aria-label="新增资料类型" placeholder="资料类型" value={draft.documentType} onChange={(event) => setDraft({ ...draft, documentType: event.target.value })} /><input aria-label="新增目标年份" type="number" min="2000" max="2100" value={draft.targetYear} onChange={(event) => setDraft({ ...draft, targetYear: Number(event.target.value) })} /><input aria-label="新增资料 URL" placeholder="https://..." value={draft.sourceUrl} onChange={(event) => setDraft({ ...draft, sourceUrl: event.target.value })} /><button type="button" onClick={create} disabled={saving || !draft.title.trim() || !draft.sourceUrl.trim()}><Plus size={14} />{saving ? '添加中' : '添加 URL'}</button></div>
  </section>;
}

function CollectionTaskRow({ task, onUpdate, onCreateTarget, onUpdateTarget, onDeleteTarget, onDiscoverLinks, onAcceptLink }: {
  task: DataCollectionTask;
  onUpdate: (schoolId: number, patch: DataCollectionTaskUpdate) => Promise<void>;
  onCreateTarget: (schoolId: number, patch: DataCollectionTargetRequest) => Promise<void>;
  onUpdateTarget: (schoolId: number, targetId: number, patch: DataCollectionTargetRequest) => Promise<void>;
  onDeleteTarget: (schoolId: number, targetId: number) => Promise<void>;
  onDiscoverLinks: (schoolId: number, targetId: number) => Promise<OfficialLinkCandidate[]>;
  onAcceptLink: (schoolId: number, targetId: number, sourceUrl: string) => Promise<void>;
}) {
  const [draft, setDraft] = useState<DataCollectionTaskUpdate>({
    status: task.status,
    assignee: task.assignee,
    dueDate: task.dueDate,
    completionCriteria: task.completionCriteria
  });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setDraft({ status: task.status, assignee: task.assignee, dueDate: task.dueDate, completionCriteria: task.completionCriteria });
  }, [task]);

  const save = () => {
    setSaving(true);
    onUpdate(task.schoolId, draft).finally(() => setSaving(false));
  };

  return <article className={`collection-task-row status-${task.status.toLowerCase()}${task.overdue ? ' overdue' : ''}`}>
    <div className="task-summary">
      <span className={`task-priority ${task.priority.toLowerCase()}`}>{task.priority}</span>
      <div className="task-school"><strong>{task.schoolName}</strong><span>{task.schoolLevel} · 覆盖率 {task.coveragePercent}%</span></div>
      <span className={`task-status ${task.status.toLowerCase()}`}>{TASK_STATUS_LABELS[task.status]}</span>
      <div className="task-ownership"><span><UserRound size={14} />{task.assignee || '未分配'}</span><span className={task.overdue ? 'is-overdue' : ''}><CalendarDays size={14} />{task.dueDate || '未设置'}</span></div>
    </div>
    <div className="task-scope"><span>{task.targetYears.join(' / ')}</span><div>{task.recommendedDocumentTypes.map((type) => <i key={type}>{type}</i>)}</div></div>
    <p>{task.reason}</p>
    <details className="task-editor">
      <summary>任务设置</summary>
      <div className="task-editor-grid">
        <label><span>状态</span><select value={draft.status} onChange={(event) => setDraft({ ...draft, status: event.target.value as DataCollectionTask['status'] })}><option value="OPEN">待处理</option><option value="IN_PROGRESS">进行中</option><option value="BLOCKED">受阻</option>{task.status === 'COMPLETED' && <option value="COMPLETED">已完成</option>}</select></label>
        <label><span>负责人</span><input value={draft.assignee ?? ''} onChange={(event) => setDraft({ ...draft, assignee: event.target.value || null })} placeholder="姓名或小组" /></label>
        <label><span>截止日期</span><input type="date" value={draft.dueDate ?? ''} onChange={(event) => setDraft({ ...draft, dueDate: event.target.value || null })} /></label>
        <label className="task-criteria"><span>完成条件</span><textarea value={draft.completionCriteria} onChange={(event) => setDraft({ ...draft, completionCriteria: event.target.value })} /></label>
      </div>
      <button type="button" className="task-save" onClick={save} disabled={saving || draft.completionCriteria.trim().length < 10}><Save size={15} />{saving ? '保存中' : '保存任务'}</button>
      <TaskTargets task={task} onCreate={(patch) => onCreateTarget(task.schoolId, patch)} onUpdate={(targetId, patch) => onUpdateTarget(task.schoolId, targetId, patch)} onDelete={(targetId) => onDeleteTarget(task.schoolId, targetId)} onDiscover={(targetId) => onDiscoverLinks(task.schoolId, targetId)} onAccept={(targetId, sourceUrl) => onAcceptLink(task.schoolId, targetId, sourceUrl)} />
      <section className="task-history"><div className="task-subheading"><strong>最近操作</strong><span>{task.history.length} 条</span></div>{task.history.length === 0 ? <p>暂无操作记录</p> : <ol>{task.history.map((item) => <li key={item.id}><span>{TASK_HISTORY_LABELS[item.action] ?? item.action}</span><p>{item.detail || '-'}</p><time>{item.operator || 'system'} · {item.createdAt}</time></li>)}</ol>}</section>
    </details>
    {task.status !== 'COMPLETED' && <a href="#admin-documents">录入资料<ChevronRight size={14} /></a>}
  </article>;
}

export function DataCollectionTasks({ tasks, onUpdate, onCreateTarget, onUpdateTarget, onDeleteTarget, onDiscoverLinks, onAcceptLink }: {
  tasks: DataCollectionTask[];
  onUpdate: (schoolId: number, patch: DataCollectionTaskUpdate) => Promise<void>;
  onCreateTarget: (schoolId: number, patch: DataCollectionTargetRequest) => Promise<void>;
  onUpdateTarget: (schoolId: number, targetId: number, patch: DataCollectionTargetRequest) => Promise<void>;
  onDeleteTarget: (schoolId: number, targetId: number) => Promise<void>;
  onDiscoverLinks: (schoolId: number, targetId: number) => Promise<OfficialLinkCandidate[]>;
  onAcceptLink: (schoolId: number, targetId: number, sourceUrl: string) => Promise<void>;
}) {
  const [filter, setFilter] = useState<CollectionTaskFilter>('ACTIVE');
  const [limit, setLimit] = useState(20);
  const filteredTasks = filterCollectionTasks(tasks, filter);
  const visibleTasks = filteredTasks.slice(0, limit);
  return <section className="panel collection-task-panel">
    <div className="panel-header">
      <div><span className="section-kicker"><ClipboardList size={14} />数据采集队列</span><h2>下一批优先任务</h2></div>
      <span>{filteredTasks.length} / {tasks.length} 项</span>
    </div>
    <div className="task-filters">{FILTERS.map((item) => <button key={item.value} type="button" className={filter === item.value ? 'selected' : ''} onClick={() => { setFilter(item.value); setLimit(20); }}>{item.label}</button>)}</div>
    {filteredTasks.length === 0 ? <div className="empty-state">当前筛选下没有采集任务</div> : <div className="collection-task-list">
      {visibleTasks.map((task) => <CollectionTaskRow key={task.schoolId} task={task} onUpdate={onUpdate} onCreateTarget={onCreateTarget} onUpdateTarget={onUpdateTarget} onDeleteTarget={onDeleteTarget} onDiscoverLinks={onDiscoverLinks} onAcceptLink={onAcceptLink} />)}
      <div className="task-list-footer"><span>显示 {visibleTasks.length} / {filteredTasks.length}</span>{visibleTasks.length < filteredTasks.length && <button type="button" onClick={() => setLimit((current) => current + 20)}>显示更多任务</button>}</div>
    </div>}
  </section>;
}
