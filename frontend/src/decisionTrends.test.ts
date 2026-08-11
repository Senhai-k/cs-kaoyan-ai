import { describe, expect, it } from 'vitest';
import { formatTrendForComparison, summarizeTrend } from './decisionTrends';

describe('decision trends', () => {
  it('refuses to infer a trend from fewer than two years', () => {
    expect(summarizeTrend([{ year: 2026, value: 350, sourceId: 1 }], '复试线')).toMatchObject({
      direction: 'INSUFFICIENT', years: 1, latestValue: 350
    });
  });

  it('sorts years and reports the latest change without mutating input', () => {
    const values = [{ year: 2026, value: 355, sourceId: 2 }, { year: 2025, value: 345, sourceId: 1 }];
    expect(summarizeTrend(values, '复试线')).toMatchObject({
      direction: 'UP', delta: 10, minimum: 345, maximum: 355, latestYear: 2026
    });
    expect(values[0].year).toBe(2026);
  });

  it('deduplicates the same year before calculating', () => {
    expect(summarizeTrend([
      { year: 2025, value: 50, sourceId: 1 },
      { year: 2025, value: 60, sourceId: 2 },
      { year: 2026, value: 45, sourceId: 3 }
    ], '招生计划')).toMatchObject({ direction: 'DOWN', years: 2, delta: -5 });
  });

  it('formats a concise comparison value', () => {
    expect(formatTrendForComparison([
      { year: 2025, value: 50, sourceId: 1 },
      { year: 2026, value: 50, sourceId: 2 }
    ], '招生计划')).toBe('与前期持平 0；2 年数据');
  });
});
