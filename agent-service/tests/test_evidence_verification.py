from app.evidence_verification import EvidenceVerificationGraph


def candidate(raw_text: str, source_url: str = "https://cs.example.edu.cn/2026/rule/page.htm"):
    return {
        "target_id": 1,
        "title": "2026年复试录取细则",
        "document_type": "复试录取细则",
        "target_year": 2026,
        "source_url": source_url,
        "document": {
            "title": "测试大学2026年硕士研究生复试录取细则",
            "documentType": "复试录取细则",
            "year": 2026,
            "rawText": raw_text,
        },
    }


def test_verification_subgraph_accepts_scoped_official_evidence():
    verifier = EvidenceVerificationGraph()
    result = verifier.verify(
        candidate("测试大学2026年硕士研究生复试录取细则。复试采用面试和机考方式。" * 12),
        "测试大学",
    )

    assert result["status"] == "VERIFIED"
    assert result["quality_score"] >= 70
    assert len(result["verification_checks"]) == 6
    assert result["verification_trace"][-1].startswith("verifier:decision=accepted")


def test_verification_subgraph_rejects_wrong_school_and_year():
    verifier = EvidenceVerificationGraph()
    result = verifier.verify(
        candidate("另一所大学2025年硕士研究生复试录取细则。复试采用面试方式。" * 12),
        "测试大学",
    )

    assert result["status"] == "REJECTED"
    assert "school_scope" in result["reason"]
    assert "year_scope" in result["reason"]


def test_verification_subgraph_rejects_non_article_url():
    verifier = EvidenceVerificationGraph()
    result = verifier.verify(
        candidate("测试大学2026年硕士研究生复试录取细则。" * 20, "https://cs.example.edu.cn/main.htm"),
        "测试大学",
    )

    assert result["status"] == "REJECTED"
    assert "official_article" in result["reason"]


def test_verification_subgraph_normalizes_real_document_type_aliases():
    verifier = EvidenceVerificationGraph()
    item = candidate("测试大学2026年硕士研究生复试工作规定，包含录取办法。" * 12)
    item["document_type"] = "复试录取规定"
    item["document"]["documentType"] = "复试录取规定"

    result = verifier.verify(item, "测试大学")

    assert result["status"] == "VERIFIED"
    type_check = next(check for check in result["verification_checks"] if check["name"] == "document_type")
    assert type_check["passed"] is True
