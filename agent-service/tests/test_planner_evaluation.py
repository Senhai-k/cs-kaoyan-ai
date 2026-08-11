import json
from pathlib import Path

import pytest

from app.config import Settings
from app.coverage_workflow import CoveragePlan, CoveragePlanStep, CoveragePlanner
from app.planner_evaluation import replay_planner_evaluation, run_planner_evaluation


def test_deterministic_planner_baseline_is_reproducible_and_llm_is_explicitly_skipped(tmp_path: Path):
    planner = CoveragePlanner(Settings(openai_api_key="", data_dir=tmp_path))
    cases_path = Path(__file__).resolve().parents[1] / "evals" / "planner_eval.json"

    result = run_planner_evaluation(planner, cases_path)

    assert result["cases"] == 10
    assert result["deterministic"]["status"] == "COMPLETED"
    assert result["deterministic"]["exact_match_rate"] == 1.0
    assert result["deterministic"]["target_precision"] == 1.0
    assert result["deterministic"]["target_recall"] == 1.0
    assert result["deterministic"]["unsafe_selection_rate"] == 0.0
    assert result["llm"]["status"] == "SKIPPED"
    assert "AGENT_OPENAI_API_KEY" in result["llm"]["reason"]
    assert result["recommended_mode"] == "deterministic"
    assert result["dataset_version"] == "planner-eval-v3"
    assert result["deterministic"]["prompt_version"] == "deterministic-v2"
    assert result["deterministic"]["cost_status"] == "NOT_APPLICABLE"
    details = {item["id"]: item for item in result["deterministic"]["details"]}
    assert details["exclude-targets-unrelated-to-gap"]["eligibility_rejections"] == [
        {"target_id": 44, "reason": "unrelated_to_missing_dimensions"}
    ]
    duplicate_rejections = details["deduplicate-url-by-gap-priority"]["eligibility_rejections"]
    assert duplicate_rejections == [{
        "target_id": 40, "reason": "duplicate_source_url", "kept_target_id": 41,
    }]
    assert result["replay_available"] is True
    assert planner.settings.planner_replay_path.exists()

    replay = replay_planner_evaluation(planner.settings.planner_replay_path)

    assert replay["run_type"] == "REPLAY"
    assert replay["replayed_from_evaluation_id"] == result["evaluation_id"]
    assert replay["deterministic"]["exact_match_rate"] == 1.0
    assert replay["dataset_hash"] == result["dataset_hash"]

    artifact = json.loads(planner.settings.planner_replay_path.read_text(encoding="utf-8"))
    artifact["result"]["deterministic"]["details"][0]["selected_target_ids"] = [999]
    planner.settings.planner_replay_path.write_text(
        json.dumps(artifact, ensure_ascii=False), encoding="utf-8"
    )
    with pytest.raises(RuntimeError, match="hash mismatch"):
        replay_planner_evaluation(planner.settings.planner_replay_path)


class FakeRawMessage:
    id = "fake-request-id"
    usage_metadata = {"input_tokens": 100, "output_tokens": 50, "total_tokens": 150}
    response_metadata = {"model_name": "fake-planner-model"}


class FakeStructuredModel:
    def invoke(self, prompt: str):
        assert "coverage-planner-v3" in prompt
        return {
            "parsed": CoveragePlan(summary="选择复试线", steps=[CoveragePlanStep(
                target_id=2,
                title="ignored",
                document_type="ignored",
                target_year=2026,
                source_url="https://ignored.test",
                reason="优先补齐复试线",
            )]),
            "raw": FakeRawMessage(),
            "parsing_error": None,
        }


