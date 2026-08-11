import { Check, ChevronRight, GitCompareArrows, Plus } from 'lucide-react';
import { formatDateTime, formatExamType, formatRegionLabel } from '../formatters';
import type { HomeUpdate, RecommendationProfile, School } from '../types';
import type { ViewKey } from '../navigation';

export function homePrimaryAction(favoriteCount: number) {
  return favoriteCount >= 2
    ? { label: '候选清单' as const, destination: 'favorites' as ViewKey }
    : { label: '智能推荐' as const, destination: 'recommend' as ViewKey };
}

function preferenceLabel(profile: RecommendationProfile) {
  return profile.prefer408 === 'ONLY_408' ? '优先 408' : profile.prefer408 === 'SELF_DESIGNED' ? '接受自命题' : '不限';
}

export function HomePage({
  profile, favoriteCount, selectedIds, compareSchools, focusSchools, latestUpdates,
  onNavigate, onSelectSchool, onToggleCompare
}: {
  profile: RecommendationProfile;
  favoriteCount: number;
  selectedIds: number[];
  compareSchools: School[];
  focusSchools: School[];
  latestUpdates: HomeUpdate[];
  onNavigate: (view: ViewKey) => void;
  onSelectSchool: (schoolId: number) => void;
  onToggleCompare: (schoolId: number) => void;
}) {
  const primaryAction = homePrimaryAction(favoriteCount);
  return <div className="home-workbench">
    <section className="decision-profile-bar">
      <div className="decision-profile-heading">
        <div><h2>报考目标</h2><span>{profile.preferredProvinces.length ? profile.preferredProvinces.join('、') : '地区不限'}</span></div>
        <button type="button" onClick={() => onNavigate('recommend')}>编辑条件</button>
      </div>
      <dl>
        <div><dt>目标分数</dt><dd>{profile.targetScore || '-'}</dd></div>
        <div><dt>专业类型</dt><dd>{profile.degreeType || '不限'}</dd></div>
        <div><dt>考试科目</dt><dd>{preferenceLabel(profile)}</dd></div>
        <div><dt>风险偏好</dt><dd>{{ CONSERVATIVE: '保守', BALANCED: '均衡', AGGRESSIVE: '冲刺' }[profile.riskPreference]}</dd></div>
      </dl>
    </section>

    <section className="home-decision-grid">
      <div className="decision-section">
        <div className="section-heading">
          <h2>{favoriteCount ? '候选院校' : '关注院校'}</h2>
          <button type="button" className="text-action" onClick={() => onNavigate(primaryAction.destination)}>{primaryAction.label}<ChevronRight size={15} /></button>
        </div>
        <div className="focus-school-list">
          {focusSchools.map((school) => (
            <article key={school.id}>
              <button type="button" className="school-main-link" onClick={() => onSelectSchool(school.id)}>
                <span>{school.name.slice(0, 1)}</span>
                <div><strong>{school.name}</strong><em>{formatRegionLabel(school.province, school.city)} · {school.schoolLevel}</em></div>
              </button>
              <div className="school-signal-row">
                <span className={school.is408 === null ? 'unknown' : 'verified'}>{formatExamType(school.is408)}</span>
                <span className={school.latestQuota === null ? 'unknown' : ''}>目录计划 {school.latestQuota ?? '待核验'}</span>
                <span className={school.latestScoreLine === null ? 'unknown' : ''}>复试线 {school.latestScoreLine ?? '待核验'}</span>
              </div>
              <button type="button" className={selectedIds.includes(school.id) ? 'row-action selected' : 'row-action'} onClick={() => onToggleCompare(school.id)}>
                {selectedIds.includes(school.id) ? <Check size={15} /> : <Plus size={15} />}{selectedIds.includes(school.id) ? '已加入' : '加入对比'}
              </button>
            </article>
          ))}
        </div>
      </div>

      <aside className="compare-queue-panel">
        <div className="section-heading"><h2>对比队列</h2><span>{compareSchools.length}/4</span></div>
        {compareSchools.length === 0 ? <div className="compact-empty"><GitCompareArrows size={20} /><span>暂无院校</span></div> : <div className="compare-queue-list">{compareSchools.map((school) => <button type="button" key={school.id} onClick={() => onSelectSchool(school.id)}><span>{school.name}</span><em>{formatRegionLabel(school.province, school.city)}</em><ChevronRight size={14} /></button>)}</div>}
        <button type="button" className="queue-action" disabled={compareSchools.length < 2} onClick={() => onNavigate('compare')}>开始对比</button>
      </aside>
    </section>

    <section className="evidence-feed">
      <div className="section-heading"><h2>最近核验</h2><button type="button" className="text-action" onClick={() => onNavigate('ai')}>资料问答<ChevronRight size={15} /></button></div>
      <div>{latestUpdates.slice(0, 6).map((item) => <a key={item.key} href={item.sourceUrl} target="_blank" rel="noreferrer"><strong>{item.title}</strong><span>{item.subtitle}</span><time>{formatDateTime(item.updatedAt)}</time></a>)}{latestUpdates.length === 0 && <div className="compact-empty">暂无核验记录</div>}</div>
    </section>
  </div>;
}
