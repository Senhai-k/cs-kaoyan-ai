from pathlib import Path

import pytest
import yaml
from pydantic import ValidationError

from app.config import Settings


ROOT = Path(__file__).resolve().parents[2]
DEPLOY = ROOT / "deploy"


def _load(name: str):
    return yaml.safe_load((DEPLOY / name).read_text(encoding="utf-8"))


def test_compose_contains_persistent_services_healthchecks_and_private_network():
    compose = _load("compose.yml")
    services = compose["services"]
    expected = {
        "mysql", "backend", "agent", "index-sync", "frontend",
        "blackbox", "prometheus", "alertmanager",
    }
    assert expected == set(services)
    for name in ("mysql", "backend", "agent", "frontend"):
        assert "healthcheck" in services[name]
        assert "private" in services[name]["networks"]
    assert services["agent"]["volumes"]
    assert services["agent"]["environment"]["AGENT_PLANNER_PRICING_MODE"] == "${AGENT_PLANNER_PRICING_MODE:-METERED}"
    assert services["backend"]["depends_on"]["mysql"]["condition"] == "service_healthy"


def test_monitoring_configs_cover_all_services_and_unique_alerts():
    prometheus = _load("prometheus.yml")
    jobs = {item["job_name"] for item in prometheus["scrape_configs"]}
    assert jobs == {"backend", "agent", "service-probes"}

    alerts = _load("alerts.yml")["groups"][0]["rules"]
    names = [item["alert"] for item in alerts]
    assert len(names) == len(set(names))
    assert {
        "CsKaoyanServiceUnavailable",
        "CsKaoyanBackendHigh5xxRatio",
        "CsKaoyanAgentIndexEmpty",
        "CsKaoyanAgentCompletionRatioLow",
        "CsKaoyanAgentToolSuccessRatioLow",
        "CsKaoyanJvmHeapPressure",
        "CsKaoyanWebCaptureReviewOverdue",
    } == set(names)
    assert all(item["labels"]["severity"] in {"warning", "critical"} for item in alerts)
    overdue = next(item for item in alerts if item["alert"] == "CsKaoyanWebCaptureReviewOverdue")
    assert overdue["expr"] == "cs_kaoyan_web_capture_oldest_pending_age_seconds > 86400"
    assert overdue["for"] == "15m"

    alertmanager = _load("alertmanager.yml")
    receiver_names = {item["name"] for item in alertmanager["receivers"]}
    assert alertmanager["route"]["receiver"] in receiver_names


def test_planner_readiness_requires_model_key_and_positive_pricing(tmp_path: Path):
    incomplete = Settings(data_dir=tmp_path, openai_api_key="", planner_input_cost_per_million_usd=0)
    assert incomplete.planner_llm_readiness["status"] == "INCOMPLETE"
    assert "AGENT_OPENAI_API_KEY" in incomplete.planner_llm_readiness["missingConfiguration"]

    ready = Settings(
        data_dir=tmp_path,
        openai_api_key="test-key",
        openai_model="test-model",
        planner_input_cost_per_million_usd=1.0,
        planner_output_cost_per_million_usd=2.0,
    )
    assert ready.planner_llm_readiness["status"] == "READY"
    assert ready.planner_llm_readiness["missingConfiguration"] == []

    unmetered = Settings(
        data_dir=tmp_path,
        openai_api_key="test-key",
        openai_model="test-model",
        planner_pricing_mode="UNMETERED",
    )
    assert unmetered.planner_llm_readiness["status"] == "READY"
    assert unmetered.planner_llm_readiness["pricingMode"] == "UNMETERED"
    assert unmetered.planner_llm_readiness["missingConfiguration"] == []

    with pytest.raises(ValidationError):
        Settings(data_dir=tmp_path, planner_input_cost_per_million_usd=-1)
