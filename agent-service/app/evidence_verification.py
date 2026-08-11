from __future__ import annotations

import re
from typing import Annotated, Any, TypedDict
from urllib.parse import urlparse

import operator
from langgraph.graph import END, START, StateGraph


class EvidenceVerificationState(TypedDict, total=False):
    candidate: dict[str, Any]
    school_name: str
    checks: Annotated[list[dict[str, Any]], operator.add]
    quality_score: int
    accepted: bool
    reasons: list[str]
    trace: Annotated[list[str], operator.add]


class EvidenceVerificationGraph:
    DOCUMENT_TYPE_KEYWORDS = {
        "招生专业目录": ("招生专业目录", "招生目录", "专业目录", "考试科目"),
        "复试录取细则": ("复试", "录取"),
        "复试录取规定": ("复试", "录取"),
        "复试录取方案": ("复试", "录取"),
        "复试分数线": ("复试线", "分数线", "复试基本要求"),
        "拟录取名单": ("拟录取", "录取名单"),
        "招生简章": ("招生简章", "硕士研究生招生"),
        "招生简章与专业目录": ("招生简章", "招生专业目录", "专业目录"),
        "考试科目调整入口": ("考试科目调整", "考试科目"),
        "官方招生快照": ("研究生招生", "硕士研究生"),
    }

    def __init__(self):
        self.graph = self._build_graph()

    def verify(self, candidate: dict[str, Any], school_name: str) -> dict[str, Any]:
        state = self.graph.invoke({
            "candidate": candidate,
            "school_name": school_name,
            "checks": [],
            "trace": [],
        })
        result = dict(candidate)
        result["status"] = "VERIFIED" if state["accepted"] else "REJECTED"
        result["quality_score"] = state["quality_score"]
        result["verification_checks"] = state["checks"]
        result["verification_trace"] = state["trace"]
        if not state["accepted"]:
            result["reason"] = "；".join(state["reasons"])
        return result

    def _build_graph(self):
        def provenance(state: EvidenceVerificationState) -> dict[str, Any]:
            candidate = state["candidate"]
            url = str(candidate.get("source_url") or "")
            parsed = urlparse(url)
            hostname = (parsed.hostname or "").lower()
            official = (
                parsed.scheme in {"http", "https"}
                and (hostname.endswith(".edu.cn") or hostname == "yz.chsi.com.cn" or hostname.endswith(".gov.cn"))
            )
            exact_article = bool(parsed.path.rstrip("/")) and not parsed.path.lower().rstrip("/").endswith(
                ("/main.htm", "/index.htm", "/list.htm")
            )
            passed = official and exact_article
            return {
                "checks": [_check("official_article", passed, 25, True, hostname or "missing host")],
                "trace": [f"verifier:provenance={'pass' if passed else 'fail'}"],
            }

        def scope(state: EvidenceVerificationState) -> dict[str, Any]:
            candidate = state["candidate"]
            document = candidate.get("document") or {}
            raw_text = str(document.get("rawText") or "")
            searchable = raw_text
            school_name = state["school_name"]
            year = int(candidate.get("target_year") or document.get("year") or 0)
            document_type = str(candidate.get("document_type") or document.get("documentType") or "")
            keywords = self.DOCUMENT_TYPE_KEYWORDS.get(document_type, (document_type,))
            school_match = bool(school_name and school_name in searchable)
            detected_years = re.findall(r"(?<!\d)20\d{2}(?!\d)", searchable)
            year_match = bool(year and detected_years and detected_years[0] == str(year))
            type_match = any(keyword and keyword in searchable for keyword in keywords)
            return {
                "checks": [
                    _check("school_scope", school_match, 20, True, school_name),
                    _check(
                        "year_scope", year_match, 15, True,
                        f"expected={year}, first={detected_years[0] if detected_years else 'missing'}",
                    ),
                    _check("document_type", type_match, 15, True, document_type),
                ],
                "trace": [
                    f"verifier:scope=school:{int(school_match)},year:{int(year_match)},type:{int(type_match)}"
                ],
            }

        def content_quality(state: EvidenceVerificationState) -> dict[str, Any]:
            document = state["candidate"].get("document") or {}
            raw_text = re.sub(r"\s+", " ", str(document.get("rawText") or "")).strip()
            content_length = len(raw_text)
            enough_content = content_length >= 120
            tokens = re.findall(r"[\w\u4e00-\u9fff]{2,}", raw_text)
            diversity = len(set(tokens)) / len(tokens) if tokens else 0.0
            diverse_content = diversity >= 0.08
            return {
                "checks": [
                    _check("content_length", enough_content, 15, True, f"{content_length} chars"),
                    _check("content_diversity", diverse_content, 10, False, f"{diversity:.3f}"),
                ],
                "trace": [f"verifier:content=length:{content_length},diversity:{diversity:.3f}"],
            }

        def decide(state: EvidenceVerificationState) -> dict[str, Any]:
            checks = state.get("checks", [])
            score = sum(int(item["weight"]) for item in checks if item["passed"])
            required_failures = [item for item in checks if item["required"] and not item["passed"]]
            accepted = not required_failures and score >= 70
            reasons = [f"{item['name']} 未通过（{item['detail']}）" for item in checks if not item["passed"]]
            return {
                "quality_score": score,
                "accepted": accepted,
                "reasons": reasons,
                "trace": [f"verifier:decision={'accepted' if accepted else 'rejected'}:score={score}"],
            }

        builder = StateGraph(EvidenceVerificationState)
        builder.add_node("provenance", provenance)
        builder.add_node("scope", scope)
        builder.add_node("content_quality", content_quality)
        builder.add_node("decide", decide)
        builder.add_edge(START, "provenance")
        builder.add_edge("provenance", "scope")
        builder.add_edge("scope", "content_quality")
        builder.add_edge("content_quality", "decide")
        builder.add_edge("decide", END)
        return builder.compile()


def _check(name: str, passed: bool, weight: int, required: bool, detail: str) -> dict[str, Any]:
    return {
        "name": name,
        "passed": passed,
        "weight": weight,
        "required": required,
        "detail": detail,
    }
