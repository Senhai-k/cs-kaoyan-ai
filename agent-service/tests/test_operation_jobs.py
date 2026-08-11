import sqlite3
import threading
import time
from pathlib import Path

from app.operation_control import ensure_not_cancelled
from app.operation_jobs import AsyncOperationManager
from app.operation_jobs import OperationJobStore
from app.telemetry import TelemetryStore


def wait_for_status(manager: AsyncOperationManager, job_id: str, statuses: set[str], timeout: float = 3):
    deadline = time.time() + timeout
    while time.time() < deadline:
        job = manager.get(job_id)
        if job["status"] in statuses:
            return job
        time.sleep(0.01)
    raise AssertionError(f"job did not reach {statuses}: {manager.get(job_id)}")


def test_async_job_persists_progress_result_and_trace(tmp_path: Path):
    def runner(progress, cancel_check):
        progress(0, 2, "start")
        ensure_not_cancelled(cancel_check)
        progress(1, 2, "middle")
        progress(2, 2, "done")
        return {"value": 42}

    manager = AsyncOperationManager(tmp_path / "jobs.sqlite3", {"TEST": runner}, max_workers=1)
    try:
        started = manager.start("TEST")
        completed = wait_for_status(manager, started["id"], {"COMPLETED"})
        trace = manager.trace(started["id"])

        assert completed["result"] == {"value": 42}
        assert completed["progress_current"] == 2
        assert [event["event_type"] for event in trace["events"]] == [
            "QUEUED", "STARTED", "PROGRESS", "PROGRESS", "PROGRESS", "COMPLETED"
        ]
    finally:
        manager.close()


def test_running_job_can_be_cancelled_cooperatively(tmp_path: Path):
    def runner(progress, cancel_check):
        for index in range(100):
            ensure_not_cancelled(cancel_check)
            progress(index, 100, "working")
            time.sleep(0.005)
        return {"unexpected": True}

    manager = AsyncOperationManager(tmp_path / "jobs.sqlite3", {"TEST": runner}, max_workers=1)
    try:
        started = manager.start("TEST")
        wait_for_status(manager, started["id"], {"RUNNING"})
        manager.cancel(started["id"])
        cancelled = wait_for_status(manager, started["id"], {"CANCELLED"})

        assert cancelled["result"] is None
        assert cancelled["cancel_requested"] is True
    finally:
        manager.close()


def test_failed_job_can_be_retried_with_parent_trace(tmp_path: Path):
    calls = 0

    def runner(progress, cancel_check):
        nonlocal calls
        calls += 1
        if calls == 1:
            raise RuntimeError("temporary failure")
        progress(1, 1, "recovered")
        return {"recovered": True}

    manager = AsyncOperationManager(tmp_path / "jobs.sqlite3", {"TEST": runner}, max_workers=1)
    try:
        first = manager.start("TEST")
        failed = wait_for_status(manager, first["id"], {"FAILED"})
        retried = manager.retry(failed["id"])
        completed = wait_for_status(manager, retried["id"], {"COMPLETED"})

        assert completed["attempt"] == 2
        assert completed["parent_job_id"] == failed["id"]
        assert completed["result"] == {"recovered": True}
    finally:
        manager.close()


def test_existing_job_database_is_migrated_with_trace_columns(tmp_path: Path):
    path = tmp_path / "jobs.sqlite3"
    connection = sqlite3.connect(path)
    connection.execute("""
        CREATE TABLE operation_job (
          id TEXT PRIMARY KEY, operation_type TEXT NOT NULL, status TEXT NOT NULL,
          progress_current INTEGER NOT NULL DEFAULT 0, progress_total INTEGER NOT NULL DEFAULT 0,
          progress_message TEXT NOT NULL DEFAULT '', attempt INTEGER NOT NULL DEFAULT 1,
          parent_job_id TEXT, result_json TEXT, error TEXT NOT NULL DEFAULT '',
          cancel_requested INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL,
          started_at TEXT, updated_at TEXT NOT NULL, completed_at TEXT
        )
    """)
    connection.commit()
    connection.close()

    store = OperationJobStore(path)
    try:
        columns = {
            row[1] for row in store._connection.execute("PRAGMA table_info(operation_job)").fetchall()
        }
        assert {"correlation_id", "trace_id", "parent_span_id"}.issubset(columns)
    finally:
        store.close()


def test_failed_job_records_error_span_with_request_trace(tmp_path: Path):
    def runner(progress, cancel_check):
        raise RuntimeError("expected failure")

    telemetry = TelemetryStore(tmp_path / "telemetry.sqlite3")
    manager = AsyncOperationManager(
        tmp_path / "jobs.sqlite3", {"TEST": runner}, max_workers=1, telemetry=telemetry
    )
    try:
        request_context = telemetry.server_context(None, "request-456")
        with telemetry.activate(request_context):
            started = manager.start("TEST")
        failed = wait_for_status(manager, started["id"], {"FAILED"})
        trace = manager.trace(failed["id"])["telemetry"]

        assert failed["trace_id"] == request_context.trace_id
        operation_span = next(span for span in trace["spans"] if span["name"] == "operation.test")
        assert operation_span["status"] == "ERROR"
        assert operation_span["parent_span_id"] == request_context.span_id
        assert operation_span["error"] == "expected failure"
    finally:
        manager.close()
        telemetry.close()


def test_worker_pool_keeps_concurrency_within_configured_limit(tmp_path: Path):
    lock = threading.Lock()
    active = 0
    peak = 0

    def runner(progress, cancel_check):
        nonlocal active, peak
        with lock:
            active += 1
            peak = max(peak, active)
        try:
            time.sleep(0.04)
            ensure_not_cancelled(cancel_check)
            return {"ok": True}
        finally:
            with lock:
                active -= 1

    manager = AsyncOperationManager(
        tmp_path / "jobs.sqlite3", {"TEST": runner}, max_workers=2, timeout_seconds=2
    )
    try:
        jobs = [manager.start("TEST") for _ in range(8)]
        completed = [
            wait_for_status(manager, job["id"], {"COMPLETED"}, timeout=3) for job in jobs
        ]

        assert all(job["status"] == "COMPLETED" for job in completed)
        assert peak == 2
    finally:
        manager.close()


def test_cooperative_timeout_is_failed_instead_of_cancelled(tmp_path: Path):
    def runner(progress, cancel_check):
        while True:
            ensure_not_cancelled(cancel_check)
            time.sleep(0.005)

    manager = AsyncOperationManager(
        tmp_path / "jobs.sqlite3", {"TEST": runner}, max_workers=1, timeout_seconds=0.03
    )
    try:
        started = manager.start("TEST")
        failed = wait_for_status(manager, started["id"], {"FAILED"}, timeout=2)

        assert failed["cancel_requested"] is False
        assert failed["error"] == "operation timed out after 0.03 seconds"
    finally:
        manager.close()


def test_restart_recovers_bulk_interrupted_jobs_as_failed(tmp_path: Path):
    path = tmp_path / "jobs.sqlite3"
    first = OperationJobStore(path)
    job_ids = []
    try:
        for _ in range(25):
            job = first.create("TEST")
            first.mark_running(job["id"])
            job_ids.append(job["id"])
    finally:
        first.close()

    recovered = OperationJobStore(path)
    try:
        jobs = [recovered.get(job_id) for job_id in job_ids]
        assert all(job["status"] == "FAILED" for job in jobs)
        assert all(job["error"] == "service restarted before completion" for job in jobs)
    finally:
        recovered.close()
