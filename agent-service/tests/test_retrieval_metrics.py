import time

from app.metrics import MetricsStore
from app.models import KnowledgeDocument
from app.models import RetrievedEvidence
from app.config import Settings
from app.generation import GroundedGenerator, focused_excerpt
from app.retrieval import HybridRetriever, tokenize


def test_chinese_tokenizer_keeps_terms_and_bigrams():
    tokens = tokenize("西安电子科技大学 408复试线")
    assert "408" in tokens
    assert "西安" in tokens
    assert "复试" in tokens


def test_metrics_expose_completion_and_tool_success_rates(tmp_path):
    store = MetricsStore(tmp_path / "metrics.sqlite3")
    store.record("a", "COMPLETED", time.perf_counter() - 0.01, 1, 1)
    store.record("b", "FAILED", time.perf_counter() - 0.01, 1, 0)

    summary = store.summary()
    assert summary.total_tasks == 2
    assert summary.task_completion_rate == 0.5
    assert summary.tool_success_rate == 0.5


def test_human_resume_counts_as_one_completed_task(tmp_path):
    store = MetricsStore(tmp_path / "metrics.sqlite3")
    store.record("review", "WAITING_HUMAN", time.perf_counter() - 0.01, 1, 1)
    store.record("review", "COMPLETED", time.perf_counter() - 0.01, 0, 0)

    summary = store.summary()
    assert summary.total_tasks == 1
    assert summary.completed_tasks == 1
    assert summary.waiting_tasks == 0
    assert summary.task_completion_rate == 1.0


def test_feature_reranker_rewards_exact_school_year_and_major_code():
    exact = KnowledgeDocument(
        chunk_id=1, document_id=1, title="2026年专业目录", content="085404计算机技术初试科目为408",
        school_name="北京邮电大学", year=2026,
    )
    generic = KnowledgeDocument(
        chunk_id=2, document_id=2, title="计算机专业目录", content="计算机相关专业初试科目信息",
        school_name="其他学校", year=2025,
    )
    question = "北京邮电大学2026年085404初试科目"

    exact_score = HybridRetriever._feature_rerank_score(question, exact, 0.7, 5.0)
    generic_score = HybridRetriever._feature_rerank_score(question, generic, 0.7, 5.0)
    assert exact_score > generic_score


def test_feature_reranker_keeps_a_strong_fused_rank_as_tie_breaker():
    document = KnowledgeDocument(
        chunk_id=1, document_id=1, title="复试方案", content="081200复试线382",
        school_name="浙江大学", year=2026,
    )
    first = HybridRetriever._feature_rerank_score(
        "浙江大学081200复试线", document, 0.8, 5.0, 1, 10
    )
    fifth = HybridRetriever._feature_rerank_score(
        "浙江大学081200复试线", document, 0.8, 5.0, 5, 10
    )
    assert first > fifth


def test_feature_reranker_prioritizes_exact_major_code_over_vector_neighbor():
    exact = KnowledgeDocument(
        chunk_id=1, document_id=1, title="计算机学院085411专业目录",
        content="专业：085411 大数据技术与工程；初试科目为408", school_name="北京理工大学",
    )
    neighbor = KnowledgeDocument(
        chunk_id=2, document_id=2, title="计算机学院085404专业目录",
        content="专业：085404 计算机技术；初试科目为408", school_name="北京理工大学",
    )
    question = "北京理工大学计算机学院085411的目录记录是什么？"

    exact_score = HybridRetriever._feature_rerank_score(question, exact, 0.0, 51.3, 2, 5)
    neighbor_score = HybridRetriever._feature_rerank_score(question, neighbor, 0.62, 46.8, 1, 5)

    assert exact_score > neighbor_score


def test_feature_reranker_rewards_explicit_college_phrase():
    college = KnowledgeDocument(
        chunk_id=1, document_id=1, title="计算机学院专业目录", content="计算机学院招生目录",
        school_name="测试大学",
    )
    other = college.model_copy(update={
        "chunk_id": 2, "document_id": 2, "title": "网络空间安全学院专业目录", "content": "网络空间安全学院招生目录",
    })
    question = "测试大学计算机学院的初试科目是什么？"

    assert HybridRetriever._feature_rerank_score(question, college, 0.5, 5.0) > HybridRetriever._feature_rerank_score(
        question, other, 0.5, 5.0
    )


