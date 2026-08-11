from __future__ import annotations

import argparse
import json
import tempfile
from pathlib import Path
from typing import Any


GATE_VERSION = "quality-gate-v3"

_RULES: dict[str, list[tuple[str, str, str, float]]] = {
    "RAG_EVALUATION": [
        ("cases", "评估案例数", ">=", 30),
        ("recall_at_5", "Recall@5", ">=", 0.95),
        ("target_recall_at_1", "目标 Recall@1", ">=", 0.90),
        ("hit_rate_at_1", "案例 Hit@1", ">=", 0.95),
        ("boundary_safety_rate", "边界安全率", ">=", 1.0),
        ("answer_support_rate", "答案证据支持率", ">=", 0.95),
        ("citation_groundedness", "引用落地率", ">=", 0.95),
        ("citation_source_url_rate", "来源 URL 落地率", ">=", 0.95),
        ("school_scope_accuracy", "院校范围准确率", ">=", 0.95),
        ("task_completion_rate", "任务完成率", ">=", 0.95),
    ],
    "COVERAGE_EVALUATION": [
        ("cases", "证据策略案例数", ">=", 8),
        ("accuracy", "证据策略准确率", ">=", 0.95),
        ("false_accept_rate", "错误接纳率", "<=", 0.0),
        ("false_reject_rate", "错误拒绝率", "<=", 0.0),
    ],
    "KNOWLEDGE_AUDIT": [
        ("total_documents", "审计资料数", ">=", 100),
        ("pass_rate", "知识库通过率", ">=", 0.95),
        ("average_quality_score", "资料平均质量", ">=", 90.0),
    ],
    "PLANNER_EVALUATION": [
        ("cases", "规划案例数", ">=", 10),
        ("deterministic.exact_match_rate", "规划 Exact Match", ">=", 0.95),
        ("deterministic.unsafe_selection_rate", "不安全目标选择率", "<=", 0.0),
    ],
    "PLANNER_REPLAY": [
        ("cases", "回放案例数", ">=", 10),
        ("deterministic.exact_match_rate", "回放 Exact Match", ">=", 0.95),
        ("deterministic.unsafe_selection_rate", "回放不安全选择率", "<=", 0.0),
    ],
    "RERANKER_BENCHMARK": [
        ("cases", "重排基准案例数", ">=", 30),
        ("completed_modes", "完成模式数", ">=", 3),
        ("recommended.metrics.recall_at_5", "推荐模式 Recall@5", ">=", 0.95),
        ("recommended.metrics.target_recall_at_1", "推荐模式目标 Recall@1", ">=", 0.90),
        ("recommended.metrics.mean_reciprocal_rank_at_5", "推荐模式 MRR@5", ">=", 0.95),
        ("recommended.metrics.citation_source_url_rate", "推荐模式来源 URL", ">=", 0.95),
        ("recommended.metrics.task_completion_rate", "推荐模式任务完成率", ">=", 0.95),
    ],
}


def attach_quality_gate(operation_type: str, result: Any) -> dict[str, Any]:
    payload = result.model_dump() if hasattr(result, "model_dump") else dict(result)
    return {**payload, "quality_gate": evaluate_quality_gate(operation_type, payload)}


