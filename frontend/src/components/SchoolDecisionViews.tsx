import React, { useEffect, useState } from 'react';
import { Bot, Bookmark, Check, ChevronRight, ExternalLink, Minus, ShieldCheck, TrendingDown, TrendingUp } from 'lucide-react';
import { auditLabel, formatAdmissionPlan, formatDateTime, formatExamType, formatRegionLabel, recommendationConfidence, recommendationConfidencePercent, riskLabel } from '../formatters';
import { buildComparisonRows } from '../comparison';
import { summarizeTrend } from '../decisionTrends';
import type { CompareSchoolItem, DetailTab, FavoriteGroup, FavoriteSchool, NationalScoreLineInfo, RecommendationItem, School, SchoolDetail, SchoolScoreLineInfo, SourceInfo, YearValue } from '../types';

export function RecommendationList({ items, favoriteIds, selectedIds, onDetail, onFavorite, onAddToCompare }: {
  items: RecommendationItem[];
  favoriteIds: Set<number>;
  selectedIds: number[];
  onDetail: (schoolId: number) => void;
  onFavorite: (item: RecommendationItem) => void;
  onAddToCompare: (schoolId: number) => void;
}) {
  if (items.length === 0) return <div className="empty-state">暂无推荐结果</div>;
  return (
    <div className="recommendation-list">
      {items.map((item) => (
        <article key={item.school.id} className="recommendation-card">
          <div className="recommendation-body">
            <div className="recommendation-title">
              <div><strong>{item.school.name}</strong><span>{formatRegionLabel(item.school.province, item.school.city)} / {item.school.schoolLevel}</span></div>
              <em className={`risk-pill risk-${item.riskLevel.toLowerCase()}`}>{riskLabel(item.riskLevel)}</em>
            </div>
            <div className="decision-signals">
              <div><span>条件匹配<strong>{item.matchScore}</strong></span><i><b style={{ width: `${item.matchScore}%` }} /></i></div>
              <div><span>资料可信<strong>{recommendationConfidence(item)}</strong></span><i><b className="confidence-bar" style={{ width: `${recommendationConfidencePercent(item)}%` }} /></i></div>
              <div className="risk-signal"><span>方案位置<strong>{item.benchmarkScore == null ? '待核验' : item.groupTag}</strong></span><i><b className={`risk-bar risk-${item.riskLevel.toLowerCase()}`} style={{ width: item.benchmarkScore == null ? '18%' : '68%' }} /></i></div>
            </div>
            <div className="recommendation-metrics">
              <span>{item.school.primarySubject || '-'}</span><span>{formatExamType(item.school.is408)}</span><span>目录计划 {item.school.latestQuota ?? '-'}</span><span>参考线 {item.benchmarkScore ?? '-'}</span><span>分差 {item.scoreGap ?? '-'}</span><span>官方来源 {item.officialSourceCount}</span>
            </div>
            <ul className="recommendation-reasons">{item.reasons.map((reason) => <li key={reason}>{reason}</li>)}</ul>
            {item.benchmarkScore == null && <p className="confidence-note">缺少已核验分数线或录取分，当前只能判断条件匹配，不能确定冲稳保风险。</p>}
            <div className="recommendation-actions">
              <button type="button" className="link-button" onClick={() => onDetail(item.school.id)}>详情</button>
              <button type="button" className="link-button" disabled={favoriteIds.has(item.school.id)} onClick={() => onFavorite(item)}>{favoriteIds.has(item.school.id) ? '已加入清单' : '加入清单'}</button>
              <button type="button" className="link-button" disabled={selectedIds.includes(item.school.id) || selectedIds.length >= 4} onClick={() => onAddToCompare(item.school.id)}>加入对比</button>
            </div>
          </div>
        </article>
      ))}
    </div>
  );
}

