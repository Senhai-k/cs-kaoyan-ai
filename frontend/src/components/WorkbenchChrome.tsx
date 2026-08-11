import React from 'react';
import { Bot, Bookmark, Database, GitCompareArrows, House, Search, Settings, Sparkles } from 'lucide-react';
import type { ViewKey } from '../navigation';

type Navigate = (view: ViewKey) => void;

const USER_NAV: Array<{ key: ViewKey; label: string; shortLabel: string; icon: React.ReactNode }> = [
  { key: 'home', label: '决策概览', shortLabel: '首页', icon: <House size={18} /> },
  { key: 'query', label: '查找院校', shortLabel: '院校', icon: <Search size={18} /> },
  { key: 'recommend', label: '智能推荐', shortLabel: '推荐', icon: <Sparkles size={18} /> },
  { key: 'favorites', label: '候选清单', shortLabel: '清单', icon: <Bookmark size={18} /> },
  { key: 'compare', label: '对比决策', shortLabel: '对比', icon: <GitCompareArrows size={18} /> },
  { key: 'ai', label: '资料问答', shortLabel: 'AI', icon: <Bot size={18} /> }
];

export function WorkbenchSidebar({ activeView, favoriteCount, selectedCount, onNavigate }: {
  activeView: ViewKey;
  favoriteCount: number;
  selectedCount: number;
  onNavigate: Navigate;
}) {
  return (
    <aside className="workbench-sidebar">
      <button type="button" className="workbench-brand" onClick={() => onNavigate('home')}>
        <span className="brand-mark"><Database size={19} /></span>
        <span><strong>研途择校</strong><em>可信数据工作台</em></span>
      </button>
      <nav aria-label="主要导航">
        <span className="nav-section-label">导航</span>
        {USER_NAV.map((item) => (
          <button key={item.key} type="button" className={activeView === item.key ? 'active' : ''} onClick={() => onNavigate(item.key)}>
            {item.icon}<span>{item.label}</span>
            {item.key === 'favorites' && favoriteCount > 0 && <em>{favoriteCount}</em>}
            {item.key === 'compare' && selectedCount > 0 && <em>{selectedCount}</em>}
          </button>
        ))}
      </nav>
      <div className="workbench-sidebar-foot">
        <button type="button" onClick={() => onNavigate('admin')}><Settings size={17} /><span>数据管理</span></button>
      </div>
    </aside>
  );
}

export function AdminSidebar({ onNavigate }: { onNavigate: Navigate }) {
  return (
    <aside className="workbench-sidebar admin-sidebar">
      <button type="button" className="workbench-brand" onClick={() => onNavigate('home')}>
        <span className="brand-mark"><Database size={19} /></span>
        <span><strong>数据管理</strong><em>研途择校后台</em></span>
      </button>
      <nav aria-label="管理导航">
        <span className="nav-section-label">工作区</span>
        <button type="button" className="active"><Settings size={18} /><span>资料与数据</span></button>
        <button type="button" onClick={() => onNavigate('home')}><House size={18} /><span>返回用户端</span></button>
      </nav>
    </aside>
  );
}

export function MobileNavigation({ activeView, favoriteCount, onNavigate }: {
  activeView: ViewKey;
  favoriteCount: number;
  onNavigate: Navigate;
}) {
  const mobileItems = USER_NAV.filter((item) => ['home', 'query', 'favorites', 'compare', 'ai'].includes(item.key));
  if (activeView === 'admin') return null;
  return (
    <nav className="mobile-navigation" aria-label="移动端导航">
      {mobileItems.map((item) => (
        <button key={item.key} type="button" className={activeView === item.key ? 'active' : ''} onClick={() => onNavigate(item.key)}>
          <span>{item.icon}{item.key === 'favorites' && favoriteCount > 0 && <em>{favoriteCount}</em>}</span>
          <small>{item.shortLabel}</small>
        </button>
      ))}
    </nav>
  );
}
