from app.diagnostics import build_diagnostics


def test_diagnostics_aggregate_and_filter_operational_failures():
    jobs = [
        {
            "id": "failed-1", "operation_type": "RAG_EVALUATION", "status": "FAILED",
            "error": "operation timed out", "updated_at": "2026-07-17T10:00:00Z",
            "trace_id": "trace-1", "result": None,
        },
        {
            "id": "planner-1", "operation_type": "PLANNER_EVALUATION", "status": "COMPLETED",
            "updated_at": "2026-07-17T11:00:00Z", "trace_id": "trace-2",
            "result": {"deterministic": {"details": [{
                "id": "duplicate-url", "eligibility_rejections": [{
                    "target_id": 4, "reason": "duplicate_source_url", "kept_target_id": 5,
                }],
            }]}, "quality_gate": {"status": "PASSED"}},
        },
    ]
    workflows = [{
        "thread_id": "workflow-1", "school_name": "测试大学", "status": "REJECTED",
        "phase": "REJECTED", "rejected_count": 1, "updated_at": "2026-07-17T09:00:00Z",
        "trace": ["human:rejected"], "error": "",
    }]

    result = build_diagnostics(jobs, workflows)
    planner = build_diagnostics(jobs, workflows, query="duplicate_source_url")
    errors = build_diagnostics(jobs, workflows, severity="ERROR")

    assert result["total"] == 3
    assert result["counts"] == {"PLANNER_FILTER": 1, "OPERATION_FAILURE": 1, "WORKFLOW": 1}
    assert [item["category"] for item in planner["items"]] == ["PLANNER_FILTER"]
    assert [item["id"] for item in errors["items"]] == ["operation:failed-1"]


def test_quality_gate_and_knowledge_audit_failures_are_actionable():
    jobs = [{
        "id": "audit-1", "operation_type": "KNOWLEDGE_AUDIT", "status": "COMPLETED",
        "updated_at": "2026-07-17T12:00:00Z", "trace_id": "trace-3",
        "result": {
            "quality_gate": {"status": "FAILED", "failed_checks": [{
                "metric": "pass_rate", "label": "知识库通过率", "actual": 0.8,
                "comparator": ">=", "threshold": 0.95,
            }]},
            "samples": [{
                "documentId": 12, "title": "官网入口", "schoolName": "测试大学",
                "status": "REJECTED", "reason": "not an exact article", "sourceUrl": "https://test.edu.cn/",
            }],
        },
    }]

    result = build_diagnostics(jobs, [], category="KNOWLEDGE_AUDIT")

    assert result["total"] == 1
    assert result["items"][0]["source_url"] == "https://test.edu.cn/"
