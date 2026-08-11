from __future__ import annotations

import ipaddress
import hashlib
import json
import operator
import re
import socket
import sqlite3
from html.parser import HTMLParser
from pathlib import Path
from typing import Annotated, Any, Callable, TypedDict
from urllib.parse import urlparse

import httpx
from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.graph import END, START, StateGraph
from langgraph.types import Command, Send, interrupt
from pydantic import BaseModel, Field

from .config import Settings
from .evidence_verification import EvidenceVerificationGraph
from .workflow_runs import WorkflowRunStore


class CoveragePlanStep(BaseModel):
    target_id: int
    title: str
    document_type: str
    target_year: int
    source_url: str
    reason: str


class CoveragePlan(BaseModel):
    summary: str
    steps: list[CoveragePlanStep] = Field(default_factory=list)


class PlannerRunMetadata(BaseModel):
    mode: str
    prompt_version: str
    prompt_hash: str
    model: str
    input_tokens: int = 0
    output_tokens: int = 0
    total_tokens: int = 0
    estimated_cost_usd: float | None = None
    usage_status: str
    cost_status: str
    request_id: str = ""
    proposed_target_ids: list[int] = Field(default_factory=list)
    guard_rejected_target_ids: list[int] = Field(default_factory=list)
    guard_intervention_count: int = 0


class PlannerRun(BaseModel):
    plan: CoveragePlan
    metadata: PlannerRunMetadata


class CoverageWorkflowState(TypedDict, total=False):
    school_name: str
    max_targets: int
    school_id: int
    task: dict[str, Any]
    existing_urls: list[str]
    planner_mode: str
    planner_metadata: dict[str, Any]
    plan: list[dict[str, Any]]
    step: dict[str, Any]
    collected: Annotated[list[dict[str, Any]], operator.add]
    candidate: dict[str, Any]
    verified_results: Annotated[list[dict[str, Any]], operator.add]
    candidates: list[dict[str, Any]]
    rejected_candidates: list[dict[str, Any]]
    approved: bool
    feedback: str
    published: Annotated[list[dict[str, Any]], operator.add]
    index_result: dict[str, Any]
    evaluation_result: dict[str, Any]
    phase: str
    route: str
    trace: Annotated[list[str], operator.add]


