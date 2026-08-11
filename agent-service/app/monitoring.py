from math import isfinite
from typing import Any


METRICS = (
    ("cs_kaoyan_agent_tasks_total", "Number of distinct Agent tasks", "total_tasks"),
    ("cs_kaoyan_agent_tasks_completed", "Number of completed Agent tasks", "completed_tasks"),
    ("cs_kaoyan_agent_tasks_waiting", "Number of Agent tasks waiting for human review", "waiting_tasks"),
    ("cs_kaoyan_agent_tasks_failed", "Number of failed Agent tasks", "failed_tasks"),
    ("cs_kaoyan_agent_tool_calls_total", "Number of Agent tool calls", "tool_calls"),
    ("cs_kaoyan_agent_tool_calls_successful", "Number of successful Agent tool calls", "successful_tool_calls"),
    ("cs_kaoyan_agent_average_latency_ms", "Average Agent task latency in milliseconds", "average_latency_ms"),
    ("cs_kaoyan_agent_task_completion_ratio", "Ratio of completed Agent tasks", "task_completion_rate"),
    ("cs_kaoyan_agent_tool_success_ratio", "Ratio of successful Agent tool calls", "tool_success_rate"),
)


def render_prometheus_metrics(summary: Any, indexed_chunks: int) -> str:
    values = summary.model_dump() if hasattr(summary, "model_dump") else dict(summary)
    lines: list[str] = []
    for name, help_text, field in METRICS:
        value = _number(values.get(field, 0))
        lines.extend((f"# HELP {name} {help_text}", f"# TYPE {name} gauge", f"{name} {value}"))
    lines.extend((
        "# HELP cs_kaoyan_agent_indexed_chunks Number of chunks in the private knowledge index",
        "# TYPE cs_kaoyan_agent_indexed_chunks gauge",
        f"cs_kaoyan_agent_indexed_chunks {_number(indexed_chunks)}",
    ))
    return "\n".join(lines) + "\n"


def _number(value: Any) -> str:
    number = float(value or 0)
    if not isfinite(number):
        number = 0.0
    return str(int(number)) if number.is_integer() else format(number, ".10g")
