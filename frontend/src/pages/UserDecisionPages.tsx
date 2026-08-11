import { useState } from 'react';
import { ArrowLeft, Bookmark, GitCompareArrows, Info, Plus, SlidersHorizontal, X } from 'lucide-react';
import { CompareMatrix, FavoriteSchoolList, RecommendationList, SchoolDetailView } from '../components/SchoolDecisionViews';
import { PROVINCE_OPTIONS } from '../provinces';
import type { CompareResult, FavoriteGroup, FavoriteSchool, RecommendationItem, RecommendationProfile, SchoolDetail } from '../types';

export function SchoolDetailPage({ detail, isFavorite, isSelected, compareFull, onBack, onToggleFavorite, onAddToCompare, onAskAi }: {
  detail: SchoolDetail | null; isFavorite: boolean; isSelected: boolean; compareFull: boolean;
  onBack: () => void; onToggleFavorite: () => void; onAddToCompare: () => void; onAskAi: (question: string) => void;
}) {
  return <section className="detail-page">
    {detail && <div className="detail-actions"><button type="button" className="back-action" onClick={onBack}><ArrowLeft size={16} />返回院校列表</button><div><button type="button" onClick={onToggleFavorite}><Bookmark size={15} />{isFavorite ? '移出清单' : '加入清单'}</button><button type="button" className="primary-action" disabled={isSelected || compareFull} onClick={onAddToCompare}><Plus size={15} />加入对比</button></div></div>}
    <SchoolDetailView detail={detail} onAskAi={onAskAi} />
  </section>;
}

export function ComparePage({ selectedIds, selectedSchoolNames, result, onlyDifferences, onOnlyDifferencesChange, onClear, onExport, onQuery }: {
  selectedIds: number[]; selectedSchoolNames: string; result: CompareResult | null; onlyDifferences: boolean;
  onOnlyDifferencesChange: (value: boolean) => void; onClear: () => void; onExport: (format: 'json' | 'csv') => void; onQuery: () => void;
}) {
  return <section className="compare-page">
    <div className="compare-summary-band"><div><span>当前方案</span><strong>{selectedIds.length ? selectedSchoolNames : '尚未选择院校'}</strong><em>{selectedIds.length}/4 所参与对比</em></div><div className="comparison-health"><span><strong>{result?.schools.filter((item) => item.primarySubject && item.latestQuota !== null).length ?? 0}</strong>资料较完整</span><span><strong>{result?.riskTips.length ?? 0}</strong>待核验事项</span></div></div>
    <div className="compare-toolbar"><label className="difference-toggle"><input type="checkbox" checked={onlyDifferences} onChange={(event) => onOnlyDifferencesChange(event.target.checked)} /><span>仅看差异</span></label><div><button type="button" disabled={selectedIds.length === 0} onClick={onClear}>清空对比</button><button type="button" disabled={!result} onClick={() => onExport('json')}>导出 JSON</button><button type="button" disabled={!result} onClick={() => onExport('csv')}>导出 CSV</button></div></div>
    {result ? <><CompareMatrix schools={result.schools} onlyDifferences={onlyDifferences} /><section className="comparison-risks"><div><Info size={18} /><h2>核验提醒</h2></div><ul className="tip-list">{result.riskTips.map((tip) => <li key={tip}>{tip}</li>)}</ul></section></> : <div className="decision-empty"><GitCompareArrows size={26} /><strong>暂无对比院校</strong><button type="button" onClick={onQuery}>查找院校</button></div>}
  </section>;
}

export function FavoritesPage({ favorites, filteredFavorites, selectedIds, filter, onFilterChange, onChange, onAddToCompare, onRemoveFromCompare, onRemove, onDetail, onExport }: {
  favorites: FavoriteSchool[]; filteredFavorites: FavoriteSchool[]; selectedIds: number[]; filter: '全部' | FavoriteGroup;
  onFilterChange: (filter: '全部' | FavoriteGroup) => void; onChange: (schoolId: number, patch: Partial<FavoriteSchool>) => void;
  onAddToCompare: (schoolId: number) => void; onRemoveFromCompare: (schoolId: number) => void; onRemove: (schoolId: number) => void; onDetail: (schoolId: number) => void; onExport: (format: 'json' | 'csv') => void;
}) {
  return <section className="panel favorites-page">
    <div className="panel-header"><h2>候选清单</h2><span>{favorites.length} 所</span></div>
    <div className="export-actions"><div><button type="button" disabled={favorites.length === 0} onClick={() => onExport('json')}>导出 JSON</button><button type="button" disabled={favorites.length === 0} onClick={() => onExport('csv')}>导出 CSV</button></div></div>
    <div className="favorite-summary"><div><strong>{favorites.filter((item) => item.groupTag === '冲刺').length}</strong><span>冲刺</span></div><div><strong>{favorites.filter((item) => item.groupTag === '稳妥').length}</strong><span>稳妥</span></div><div><strong>{favorites.filter((item) => item.groupTag === '保底').length}</strong><span>保底</span></div></div>
    <div className="favorite-filters">{(['全部', '冲刺', '稳妥', '保底'] as const).map((group) => <button key={group} type="button" className={filter === group ? 'selected' : ''} onClick={() => onFilterChange(group)}>{group}</button>)}</div>
    <FavoriteSchoolList favorites={filteredFavorites} selectedIds={selectedIds} onChange={onChange} onAddToCompare={onAddToCompare} onRemoveFromCompare={onRemoveFromCompare} onRemove={onRemove} onDetail={onDetail} />
  </section>;
}

