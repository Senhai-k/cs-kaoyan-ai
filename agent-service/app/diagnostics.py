from __future__ import annotations

from typing import Any


def build_diagnostics(
    operation_jobs: list[dict[str, Any]],
    workflow_runs: list[dict[str, Any]],
    query: str = "",
    category: str = "ALL",
    severity: str = "ALL",
    limit: int = 50,
) -> dict[str, Any]:
    entries: list[dict[str, Any]] = []
    latest_results: set[str] = set()
    for job in operation_jobs:
        operation_type = str(job.get("operation_type") or "UNKNOWN")
        timestamp = str(job.get("updated_at") or job.get("created_at") or "")
        if job.get("status") in {"FAILED", "CANCELLED"}:
            entries.append(_entry(
                f"operation:{job.get('id')}",
                "OPERATION_FAILURE",
                "ERROR" if job.get("status") == "FAILED" else "WARNING",
                f"{operation_type} {job.get('status')}",
                str(job.get("error") or "任务被取消"),
                timestamp,
                operation_type=operation_type,
                job_id=str(job.get("id") or ""),
                trace_id=str(job.get("trace_id") or ""),
            ))

        result = job.get("result")
        if job.get("status") != "COMPLETED" or not isinstance(result, dict):
            continue
        gate = result.get("quality_gate")
        if isinstance(gate, dict) and gate.get("status") == "FAILED":
            for check in gate.get("failed_checks") or []:
                metric = str(check.get("metric") or "unknown")
                entries.append(_entry(
                    f"gate:{job.get('id')}:{metric}",
                    "QUALITY_GATE",
                    "ERROR",
                    str(check.get("label") or metric),
                    f"实际 {check.get('actual')}，要求 {check.get('comparator')} {check.get('threshold')}",
                    timestamp,
                    operation_type=operation_type,
                    job_id=str(job.get("id") or ""),
                    trace_id=str(job.get("trace_id") or ""),
                    code=metric,
                ))

        result_group = "PLANNER" if operation_type in {"PLANNER_EVALUATION", "PLANNER_REPLAY"} else operation_type
        if result_group in latest_results:
            continue
        latest_results.add(result_group)
        if result_group == "PLANNER":
            entries.extend(_planner_entries(job, result, timestamp))
        elif operation_type == "COVERAGE_EVALUATION":
            entries.extend(_coverage_entries(job, result, timestamp))
        elif operation_type == "KNOWLEDGE_AUDIT":
            entries.extend(_audit_entries(job, result, timestamp))

    for run in workflow_runs:
        status = str(run.get("status") or "")
        if status not in {"FAILED", "REJECTED"} and int(run.get("rejected_count") or 0) == 0:
            continue
        trace = run.get("trace") or []
        detail = str(run.get("error") or (trace[-1] if trace else "工作流存在拒绝候选"))
        entries.append(_entry(
            f"workflow:{run.get('thread_id')}",
            "WORKFLOW",
            "ERROR" if status == "FAILED" else "WARNING",
            f"{run.get('school_name') or '未知院校'} · {status or 'REJECTED_CANDIDATE'}",
            detail,
            str(run.get("updated_at") or ""),
            workflow_id=str(run.get("thread_id") or ""),
            school_name=str(run.get("school_name") or ""),
            code=str(run.get("phase") or ""),
        ))

    normalized_query = query.strip().lower()
    normalized_category = category.strip().upper() or "ALL"
    normalized_severity = severity.strip().upper() or "ALL"
    filtered = [item for item in entries if (
        (normalized_category == "ALL" or item["category"] == normalized_category)
        and (normalized_severity == "ALL" or item["severity"] == normalized_severity)
        and (not normalized_query or normalized_query in _search_text(item))
    )]
    filtered.sort(key=lambda item: item["timestamp"], reverse=True)
    counts: dict[str, int] = {}
    for item in filtered:
        counts[item["category"]] = counts.get(item["category"], 0) + 1
    safe_limit = max(1, min(int(limit), 100))
    return {
        "total": len(filtered),
        "counts": counts,
        "query": query.strip(),
        "category": normalized_category,
        "severity": normalized_severity,
        "items": filtered[:safe_limit],
    }


def _planner_entries(job: dict[str, Any], result: dict[str, Any], timestamp: str) -> list[dict[str, Any]]:
    entries = []
    deterministic = result.get("deterministic") or {}
    for detail in deterministic.get("details") or []:
        for rejection in detail.get("eligibility_rejections") or []:
            reason = str(rejection.get("reason") or "filtered")
            target_id = rejection.get("target_id")
            kept = rejection.get("kept_target_id")
            suffix = f"，保留目标 {kept}" if kept is not None else ""
            entries.append(_entry(
                f"planner:{job.get('id')}:{detail.get('id')}:{target_id}:{reason}",
                "PLANNER_FILTER",
                "INFO",
                f"规划目标 {target_id} 已过滤",
                f"案例 {detail.get('id')}：{reason}{suffix}",
                timestamp,
                operation_type=str(job.get("operation_type") or ""),
                job_id=str(job.get("id") or ""),
                trace_id=str(job.get("trace_id") or ""),
                code=reason,
            ))
    return entries


def _coverage_entries(job: dict[str, Any], result: dict[str, Any], timestamp: str) -> list[dict[str, Any]]:
    return [
        _entry(
            f"coverage:{job.get('id')}:{detail.get('id')}",
            "EVIDENCE_POLICY",
            "ERROR",
            f"证据策略案例 {detail.get('id')} 未通过",
            str(detail.get("reason") or "预期与实际状态不一致"),
            timestamp,
            operation_type="COVERAGE_EVALUATION",
            job_id=str(job.get("id") or ""),
            trace_id=str(job.get("trace_id") or ""),
            code=str(detail.get("actualStatus") or "MISMATCH"),
        )
        for detail in result.get("details") or [] if not detail.get("matched", False)
    ]


def _audit_entries(job: dict[str, Any], result: dict[str, Any], timestamp: str) -> list[dict[str, Any]]:
    return [
        _entry(
            f"audit:{job.get('id')}:{sample.get('documentId')}",
            "KNOWLEDGE_AUDIT",
            "WARNING",
            str(sample.get("title") or f"资料 {sample.get('documentId')}"),
            str(sample.get("reason") or "资料未通过审计"),
            timestamp,
            operation_type="KNOWLEDGE_AUDIT",
            job_id=str(job.get("id") or ""),
            trace_id=str(job.get("trace_id") or ""),
            school_name=str(sample.get("schoolName") or ""),
            source_url=str(sample.get("sourceUrl") or ""),
            code=str(sample.get("status") or "REJECTED"),
        )
        for sample in result.get("samples") or [] if sample.get("status") != "VERIFIED"
    ]


def _entry(
    entry_id: str,
    category: str,
    severity: str,
    title: str,
    detail: str,
    timestamp: str,
    **context: str,
) -> dict[str, Any]:
    return {
        "id": entry_id,
        "category": category,
        "severity": severity,
        "title": title,
        "detail": detail,
        "timestamp": timestamp,
        **context,
    }


def _search_text(item: dict[str, Any]) -> str:
    return " ".join(str(value) for value in item.values() if value is not None).lower()
