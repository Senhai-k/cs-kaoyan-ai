from __future__ import annotations

import json
import sqlite3
import threading
import time
import uuid
from concurrent.futures import Future, ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

from .operation_control import CancelCheck, OperationCancelled, ProgressCallback
from .telemetry import TelemetryStore, current_trace_context

OperationRunner = Callable[[ProgressCallback, CancelCheck], Any]
TERMINAL_STATUSES = {"COMPLETED", "FAILED", "CANCELLED"}


class OperationJobStore:
    def __init__(self, path: Path):
        self._connection = sqlite3.connect(path, check_same_thread=False)
        self._connection.row_factory = sqlite3.Row
        self._lock = threading.Lock()
        with self._connection:
            self._connection.executescript("""
                CREATE TABLE IF NOT EXISTS operation_job (
                  id TEXT PRIMARY KEY,
                  operation_type TEXT NOT NULL,
                  status TEXT NOT NULL,
                  progress_current INTEGER NOT NULL DEFAULT 0,
                  progress_total INTEGER NOT NULL DEFAULT 0,
                  progress_message TEXT NOT NULL DEFAULT '',
                  attempt INTEGER NOT NULL DEFAULT 1,
                  parent_job_id TEXT,
                  result_json TEXT,
                  error TEXT NOT NULL DEFAULT '',
                  cancel_requested INTEGER NOT NULL DEFAULT 0,
                  created_at TEXT NOT NULL,
                  started_at TEXT,
                  updated_at TEXT NOT NULL,
                  completed_at TEXT
                  ,correlation_id TEXT NOT NULL DEFAULT ''
                  ,trace_id TEXT NOT NULL DEFAULT ''
                  ,parent_span_id TEXT
                );
                CREATE TABLE IF NOT EXISTS operation_job_event (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  job_id TEXT NOT NULL,
                  event_type TEXT NOT NULL,
                  detail TEXT NOT NULL DEFAULT '',
                  created_at TEXT NOT NULL
                );
            """)
            self._ensure_column("correlation_id", "TEXT NOT NULL DEFAULT ''")
            self._ensure_column("trace_id", "TEXT NOT NULL DEFAULT ''")
            self._ensure_column("parent_span_id", "TEXT")
            now = _now()
            self._connection.execute("""
                UPDATE operation_job SET status = 'FAILED', error = 'service restarted before completion',
                  completed_at = ?, updated_at = ?
                WHERE status IN ('QUEUED', 'RUNNING', 'CANCEL_REQUESTED')
                """, (now, now))

    def close(self) -> None:
        self._connection.close()

    def _ensure_column(self, name: str, definition: str) -> None:
        columns = {
            row["name"] for row in self._connection.execute("PRAGMA table_info(operation_job)").fetchall()
        }
        if name not in columns:
            self._connection.execute(f"ALTER TABLE operation_job ADD COLUMN {name} {definition}")

    def create(
        self,
        operation_type: str,
        parent_job_id: str | None = None,
        attempt: int = 1,
        correlation_id: str = "",
        trace_id: str = "",
        parent_span_id: str | None = None,
    ) -> dict[str, Any]:
        job_id = str(uuid.uuid4())
        now = _now()
        with self._lock, self._connection:
            self._prune()
            self._connection.execute("""
                INSERT INTO operation_job (
                  id, operation_type, status, attempt, parent_job_id, created_at, updated_at,
                  correlation_id, trace_id, parent_span_id
                ) VALUES (?, ?, 'QUEUED', ?, ?, ?, ?, ?, ?, ?)
                """, (
                    job_id, operation_type, attempt, parent_job_id, now, now,
                    correlation_id, trace_id, parent_span_id,
                ))
            self._event(job_id, "QUEUED", f"operation={operation_type}", now)
        return self.get(job_id)

    def _prune(self) -> None:
        stale_rows = self._connection.execute("""
            SELECT id FROM operation_job ORDER BY created_at DESC LIMIT -1 OFFSET 500
            """).fetchall()
        stale_ids = [row["id"] for row in stale_rows]
        if not stale_ids:
            return
        placeholders = ",".join("?" for _ in stale_ids)
        self._connection.execute(
            f"DELETE FROM operation_job_event WHERE job_id IN ({placeholders})", stale_ids
        )
        self._connection.execute(
            f"DELETE FROM operation_job WHERE id IN ({placeholders})", stale_ids
        )

    def mark_running(self, job_id: str) -> None:
        now = _now()
        with self._lock, self._connection:
            self._connection.execute("""
                UPDATE operation_job SET status = 'RUNNING', started_at = ?, updated_at = ? WHERE id = ?
                """, (now, now, job_id))
            self._event(job_id, "STARTED", "worker acquired job", now)

    def progress(self, job_id: str, current: int, total: int, message: str) -> None:
        now = _now()
        with self._lock, self._connection:
            self._connection.execute("""
                UPDATE operation_job SET progress_current = ?, progress_total = ?,
                  progress_message = ?, updated_at = ? WHERE id = ?
                """, (current, total, message, now, job_id))
            self._event(job_id, "PROGRESS", f"{current}/{total} {message}", now)

    def request_cancel(self, job_id: str) -> dict[str, Any]:
        job = self.get(job_id)
        if job["status"] in TERMINAL_STATUSES:
            return job
        now = _now()
        with self._lock, self._connection:
            self._connection.execute("""
                UPDATE operation_job SET status = 'CANCEL_REQUESTED', cancel_requested = 1,
                  updated_at = ? WHERE id = ?
                """, (now, job_id))
            self._event(job_id, "CANCEL_REQUESTED", "administrator requested cancellation", now)
        return self.get(job_id)

    def is_cancel_requested(self, job_id: str) -> bool:
        with self._lock:
            row = self._connection.execute(
                "SELECT cancel_requested FROM operation_job WHERE id = ?", (job_id,)
            ).fetchone()
        return bool(row and row["cancel_requested"])

    def complete(self, job_id: str, result: Any) -> None:
        now = _now()
        payload = result.model_dump() if hasattr(result, "model_dump") else result
        with self._lock, self._connection:
            self._connection.execute("""
                UPDATE operation_job SET status = 'COMPLETED', result_json = ?, error = '',
                  completed_at = ?, updated_at = ? WHERE id = ?
                """, (json.dumps(payload, ensure_ascii=False), now, now, job_id))
            self._event(job_id, "COMPLETED", "operation completed", now)

    def fail(self, job_id: str, error: str) -> None:
        self._finish(job_id, "FAILED", error, "FAILED")

    def cancel(self, job_id: str) -> None:
        self._finish(job_id, "CANCELLED", "", "CANCELLED")

    def _finish(self, job_id: str, status: str, error: str, event_type: str) -> None:
        now = _now()
        with self._lock, self._connection:
            self._connection.execute("""
                UPDATE operation_job SET status = ?, error = ?, completed_at = ?, updated_at = ?
                WHERE id = ?
                """, (status, error, now, now, job_id))
            self._event(job_id, event_type, error, now)

    def get(self, job_id: str) -> dict[str, Any]:
        with self._lock:
            row = self._connection.execute(
                "SELECT * FROM operation_job WHERE id = ?", (job_id,)
            ).fetchone()
        if row is None:
            raise ValueError("operation job not found")
        return _job_to_dict(row)

    def list(self, limit: int = 20) -> list[dict[str, Any]]:
        safe_limit = max(1, min(limit, 100))
        with self._lock:
            rows = self._connection.execute("""
                SELECT * FROM operation_job ORDER BY created_at DESC LIMIT ?
                """, (safe_limit,)).fetchall()
        return [_job_to_dict(row) for row in rows]

    def trace(self, job_id: str) -> dict[str, Any]:
        job = self.get(job_id)
        with self._lock:
            rows = self._connection.execute("""
                SELECT event_type, detail, created_at FROM operation_job_event
                WHERE job_id = ? ORDER BY id ASC
                """, (job_id,)).fetchall()
        return {
            "job": job,
            "events": [dict(row) for row in rows],
        }

    def _event(self, job_id: str, event_type: str, detail: str, created_at: str) -> None:
        self._connection.execute("""
            INSERT INTO operation_job_event (job_id, event_type, detail, created_at)
            VALUES (?, ?, ?, ?)
            """, (job_id, event_type, detail, created_at))


