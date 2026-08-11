import { describe, expect, it } from 'vitest';
import { coveragePhaseLabel, diagnosticCategoryLabel, operationTypeLabel, parseAgentSource } from './AgentWorkspace';

describe('parseAgentSource', () => {
  it('separates the trailing official URL from the citation label', () => {
    expect(parseAgentSource('[1] 北京邮电大学 / 2026 / 招生目录 / https://yz.chsi.com.cn/')).toEqual({
      label: '[1] 北京邮电大学 / 2026 / 招生目录',
      url: 'https://yz.chsi.com.cn/'
    });
  });

  it('keeps non-link citations readable', () => {
    expect(parseAgentSource('[1] 年份未标注 / 校内资料')).toEqual({
      label: '[1] 年份未标注 / 校内资料',
      url: null
    });
  });
});

describe('coveragePhaseLabel', () => {
  it('uses concise Chinese labels for workflow phases', () => {
    expect(coveragePhaseLabel('WAITING_HUMAN')).toBe('等待审核');
    expect(coveragePhaseLabel('COMPLETED')).toBe('已完成');
  });
});

describe('operationTypeLabel', () => {
  it('maps persisted operation types to compact labels', () => {
    expect(operationTypeLabel('RAG_EVALUATION')).toBe('RAG 评估');
    expect(operationTypeLabel('KNOWLEDGE_AUDIT')).toBe('知识审计');
    expect(operationTypeLabel('PLANNER_EVALUATION')).toBe('规划器 A/B');
    expect(operationTypeLabel('PLANNER_REPLAY')).toBe('规划器回放');
    expect(operationTypeLabel('RERANKER_BENCHMARK')).toBe('重排基准');
  });
});

describe('diagnosticCategoryLabel', () => {
  it('maps machine categories to operational Chinese labels', () => {
    expect(diagnosticCategoryLabel('QUALITY_GATE')).toBe('质量门禁');
    expect(diagnosticCategoryLabel('PLANNER_FILTER')).toBe('规划过滤');
  });
});
