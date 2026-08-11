import React, { useEffect, useMemo, useState } from 'react';
import ReactDOM from 'react-dom/client';
import { ChevronRight, Search, X } from 'lucide-react';
import { requestJson } from './api';
import type { ViewKey } from './navigation';
import { AdminSidebar, MobileNavigation, WorkbenchSidebar } from './components/WorkbenchChrome';
import { SchoolTable as SchoolTableView } from './components/SchoolDecisionViews';
import { AgentWorkspace } from './components/AgentWorkspace';
import { useShortlist } from './hooks/useShortlist';
import { useRouteState } from './hooks/useRouteState';
import { useRecommendations } from './hooks/useRecommendations';
import { useSchools } from './hooks/useSchools';
import { useAdminWorkspace } from './hooks/useAdminWorkspace';
import { useAgentAssistant } from './hooks/useAgentAssistant';
import { useHomeData } from './hooks/useHomeData';
import { ComparePage, FavoritesPage, RecommendationPage, SchoolDetailPage } from './pages/UserDecisionPages';
import { SchoolSearchPage } from './pages/SchoolSearchPage';
import { HomePage } from './pages/HomePage';
import { AdminPage } from './pages/AdminPage';
import type { CompareResult, FavoriteGroup, RecommendationItem, School, SchoolDetail } from './types';
import { formatDateTime, formatExamType, formatRegionLabel } from './formatters';
import { downloadExport, todayStamp } from './exportUtils';
import { formatTrendForComparison } from './decisionTrends';
import './styles.css';
import './workbench.css';

