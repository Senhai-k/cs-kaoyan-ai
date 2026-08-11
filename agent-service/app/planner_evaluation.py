from __future__ import annotations

import hashlib
import json
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

from .coverage_workflow import CoveragePlanner, PlannerRun
from .operation_control import CancelCheck, ProgressCallback, ensure_not_cancelled, report_progress


def run_planner_evaluation(
    planner: CoveragePlanner,
    cases_path: Path,
    progress: ProgressCallback | None = None,
    cancel_check: CancelCheck | None = None,
    require_llm: bool = False,
) -> dict[str, Any]:
    dataset = json.loads(cases_path.read_text(encoding="utf-8"))
    if isinstance(dataset, list):
        cases = dataset
        dataset_version = "legacy-v1"
    else:
        cases = list(dataset.get("cases") or [])
        dataset_version = str(dataset.get("datasetVersion") or "unversioned")
    dataset_hash = _stable_hash(cases)
    total = len(cases) * (2 if planner.llm_available or require_llm else 1)
    completed = 0
    report_progress(progress, completed, total, "准备规划器 A/B 评估")

    deterministic, completed = _evaluate_mode(
        "deterministic",
        lambda task, limit: planner.plan_with_metadata(task, limit, "deterministic"),
        planner,
        cases,
        completed,
        total,
        progress,
        cancel_check,
    )
    if planner.llm_available:
        try:
            llm, completed = _evaluate_mode(
                "llm",
                lambda task, limit: planner.plan_with_metadata(task, limit, "llm"),
                planner,
                cases,
                completed,
                total,
                progress,
                cancel_check,
                continue_on_error=True,
            )
        except Exception as error:
            llm = _empty_mode("FAILED", str(error), planner.settings.planner_prompt_version, planner.settings.openai_model)
    else:
        llm = _empty_mode(
            "SKIPPED",
            "AGENT_OPENAI_API_KEY is not configured",
            planner.settings.planner_prompt_version,
            planner.settings.openai_model,
        )
        report_progress(progress, total, total, "LLM 规划器配置不完整，已记录阻断原因")

    result = {
        "evaluation_id": str(uuid.uuid4()),
        "run_type": "LIVE",
        "created_at": _now(),
        "cases": len(cases),
        "dataset_version": dataset_version,
        "dataset_hash": dataset_hash,
        "prompt_version": planner.settings.planner_prompt_version,
        "configured_model": planner.settings.openai_model,
        "llm_readiness": planner.llm_readiness,
        "llm_required": require_llm,
        "deterministic": deterministic,
        "llm": llm,
        "recommended_mode": _recommended_mode(deterministic, llm),
        "replay_available": True,
        "replay_artifact": planner.settings.planner_replay_path.name,
        "replayed_from_evaluation_id": None,
    }
    _write_replay_artifact(planner.settings.planner_replay_path, result)
    return result


def replay_planner_evaluation(
    replay_path: Path,
    progress: ProgressCallback | None = None,
    cancel_check: CancelCheck | None = None,
) -> dict[str, Any]:
    ensure_not_cancelled(cancel_check)
    if not replay_path.exists():
        raise RuntimeError("planner replay artifact does not exist; run PLANNER_EVALUATION first")
    artifact = json.loads(replay_path.read_text(encoding="utf-8"))
    source = artifact.get("result")
    if not isinstance(source, dict) or artifact.get("artifact_type") != "planner-evaluation-replay":
        raise RuntimeError("invalid planner replay artifact")
    if artifact.get("result_hash") != _stable_hash(source):
        raise RuntimeError("planner replay artifact hash mismatch")

    report_progress(progress, 0, 2, "校验规划器回放工件")
    ensure_not_cancelled(cancel_check)
    deterministic = _recompute_mode(source.get("deterministic") or {})
    report_progress(progress, 1, 2, "重算 deterministic 指标")
    ensure_not_cancelled(cancel_check)
    llm = _recompute_mode(source.get("llm") or {})
    report_progress(progress, 2, 2, "重算 LLM 指标")
    return {
        **source,
        "evaluation_id": str(uuid.uuid4()),
        "run_type": "REPLAY",
        "created_at": _now(),
        "deterministic": deterministic,
        "llm": llm,
        "recommended_mode": _recommended_mode(deterministic, llm),
        "replayed_from_evaluation_id": source.get("evaluation_id"),
    }


