from pathlib import Path
import threading

import app.coverage_workflow as workflow_module
from app.config import Settings
from app.coverage_workflow import CoveragePlanner, CoverageWorkflow, _is_exact_official_url


class FakeResponse:
    def __init__(self, payload):
        self.payload = payload

    def raise_for_status(self):
        return self

    def json(self):
        return self.payload


class FakeSpringClient:
    published = []

    def __init__(self, *args, **kwargs):
        pass

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def get(self, path, params=None):
        if path == "/api/data-coverage/tasks":
            return FakeResponse({"data": [{
                "schoolId": 3,
                "schoolName": "测试大学",
                "coveragePercent": 50,
                "missingDimensions": ["复试线", "复试规则"],
                "targets": [
                    {"id": 31, "title": "复试线", "documentType": "复试分数线", "targetYear": 2026,
                     "sourceUrl": "https://cs.test.edu.cn/2026/score/page.htm", "status": "PENDING"},
                    {"id": 32, "title": "复试规则", "documentType": "复试录取细则", "targetYear": 2026,
                     "sourceUrl": "https://cs.test.edu.cn/2026/rule/page.htm", "status": "PENDING"},
                ],
            }]})
        if path == "/api/source-documents":
            return FakeResponse({"data": []})
        raise AssertionError(f"unexpected GET {path}")

    def post(self, path, json=None, headers=None):
        assert path == "/api/internal/agent/evidence"
        assert headers == {"X-Agent-Service-Token": "test-token"}
        self.published.append(json)
        return FakeResponse({"data": {
            "documentId": 100 + len(self.published),
            "chunkCount": 2,
            "created": True,
            "targetId": json["targetId"],
            "targetStatus": "VERIFIED",
        }})


def build_workflow(tmp_path: Path, monkeypatch):
    FakeSpringClient.published = []
    monkeypatch.setattr(workflow_module.httpx, "Client", FakeSpringClient)
    calls = {"index": 0, "evaluation": 0}

    def sync_index():
        calls["index"] += 1
        return {"documents": 2, "chunks": 4, "schools": 1, "collection": "test", "embedding_model": "test"}

    def evaluate():
        calls["evaluation"] += 1
        return {"cases": 2, "recall_at_5": 1.0}

    workflow = CoverageWorkflow(
        Settings(data_dir=tmp_path, internal_token="test-token"), sync_index, evaluate
    )

    def collect(step, school_id, existing_urls):
        raw_text = f"测试大学2026年{step['document_type']}官方公告。" + "招生复试录取要求和考试安排。" * 30
        return {
            **step,
            "status": "VERIFIED",
            "content_length": 500,
            "document": {
                "title": step["title"], "documentType": step["document_type"],
                "sourceUrl": step["source_url"], "schoolId": school_id,
                "year": step["target_year"], "auditStatus": "PUBLISHED",
                "sourceReliability": "OFFICIAL", "rawText": raw_text,
            },
        }

    monkeypatch.setattr(workflow, "_collect", collect)
    return workflow, calls


def test_planner_only_selects_exact_official_articles(tmp_path: Path):
    planner = CoveragePlanner(Settings(data_dir=tmp_path))
    task = {"targets": [
        {"id": 1, "title": "文章", "documentType": "复试线", "targetYear": 2026,
         "sourceUrl": "https://cs.example.edu.cn/2026/score/page.htm"},
        {"id": 2, "title": "首页", "documentType": "招生目录", "targetYear": 2026,
         "sourceUrl": "https://cs.example.edu.cn/main.htm"},
        {"id": 3, "title": "已核验", "documentType": "复试线", "targetYear": 2026,
         "sourceUrl": "https://cs.example.edu.cn/2026/verified/page.htm", "status": "VERIFIED"},
    ]}

    result = planner.plan(task, 3)

    assert [step.target_id for step in result.steps] == [1]
    assert _is_exact_official_url("http://127.0.0.1/article.htm") is False
    assert _is_exact_official_url("https://example.com/article.htm") is False


def test_parallel_collection_interrupts_and_rejection_does_not_publish(tmp_path: Path, monkeypatch):
    workflow, calls = build_workflow(tmp_path, monkeypatch)
    try:
        interrupted = workflow.start("测试大学", 3, "reject-thread")
        assert interrupted.get("__interrupt__")
        assert len(interrupted["candidates"]) == 2
        assert all(item["quality_score"] >= 70 for item in interrupted["candidates"])
        assert all(len(item["verification_checks"]) == 6 for item in interrupted["candidates"])
        assert sum(item.startswith("collect:") for item in interrupted["trace"]) == 2

        rejected = workflow.resume("reject-thread", False, "证据不足")
        assert rejected["phase"] == "REJECTED"
        assert FakeSpringClient.published == []
        assert calls == {"index": 0, "evaluation": 0}
    finally:
        workflow.close()


def test_approval_publishes_then_indexes_and_evaluates(tmp_path: Path, monkeypatch):
    workflow, calls = build_workflow(tmp_path, monkeypatch)
    try:
        workflow.start("测试大学", 3, "approve-thread")
        completed = workflow.resume("approve-thread", True, "同意发布")

        assert completed["phase"] == "COMPLETED"
        assert len(completed["published"]) == 2
        assert len(FakeSpringClient.published) == 2
        assert calls == {"index": 1, "evaluation": 1}
        assert "human:approved" in completed["trace"]
        assert any(item.startswith("evaluate:cases=2") for item in completed["trace"])
        assert workflow.metrics()["completed_runs"] == 1
        assert workflow.runs(1)[0]["status"] == "COMPLETED"
    finally:
        workflow.close()


def test_send_collection_workers_execute_concurrently(tmp_path: Path, monkeypatch):
    workflow, _ = build_workflow(tmp_path, monkeypatch)
    original_collect = workflow._collect
    barrier = threading.Barrier(2)
    lock = threading.Lock()
    active = 0
    peak = 0

    def concurrent_collect(step, school_id, existing_urls):
        nonlocal active, peak
        with lock:
            active += 1
            peak = max(peak, active)
        try:
            barrier.wait(timeout=2)
            return original_collect(step, school_id, existing_urls)
        finally:
            with lock:
                active -= 1

    monkeypatch.setattr(workflow, "_collect", concurrent_collect)
    try:
        interrupted = workflow.start("测试大学", 3, "parallel-stress-thread")

        assert interrupted.get("__interrupt__")
        assert len(interrupted["candidates"]) == 2
        assert peak == 2
    finally:
        workflow.close()
