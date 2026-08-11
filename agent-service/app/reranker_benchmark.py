from __future__ import annotations

import hashlib
import json
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .config import Settings
from .evaluation import run_evaluation
from .generation import GroundedGenerator
from .graph import AdmissionsAgentGraph
from .operation_control import CancelCheck, ProgressCallback, ensure_not_cancelled, report_progress
from .retrieval import HybridRetriever


MODES = ("off", "feature", "cross-encoder")
METRIC_KEYS = (
    "recall_at_5",
    "target_recall_at_1",
    "hit_rate_at_1",
    "mean_reciprocal_rank_at_5",
    "citation_source_url_rate",
    "task_completion_rate",
)


def run_reranker_benchmark(
    settings: Settings,
    source_retriever: HybridRetriever,
    cases_path: Path,
    progress: ProgressCallback | None = None,
    cancel_check: CancelCheck | None = None,
) -> dict[str, Any]:
    cases = json.loads(cases_path.read_text(encoding="utf-8"))
    case_count = len(cases)
    corpus = source_retriever.corpus_snapshot()
    if not corpus:
        raise RuntimeError("reranker benchmark requires a non-empty indexed corpus")
    total = 1 + case_count * len(MODES)
    report_progress(progress, 0, total, "准备隔离重排基准索引")
    ensure_not_cancelled(cancel_check)

    mode_runs: list[dict[str, Any]] = []
    full_results: dict[str, Any] = {}
    with tempfile.TemporaryDirectory(prefix="reranker-benchmark-", dir=settings.data_dir) as temporary:
        benchmark_settings = settings.model_copy(update={
            "data_dir": Path(temporary),
            "openai_api_key": "",
            "local_llm_model": "",
            "enable_reranker": True,
            "reranker_mode": "off",
        })
        retriever = HybridRetriever(benchmark_settings)
        agent = None
        try:
            retriever.index(corpus)
            agent = AdmissionsAgentGraph(
                benchmark_settings,
                retriever,
                GroundedGenerator(benchmark_settings),
            )
            report_progress(progress, 1, total, "隔离索引完成")
            for mode_index, mode in enumerate(MODES):
                ensure_not_cancelled(cancel_check)
                benchmark_settings.reranker_mode = mode
                offset = 1 + mode_index * case_count

                def mode_progress(current: int, _: int, message: str, *, _mode: str = mode) -> None:
                    report_progress(progress, offset + current, total, f"{_mode}: {message}")

                started = time.perf_counter()
                evaluation = run_evaluation(
                    agent,
                    retriever,
                    cases_path,
                    mode_progress,
                    cancel_check,
                ).model_dump()
                duration_ms = (time.perf_counter() - started) * 1000
                full_results[mode] = evaluation
                mode_runs.append({
                    "mode": mode,
                    "status": "COMPLETED",
                    "duration_ms": round(duration_ms, 3),
                    "average_case_latency_ms": round(duration_ms / max(1, case_count), 3),
                    "metrics": {key: evaluation[key] for key in METRIC_KEYS},
                    "failed_case_ids": evaluation["failed_case_ids"],
                    "category_scores": evaluation["category_scores"],
                })
        finally:
            if agent is not None:
                agent.close()
            retriever.close()

    ensure_not_cancelled(cancel_check)
    model_manifest = _model_manifest(Path(settings.reranker_model), cancel_check)
    summary = build_benchmark_summary(
        mode_runs=mode_runs,
        cases=case_count,
        corpus_chunks=len(corpus),
        dataset_hash=_sha256_bytes(cases_path.read_bytes()),
        corpus_hash=_corpus_hash(corpus),
        model_manifest=model_manifest,
    )
    artifact_payload = {**summary, "mode_details": full_results}
    artifact_hash = _stable_hash(artifact_payload)
    _write_artifact(settings.reranker_benchmark_path, artifact_payload, artifact_hash)
    report_progress(progress, total, total, "重排基准完成")
    return {
        **summary,
        "artifact": settings.reranker_benchmark_path.name,
        "artifact_hash": artifact_hash,
    }


