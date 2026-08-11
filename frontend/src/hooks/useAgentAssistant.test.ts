import { describe, expect, it } from 'vitest';
import { latestCompletedOperationResults } from './useAgentAssistant';
import type { OperationJob, OperationType } from '../types';

function job(id: string, operationType: OperationType, status: OperationJob['status']): OperationJob {
  return {
    id,
    operation_type: operationType,
    status,
    progress_current: 1,
    progress_total: 1,
    progress_message: '',
    attempt: 1,
    parent_job_id: null,
    result: status === 'COMPLETED' ? { id } : null,
    error: '',
    cancel_requested: false,
    created_at: '',
    started_at: null,
    updated_at: '',
    completed_at: null,
    correlation_id: '',
    trace_id: '',
    parent_span_id: null,
  };
}

describe('latestCompletedOperationResults', () => {
  it('keeps only the newest completed result for each operation type', () => {
    const jobs = [
      job('running', 'RAG_EVALUATION', 'RUNNING'),
      job('new-rag', 'RAG_EVALUATION', 'COMPLETED'),
      job('audit', 'KNOWLEDGE_AUDIT', 'COMPLETED'),
      job('old-rag', 'RAG_EVALUATION', 'COMPLETED'),
    ];

    expect(latestCompletedOperationResults(jobs).map((item) => item.id))
      .toEqual(['new-rag', 'audit']);
  });

  it('treats planner replay as the same result stream as planner evaluation', () => {
    const jobs = [
      job('replay', 'PLANNER_REPLAY', 'COMPLETED'),
      job('old-planner', 'PLANNER_EVALUATION', 'COMPLETED'),
    ];

    expect(latestCompletedOperationResults(jobs).map((item) => item.id)).toEqual(['replay']);
  });
});