def test_llm_planner_captures_usage_prompt_fingerprint_and_estimated_cost(tmp_path: Path):
    planner = CoveragePlanner(Settings(
        openai_api_key="test-key",
        openai_model="configured-model",
        data_dir=tmp_path,
        planner_input_cost_per_million_usd=2.0,
        planner_output_cost_per_million_usd=8.0,
    ))
    planner._model = FakeStructuredModel()
    task = {
        "schoolName": "测试大学",
        "missingDimensions": ["复试线"],
        "targets": [{
            "id": 2,
            "title": "复试分数线",
            "documentType": "复试分数线",
            "targetYear": 2026,
            "sourceUrl": "https://cs.test.edu.cn/2026/score/page.htm",
            "status": "PENDING",
        }],
    }

    run = planner.plan_with_metadata(task, 1, "llm")

    assert [step.target_id for step in run.plan.steps] == [2]
    assert run.metadata.prompt_version == "coverage-planner-v3"
    assert len(run.metadata.prompt_hash) == 64
    assert run.metadata.model == "fake-planner-model"
    assert run.metadata.total_tokens == 150
    assert run.metadata.estimated_cost_usd == 0.0006
    assert run.metadata.usage_status == "MEASURED"
    assert run.metadata.cost_status == "ESTIMATED"
    assert run.metadata.request_id == "fake-request-id"
    assert run.metadata.proposed_target_ids == [2]
    assert run.metadata.guard_rejected_target_ids == []
    assert run.metadata.guard_intervention_count == 0


class GuardedStructuredModel:
    def invoke(self, prompt: str):
        return {
            "parsed": CoveragePlan(summary="包含越界目标", steps=[
                CoveragePlanStep(
                    target_id=999, title="越界", document_type="越界", target_year=2026,
                    source_url="https://invalid.test", reason="模型越界提议",
                ),
                CoveragePlanStep(
                    target_id=2, title="ignored", document_type="ignored", target_year=2026,
                    source_url="https://ignored.test", reason="有效目标",
                ),
            ]),
            "raw": FakeRawMessage(),
            "parsing_error": None,
        }


def test_llm_planner_records_guarded_model_proposals(tmp_path: Path):
    planner = CoveragePlanner(Settings(openai_api_key="test-key", data_dir=tmp_path))
    planner._model = GuardedStructuredModel()
    task = {
        "schoolName": "测试大学",
        "missingDimensions": ["复试线"],
        "targets": [{
            "id": 2, "title": "复试分数线", "documentType": "复试分数线", "targetYear": 2026,
            "sourceUrl": "https://cs.test.edu.cn/2026/score/page.htm", "status": "PENDING",
        }],
    }

    run = planner.plan_with_metadata(task, 1, "llm")

    assert [step.target_id for step in run.plan.steps] == [2]
    assert run.metadata.proposed_target_ids == [999, 2]
    assert run.metadata.guard_rejected_target_ids == [999]
    assert run.metadata.guard_intervention_count == 1


def test_llm_planner_does_not_underestimate_cost_with_partial_pricing(tmp_path: Path):
    planner = CoveragePlanner(Settings(
        openai_api_key="test-key",
        data_dir=tmp_path,
        planner_input_cost_per_million_usd=2.0,
        planner_output_cost_per_million_usd=0.0,
    ))
    planner._model = FakeStructuredModel()
    task = {
        "schoolName": "测试大学",
        "missingDimensions": ["复试线"],
        "targets": [{
            "id": 2, "title": "复试分数线", "documentType": "复试分数线", "targetYear": 2026,
            "sourceUrl": "https://cs.test.edu.cn/2026/score/page.htm", "status": "PENDING",
        }],
    }

    run = planner.plan_with_metadata(task, 1, "llm")

    assert run.metadata.total_tokens == 150
    assert run.metadata.estimated_cost_usd is None
    assert run.metadata.cost_status == "RATE_UNCONFIGURED"


def test_unmetered_planner_reports_usage_without_inventing_cost(tmp_path: Path):
    planner = CoveragePlanner(Settings(
        openai_api_key="test-key",
        data_dir=tmp_path,
        planner_pricing_mode="UNMETERED",
    ))
    planner._model = FakeStructuredModel()
    task = {
        "schoolName": "测试大学",
        "missingDimensions": ["复试线"],
        "targets": [{
            "id": 2, "title": "复试分数线", "documentType": "复试分数线", "targetYear": 2026,
            "sourceUrl": "https://cs.test.edu.cn/2026/score/page.htm", "status": "PENDING",
        }],
    }

    run = planner.plan_with_metadata(task, 1, "llm")

    assert run.metadata.total_tokens == 150
    assert run.metadata.estimated_cost_usd is None
    assert run.metadata.cost_status == "UNMETERED"
