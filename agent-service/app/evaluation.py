import json
import re
import uuid
from collections import defaultdict
from pathlib import Path

from .graph import AdmissionsAgentGraph
from .models import EvaluationResult
from .operation_control import CancelCheck, ProgressCallback, ensure_not_cancelled, report_progress
from .retrieval import HybridRetriever


def run_evaluation(
    agent: AdmissionsAgentGraph,
    retriever: HybridRetriever,
    cases_path: Path,
    progress: ProgressCallback | None = None,
    cancel_check: CancelCheck | None = None,
) -> EvaluationResult:
    cases = json.loads(cases_path.read_text(encoding="utf-8"))
    details = []
    recall_hits = 0
    target_recall_at_1_hits = 0
    target_count = 0
    hit_at_1_cases = 0
    reciprocal_rank_sum = 0.0
    baseline_reciprocal_rank_sum = 0.0
    citation_hits = 0
    citation_grounded_hits = 0
    citation_source_url_hits = 0
    answer_support_hits = 0
    school_scope_hits = 0
    completed = 0
    retrieval_cases = 0
    safety_cases = 0
    safety_hits = 0
    category_totals: dict[str, int] = defaultdict(int)
    category_hits: dict[str, int] = defaultdict(int)
    failed_case_ids: list[str] = []
    total = len(cases)
    report_progress(progress, 0, total, "准备 RAG 评估")
    for index, case in enumerate(cases, 1):
        ensure_not_cancelled(cancel_check)
        case_type = str(case.get("type") or ("guard" if case.get("expectedGuard") else "retrieval"))
        guard_case = case_type == "guard"
        category = str(case.get("category") or "baseline")
        expected_texts = _expected_texts(case, "expectedTexts", "expectedText")
        requested_schools = _expected_texts(case, "schools", "school")
        expected_targets = _expected_targets(case, expected_texts, requested_schools)
        relevant_rank = None
        baseline_rank = None
        relevant_ranks: list[int | None] = []
        baseline_ranks: list[int | None] = []
        local_relevant_ranks: list[int | None] = []
        baseline_local_ranks: list[int | None] = []
        target_matches: list[dict] = []
        recall = None
        recall_at_1 = None
        school_scope_valid = True
        if case_type == "retrieval":
            retrieval_cases += 1
            evidence, local_evidence = _search_case(retriever, case["question"], requested_schools, True)
            baseline_evidence, baseline_local_evidence = _search_case(
                retriever, case["question"], requested_schools, False
            )
            target_matches = [
                _target_match(target, evidence, local_evidence, baseline_evidence, baseline_local_evidence)
                for target in expected_targets
            ]
            relevant_ranks = [item["globalRank"] for item in target_matches]
            baseline_ranks = [item["baselineGlobalRank"] for item in target_matches]
            local_relevant_ranks = [item["localRank"] for item in target_matches]
            baseline_local_ranks = [item["baselineLocalRank"] for item in target_matches]
            relevant_rank = next((rank for rank in relevant_ranks if rank is not None), None)
            baseline_rank = next((rank for rank in baseline_ranks if rank is not None), None)
            recall = bool(expected_targets) and all(rank is not None for rank in local_relevant_ranks)
            recall_at_1 = bool(expected_targets) and any(rank == 1 for rank in local_relevant_ranks)
            target_top_1 = sum(rank == 1 for rank in local_relevant_ranks)
            target_count += len(expected_targets)
            target_recall_at_1_hits += target_top_1
            hit_at_1_cases += int(recall_at_1)
            recall_hits += int(recall)
            reciprocal_rank_sum += _mean_reciprocal_rank(local_relevant_ranks)
            baseline_reciprocal_rank_sum += _mean_reciprocal_rank(baseline_local_ranks)
            school_scope_valid = not requested_schools or all(
                item.school_name in requested_schools for item in evidence
            )
            school_scope_hits += int(school_scope_valid)
        state = agent.graph.invoke({
            "messages": [], "question": case["question"], "allow_human_review": False,
            "attempts": 0, "trace": [],
        }, {"configurable": {"thread_id": f"eval-{uuid.uuid4()}"}})
        trace = state.get("trace", [])
        answer = str(state.get("answer") or "")
        sources = state.get("sources", [])
        status = "COMPLETED" if state.get("route") == "completed" else "FAILED"
        expected_answer_texts = _expected_texts(case, "expectedAnswerTexts", "expectedAnswerText")
        if not expected_answer_texts and case_type == "retrieval":
            expected_answer_texts = expected_texts
        answer_supported = all(text in answer for text in expected_answer_texts)
        forbidden_answer_texts = _expected_texts(case, "forbiddenAnswerTexts", "forbiddenAnswerText")
        answer_supported = answer_supported and not any(text in answer for text in forbidden_answer_texts)
        expected_trace = _expected_texts(case, "expectedTrace", "expectedTraceItem")
        trace_valid = all(any(expected in item for item in trace) for expected in expected_trace)
        guard_valid = guard_case and "guard:unknown_school" in trace and answer_supported
        if guard_case:
            safety_cases += 1
            safety_hits += int(guard_valid)
        citation_indices = [int(item) for item in re.findall(r"\[(\d+)]", answer)]
        valid_citations = (
            guard_case and not sources and not citation_indices
            or bool(sources) and bool(citation_indices) and all(source.startswith("[") for source in sources)
        )
        citation_grounded = valid_citations and all(
            1 <= citation <= len(sources) and sources[citation - 1].startswith(f"[{citation}]")
            for citation in citation_indices
        )
        cited_sources = [
            sources[citation - 1]
            for citation in citation_indices
            if 1 <= citation <= len(sources)
        ]
        citation_source_url_valid = (
            guard_case and not sources and not citation_indices
            or bool(cited_sources) and all(
                re.search(r"https?://\S+", source) is not None for source in cited_sources
            )
        )
        citation_hits += int(valid_citations)
        citation_grounded_hits += int(citation_grounded)
        citation_source_url_hits += int(citation_source_url_valid)
        answer_support_hits += int(answer_supported)
        case_success = (
            status == "COMPLETED" and answer_supported and trace_valid and citation_grounded
            and citation_source_url_valid
            and school_scope_valid and (not guard_case or guard_valid)
        )
        completed += int(case_success)
        category_totals[category] += 1
        category_hits[category] += int(case_success)
        if not case_success:
            failed_case_ids.append(str(case["id"]))
        details.append({
            "id": case["id"], "category": category, "type": case_type,
            "recall": recall, "recallAt1": recall_at_1, "relevantRank": relevant_rank,
            "relevantRanks": relevant_ranks, "baselineRank": baseline_rank,
            "baselineRanks": baseline_ranks, "localRelevantRanks": local_relevant_ranks,
            "baselineLocalRanks": baseline_local_ranks,
            "targetRecallAt1": (
                round(sum(rank == 1 for rank in local_relevant_ranks) / len(local_relevant_ranks), 4)
                if local_relevant_ranks else None
            ),
            "expectedTargets": target_matches,
            "rankingDiagnostics": _ranking_diagnostics(evidence, local_evidence, expected_targets)
            if case_type == "retrieval" else [],
            "baselineRankingDiagnostics": _ranking_diagnostics(
                baseline_evidence, baseline_local_evidence, expected_targets
            ) if case_type == "retrieval" else [],
            "guardValid": guard_valid if guard_case else None,
            "citationValid": valid_citations, "citationGrounded": citation_grounded,
            "citationSourceUrlValid": citation_source_url_valid,
            "answerSupported": answer_supported, "schoolScopeValid": school_scope_valid,
            "traceValid": trace_valid, "status": status, "success": case_success,
        })
        report_progress(progress, index, total, str(case["id"]))
    baseline_mrr = baseline_reciprocal_rank_sum / retrieval_cases if retrieval_cases else 0.0
    reranked_mrr = reciprocal_rank_sum / retrieval_cases if retrieval_cases else 0.0
    return EvaluationResult(
        cases=total,
        recall_at_5=round(recall_hits / retrieval_cases, 4) if retrieval_cases else 0.0,
        recall_at_1=round(target_recall_at_1_hits / target_count, 4) if target_count else 0.0,
        target_recall_at_1=round(target_recall_at_1_hits / target_count, 4) if target_count else 0.0,
        hit_rate_at_1=round(hit_at_1_cases / retrieval_cases, 4) if retrieval_cases else 0.0,
        baseline_mean_reciprocal_rank_at_5=round(baseline_mrr, 4),
        mean_reciprocal_rank_at_5=round(reranked_mrr, 4),
        rerank_mrr_lift=round(reranked_mrr - baseline_mrr, 4),
        boundary_safety_rate=round(safety_hits / safety_cases, 4) if safety_cases else 0.0,
        citation_validity=round(citation_hits / total, 4) if total else 0.0,
        citation_groundedness=round(citation_grounded_hits / total, 4) if total else 0.0,
        citation_source_url_rate=round(citation_source_url_hits / total, 4) if total else 0.0,
        answer_support_rate=round(answer_support_hits / total, 4) if total else 0.0,
        school_scope_accuracy=round(school_scope_hits / retrieval_cases, 4) if retrieval_cases else 1.0,
        task_completion_rate=round(completed / total, 4) if total else 0.0,
        category_scores={
            category: round(category_hits[category] / count, 4)
            for category, count in sorted(category_totals.items())
        },
        failed_case_ids=failed_case_ids,
        details=details,
    )


