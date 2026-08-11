from app.monitoring import render_prometheus_metrics


def test_prometheus_metrics_include_agent_quality_and_index_values():
    content = render_prometheus_metrics({
        "total_tasks": 10,
        "completed_tasks": 9,
        "waiting_tasks": 0,
        "failed_tasks": 1,
        "tool_calls": 20,
        "successful_tool_calls": 19,
        "average_latency_ms": 125.5,
        "task_completion_rate": 0.9,
        "tool_success_rate": 0.95,
    }, indexed_chunks=142)

    assert "cs_kaoyan_agent_task_completion_ratio 0.9" in content
    assert "cs_kaoyan_agent_tool_success_ratio 0.95" in content
    assert "cs_kaoyan_agent_indexed_chunks 142" in content
    assert content.endswith("\n")
