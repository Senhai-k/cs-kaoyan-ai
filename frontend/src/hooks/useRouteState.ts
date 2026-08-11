import { useEffect, useState, type Dispatch, type SetStateAction } from 'react';
import { requestJson } from '../api';
import { readRoute, routePath, type ViewKey } from '../navigation';
import type { SchoolDetail } from '../types';

type RouteStateOptions = {
  selectedIds: number[];
  setSelectedIds: Dispatch<SetStateAction<number[]>>;
  setSchoolDetail: Dispatch<SetStateAction<SchoolDetail | null>>;
  setDataWarning: Dispatch<SetStateAction<string>>;
};

export function useRouteState({
  selectedIds,
  setSelectedIds,
  setSchoolDetail,
  setDataWarning
}: RouteStateOptions) {
  const [activeView, setActiveView] = useState<ViewKey>(() => readRoute().view);

  const navigateTo = (view: ViewKey, options?: { schoolId?: number; compareIds?: number[] }, replace = false) => {
    const nextPath = routePath(view, options);
    if (`${window.location.pathname}${window.location.search}` !== nextPath) {
      window.history[replace ? 'replaceState' : 'pushState']({}, '', nextPath);
    }
    setActiveView(view);
  };

  useEffect(() => {
    const applyRoute = () => {
      const route = readRoute();
      setActiveView(route.view);
      if (route.compareIds) setSelectedIds(route.compareIds);
      if (route.schoolId) {
        requestJson<SchoolDetail>(`/api/schools/${route.schoolId}`)
          .then((payload) => setSchoolDetail(payload.data))
          .catch((requestError: Error) => setDataWarning(`院校详情加载失败：${requestError.message}`));
      }
    };

    applyRoute();
    window.addEventListener('popstate', applyRoute);
    return () => window.removeEventListener('popstate', applyRoute);
  }, [setDataWarning, setSchoolDetail, setSelectedIds]);

  useEffect(() => {
    if (activeView === 'compare') navigateTo('compare', { compareIds: selectedIds }, true);
  }, [activeView, selectedIds]);

  return { activeView, navigateTo };
}