export function SchoolTable({ schools, selectedIds, favoriteIds, loading, error, onToggle, onToggleFavorite, onDetail }: {
  schools: School[]; selectedIds: number[]; favoriteIds: Set<number>; loading: boolean; error: string;
  onToggle: (id: number) => void; onToggleFavorite: (school: School) => void; onDetail: (id: number) => void;
}) {
  return (
    <div className="school-result-list">
      {loading && [0, 1, 2].map((item) => <div className="result-skeleton" key={item}><span /><span /><span /></div>)}
      {!loading && !error && schools.map((school) => (
        <article className={selectedIds.includes(school.id) ? 'selected' : ''} key={school.id}>
          <div className="result-card-head">
            <label className="school-select"><input type="checkbox" checked={selectedIds.includes(school.id)} onChange={() => onToggle(school.id)} /><span><Check size={13} /></span></label>
            <button type="button" className="result-title" onClick={() => onDetail(school.id)}><strong>{school.name}</strong><span>{formatRegionLabel(school.province, school.city)} · {school.schoolLevel}</span></button>
            <button type="button" className={`favorite-icon ${favoriteIds.has(school.id) ? 'active' : ''}`} aria-label={favoriteIds.has(school.id) ? '取消收藏' : '收藏院校'} title={favoriteIds.has(school.id) ? '取消收藏' : '收藏院校'} onClick={() => onToggleFavorite(school)}><Bookmark size={17} /></button>
          </div>
          <div className="result-metrics">
            <div><span>专业课</span><strong className={!school.primarySubject ? 'unknown' : ''}>{school.primarySubject || '待核验'}</strong></div>
            <div><span>考试类型</span><strong className={school.is408 === null ? 'unknown' : ''}>{formatExamType(school.is408)}</strong></div>
            <div><span>目录计划</span><strong className={school.latestQuota === null ? 'unknown' : ''}>{school.latestQuota ?? '待核验'}</strong></div>
            <div><span>复试线</span><strong className={school.latestScoreLine === null ? 'unknown' : ''}>{school.latestScoreLine ?? '待核验'}</strong></div>
          </div>
          <div className="result-card-foot"><span>{[school.primarySubject, school.latestQuota, school.latestScoreLine].filter((value) => value !== null).length}/3 项关键数据已覆盖</span><button type="button" onClick={() => onDetail(school.id)}>查看档案<ChevronRight size={15} /></button></div>
        </article>
      ))}
      {!loading && !error && schools.length === 0 && <div className="empty-state">未找到匹配院校</div>}
      {error && <div className="empty-state error">{error}</div>}
    </div>
  );
}

function EvidenceLink({ sourceId, sources }: { sourceId: number | null; sources: SourceInfo[] }) {
  const source = sources.find((item) => item.id === sourceId);
  if (!source?.sourceUrl) return null;
  return <a className="field-evidence" href={source.sourceUrl} target="_blank" rel="noreferrer" title={source.title}><ExternalLink size={12} />官方证据</a>;
}

function TrendInsight({ title, metricLabel, values }: { title: string; metricLabel: string; values: YearValue[] }) {
  const trend = summarizeTrend(values, metricLabel);
  const Icon = trend.direction === 'UP' ? TrendingUp : trend.direction === 'DOWN' ? TrendingDown : Minus;
  return <div className={`trend-insight trend-${trend.direction.toLowerCase()}`} aria-label={`${title}趋势结论`}>
    <span><Icon size={17} /></span>
    <div><em>{title}</em><strong>{trend.label}</strong><p>{trend.detail}</p></div>
    <small>{trend.years >= 2 ? `基于 ${trend.years} 个年度的同口径数据` : '至少需要两个年度才能判断趋势'}</small>
  </div>;
}

