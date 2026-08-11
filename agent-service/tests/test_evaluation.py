import json
from pathlib import Path

from app.evaluation import run_evaluation
from app.models import RetrievedEvidence


class FakeRetriever:
    def __init__(self):
        self.evidence = RetrievedEvidence(
            chunk_id=1,
            document_id=10,
            title="2026年招生目录",
            content="测试大学085404初试科目为408",
            school_name="测试大学",
            year=2026,
            score=0.9,
        )

    def search(self, question, school, limit, apply_reranker=True):
        return [self.evidence]


class MultiTargetRetriever:
    def __init__(self, same_school: bool = False):
        self.same_school = same_school

    def search(self, question, school, limit, apply_reranker=True):
        first = RetrievedEvidence(
            chunk_id=1, document_id=10, title="甲校目录", content="甲校目标内容",
            school_name="甲校", score=0.9, vector_score=0.8, lexical_score=3.0, rerank_score=0.7,
        )
        second_school = "甲校" if self.same_school else "乙校"
        second_content = "甲校第二目标内容" if self.same_school else "乙校目标内容"
        second = RetrievedEvidence(
            chunk_id=2, document_id=20, title=f"{second_school}目录", content=second_content,
            school_name=second_school, score=0.8, vector_score=0.7, lexical_score=2.0, rerank_score=0.6,
        )
        if self.same_school:
            return [first, second]
        return [first] if school == "甲校" else [second]


class FakeGraph:
    def __init__(self, invalid_citation: bool = False, missing_source_url: bool = False):
        self.invalid_citation = invalid_citation
        self.missing_source_url = missing_source_url

    def invoke(self, state, config):
        if "未知" in state["question"]:
            return {
                "answer": "不执行跨校推断",
                "sources": [],
                "route": "completed",
                "trace": ["guard:unknown_school"],
            }
        citation = "[2]" if self.invalid_citation else "[1]"
        return {
            "answer": f"测试大学085404初试科目为408 {citation}",
            "sources": [
                "[1] 测试大学 / 2026 / 招生目录"
                + ("" if self.missing_source_url else " / https://yz.test.edu.cn/catalog")
            ],
            "route": "completed",
            "trace": ["tool:hybrid_retrieve", "generate:grounded-extractive"],
        }


class FakeAgent:
    def __init__(self, invalid_citation: bool = False, missing_source_url: bool = False):
        self.graph = FakeGraph(invalid_citation, missing_source_url)


def _write_cases(path: Path):
    path.write_text(json.dumps([
        {
            "id": "retrieval",
            "category": "exact_major",
            "question": "测试大学085404考什么？",
            "school": "测试大学",
            "expectedText": "408",
            "expectedTrace": ["tool:hybrid_retrieve"],
        },
        {
            "id": "guard",
            "type": "guard",
            "category": "unknown_school_guard",
            "question": "未知大学考什么？",
            "expectedGuard": True,
            "expectedAnswerText": "不执行跨校推断",
        },
    ], ensure_ascii=False), encoding="utf-8")


def test_evaluation_reports_grounding_scope_and_category_metrics(tmp_path: Path):
    cases_path = tmp_path / "cases.json"
    _write_cases(cases_path)

    result = run_evaluation(FakeAgent(), FakeRetriever(), cases_path)

    assert result.recall_at_1 == 1.0
    assert result.recall_at_5 == 1.0
    assert result.answer_support_rate == 1.0
    assert result.citation_groundedness == 1.0
    assert result.citation_source_url_rate == 1.0
    assert result.school_scope_accuracy == 1.0
    assert result.task_completion_rate == 1.0
    assert result.category_scores == {"exact_major": 1.0, "unknown_school_guard": 1.0}
    assert result.failed_case_ids == []


def test_evaluation_marks_out_of_range_citation_as_failed(tmp_path: Path):
    cases_path = tmp_path / "cases.json"
    _write_cases(cases_path)

    result = run_evaluation(FakeAgent(invalid_citation=True), FakeRetriever(), cases_path)

    assert result.citation_validity == 1.0
    assert result.citation_groundedness == 0.5
    assert result.citation_source_url_rate == 0.5
    assert result.task_completion_rate == 0.5
    assert result.failed_case_ids == ["retrieval"]


def test_evaluation_rejects_numbered_citation_without_source_url(tmp_path: Path):
    cases_path = tmp_path / "cases.json"
    _write_cases(cases_path)

    result = run_evaluation(FakeAgent(missing_source_url=True), FakeRetriever(), cases_path)

    assert result.citation_groundedness == 1.0
    assert result.citation_source_url_rate == 0.5
    assert result.task_completion_rate == 0.5
    assert result.details[0]["citationSourceUrlValid"] is False
    assert result.failed_case_ids == ["retrieval"]


def test_evaluation_uses_school_local_ranks_for_multi_school_targets(tmp_path: Path):
    cases_path = tmp_path / "cases.json"
    cases_path.write_text(json.dumps([{
        "id": "multi-school",
        "question": "比较甲校和乙校",
        "schools": ["甲校", "乙校"],
        "expectedTargets": [
            {"text": "甲校目标内容", "school": "甲校"},
            {"text": "乙校目标内容", "school": "乙校"},
        ],
    }], ensure_ascii=False), encoding="utf-8")

    result = run_evaluation(FakeAgent(), MultiTargetRetriever(), cases_path)

    assert result.recall_at_1 == 1.0
    assert result.target_recall_at_1 == 1.0
    assert result.hit_rate_at_1 == 1.0
    assert result.details[0]["relevantRanks"] == [1, 2]
    assert result.details[0]["localRelevantRanks"] == [1, 1]
    assert result.details[0]["expectedTargets"][1]["chunkId"] == 2
    assert result.details[0]["rankingDiagnostics"][1]["localRank"] == 1


def test_evaluation_separates_case_hit_rate_from_target_recall(tmp_path: Path):
    cases_path = tmp_path / "cases.json"
    cases_path.write_text(json.dumps([{
        "id": "same-school-contrast",
        "question": "对比甲校两个目标",
        "school": "甲校",
        "expectedTexts": ["甲校目标内容", "甲校第二目标内容"],
    }], ensure_ascii=False), encoding="utf-8")

    result = run_evaluation(FakeAgent(), MultiTargetRetriever(same_school=True), cases_path)

    assert result.recall_at_1 == 0.5
    assert result.target_recall_at_1 == 0.5
    assert result.hit_rate_at_1 == 1.0
    assert result.details[0]["localRelevantRanks"] == [1, 2]