def _expected_texts(case: dict, plural_key: str, singular_key: str) -> list[str]:
    values = case.get(plural_key)
    if isinstance(values, list):
        return [str(item) for item in values if str(item)]
    value = case.get(singular_key)
    return [str(value)] if value else []


def _expected_targets(case: dict, expected_texts: list[str], schools: list[str]) -> list[dict[str, str | None]]:
    configured = case.get("expectedTargets")
    if isinstance(configured, list):
        targets = []
        for item in configured:
            if not isinstance(item, dict) or not str(item.get("text") or ""):
                continue
            targets.append({
                "text": str(item["text"]),
                "school": str(item["school"]) if item.get("school") else None,
            })
        if targets:
            return targets
    if len(schools) > 1 and len(schools) == len(expected_texts):
        return [{"text": text, "school": school} for text, school in zip(expected_texts, schools)]
    school = schools[0] if len(schools) == 1 else None
    return [{"text": text, "school": school} for text in expected_texts]


def _search_case(
    retriever: HybridRetriever,
    question: str,
    schools: list[str],
    apply_reranker: bool,
) -> tuple[list, dict[str, list]]:
    targets = schools or [""]
    limit = 3 if len(targets) > 1 else 5
    evidence = []
    local_evidence: dict[str, list] = {}
    seen_chunks = set()
    for school in targets:
        school_evidence = retriever.search(question, school or None, limit, apply_reranker)
        local_evidence[school] = school_evidence
        for item in school_evidence:
            if item.chunk_id not in seen_chunks:
                evidence.append(item)
                seen_chunks.add(item.chunk_id)
    return evidence, local_evidence


