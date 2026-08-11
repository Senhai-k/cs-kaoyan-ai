import asyncio
import logging
import time
import uuid
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException, Request, Response
from langgraph.types import Command

from .config import get_settings
from .coverage_workflow import CoverageWorkflow
from .coverage_evaluation import run_coverage_evaluation
from .diagnostics import build_diagnostics
from .evaluation import run_evaluation as execute_evaluation
from .generation import GroundedGenerator
from .graph import AdmissionsAgentGraph
from .metrics import MetricsStore
from .monitoring import render_prometheus_metrics
from .knowledge_audit import KnowledgeAuditor
from .operation_control import ensure_not_cancelled, report_progress
from .operation_jobs import AsyncOperationManager
from .planner_evaluation import replay_planner_evaluation, run_planner_evaluation
from .quality_gate import attach_quality_gate
from .reranker_benchmark import run_reranker_benchmark
from .telemetry import TelemetryStore, configure_structured_logging
from .models import (
    AgentQueryRequest,
    AgentQueryResponse,
    AgentResumeRequest,
    CoverageWorkflowResponse,
    CoverageWorkflowMetrics,
    CoverageWorkflowRun,
    CoverageEvaluationResult,
    KnowledgeAuditResult,
    CoverageWorkflowStartRequest,
    EvaluationResult,
    IndexSyncResult,
    OperationJobResponse,
    OperationJobStartRequest,
)
from .retrieval import HybridRetriever


class Runtime:
    def __init__(self):
        self.settings = get_settings()
        configure_structured_logging()
        self.telemetry = TelemetryStore(
            self.settings.data_dir / "telemetry.sqlite3",
            self.settings.telemetry_max_spans,
            self.settings.otlp_endpoint,
        )
        self.retriever = HybridRetriever(self.settings)
        self.generator = GroundedGenerator(self.settings)
        self.agent = AdmissionsAgentGraph(self.settings, self.retriever, self.generator)
        self.metrics = MetricsStore(self.settings.metrics_path)
        cases_path = Path(__file__).resolve().parents[1] / "evals" / "rag_eval.json"
        self.coverage_workflow = CoverageWorkflow(
            self.settings,
            self.retriever.sync_from_spring,
            lambda: self._run_rag_evaluation(cases_path),
        )
        self.knowledge_auditor = KnowledgeAuditor(self.settings, self.coverage_workflow.verifier)
        coverage_cases_path = Path(__file__).resolve().parents[1] / "evals" / "coverage_eval.json"
        planner_cases_path = Path(__file__).resolve().parents[1] / "evals" / "planner_eval.json"
        self.operation_jobs = AsyncOperationManager(
            self.settings.data_dir / "operation-jobs.sqlite3",
            {
                "INDEX_SYNC": self._run_index_sync,
                "RAG_EVALUATION": lambda progress, cancel: self._run_rag_evaluation(
                    cases_path, progress, cancel
                ),
                "COVERAGE_EVALUATION": lambda progress, cancel: attach_quality_gate(
                    "COVERAGE_EVALUATION",
                    run_coverage_evaluation(
                        self.coverage_workflow.verifier, coverage_cases_path, progress, cancel
                    ),
                ),
                "KNOWLEDGE_AUDIT": lambda progress, cancel: attach_quality_gate(
                    "KNOWLEDGE_AUDIT", self.knowledge_auditor.run(progress, cancel)
                ),
                "PLANNER_EVALUATION": lambda progress, cancel: attach_quality_gate(
                    "PLANNER_EVALUATION",
                    run_planner_evaluation(
                        self.coverage_workflow.planner,
                        planner_cases_path,
                        progress,
                        cancel,
                        require_llm=True,
                    ),
                ),
                "PLANNER_REPLAY": lambda progress, cancel: attach_quality_gate(
                    "PLANNER_REPLAY",
                    replay_planner_evaluation(self.settings.planner_replay_path, progress, cancel),
                ),
                "RERANKER_BENCHMARK": lambda progress, cancel: attach_quality_gate(
                    "RERANKER_BENCHMARK",
                    run_reranker_benchmark(
                        self.settings, self.retriever, cases_path, progress, cancel
                    ),
                ),
            },
            max_workers=self.settings.operation_max_workers,
            timeout_seconds=self.settings.operation_timeout_seconds,
            telemetry=self.telemetry,
        )

    def _run_rag_evaluation(self, cases_path, progress=None, cancel_check=None):
        return attach_quality_gate(
            "RAG_EVALUATION",
            execute_evaluation(
                self.agent, self.retriever, cases_path, progress, cancel_check
            ),
        )

    def _run_index_sync(self, progress, cancel_check):
        ensure_not_cancelled(cancel_check)
        report_progress(progress, 0, 1, "同步 Spring 文档到向量索引")
        result = self.retriever.sync_from_spring()
        ensure_not_cancelled(cancel_check)
        report_progress(progress, 1, 1, "索引同步完成")
        return result

    def close(self) -> None:
        self.operation_jobs.close()
        self.coverage_workflow.close()
        self.agent.close()
        self.retriever.close()
        self.telemetry.close()


