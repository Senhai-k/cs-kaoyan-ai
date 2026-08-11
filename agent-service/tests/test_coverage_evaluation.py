from pathlib import Path

from app.coverage_evaluation import run_coverage_evaluation
from app.evidence_verification import EvidenceVerificationGraph


def test_coverage_policy_evaluation_has_no_false_accepts_or_rejects():
    cases_path = Path(__file__).resolve().parents[1] / "evals" / "coverage_eval.json"

    result = run_coverage_evaluation(EvidenceVerificationGraph(), cases_path)

    assert result.cases == 8
    assert result.passed == 8
    assert result.accuracy == 1.0
    assert result.false_accept_rate == 0.0
    assert result.false_reject_rate == 0.0
