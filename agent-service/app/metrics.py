import sqlite3
import threading
import time
from pathlib import Path

from .models import AgentMetrics


class MetricsStore:
    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()
        self._connection = sqlite3.connect(path, check_same_thread=False)
        self._connection.execute("""
            CREATE TABLE IF NOT EXISTS agent_task_metric (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              thread_id TEXT NOT NULL,
              status TEXT NOT NULL,
              latency_ms REAL NOT NULL,
              tool_calls INTEGER NOT NULL,
              successful_tool_calls INTEGER NOT NULL,
              created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """)
        self._connection.commit()

    def record(self, thread_id: str, status: str, started_at: float,
               tool_calls: int, successful_tool_calls: int) -> None:
        latency_ms = (time.perf_counter() - started_at) * 1000
        with self._lock:
            self._connection.execute("""
                INSERT INTO agent_task_metric (
                  thread_id, status, latency_ms, tool_calls, successful_tool_calls
                ) VALUES (?, ?, ?, ?, ?)
            """, (thread_id, status, latency_ms, tool_calls, successful_tool_calls))
            self._connection.commit()

    def summary(self) -> AgentMetrics:
        with self._lock:
            row = self._connection.execute("""
                WITH ranked AS (
                  SELECT *, ROW_NUMBER() OVER (PARTITION BY thread_id ORDER BY id DESC) AS sequence
                  FROM agent_task_metric
                ), tasks AS (
                  SELECT thread_id,
                    MAX(CASE WHEN sequence = 1 THEN status END) AS status,
                    SUM(latency_ms) AS latency_ms,
                    SUM(tool_calls) AS tool_calls,
                    SUM(successful_tool_calls) AS successful_tool_calls
                  FROM ranked
                  GROUP BY thread_id
                )
                SELECT COUNT(*) AS total,
                  SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed,
                  SUM(CASE WHEN status = 'WAITING_HUMAN' THEN 1 ELSE 0 END) AS waiting,
                  SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed,
                  COALESCE(SUM(tool_calls), 0) AS tool_calls,
                  COALESCE(SUM(successful_tool_calls), 0) AS successful_tool_calls,
                  COALESCE(AVG(latency_ms), 0) AS average_latency_ms
                FROM tasks
            """).fetchone()
        total, completed, waiting, failed, tool_calls, successful_tool_calls, latency = row
        return AgentMetrics(
            total_tasks=total or 0,
            completed_tasks=completed or 0,
            waiting_tasks=waiting or 0,
            failed_tasks=failed or 0,
            tool_calls=tool_calls or 0,
            successful_tool_calls=successful_tool_calls or 0,
            average_latency_ms=round(float(latency or 0), 2),
            task_completion_rate=round((completed or 0) / total, 4) if total else 0.0,
            tool_success_rate=round((successful_tool_calls or 0) / tool_calls, 4) if tool_calls else 0.0,
        )