runtime: Runtime | None = None


@asynccontextmanager
async def lifespan(_: FastAPI):
    global runtime
    runtime = Runtime()
    try:
        yield
    finally:
        runtime.close()


app = FastAPI(title="CS Kaoyan LangGraph Agent", version="1.0.0", lifespan=lifespan)
telemetry_logger = logging.getLogger("agent.telemetry")


def get_runtime() -> Runtime:
    if runtime is None:
        raise RuntimeError("agent runtime is not initialized")
    return runtime


@app.middleware("http")
async def trace_http_request(request: Request, call_next):
    telemetry = get_runtime().telemetry
    context = telemetry.server_context(
        request.headers.get("traceparent"),
        request.headers.get("x-correlation-id"),
    )
    attributes = {
        "http.request.method": request.method,
        "url.path": request.url.path,
    }
    started_at = time.perf_counter()
    with telemetry.span("http.request", kind="SERVER", attributes=attributes, context=context):
        try:
            response = await call_next(request)
            attributes["http.response.status_code"] = response.status_code
            response.headers["X-Correlation-ID"] = context.correlation_id
            response.headers["traceparent"] = context.traceparent
            telemetry_logger.info(
                "request_completed method=%s path=%s status=%s duration_ms=%.3f",
                request.method,
                request.url.path,
                response.status_code,
                (time.perf_counter() - started_at) * 1000,
            )
            return response
        except Exception:
            attributes["http.response.status_code"] = 500
            telemetry_logger.exception(
                "request_failed method=%s path=%s duration_ms=%.3f",
                request.method,
                request.url.path,
                (time.perf_counter() - started_at) * 1000,
            )
            raise


@app.get("/api/health")
def health() -> dict[str, Any]:
    current = get_runtime()
    return {
        "status": "UP",
        "framework": "LangGraph",
        "capabilities": [
            "StateGraph", "ToolNode", "Multi-tool routing", "SQLite Checkpointer",
            "Human-in-the-loop", "Hybrid retrieval", "Second-stage reranking",
            "Structured planning", "Send parallel workers", "Autonomous coverage workflow",
            "Specialist verification subgraphs", "Evidence quality scoring", "Persistent workflow registry",
            "Persistent async operations", "Cooperative cancellation", "Retry and trace export",
            "Versioned planner evaluation", "Tamper-evident replay", "Scope-locked evidence publishing",
            "Versioned quality gates", "Bounded operation execution",
            "Searchable operations diagnostics", "Isolated reranker benchmarking",
        ],
        "indexedChunks": current.retriever.indexed_chunks,
        "embeddingModel": current.retriever.embedding_model_name,
        "rerankerEnabled": current.retriever.reranker_enabled,
        "rerankerMode": current.retriever.reranker_mode,
        "generationMode": current.generator.mode,
        "plannerLlm": current.coverage_workflow.planner.llm_readiness,
        "otlpExporterEnabled": current.telemetry.otlp_exporter_enabled,
    }


@app.post("/api/index/sync", response_model=IndexSyncResult)
async def sync_index() -> IndexSyncResult:
    try:
        return await asyncio.to_thread(get_runtime().retriever.sync_from_spring)
    except Exception as error:
        raise HTTPException(status_code=502, detail=f"knowledge sync failed: {error}") from error


@app.post("/api/agent/query", response_model=AgentQueryResponse)
async def query_agent(request: AgentQueryRequest) -> AgentQueryResponse:
    current = get_runtime()
    thread_id = request.thread_id or str(uuid.uuid4())
    config = {"configurable": {"thread_id": thread_id}}
    started_at = time.perf_counter()
    try:
        result = await asyncio.to_thread(current.agent.graph.invoke, {
            "messages": [],
            "question": request.question,
            "allow_human_review": request.allow_human_review,
            "attempts": 0,
            "trace": [],
        }, config)
        response = state_to_response(thread_id, result)
        tool_calls = sum(1 for item in response.trace if item.startswith("tool:"))
        successful = sum(1 for item in response.trace if item.startswith("tool_result:") and not item.endswith("missing"))
        current.metrics.record(thread_id, response.status, started_at, tool_calls, successful)
        return response
    except Exception as error:
        current.metrics.record(thread_id, "FAILED", started_at, 1, 0)
        raise HTTPException(status_code=500, detail=f"agent execution failed: {error}") from error


