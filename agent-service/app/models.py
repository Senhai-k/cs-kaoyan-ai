from typing import Any, Literal

from pydantic import BaseModel, Field


class KnowledgeDocument(BaseModel):
    chunk_id: int
    document_id: int
    title: str
    content: str
    source_url: str | None = None
    school_id: int | None = None
    school_name: str | None = None
    college_id: int | None = None
    major_id: int | None = None
    year: int | None = None
    document_type: str | None = None
    chunk_index: int = 0


class RetrievedEvidence(KnowledgeDocument):
    score: float
    vector_score: float = 0.0
    lexical_score: float = 0.0
    rerank_score: float = 0.0
    parent_context: str = ""


class AgentQueryRequest(BaseModel):
    question: str = Field(min_length=2, max_length=2000)
    thread_id: str | None = None
    allow_human_review: bool = False


class AgentResumeRequest(BaseModel):
    approved: bool
    feedback: str = ""


class CoverageWorkflowStartRequest(BaseModel):
    school_name: str = Field(min_length=2, max_length=100)
    thread_id: str | None = None
    max_targets: int = Field(default=3, ge=1, le=5)


class CoverageWorkflowResponse(BaseModel):
    status: Literal["COMPLETED", "WAITING_HUMAN", "REJECTED", "FAILED"]
    thread_id: str
    school_name: str
    phase: str = ""
    planner_mode: str = ""
    planner_metadata: dict[str, Any] = Field(default_factory=dict)
    plan: list[dict[str, Any]] = Field(default_factory=list)
    candidates: list[dict[str, Any]] = Field(default_factory=list)
    rejected_candidates: list[dict[str, Any]] = Field(default_factory=list)
    published: list[dict[str, Any]] = Field(default_factory=list)
    index_result: dict[str, Any] | None = None
    evaluation_result: dict[str, Any] | None = None
    trace: list[str] = Field(default_factory=list)
    review: dict[str, Any] | None = None


class CoverageWorkflowRun(BaseModel):
    thread_id: str
    workflow_type: str
    school_name: str
    status: str
    phase: str
    planner_mode: str
    plan_count: int
    candidate_count: int
    rejected_count: int
    published_count: int
    average_quality_score: float
    started_at: str
    updated_at: str
    completed_at: str | None = None
    error: str = ""
    trace: list[str] = Field(default_factory=list)


class CoverageWorkflowMetrics(BaseModel):
    total_runs: int
    completed_runs: int
    waiting_runs: int
    rejected_runs: int
    failed_runs: int
    published_documents: int
    average_quality_score: float
    completion_rate: float


class CoverageEvaluationResult(BaseModel):
    cases: int
    passed: int
    accuracy: float
    false_accept_rate: float
    false_reject_rate: float
    details: list[dict[str, Any]] = Field(default_factory=list)


class KnowledgeAuditResult(BaseModel):
    total_documents: int
    verified_documents: int
    rejected_documents: int
    pass_rate: float
    average_quality_score: float
    failure_counts: dict[str, int] = Field(default_factory=dict)
    samples: list[dict[str, Any]] = Field(default_factory=list)


class OperationJobStartRequest(BaseModel):
    operation_type: Literal[
        "INDEX_SYNC", "RAG_EVALUATION", "COVERAGE_EVALUATION", "KNOWLEDGE_AUDIT",
        "PLANNER_EVALUATION", "PLANNER_REPLAY", "RERANKER_BENCHMARK",
    ]


class OperationJobResponse(BaseModel):
    id: str
    operation_type: str
    status: str
    progress_current: int
    progress_total: int
    progress_message: str
    attempt: int
    parent_job_id: str | None = None
    result: dict[str, Any] | None = None
    error: str = ""
    cancel_requested: bool = False
    created_at: str
    started_at: str | None = None
    updated_at: str
    completed_at: str | None = None
    correlation_id: str = ""
    trace_id: str = ""
    parent_span_id: str | None = None


class AgentQueryResponse(BaseModel):
    status: Literal["COMPLETED", "WAITING_HUMAN", "REJECTED", "FAILED"]
    thread_id: str
    answer: str
    sources: list[str] = Field(default_factory=list)
    related_school_id: int | None = None
    confidence: float = 0.0
    route: str = ""
    retrieval_count: int = 0
    trace: list[str] = Field(default_factory=list)
    review: dict[str, Any] | None = None


class IndexSyncResult(BaseModel):
    documents: int
    chunks: int
    schools: int
    collection: str
    embedding_model: str


class AgentMetrics(BaseModel):
    total_tasks: int
    completed_tasks: int
    waiting_tasks: int
    failed_tasks: int
    tool_calls: int
    successful_tool_calls: int
    average_latency_ms: float
    task_completion_rate: float
    tool_success_rate: float


class EvaluationResult(BaseModel):
    cases: int
    recall_at_5: float
    recall_at_1: float = 0.0
    target_recall_at_1: float = 0.0
    hit_rate_at_1: float = 0.0
    baseline_mean_reciprocal_rank_at_5: float
    mean_reciprocal_rank_at_5: float
    rerank_mrr_lift: float
    boundary_safety_rate: float
    citation_validity: float
    citation_groundedness: float = 0.0
    citation_source_url_rate: float = 0.0
    answer_support_rate: float = 0.0
    school_scope_accuracy: float = 0.0
    task_completion_rate: float
    category_scores: dict[str, float] = Field(default_factory=dict)
    failed_case_ids: list[str] = Field(default_factory=list)
    details: list[dict[str, Any]]