def evaluate_quality_gate(operation_type: str, result: dict[str, Any]) -> dict[str, Any]:
    rules = list(_RULES.get(operation_type, []))
    if operation_type in {"PLANNER_EVALUATION", "PLANNER_REPLAY"} and result.get("llm_required"):
        pricing_mode = str((result.get("llm_readiness") or {}).get("pricingMode") or "METERED")
        expected_cost_status = "UNMETERED" if pricing_mode == "UNMETERED" else "ESTIMATED"
        rules.extend([
            ("llm_readiness.status", "LLM 实验配置", "==", "READY"),
            ("llm.status", "LLM 对照状态", "==", "COMPLETED"),
            ("llm.cases", "LLM 对照案例数", ">=", 10),
            ("llm.completed_cases", "LLM 完成案例数", ">=", 10),
            ("llm.failed_cases", "LLM 失败案例数", "<=", 0),
            ("llm.exact_match_rate", "LLM Exact Match", ">=", 0.90),
            ("llm.target_recall", "LLM 目标召回率", ">=", 0.90),
            ("llm.usage_coverage_rate", "LLM Usage 覆盖率", ">=", 1.0),
            ("llm.cost_status", "LLM 费用状态", "==", expected_cost_status),
            ("llm.unsafe_selection_rate", "LLM 不安全选择率", "<=", 0.0),
            ("llm.invalid_proposal_rate", "LLM 越界提议率", "<=", 0.0),
        ])
    if not rules:
        return {
            "version": GATE_VERSION,
            "status": "NOT_APPLICABLE",
            "passed": True,
            "checks": [],
            "failed_checks": [],
        }

    checks = []
    for metric, label, comparator, threshold in rules:
        actual = _read_metric(result, metric)
        passed = actual is not None and _compare(actual, comparator, threshold)
        checks.append({
            "metric": metric,
            "label": label,
            "comparator": comparator,
            "threshold": threshold,
            "actual": actual,
            "passed": passed,
        })
    failed = [item for item in checks if not item["passed"]]
    return {
        "version": GATE_VERSION,
        "status": "PASSED" if not failed else "FAILED",
        "passed": not failed,
        "checks": checks,
        "failed_checks": failed,
    }


def enforce_quality_gate(operation_type: str, result: dict[str, Any]) -> dict[str, Any]:
    gate = evaluate_quality_gate(operation_type, result)
    if not gate["passed"]:
        failures = ", ".join(
            f"{item['metric']}={item['actual']} {item['comparator']} {item['threshold']}"
            for item in gate["failed_checks"]
        )
        raise RuntimeError(f"{operation_type} quality gate failed: {failures}")
    return gate


def _read_metric(result: dict[str, Any], path: str) -> Any:
    value: Any = result
    for part in path.split("."):
        if not isinstance(value, dict) or part not in value:
            return None
        value = value[part]
    if isinstance(value, bool):
        return None
    return float(value) if isinstance(value, (int, float)) else value


def _compare(actual: Any, comparator: str, threshold: Any) -> bool:
    if comparator == ">=":
        return actual >= threshold
    if comparator == "<=":
        return actual <= threshold
    if comparator == "==":
        return actual == threshold
    raise ValueError(f"unsupported comparator: {comparator}")


def run_offline_gate() -> dict[str, Any]:
    from .config import Settings
    from .coverage_evaluation import run_coverage_evaluation
    from .coverage_workflow import CoveragePlanner
    from .evidence_verification import EvidenceVerificationGraph
    from .planner_evaluation import run_planner_evaluation

    root = Path(__file__).resolve().parents[1]
    with tempfile.TemporaryDirectory(prefix="cs-kaoyan-quality-gate-") as data_dir:
        planner = CoveragePlanner(Settings(data_dir=Path(data_dir), openai_api_key=""))
        planner_result = run_planner_evaluation(planner, root / "evals" / "planner_eval.json")
        coverage_result = run_coverage_evaluation(
            EvidenceVerificationGraph(), root / "evals" / "coverage_eval.json"
        ).model_dump()
    planner_gate = enforce_quality_gate("PLANNER_EVALUATION", planner_result)
    coverage_gate = enforce_quality_gate("COVERAGE_EVALUATION", coverage_result)
    return {
        "version": GATE_VERSION,
        "status": "PASSED",
        "planner": planner_gate,
        "coverage": coverage_gate,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Run deterministic Agent quality gates")
    parser.parse_args()
    try:
        print(json.dumps(run_offline_gate(), ensure_ascii=False))
        return 0
    except Exception as error:
        print(json.dumps({"version": GATE_VERSION, "status": "FAILED", "error": str(error)}, ensure_ascii=False))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