class _HtmlTextExtractor(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts: list[str] = []
        self.title_parts: list[str] = []
        self._ignored = 0
        self._in_title = False

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in {"script", "style", "noscript"}:
            self._ignored += 1
        if tag == "title":
            self._in_title = True

    def handle_endtag(self, tag: str) -> None:
        if tag in {"script", "style", "noscript"} and self._ignored:
            self._ignored -= 1
        if tag == "title":
            self._in_title = False

    def handle_data(self, data: str) -> None:
        if self._ignored:
            return
        value = re.sub(r"\s+", " ", data).strip()
        if value:
            self.parts.append(value)
            if self._in_title:
                self.title_parts.append(value)

    @property
    def text(self) -> str:
        return re.sub(r"\s+", " ", " ".join(self.parts)).strip()

    @property
    def title(self) -> str:
        return " ".join(self.title_parts).strip()


class CoveragePlanner:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._model: Any = None

    @property
    def mode(self) -> str:
        return "llm-structured" if self.llm_available else "deterministic-fallback"

    def plan(self, task: dict[str, Any], max_targets: int) -> CoveragePlan:
        return self.plan_with_metadata(task, max_targets).plan

    def plan_with_metadata(
        self, task: dict[str, Any], max_targets: int, mode: str | None = None
    ) -> PlannerRun:
        selected_mode = mode or ("llm" if self.llm_available else "deterministic")
        if selected_mode == "deterministic":
            plan = self._plan_deterministic(task, max_targets)
            prompt_hash = _stable_hash({
                "version": "deterministic-v2", "task": task, "maxTargets": max_targets,
            })
            return PlannerRun(plan=plan, metadata=PlannerRunMetadata(
                mode="deterministic", prompt_version="deterministic-v2", prompt_hash=prompt_hash,
                model="rules", usage_status="NOT_APPLICABLE", cost_status="NOT_APPLICABLE",
                proposed_target_ids=[step.target_id for step in plan.steps],
            ))
        if selected_mode != "llm":
            raise ValueError(f"unsupported planner mode: {selected_mode}")
        if not self.llm_available:
            raise RuntimeError("LLM planner is not configured")
        return self._plan_llm_with_metadata(task, max_targets)

    @property
    def llm_available(self) -> bool:
        return bool(self.settings.openai_api_key.strip() and self.settings.openai_model.strip())

    @property
    def llm_readiness(self) -> dict[str, object]:
        return self.settings.planner_llm_readiness

    def eligible_targets(self, task: dict[str, Any]) -> list[dict[str, Any]]:
        return self.target_eligibility_report(task)["eligible"]

    def target_eligibility_report(self, task: dict[str, Any]) -> dict[str, list[dict[str, Any]]]:
        missing_configured = "missingDimensions" in task
        missing = [str(item) for item in task.get("missingDimensions", [])]
        if missing_configured and not missing:
            return {
                "eligible": [],
                "rejected": [
                    {"target_id": target.get("id"), "reason": "no_missing_dimensions"}
                    for target in task.get("targets", [])
                ],
            }
        candidates: list[tuple[int, dict[str, Any], str]] = []
        seen_ids: set[int] = set()
        rejected: list[dict[str, Any]] = []
        for original_index, target in enumerate(task.get("targets", [])):
            try:
                target_id = int(target["id"])
            except (KeyError, TypeError, ValueError):
                rejected.append({"target_id": target.get("id"), "reason": "invalid_target_id"})
                continue
            url = str(target.get("sourceUrl") or "")
            normalized_url = _normalized_target_url(url)
            if target_id in seen_ids:
                rejected.append({"target_id": target_id, "reason": "duplicate_target_id"})
                continue
            if not normalized_url or not _is_exact_official_url(url):
                rejected.append({"target_id": target_id, "reason": "unsafe_or_non_article_url"})
                continue
            if str(target.get("status") or "PENDING").upper() == "VERIFIED":
                rejected.append({"target_id": target_id, "reason": "already_verified"})
                continue
            if missing_configured and _matching_dimension(target, task) is None:
                rejected.append({"target_id": target_id, "reason": "unrelated_to_missing_dimensions"})
                continue
            candidates.append((original_index, target, normalized_url))
            seen_ids.add(target_id)
        candidates.sort(key=lambda item: _target_sort_key(item[1], task, item[0]))
        eligible = []
        seen_urls: dict[str, int] = {}
        for _, target, normalized_url in candidates:
            if normalized_url in seen_urls:
                rejected.append({
                    "target_id": int(target["id"]),
                    "reason": "duplicate_source_url",
                    "kept_target_id": seen_urls[normalized_url],
                })
                continue
            eligible.append(target)
            seen_urls[normalized_url] = int(target["id"])
        return {"eligible": eligible, "rejected": rejected}

    def plan_deterministic(self, task: dict[str, Any], max_targets: int) -> CoveragePlan:
        return self.plan_with_metadata(task, max_targets, "deterministic").plan

    def _plan_deterministic(self, task: dict[str, Any], max_targets: int) -> CoveragePlan:
        eligible = self.eligible_targets(task)
        indexed = list(enumerate(eligible))
        indexed.sort(key=lambda item: _target_sort_key(item[1], task, item[0]))
        selected = [target for _, target in indexed[:max_targets]]
        steps = []
        for target in selected:
            dimension = _matching_dimension(target, task)
            reason = f"优先补齐覆盖缺口：{dimension}" if dimension else f"核验待办：{target.get('documentType')}"
            steps.append(_step_from_target(target, reason))
        return CoveragePlan(summary=f"按覆盖缺口核验 {len(steps)} 个精确官方页面", steps=steps)

    def plan_llm(self, task: dict[str, Any], max_targets: int) -> CoveragePlan:
        return self.plan_with_metadata(task, max_targets, "llm").plan

    def _plan_llm_with_metadata(self, task: dict[str, Any], max_targets: int) -> PlannerRun:
        eligible = self.eligible_targets(task)
        prompt = self._build_prompt(task, eligible, max_targets)
        plan, raw = self._llm_plan(prompt)
        allowed = {int(item["id"]): item for item in eligible}
        steps = []
        seen: set[int] = set()
        proposed_target_ids = [step.target_id for step in plan.steps]
        guard_rejected_target_ids = []
        for step in plan.steps:
            target = allowed.get(step.target_id)
            if target is not None and step.target_id not in seen:
                steps.append(_step_from_target(target, step.reason))
                seen.add(step.target_id)
            else:
                guard_rejected_target_ids.append(step.target_id)
        validated = CoveragePlan(summary=plan.summary, steps=steps[:max_targets])
        usage = self._usage_metadata(raw)
        input_tokens = usage["input_tokens"]
        output_tokens = usage["output_tokens"]
        total_tokens = usage["total_tokens"]
        rates_configured = (
            self.settings.planner_pricing_mode == "METERED"
            and self.settings.planner_input_cost_per_million_usd > 0
            and self.settings.planner_output_cost_per_million_usd > 0
        )
        estimated_cost = None
        if total_tokens > 0 and rates_configured:
            estimated_cost = round(
                input_tokens * self.settings.planner_input_cost_per_million_usd / 1_000_000
                + output_tokens * self.settings.planner_output_cost_per_million_usd / 1_000_000,
                8,
            )
        response_metadata = getattr(raw, "response_metadata", {}) or {}
        return PlannerRun(plan=validated, metadata=PlannerRunMetadata(
            mode="llm",
            prompt_version=self.settings.planner_prompt_version,
            prompt_hash=hashlib.sha256(prompt.encode("utf-8")).hexdigest(),
            model=str(response_metadata.get("model_name") or self.settings.openai_model),
            input_tokens=input_tokens,
            output_tokens=output_tokens,
            total_tokens=total_tokens,
            estimated_cost_usd=estimated_cost,
            usage_status="MEASURED" if total_tokens > 0 else "UNAVAILABLE",
            cost_status=(
                "UNMETERED" if total_tokens > 0 and self.settings.planner_pricing_mode == "UNMETERED"
                else "ESTIMATED" if estimated_cost is not None
                else "USAGE_UNAVAILABLE" if total_tokens <= 0
                else "RATE_UNCONFIGURED"
            ),
            request_id=str(getattr(raw, "id", "") or response_metadata.get("request_id") or ""),
            proposed_target_ids=proposed_target_ids,
            guard_rejected_target_ids=guard_rejected_target_ids,
            guard_intervention_count=len(guard_rejected_target_ids),
        ))

    def _llm_plan(self, prompt: str) -> tuple[CoveragePlan, Any]:
        if self._model is None:
            from langchain_openai import ChatOpenAI

            self._model = ChatOpenAI(
                model=self.settings.openai_model,
                api_key=self.settings.openai_api_key,
                base_url=self.settings.openai_base_url or None,
                temperature=0,
                timeout=self.settings.request_timeout_seconds,
                max_retries=2,
            ).with_structured_output(CoveragePlan, include_raw=True)
        result = self._model.invoke(prompt)
        if not isinstance(result, dict) or result.get("parsed") is None:
            parsing_error = result.get("parsing_error") if isinstance(result, dict) else None
            raise RuntimeError(f"LLM planner returned invalid structured output: {parsing_error or 'missing parsed result'}")
        return CoveragePlan.model_validate(result["parsed"]), result.get("raw")

    def _build_prompt(self, task: dict[str, Any], eligible: list[dict[str, Any]], max_targets: int) -> str:
        if self.settings.planner_prompt_version != "coverage-planner-v3":
            raise ValueError(f"unsupported planner prompt version: {self.settings.planner_prompt_version}")
        return (
            "提示词版本=coverage-planner-v3。你是招生数据运营规划器。"
            "只能选择给定的精确官方URL目标，不得猜测URL。"
            "按缺失维度和目标年份原顺序决定优先级，已核验、重复、不相关或不安全目标已经由系统剔除。"
            f"最多选择{max_targets}项；输出每个选择的目标ID和可审计理由。\n"
            f"任务={json.dumps({'school': task.get('schoolName'), 'missing': task.get('missingDimensions'), 'targets': eligible}, ensure_ascii=False)}"
        )

    @staticmethod
    def _usage_metadata(raw: Any) -> dict[str, int]:
        usage = getattr(raw, "usage_metadata", None) or {}
        response_metadata = getattr(raw, "response_metadata", {}) or {}
        token_usage = response_metadata.get("token_usage") or {}
        input_tokens = int(usage.get("input_tokens") or token_usage.get("prompt_tokens") or 0)
        output_tokens = int(usage.get("output_tokens") or token_usage.get("completion_tokens") or 0)
        total_tokens = int(usage.get("total_tokens") or token_usage.get("total_tokens") or input_tokens + output_tokens)
        return {"input_tokens": input_tokens, "output_tokens": output_tokens, "total_tokens": total_tokens}


class CoverageWorkflow:
    def __init__(self, settings: Settings, sync_index: Callable[[], Any], run_evaluation: Callable[[], Any]):
        self.settings = settings
        self.planner = CoveragePlanner(settings)
        self.verifier = EvidenceVerificationGraph()
        self.sync_index = sync_index
        self.run_evaluation = run_evaluation
        workflow_path = settings.data_dir / "coverage-workflow-checkpoints.sqlite3"
        self._connection = sqlite3.connect(workflow_path, check_same_thread=False)
        self.checkpointer = SqliteSaver(self._connection)
        self.run_store = WorkflowRunStore(settings.data_dir / "workflow-runs.sqlite3")
        self.graph = self._build_graph()

    def close(self) -> None:
        self.run_store.close()
        self._connection.close()

    def start(self, school_name: str, max_targets: int, thread_id: str) -> dict[str, Any]:
        self.run_store.start(thread_id, school_name)
        try:
            state = self.graph.invoke({
                "school_name": school_name,
                "max_targets": max_targets,
                "collected": [],
                "verified_results": [],
                "published": [],
                "trace": [],
            }, self._config(thread_id))
            self.run_store.update(thread_id, state)
            return state
        except Exception as error:
            self.run_store.update(thread_id, {"school_name": school_name, "phase": "FAILED"}, str(error))
            raise

    def resume(self, thread_id: str, approved: bool, feedback: str) -> dict[str, Any]:
        try:
            state = self.graph.invoke(
                Command(resume={"approved": approved, "feedback": feedback}),
                self._config(thread_id),
            )
            self.run_store.update(thread_id, state)
            return state
        except Exception as error:
            snapshot = self.graph.get_state(self._config(thread_id))
            state = dict(snapshot.values) if snapshot else {"phase": "FAILED"}
            self.run_store.update(thread_id, state, str(error))
            raise

    def runs(self, limit: int = 20) -> list[dict[str, Any]]:
        return self.run_store.list(limit)

    def metrics(self) -> dict[str, Any]:
        return self.run_store.metrics()

    def _config(self, thread_id: str) -> dict[str, dict[str, str]]:
        return {"configurable": {"thread_id": thread_id}}

    def _build_graph(self):
        def inspect(state: CoverageWorkflowState) -> dict[str, Any]:
            with httpx.Client(base_url=self.settings.spring_base_url, timeout=self.settings.request_timeout_seconds) as client:
                tasks = client.get("/api/data-coverage/tasks", params={"limit": 100, "status": "ALL"}).raise_for_status().json().get("data", [])
                task = next((item for item in tasks if item.get("schoolName") == state["school_name"]), None)
                if task is None:
                    raise ValueError("school collection task not found")
                documents = client.get("/api/source-documents", params={"schoolId": task["schoolId"]}).raise_for_status().json().get("data", [])
            return {
                "task": task,
                "school_id": int(task["schoolId"]),
                "existing_urls": [str(item.get("sourceUrl") or "") for item in documents],
                "phase": "PLANNING",
                "trace": [f"inspect:coverage={task.get('coveragePercent')}:missing={len(task.get('missingDimensions', []))}"],
            }

        def plan(state: CoverageWorkflowState) -> dict[str, Any]:
            run = self.planner.plan_with_metadata(state["task"], state.get("max_targets", 3))
            result = run.plan
            return {
                "plan": [item.model_dump() for item in result.steps],
                "planner_mode": self.planner.mode,
                "planner_metadata": run.metadata.model_dump(),
                "phase": "COLLECTING" if result.steps else "COMPLETED",
                "route": "collect" if result.steps else "no_targets",
                "trace": [
                    f"plan:{self.planner.mode}:steps={len(result.steps)}:prompt={run.metadata.prompt_version}"
                ],
            }

        def dispatch(state: CoverageWorkflowState):
            if not state.get("plan"):
                return "finish"
            return [Send("collect", {
                "school_name": state["school_name"],
                "school_id": state["school_id"],
                "existing_urls": state.get("existing_urls", []),
                "step": step,
            }) for step in state["plan"]]

        def collect(state: CoverageWorkflowState) -> dict[str, Any]:
            step = state["step"]
            result = self._collect(step, state["school_id"], state.get("existing_urls", []))
            return {"collected": [result], "trace": [f"collect:{step['target_id']}:{result['status']}"]}

        def prepare_verification(_: CoverageWorkflowState) -> dict[str, Any]:
            return {"phase": "VERIFYING", "trace": ["verifier:dispatch"]}

        def dispatch_verification(state: CoverageWorkflowState):
            collected = state.get("collected", [])
            if not collected:
                return "finish"
            return [Send("verify_candidate", {
                "school_name": state["school_name"],
                "candidate": candidate,
            }) for candidate in collected]

        def verify_candidate(state: CoverageWorkflowState) -> dict[str, Any]:
            result = self.verifier.verify(state["candidate"], state["school_name"])
            return {
                "verified_results": [result],
                "trace": [
                    f"verifier:{result.get('target_id')}:{result['status']}:score={result['quality_score']}"
                ],
            }

        def finalize_verification(state: CoverageWorkflowState) -> dict[str, Any]:
            candidates = [item for item in state.get("verified_results", []) if item.get("status") == "VERIFIED"]
            rejected = [item for item in state.get("verified_results", []) if item.get("status") != "VERIFIED"]
            return {
                "candidates": candidates,
                "rejected_candidates": rejected,
                "phase": "WAITING_HUMAN" if candidates else "COMPLETED",
                "route": "review" if candidates else "no_candidates",
                "trace": [f"verifier:finalize=accepted:{len(candidates)},rejected:{len(rejected)}"],
            }

        def human_review(state: CoverageWorkflowState) -> dict[str, Any]:
            decision = interrupt({
                "reason": "发布官方资料并重建私域索引属于高风险写操作",
                "school": state["school_name"],
                "candidates": [_public_candidate(item) for item in state.get("candidates", [])],
                "rejected": [_public_candidate(item) for item in state.get("rejected_candidates", [])],
            })
            approved = bool(decision.get("approved")) if isinstance(decision, dict) else False
            feedback = str(decision.get("feedback") or "") if isinstance(decision, dict) else ""
            return {
                "approved": approved,
                "feedback": feedback,
                "phase": "PUBLISHING" if approved else "REJECTED",
                "route": "publish" if approved else "rejected",
                "trace": ["human:approved" if approved else "human:rejected"],
            }

        def publish(state: CoverageWorkflowState) -> dict[str, Any]:
            if not self.settings.internal_token:
                raise RuntimeError("agent internal token is not configured")
            published = []
            headers = {"X-Agent-Service-Token": self.settings.internal_token}
            with httpx.Client(base_url=self.settings.spring_base_url, timeout=self.settings.request_timeout_seconds) as client:
                for candidate in state.get("candidates", []):
                    payload = {
                        "targetId": candidate["target_id"],
                        "document": candidate["document"],
                        "feedback": state.get("feedback", ""),
                    }
                    data = client.post("/api/internal/agent/evidence", json=payload, headers=headers).raise_for_status().json().get("data", {})
                    published.append(data)
            return {"published": published, "phase": "INDEXING", "trace": [f"publish:documents={len(published)}"]}

        def reindex(_: CoverageWorkflowState) -> dict[str, Any]:
            result = self.sync_index()
            payload = result.model_dump() if hasattr(result, "model_dump") else dict(result)
            return {"index_result": payload, "phase": "EVALUATING", "trace": [f"index:chunks={payload.get('chunks', 0)}"]}

        def evaluate(_: CoverageWorkflowState) -> dict[str, Any]:
            result = self.run_evaluation()
            payload = result.model_dump() if hasattr(result, "model_dump") else dict(result)
            return {"evaluation_result": payload, "phase": "COMPLETED", "route": "completed", "trace": [f"evaluate:cases={payload.get('cases', 0)}"]}

        def finish(_: CoverageWorkflowState) -> dict[str, Any]:
            return {"phase": "COMPLETED", "route": "completed", "trace": ["finish:no_publishable_targets"]}

        builder = StateGraph(CoverageWorkflowState)
        builder.add_node("inspect", inspect)
        builder.add_node("plan", plan)
        builder.add_node("collect", collect)
        builder.add_node("prepare_verification", prepare_verification)
        builder.add_node("verify_candidate", verify_candidate)
        builder.add_node("finalize_verification", finalize_verification)
        builder.add_node("human_review", human_review)
        builder.add_node("publish", publish)
        builder.add_node("reindex", reindex)
        builder.add_node("evaluate", evaluate)
        builder.add_node("finish", finish)
        builder.add_edge(START, "inspect")
        builder.add_edge("inspect", "plan")
        builder.add_conditional_edges("plan", dispatch, ["collect", "finish"])
        builder.add_edge("collect", "prepare_verification")
        builder.add_conditional_edges("prepare_verification", dispatch_verification, ["verify_candidate", "finish"])
        builder.add_edge("verify_candidate", "finalize_verification")
        builder.add_conditional_edges("finalize_verification", lambda state: state["route"], {
            "review": "human_review", "no_candidates": "finish",
        })
        builder.add_conditional_edges("human_review", lambda state: state["route"], {
            "publish": "publish", "rejected": END,
        })
        builder.add_edge("publish", "reindex")
        builder.add_edge("reindex", "evaluate")
        builder.add_edge("evaluate", END)
        builder.add_edge("finish", END)
        return builder.compile(checkpointer=self.checkpointer)

    def _collect(self, step: dict[str, Any], school_id: int, existing_urls: list[str]) -> dict[str, Any]:
        url = str(step["source_url"])
        existing_document = url in existing_urls
        try:
            _assert_safe_official_url(url)
            with httpx.Client(
                timeout=self.settings.workflow_fetch_timeout_seconds,
                follow_redirects=True,
                headers={"User-Agent": "CS-Kaoyan-AI-EvidenceCollector/1.0"},
            ) as client:
                response = client.get(url)
                response.raise_for_status()
                _assert_safe_official_url(str(response.url))
                content_type = response.headers.get("content-type", "").lower()
                content = response.content
            if len(content) > self.settings.workflow_max_content_bytes:
                raise ValueError("response exceeds workflow content limit")
            if "pdf" in content_type or urlparse(url).path.lower().endswith(".pdf"):
                raise ValueError("PDF targets require the existing manual parser before autonomous publication")
            encoding = response.encoding or "utf-8"
            html = content.decode(encoding, errors="replace")
            extractor = _HtmlTextExtractor()
            extractor.feed(html)
            raw_text = extractor.text[:12000]
            if len(raw_text) < 120:
                raise ValueError("official page text is too short")
            title = extractor.title or step["title"]
            document = {
                "title": title[:255],
                "documentType": step["document_type"],
                "sourceUrl": url,
                "schoolId": school_id,
                "collegeId": None,
                "majorId": None,
                "year": step["target_year"],
                "auditStatus": "PUBLISHED",
                "sourceReliability": "OFFICIAL",
                "rawText": raw_text,
                "remark": "CoverageWorkflow自动采集；发布前已完成人工审核。",
            }
            return {
                **step,
                "status": "VERIFIED",
                "content_length": len(raw_text),
                "existing_document": existing_document,
                "evidence_hash": hashlib.sha256(raw_text.encode("utf-8")).hexdigest(),
                "document": document,
            }
        except Exception as error:
            return {**step, "status": "REJECTED", "reason": str(error)}


def _step_from_target(target: dict[str, Any], reason: str) -> CoveragePlanStep:
    return CoveragePlanStep(
        target_id=int(target["id"]),
        title=str(target.get("title") or "官方招生资料"),
        document_type=str(target.get("documentType") or "招生资料"),
        target_year=int(target.get("targetYear") or 0),
        source_url=str(target.get("sourceUrl") or ""),
        reason=reason,
    )


def _stable_hash(value: Any) -> str:
    payload = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


_DIMENSION_DOCUMENT_TYPES = {
    "学院": "招生专业目录",
    "专业": "招生专业目录",
    "考试科目": "招生专业目录",
    "招生计划": "招生专业目录",
    "复试线": "复试分数线",
    "录取结果": "拟录取名单",
    "复试规则": "复试录取细则",
    "官方证据": "官方招生公告",
}


def _matching_dimension(target: dict[str, Any], task: dict[str, Any]) -> str | None:
    document_type = str(target.get("documentType") or "")
    for dimension in task.get("missingDimensions", []):
        if _DIMENSION_DOCUMENT_TYPES.get(str(dimension)) == document_type:
            return str(dimension)
    return None


def _target_priority(target: dict[str, Any], task: dict[str, Any]) -> int:
    dimension = _matching_dimension(target, task)
    missing = [str(item) for item in task.get("missingDimensions", [])]
    return missing.index(dimension) if dimension in missing else len(missing) + 1


def _target_sort_key(target: dict[str, Any], task: dict[str, Any], original_index: int) -> tuple[int, int, int]:
    target_years = [int(item) for item in task.get("targetYears", []) if str(item).isdigit()]
    try:
        target_year = int(target.get("targetYear") or 0)
    except (TypeError, ValueError):
        target_year = 0
    if target_years:
        year_priority = target_years.index(target_year) if target_year in target_years else len(target_years) + 1
    else:
        year_priority = -target_year
    return _target_priority(target, task), year_priority, original_index


def _is_exact_official_url(url: str) -> bool:
    try:
        parsed = urlparse(url)
        path = parsed.path.rstrip("/").lower()
        if parsed.scheme not in {"http", "https"} or not parsed.hostname or not path:
            return False
        if parsed.username or parsed.password or parsed.port not in {None, 80, 443}:
            return False
        if path.endswith(("/main.htm", "/index.htm", "/list.htm")):
            return False
        return _is_official_hostname(parsed.hostname)
    except ValueError:
        return False


def _normalized_target_url(url: str) -> str:
    try:
        parsed = urlparse(url.strip())
        if not parsed.scheme or not parsed.hostname:
            return ""
        port = f":{parsed.port}" if parsed.port else ""
        path = re.sub(r"/{2,}", "/", parsed.path).rstrip("/") or "/"
        query = f"?{parsed.query}" if parsed.query else ""
        return f"{parsed.scheme.lower()}://{parsed.hostname.lower()}{port}{path}{query}"
    except ValueError:
        return ""


def _assert_safe_official_url(url: str) -> None:
    parsed = urlparse(url)
    if not _is_exact_official_url(url):
        raise ValueError("target must be an exact official article URL")
    if parsed.username or parsed.password or parsed.port not in {None, 80, 443}:
        raise ValueError("URL credentials and non-standard ports are not allowed")
    hostname = parsed.hostname or ""
    try:
        addresses = {item[4][0] for item in socket.getaddrinfo(hostname, parsed.port or 443)}
    except socket.gaierror as error:
        raise ValueError("official hostname cannot be resolved") from error
    for address in addresses:
        ip = ipaddress.ip_address(address)
        if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved:
            raise ValueError("private network targets are not allowed")


def _is_official_hostname(hostname: str) -> bool:
    normalized = hostname.rstrip(".").lower()
    return normalized.endswith(".edu.cn") or normalized == "yz.chsi.com.cn" or normalized.endswith(".gov.cn")


def _public_candidate(item: dict[str, Any]) -> dict[str, Any]:
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