def _evaluate_mode(
    mode: str,
    runner: Callable[[dict[str, Any], int], PlannerRun],
    planner: CoveragePlanner,
    cases: list[dict[str, Any]],
    completed: int,
    total: int,
    progress: ProgressCallback | None,
    cancel_check: CancelCheck | None,
    continue_on_error: bool = False,
) -> tuple[dict[str, Any], int]:
    details = []
    latency_total = 0.0
    for case in cases:
        ensure_not_cancelled(cancel_check)
        started = time.perf_counter()
        expected = [int(item) for item in case.get("expectedTargetIds", [])]
        eligibility = planner.target_eligibility_report(case["task"])
        eligible = sorted(int(item["id"]) for item in eligibility["eligible"])
        try:
            run = runner(case["task"], int(case.get("maxTargets", 3)))
            latency_ms = (time.perf_counter() - started) * 1000
            selected = [step.target_id for step in run.plan.steps]
            details.append({
                "id": case["id"],
                "status": "COMPLETED",
                "error": "",
                "selected_target_ids": selected,
                "expected_target_ids": expected,
                "eligible_target_ids": eligible,
                "eligibility_rejections": eligibility["rejected"],
                "exact_match": selected == expected,
                "latency_ms": round(latency_ms, 3),
                "plan": run.plan.model_dump(),
                "metadata": run.metadata.model_dump(),
                "replay_input": {
                    "task": case["task"],
                    "max_targets": int(case.get("maxTargets", 3)),
                },
            })
        except Exception as error:
            if not continue_on_error:
                raise
            latency_ms = (time.perf_counter() - started) * 1000
            details.append({
                "id": case["id"],
                "status": "FAILED",
                "error": str(error),
                "selected_target_ids": [],
                "expected_target_ids": expected,
                "eligible_target_ids": eligible,
                "eligibility_rejections": eligibility["rejected"],
                "exact_match": False,
                "latency_ms": round(latency_ms, 3),
                "plan": {"summary": "", "steps": []},
                "metadata": {},
                "replay_input": {
                    "task": case["task"],
                    "max_targets": int(case.get("maxTargets", 3)),
                },
            })
        latency_total += latency_ms
        completed += 1
        report_progress(progress, completed, total, f"{mode}: {case['id']}")
    status = "FAILED" if any(item["status"] == "FAILED" for item in details) else "COMPLETED"
    return _aggregate_details(status, details, latency_total), completed