export function RecommendationPage({ profile, loading, error, items, favoriteIds, selectedIds, onProfileChange, onToggleProvince, onGenerate, onDetail, onFavorite, onAddToCompare }: {
  profile: RecommendationProfile; loading: boolean; error: string; items: RecommendationItem[]; favoriteIds: Set<number>; selectedIds: number[];
  onProfileChange: (profile: RecommendationProfile) => void; onToggleProvince: (province: string) => void; onGenerate: () => void; onDetail: (schoolId: number) => void; onFavorite: (item: RecommendationItem) => void; onAddToCompare: (schoolId: number) => void;
}) {
  const [filtersOpen, setFiltersOpen] = useState(false);
  const degreeLabel = profile.degreeType || '专业不限';
  const examLabel = profile.prefer408 === 'ONLY_408' ? '优先 408' : profile.prefer408 === 'SELF_DESIGNED' ? '接受自命题' : '科目不限';
  const riskLabel = profile.riskPreference === 'CONSERVATIVE' ? '保守' : profile.riskPreference === 'AGGRESSIVE' ? '冲刺' : '均衡';
  const provinceLabel = profile.preferredProvinces.length ? profile.preferredProvinces.join('、') : '地区不限';

  return <section className="recommendation-layout">
    <section className="recommendation-condition-bar" aria-label="当前报考条件">
      <div className="condition-summary">
        <strong>{profile.targetScore ? `${profile.targetScore} 分` : '未设目标分'}</strong>
        <span>{degreeLabel}</span><span>{examLabel}</span><span>{riskLabel}</span><span>{provinceLabel}</span>
      </div>
      <button type="button" className={filtersOpen ? 'active' : ''} onClick={() => setFiltersOpen((current) => !current)} aria-expanded={filtersOpen}>
        <SlidersHorizontal size={16} />调整条件
      </button>
    </section>

    {filtersOpen && <section className="panel recommendation-filter-panel"><div className="profile-form">
      <label><span>目标初试总分</span><input value={profile.targetScore} onChange={(event) => onProfileChange({ ...profile, targetScore: event.target.value })} placeholder="例如 360" /></label>
      <label><span>专业类型</span><select value={profile.degreeType} onChange={(event) => onProfileChange({ ...profile, degreeType: event.target.value })}><option value="">不限</option><option>学硕</option><option>专硕</option></select></label>
      <label><span>考试科目偏好</span><select value={profile.prefer408} onChange={(event) => onProfileChange({ ...profile, prefer408: event.target.value as RecommendationProfile['prefer408'] })}><option value="ANY">不限</option><option value="ONLY_408">优先 408</option><option value="SELF_DESIGNED">接受自命题</option></select></label>
      <label><span>风险偏好</span><select value={profile.riskPreference} onChange={(event) => onProfileChange({ ...profile, riskPreference: event.target.value as RecommendationProfile['riskPreference'] })}><option value="CONSERVATIVE">保守</option><option value="BALANCED">均衡</option><option value="AGGRESSIVE">冲刺</option></select></label>
      <div className="province-picks"><span>省份偏好</span><select aria-label="添加省份偏好" value="" onChange={(event) => event.target.value && onToggleProvince(event.target.value)}><option value="">添加省份</option>{PROVINCE_OPTIONS.map((province) => <option key={province} disabled={profile.preferredProvinces.includes(province)}>{province}</option>)}</select><div className="selected-provinces">{profile.preferredProvinces.map((province) => <button key={province} type="button" className="selected" onClick={() => onToggleProvince(province)}>{province}<X size={13} aria-hidden="true" /></button>)}</div></div>
      <button type="button" className="primary-action recommendation-generate" onClick={() => { onGenerate(); setFiltersOpen(false); }} disabled={loading}>{loading ? '生成中...' : '生成推荐'}</button>
    </div></section>}

    <section className="panel wide recommendation-results">
      <div className="panel-header"><h2>推荐结果</h2><span>{loading ? '加载中' : `${items.length} 所`}</span></div>
      <div className="recommendation-legend"><span><i className="fit" /><strong>条件匹配</strong></span><span><i className="confidence" /><strong>资料可信</strong></span><span><i className="risk" /><strong>报考风险</strong></span></div>
      {error
        ? <div className="empty-state error">{error}</div>
        : loading && items.length === 0
          ? <div className="recommendation-loading">正在计算匹配结果...</div>
          : <RecommendationList items={items} favoriteIds={favoriteIds} selectedIds={selectedIds} onDetail={onDetail} onFavorite={onFavorite} onAddToCompare={onAddToCompare} />}
    </section>
  </section>;
}
