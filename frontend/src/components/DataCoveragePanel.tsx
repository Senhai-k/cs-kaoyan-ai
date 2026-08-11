import { AlertTriangle, ChevronRight, Database, ShieldCheck } from 'lucide-react';
import type { DataCoverageReport } from '../types';
import { formatRegionLabel } from '../formatters';

export function DataCoveragePanel({ report, onSelectSchool }: {
  report: DataCoverageReport | null;
  onSelectSchool?: (schoolId: number) => void;
}) {
  if (!report) {
    return <section className="coverage-panel coverage-loading" aria-label="数据覆盖"><span /><span /><span /></section>;
  }

  const priorities = report.schools.slice(0, 5);
  const decisionDimensions = report.dimensions.filter((item) =>
    ['admissionPlan', 'nationalBaseline', 'schoolBaseline', 'scoreLine', 'admissionResult', 'retestRule'].includes(item.key)
  );
  return (
    <section className="coverage-panel">
      <div className="coverage-summary">
        <div className="section-heading">
          <div><span className="section-kicker"><Database size={14} />数据建设进度</span><h2>真实字段覆盖</h2></div>
          <strong>{report.averageCoveragePercent}%</strong>
        </div>
        <div className="coverage-progress"><span style={{ width: `${report.averageCoveragePercent}%` }} /></div>
        <dl>
          <div><dt>院校</dt><dd>{report.schoolCount}</dd></div>
          <div><dt>达到 75%</dt><dd>{report.readySchoolCount}</dd></div>
          <div><dt>官方证据</dt><dd>{report.officialSourceCount + report.officialDocumentCount}</dd></div>
        </dl>
        <div className="coverage-dimensions" aria-label="决策字段覆盖明细">
          {decisionDimensions.map((item) => <div key={item.key}>
            <span><strong>{item.label}</strong><em>{item.coveredSchoolCount} / {item.totalSchoolCount}</em></span>
            <i><b style={{ width: `${item.coveragePercent}%` }} /></i>
          </div>)}
        </div>
      </div>
      <div className="coverage-priorities">
        <div className="section-heading">
          <div><span className="section-kicker"><AlertTriangle size={14} />优先补齐</span><h2>缺失维度最多的院校</h2></div>
          <ShieldCheck size={19} />
        </div>
        <div>
          {priorities.map((school) => (
            <article key={school.schoolId}>
              {onSelectSchool ? <button type="button" onClick={() => onSelectSchool(school.schoolId)}>
                <span className="coverage-score">{school.coveragePercent}%</span>
                <span className="coverage-school"><strong>{school.name}</strong><em>{formatRegionLabel(school.province, school.city)}</em></span>
                <span className="coverage-missing">{school.missingDimensions.slice(0, 3).map((item) => <i key={item}>{item}</i>)}</span>
                <ChevronRight size={15} />
              </button> : <div className="coverage-priority-row">
                <span className="coverage-score">{school.coveragePercent}%</span>
                <span className="coverage-school"><strong>{school.name}</strong><em>{formatRegionLabel(school.province, school.city)}</em></span>
                <span className="coverage-missing">{school.missingDimensions.slice(0, 3).map((item) => <i key={item}>{item}</i>)}</span>
              </div>}
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
