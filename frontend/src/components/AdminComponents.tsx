import { Plus, X } from 'lucide-react';
import { useDeferredValue, useEffect, useState } from 'react';
import type React from 'react';
import { auditLabel } from '../formatters';
import type { College, DocumentChunk, DocumentSource, Major, School } from '../types';

export function ChunkList({ title, chunks }: { title: string; chunks: DocumentChunk[] }) {
  return <div className="chunk-list"><h3>{title}</h3>{chunks.length === 0 ? <p>暂无切片</p> : chunks.map((chunk) => <article key={chunk.id}><span>#{chunk.chunkIndex} / {chunk.year ?? '-'} / {auditLabel(chunk.auditStatus)}</span><p>{chunk.content}</p></article>)}</div>;
}

export function AdminBlock({ id, title, items, deletePath, onDelete, editing = false, onCancelEditor, children }: {
  id?: string; title: string; items: Array<{ id: number; label: string; onEdit: () => void }>; deletePath: string; onDelete: (path: string, id: number) => void;
  editing?: boolean; onCancelEditor?: () => void; children: React.ReactNode;
}) {
  const [keyword, setKeyword] = useState('');
  const [limit, setLimit] = useState(50);
  const [editorOpen, setEditorOpen] = useState(false);
  const deferredKeyword = useDeferredValue(keyword.trim().toLocaleLowerCase());
  const filteredItems = deferredKeyword
    ? items.filter((item) => item.label.toLocaleLowerCase().includes(deferredKeyword))
    : items;
  const visibleItems = filteredItems.slice(0, limit);
  useEffect(() => {
    if (editing) setEditorOpen(true);
  }, [editing]);

  const toggleEditor = () => {
    if (editorOpen && editing) onCancelEditor?.();
    setEditorOpen((current) => !current);
  };

  return <section id={id} className="panel admin-block">
    <header className="admin-block-header">
      <div><h3>{title}</h3><span>{items.length} 条记录</span></div>
      <button type="button" className={editorOpen ? 'is-open' : ''} onClick={toggleEditor}>
        {editorOpen ? <X size={15} /> : <Plus size={15} />}{editorOpen ? '收起表单' : '新增记录'}
      </button>
    </header>
    {editorOpen && <div className="admin-form admin-editor">{children}</div>}
    <div className="admin-list-toolbar">
      <input aria-label={`${title}列表筛选`} placeholder={`筛选${title}`} value={keyword} onChange={(event) => { setKeyword(event.target.value); setLimit(50); }} />
      <span>显示 {visibleItems.length} / {filteredItems.length}</span>
    </div>
    <div className="admin-list">{visibleItems.length === 0 ? <div className="admin-list-empty">暂无记录</div> : visibleItems.map((item) => <div key={item.id}><span>{item.label}</span><button type="button" onClick={() => { setEditorOpen(true); item.onEdit(); }}>编辑</button><button type="button" onClick={() => onDelete(deletePath, item.id)}>删除</button></div>)}</div>
    {visibleItems.length < filteredItems.length && <button type="button" className="secondary-button admin-list-more" onClick={() => setLimit((current) => current + 50)}>显示更多</button>}
  </section>;
}

export type AdminSectionKey = 'overview' | 'catalog' | 'knowledge' | 'admissions' | 'retest' | 'agent';

export function AdminSectionNav({ active, onChange }: { active: AdminSectionKey; onChange: (section: AdminSectionKey) => void }) {
  const sections = [
    ['overview', '覆盖任务'],
    ['catalog', '基础档案'],
    ['knowledge', '知识资料'],
    ['admissions', '招生数据'],
    ['retest', '复试数据'],
    ['agent', 'Agent 运维']
  ] as const;
  return <nav className="admin-section-nav" aria-label="管理端分区">{sections.map(([key, label]) => <button type="button" className={active === key ? 'active' : ''} key={key} onClick={() => onChange(key)}>{label}</button>)}</nav>;
}

export function SchoolSelect({ schools, value, onChange }: { schools: School[]; value: string; onChange: (value: string) => void }) {
  return <select value={value} onChange={(event) => onChange(event.target.value)}><option value="">选择学校</option>{schools.map((school) => <option value={school.id} key={school.id}>{school.name}</option>)}</select>;
}

export function CollegeSelect({ colleges, schoolId, value, onChange }: { colleges: College[]; schoolId: string; value: string; onChange: (value: string) => void }) {
  return <select value={value} onChange={(event) => onChange(event.target.value)}><option value="">选择学院</option>{colleges.filter((college) => !schoolId || college.schoolId === Number(schoolId)).map((college) => <option value={college.id} key={college.id}>{college.name}</option>)}</select>;
}

export function MajorSelect({ majors, value, onChange }: { majors: Major[]; value: string; onChange: (value: string) => void }) {
  return <select value={value} onChange={(event) => onChange(event.target.value)}><option value="">选择专业</option>{majors.map((major) => <option value={major.id} key={major.id}>{major.name}</option>)}</select>;
}

export function SourceSelect({ sources, schoolId, value, onChange }: { sources: DocumentSource[]; schoolId?: number; value: string; onChange: (value: string) => void }) {
  const options = schoolId
    ? sources.filter((source) => source.official && source.auditStatus === 'PUBLISHED' && source.schoolId === schoolId)
    : [];
  return <select value={value} disabled={!schoolId} onChange={(event) => onChange(event.target.value)}><option value="">{schoolId ? '选择官方证据来源' : '先选择学校或专业'}</option>{options.map((source) => <option value={source.id} key={source.id}>{source.year ?? '常设'} {source.title}</option>)}</select>;
}