def _aggregate_details(status: str, details: list[dict[str, Any]], latency_total: float) -> dict[str, Any]:
    exact_matches = 0
    selected_total = 0
    expected_total = 0
    true_positives = 0
    unsafe = 0
    input_tokens = 0
    output_tokens = 0
    total_tokens = 0
    estimated_cost = 0.0
    cost_count = 0
    measured_usage = 0
    prompt_versions: set[str] = set()
    models: set[str] = set()
    cost_statuses: set[str] = set()
    proposed_targets = 0
    guard_interventions = 0
    guarded_cases = 0
    for detail in details:
        selected = [int(item) for item in detail.get("selected_target_ids", [])]
        expected = [int(item) for item in detail.get("expected_target_ids", [])]
        eligible = {int(item) for item in detail.get("eligible_target_ids", [])}
        selected_set = set(selected)
        expected_set = set(expected)
        exact_matches += int(selected == expected)
        selected_total += len(selected_set)
        expected_total += len(expected_set)
        true_positives += len(selected_set & expected_set)
        unsafe += len(selected_set - eligible)
        metadata = detail.get("metadata") or {}
        input_tokens += int(metadata.get("input_tokens") or 0)
        output_tokens += int(metadata.get("output_tokens") or 0)
        total_tokens += int(metadata.get("total_tokens") or 0)
        if metadata.get("usage_status") == "MEASURED":
            measured_usage += 1
        if metadata.get("estimated_cost_usd") is not None:
            estimated_cost += float(metadata["estimated_cost_usd"])
            cost_count += 1
        if metadata.get("prompt_version"):
            prompt_versions.add(str(metadata["prompt_version"]))
        if metadata.get("model"):
            models.add(str(metadata["model"]))
        if metadata.get("cost_status"):
            cost_statuses.add(str(metadata["cost_status"]))
        proposed = list(metadata.get("proposed_target_ids") or [])
        rejected = list(metadata.get("guard_rejected_target_ids") or [])
        proposed_targets += len(proposed)
        guard_interventions += len(rejected)
        guarded_cases += int(bool(rejected))
    count = len(details)
    failed_cases = sum(item.get("status") == "FAILED" for item in details)
    not_applicable = bool(details) and all(
        (detail.get("metadata") or {}).get("usage_status") == "NOT_APPLICABLE" for detail in details
    )
    return {
        "status": status,
        "cases": count,
        "completed_cases": count - failed_cases,
        "failed_cases": failed_cases,
        "exact_match_rate": round(exact_matches / count, 4) if count else 0.0,
        "target_precision": round(true_positives / selected_total, 4) if selected_total else 1.0,
        "target_recall": round(true_positives / expected_total, 4) if expected_total else 1.0,
        "unsafe_selection_rate": round(unsafe / selected_total, 4) if selected_total else 0.0,
        "average_latency_ms": round(latency_total / count, 3) if count else 0.0,
        "prompt_version": next(iter(prompt_versions)) if len(prompt_versions) == 1 else "mixed",
        "model": next(iter(models)) if len(models) == 1 else "mixed",
        "input_tokens": input_tokens,
        "output_tokens": output_tokens,
        "total_tokens": total_tokens,
        "usage_coverage_rate": round(measured_usage / count, 4) if count else 0.0,
        "guard_intervention_rate": round(guarded_cases / count, 4) if count else 0.0,
        "invalid_proposal_rate": round(guard_interventions / proposed_targets, 4) if proposed_targets else 0.0,
        "guard_intervention_count": guard_interventions,
        "estimated_cost_usd": round(estimated_cost, 8) if cost_count else None,
        "cost_status": (
            "NOT_APPLICABLE" if not_applicable
            else "ESTIMATED" if count and cost_count == count
            else next(iter(cost_statuses)) if len(cost_statuses) == 1
            else "MIXED"
        ),
        "details": details,
        "reason": f"{failed_cases} planner cases failed" if failed_cases else "",
    }


def _recompute_mode(source: dict[str, Any]) -> dict[str, Any]:
    if source.get("status") != "COMPLETED":
        return source
    details = list(source.get("details") or [])
    latency_total = sum(float(item.get("latency_ms") or 0.0) for item in details)
    return _aggregate_details("COMPLETED", details, latency_total)


def _empty_mode(status: str, reason: str, prompt_version: str, model: str) -> dict[str, Any]:
    return {
        "status": status,
        "cases": 0,
        "completed_cases": 0,
        "failed_cases": 0,
        "exact_match_rate": 0.0,
        "target_precision": 0.0,
        "target_recall": 0.0,
        "unsafe_selection_rate": 0.0,
        "average_latency_ms": 0.0,
        "prompt_version": prompt_version,
        "model": model,
        "input_tokens": 0,
        "output_tokens": 0,
        "total_tokens": 0,
        "usage_coverage_rate": 0.0,
        "guard_intervention_rate": 0.0,
        "invalid_proposal_rate": 0.0,
        "guard_intervention_count": 0,
        "estimated_cost_usd": None,
        "cost_status": "NOT_AVAILABLE",
        "details": [],
        "reason": reason,
    }


def _recommended_mode(deterministic: dict[str, Any], llm: dict[str, Any]) -> str:
    if llm.get("status") == "COMPLETED" and (
        llm["exact_match_rate"], llm["target_recall"], -llm["average_latency_ms"]
    ) > (
        deterministic["exact_match_rate"], deterministic["target_recall"],
        -deterministic["average_latency_ms"],
    ):
        return "llm"
    return "deterministic"


def _write_replay_artifact(path: Path, result: dict[str, Any]) -> None:
    artifact = {
        "artifact_schema_version": 1,
        "artifact_type": "planner-evaluation-replay",
        "result_hash": _stable_hash(result),
        "result": result,
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(artifact, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


def _stable_hash(value: Any) -> str:
    payload = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()