function NationalBaseline({ lines }: { lines: NationalScoreLineInfo[] }) {
  if (lines.length === 0) return <div className="national-baseline is-missing"><strong>国家线基准待核验</strong></div>;
  const item = lines[0];
  return <div className={`national-baseline ${item.applicable ? 'is-applicable' : 'is-reference'}`}>
    <div><span>{item.year} 国家线 · {item.candidateType} 类考生</span><strong>{item.categoryName} {item.totalScore}</strong></div>
    <p>单科（满分 100）{item.score100} · 单科（满分 &gt; 100）{item.scoreOver100}</p>
    <small>{item.remark}</small>
    <a href={item.sourceUrl} target="_blank" rel="noreferrer" title={item.sourceTitle}><ExternalLink size={12} />教育部官方 PDF</a>
  </div>;
}

function formatSchoolScoreSubjects(item: SchoolScoreLineInfo) {
  if (item.availabilityStatus !== 'AVAILABLE') return '官方汇总中尚无可用分数，不进行推断';
  if (item.score100 !== null && item.scoreOver100 !== null) {
    return `单科（满分 100）${item.score100} · 单科（满分 > 100）${item.scoreOver100}`;
  }
  return `政治 ${item.politicsScore ?? '-'} · 外语 ${item.foreignLanguageScore ?? '-'} · 业务课一 ${item.subjectOneScore ?? '-'} · 业务课二 ${item.subjectTwoScore ?? '-'}`;
}

function SchoolBaseline({ lines, sources }: { lines: SchoolScoreLineInfo[]; sources: SourceInfo[] }) {
  if (lines.length === 0) return <div className="school-baseline is-missing"><strong>学校基本线待核验</strong><small>不能用国家线或其他院校分数代替</small></div>;
  return <div className="school-baseline-list">{lines.map((item) => (
    <div className={`school-baseline ${item.availabilityStatus === 'AVAILABLE' ? '' : 'is-missing'}`} key={`${item.year}-${item.categoryCode}-${item.degreeType}`}>
      <div><span>{item.year} 学校基本线 · {item.degreeType}</span><strong>{item.totalScore === null ? '尚未公布' : `${item.categoryName} ${item.totalScore}`}</strong></div>
      <p>{formatSchoolScoreSubjects(item)}</p>
      <small>{item.scopeNote}。学校基本线只是最低门槛，学院或专业可以上调。</small>
      <div className="school-baseline-links"><EvidenceLink sourceId={item.sourceId} sources={sources} />{item.imageUrl && <a href={item.imageUrl} target="_blank" rel="noreferrer"><ExternalLink size={12} />查看官方表格</a>}</div>
    </div>
  ))}</div>;
}