def test_feature_reranker_rewards_structured_admission_plan_section():
    plan = KnowledgeDocument(
        chunk_id=1, document_id=1, title="复试录取方案",
        content="四、招生计划 专业代码 专业名称 招生人数 085400 电子信息 102", school_name="浙江大学",
    )
    score_line = plan.model_copy(update={
        "chunk_id": 2, "content": "二、复试分数线 085400 电子信息 377，根据实际招生计划确定名单",
    })
    question = "浙江大学085400电子信息复试阶段招生计划是多少？"

    assert HybridRetriever._feature_rerank_score(question, plan, 0.67, 36.8, 2, 5) > HybridRetriever._feature_rerank_score(
        question, score_line, 0.70, 52.9, 1, 5
    )


def test_feature_reranker_rewards_structured_score_line_table_over_summary():
    table = KnowledgeDocument(
        chunk_id=1, document_id=1, title="复试录取方案",
        content="二、复试分数线 专业代码 专业名称 政治 外语 业务课 1 业务课 2 总分 "
                "081200 计算机科学与技术 50 50 75 75 382，复试比例为1:1.3",
        school_name="浙江大学",
    )
    summary = table.model_copy(update={
        "chunk_id": 2,
        "content": "浙江大学081200复试线总分382，按照1:1.3差额复试，统考阶段计划9人",
    })
    question = "浙江大学081200复试线及复试比例分别是多少？"

    assert HybridRetriever._feature_rerank_score(question, table, 0.69, 82.4, 1, 5) > HybridRetriever._feature_rerank_score(
        question, summary, 0.76, 78.3, 2, 5
    )


def test_focused_excerpt_keeps_target_after_long_document_prefix():
    text = (
        "复试安排" * 30 + "复试线 085400 电子信息 377 " + "其他说明" * 30
        + "招生计划 专业代码 085400 电子信息 102 计算机技术方向" + "其他说明" * 40
    )

    excerpt = focused_excerpt("浙江大学085400招生计划是多少？", text, 180)

    assert "085400 电子信息 102" in excerpt
    assert "招生计划" in excerpt


def test_parent_context_preserves_the_full_matching_chunk():
    previous = KnowledgeDocument(
        chunk_id=1, document_id=7, title="方案", content="前文" * 900, chunk_index=0
    )
    current = KnowledgeDocument(
        chunk_id=2, document_id=7, title="方案",
        content="招生计划" + "表格" * 180 + "085400 电子信息 102", chunk_index=1,
    )
    following = KnowledgeDocument(
        chunk_id=3, document_id=7, title="方案", content="后文" * 900, chunk_index=2
    )
    retriever = HybridRetriever.__new__(HybridRetriever)
    retriever._by_document_id = {7: [previous, current, following]}

    context = retriever._parent_context(current)

    assert "招生计划" in context
    assert "085400 电子信息 102" in context
    assert len(context) <= 2400


def test_extractive_generator_groups_multi_school_evidence(tmp_path):
    generator = GroundedGenerator(Settings(data_dir=tmp_path))
    evidence = [
        RetrievedEvidence(
            chunk_id=1, document_id=1, title="北邮目录", content="北邮085404初试科目包含408。",
            school_name="北京邮电大学", year=2026, score=0.9,
        ),
        RetrievedEvidence(
            chunk_id=2, document_id=2, title="南大资料", content="南大当前资料未包含具体专业录取分数。",
            school_name="南京大学", year=2026, score=0.8,
        ),
    ]

    answer = generator.generate("比较北京邮电大学和南京大学", evidence)
    assert "北京邮电大学：" in answer
    assert "南京大学：" in answer
    assert "[1]" in answer and "[2]" in answer
def test_national_line_intent_filters_only_unscoped_questions():
    assert HybridRetriever._intent_document_type("2026年工学国家线是多少", None) == "国家线"
    assert HybridRetriever._intent_document_type("浙江大学比国家线高多少", "浙江大学") is None
    assert HybridRetriever._intent_document_type("浙江大学复试线是多少", None) is None