class AsyncOperationManager:
    def __init__(
        self,
        path: Path,
        runners: dict[str, OperationRunner],
        max_workers: int = 2,
        timeout_seconds: float = 300.0,
        telemetry: TelemetryStore | None = None,
    ):
        self.store = OperationJobStore(path)
        self.runners = runners
        self.telemetry = telemetry
        self.timeout_seconds = max(0.01, float(timeout_seconds))
        self.executor = ThreadPoolExecutor(
            max_workers=max(1, int(max_workers)), thread_name_prefix="agent-operation"
        )
        self._futures: dict[str, Future] = {}
        self._lock = threading.Lock()

    def close(self) -> None:
        self.executor.shutdown(wait=True, cancel_futures=True)
        self.store.close()

    def start(self, operation_type: str, parent_job_id: str | None = None, attempt: int = 1) -> dict[str, Any]:
        if operation_type not in self.runners:
            raise ValueError("unsupported operation type")
        trace_context = current_trace_context()
        if self.telemetry and trace_context is None:
            trace_context = self.telemetry.child_context()
        job = self.store.create(
            operation_type,
            parent_job_id,
            attempt,
            trace_context.correlation_id if trace_context else "",
            trace_context.trace_id if trace_context else "",
            trace_context.span_id if trace_context else None,
        )
        future = self.executor.submit(self._execute, job["id"], operation_type)
        with self._lock:
            self._futures[job["id"]] = future
        return self.store.get(job["id"])

    def cancel(self, job_id: str) -> dict[str, Any]:
        job = self.store.request_cancel(job_id)
        with self._lock:
            future = self._futures.get(job_id)
        if future and future.cancel():
            self.store.cancel(job_id)
        return self.store.get(job_id)

    def retry(self, job_id: str) -> dict[str, Any]:
        job = self.store.get(job_id)
        if job["status"] not in {"FAILED", "CANCELLED"}:
            raise ValueError("only failed or cancelled jobs can be retried")
        return self.start(job["operation_type"], job_id, int(job["attempt"]) + 1)

    def get(self, job_id: str) -> dict[str, Any]:
        return self.store.get(job_id)

    def list(self, limit: int = 20) -> list[dict[str, Any]]:
        return self.store.list(limit)

    def trace(self, job_id: str) -> dict[str, Any]:
        payload = self.store.trace(job_id)
        trace_id = payload["job"].get("trace_id")
        payload["telemetry"] = None
        if self.telemetry and trace_id:
            try:
                payload["telemetry"] = self.telemetry.export_trace(trace_id)
            except ValueError:
                pass
        return payload

    def _execute(self, job_id: str, operation_type: str) -> None:
        job = self.store.get(job_id)
        if self.telemetry:
            context = self.telemetry.child_context(
                job.get("trace_id") or None,
                job.get("correlation_id") or None,
                job.get("parent_span_id"),
            )
            attributes = {"operation.type": operation_type, "job.id": job_id}
            try:
                with self.telemetry.span(
                    f"operation.{operation_type.lower()}",
                    kind="CONSUMER",
                    attributes=attributes,
                    context=context,
                ):
                    self._execute_job(job_id, operation_type)
                    attributes["operation.status"] = self.store.get(job_id)["status"]
            except Exception:
                # The job store already contains the failure. The exception is raised only
                # through the span so telemetry records an ERROR terminal state.
                return
            return
        self._execute_job(job_id, operation_type)

    def _execute_job(self, job_id: str, operation_type: str) -> None:
        deadline = time.monotonic() + self.timeout_seconds
        timed_out = False

        def should_cancel() -> bool:
            nonlocal timed_out
            if time.monotonic() >= deadline:
                timed_out = True
                return True
            return self.store.is_cancel_requested(job_id)

        try:
            if self.store.is_cancel_requested(job_id):
                self.store.cancel(job_id)
                return
            self.store.mark_running(job_id)
            result = self.runners[operation_type](
                lambda current, total, message: self.store.progress(job_id, current, total, message),
                should_cancel,
            )
            if timed_out or time.monotonic() >= deadline:
                self.store.fail(job_id, f"operation timed out after {self.timeout_seconds:g} seconds")
            elif self.store.is_cancel_requested(job_id):
                self.store.cancel(job_id)
            else:
                self.store.complete(job_id, result)
        except OperationCancelled:
            if timed_out:
                self.store.fail(job_id, f"operation timed out after {self.timeout_seconds:g} seconds")
            else:
                self.store.cancel(job_id)
        except Exception as error:
            self.store.fail(job_id, str(error))
            raise
        finally:
            with self._lock:
                self._futures.pop(job_id, None)


def _job_to_dict(row: sqlite3.Row) -> dict[str, Any]:
    return {
        "id": row["id"],
        "operation_type": row["operation_type"],
        "status": row["status"],
        "progress_current": row["progress_current"],
        "progress_total": row["progress_total"],
        "progress_message": row["progress_message"],
        "attempt": row["attempt"],
        "parent_job_id": row["parent_job_id"],
        "result": json.loads(row["result_json"]) if row["result_json"] else None,
        "error": row["error"],
        "cancel_requested": bool(row["cancel_requested"]),
        "created_at": row["created_at"],
        "started_at": row["started_at"],
        "updated_at": row["updated_at"],
        "completed_at": row["completed_at"],
        "correlation_id": row["correlation_id"],
        "trace_id": row["trace_id"],
        "parent_span_id": row["parent_span_id"],
    }


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()
