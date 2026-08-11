from __future__ import annotations

import json
import sqlite3
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


class WorkflowRunStore:
    def __init__(self, path: Path):
        self._connection = sqlite3.connect(path, check_same_thread=False)
        self._connection.row_factory = sqlite3.Row
        self._lock = threading.Lock()
        with self._connection:
            self._connection.execute("""
                CREATE TABLE IF NOT EXISTS workflow_run (
                  thread_id TEXT PRIMARY KEY,
                  workflow_type TEXT NOT NULL,
                  school_name TEXT NOT NULL,
                  status TEXT NOT NULL,
                  phase TEXT NOT NULL,
                  planner_mode TEXT NOT NULL DEFAULT '',
                  plan_count INTEGER NOT NULL DEFAULT 0,
                  candidate_count INTEGER NOT NULL DEFAULT 0,
                  rejected_count INTEGER NOT NULL DEFAULT 0,
                  published_count INTEGER NOT NULL DEFAULT 0,
                  average_quality_score REAL NOT NULL DEFAULT 0,
                  started_at TEXT NOT NULL,
                  updated_at TEXT NOT NULL,
                  completed_at TEXT,
                  error TEXT NOT NULL DEFAULT '',
                  trace_json TEXT NOT NULL DEFAULT '[]'
                )
            """)

    def close(self) -> None:
        self._connection.close()

    def start(self, thread_id: str, school_name: str) -> None:
        now = _now()
        with self._lock, self._connection:
            self._connection.execute("""
                INSERT INTO workflow_run (
                  thread_id, workflow_type, school_name, status, phase, started_at, updated_at
                ) VALUES (?, 'COVERAGE', ?, 'RUNNING', 'INSPECTING', ?, ?)
                ON CONFLICT(thread_id) DO UPDATE SET
                  school_name = excluded.school_name,
                  status = 'RUNNING', phase = 'INSPECTING', updated_at = excluded.updated_at, error = ''
                """, (thread_id, school_name, now, now))

    def update(self, thread_id: str, state: dict[str, Any], error: str = "") -> None:
        status = _status_from_state(state, error)
        candidates = state.get("candidates", [])
        quality_scores = [float(item.get("quality_score", 0)) for item in candidates]
        average_quality = sum(quality_scores) / len(quality_scores) if quality_scores else 0.0
        now = _now()
        completed_at = now if status in {"COMPLETED", "REJECTED", "FAILED"} else None
        with self._lock, self._connection:
            self._connection.execute("""
                UPDATE workflow_run SET
                  status = ?, phase = ?, planner_mode = ?, plan_count = ?, candidate_count = ?,
                  rejected_count = ?, published_count = ?, average_quality_score = ?, updated_at = ?,
                  completed_at = ?, error = ?, trace_json = ?
                WHERE thread_id = ?
                """, (
                    status,
                    str(state.get("phase") or ""),
                    str(state.get("planner_mode") or ""),
                    len(state.get("plan", [])),
                    len(candidates),
                    len(state.get("rejected_candidates", [])),
                    len(state.get("published", [])),
                    round(average_quality, 2),
                    now,
                    completed_at,
                    error,
                    json.dumps(state.get("trace", []), ensure_ascii=False),
                    thread_id,
                ))

    def list(self, limit: int = 20) -> list[dict[str, Any]]:
        safe_limit = max(1, min(limit, 100))
        with self._lock:
            rows = self._connection.execute("""
                SELECT * FROM workflow_run ORDER BY started_at DESC LIMIT ?
                """, (safe_limit,)).fetchall()
        return [_row_to_dict(row) for row in rows]

    def metrics(self) -> dict[str, Any]:
        with self._lock:
            row = self._connection.execute("""
                SELECT COUNT(*) AS total,
                  SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed,
                  SUM(CASE WHEN status = 'WAITING_HUMAN' THEN 1 ELSE 0 END) AS waiting,
                  SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected,
                  SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed,
                  SUM(published_count) AS published,
                  AVG(CASE WHEN candidate_count > 0 THEN average_quality_score END) AS average_quality
                FROM workflow_run
                """).fetchone()
        total = int(row["total"] or 0)
        completed = int(row["completed"] or 0)
        terminal = completed + int(row["rejected"] or 0) + int(row["failed"] or 0)
        return {
            "total_runs": total,
            "completed_runs": completed,
            "waiting_runs": int(row["waiting"] or 0),
            "rejected_runs": int(row["rejected"] or 0),
            "failed_runs": int(row["failed"] or 0),
            "published_documents": int(row["published"] or 0),
            "average_quality_score": round(float(row["average_quality"] or 0), 2),
            "completion_rate": round(completed / terminal, 4) if terminal else 0.0,
        }


def _status_from_state(state: dict[str, Any], error: str) -> str:
    if error:
        return "FAILED"
    if state.get("__interrupt__"):
        return "WAITING_HUMAN"
    if state.get("phase") == "REJECTED" or state.get("route") == "rejected":
        return "REJECTED"
    if state.get("phase") == "COMPLETED":
        return "COMPLETED"
    return "RUNNING"


def _row_to_dict(row: sqlite3.Row) -> dict[str, Any]:
    return {
        "thread_id": row["thread_id"],
        "workflow_type": row["workflow_type"],
        "school_name": row["school_name"],
        "status": row["status"],
        "phase": row["phase"],
        "planner_mode": row["planner_mode"],
        "plan_count": row["plan_count"],
        "candidate_count": row["candidate_count"],
        "rejected_count": row["rejected_count"],
        "published_count": row["published_count"],
        "average_quality_score": row["average_quality_score"],
        "started_at": row["started_at"],
        "updated_at": row["updated_at"],
        "completed_at": row["completed_at"],
        "error": row["error"],
        "trace": json.loads(row["trace_json"] or "[]"),
    }


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()
