from pathlib import Path

from langgraph.types import Command

from app.config import Settings
from app.graph import AdmissionsAgentGraph
from app.models import RetrievedEvidence


class FakeRetriever:
    def __init__(self, evidence: list[RetrievedEvidence]):
        self.evidence = evidence
        self.calls = 0

    def school_names(self) -> list[str]:
        return ["西安电子科技大学"]

    def search(self, question: str, school_name: str | None = None, limit: int | None = None):
        self.calls += 1
        return self.evidence

    def knowledge_profile(self, school_name: str | None = None):
        return {
            "school_name": school_name,
            "school_id": 9,
            "documents": 2,
            "chunks": 3,
            "years": [2026, 2025],
            "document_types": {"招生专业目录": 2, "复试细则": 1},
            "sources": [{"title": "2026年招生专业目录", "year": 2026, "url": "https://example.test/catalog"}],
            "total_schools": 1,
        }


class FakeGenerator:
    mode = "test-generator"

    def generate(self, question: str, evidence: list[RetrievedEvidence], feedback: str = "") -> str:
        if not evidence:
            return "资料不足"
        return f"西安电子科技大学初试科目包含408 [1]{'；' + feedback if feedback else ''}"


class MultiSchoolRetriever(FakeRetriever):
    def school_names(self) -> list[str]:
        return ["北京邮电大学", "南京大学"]

    def search(self, question: str, school_name: str | None = None, limit: int | None = None):
        self.calls += 1
        school_id = 13 if school_name == "北京邮电大学" else 14
        return [self.evidence[0].model_copy(update={"school_name": school_name, "school_id": school_id, "chunk_id": school_id})]


def evidence() -> RetrievedEvidence:
    return RetrievedEvidence(
        chunk_id=1,
        document_id=1,
        title="2026年招生专业目录",
        content="081200计算机科学与技术初试科目包含408计算机学科专业基础。",
        source_url="https://gr.xidian.edu.cn/catalog.pdf",
        school_id=9,
        school_name="西安电子科技大学",
        year=2026,
        chunk_index=1,
        score=0.03,
        parent_context="081200计算机科学与技术初试科目包含408计算机学科专业基础。",
    )


def build_graph(tmp_path: Path, items: list[RetrievedEvidence]):
    settings = Settings(data_dir=tmp_path)
    retriever = FakeRetriever(items)
    graph = AdmissionsAgentGraph(settings, retriever, FakeGenerator())
    return graph, retriever


def test_state_graph_executes_tool_and_generates_grounded_answer(tmp_path: Path):
    agent, retriever = build_graph(tmp_path, [evidence()])
    state = agent.graph.invoke({
        "messages": [],
        "question": "西安电子科技大学是否考408？",
        "allow_human_review": False,
        "attempts": 0,
        "trace": [],
    }, {"configurable": {"thread_id": "normal"}})

    assert "408 [1]" in state["answer"]
    assert state["related_school_id"] == 9
    assert any(item.startswith("tool:hybrid_retrieve") for item in state["trace"])
    assert any(item.startswith("critic:citations=1") for item in state["trace"])
    assert retriever.calls == 1


def test_human_interrupt_can_resume_and_preserves_checkpoint(tmp_path: Path):
    agent, _ = build_graph(tmp_path, [evidence()])
    config = {"configurable": {"thread_id": "review"}}
    interrupted = agent.graph.invoke({
        "messages": [],
        "question": "西安电子科技大学录取最低分是多少？",
        "allow_human_review": True,
        "attempts": 0,
        "trace": [],
    }, config)

    assert interrupted.get("__interrupt__")
    resumed = agent.graph.invoke(Command(resume={"approved": True, "feedback": "注明年份"}), config)
    assert resumed["route"] == "completed"
    assert "注明年份" in resumed["answer"]
    assert "human:approved" in resumed["trace"]


def test_human_rejection_stops_publication(tmp_path: Path):
    agent, _ = build_graph(tmp_path, [evidence()])
    config = {"configurable": {"thread_id": "reject"}}
    agent.graph.invoke({
        "messages": [],
        "question": "西安电子科技大学录取最低分是多少？",
        "allow_human_review": True,
        "attempts": 0,
        "trace": [],
    }, config)
    rejected = agent.graph.invoke(Command(resume={"approved": False, "feedback": "证据不足"}), config)

    assert rejected["route"] == "rejected"
    assert rejected["answer"] == "人工审核未通过，本次回答不发布。"


def test_planner_selects_knowledge_profile_tool(tmp_path: Path):
    agent, retriever = build_graph(tmp_path, [evidence()])
    state = agent.graph.invoke({
        "messages": [],
        "question": "西安电子科技大学知识库收录了哪些年份和资料？",
        "allow_human_review": False,
        "attempts": 0,
        "trace": [],
    }, {"configurable": {"thread_id": "coverage"}})

    assert "2 份文档、3 个切片" in state["answer"]
    assert state["retrieval_count"] == 3
    assert "tool:school_knowledge_profile" in state["trace"]
    assert retriever.calls == 0


def test_planner_prioritizes_admissions_fact_over_collected_data_wording(tmp_path: Path):
    agent, retriever = build_graph(tmp_path, [evidence()])
    state = agent.graph.invoke({
        "messages": [],
        "question": "西安电子科技大学是否采用408？请只根据已收录官方目录回答。",
        "allow_human_review": False,
        "attempts": 0,
        "trace": [],
    }, {"configurable": {"thread_id": "collected-factual-question"}})

    assert "408 [1]" in state["answer"]
    assert "tool:hybrid_retrieve" in state["trace"]
    assert retriever.calls == 1


def test_planner_decomposes_multi_school_question_into_parallel_tool_calls(tmp_path: Path):
    settings = Settings(data_dir=tmp_path)
    retriever = MultiSchoolRetriever([evidence()])
    agent = AdmissionsAgentGraph(settings, retriever, FakeGenerator())
    state = agent.graph.invoke({
        "messages": [],
        "question": "比较北京邮电大学和南京大学的408资料",
        "allow_human_review": False,
        "attempts": 0,
        "trace": [],
    }, {"configurable": {"thread_id": "multi-school"}})

    assert retriever.calls == 2
    assert {item["school_name"] for item in state["evidence"]} == {"北京邮电大学", "南京大学"}
    assert "tool:hybrid_retrieve:parallel=2" in state["trace"]


def test_unknown_school_is_guarded_without_cross_school_retrieval(tmp_path: Path):
    agent, retriever = build_graph(tmp_path, [evidence()])
    state = agent.graph.invoke({
        "messages": [],
        "question": "杭州电子科技大学计算机专业录取最低分是多少？",
        "allow_human_review": False,
        "attempts": 0,
        "trace": [],
    }, {"configurable": {"thread_id": "unknown-school"}})

    assert retriever.calls == 0
    assert state["confidence"] == 0.15
    assert "不执行跨校推断" in state["answer"]
    assert "guard:unknown_school" in state["trace"]
