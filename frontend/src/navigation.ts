export type ViewKey = 'home' | 'query' | 'detail' | 'compare' | 'favorites' | 'recommend' | 'ai' | 'admin';

export type AppRoute = { view: ViewKey; schoolId?: number; compareIds?: number[] };

const VIEW_PATHS: Record<Exclude<ViewKey, 'detail'>, string> = {
  home: '/',
  query: '/schools',
  compare: '/compare',
  favorites: '/favorites',
  recommend: '/recommendations',
  ai: '/ai',
  admin: '/admin'
};

export function readRoute(): AppRoute {
  return parseRoute(window.location.pathname, window.location.search);
}

export function parseRoute(pathname: string, search = ''): AppRoute {
  const path = pathname.replace(/\/+$/, '') || '/';
  const schoolMatch = path.match(/^\/schools\/(\d+)$/);
  if (schoolMatch) return { view: 'detail', schoolId: Number(schoolMatch[1]) };
  const matchedView = (Object.entries(VIEW_PATHS).find(([, routePath]) => routePath === path)?.[0] ?? 'home') as ViewKey;
  if (matchedView === 'compare') {
    const ids = new URLSearchParams(search).get('ids')
      ?.split(',')
      .map(Number)
      .filter((id) => Number.isInteger(id) && id > 0)
      .slice(0, 4) ?? [];
    return { view: matchedView, compareIds: ids };
  }
  return { view: matchedView };
}

export function routePath(view: ViewKey, options?: { schoolId?: number; compareIds?: number[] }) {
  if (view === 'detail' && options?.schoolId) return `/schools/${options.schoolId}`;
  if (view === 'compare') {
    const ids = options?.compareIds?.slice(0, 4) ?? [];
    return ids.length ? `/compare?ids=${ids.join(',')}` : '/compare';
  }
  return VIEW_PATHS[view === 'detail' ? 'query' : view];
}
