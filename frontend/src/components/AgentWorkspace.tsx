import { Activity, AlertTriangle, Ban, Bot, CheckCircle2, Clock3, Database, Download, ExternalLink, FileJson2, GitBranch, Info, Play, RefreshCw, RotateCcw, Search, ShieldCheck, XCircle } from 'lucide-react';
import { useState } from 'react';
import type { AgentDiagnostics, AgentEvaluation, AgentIndexResult, AgentStatus, AiChatResponse, AiConversation, CoverageEvaluation, CoverageWorkflow, CoverageWorkflowMetrics, CoverageWorkflowRun, KnowledgeAudit, OperationJob, OperationJobTrace, OperationType, PlannerEvaluation, RerankerBenchmark } from '../types';
import { formatDateTime } from '../formatters';

export function parseAgentSource(source: string): { label: string; url: string | null } {
  const match = source.match(/\s\/\s(https?:\/\/\S+)$/);
  if (!match) return { label: source, url: null };
  return { label: source.slice(0, match.index), url: match[1] };
}

function percent(value: number | undefined): string {
  return `${Math.round((value ?? 0) * 100)}%`;
}

function plannerConfigurationLabel(key: string): string {
  return ({
    AGENT_OPENAI_API_KEY: 'API Key',
    AGENT_OPENAI_MODEL: '模型名称',
    AGENT_PLANNER_INPUT_COST_PER_MILLION_USD: '输入费率',
    AGENT_PLANNER_OUTPUT_COST_PER_MILLION_USD: '输出费率'
  } as Record<string, string>)[key] ?? key;
}

export function coveragePhaseLabel(phase: string): string {
  return ({
    PLANNING: '规划', COLLECTING: '并行采集', WAITING_HUMAN: '等待审核',
    PUBLISHING: '发布', INDEXING: '索引重建', EVALUATING: '质量评估',
    COMPLETED: '已完成', REJECTED: '已驳回'
  } as Record<string, string>)[phase] ?? phase;
}

export function operationTypeLabel(type: OperationType): string {
  return ({ INDEX_SYNC: '索引同步', RAG_EVALUATION: 'RAG 评估', COVERAGE_EVALUATION: '证据策略', KNOWLEDGE_AUDIT: '知识审计', PLANNER_EVALUATION: '规划器 A/B', PLANNER_REPLAY: '规划器回放', RERANKER_BENCHMARK: '重排基准' })[type];
}

export function diagnosticCategoryLabel(category: string): string {
  return ({ OPERATION_FAILURE: '任务失败', QUALITY_GATE: '质量门禁', PLANNER_FILTER: '规划过滤', EVIDENCE_POLICY: '证据策略', KNOWLEDGE_AUDIT: '知识审计', WORKFLOW: '工作流' } as Record<string, string>)[category] ?? category;
}

