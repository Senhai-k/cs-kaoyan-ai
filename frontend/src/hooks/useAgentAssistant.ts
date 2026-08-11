import { useEffect, useState } from 'react';
import { requestJson } from '../api';
import type {
  AgentDiagnostics, AgentEvaluation, AgentIndexResult, AgentStatus, AiChatResponse, AiConversation, CoverageWorkflow,
  CoverageEvaluation, CoverageWorkflowMetrics, CoverageWorkflowRun, KnowledgeAudit,
  OperationJob, OperationJobTrace, OperationType, PlannerEvaluation, RerankerBenchmark
} from '../types';

export function latestCompletedOperationResults(jobs: OperationJob[]): OperationJob[] {
  const appliedTypes = new Set<string>();
  return jobs.filter((job) => {
    const resultGroup = job.operation_type === 'PLANNER_EVALUATION' || job.operation_type === 'PLANNER_REPLAY'
      ? 'PLANNER'
      : job.operation_type;
    if (job.status !== 'COMPLETED' || appliedTypes.has(resultGroup)) return false;
    appliedTypes.add(resultGroup);
    return true;
  });
}

export function useAgentAssistant({
  active,
  adminToken,
  onOpen
}: {
  active: boolean;
  adminToken: string;
  onOpen: () => void;
}) {
  const [question, setQuestion] = useState('帮我分析南京大学计算机专硕的报考风险');
  const [answer, setAnswer] = useState<AiChatResponse | null>(null);
  const [conversations, setConversations] = useState<AiConversation[]>([]);
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<AgentStatus | null>(null);
  const [evaluation, setEvaluation] = useState<AgentEvaluation | null>(null);
  const [indexResult, setIndexResult] = useState<AgentIndexResult | null>(null);
  const [operationLoading, setOperationLoading] = useState(false);
  const [operationMessage, setOperationMessage] = useState('');
  const [workflowSchool, setWorkflowSchool] = useState('浙江大学');
  const [workflowFeedback, setWorkflowFeedback] = useState('已核对页面标题、年份、院校和资料类型，同意发布。');
  const [coverageWorkflow, setCoverageWorkflow] = useState<CoverageWorkflow | null>(null);
  const [workflowRuns, setWorkflowRuns] = useState<CoverageWorkflowRun[]>([]);
  const [workflowMetrics, setWorkflowMetrics] = useState<CoverageWorkflowMetrics | null>(null);
  const [coverageEvaluation, setCoverageEvaluation] = useState<CoverageEvaluation | null>(null);
  const [knowledgeAudit, setKnowledgeAudit] = useState<KnowledgeAudit | null>(null);
  const [plannerEvaluation, setPlannerEvaluation] = useState<PlannerEvaluation | null>(null);
  const [rerankerBenchmark, setRerankerBenchmark] = useState<RerankerBenchmark | null>(null);
  const [operationJobs, setOperationJobs] = useState<OperationJob[]>([]);
  const [operationTrace, setOperationTrace] = useState<OperationJobTrace | null>(null);
  const [diagnostics, setDiagnostics] = useState<AgentDiagnostics | null>(null);
  const [diagnosticQuery, setDiagnosticQuery] = useState('');
  const [diagnosticCategory, setDiagnosticCategory] = useState('ALL');
  const [diagnosticSeverity, setDiagnosticSeverity] = useState('ALL');

  const loadConversations = () => {
    requestJson<AiConversation[]>('/api/ai/conversations?limit=5')
      .then((payload) => setConversations(payload.data ?? []))
      .catch(() => setConversations([]));
  };

  const loadStatus = () => {
    requestJson<AgentStatus>('/api/ai/agent/status')
      .then((payload) => setStatus(payload.data))
      .catch((requestError: Error) => setStatus({
        available: false, status: 'DOWN', framework: 'LangGraph', capabilities: [], indexedChunks: 0,
        embeddingModel: '', rerankerEnabled: false, rerankerMode: 'off', generationMode: '', otlpExporterEnabled: false,
        plannerLlm: { configured: false, experimentReady: false, status: 'UNAVAILABLE', model: '', endpointType: '', pricingMode: 'METERED', missingConfiguration: [], pricingUnit: 'USD_PER_MILLION_TOKENS' },
        metrics: { totalTasks: 0, completedTasks: 0, waitingTasks: 0, failedTasks: 0, toolCalls: 0, successfulToolCalls: 0, averageLatencyMs: 0, taskCompletionRate: 0, toolSuccessRate: 0 },
        message: requestError.message
      }));
  };

  const loadWorkflowTelemetry = () => {
    if (!adminToken) {
      setWorkflowRuns([]);
      setWorkflowMetrics(null);
      return;
    }
    const headers = { Authorization: `Bearer ${adminToken}` };
    Promise.all([
      requestJson<CoverageWorkflowRun[]>('/api/ai/agent/operations/coverage-workflows/runs?limit=8', { headers }),
      requestJson<CoverageWorkflowMetrics>('/api/ai/agent/operations/coverage-workflows/metrics', { headers })
    ])
      .then(([runsPayload, metricsPayload]) => {
        setWorkflowRuns(runsPayload.data ?? []);
        setWorkflowMetrics(metricsPayload.data);
      })
      .catch(() => {
        setWorkflowRuns([]);
        setWorkflowMetrics(null);
      });
  };

  const applyOperationResult = (job: OperationJob) => {
    if (job.status !== 'COMPLETED' || !job.result) return;
    if (job.operation_type === 'INDEX_SYNC') setIndexResult(job.result as AgentIndexResult);
    if (job.operation_type === 'RAG_EVALUATION') setEvaluation(job.result as AgentEvaluation);
    if (job.operation_type === 'COVERAGE_EVALUATION') setCoverageEvaluation(job.result as CoverageEvaluation);
    if (job.operation_type === 'KNOWLEDGE_AUDIT') setKnowledgeAudit(job.result as KnowledgeAudit);
    if (job.operation_type === 'PLANNER_EVALUATION' || job.operation_type === 'PLANNER_REPLAY') {
      setPlannerEvaluation(job.result as PlannerEvaluation);
    }
    if (job.operation_type === 'RERANKER_BENCHMARK') {
      setRerankerBenchmark(job.result as RerankerBenchmark);
    }
  };

  const loadOperationJobs = () => {
    if (!adminToken) {
      setOperationJobs([]);
      return;
    }
    requestJson<OperationJob[]>('/api/ai/agent/operations/jobs?limit=8', {
      headers: { Authorization: `Bearer ${adminToken}` }
    })
      .then((payload) => {
        const jobs = payload.data ?? [];
        setOperationJobs(jobs);
        latestCompletedOperationResults(jobs).forEach(applyOperationResult);
      })
      .catch(() => setOperationJobs([]));
  };

  const loadDiagnostics = () => {
    if (!adminToken) {
      setDiagnostics(null);
      return;
    }
    const params = new URLSearchParams({
      query: diagnosticQuery.trim(), category: diagnosticCategory, severity: diagnosticSeverity, limit: '50'
    });
    requestJson<AgentDiagnostics>(`/api/ai/agent/operations/diagnostics?${params}`, {
      headers: { Authorization: `Bearer ${adminToken}` }
    })
      .then((payload) => setDiagnostics(payload.data))
      .catch((requestError: Error) => setOperationMessage(requestError.message));
  };

  useEffect(() => {
    if (active) {
      loadConversations();
      loadStatus();
      loadWorkflowTelemetry();
      loadOperationJobs();
      loadDiagnostics();
    }
  }, [active, adminToken]);

  useEffect(() => {
    if (!active || !adminToken) return;
    const timer = window.setInterval(loadOperationJobs, 1500);
    return () => window.clearInterval(timer);
  }, [active, adminToken]);

  useEffect(() => {
    if (!active || !adminToken) return;
    const timer = window.setInterval(loadDiagnostics, 5000);
    return () => window.clearInterval(timer);
  }, [active, adminToken, diagnosticCategory, diagnosticSeverity]);

  const ask = () => {
    if (!question.trim()) return;
    setLoading(true);
    requestJson<AiChatResponse>('/api/ai/chat', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ question })
    })
      .then((payload) => {
        setAnswer(payload.data);
        loadConversations();
      })
      .catch((requestError: Error) => setAnswer({ answer: requestError.message, sources: [] }))
      .finally(() => {
        setLoading(false);
        loadStatus();
      });
  };

  const startOperation = (operationType: OperationType) => {
    if (!adminToken) return;
    setOperationLoading(true);
    setOperationMessage('');
    requestJson<OperationJob>('/api/ai/agent/operations/jobs', {
      method: 'POST',
      headers: { Authorization: `Bearer ${adminToken}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ operation_type: operationType })
    })
      .then((payload) => {
        setOperationJobs((current) => [payload.data, ...current.filter((item) => item.id !== payload.data.id)]);
        setOperationMessage('异步任务已提交');
      })
      .catch((requestError: Error) => setOperationMessage(requestError.message))
      .finally(() => setOperationLoading(false));
  };

  const updateOperationJob = (jobId: string, action: 'cancel' | 'retry') => {
    if (!adminToken) return;
    requestJson<OperationJob>(`/api/ai/agent/operations/jobs/${encodeURIComponent(jobId)}/${action}`, {
      method: 'POST', headers: { Authorization: `Bearer ${adminToken}` }
    })
      .then(() => {
        setOperationMessage(action === 'cancel' ? '已请求取消任务' : '已创建重试任务');
        loadOperationJobs();
      })
      .catch((requestError: Error) => setOperationMessage(requestError.message));
  };

  const loadOperationTrace = (jobId: string) => {
    if (!adminToken) return;
    requestJson<OperationJobTrace>(`/api/ai/agent/operations/jobs/${encodeURIComponent(jobId)}/trace`, {
      headers: { Authorization: `Bearer ${adminToken}` }
    })
      .then((payload) => setOperationTrace(payload.data))
      .catch((requestError: Error) => setOperationMessage(requestError.message));
  };

  const runCoverageRequest = (path: string, body: object, successMessage: string) => {
    if (!adminToken) return;
    setOperationLoading(true);
    setOperationMessage('');
    requestJson<CoverageWorkflow>(path, {
      method: 'POST',
      headers: { Authorization: `Bearer ${adminToken}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
      .then((payload) => {
        setCoverageWorkflow(payload.data);
        setOperationMessage(successMessage);
        loadStatus();
        loadWorkflowTelemetry();
      })
      .catch((requestError: Error) => setOperationMessage(requestError.message))
      .finally(() => setOperationLoading(false));
  };

  const startCoverageWorkflow = () => {
    if (!workflowSchool.trim()) return;
    runCoverageRequest(
      '/api/ai/agent/operations/coverage-workflows',
      { school_name: workflowSchool.trim(), max_targets: 3 },
      '资料采集完成，等待人工审核'
    );
  };

  const resumeCoverageWorkflow = (approved: boolean) => {
    if (!coverageWorkflow) return;
    runCoverageRequest(
      `/api/ai/agent/operations/coverage-workflows/${encodeURIComponent(coverageWorkflow.thread_id)}/resume`,
      { approved, feedback: workflowFeedback.trim() },
      approved ? '证据已发布，索引与评估已更新' : '本次发布已驳回'
    );
  };

  return {
    openWithQuestion: (nextQuestion: string) => {
      setQuestion(nextQuestion);
      setAnswer(null);
      onOpen();
    },
    workspaceProps: {
      status,
      evaluation,
      indexResult,
      answer,
      conversations,
      question,
      loading,
      operationLoading,
      operationMessage,
      workflowSchool,
      workflowFeedback,
      coverageWorkflow,
      workflowRuns,
      workflowMetrics,
      coverageEvaluation,
      knowledgeAudit,
      plannerEvaluation,
      rerankerBenchmark,
      operationJobs,
      operationTrace,
      diagnostics,
      diagnosticQuery,
      diagnosticCategory,
      diagnosticSeverity,
      adminEnabled: Boolean(adminToken),
      onQuestionChange: setQuestion,
      onAsk: ask,
      onRefresh: () => { loadStatus(); loadWorkflowTelemetry(); loadOperationJobs(); loadDiagnostics(); },
      onEvaluate: () => startOperation('RAG_EVALUATION'),
      onEvaluateCoverage: () => startOperation('COVERAGE_EVALUATION'),
      onAuditKnowledge: () => startOperation('KNOWLEDGE_AUDIT'),
      onEvaluatePlanner: () => startOperation('PLANNER_EVALUATION'),
      onReplayPlanner: () => startOperation('PLANNER_REPLAY'),
      onBenchmarkReranker: () => startOperation('RERANKER_BENCHMARK'),
      onSync: () => startOperation('INDEX_SYNC'),
      onCancelOperation: (jobId: string) => updateOperationJob(jobId, 'cancel'),
      onRetryOperation: (jobId: string) => updateOperationJob(jobId, 'retry'),
      onViewOperationTrace: loadOperationTrace,
      onDiagnosticQueryChange: setDiagnosticQuery,
      onDiagnosticCategoryChange: setDiagnosticCategory,
      onDiagnosticSeverityChange: setDiagnosticSeverity,
      onRefreshDiagnostics: loadDiagnostics,
      onWorkflowSchoolChange: setWorkflowSchool,
      onWorkflowFeedbackChange: setWorkflowFeedback,
      onStartCoverageWorkflow: startCoverageWorkflow,
      onResumeCoverageWorkflow: resumeCoverageWorkflow
    }
  };
}
