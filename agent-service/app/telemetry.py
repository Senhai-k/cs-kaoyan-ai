from __future__ import annotations

import json
import logging
import re
import secrets
import sqlite3
import threading
import time
from contextlib import contextmanager
from contextvars import ContextVar
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import TYPE_CHECKING, Any, Iterator

if TYPE_CHECKING:
    from opentelemetry.sdk.trace.export import SpanExporter
    from .otel_export import OpenTelemetryBridge


TRACEPARENT_PATTERN = re.compile(r"^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$")
CORRELATION_PATTERN = re.compile(r"^[A-Za-z0-9._-]{1,64}$")


@dataclass(frozen=True)
class TraceContext:
    trace_id: str
    span_id: str
    parent_span_id: str | None
    correlation_id: str

    @property
    def traceparent(self) -> str:
        return f"00-{self.trace_id}-{self.span_id}-01"


_current_context: ContextVar[TraceContext | None] = ContextVar("trace_context", default=None)


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        context = current_trace_context()
        payload = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "correlation_id": context.correlation_id if context else "",
            "trace_id": context.trace_id if context else "",
            "span_id": context.span_id if context else "",
        }
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        return json.dumps(payload, ensure_ascii=False)


def configure_structured_logging() -> None:
    logger = logging.getLogger("agent.telemetry")
    if logger.handlers:
        return
    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter())
    logger.addHandler(handler)
    logger.setLevel(logging.INFO)
    logger.propagate = False