export function SchoolDetailView({ detail, onAskAi }: { detail: SchoolDetail | null; onAskAi: (question: string) => void }) {
  const [activeTab, setActiveTab] = useState<DetailTab>('overview');
  useEffect(() => setActiveTab('overview'), [detail?.summary.id]);
  if (!detail) return <div className="empty-state">未选择院校</div>;
  const completenessItems = [Boolean(detail.summary.primarySubject), detail.admissionPlans.length > 0, detail.nationalScoreLines.length > 0, detail.schoolScoreLines.length > 0, detail.scoreLines.length > 0, detail.admissionResults.length > 0, detail.retestRules.length > 0, detail.sources.length > 0];
  const completeness = Math.round(completenessItems.filter(Boolean).length / completenessItems.length * 100);
  const sourceUpdates = detail.sources.map((source) => source.updatedAt).filter(Boolean).sort();
  const latestSourceUpdate = sourceUpdates[sourceUpdates.length - 1];
  const tabs: Array<{ key: DetailTab; label: string }> = [
    { key: 'overview', label: '概览' }, { key: 'admissions', label: '招生与分数' }, { key: 'retest', label: '复试与书目' }, { key: 'results', label: '录取与调剂' }, { key: 'sources', label: `官方资料 ${detail.sources.length}` }
  ];
  return (
    <div className="detail-view">
      <div className="detail-summary-band"><div className="detail-identity"><span>{detail.summary.name.slice(0, 1)}</span><div><em>{formatRegionLabel(detail.summary.province, detail.summary.city)} · {detail.summary.schoolLevel}</em><h2>{detail.summary.name}</h2><p>{detail.collegeName} / {detail.majorName} / {detail.degreeType}</p></div></div><div className="detail-trust"><div><span>数据完整度<strong>{completeness}%</strong></span><i><b style={{ width: `${completeness}%` }} /></i></div><div><span>官方来源</span><strong>{detail.sources.filter((source) => source.official).length}</strong></div><div><span>最近核验</span><strong>{formatDateTime(latestSourceUpdate)}</strong></div></div></div>
      <div className="detail-command-bar"><button type="button" onClick={() => onAskAi(`请基于已收录官方资料分析${detail.summary.name}${detail.majorName || '计算机相关专业'}的报考风险，并明确哪些数据尚未核验`)}><Bot size={15} />分析报考风险</button><button type="button" onClick={() => onAskAi(`请总结${detail.summary.name}当前已收录的官方资料、复试规则和需要继续核验的字段`)}><ShieldCheck size={15} />总结官方资料</button></div>
      <div className="detail-tabs" role="tablist">{tabs.map((tab) => <button key={tab.key} type="button" role="tab" aria-selected={activeTab === tab.key} className={activeTab === tab.key ? 'active' : ''} onClick={() => setActiveTab(tab.key)}>{tab.label}</button>)}</div>
      {activeTab === 'overview' && <><dl><div><dt>主专业代码</dt><dd>{detail.majorCode || '待核验'}</dd></div><div><dt>研究方向</dt><dd>{detail.researchDirection || '待核验'}</dd></div><div><dt>学习方式</dt><dd>{detail.studyMode || '待核验'}</dd></div><div><dt>考试科目</dt><dd>{detail.summary.primarySubject || '待核验'}<EvidenceLink sourceId={detail.examSourceId} sources={detail.sources} /></dd></div></dl><section className="program-catalog"><div className="program-catalog-head"><h3>2026 计算机类 408 专业目录</h3><span>{detail.programs.length} 个组合</span></div>{detail.programs.length === 0 ? <div className="empty-state">尚未收录可核验的 408 专业组合</div> : <div className="program-list">{detail.programs.map((program, index) => <article key={`${program.majorId}-${program.year}-${program.politics}-${program.foreignLanguage}-${program.mathSubject}-${index}`}><div className="program-identity"><strong>{program.majorCode} {program.majorName}</strong><span>{program.collegeName} · {program.degreeType} · {program.studyMode}</span></div><div className="program-subjects"><span>{program.politics}</span><span>{program.foreignLanguage}</span><span>{program.mathSubject}</span><span className="is-408">{program.professionalSubject}</span></div>{program.researchDirection && <p>方向：{program.researchDirection}</p>}<div className="program-meta"><span>{program.year} 年</span><EvidenceLink sourceId={program.sourceId} sources={detail.sources} /></div></article>)}</div>}</section></>}
      {activeTab === 'admissions' && <div className="trend-grid"><div><h3>历年招生</h3><TrendInsight title="招生计划变化" metricLabel="招生计划" values={detail.quotas} />{detail.admissionPlans.length ? detail.admissionPlans.map((item) => <p key={item.year}><span>{item.year}：{formatAdmissionPlan(item)}<EvidenceLink sourceId={item.sourceId} sources={detail.sources} /></span>{item.remark && <small>{item.remark}</small>}</p>) : <p>尚未收录已核验招生计划</p>}</div><div className="score-thresholds"><h3>复试门槛</h3><div className="score-level"><em>1. 国家线</em><NationalBaseline lines={detail.nationalScoreLines} /></div><div className="score-level"><em>2. 学校基本线</em><SchoolBaseline lines={detail.schoolScoreLines} sources={detail.sources} /></div><div className="score-level"><em>3. 学院 / 专业复试线</em><TrendInsight title="学院或专业复试线变化" metricLabel="复试线" values={detail.scoreLines} />{detail.scoreLines.length ? detail.scoreLines.map((item) => <p key={item.year}>{item.year}：{item.value}<EvidenceLink sourceId={item.sourceId} sources={detail.sources} /></p>) : <p>尚未收录已核验学院或专业复试线，不能用前两级分数替代。</p>}</div></div></div>}
      {activeTab === 'retest' && <><div className="policy-list"><h3>复试细则</h3>{detail.retestRules.length === 0 ? <p>尚未收录已核验复试细则</p> : detail.retestRules.map((item) => <article key={`${item.scopeType}-${item.year}-${item.retestMethod}`}><div className="policy-head"><strong>{item.year}</strong><span>{item.scopeType === 'SCHOOL' ? '学校规则' : item.scopeType === 'COLLEGE' ? '学院规则' : '专业规则'}</span><span>{item.retestMethod || '-'}</span><span>比例 {item.retestRatio ?? '-'}</span><span>初试 {item.initialScoreWeight ?? '-'}%</span><span>复试 {item.retestScoreWeight ?? '-'}%</span><EvidenceLink sourceId={item.sourceId} sources={detail.sources} /></div><p>时间：{item.retestTime || '-'}</p><p>规则：{item.qualificationLine || '-'}</p><p>材料：{item.materials || '-'}</p>{item.remark && <p>备注：{item.remark}</p>}</article>)}</div><div className="book-list"><h3>参考书目</h3>{detail.referenceBooks.length === 0 ? <p>尚未收录已核验参考书目</p> : detail.referenceBooks.map((item) => <article key={`${item.year}-${item.bookTitle}-${item.author}`}><strong>{item.bookTitle || '-'}</strong><span>{item.year}</span><span>{item.subjectName || '-'}</span><span>作者 {item.author || '-'}</span><span>版本 {item.edition || '-'}</span><span>出版社 {item.publisher || '-'}</span><EvidenceLink sourceId={item.sourceId} sources={detail.sources} />{item.remark && <p>备注：{item.remark}</p>}</article>)}</div></>}
      {activeTab === 'results' && <><div className="result-list"><h3>历年录取结果</h3>{detail.admissionResults.length === 0 ? <p>尚未收录已核验录取结果</p> : detail.admissionResults.map((item) => <article key={item.year}><strong>{item.year}</strong><span>录取 {item.admittedCount ?? '-'}</span><span>最低分 {item.lowestScore ?? '-'}</span><span>平均分 {item.averageScore ?? '-'}</span><span>最高分 {item.highestScore ?? '-'}</span><span>复试比例 {item.retestRatio ?? '-'}</span><EvidenceLink sourceId={item.sourceId} sources={detail.sources} /></article>)}</div><div className="adjustment-list"><h3>调剂信息</h3>{detail.adjustmentInfos.length === 0 ? <p>尚未收录已核验调剂信息，不能据此判断调剂空间</p> : detail.adjustmentInfos.map((item) => <article key={`${item.year}-${item.title}`}><div className="adjustment-head"><strong>{item.title || '-'}</strong><span>{item.year}</span><span>{item.open ? '开放中' : '已关闭'}</span><span>缺额 {item.vacancyCount ?? '-'}</span><EvidenceLink sourceId={item.sourceId} sources={detail.sources} /></div><p>时间：{item.applicationWindow || '-'}</p><p>条件：{item.requirements || '-'}</p>{item.noticeUrl && <a href={item.noticeUrl} target="_blank" rel="noreferrer">查看公告</a>}{item.remark && <p>备注：{item.remark}</p>}</article>)}</div></>}
      {activeTab === 'sources' && <div className="source-list"><h3>官方资料来源</h3>{detail.sources.length === 0 ? <p>暂无已发布官方来源</p> : detail.sources.map((source) => <a key={source.title} href={source.sourceUrl} target="_blank" rel="noreferrer"><ExternalLink size={14} /><span>{source.year ?? '常设'} {source.title}</span><em>{auditLabel(source.auditStatus)} / 更新 {formatDateTime(source.updatedAt)}</em></a>)}</div>}
    </div>
  );
}

