import json
import operator
import re
import sqlite3
from typing import Annotated, Any, TypedDict

from langchain_core.messages import AIMessage, AnyMessage, ToolMessage
from langchain_core.tools import tool
from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.graph import END, START, StateGraph, add_messages
from langgraph.prebuilt import ToolNode
from langgraph.types import interrupt

from .config import Settings
from .generation import GroundedGenerator
from .models import RetrievedEvidence
from .retrieval import HybridRetriever


class AgentState(TypedDict, total=False):
    messages: Annotated[list[AnyMessage], add_messages]
    question: str
    retrieval_query: str
    tool_name: str
    school_name: str | None
    school_names: list[str]
    unknown_school: bool
    related_school_id: int | None
    allow_human_review: bool
    evidence: list[dict[str, Any]]
    coverage: dict[str, Any]
    coverage_profiles: list[dict[str, Any]]
    answer: str
    sources: list[str]
    retrieval_count: int
    confidence: float
    route: str
    attempts: int
    feedback: str
    approved: bool
    trace: Annotated[list[str], operator.add]


class AdmissionsAgentGraph:
    def __init__(self, settings: Settings, retriever: HybridRetriever, generator: GroundedGenerator):
        self.settings = settings
        self.retriever = retriever
        self.generator = generator
        self._connection = sqlite3.connect(settings.checkpoint_path, check_same_thread=False)
        self.checkpointer = SqliteSaver(self._connection)
        self.graph = self._build_graph()

    def close(self) -> None:
        self._connection.close()

    def _build_graph(self):
        retriever = self.retriever

        @tool
        def hybrid_retrieve(query: str, school_name: str = "", limit: int = 5) -> str:
            """Search the private admissions knowledge base with vector and BM25 retrieval."""
            evidence = retriever.search(query, school_name or None, max(1, min(limit, 5)))
            return json.dumps([item.model_dump() for item in evidence], ensure_ascii=False)

        @tool
        def school_knowledge_profile(school_name: str = "") -> str:
            """Inspect private knowledge coverage, years, document types, and source count for a school."""
            return json.dumps(retriever.knowledge_profile(school_name or None), ensure_ascii=False)

        def plan(state: AgentState) -> dict[str, Any]:
            question = state["question"].strip()
            known_schools = retriever.school_names()
            school_names = [name for name in known_schools if name in question]
            school_name = school_names[0] if school_names else None
            remaining_question = question
            for name in school_names:
                remaining_question = remaining_question.replace(name, " ")
            unknown_school = bool(re.search(r"[\u4e00-\u9fff]{2,16}大学", remaining_question))
            query = question
            coverage_phrases = (
                "知识库覆盖", "覆盖情况", "收录情况", "收录了哪些", "收录多少",
                "有哪些资料", "资料完整", "资料年份", "数据情况",
            )
            admissions_fact_terms = (
                "是否", "408", "初试", "科目", "分数", "复试", "录取", "调剂",
                "招生人数", "专业代码",
            )
            coverage_intent = (
                any(term in question for term in coverage_phrases)
                and not any(term in question for term in admissions_fact_terms)
            )
            tool_name = "school_knowledge_profile" if coverage_intent else "hybrid_retrieve"
            return {
                "retrieval_query": query,
                "school_name": school_name,
                "school_names": school_names,
                "unknown_school": unknown_school,
                "tool_name": tool_name,
                "attempts": state.get("attempts", 0),
                "trace": [
                    f"plan:tool={tool_name}:schools={','.join(school_names) or 'ALL'}:unknown={str(unknown_school).lower()}"
                ],
            }

        def prepare_tool(state: AgentState) -> dict[str, Any]:
            tool_name = state.get("tool_name", "hybrid_retrieve")
            targets = state.get("school_names") or [""]
            limit = 3 if len(targets) > 1 else 5
            tool_calls = []
            for index, school_name in enumerate(targets):
                args: dict[str, Any] = {"school_name": school_name}
                if tool_name == "hybrid_retrieve":
                    args.update({"query": state.get("retrieval_query", state["question"]), "limit": limit})
                tool_calls.append({
                    "name": tool_name,
                    "args": args,
                    "id": f"retrieve-{state.get('attempts', 0)}-{index}",
                    "type": "tool_call",
                })
            trace = f"tool:{tool_name}" + (f":parallel={len(tool_calls)}" if len(tool_calls) > 1 else "")
            return {"messages": [AIMessage(content="", tool_calls=tool_calls)], "trace": [trace]}

        def parse_tool_result(state: AgentState) -> dict[str, Any]:
            expected_results = max(1, len(state.get("school_names") or []))
            tool_messages = [
                message for message in reversed(state.get("messages", [])) if isinstance(message, ToolMessage)
            ][:expected_results]
            tool_messages.reverse()
            if not tool_messages:
                return {"evidence": [], "trace": ["tool_result:missing"]}
            payloads = []
            for tool_message in tool_messages:
                try:
                    payloads.append(json.loads(str(tool_message.content)))
                except json.JSONDecodeError:
                    payloads.append({} if state.get("tool_name") == "school_knowledge_profile" else [])
            if state.get("tool_name") == "school_knowledge_profile":
                profiles = [payload for payload in payloads if isinstance(payload, dict)]
                coverage = profiles[0] if profiles else {}
                return {
                    "coverage": coverage,
                    "coverage_profiles": profiles,
                    "related_school_id": coverage.get("school_id"),
                    "trace": [f"tool_result:coverage:{sum(int(item.get('chunks', 0)) for item in profiles)}"],
                }
            evidence = []
            seen_chunks = set()
            for payload in payloads:
                if not isinstance(payload, list):
                    continue
                for item in payload:
                    chunk_id = item.get("chunk_id")
                    if chunk_id in seen_chunks:
                        continue
                    seen_chunks.add(chunk_id)
                    evidence.append(item)
            related_school_id = evidence[0].get("school_id") if evidence else None
            return {
                "evidence": evidence,
                "related_school_id": related_school_id,
                "trace": [f"tool_result:{len(evidence)}"],
            }

        def assess(state: AgentState) -> dict[str, Any]:
            if state.get("tool_name") == "school_knowledge_profile":
                chunks = int(state.get("coverage", {}).get("chunks", 0))
                confidence = 0.95 if chunks else 0.4
                return {"route": "coverage", "confidence": confidence, "trace": [f"assess:coverage:{confidence:.2f}"]}
            evidence = state.get("evidence", [])
            attempts = state.get("attempts", 0)
            if not evidence and attempts < 1:
                return {"route": "rewrite", "confidence": 0.0, "trace": ["assess:rewrite"]}
            exact_school = bool(state.get("school_name")) and any(
                item.get("school_name") == state.get("school_name") for item in evidence
            )
            confidence = min(0.95, 0.25 + len(evidence) * 0.12 + (0.2 if exact_school else 0.0))
            sensitive = any(word in state["question"] for word in ("人数", "分数", "最低", "录取", "调剂"))
            if state.get("allow_human_review") and (confidence < 0.6 or (sensitive and confidence < 0.75)):
                route = "human"
            else:
                route = "generate"
            return {"route": route, "confidence": confidence, "trace": [f"assess:{route}:{confidence:.2f}"]}

        def rewrite(state: AgentState) -> dict[str, Any]:
            replacements = {
                "分数": "复试线 录取最低分 平均分",
                "人数": "招生人数 统考名额 拟招生人数",
                "科目": "初试科目 专业课 408",
                "复试": "复试细则 差额比例 成绩权重",
            }
            query = state["question"]
            additions = [expanded for word, expanded in replacements.items() if word in query]
            if additions:
                query = f"{query} {' '.join(additions)}"
            return {
                "retrieval_query": query,
                "attempts": state.get("attempts", 0) + 1,
                "trace": ["rewrite:query_expansion"],
            }

        def human_review(state: AgentState) -> dict[str, Any]:
            decision = interrupt({
                "reason": "证据置信度不足或问题涉及高风险招生数值",
                "question": state["question"],
                "confidence": state.get("confidence", 0.0),
                "sources": [self._citation(item, index) for index, item in enumerate(state.get("evidence", []), 1)],
            })
            approved = bool(decision.get("approved")) if isinstance(decision, dict) else False
            feedback = str(decision.get("feedback") or "") if isinstance(decision, dict) else ""
            if not approved:
                return {
                    "approved": False,
                    "feedback": feedback,
                    "answer": "人工审核未通过，本次回答不发布。",
                    "route": "rejected",
                    "trace": ["human:rejected"],
                }
            return {"approved": True, "feedback": feedback, "trace": ["human:approved"]}

        def after_human(state: AgentState) -> str:
            return "generate" if state.get("approved") else "end"

        def generate(state: AgentState) -> dict[str, Any]:
            evidence = [RetrievedEvidence.model_validate(item) for item in state.get("evidence", [])]
            answer = self.generator.generate(state["question"], evidence, state.get("feedback", ""))
            sources = [self._citation(item.model_dump(), index) for index, item in enumerate(evidence, 1)]
            return {"answer": answer, "sources": sources, "route": "completed", "trace": [f"generate:{self.generator.mode}"]}

        def generate_coverage(state: AgentState) -> dict[str, Any]:
            profiles = state.get("coverage_profiles") or [state.get("coverage", {})]
            answer_lines = []
            sources = []
            source_index = 1
            for profile in profiles:
                school_name = profile.get("school_name") or "全部院校"
                years = "、".join(str(item) for item in profile.get("years", [])) or "未标注"
                types = "、".join(
                    f"{name} {count} 条" for name, count in profile.get("document_types", {}).items()
                ) or "暂无"
                if not profile.get("chunks"):
                    answer_lines.append(f"{school_name}：暂未收录已发布资料。")
                else:
                    answer_lines.append(
                        f"{school_name}：{profile.get('documents', 0)} 份文档、{profile.get('chunks', 0)} 个切片；"
                        f"年份 {years}；类型 {types}。"
                    )
                for item in profile.get("sources", []):
                    sources.append(
                        f"[{source_index}] {school_name} / {item.get('year') or '年份未标注'} / {item.get('title')}"
                        + (f" / {item.get('url')}" if item.get("url") else "")
                    )
                    source_index += 1
            answer = "私域知识库覆盖情况：\n" + "\n".join(answer_lines)
            answer += "\n该统计只反映当前已入库资料，不代表学校官网全部历史数据。"
            if sources and profile.get("chunks"):
                answer += "代表性入库来源见 [1]。"
            return {
                "answer": answer,
                "sources": sources,
                "route": "completed",
                "retrieval_count": sum(int(item.get("chunks", 0)) for item in profiles),
                "trace": ["generate:knowledge_profile"],
            }

        def generate_unknown_school(state: AgentState) -> dict[str, Any]:
            return {
                "answer": "问题中包含私域知识库尚未收录的院校。为避免把其他学校的招生政策混入回答，本次不执行跨校推断；请先补充该校官方招生目录、复试细则或录取公示。",
                "sources": [],
                "retrieval_count": 0,
                "confidence": 0.15,
                "route": "completed",
                "trace": ["guard:unknown_school"],
            }

        def critic(state: AgentState) -> dict[str, Any]:
            answer = state.get("answer", "")
            evidence = state.get("evidence", [])
            sources = state.get("sources", [])
            citation_count = len(set(int(item) for item in __import__("re").findall(r"\[(\d+)]", answer)))
            valid = not (evidence or sources) or citation_count > 0 or state.get("route") == "rejected"
            return {
                "trace": [f"critic:citations={citation_count}:valid={str(valid).lower()}"],
                "confidence": state.get("confidence", 0.0) if valid else max(0.0, state.get("confidence", 0.0) - 0.2),
            }

        builder = StateGraph(AgentState)
        builder.add_node("plan", plan)
        builder.add_node("prepare_tool", prepare_tool)
        builder.add_node("tools", ToolNode([hybrid_retrieve, school_knowledge_profile]))
        builder.add_node("parse_tool_result", parse_tool_result)
        builder.add_node("assess", assess)
        builder.add_node("rewrite", rewrite)
        builder.add_node("human_review", human_review)
        builder.add_node("generate", generate)
        builder.add_node("generate_coverage", generate_coverage)
        builder.add_node("generate_unknown_school", generate_unknown_school)
        builder.add_node("critic", critic)
        builder.add_edge(START, "plan")
        builder.add_conditional_edges("plan", lambda state: "unknown" if state.get("unknown_school") else "retrieve", {
            "unknown": "generate_unknown_school", "retrieve": "prepare_tool",
        })
        builder.add_edge("prepare_tool", "tools")
        builder.add_edge("tools", "parse_tool_result")
        builder.add_edge("parse_tool_result", "assess")
        builder.add_conditional_edges("assess", lambda state: state["route"], {
            "rewrite": "rewrite", "human": "human_review", "generate": "generate",
            "coverage": "generate_coverage",
        })
        builder.add_edge("rewrite", "prepare_tool")
        builder.add_conditional_edges("human_review", after_human, {"generate": "generate", "end": END})
        builder.add_edge("generate", "critic")
        builder.add_edge("generate_coverage", "critic")
        builder.add_edge("generate_unknown_school", "critic")
        builder.add_edge("critic", END)
        return builder.compile(checkpointer=self.checkpointer)

    @staticmethod
    def _citation(item: dict[str, Any], index: int) -> str:
        title = item.get("title") or f"资料 {item.get('document_id')}"
        school = item.get("school_name") or "学校未标注"
        year = item.get("year") or "年份未标注"
        url = item.get("source_url") or ""
        return f"[{index}] {school} / {year} / {title}" + (f" / {url}" if url else "")
