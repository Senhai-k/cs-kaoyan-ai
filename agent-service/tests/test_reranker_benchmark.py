from app.quality_gate import evaluate_quality_gate
from app.reranker_benchmark import build_benchmark_summary


def _run(mode: str, target_recall: float, mrr: float, latency: float):
    return {
        "mode": mode,
        "status": "COMPLETED",
        "duration_ms": latency * 30,
        "average_case_latency_ms": latency,
        "metrics": {
            "recall_at_5": 1.0,
            "target_recall_at_1": target_recall,
            "hit_rate_at_1": 1.0,
            "mean_reciprocal_rank_at_5": mrr,
            "citation_source_url_rate": 1.0,
            "task_completion_rate": 1.0,
        },
        "failed_case_ids": [],
        "category_scores": {"exact_major": 1.0},
    }


def test_benchmark_recommends_faster_mode_within_quality_tolerance():
    summary = build_benchmark_summary(
        mode_runs=[
            _run("off", 0.90, 0.91, 30.0),
            _run("feature", 0.94, 0.981, 35.0),
            _run("cross-encoder", 0.94, 0.984, 420.0),
        ],
        cases=30,
        corpus_chunks=142,
        dataset_hash="dataset",
        corpus_hash="corpus",
        model_manifest={"model": "bge-reranker-base"},
    )

    assert summary["completed_modes"] == 3
    assert summary["recommended"]["mode"] == "feature"
    assert summary["modes"][1]["delta_vs_off"]["mean_reciprocal_rank_at_5"] == 0.071
    assert evaluate_quality_gate("RERANKER_BENCHMARK", summary)["status"] == "PASSED"


def test_benchmark_gate_rejects_incomplete_or_low_quality_run():
    summary = build_benchmark_summary(
        mode_runs=[_run("off", 0.6, 0.7, 30.0)],
        cases=30,
        corpus_chunks=142,
        dataset_hash="dataset",
        corpus_hash="corpus",
        model_manifest={"model": "bge-reranker-base"},
    )

    gate = evaluate_quality_gate("RERANKER_BENCHMARK", summary)

    assert gate["status"] == "FAILED"
    assert "completed_modes" in {item["metric"] for item in gate["failed_checks"]}
    assert "recommended.metrics.target_recall_at_1" in {
        item["metric"] for item in gate["failed_checks"]
    }
