import { Bookmark, Check, ChevronRight, Database, Plus, SlidersHorizontal, X } from 'lucide-react';
import { SchoolTable as SchoolTableView } from '../components/SchoolDecisionViews';
import type { Catalog408Status, FavoriteSchool, School } from '../types';
import type { SchoolFilterState } from '../schoolFilters';
import { PROVINCE_OPTIONS } from '../provinces';

type FilterTag = { key: string; label: string };

export function SchoolSearchPage({ filters, onFilterChange, onReset, filtersExpanded, onToggleFilters, activeFilters, schools, selectedIds, favoriteIds, favoriteSchools, catalog408Status, loading, error, onToggle, onToggleFavorite, onDetail, onManageFavorites, onCompare }: {
  filters: SchoolFilterState;
  onFilterChange: (patch: Partial<SchoolFilterState>) => void;
  onReset: () => void;
  filtersExpanded: boolean;
  onToggleFilters: () => void;
  activeFilters: Array<FilterTag & { clear: () => void }>;
  schools: School[];
  selectedIds: number[];
  favoriteIds: Set<number>;
  favoriteSchools: FavoriteSchool[];
  catalog408Status: Catalog408Status | null;
  loading: boolean;
  error: string;
  onToggle: (id: number) => void;
  onToggleFavorite: (school: School) => void;
  onDetail: (id: number) => void;
  onManageFavorites: () => void;
  onCompare: () => void;
}) {
  return <div className="query-workbench">
    <button type="button" className="filter-toggle" onClick={onToggleFilters} aria-expanded={filtersExpanded}><SlidersHorizontal size={17} />{filtersExpanded ? '收起筛选' : `展开筛选${activeFilters.length ? `（${activeFilters.length}）` : ''}`}</button>
    <aside className={`query-filter-rail ${filtersExpanded ? 'mobile-open' : ''}`}>
      <div className="rail-heading"><div><SlidersHorizontal size={17} /><strong>筛选条件</strong></div><button type="button" onClick={onReset}>重置</button></div>
      <div className="filter-group"><span>专业课</span><div className="segmented-control">
        <button type="button" className={filters.is408 === undefined ? 'selected' : ''} onClick={() => onFilterChange({ is408: undefined })}>不限</button>
        <button type="button" className={filters.is408 === true ? 'selected' : ''} onClick={() => onFilterChange({ is408: true })}>408</button>
        <button type="button" className={filters.is408 === false ? 'selected' : ''} onClick={() => onFilterChange({ is408: false })}>自命题</button>
      </div></div>
      <label className="filter-field"><span>省份</span><select value={filters.province} onChange={(event) => onFilterChange({ province: event.target.value })}><option value="">全部省份</option>{PROVINCE_OPTIONS.map((province) => <option key={province}>{province}</option>)}</select></label>
      <label className="filter-field"><span>院校层次</span><select value={filters.schoolLevel} onChange={(event) => onFilterChange({ schoolLevel: event.target.value })}><option value="">全部层次</option><option value="985">985</option><option value="211">211</option><option value="双一流">双一流</option><option value="普通院校">普通院校</option></select></label>
      <label className="filter-field"><span>专业类型</span><select value={filters.degreeType} onChange={(event) => onFilterChange({ degreeType: event.target.value })}><option value="">不限</option><option>学硕</option><option>专硕</option></select></label>
      <label className="filter-field"><span>专业课关键词</span><input value={filters.professionalKeyword} onChange={(event) => onFilterChange({ professionalKeyword: event.target.value })} placeholder="例如 408" /></label>
      <div className="filter-group"><span>目录计划人数</span><div className="range-fields"><input value={filters.minQuota} onChange={(event) => onFilterChange({ minQuota: event.target.value })} placeholder="最低" /><i /><input value={filters.maxQuota} onChange={(event) => onFilterChange({ maxQuota: event.target.value })} placeholder="最高" /></div></div>
      <div className="filter-group"><span>复试线</span><div className="range-fields"><input value={filters.minScore} onChange={(event) => onFilterChange({ minScore: event.target.value })} placeholder="最低" /><i /><input value={filters.maxScore} onChange={(event) => onFilterChange({ maxScore: event.target.value })} placeholder="最高" /></div></div>
    </aside>
    <section className="query-results">{catalog408Status && <div className={`catalog-status ${catalog408Status.complete ? 'complete' : 'partial'}`}><Database size={17} /><div><strong>{catalog408Status.year} 年 408 目录</strong><span>{catalog408Status.schools} 所采集院校 · {catalog408Status.inputRecords} 条院系专业记录</span></div><em>{catalog408Status.complete ? '完整批次' : '官方公开首屏，待登录补全'}</em></div>}<div className="result-toolbar"><div><strong>{loading ? '正在查询' : `${schools.length} 所院校`}</strong><span>{activeFilters.length ? `已应用 ${activeFilters.length} 个条件` : '全部已收录院校'}</span></div><div className="filter-tags">{activeFilters.map((item) => <button type="button" className="filter-tag" key={item.key} onClick={item.clear}>{item.label}<X size={12} /></button>)}</div></div><SchoolTableView schools={schools} selectedIds={selectedIds} favoriteIds={favoriteIds} error={error} loading={loading} onToggle={onToggle} onToggleFavorite={onToggleFavorite} onDetail={onDetail} /></section>
    <aside className="shortlist-rail"><div className="rail-heading"><div><strong>候选清单</strong><span>{favoriteSchools.length} 所</span></div><button type="button" onClick={onManageFavorites}>管理</button></div>{favoriteSchools.length === 0 ? <div className="rail-empty"><Bookmark size={19} /><strong>暂无候选院校</strong></div> : <div className="shortlist-items">{favoriteSchools.slice(0, 6).map((item) => <article key={item.schoolId}><button type="button" onClick={() => onDetail(item.schoolId)}><strong>{item.name}</strong><span>{item.groupTag} · {item.regionLabel}</span></button><button type="button" className={selectedIds.includes(item.schoolId) ? 'selected' : ''} aria-label="切换对比" onClick={() => onToggle(item.schoolId)}>{selectedIds.includes(item.schoolId) ? <Check size={14} /> : <Plus size={14} />}</button></article>)}</div>}<div className="shortlist-footer"><span>已选 {selectedIds.length}/4 所对比</span><button type="button" disabled={selectedIds.length < 2} onClick={onCompare}>进入对比<ChevronRight size={15} /></button></div></aside>
  </div>;
}
