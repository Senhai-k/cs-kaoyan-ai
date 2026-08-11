from pathlib import Path

from app.config import Settings
from app.evidence_verification import EvidenceVerificationGraph
from app.knowledge_audit import KnowledgeAuditor


def test_knowledge_audit_uses_existing_documents_without_writes(tmp_path: Path):
    auditor = KnowledgeAuditor(Settings(data_dir=tmp_path), EvidenceVerificationGraph())
    documents = [
        {
            "id": 1,
            "title": "测试大学2026年复试录取细则",
            "documentType": "复试录取细则",
            "sourceUrl": "https://cs.example.edu.cn/2026/retest/page.htm",
            "schoolId": 7,
            "year": 2026,
            "rawText": "测试大学2026年硕士研究生复试录取细则，包含复试安排和录取办法。" * 10,
        },
        {
            "id": 2,
            "title": "范围错误资料",
            "documentType": "复试录取细则",
            "sourceUrl": "https://example.com/retest.html",
            "schoolId": 7,
            "year": 2026,
            "rawText": "另一所大学2025年复试安排。" * 10,
        },
    ]

    result = auditor.audit_documents(documents, [{"id": 7, "name": "测试大学"}])

    assert result.total_documents == 2
    assert result.verified_documents == 1
    assert result.rejected_documents == 1
    assert result.failure_counts["official_article"] == 1
    assert result.samples[0]["documentId"] == 2