@app.post("/api/agent/threads/{thread_id}/resume", response_model=AgentQueryResponse)
async def resume_agent(thread_id: str, request: AgentResumeRequest) -> AgentQueryResponse:
    current = get_runtime()
    started_at = time.perf_counter()
    config = {"configurable": {"thread_id": thread_id}}
    try:
        result = await asyncio.to_thread(
            current.agent.graph.invoke,
            Command(resume={"approved": request.approved, "feedback": request.feedback}),
            config,
        )
        response = state_to_response(thread_id, result)
        current.metrics.record(thread_id, response.status, started_at, 0, 0)
        return response
    except Exception as error:
        current.metrics.record(thread_id, "FAILED", started_at, 0, 0)
        raise HTTPException(status_code=500, detail=f"agent resume failed: {error}") from error


@app.get("/api/metrics")
def metrics():
    return get_runtime().metrics.summary()


@app.get("/metrics/prometheus", include_in_schema=False)
def prometheus_metrics() -> Response:
    current = get_runtime()
    content = render_prometheus_metrics(
        current.metrics.summary(), current.retriever.indexed_chunks
    )
    return Response(content=content, media_type="text/plain; version=0.0.4")


@app.get("/api/telemetry/traces")
def telemetry_traces(limit: int = 20) -> list[dict[str, Any]]:
    return get_runtime().telemetry.recent_traces(limit)


@app.get("/api/telemetry/traces/{trace_id}")
def telemetry_trace(trace_id: str) -> dict[str, Any]:
    try:
        return get_runtime().telemetry.export_trace(trace_id)
    except ValueError as error:
        raise HTTPException(status_code=404, detail=str(error)) from error


@app.post("/api/evaluation/run", response_model=EvaluationResult)
async def run_evaluation() -> EvaluationResult:
    current = get_runtime()
    cases_path = Path(__file__).resolve().parents[1] / "evals" / "rag_eval.json"
    try:
        return await asyncio.to_thread(execute_evaluation, current.agent, current.retriever, cases_path)
    except Exception as error:
        raise HTTPException(status_code=500, detail=f"evaluation failed: {error}") from error


@app.post("/api/evaluation/coverage/run", response_model=CoverageEvaluationResult)
async def evaluate_coverage_policy() -> CoverageEvaluationResult:
    current = get_runtime()
    cases_path = Path(__file__).resolve().parents[1] / "evals" / "coverage_eval.json"
    return await asyncio.to_thread(run_coverage_evaluation, current.coverage_workflow.verifier, cases_path)


@app.post("/api/audit/knowledge/run", response_model=KnowledgeAuditResult)
async def audit_private_knowledge() -> KnowledgeAuditResult:
    try:
        return await asyncio.to_thread(get_runtime().knowledge_auditor.run)
    except Exception as error:
        raise HTTPException(status_code=502, detail=f"knowledge audit failed: {error}") from error


@app.post("/api/workflows/coverage/start", response_model=CoverageWorkflowResponse)
async def start_coverage_workflow(request: CoverageWorkflowStartRequest) -> CoverageWorkflowResponse:
    current = get_runtime()
    thread_id = request.thread_id or str(uuid.uuid4())
    try:
        state = await asyncio.to_thread(
            current.coverage_workflow.start,
            request.school_name.strip(),
            request.max_targets,
            thread_id,
        )
        return state_to_coverage_response(thread_id, state)
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
    except Exception as error:
        raise HTTPException(status_code=500, detail=f"coverage workflow failed: {error}") from error


@app.post("/api/workflows/coverage/{thread_id}/resume", response_model=CoverageWorkflowResponse)
async def resume_coverage_workflow(thread_id: str, request: AgentResumeRequest) -> CoverageWorkflowResponse:
    try:
        state = await asyncio.to_thread(
            get_runtime().coverage_workflow.resume,
            thread_id,
            request.approved,
            request.feedback,
        )
        return state_to_coverage_response(thread_id, state)
    except Exception as error:
        raise HTTPException(status_code=500, detail=f"coverage workflow resume failed: {error}") from error


@app.get("/api/workflows/coverage/runs", response_model=list[CoverageWorkflowRun])
def coverage_workflow_runs(limit: int = 20) -> list[CoverageWorkflowRun]:
    return [CoverageWorkflowRun.model_validate(item) for item in get_runtime().coverage_workflow.runs(limit)]


@app.get("/api/workflows/coverage/metrics", response_model=CoverageWorkflowMetrics)
def coverage_workflow_metrics() -> CoverageWorkflowMetrics:
    return CoverageWorkflowMetrics.model_validate(get_runtime().coverage_workflow.metrics())


@app.post("/api/operations/jobs", response_model=OperationJobResponse)
def start_operation_job(request: OperationJobStartRequest) -> OperationJobResponse:
    try:
        return OperationJobResponse.model_validate(
            get_runtime().operation_jobs.start(request.operation_type)
        )
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error