export function CompareMatrix({ schools, onlyDifferences }: { schools: CompareSchoolItem[]; onlyDifferences: boolean }) {
  const gridStyle = { gridTemplateColumns: `minmax(120px, 160px) repeat(${schools.length}, minmax(0, 1fr))` };
  const rows = buildComparisonRows(schools, onlyDifferences);
  return <div className="compare-matrix"><div className="compare-row compare-head" style={gridStyle}><div className="compare-cell compare-label">维度</div>{schools.map((school) => <div className="compare-cell compare-school" key={school.id}><strong>{school.name}</strong></div>)}</div>{rows.map((row) => <div className="compare-row" key={row.label} style={gridStyle}><div className="compare-cell compare-label">{row.label}</div>{row.values.map((value, index) => <div className="compare-cell" key={`${row.label}-${schools[index].id}`}>{value}</div>)}</div>)}{rows.length === 0 && <div className="compare-no-difference">当前维度没有差异</div>}</div>;
}

export function FavoriteSchoolList({ favorites, selectedIds, onChange, onAddToCompare, onRemoveFromCompare, onRemove, onDetail }: {
  favorites: FavoriteSchool[]; selectedIds: number[]; onChange: (schoolId: number, patch: Partial<FavoriteSchool>) => void; onAddToCompare: (schoolId: number) => void; onRemoveFromCompare: (schoolId: number) => void; onRemove: (schoolId: number) => void; onDetail: (schoolId: number) => void;
}) {
  if (favorites.length === 0) return <div className="empty-state">暂无候选院校</div>;
  return <div className="favorite-list">{favorites.map((item) => <article key={item.schoolId} className="favorite-card"><div className="favorite-card-header"><div><strong>{item.name}</strong><span>{item.regionLabel} / {item.schoolLevel}</span></div><div className="favorite-card-actions"><button type="button" className="link-button" onClick={() => onDetail(item.schoolId)}>详情</button>{selectedIds.includes(item.schoolId) ? <button type="button" className="link-button" onClick={() => onRemoveFromCompare(item.schoolId)}>移出对比</button> : <button type="button" className="link-button" onClick={() => onAddToCompare(item.schoolId)} disabled={selectedIds.length >= 4}>加入对比</button>}<button type="button" className="link-button" onClick={() => onRemove(item.schoolId)}>移除</button></div></div><div className="favorite-metrics"><span>{item.primarySubject || '-'}</span><span>{formatExamType(item.is408)}</span><span>目录计划 {item.latestQuota ?? '-'}</span><span>复试线 {item.latestScoreLine ?? '-'}</span></div><div className="favorite-editor"><label><span>分组</span><select value={item.groupTag} onChange={(event) => onChange(item.schoolId, { groupTag: event.target.value as FavoriteGroup })}><option value="冲刺">冲刺</option><option value="稳妥">稳妥</option><option value="保底">保底</option></select></label><label className="favorite-note"><span>个人备注</span><textarea aria-label={`${item.name}个人备注`} maxLength={300} rows={3} value={item.note} onChange={(event) => onChange(item.schoolId, { note: event.target.value, noteUpdatedAt: new Date().toISOString() })} placeholder="记录地域、方向、复试风险或待核验事项" /><small><span>{item.note.length}/300</span><span>已保存到本机 · {formatDateTime(item.noteUpdatedAt ?? item.savedAt)}</span></small></label></div><em>加入时间 {formatDateTime(item.savedAt)}</em></article>)}</div>;
}