function App() {
  const {
    schools, keyword, setKeyword, filtersExpanded, setFiltersExpanded, loading, error,
    filterState, activeFilters, updateSchoolFilters, resetFilters, loadSchools
  } = useSchools();
  const [schoolDetail, setSchoolDetail] = useState<SchoolDetail | null>(null);
  const {
    selectedIds, setSelectedIds, favoriteSchools, setFavoriteSchools, favoriteIds,
    toggleComparison: toggleSchool, addToComparison: addSchoolToCompare,
    removeFromComparison: removeSchoolFromCompare, clearComparison,
    toggleFavoriteSchool, updateFavoriteSchool, removeFavoriteSchool
  } = useShortlist(schools);
  const [compareResult, setCompareResult] = useState<CompareResult | null>(null);
  const {
    recommendationProfile, setRecommendationProfile, recommendations,
    recommendationLoading, recommendationError, loadRecommendations,
    toggleProfileProvince
  } = useRecommendations();
  const [favoriteGroupFilter, setFavoriteGroupFilter] = useState<'全部' | FavoriteGroup>('全部');
  const [compareOnlyDifferences, setCompareOnlyDifferences] = useState(false);
  const [dataWarning, setDataWarning] = useState('');
  const { activeView, navigateTo } = useRouteState({
    selectedIds, setSelectedIds, setSchoolDetail, setDataWarning
  });
  const adminWorkspace = useAdminWorkspace({ schools, reloadSchools: loadSchools, setDataWarning });
  const {
    adminToken, adminRole, isAdminLoggedIn, sources, sourceDocuments, coverageReport, catalog408Status,
    loadPublicData, loadAdminData
  } = adminWorkspace;
  const agentAssistant = useAgentAssistant({
    active: activeView === 'ai' || (activeView === 'admin' && adminRole === 'ADMIN'),
    adminToken: adminRole === 'ADMIN' ? adminToken : '',
    onOpen: () => navigateTo('ai')
  });
  const { schoolById, latestUpdates } = useHomeData({
    schools, sources, sourceDocuments, coverageReport
  });

  const viewTitle: Record<ViewKey, string> = {
    home: '决策概览',
    query: '查找院校',
    detail: '院校档案',
    compare: '对比决策',
    favorites: '候选清单',
    recommend: '智能推荐',
    ai: '资料问答',
    admin: '数据管理'
  };

  const selectedSchoolNames = useMemo(
    () => schools.filter((school) => selectedIds.includes(school.id)).map((school) => school.name).join('、') || '未选择院校',
    [schools, selectedIds]
  );
  const filteredFavoriteSchools = useMemo(
    () => favoriteGroupFilter === '全部' ? favoriteSchools : favoriteSchools.filter((item) => item.groupTag === favoriteGroupFilter),
    [favoriteGroupFilter, favoriteSchools]
  );
  useEffect(() => loadPublicData(), []);
  useEffect(() => {
    if (activeView === 'admin' && isAdminLoggedIn) loadAdminData();
  }, [activeView, adminToken]);
  useEffect(() => {
    if (activeView === 'recommend' && recommendations.length === 0) loadRecommendations();
  }, [activeView]);

  useEffect(() => {
    if (selectedIds.length < 2) {
      setCompareResult(null);
      return;
    }
    const params = new URLSearchParams();
    selectedIds.forEach((id) => params.append('ids', String(id)));
    requestJson<CompareResult>(`/api/compare?${params.toString()}`)
      .then((payload) => setCompareResult(payload.data))
      .catch(() => setCompareResult(null));
  }, [selectedIds]);

  const loadDetail = (id: number) => {
    setDataWarning('');
    requestJson<SchoolDetail>(`/api/schools/${id}`)
      .then((payload) => {
        setSchoolDetail(payload.data);
        navigateTo('detail', { schoolId: id });
      })
      .catch((requestError: Error) => setDataWarning(`院校详情加载失败：${requestError.message}`));
  };


  const favoriteRecommendation = (item: RecommendationItem) => {
    setFavoriteSchools((current) => {
      const exists = current.some((favorite) => favorite.schoolId === item.school.id);
      if (exists) {
        return current;
      }
      return [{
        schoolId: item.school.id,
        name: item.school.name,
        regionLabel: formatRegionLabel(item.school.province, item.school.city),
        schoolLevel: item.school.schoolLevel,
        primarySubject: item.school.primarySubject,
        is408: item.school.is408,
        latestQuota: item.school.latestQuota ?? null,
        latestScoreLine: item.school.latestScoreLine ?? null,
        groupTag: item.groupTag === '待核验' ? '稳妥' : item.groupTag,
        note: `推荐匹配度 ${item.matchScore}，${item.reasons[0] ?? '待进一步核验'}`,
        savedAt: new Date().toISOString(),
        noteUpdatedAt: new Date().toISOString()
      }, ...current];
    });
  };

  const refreshAll = () => {
    loadSchools();
    if (activeView === 'admin' && isAdminLoggedIn) loadAdminData();
    else loadPublicData();
  };

  const handleTopSearchKeyDown: React.KeyboardEventHandler<HTMLInputElement> = (event) => {
    if (event.key === 'Enter') {
      navigateTo('query');
    }
  };

  const exportFavorites = (format: 'json' | 'csv') => {
    const payload = favoriteSchools.map((item) => ({
      schoolId: item.schoolId,
      name: item.name,
      regionLabel: item.regionLabel,
      schoolLevel: item.schoolLevel,
      primarySubject: item.primarySubject,
      is408: formatExamType(item.is408),
      latestQuota: item.latestQuota ?? '',
      latestScoreLine: item.latestScoreLine ?? '',
      groupTag: item.groupTag,
      note: item.note,
      noteUpdatedAt: formatDateTime(item.noteUpdatedAt ?? item.savedAt),
      savedAt: formatDateTime(item.savedAt)
    }));
    downloadExport(`favorite-schools-${todayStamp()}.${format}`, payload, format);
  };

  const exportCompare = (format: 'json' | 'csv') => {
    if (!compareResult) return;
    const payload = compareResult.schools.map((item) => ({
      id: item.id,
      name: item.name,
      regionLabel: item.regionLabel,
      schoolLevel: item.schoolLevel,
      collegeName: item.collegeName ?? '',
      majorName: item.majorName ?? '',
      degreeType: item.degreeType ?? '',
      primarySubject: item.primarySubject ?? '',
      is408: formatExamType(item.is408),
      latestQuota: item.latestQuota ?? '',
      quotaTrend: formatTrendForComparison(item.quotaHistory, '招生计划'),
      latestScoreLine: item.latestScoreLine ?? '',
      scoreLineTrend: formatTrendForComparison(item.scoreLineHistory, '复试线'),
      officialSourceCount: item.officialSourceCount,
      latestSourceUpdatedAt: formatDateTime(item.latestSourceUpdatedAt ?? undefined)
    }));
    if (format === 'json') {
      downloadExport(`compare-result-${todayStamp()}.json`, {
        schools: payload,
        riskTips: compareResult.riskTips,
        selectedSchoolNames,
        exportedAt: new Date().toISOString()
      }, 'json');
      return;
    }
    downloadExport(`compare-result-${todayStamp()}.csv`, payload, 'csv');
  };

  return (
    <main className={`app-shell workbench-shell ${activeView === 'admin' ? 'is-admin' : ''}`}>
      {activeView === 'admin'
        ? <AdminSidebar onNavigate={(view) => navigateTo(view)} />
        : <WorkbenchSidebar activeView={activeView} favoriteCount={favoriteSchools.length} selectedCount={selectedIds.length} onNavigate={(view) => navigateTo(view)} />}

      <section className="workspace">
        <header className="topbar">
          <h1>{viewTitle[activeView]}</h1>
          {activeView !== 'admin' && <div className="topbar-tools">
            <label className="search-box">
              <Search size={18} />
              <input value={keyword} onChange={(event) => setKeyword(event.target.value)} onKeyDown={handleTopSearchKeyDown} placeholder="搜索院校或地区" />
              <kbd>Enter</kbd>
            </label>
          </div>}
        </header>

        {dataWarning && (
          <div className="service-banner" role="alert">
            <span>{dataWarning}</span>
            <button type="button" onClick={() => { setDataWarning(''); refreshAll(); }}>重新加载</button>
          </div>
        )}

        {activeView === 'home' && (
          <HomePage
            profile={recommendationProfile} favoriteCount={favoriteSchools.length} selectedIds={selectedIds}
            compareSchools={selectedIds.map((id) => schoolById.get(id)).filter(Boolean) as School[]}
            focusSchools={favoriteSchools.length
              ? favoriteSchools.slice(0, 4).map((item) => schoolById.get(item.schoolId)).filter(Boolean) as School[]
              : (recommendations.length ? recommendations.slice(0, 4).map((item) => item.school) : schools.slice(0, 4))}
            latestUpdates={latestUpdates} onNavigate={(view) => navigateTo(view)}
            onSelectSchool={loadDetail} onToggleCompare={toggleSchool}
          />
        )}

        {activeView === 'query' && <SchoolSearchPage
          filters={filterState}
          onFilterChange={updateSchoolFilters}
          onReset={resetFilters}
          filtersExpanded={filtersExpanded}
          onToggleFilters={() => setFiltersExpanded((current) => !current)}
          activeFilters={activeFilters}
          schools={schools}
          selectedIds={selectedIds}
          favoriteIds={favoriteIds}
          favoriteSchools={favoriteSchools}
          catalog408Status={catalog408Status}
          loading={loading}
          error={error}
          onToggle={toggleSchool}
          onToggleFavorite={toggleFavoriteSchool}
          onDetail={loadDetail}
          onManageFavorites={() => navigateTo('favorites')}
          onCompare={() => navigateTo('compare', { compareIds: selectedIds })}
        />}

        {activeView === 'detail' && (
          <SchoolDetailPage
            detail={schoolDetail}
            isFavorite={schoolDetail ? favoriteIds.has(schoolDetail.summary.id) : false}
            isSelected={schoolDetail ? selectedIds.includes(schoolDetail.summary.id) : false}
            compareFull={selectedIds.length >= 4}
            onBack={() => navigateTo('query')}
            onToggleFavorite={() => schoolDetail && toggleFavoriteSchool(schoolDetail.summary)}
            onAddToCompare={() => schoolDetail && addSchoolToCompare(schoolDetail.summary.id)}
            onAskAi={agentAssistant.openWithQuestion}
          />
        )}

        {activeView === 'compare' && (
          <ComparePage selectedIds={selectedIds} selectedSchoolNames={selectedSchoolNames} result={compareResult} onlyDifferences={compareOnlyDifferences} onOnlyDifferencesChange={setCompareOnlyDifferences} onClear={clearComparison} onExport={exportCompare} onQuery={() => navigateTo('query')} />
        )}

        {activeView === 'favorites' && (
          <FavoritesPage favorites={favoriteSchools} filteredFavorites={filteredFavoriteSchools} selectedIds={selectedIds} filter={favoriteGroupFilter} onFilterChange={setFavoriteGroupFilter} onChange={updateFavoriteSchool} onAddToCompare={addSchoolToCompare} onRemoveFromCompare={removeSchoolFromCompare} onRemove={removeFavoriteSchool} onDetail={loadDetail} onExport={exportFavorites} />
        )}

        {activeView === 'recommend' && (
          <RecommendationPage profile={recommendationProfile} loading={recommendationLoading} error={recommendationError} items={recommendations} favoriteIds={favoriteIds} selectedIds={selectedIds} onProfileChange={setRecommendationProfile} onToggleProvince={toggleProfileProvince} onGenerate={loadRecommendations} onDetail={loadDetail} onFavorite={favoriteRecommendation} onAddToCompare={addSchoolToCompare} />
        )}

        {activeView === 'ai' && (
          <AgentWorkspace {...agentAssistant.workspaceProps} adminEnabled={false} />
        )}

        {activeView === 'admin' && (
          <AdminPage {...adminWorkspace.pageProps} agentWorkspace={<AgentWorkspace {...agentAssistant.workspaceProps} />} />
        )}
      </section>
      {activeView !== 'admin' && activeView !== 'compare' && selectedIds.length > 0 && (
        <aside className="compare-dock" aria-label="待对比院校">
          <div>
            <strong>待对比</strong>
            <span>{selectedIds.length}/4</span>
          </div>
          <div className="compare-dock-schools">
            {selectedIds.map((id) => (
              <span key={id}>{schoolById.get(id)?.name ?? `院校 ${id}`}<button type="button" aria-label="移出对比" onClick={() => removeSchoolFromCompare(id)}><X size={13} /></button></span>
            ))}
          </div>
          <button type="button" className="dock-primary" disabled={selectedIds.length < 2} onClick={() => navigateTo('compare', { compareIds: selectedIds })}>
            开始对比<ChevronRight size={16} />
          </button>
        </aside>
      )}
      <MobileNavigation activeView={activeView} favoriteCount={favoriteSchools.length} onNavigate={(view) => navigateTo(view)} />
    </main>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(<App />);
