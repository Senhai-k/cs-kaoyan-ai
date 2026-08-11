import pytest

from app.quality_gate import enforce_quality_gate, evaluate_quality_gate, run_offline_gate


def test_planner_gate_passes_current_deterministic_baseline():
    result = {
        "cases": 10,
        "deterministic": {"exact_match_rate": 1.0, "unsafe_selection_rate": 0.0},
    }

    gate = evaluate_quality_gate("PLANNER_EVALUATION", result)

    assert gate["version"] == "quality-gate-v3"
    assert gate["status"] == "PASSED"
    assert gate["failed_checks"] == []


def test_required_llm_planner_gate_rejects_skipped_experiment():
    result = {
        "cases": 10,
        "llm_required": True,
        "llm_readiness": {"status": "INCOMPLETE"},
        "deterministic": {"exact_match_rate": 1.0, "unsafe_selection_rate": 0.0},
        "llm": {
            "status": "SKIPPED",
            "cases": 0,
            "completed_cases": 0,
            "failed_cases": 0,
            "exact_match_rate": 0.0,
            "target_recall": 0.0,
            "usage_coverage_rate": 0.0,
            "cost_status": "NOT_AVAILABLE",
            "unsafe_selection_rate": 0.0,
            "invalid_proposal_rate": 0.0,
        },
    }

    gate = evaluate_quality_gate("PLANNER_EVALUATION", result)

    assert gate["status"] == "FAILED"
    assert {item["metric"] for item in gate["failed_checks"]} == {
        "llm_readiness.status", "llm.status", "llm.cases", "llm.completed_cases",
        "llm.exact_match_rate", "llm.target_recall", "llm.usage_coverage_rate", "llm.cost_status",
    }


def test_required_llm_planner_gate_rejects_low_quality_or_guarded_output():
    result = {
        "cases": 10,
        "llm_required": True,
        "llm_readiness": {"status": "READY"},
        "deterministic": {"exact_match_rate": 1.0, "unsafe_selection_rate": 0.0},
        "llm": {
            "status": "COMPLETED", "cases": 10, "completed_cases": 10, "failed_cases": 0,
            "exact_match_rate": 0.8, "target_recall": 0.85, "usage_coverage_rate": 1.0,
            "cost_status": "ESTIMATED", "unsafe_selection_rate": 0.0,
            "invalid_proposal_rate": 0.1,
        },
    }

    gate = evaluate_quality_gate("PLANNER_EVALUATION", result)

    assert {item["metric"] for item in gate["failed_checks"]} == {
        "llm.exact_match_rate", "llm.target_recall", "llm.invalid_proposal_rate",
    }


def test_required_llm_planner_gate_accepts_explicit_unmetered_provider():
    result = {
        "cases": 10,
        "llm_required": True,
        "llm_readiness": {"status": "READY", "pricingMode": "UNMETERED"},
        "deterministic": {"exact_match_rate": 1.0, "unsafe_selection_rate": 0.0},
        "llm": {
            "status": "COMPLETED", "cases": 10, "completed_cases": 10, "failed_cases": 0,
            "exact_match_rate": 1.0, "target_recall": 1.0, "usage_coverage_rate": 1.0,
            "cost_status": "UNMETERED", "unsafe_selection_rate": 0.0,
            "invalid_proposal_rate": 0.0,
        },
    }

    gate = evaluate_quality_gate("PLANNER_EVALUATION", result)

    assert gate["status"] == "PASSED"


def test_gate_reports_every_failed_metric_and_enforcement_stops_ci():
    result = {
        "cases": 7,
        "accuracy": 0.8,
        "false_accept_rate": 0.1,
        "false_reject_rate": 0.0,
    }

    gate = evaluate_quality_gate("COVERAGE_EVALUATION", result)

    assert gate["status"] == "FAILED"
    assert {item["metric"] for item in gate["failed_checks"]} == {
        "cases", "accuracy", "false_accept_rate",
    }
    with pytest.raises(RuntimeError, match="quality gate failed"):
        enforce_quality_gate("COVERAGE_EVALUATION", result)


def test_offline_ci_gate_runs_current_planner_and_evidence_datasets():
    result = run_offline_gate()

    assert result["status"] == "PASSED"
    assert result["planner"]["status"] == "PASSED"
    assert result["coverage"]["status"] == "PASSED"


def test_rag_gate_rejects_citations_without_source_urls():
    result = {
        "cases": 30,
        "recall_at_5": 1.0,
        "target_recall_at_1": 1.0,
        "hit_rate_at_1": 1.0,
        "boundary_safety_rate": 1.0,
        "answer_support_rate": 1.0,
        "citation_groundedness": 1.0,
        "citation_source_url_rate": 0.9,
        "school_scope_accuracy": 1.0,
        "task_completion_rate": 1.0,
    }

    gate = evaluate_quality_gate("RAG_EVALUATION", result)

    assert gate["status"] == "FAILED"
    assert [item["metric"] for item in gate["failed_checks"]] == ["citation_source_url_rate"]
