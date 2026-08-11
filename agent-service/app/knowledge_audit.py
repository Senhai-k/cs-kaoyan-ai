from __future__ import annotations

from collections import Counter
from typing import Any

import httpx

from .config import Settings
from .evidence_verification import EvidenceVerificationGraph
from .models import KnowledgeAuditResult
from .operation_control import CancelCheck, ProgressCallback, ensure_not_cancelled, report_progress


class KnowledgeAuditor:
    def __init__(self, settings: Settings, verifier: EvidenceVerificationGraph):
        self.settings = settings
        self.verifier = verifier

    def run(
        self,
        progress: ProgressCallback | None = None,
        cancel_check: CancelCheck | None = None,
    ) -> KnowledgeAuditResult:
        with httpx.Client(
            base_url=self.settings.spring_base_url,
            timeout=self.settings.request_timeout_seconds,
        ) as client:
            documents = client.get(
                "/api/source-documents", params={"auditStatus": "PUBLISHED"}
            ).raise_for_status().json().get("data", [])
            schools = client.get("/api/schools").raise_for_status().json().get("data", [])
        return self.audit_documents(documents, schools, progress, cancel_check)

    def audit_documents(
        self,
        documents: list[dict[str, Any]],
        schools: list[dict[str, Any]],
        progress: ProgressCallback | None = None,
        cancel_check: CancelCheck | None = None,
    ) -> KnowledgeAuditResult:
        school_names = {int(item["id"]): str(item["name"]) for item in schools}
        results = []
        failure_counts: Counter[str] = Counter()
        total = len(documents)
        report_progress(progress, 0, total, "准备私域资料审计")
        for index, document in enumerate(documents, 1):
            ensure_not_cancelled(cancel_check)
            school_id = document.get("schoolId")
            school_name = school_names.get(int(school_id)) if school_id is not None else ""
            candidate = {
                "target_id": document.get("id"),
                "title": document.get("title") or "",
                "document_type": document.get("documentType") or "资料文档",
                "target_year": document.get("year") or 0,
                "source_url": document.get("sourceUrl") or "",
                "document": {
                    "title": document.get("title") or "",
                    "documentType": document.get("documentType") or "资料文档",
                    "year": document.get("year"),
                    "rawText": document.get("rawText") or "",
                },
            }
            result = self.verifier.verify(candidate, school_name)
            for check in result.get("verification_checks", []):
                if not check["passed"]:
                    failure_counts[check["name"]] += 1
            results.append({
                "documentId": document.get("id"),
                "title": document.get("title") or "",
                "schoolName": school_name,
                "status": result["status"],
                "qualityScore": result["quality_score"],
                "reason": result.get("reason", ""),
                "sourceUrl": document.get("sourceUrl") or "",
            })
            report_progress(progress, index, total, str(document.get("title") or document.get("id")))
        verified = sum(item["status"] == "VERIFIED" for item in results)
        total = len(results)
        average_score = sum(item["qualityScore"] for item in results) / total if total else 0.0
        samples = sorted(
            (item for item in results if item["status"] == "REJECTED"),
            key=lambda item: (item["qualityScore"], item["documentId"] or 0),
        )[:10]
        return KnowledgeAuditResult(
            total_documents=total,
            verified_documents=verified,
            rejected_documents=total - verified,
            pass_rate=round(verified / total, 4) if total else 0.0,
            average_quality_score=round(average_score, 2),
            failure_counts=dict(failure_counts.most_common()),
            samples=samples,
        )
