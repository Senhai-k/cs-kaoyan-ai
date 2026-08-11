import type { YearValue } from './types';

export type TrendDirection = 'UP' | 'DOWN' | 'STABLE' | 'INSUFFICIENT';

export type TrendSummary = {
  direction: TrendDirection;
  label: string;
  detail: string;
  years: number;
  latestYear: number | null;
  latestValue: number | null;
  delta: number | null;
  minimum: number | null;
  maximum: number | null;
};

export function summarizeTrend(values: YearValue[], metricLabel: string): TrendSummary {
  const byYear = new Map<number, number>();
  values.forEach((item) => {
    if (Number.isFinite(item.year) && Number.isFinite(item.value) && !byYear.has(item.year)) {
      byYear.set(item.year, item.value);
    }
  });
  const series = [...byYear.entries()].sort(([first], [second]) => first - second);
  const latest = series.length ? series[series.length - 1] : undefined;
  const allValues = series.map(([, value]) => value);
  const minimum = allValues.length ? Math.min(...allValues) : null;
  const maximum = allValues.length ? Math.max(...allValues) : null;

  if (series.length < 2 || !latest) {
    return {
      direction: 'INSUFFICIENT', label: '数据不足',
      detail: series.length === 1 ? `仅有 ${latest?.[0]} 年数据，暂不能判断${metricLabel}趋势` : `暂无可比较的${metricLabel}年度数据`,
      years: series.length, latestYear: latest?.[0] ?? null, latestValue: latest?.[1] ?? null,
      delta: null, minimum, maximum
    };
  }

  const previous = series[series.length - 2];
  const delta = latest[1] - previous[1];
  const direction: TrendDirection = delta > 0 ? 'UP' : delta < 0 ? 'DOWN' : 'STABLE';
  const label = direction === 'UP' ? '较前期上升' : direction === 'DOWN' ? '较前期下降' : '与前期持平';
  const signedDelta = delta > 0 ? `+${delta}` : String(delta);
  return {
    direction,
    label,
    detail: `${previous[0]} 至 ${latest[0]}：${previous[1]} -> ${latest[1]}（${signedDelta}），已收录范围 ${minimum}-${maximum}`,
    years: series.length,
    latestYear: latest[0],
    latestValue: latest[1],
    delta,
    minimum,
    maximum
  };
}

export function formatTrendForComparison(values: YearValue[], metricLabel: string) {
  const trend = summarizeTrend(values, metricLabel);
  if (trend.direction === 'INSUFFICIENT') return trend.detail;
  return `${trend.label} ${trend.delta && trend.delta > 0 ? '+' : ''}${trend.delta}；${trend.years} 年数据`;
}