def build_benchmark_summary(
    mode_runs: list[dict[str, Any]],
    cases: int,
    corpus_chunks: int,
    dataset_hash: str,
    corpus_hash: str,
    model_manifest: dict[str, Any],
) -> dict[str, Any]:
    completed = [run for run in mode_runs if run.get("status") == "COMPLETED"]
    by_mode = {str(run["mode"]): run for run in completed}
    baseline = by_mode.get("off")
    for run in completed:
        metrics = run["metrics"]
        baseline_metrics = baseline["metrics"] if baseline else metrics
        run["delta_vs_off"] = {
            "target_recall_at_1": round(
                metrics["target_recall_at_1"] - baseline_metrics["target_recall_at_1"], 4
            ),
            "mean_reciprocal_rank_at_5": round(
                metrics["mean_reciprocal_rank_at_5"]
                - baseline_metrics["mean_reciprocal_rank_at_5"], 4
            ),
            "average_case_latency_ms": round(
                run["average_case_latency_ms"] - baseline["average_case_latency_ms"], 3
            ) if baseline else 0.0,
        }
    recommended = _recommend(completed)
    return {
        "benchmark_version": "reranker-benchmark-v1",
        "created_at": datetime.now(timezone.utc).isoformat(),
        "cases": cases,
        "corpus_chunks": corpus_chunks,
        "dataset_hash": dataset_hash,
        "corpus_hash": corpus_hash,
        "completed_modes": len(completed),
        "model_manifest": model_manifest,
        "modes": completed,
        "recommended": recommended,
    }


def _recommend(mode_runs: list[dict[str, Any]]) -> dict[str, Any]:
    if not mode_runs:
        return {"mode": "none", "reason": "没有成功完成的模式", "metrics": {}}
    qualified = [run for run in mode_runs if _qualified(run["metrics"])]
    candidates = qualified or mode_runs
    best_target_recall = max(run["metrics"]["target_recall_at_1"] for run in candidates)
    target_contenders = [
        run for run in candidates
        if run["metrics"]["target_recall_at_1"] >= best_target_recall - 0.005
    ]
    best_mrr = max(run["metrics"]["mean_reciprocal_rank_at_5"] for run in target_contenders)
    quality_contenders = [
        run for run in target_contenders
        if run["metrics"]["mean_reciprocal_rank_at_5"] >= best_mrr - 0.005
    ]
    selected = min(quality_contenders, key=lambda run: run["average_case_latency_ms"])
    return {
        "mode": selected["mode"],
        "reason": "先保证目标 Recall@1 和 MRR@5，再在 0.5% 质量容差内选择延迟最低模式",
        "metrics": selected["metrics"],
        "average_case_latency_ms": selected["average_case_latency_ms"],
    }


def _qualified(metrics: dict[str, float]) -> bool:
    return (
        metrics["recall_at_5"] >= 0.95
        and metrics["target_recall_at_1"] >= 0.90
        and metrics["citation_source_url_rate"] >= 0.95
        and metrics["task_completion_rate"] >= 0.95
    )


def _model_manifest(path: Path, cancel_check: CancelCheck | None) -> dict[str, Any]:
    if not path.exists() or not path.is_dir():
        raise RuntimeError(f"local reranker model directory does not exist: {path}")
    weights = next(iter(sorted(path.glob("*.safetensors"))), None)
    if weights is None:
        weights = next(iter(sorted(path.glob("pytorch_model*.bin"))), None)
    if weights is None:
        raise RuntimeError(f"local reranker weights are missing: {path}")
    return {
        "model": path.name,
        "weights_file": weights.name,
        "weights_bytes": weights.stat().st_size,
        "weights_sha256": _sha256_file(weights, cancel_check),
        "config_sha256": _sha256_bytes((path / "config.json").read_bytes()),
    }


def _sha256_file(path: Path, cancel_check: CancelCheck | None) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(8 * 1024 * 1024):
            ensure_not_cancelled(cancel_check)
            digest.update(chunk)
    return digest.hexdigest()


def _corpus_hash(corpus: list[Any]) -> str:
    return _stable_hash([
        {
            "chunk_id": item.chunk_id,
            "document_id": item.document_id,
            "content_sha256": _sha256_bytes(item.content.encode("utf-8")),
        }
        for item in sorted(corpus, key=lambda value: value.chunk_id)
    ])


def _write_artifact(path: Path, result: dict[str, Any], result_hash: str) -> None:
    artifact = {
        "artifact_schema_version": 1,
        "artifact_type": "reranker-benchmark",
        "result_hash": result_hash,
        "result": result,
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(artifact, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


def _sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _stable_hash(value: Any) -> str:
    payload = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return _sha256_bytes(payload.encode("utf-8"))