function downloadTrace(trace: OperationJobTrace): void {
  const blob = new Blob([JSON.stringify(trace, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `operation-trace-${trace.job.id}.json`;
  anchor.click();
  URL.revokeObjectURL(url);
}

export function AgentWorkspace({
  status, evaluation, indexResult, answer, conversations, question, loading, operationLoading,
  operationMessage, adminEnabled, workflowSchool, workflowFeedback, coverageWorkflow, workflowRuns, workflowMetrics, coverageEvaluation, knowledgeAudit, plannerEvaluation, rerankerBenchmark, operationJobs, operationTrace,
  diagnostics, diagnosticQuery, diagnosticCategory, diagnosticSeverity,
  onQuestionChange, onAsk, onRefresh, onEvaluate, onSync, onWorkflowSchoolChange,
  onWorkflowFeedbackChange, onStartCoverageWorkflow, onResumeCoverageWorkflow, onEvaluateCoverage, onAuditKnowledge,
  onCancelOperation, onRetryOperation, onViewOperationTrace, onEvaluatePlanner, onReplayPlanner, onBenchmarkReranker,
  onDiagnosticQueryChange, onDiagnosticCategoryChange, onDiagnosticSeverityChange, onRefreshDiagnostics
}: {
  status: AgentStatus | null;
  evaluation: AgentEvaluation | null;
  indexResult: AgentIndexResult | null;
  answer: AiChatResponse | null;
  conversations: AiConversation[];
  question: string;
  loading: boolean;
  operationLoading: boolean;
  operationMessage: string;
  adminEnabled: boolean;
  workflowSchool: string;
  workflowFeedback: string;
  coverageWorkflow: CoverageWorkflow | null;
  workflowRuns: CoverageWorkflowRun[];
  workflowMetrics: CoverageWorkflowMetrics | null;
  coverageEvaluation: CoverageEvaluation | null;
  knowledgeAudit: KnowledgeAudit | null;
  plannerEvaluation: PlannerEvaluation | null;
  rerankerBenchmark: RerankerBenchmark | null;
  operationJobs: OperationJob[];
  operationTrace: OperationJobTrace | null;
  diagnostics: AgentDiagnostics | null;
  diagnosticQuery: string;
  diagnosticCategory: string;
  diagnosticSeverity: string;
  onQuestionChange: (value: string) => void;
  onAsk: () => void;
  onRefresh: () => void;
  onEvaluate: () => void;
  onSync: () => void;
  onWorkflowSchoolChange: (value: string) => void;
  onWorkflowFeedbackChange: (value: string) => void;
  onStartCoverageWorkflow: () => void;
  onResumeCoverageWorkflow: (approved: boolean) => void;
  onEvaluateCoverage: () => void;
  onAuditKnowledge: () => void;
  onCancelOperation: (jobId: string) => void;
  onRetryOperation: (jobId: string) => void;
  onViewOperationTrace: (jobId: string) => void;
  onEvaluatePlanner: () => void;
  onReplayPlanner: () => void;
  onBenchmarkReranker: () => void;
  onDiagnosticQueryChange: (value: string) => void;
  onDiagnosticCategoryChange: (value: string) => void;
  onDiagnosticSeverityChange: (value: string) => void;
  onRefreshDiagnostics: () => void;
}) {
  const suggestions = [
    '北京邮电大学计算机学院085404采用什么初试科目？',
    '华中科技大学2026年复试成绩权重是什么？',
    '北京邮电大学知识库收录了哪些年份和资料？'
  ];
  const metrics = status?.metrics;
  const [adminView, setAdminView] = useState<'operations' | 'workflow' | 'diagnostics' | 'chat'>('operations');

  return <div className="agent-workspace">
    {adminEnabled && <section className={`agent-status-band ${status?.available ? 'is-up' : 'is-down'}`}>
      <div className="agent-status-title">
        <span><Bot size={19} /></span>
        <div><strong>LangGraph Agent</strong><em>{status?.available ? `${status.generationMode} / ${status.rerankerMode} reranker${status.otlpExporterEnabled ? ' / OTLP' : ''}` : '服务不可用'}</em></div>
      </div>
      <div className="agent-status-actions">
        <span className="agent-health"><i />{status?.status ?? 'LOADING'}</span>
        <button type="button" className="icon-action" onClick={onRefresh} title="刷新 Agent 状态"><RefreshCw size={16} /></button>
      </div>
    </section>}

    {adminEnabled && <section className="agent-metric-strip" aria-label="Agent 运行指标">
      <div><Database size={17} /><span>知识切片</span><strong>{status?.indexedChunks ?? '-'}</strong></div>
      <div><ShieldCheck size={17} /><span>任务完成率</span><strong>{percent(metrics?.taskCompletionRate)}</strong></div>
      <div><Activity size={17} /><span>工具成功率</span><strong>{percent(metrics?.toolSuccessRate)}</strong></div>
      <div><Clock3 size={17} /><span>平均延迟</span><strong>{metrics ? `${Math.round(metrics.averageLatencyMs)} ms` : '-'}</strong></div>
    </section>}

    {status && !status.available && <div className="agent-warning" role="alert">{adminEnabled ? (status.message || 'Agent 当前不可用，问答将由 Spring 本地检索降级处理。') : '智能问答暂不可用，请稍后重试。'}</div>}

    {adminEnabled && <nav className="admin-workspace-tabs" aria-label="Agent 运维工作区">
      <button type="button" className={adminView === 'operations' ? 'active' : ''} onClick={() => setAdminView('operations')}>运行评估</button>
      <button type="button" className={adminView === 'workflow' ? 'active' : ''} onClick={() => setAdminView('workflow')}>证据工作流</button>
      <button type="button" className={adminView === 'diagnostics' ? 'active' : ''} onClick={() => setAdminView('diagnostics')}>诊断记录</button>
      <button type="button" className={adminView === 'chat' ? 'active' : ''} onClick={() => setAdminView('chat')}>问答抽检</button>
    </nav>}

    {(!adminEnabled || adminView === 'chat') && <div className="agent-main-grid">
      <section className="agent-chat-area">
        <div className="agent-section-heading"><h2>知识库问答</h2><em>Ctrl + Enter</em></div>
        <div className="agent-suggestions">
          {suggestions.map((item) => <button type="button" key={item} onClick={() => onQuestionChange(item)}>{item}</button>)}
        </div>
        <div className="agent-composer">
          <textarea value={question} onChange={(event) => onQuestionChange(event.target.value)} onKeyDown={(event) => { if (event.ctrlKey && event.key === 'Enter') onAsk(); }} placeholder="输入院校、专业、年份和要核验的问题" />
          <button type="button" onClick={onAsk} disabled={loading || !question.trim()}>
            {loading ? <RefreshCw className="spin" size={17} /> : <Bot size={17} />}{loading ? '检索中' : '提问'}
          </button>
        </div>

        <div className="agent-answer" aria-live="polite">
          {answer ? <>
            <div className="agent-answer-label"><ShieldCheck size={16} /><span>有依据回答</span></div>
            {answer.meta && <div className="agent-answer-meta">
              {adminEnabled && <span>{answer.meta.provider}</span>}<span>置信度 {percent(answer.meta.confidence)}</span>
              <span>证据 {answer.meta.retrievalCount}</span>{adminEnabled && <span>{answer.meta.route}</span>}
            </div>}
            <div className="agent-answer-text">{answer.answer}</div>
            {answer.sources.length > 0 && <div className="agent-source-list">
              {answer.sources.map((source, index) => {
                const parsed = parseAgentSource(source);
                return parsed.url
                  ? <a key={`${source}-${index}`} href={parsed.url} target="_blank" rel="noreferrer"><span>{parsed.label}</span><ExternalLink size={14} /></a>
                  : <div key={`${source}-${index}`}><span>{parsed.label}</span></div>;
              })}
            </div>}
          </> : <div className="agent-empty-answer"><Bot size={22} /><strong>等待问题</strong></div>}
        </div>
      </section>

      <aside className="agent-history">
        <div className="agent-section-heading"><h2>最近问答</h2></div>
        {conversations.length === 0 ? <div className="agent-empty-history">暂无问答记录</div> : conversations.map((item) => <article key={item.id}>
          <strong>{item.question}</strong>
          <p>{item.answer}</p>
          <span>{formatDateTime(item.createdAt)}</span>
        </article>)}
      </aside>
    </div>}

    {adminEnabled && adminView === 'operations' && <section className="agent-operations">
      <div><strong>索引与评估</strong>{operationMessage && <em>{operationMessage}</em>}</div>
      <div className="agent-operation-actions">
        <button type="button" onClick={onSync} disabled={operationLoading}><RefreshCw size={15} />同步索引</button>
        <button type="button" onClick={onEvaluate} disabled={operationLoading}><Play size={15} />运行评估</button>
        <button type="button" onClick={onEvaluateCoverage} disabled={operationLoading}><ShieldCheck size={15} />验证策略</button>
        <button type="button" onClick={onAuditKnowledge} disabled={operationLoading}><Database size={15} />审计知识库</button>
        <button type="button" onClick={onEvaluatePlanner} disabled={operationLoading || !status?.plannerLlm?.experimentReady} title={status?.plannerLlm?.experimentReady ? '运行真实规划器 A/B' : '模型与费率配置不完整'}><GitBranch size={15} />规划器 A/B</button>
        <button type="button" onClick={onReplayPlanner} disabled={operationLoading}><RotateCcw size={15} />回放规划</button>
        <button type="button" onClick={onBenchmarkReranker} disabled={operationLoading}><Activity size={15} />重排基准</button>
      </div>
      {(evaluation || indexResult || coverageEvaluation || knowledgeAudit || plannerEvaluation || rerankerBenchmark) && <dl>
        {indexResult && <div><dt>最近索引</dt><dd>{indexResult.chunks} 切片 / {indexResult.schools} 院校</dd></div>}
        {evaluation && <>
          <div><dt>RAG 门禁</dt><dd>{evaluation.quality_gate?.status ?? '-'}</dd></div>
          <div><dt>Recall@5</dt><dd>{percent(evaluation.recall_at_5)}</dd></div>
          <div><dt>目标 Recall@1</dt><dd>{percent(evaluation.target_recall_at_1 ?? evaluation.recall_at_1)}</dd></div>
          <div><dt>案例命中@1</dt><dd>{percent(evaluation.hit_rate_at_1)}</dd></div>
          <div><dt>MRR@5</dt><dd>{evaluation.mean_reciprocal_rank_at_5.toFixed(3)}</dd></div>
          <div><dt>重排增益</dt><dd>{evaluation.rerank_mrr_lift >= 0 ? '+' : ''}{evaluation.rerank_mrr_lift.toFixed(3)}</dd></div>
          <div><dt>边界安全</dt><dd>{percent(evaluation.boundary_safety_rate)}</dd></div>
          <div><dt>答案证据支持</dt><dd>{percent(evaluation.answer_support_rate)}</dd></div>
          <div><dt>引用落地</dt><dd>{percent(evaluation.citation_groundedness)}</dd></div>
          <div><dt>来源 URL</dt><dd>{evaluation.citation_source_url_rate == null ? '-' : percent(evaluation.citation_source_url_rate)}</dd></div>
          <div><dt>院校范围准确</dt><dd>{percent(evaluation.school_scope_accuracy)}</dd></div>
        </>}
        {coverageEvaluation && <>
          <div><dt>证据门禁</dt><dd>{coverageEvaluation.quality_gate?.status ?? '-'}</dd></div>
          <div><dt>策略样本</dt><dd>{coverageEvaluation.cases}</dd></div>
          <div><dt>证据策略准确率</dt><dd>{percent(coverageEvaluation.accuracy)}</dd></div>
          <div><dt>错误接纳率</dt><dd>{percent(coverageEvaluation.false_accept_rate)}</dd></div>
        </>}
        {knowledgeAudit && <>
          <div><dt>知识门禁</dt><dd>{knowledgeAudit.quality_gate?.status ?? '-'}</dd></div>
          <div><dt>知识库通过率</dt><dd>{percent(knowledgeAudit.pass_rate)}</dd></div>
          <div><dt>资料平均质量</dt><dd>{Math.round(knowledgeAudit.average_quality_score)}</dd></div>
        </>}
        {plannerEvaluation && <>
          <div><dt>规划门禁</dt><dd>{plannerEvaluation.quality_gate?.status ?? '-'}</dd></div>
          <div><dt>规划样本</dt><dd>{plannerEvaluation.cases}</dd></div>
          <div><dt>规划评估集</dt><dd>{plannerEvaluation.dataset_version || '-'}</dd></div>
          <div><dt>规则 Exact Match</dt><dd>{percent(plannerEvaluation.deterministic.exact_match_rate)}</dd></div>
          <div><dt>规则延迟</dt><dd>{Math.round(plannerEvaluation.deterministic.average_latency_ms)} ms</dd></div>
          <div><dt>LLM Exact Match</dt><dd>{plannerEvaluation.llm.status === 'COMPLETED' ? percent(plannerEvaluation.llm.exact_match_rate) : plannerEvaluation.llm.status}</dd></div>
          <div><dt>LLM 目标召回</dt><dd>{plannerEvaluation.llm.status === 'COMPLETED' ? percent(plannerEvaluation.llm.target_recall) : '-'}</dd></div>
          <div><dt>LLM 延迟</dt><dd>{plannerEvaluation.llm.status === 'COMPLETED' ? `${Math.round(plannerEvaluation.llm.average_latency_ms)} ms` : '-'}</dd></div>
          <div><dt>LLM 越界提议</dt><dd>{plannerEvaluation.llm.status === 'COMPLETED' ? percent(plannerEvaluation.llm.invalid_proposal_rate) : '-'}</dd></div>
          <div><dt>LLM 失败案例</dt><dd>{plannerEvaluation.llm.failed_cases ?? '-'}</dd></div>
          <div><dt>LLM 配置</dt><dd>{plannerEvaluation.llm_readiness ? `${plannerEvaluation.llm_readiness.status} / ${plannerEvaluation.llm_readiness.pricingMode}` : '历史报告'}</dd></div>
          <div><dt>提示词版本</dt><dd>{plannerEvaluation.prompt_version || '-'}</dd></div>
          <div><dt>LLM Token</dt><dd>{plannerEvaluation.llm.status === 'COMPLETED' ? plannerEvaluation.llm.total_tokens : plannerEvaluation.llm.status}</dd></div>
          <div><dt>LLM 成本</dt><dd>{plannerEvaluation.llm.estimated_cost_usd == null ? plannerEvaluation.llm.cost_status : `$${plannerEvaluation.llm.estimated_cost_usd.toFixed(6)}`}</dd></div>
          <div><dt>运行类型</dt><dd>{plannerEvaluation.run_type === 'REPLAY' ? '离线回放' : '实时评估'}</dd></div>
          <div><dt>推荐策略</dt><dd>{plannerEvaluation.recommended_mode}</dd></div>
        </>}
        {rerankerBenchmark && <>
          <div><dt>重排门禁</dt><dd>{rerankerBenchmark.quality_gate?.status ?? '-'}</dd></div>
          <div><dt>基准规模</dt><dd>{rerankerBenchmark.cases} 案例 / {rerankerBenchmark.corpus_chunks} 切片</dd></div>
          <div><dt>推荐重排</dt><dd>{rerankerBenchmark.recommended.mode}</dd></div>
          <div><dt>推荐 MRR@5</dt><dd>{rerankerBenchmark.recommended.metrics.mean_reciprocal_rank_at_5.toFixed(3)}</dd></div>
          <div><dt>推荐延迟</dt><dd>{Math.round(rerankerBenchmark.recommended.average_case_latency_ms)} ms/案例</dd></div>
          <div><dt>模型指纹</dt><dd>{rerankerBenchmark.model_manifest.weights_sha256.slice(0, 12)}</dd></div>
          {rerankerBenchmark.modes.map((item) => <div key={item.mode}><dt>{item.mode}</dt><dd>MRR {item.metrics.mean_reciprocal_rank_at_5.toFixed(3)} / {Math.round(item.average_case_latency_ms)} ms</dd></div>)}
        </>}
      </dl>}
      {status && !status.plannerLlm?.experimentReady && <div className="agent-operation-warning" role="status">
        <AlertTriangle size={15} />规划器真实 A/B 未就绪：{status.plannerLlm?.missingConfiguration.map(plannerConfigurationLabel).join('、') || '模型服务不可用'}
      </div>}
      {operationJobs.length > 0 && <div className="operation-job-queue">
        <h3><Clock3 size={15} />异步任务</h3>
        <div>{operationJobs.map((job) => {
          const progress = job.progress_total > 0 ? Math.round(job.progress_current * 100 / job.progress_total) : 0;
          const activeJob = ['QUEUED', 'RUNNING', 'CANCEL_REQUESTED'].includes(job.status);
          return <article key={job.id}>
            <div><strong>{operationTypeLabel(job.operation_type)}</strong><span className={`job-status status-${job.status.toLowerCase()}`}>{job.status}</span></div>
            <div className="job-progress"><i style={{ width: `${progress}%` }} /><span>{job.progress_message || '等待执行'} · {progress}%</span></div>
            <em>第 {job.attempt} 次 · {formatDateTime(job.updated_at)}{job.trace_id ? ` · trace ${job.trace_id.slice(0, 8)}` : ''}{job.error ? ` · ${job.error}` : ''}</em>
            <div className="job-actions">
              {activeJob && <button type="button" title="取消任务" onClick={() => onCancelOperation(job.id)}><Ban size={14} /></button>}
              {['FAILED', 'CANCELLED'].includes(job.status) && <button type="button" title="重试任务" onClick={() => onRetryOperation(job.id)}><RotateCcw size={14} /></button>}
              <button type="button" title="查看任务 trace" onClick={() => onViewOperationTrace(job.id)}><FileJson2 size={14} /></button>
            </div>
          </article>;
        })}</div>
        {operationTrace && <details className="operation-trace" open>
          <summary>Trace · {operationTrace.job.trace_id.slice(0, 8) || operationTrace.job.id.slice(0, 8)}</summary>
          <div className="trace-toolbar">
            <span>Correlation <code>{operationTrace.job.correlation_id || '-'}</code></span>
            <button type="button" title="下载完整 Trace JSON" onClick={() => downloadTrace(operationTrace)}><Download size={14} /></button>
          </div>
          <ol>{operationTrace.events.map((event, index) => <li key={`${event.created_at}-${index}`}><time>{formatDateTime(event.created_at)}</time><strong>{event.event_type}</strong><code>{event.detail}</code></li>)}</ol>
          {operationTrace.telemetry && <ul className="telemetry-spans">{operationTrace.telemetry.spans.map((span) => <li key={span.span_id}>
            <span><strong>{span.name}</strong><em>{span.service_name} · {span.kind}</em></span>
            <code>{span.span_id.slice(0, 8)} ← {span.parent_span_id?.slice(0, 8) || 'root'}</code>
            <span className={`span-status status-${span.status.toLowerCase()}`}>{span.status}</span>
            <time>{span.duration_ms.toFixed(1)} ms</time>
          </li>)}</ul>}
        </details>}
      </div>}
    </section>}

    {adminEnabled && adminView === 'diagnostics' && <section className="agent-diagnostics">
      <header>
        <div><span>运行诊断</span><h2>失败、拒绝与过滤记录</h2></div>
        <strong>{diagnostics?.total ?? 0}</strong>
      </header>
      <div className="diagnostic-controls">
        <label><Search size={15} /><input value={diagnosticQuery} onChange={(event) => onDiagnosticQueryChange(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') onRefreshDiagnostics(); }} placeholder="搜索院校、任务、原因或 trace" /></label>
        <select value={diagnosticCategory} onChange={(event) => onDiagnosticCategoryChange(event.target.value)} aria-label="诊断类别">
          <option value="ALL">全部类别</option><option value="OPERATION_FAILURE">任务失败</option><option value="QUALITY_GATE">质量门禁</option><option value="PLANNER_FILTER">规划过滤</option><option value="EVIDENCE_POLICY">证据策略</option><option value="KNOWLEDGE_AUDIT">知识审计</option><option value="WORKFLOW">工作流</option>
        </select>
        <select value={diagnosticSeverity} onChange={(event) => onDiagnosticSeverityChange(event.target.value)} aria-label="严重级别">
          <option value="ALL">全部级别</option><option value="ERROR">错误</option><option value="WARNING">警告</option><option value="INFO">信息</option>
        </select>
        <button type="button" title="应用诊断筛选" onClick={onRefreshDiagnostics}><Search size={16} /></button>
      </div>
      {diagnostics && Object.keys(diagnostics.counts).length > 0 && <div className="diagnostic-summary">{Object.entries(diagnostics.counts).map(([key, value]) => <span key={key}>{diagnosticCategoryLabel(key)} <strong>{value}</strong></span>)}</div>}
      {!diagnostics || diagnostics.items.length === 0 ? <div className="diagnostic-empty">当前筛选条件下没有诊断记录</div> : <div className="diagnostic-list">{diagnostics.items.map((item) => <article key={item.id} className={`severity-${item.severity.toLowerCase()}`}>
        <span className="diagnostic-icon">{item.severity === 'ERROR' ? <XCircle size={16} /> : item.severity === 'WARNING' ? <AlertTriangle size={16} /> : <Info size={16} />}</span>
        <div><strong>{item.title}</strong><p>{item.detail}</p><em>{diagnosticCategoryLabel(item.category)}{item.code ? ` · ${item.code}` : ''}{item.trace_id ? ` · trace ${item.trace_id.slice(0, 8)}` : ''}</em></div>
        <time>{formatDateTime(item.timestamp)}</time>
        {item.source_url && <a href={item.source_url} target="_blank" rel="noreferrer" title="打开资料来源"><ExternalLink size={14} /></a>}
      </article>)}</div>}
    </section>}

    {adminEnabled && adminView === 'workflow' && <section className="coverage-workflow-console">
      <header>
        <div><span>自主数据补全</span><h2>官方证据工作流</h2></div>
        {coverageWorkflow && <strong className={`workflow-status status-${coverageWorkflow.status.toLowerCase()}`}>{coveragePhaseLabel(coverageWorkflow.phase)}</strong>}
      </header>
      {workflowMetrics && <dl className="workflow-telemetry">
        <div><dt>历史运行</dt><dd>{workflowMetrics.total_runs}</dd></div>
        <div><dt>闭环完成率</dt><dd>{percent(workflowMetrics.completion_rate)}</dd></div>
        <div><dt>等待审批</dt><dd>{workflowMetrics.waiting_runs}</dd></div>
        <div><dt>平均证据分</dt><dd>{Math.round(workflowMetrics.average_quality_score)}</dd></div>
      </dl>}
      <div className="workflow-command">
        <label><span>目标院校</span><input value={workflowSchool} onChange={(event) => onWorkflowSchoolChange(event.target.value)} placeholder="输入数据采集任务中的院校全称" /></label>
        <button type="button" onClick={onStartCoverageWorkflow} disabled={operationLoading || !workflowSchool.trim()}>
          {operationLoading ? <RefreshCw className="spin" size={16} /> : <GitBranch size={16} />}启动工作流
        </button>
      </div>

      {!coverageWorkflow ? <div className="workflow-empty">选择已有数据采集任务的院校，Agent 将规划精确官方页面并在发布前暂停。</div> : <>
        <div className="workflow-summary">
          <span>线程 <code>{coverageWorkflow.thread_id.slice(0, 8)}</code></span>
          <span>规划器 {coverageWorkflow.planner_mode}</span>
          <span>{coverageWorkflow.plan.length} 个目标</span>
          <span>{coverageWorkflow.candidates.length} 个候选</span>
        </div>

        {coverageWorkflow.plan.length > 0 && <div className="workflow-stage">
          <h3><GitBranch size={15} />执行计划</h3>
          <div className="workflow-plan-list">{coverageWorkflow.plan.map((step) => <div key={step.target_id}>
            <span>{step.target_year} · {step.document_type}</span>
            <strong>{step.title}</strong>
            <a href={step.source_url} target="_blank" rel="noreferrer">查看官方页<ExternalLink size={13} /></a>
          </div>)}</div>
        </div>}

        {(coverageWorkflow.candidates.length > 0 || coverageWorkflow.rejected_candidates.length > 0) && <div className="workflow-stage">
          <h3><ShieldCheck size={15} />采集核验</h3>
          <div className="workflow-evidence-list">
            {coverageWorkflow.candidates.map((item) => <div className="is-accepted" key={item.targetId}><CheckCircle2 size={15} /><span><strong>{item.title}</strong><em>{item.documentType} · {item.contentLength ?? 0} 字 · 验证 {item.qualityScore ?? 0} 分</em></span><a href={item.sourceUrl} target="_blank" rel="noreferrer"><ExternalLink size={14} /></a></div>)}
            {coverageWorkflow.rejected_candidates.map((item) => <div className="is-rejected" key={item.targetId}><XCircle size={15} /><span><strong>{item.title}</strong><em>{item.reason || '未通过安全与质量检查'}</em></span></div>)}
          </div>
        </div>}

        {coverageWorkflow.status === 'WAITING_HUMAN' && <div className="workflow-review">
          <label><span>审核意见</span><textarea value={workflowFeedback} onChange={(event) => onWorkflowFeedbackChange(event.target.value)} /></label>
          <div><button type="button" className="reject" onClick={() => onResumeCoverageWorkflow(false)} disabled={operationLoading}><XCircle size={15} />驳回</button><button type="button" onClick={() => onResumeCoverageWorkflow(true)} disabled={operationLoading}><CheckCircle2 size={15} />批准并发布</button></div>
        </div>}

        {(coverageWorkflow.published.length > 0 || coverageWorkflow.evaluation_result) && <dl className="workflow-results">
          <div><dt>发布文档</dt><dd>{coverageWorkflow.published.length}</dd></div>
          <div><dt>生成切片</dt><dd>{coverageWorkflow.published.reduce((sum, item) => sum + item.chunkCount, 0)}</dd></div>
          <div><dt>索引切片</dt><dd>{coverageWorkflow.index_result?.chunks ?? '-'}</dd></div>
          <div><dt>Recall@5</dt><dd>{coverageWorkflow.evaluation_result ? percent(coverageWorkflow.evaluation_result.recall_at_5) : '-'}</dd></div>
        </dl>}

        <details className="workflow-trace"><summary>执行追踪 · {coverageWorkflow.trace.length} 步</summary><ol>{coverageWorkflow.trace.map((item, index) => <li key={`${item}-${index}`}><code>{item}</code></li>)}</ol></details>
      </>}
      {workflowRuns.length > 0 && <div className="workflow-run-history">
        <h3><Clock3 size={15} />最近运行</h3>
        <div>{workflowRuns.map((run) => <article key={run.thread_id}>
          <span className={`run-state state-${run.status.toLowerCase()}`}>{coveragePhaseLabel(run.phase)}</span>
          <strong>{run.school_name}</strong>
          <em>{run.candidate_count} 候选 · {run.published_count} 发布 · 质量 {Math.round(run.average_quality_score)}</em>
          <time>{formatDateTime(run.updated_at)}</time>
        </article>)}</div>
      </div>}
    </section>}
  </div>;
}
