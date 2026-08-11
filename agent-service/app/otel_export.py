from __future__ import annotations

from contextlib import contextmanager
from contextvars import ContextVar
from typing import Any, Iterator

from opentelemetry import trace
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor, SimpleSpanProcessor, SpanExporter
from opentelemetry.sdk.trace.id_generator import IdGenerator, RandomIdGenerator
from opentelemetry.trace import NonRecordingSpan, SpanContext, SpanKind, TraceFlags, TraceState

from .telemetry import TraceContext


_desired_trace_id: ContextVar[int | None] = ContextVar("otel_desired_trace_id", default=None)
_desired_span_id: ContextVar[int | None] = ContextVar("otel_desired_span_id", default=None)


class ContextIdGenerator(IdGenerator):
    def __init__(self):
        self.random = RandomIdGenerator()

    def generate_span_id(self) -> int:
        return _desired_span_id.get() or self.random.generate_span_id()

    def generate_trace_id(self) -> int:
        return _desired_trace_id.get() or self.random.generate_trace_id()


class OpenTelemetryBridge:
    def __init__(
        self,
        endpoint: str = "",
        service_name: str = "cs-kaoyan-agent",
        exporter: SpanExporter | None = None,
    ):
        provider = TracerProvider(
            resource=Resource.create({"service.name": service_name}),
            id_generator=ContextIdGenerator(),
        )
        if exporter is not None:
            provider.add_span_processor(SimpleSpanProcessor(exporter))
        elif endpoint:
            from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter

            provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter(endpoint=endpoint)))
        self.provider = provider
        self.tracer = provider.get_tracer("cs-kaoyan-agent")

    @contextmanager
    def span(
        self,
        name: str,
        kind: str,
        context: TraceContext,
        attributes: dict[str, Any],
    ) -> Iterator[None]:
        parent_context = None
        if context.parent_span_id:
            parent = SpanContext(
                trace_id=int(context.trace_id, 16),
                span_id=int(context.parent_span_id, 16),
                is_remote=True,
                trace_flags=TraceFlags(TraceFlags.SAMPLED),
                trace_state=TraceState(),
            )
            parent_context = trace.set_span_in_context(NonRecordingSpan(parent))
        trace_token = _desired_trace_id.set(int(context.trace_id, 16))
        span_token = _desired_span_id.set(int(context.span_id, 16))
        try:
            with self.tracer.start_as_current_span(
                name,
                context=parent_context,
                kind=_span_kind(kind),
                attributes=_otel_attributes(attributes),
            ) as span:
                actual = span.get_span_context()
                if actual.trace_id != int(context.trace_id, 16) or actual.span_id != int(context.span_id, 16):
                    raise RuntimeError("OpenTelemetry span IDs diverged from local trace context")
                try:
                    yield
                finally:
                    for key, value in _otel_attributes(attributes).items():
                        span.set_attribute(key, value)
        finally:
            _desired_span_id.reset(span_token)
            _desired_trace_id.reset(trace_token)

    def close(self) -> None:
        self.provider.force_flush(timeout_millis=5000)
        self.provider.shutdown()


def _span_kind(value: str) -> SpanKind:
    return {
        "SERVER": SpanKind.SERVER,
        "CONSUMER": SpanKind.CONSUMER,
        "CLIENT": SpanKind.CLIENT,
        "PRODUCER": SpanKind.PRODUCER,
    }.get(value, SpanKind.INTERNAL)


def _otel_attributes(attributes: dict[str, Any]) -> dict[str, Any]:
    return {
        key: value
        for key, value in attributes.items()
        if isinstance(value, (bool, str, int, float))
    }
