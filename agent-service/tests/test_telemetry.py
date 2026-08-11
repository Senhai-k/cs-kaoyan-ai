from pathlib import Path
from http.server import BaseHTTPRequestHandler, HTTPServer
from threading import Thread

import pytest
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter

from app.telemetry import TelemetryStore, parse_traceparent


def test_parse_traceparent_rejects_invalid_and_zero_ids():
    assert parse_traceparent("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01") == (
        "0123456789abcdef0123456789abcdef",
        "0123456789abcdef",
    )
    assert parse_traceparent("00-" + "0" * 32 + "-0123456789abcdef-01") is None
    assert parse_traceparent("not-a-trace") is None


def test_store_exports_parented_spans_and_enforces_trace_id(tmp_path: Path):
    store = TelemetryStore(tmp_path / "telemetry.sqlite3")
    try:
        server = store.server_context(None, "request-123")
        with store.span("http.request", kind="SERVER", context=server):
            child = store.child_context()
            with store.span("operation.test", kind="CONSUMER", context=child):
                pass

        exported = store.export_trace(server.trace_id)
        assert [span["name"] for span in exported["spans"]] == ["operation.test", "http.request"]
        operation = exported["spans"][0]
        assert operation["parent_span_id"] == server.span_id
        assert operation["correlation_id"] == "request-123"
        with pytest.raises(ValueError, match="invalid trace id"):
            store.export_trace("../../invalid")
    finally:
        store.close()


def test_otlp_bridge_exports_the_same_trace_and_span_ids(tmp_path: Path):
    exporter = InMemorySpanExporter()
    store = TelemetryStore(tmp_path / "telemetry.sqlite3", otel_exporter=exporter)
    try:
        server = store.server_context(
            "00-0123456789abcdef0123456789abcdef-1111111111111111-01",
            "request-otel",
        )
        attributes = {"http.request.method": "GET"}
        with store.span("http.request", kind="SERVER", context=server, attributes=attributes):
            attributes["http.response.status_code"] = 200

        spans = exporter.get_finished_spans()
        assert len(spans) == 1
        assert f"{spans[0].context.trace_id:032x}" == server.trace_id
        assert f"{spans[0].context.span_id:016x}" == server.span_id
        assert f"{spans[0].parent.span_id:016x}" == "1111111111111111"
        assert spans[0].attributes["http.response.status_code"] == 200
    finally:
        store.close()


def test_otlp_http_exporter_posts_protobuf_to_configured_collector(tmp_path: Path):
    received = []

    class CollectorHandler(BaseHTTPRequestHandler):
        def do_POST(self):
            body = self.rfile.read(int(self.headers["Content-Length"]))
            received.append((self.path, self.headers.get("Content-Type"), body))
            self.send_response(200)
            self.end_headers()

        def log_message(self, format, *args):
            return

    server = HTTPServer(("127.0.0.1", 0), CollectorHandler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    endpoint = f"http://127.0.0.1:{server.server_port}/v1/traces"
    store = TelemetryStore(tmp_path / "telemetry.sqlite3", otlp_endpoint=endpoint)
    try:
        context = store.server_context(None, "request-collector")
        with store.span("http.request", kind="SERVER", context=context):
            pass
    finally:
        store.close()
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)

    assert len(received) == 1
    assert received[0][0] == "/v1/traces"
    assert received[0][1] == "application/x-protobuf"
    assert received[0][2]