@app.get("/api/operations/jobs", response_model=list[OperationJobResponse])
def operation_jobs(limit: int = 20) -> list[OperationJobResponse]:
    return [
        OperationJobResponse.model_validate(item)
        for item in get_runtime().operation_jobs.list(limit)
    ]


@app.get("/api/operations/diagnostics")
def operation_diagnostics(
    query: str = "",
    category: str = "ALL",
    severity: str = "ALL",
    limit: int = 50,
) -> dict[str, Any]:
    current = get_runtime()
    return build_diagnostics(
        current.operation_jobs.list(100),
        current.coverage_workflow.runs(100),
        query,
        category,
        severity,
        limit,
    )


@app.get("/api/operations/jobs/{job_id}", response_model=OperationJobResponse)
def operation_job(job_id: str) -> OperationJobResponse:
    try:
        return OperationJobResponse.model_validate(get_runtime().operation_jobs.get(job_id))
    except ValueError as error:
        raise HTTPException(status_code=404, detail=str(error)) from error


@app.post("/api/operations/jobs/{job_id}/cancel", response_model=OperationJobResponse)
def cancel_operation_job(job_id: str) -> OperationJobResponse:
    try:
        return OperationJobResponse.model_validate(get_runtime().operation_jobs.cancel(job_id))
    except ValueError as error:
        raise HTTPException(status_code=404, detail=str(error)) from error


@app.post("/api/operations/jobs/{job_id}/retry", response_model=OperationJobResponse)
def retry_operation_job(job_id: str) -> OperationJobResponse:
    try:
        return OperationJobResponse.model_validate(get_runtime().operation_jobs.retry(job_id))
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error


@app.get("/api/operations/jobs/{job_id}/trace")
def operation_job_trace(job_id: str) -> dict[str, Any]:
    try:
        return get_runtime().operation_jobs.trace(job_id)
    except ValueError as error:
        raise HTTPException(status_code=404, detail=str(error)) from error


def state_to_response(thread_id: str, state: dict[str, Any]) -> AgentQueryResponse:
    interrupts = state.get("__interrupt__") or []
    if interrupts:
        review = getattr(interrupts[0], "value", interrupts[0])
        return AgentQueryResponse(
            status="WAITING_HUMAN",
            thread_id=thread_id,
            answer="该问题需要人工审核后继续。",
            sources=review.get("sources", []) if isinstance(review, dict) else [],
            related_school_id=state.get("related_school_id"),
            confidence=float(state.get("confidence", 0.0)),
            route="human_review",
            retrieval_count=int(state.get("retrieval_count", len(state.get("evidence", [])))),
            trace=state.get("trace", []),
            review=review if isinstance(review, dict) else {"value": str(review)},
        )
    route = state.get("route", "completed")
    status = "REJECTED" if route == "rejected" else "COMPLETED"
    return AgentQueryResponse(
        status=status,
        thread_id=thread_id,
        answer=state.get("answer", ""),
        sources=state.get("sources", []),
        related_school_id=state.get("related_school_id"),
        confidence=float(state.get("confidence", 0.0)),
        route=route,
        retrieval_count=int(state.get("retrieval_count", len(state.get("evidence", [])))),
        trace=state.get("trace", []),
    )


def state_to_coverage_response(thread_id: str, state: dict[str, Any]) -> CoverageWorkflowResponse:
    interrupts = state.get("__interrupt__") or []
    review = getattr(interrupts[0], "value", interrupts[0]) if interrupts else None
    route = state.get("route", "")
    if interrupts:
        status = "WAITING_HUMAN"
    elif route == "rejected" or state.get("phase") == "REJECTED":
        status = "REJECTED"
    else:
        status = "COMPLETED"
    return CoverageWorkflowResponse(
        status=status,
        thread_id=thread_id,
        school_name=state.get("school_name", ""),
        phase=state.get("phase", ""),
        planner_mode=state.get("planner_mode", ""),
        planner_metadata=state.get("planner_metadata", {}),
        plan=state.get("plan", []),
        candidates=[_coverage_candidate(item) for item in state.get("candidates", [])],
        rejected_candidates=[_coverage_candidate(item) for item in state.get("rejected_candidates", [])],
        published=state.get("published", []),
        index_result=state.get("index_result"),
        evaluation_result=state.get("evaluation_result"),
        trace=state.get("trace", []),
        review=review if isinstance(review, dict) else None,
    )


def _coverage_candidate(item: dict[str, Any]) -> dict[str, Any]:
    return {
        "targetId": item.get("target_id"),
        "title": item.get("title"),
        "documentType": item.get("document_type"),
        "sourceUrl": item.get("source_url"),
        "status": item.get("status"),
        "contentLength": item.get("content_length"),
        "reason": item.get("reason"),
        "qualityScore": item.get("quality_score"),
        "checks": item.get("verification_checks", []),
        "evidenceHash": item.get("evidence_hash"),
    }
