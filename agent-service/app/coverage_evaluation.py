import json
from pathlib import Path
from typing import Any

from .evidence_verification import EvidenceVerificationGraph
from .models import CoverageEvaluationResult
from .operation_control import CancelCheck, ProgressCallback, ensure_not_cancelled, report_progress


def run_coverage_evaluation(
    verifier: EvidenceVerificationGraph,
    cases_path: Path,
    progress: ProgressCallback | None = None,
    cancel_check: CancelCheck | None = None,
) -> CoverageEvaluationResult:
    cases = json.loads(cases_path.read_text(encoding="utf-8"))
    details: list[dict[str, Any]] = []
    passed = 0
    false_accepts = 0
    false_rejects = 0
    expected_accepts = 0
    expected_rejects = 0
    total = len(cases)
    report_progress(progress, 0, total, "准备证据策略评估")
    for index, case in enumerate(cases, 1):
        ensure_not_cancelled(cancel_check)
        expected = case["expectedStatus"]
        expected_accepts += int(expected == "VERIFIED")
        expected_rejects += int(expected == "REJECTED")
        raw_text = str(case["rawText"]) * int(case.get("repeat", 1))
        candidate = {
            "target_id": 1,
            "title": case["title"],
            "document_type": case["documentType"],
            "target_year": case["year"],
            "source_url": case["sourceUrl"],
            "document": {
                "title": case["title"],
                "documentType": case["documentType"],
                "year": case["year"],
                "rawText": raw_text,
            },
        }
        result = verifier.verify(candidate, case["schoolName"])
        actual = result["status"]
        matched = actual == expected
        passed += int(matched)
        false_accepts += int(expected == "REJECTED" and actual == "VERIFIED")
        false_rejects += int(expected == "VERIFIED" and actual == "REJECTED")
        details.append({
            "id": case["id"],
            "expectedStatus": expected,
            "actualStatus": actual,
            "qualityScore": result["quality_score"],
            "matched": matched,
            "reason": result.get("reason", ""),
        })
        report_progress(progress, index, total, str(case["id"]))
    return CoverageEvaluationResult(
        cases=total,
        passed=passed,
        accuracy=round(passed / total, 4) if total else 0.0,
        false_accept_rate=round(false_accepts / expected_rejects, 4) if expected_rejects else 0.0,
        false_reject_rate=round(false_rejects / expected_accepts, 4) if expected_accepts else 0.0,
        details=details,
    )