def _rank_for_text(evidence: list, expected_text: str) -> int | None:
    return next(
        (rank for rank, item in enumerate(evidence, 1) if expected_text in item.content),
        None,
    )


def _rank_for_target(evidence: list, expected_text: str, school: str | None) -> int | None:
    return next(
        (rank for rank, item in enumerate(evidence, 1)
         if expected_text in item.content and (not school or item.school_name == school)),
        None,
    )


def _target_match(
    target: dict[str, str | None],
    evidence: list,
    local_evidence: dict[str, list],
    baseline_evidence: list,
    baseline_local_evidence: dict[str, list],
) -> dict:
    text = str(target["text"])
    school = target.get("school")
    scoped = local_evidence.get(school, evidence) if school else evidence
    baseline_scoped = baseline_local_evidence.get(school, baseline_evidence) if school else baseline_evidence
    global_rank = _rank_for_target(evidence, text, school)
    local_rank = _rank_for_text(scoped, text)
    matched = evidence[global_rank - 1] if global_rank else None
    return {
        "text": text,
        "school": school,
        "globalRank": global_rank,
        "localRank": local_rank,
        "baselineGlobalRank": _rank_for_target(baseline_evidence, text, school),
        "baselineLocalRank": _rank_for_text(baseline_scoped, text),
        "chunkId": matched.chunk_id if matched else None,
        "documentId": matched.document_id if matched else None,
    }


def _ranking_diagnostics(evidence: list, local_evidence: dict[str, list], targets: list[dict]) -> list[dict]:
    diagnostics = []
    for global_rank, item in enumerate(evidence, 1):
        school_key = item.school_name or ""
        local_rank = next(
            (rank for rank, candidate in enumerate(local_evidence.get(school_key, evidence), 1)
             if candidate.chunk_id == item.chunk_id),
            global_rank,
        )
        matched_targets = [
            index for index, target in enumerate(targets)
            if (not target.get("school") or target["school"] == item.school_name)
            and str(target["text"]) in item.content
        ]
        diagnostics.append({
            "globalRank": global_rank,
            "localRank": local_rank,
            "chunkId": item.chunk_id,
            "documentId": item.document_id,
            "title": item.title,
            "school": item.school_name,
            "vectorScore": round(item.vector_score, 6),
            "lexicalScore": round(item.lexical_score, 6),
            "fusedScore": round(item.score, 6),
            "rerankScore": round(item.rerank_score, 6),
            "matchedTargetIndexes": matched_targets,
        })
    return diagnostics


def _mean_reciprocal_rank(ranks: list[int | None]) -> float:
    return sum(1.0 / rank if rank else 0.0 for rank in ranks) / len(ranks) if ranks else 0.0