class TelemetryStore:
    def __init__(
        self,
        path: Path,
        max_spans: int = 2000,
        otlp_endpoint: str = "",
        otel_exporter: "SpanExporter | None" = None,
    ):
        self.max_spans = max(100, max_spans)
        self._connection = sqlite3.connect(path, check_same_thread=False)
        self._connection.row_factory = sqlite3.Row
        self._lock = threading.Lock()
        self._otel: "OpenTelemetryBridge | None" = None
        if otlp_endpoint or otel_exporter is not None:
            from .otel_export import OpenTelemetryBridge

            self._otel = OpenTelemetryBridge(otlp_endpoint, exporter=otel_exporter)
        with self._connection:
            self._connection.execute("""
                CREATE TABLE IF NOT EXISTS telemetry_span (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  trace_id TEXT NOT NULL,
                  span_id TEXT NOT NULL,
                  parent_span_id TEXT,
                  correlation_id TEXT NOT NULL,
                  service_name TEXT NOT NULL,
                  span_name TEXT NOT NULL,
                  span_kind TEXT NOT NULL,
                  status TEXT NOT NULL,
                  duration_ms REAL NOT NULL,
                  attributes_json TEXT NOT NULL DEFAULT '{}',
                  error TEXT NOT NULL DEFAULT '',
                  started_at TEXT NOT NULL
                )
            """)

    def close(self) -> None:
        if self._otel:
            self._otel.close()
        self._connection.close()

    @property
    def otlp_exporter_enabled(self) -> bool:
        return self._otel is not None

    def server_context(self, traceparent: str | None, correlation_id: str | None) -> TraceContext:
        parsed = parse_traceparent(traceparent)
        trace_id = parsed[0] if parsed else _hex(16)
        parent_span_id = parsed[1] if parsed else None
        correlation = correlation_id if correlation_id and CORRELATION_PATTERN.fullmatch(correlation_id) else _hex(16)
        return TraceContext(trace_id, _hex(8), parent_span_id, correlation)

    def child_context(
        self,
        trace_id: str | None = None,
        correlation_id: str | None = None,
        parent_span_id: str | None = None,
    ) -> TraceContext:
        current = current_trace_context()
        return TraceContext(
            trace_id or (current.trace_id if current else _hex(16)),
            _hex(8),
            parent_span_id or (current.span_id if current else None),
            correlation_id or (current.correlation_id if current else _hex(16)),
        )

    @contextmanager
    def activate(self, context: TraceContext) -> Iterator[None]:
        token = _current_context.set(context)
        try:
            yield
        finally:
            _current_context.reset(token)

    @contextmanager
    def span(
        self,
        name: str,
        kind: str = "INTERNAL",
        attributes: dict[str, Any] | None = None,
        context: TraceContext | None = None,
    ) -> Iterator[TraceContext]:
        span_context = context or self.child_context()
        started = time.perf_counter()
        started_at = datetime.now(timezone.utc).isoformat()
        status = "OK"
        error = ""
        otel_span = self._otel.span(name, kind, span_context, attributes or {}) if self._otel else _noop_span()
        with otel_span, self.activate(span_context):
            try:
                yield span_context
            except Exception as exception:
                status = "ERROR"
                error = str(exception)
                raise
            finally:
                self.record(
                    span_context,
                    name,
                    kind,
                    status,
                    (time.perf_counter() - started) * 1000,
                    attributes or {},
                    error,
                    started_at,
                )

    def record(
        self,
        context: TraceContext,
        name: str,
        kind: str,
        status: str,
        duration_ms: float,
        attributes: dict[str, Any],
        error: str,
        started_at: str,
    ) -> None:
        with self._lock, self._connection:
            self._connection.execute("""
                INSERT INTO telemetry_span (
                  trace_id, span_id, parent_span_id, correlation_id, service_name,
                  span_name, span_kind, status, duration_ms, attributes_json, error, started_at
                ) VALUES (?, ?, ?, ?, 'cs-kaoyan-agent', ?, ?, ?, ?, ?, ?, ?)
                """, (
                    context.trace_id,
                    context.span_id,
                    context.parent_span_id,
                    context.correlation_id,
                    name,
                    kind,
                    status,
                    round(duration_ms, 3),
                    json.dumps(attributes, ensure_ascii=False),
                    error,
                    started_at,
                ))
            self._connection.execute("""
                DELETE FROM telemetry_span WHERE id IN (
                  SELECT id FROM telemetry_span ORDER BY id DESC LIMIT -1 OFFSET ?
                )
                """, (self.max_spans,))

    def recent_traces(self, limit: int = 20) -> list[dict[str, Any]]:
        safe_limit = max(1, min(limit, 100))
        with self._lock:
            rows = self._connection.execute("""
                SELECT trace_id, correlation_id, COUNT(*) AS span_count,
                  SUM(duration_ms) AS total_duration_ms,
                  MAX(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END) AS has_error,
                  MAX(started_at) AS updated_at
                FROM telemetry_span GROUP BY trace_id, correlation_id
                ORDER BY updated_at DESC LIMIT ?
                """, (safe_limit,)).fetchall()
        return [dict(row) for row in rows]

    def export_trace(self, trace_id: str) -> dict[str, Any]:
        if not re.fullmatch(r"[0-9a-f]{32}", trace_id):
            raise ValueError("invalid trace id")
        with self._lock:
            rows = self._connection.execute("""
                SELECT * FROM telemetry_span WHERE trace_id = ? ORDER BY id ASC
                """, (trace_id,)).fetchall()
        if not rows:
            raise ValueError("trace not found")
        return {
            "resource": {"service.name": "cs-kaoyan-agent"},
            "trace_id": trace_id,
            "spans": [_span_to_dict(row) for row in rows],
        }


def current_trace_context() -> TraceContext | None:
    return _current_context.get()


def parse_traceparent(value: str | None) -> tuple[str, str] | None:
    if not value:
        return None
    match = TRACEPARENT_PATTERN.fullmatch(value.strip().lower())
    if not match or match.group(1) == "0" * 32 or match.group(2) == "0" * 16:
        return None
    return match.group(1), match.group(2)


def _span_to_dict(row: sqlite3.Row) -> dict[str, Any]:
    return {
        "trace_id": row["trace_id"],
        "span_id": row["span_id"],
        "parent_span_id": row["parent_span_id"],
        "correlation_id": row["correlation_id"],
        "service_name": row["service_name"],
        "name": row["span_name"],
        "kind": row["span_kind"],
        "status": row["status"],
        "duration_ms": row["duration_ms"],
        "attributes": json.loads(row["attributes_json"] or "{}"),
        "error": row["error"],
        "started_at": row["started_at"],
    }


def _hex(byte_count: int) -> str:
    return secrets.token_hex(byte_count)


@contextmanager
def _noop_span() -> Iterator[None]:
    yield
