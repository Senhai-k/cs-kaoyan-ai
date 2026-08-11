from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AGENT_", env_file=".env", extra="ignore")

    service_host: str = "127.0.0.1"
    service_port: int = 18889
    spring_base_url: str = "http://127.0.0.1:18888"
    internal_token: str = ""
    data_dir: Path = Path("data")
    embedding_model: str = "./models/bge-small-zh-v1.5"
    reranker_model: str = "BAAI/bge-reranker-base"
    enable_reranker: bool = True
    reranker_mode: str = "feature"
    local_llm_model: str = ""
    openai_api_key: str = ""
    openai_base_url: str = ""
    openai_model: str = "gpt-4.1-mini"
    planner_prompt_version: str = "coverage-planner-v3"
    planner_pricing_mode: Literal["METERED", "UNMETERED"] = "METERED"
    planner_input_cost_per_million_usd: float = Field(default=0.0, ge=0.0)
    planner_output_cost_per_million_usd: float = Field(default=0.0, ge=0.0)
    retrieval_limit: int = 5
    candidate_limit: int = 20
    request_timeout_seconds: float = 60.0
    workflow_fetch_timeout_seconds: float = 20.0
    workflow_max_content_bytes: int = 2 * 1024 * 1024
    operation_max_workers: int = 2
    operation_timeout_seconds: float = 300.0
    telemetry_max_spans: int = 2000
    otlp_endpoint: str = ""

    @property
    def planner_llm_readiness(self) -> dict[str, object]:
        missing = []
        if not self.openai_api_key.strip():
            missing.append("AGENT_OPENAI_API_KEY")
        if not self.openai_model.strip():
            missing.append("AGENT_OPENAI_MODEL")
        if self.planner_pricing_mode == "METERED":
            if self.planner_input_cost_per_million_usd <= 0:
                missing.append("AGENT_PLANNER_INPUT_COST_PER_MILLION_USD")
            if self.planner_output_cost_per_million_usd <= 0:
                missing.append("AGENT_PLANNER_OUTPUT_COST_PER_MILLION_USD")
        configured = bool(self.openai_api_key.strip() and self.openai_model.strip())
        experiment_ready = not missing
        return {
            "configured": configured,
            "experimentReady": experiment_ready,
            "status": "READY" if experiment_ready else "INCOMPLETE",
            "model": self.openai_model,
            "endpointType": "custom" if self.openai_base_url.strip() else "openai-default",
            "pricingMode": self.planner_pricing_mode,
            "missingConfiguration": missing,
            "pricingUnit": "USD_PER_MILLION_TOKENS" if self.planner_pricing_mode == "METERED" else "UNMETERED",
        }

    @property
    def planner_replay_path(self) -> Path:
        return self.data_dir / "planner-evaluation-replay.json"

    @property
    def reranker_benchmark_path(self) -> Path:
        return self.data_dir / "reranker-benchmark-latest.json"

    @property
    def qdrant_path(self) -> Path:
        return self.data_dir / "qdrant"

    @property
    def corpus_path(self) -> Path:
        return self.data_dir / "corpus.json"

    @property
    def checkpoint_path(self) -> Path:
        return self.data_dir / "checkpoints.sqlite3"

    @property
    def metrics_path(self) -> Path:
        return self.data_dir / "metrics.sqlite3"


@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    settings.data_dir.mkdir(parents=True, exist_ok=True)
    return settings
