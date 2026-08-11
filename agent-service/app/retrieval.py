import json
import math
import re
import threading
import uuid
from collections import defaultdict
from pathlib import Path
from typing import Iterable

import httpx
from qdrant_client import QdrantClient, models
from rank_bm25 import BM25Okapi
from sentence_transformers import CrossEncoder, SentenceTransformer

from .config import Settings
from .models import IndexSyncResult, KnowledgeDocument, RetrievedEvidence


COLLECTION_NAME = "kaoyan_private_knowledge"


def tokenize(text: str) -> list[str]:
    normalized = re.sub(r"\s+", "", text.lower())
    chinese = re.findall(r"[\u4e00-\u9fff]", normalized)
    bigrams = ["".join(chinese[index:index + 2]) for index in range(max(0, len(chinese) - 1))]
    latin = re.findall(r"[a-z0-9]+", text.lower())
    return chinese + bigrams + latin


class HybridRetriever:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._lock = threading.RLock()
        self._embedder: SentenceTransformer | None = None
        self._reranker: CrossEncoder | None = None
        self._qdrant: QdrantClient | None = None
        self._corpus: list[KnowledgeDocument] = []
        self._by_chunk_id: dict[int, KnowledgeDocument] = {}
        self._by_document_id: dict[int, list[KnowledgeDocument]] = defaultdict(list)
        self._bm25: BM25Okapi | None = None
        self._load_corpus()

    @property
    def embedding_model_name(self) -> str:
        return self.settings.embedding_model

    @property
    def indexed_chunks(self) -> int:
        return len(self._corpus)

    def corpus_snapshot(self) -> list[KnowledgeDocument]:
        with self._lock:
            return [item.model_copy(deep=True) for item in self._corpus]

    @property
    def reranker_enabled(self) -> bool:
        return self.settings.enable_reranker and self.settings.reranker_mode != "off"

    @property
    def reranker_mode(self) -> str:
        return self.settings.reranker_mode if self.reranker_enabled else "off"

    def _embedding(self) -> SentenceTransformer:
        if self._embedder is None:
            self._embedder = SentenceTransformer(self.settings.embedding_model)
        return self._embedder

    def _cross_encoder(self) -> CrossEncoder | None:
        if not self.reranker_enabled or self.settings.reranker_mode != "cross-encoder":
            return None
        if self._reranker is None:
            self._reranker = CrossEncoder(self.settings.reranker_model)
        return self._reranker

    def _client(self) -> QdrantClient:
        if self._qdrant is None:
            self.settings.qdrant_path.parent.mkdir(parents=True, exist_ok=True)
            self._qdrant = QdrantClient(path=str(self.settings.qdrant_path))
        return self._qdrant

    def close(self) -> None:
        if self._qdrant is not None:
            self._qdrant.close()
            self._qdrant = None

    def sync_from_spring(self) -> IndexSyncResult:
        with httpx.Client(base_url=self.settings.spring_base_url,
                          timeout=self.settings.request_timeout_seconds) as client:
            schools_payload = client.get("/api/schools").raise_for_status().json()
            schools = {item["id"]: item["name"] for item in schools_payload.get("data", [])}
            documents_payload = client.get("/api/source-documents").raise_for_status().json()
            source_documents = documents_payload.get("data", [])
            corpus: list[KnowledgeDocument] = []
            for document in source_documents:
                chunks_payload = client.get(f"/api/source-documents/{document['id']}/chunks").raise_for_status().json()
                for chunk in chunks_payload.get("data", []):
                    content = str(chunk.get("content") or "").strip()
                    if not content:
                        continue
                    school_id = chunk.get("schoolId")
                    corpus.append(KnowledgeDocument(
                        chunk_id=int(chunk["id"]),
                        document_id=int(document["id"]),
                        title=str(document.get("title") or f"资料 {document['id']}"),
                        content=content,
                        source_url=document.get("sourceUrl"),
                        school_id=school_id,
                        school_name=schools.get(school_id),
                        college_id=chunk.get("collegeId"),
                        major_id=chunk.get("majorId"),
                        year=chunk.get("year"),
                        document_type=chunk.get("documentType"),
                        chunk_index=int(chunk.get("chunkIndex") or 0),
                    ))
        self.index(corpus)
        return IndexSyncResult(
            documents=len({item.document_id for item in corpus}),
            chunks=len(corpus),
            schools=len({item.school_id for item in corpus if item.school_id is not None}),
            collection=COLLECTION_NAME,
            embedding_model=self.settings.embedding_model,
        )

    def index(self, corpus: list[KnowledgeDocument]) -> None:
        if not corpus:
            raise ValueError("knowledge corpus is empty")
        with self._lock:
            vectors = self._embedding().encode(
                [f"为这个句子生成表示以用于检索相关文章：{item.content}" for item in corpus],
                normalize_embeddings=True,
                show_progress_bar=False,
                batch_size=32,
            )
            client = self._client()
            vector_size = int(vectors.shape[1])
            if client.collection_exists(COLLECTION_NAME):
                client.delete_collection(COLLECTION_NAME)
            client.create_collection(
                collection_name=COLLECTION_NAME,
                vectors_config=models.VectorParams(size=vector_size, distance=models.Distance.COSINE),
            )
            points = []
            for item, vector in zip(corpus, vectors):
                point_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f"kaoyan-chunk:{item.chunk_id}"))
                points.append(models.PointStruct(
                    id=point_id,
                    vector=vector.tolist(),
                    payload=item.model_dump(),
                ))
            client.upsert(collection_name=COLLECTION_NAME, points=points, wait=True)
            self._write_corpus(corpus)
            self._set_corpus(corpus)

    def search(self, question: str, school_name: str | None = None,
               limit: int | None = None, apply_reranker: bool = True) -> list[RetrievedEvidence]:
        with self._lock:
            if not self._corpus:
                return []
            result_limit = limit or self.settings.retrieval_limit
            candidates = self.settings.candidate_limit
            query_vector = self._embedding().encode(
                f"为这个句子生成表示以用于检索相关文章：{question}",
                normalize_embeddings=True,
                show_progress_bar=False,
            ).tolist()
            document_type = self._intent_document_type(question, school_name)
            filter_conditions = []
            if school_name:
                filter_conditions.append(
                    models.FieldCondition(key="school_name", match=models.MatchValue(value=school_name))
                )
            if document_type:
                filter_conditions.append(
                    models.FieldCondition(key="document_type", match=models.MatchValue(value=document_type))
                )
            query_filter = models.Filter(must=filter_conditions) if filter_conditions else None
            vector_hits = self._client().query_points(
                collection_name=COLLECTION_NAME,
                query=query_vector,
                query_filter=query_filter,
                limit=candidates,
                with_payload=True,
            ).points
            vector_ranks: dict[int, tuple[int, float]] = {}
            for rank, hit in enumerate(vector_hits, start=1):
                chunk_id = int((hit.payload or {}).get("chunk_id"))
                if chunk_id not in self._by_chunk_id:
                    continue
                vector_ranks[chunk_id] = (rank, float(hit.score))

            lexical_ranks = self._lexical_ranks(question, school_name, document_type, candidates)
            fused: dict[int, dict[str, float]] = defaultdict(lambda: {
                "score": 0.0, "vector_score": 0.0, "lexical_score": 0.0
            })
            for chunk_id, (rank, score) in vector_ranks.items():
                fused[chunk_id]["score"] += 1.0 / (60 + rank)
                fused[chunk_id]["vector_score"] = score
            for chunk_id, (rank, score) in lexical_ranks.items():
                fused[chunk_id]["score"] += 1.0 / (60 + rank)
                fused[chunk_id]["lexical_score"] = score

            ranked_ids = sorted(fused, key=lambda item: fused[item]["score"], reverse=True)[:candidates]
            rerank_scores = self._rerank(question, ranked_ids, fused) if apply_reranker else {}
            if rerank_scores:
                ranked_ids.sort(key=lambda item: rerank_scores.get(item, -math.inf), reverse=True)
            evidence: list[RetrievedEvidence] = []
            for chunk_id in ranked_ids[:result_limit]:
                document = self._by_chunk_id[chunk_id]
                parent_context = self._parent_context(document)
                evidence.append(RetrievedEvidence(
                    **document.model_dump(),
                    score=fused[chunk_id]["score"],
                    vector_score=fused[chunk_id]["vector_score"],
                    lexical_score=fused[chunk_id]["lexical_score"],
                    rerank_score=rerank_scores.get(chunk_id, 0.0),
                    parent_context=parent_context,
                ))
            return evidence

    def school_names(self) -> list[str]:
        return sorted({item.school_name for item in self._corpus if item.school_name}, key=len, reverse=True)

    def knowledge_profile(self, school_name: str | None = None) -> dict[str, object]:
        with self._lock:
            documents = [
                item for item in self._corpus
                if not school_name or item.school_name == school_name
            ]
            type_counts: dict[str, int] = defaultdict(int)
            for item in documents:
                type_counts[item.document_type or "未分类"] += 1
            source_items = []
            seen_documents: set[int] = set()
            for item in sorted(documents, key=lambda value: (value.year or 0, value.title), reverse=True):
                if item.document_id in seen_documents:
                    continue
                seen_documents.add(item.document_id)
                source_items.append({
                    "title": item.title,
                    "year": item.year,
                    "url": item.source_url,
                })
                if len(source_items) >= 5:
                    break
            return {
                "school_name": school_name,
                "school_id": next((item.school_id for item in documents if item.school_id is not None), None),
                "documents": len({item.document_id for item in documents}),
                "chunks": len(documents),
                "years": sorted({item.year for item in documents if item.year is not None}, reverse=True),
                "document_types": dict(sorted(type_counts.items())),
                "sources": source_items,
                "total_schools": len(self.school_names()) if not school_name else 1,
            }

    def _lexical_ranks(self, question: str, school_name: str | None, document_type: str | None,
                       limit: int) -> dict[int, tuple[int, float]]:
        if self._bm25 is None:
            return {}
        scores = self._bm25.get_scores(tokenize(question))
        ranked = sorted(range(len(scores)), key=lambda index: scores[index], reverse=True)
        result: dict[int, tuple[int, float]] = {}
        for index in ranked:
            document = self._corpus[index]
            if school_name and document.school_name != school_name:
                continue
            if document_type and document.document_type != document_type:
                continue
            if scores[index] <= 0 and result:
                break
            result[document.chunk_id] = (len(result) + 1, float(scores[index]))
            if len(result) >= limit:
                break
        return result

    @staticmethod
    def _intent_document_type(question: str, school_name: str | None) -> str | None:
        if not school_name and "国家线" in question:
            return "国家线"
        return None

    def _rerank(self, question: str, chunk_ids: list[int],
                fused: dict[int, dict[str, float]]) -> dict[int, float]:
        if not self.reranker_enabled or not chunk_ids:
            return {}
        reranker = self._cross_encoder()
        if reranker is not None:
            scores = reranker.predict([(question, self._by_chunk_id[item].content) for item in chunk_ids])
            return {chunk_id: float(score) for chunk_id, score in zip(chunk_ids, scores)}
        return {
            chunk_id: self._feature_rerank_score(
                question,
                self._by_chunk_id[chunk_id],
                fused[chunk_id]["vector_score"],
                fused[chunk_id]["lexical_score"],
                rank,
                len(chunk_ids),
            )
            for rank, chunk_id in enumerate(chunk_ids, 1)
        }

    @staticmethod
    def _feature_rerank_score(question: str, document: KnowledgeDocument,
                              vector_score: float, lexical_score: float,
                              fused_rank: int = 1, candidate_count: int = 1) -> float:
        query_tokens = set(tokenize(question))
        document_tokens = set(tokenize(f"{document.title} {document.content}"))
        searchable_text = f"{document.title} {document.content}"
        coverage = len(query_tokens & document_tokens) / max(1, len(query_tokens))
        lexical_normalized = 1.0 - math.exp(-max(0.0, lexical_score) / 10.0)
        school_match = 1.0 if document.school_name and document.school_name in question else 0.0
        query_codes = {
            code for code in re.findall(r"(?<!\d)\d{4,6}(?!\d)", question)
            if not re.fullmatch(r"20\d{2}", code)
        }
        code_match = (
            sum(code in searchable_text for code in query_codes) / len(query_codes)
            if query_codes else 0.0
        )
        query_years = set(re.findall(r"20\d{2}", question))
        year_match = 1.0 if document.year and str(document.year) in query_years else 0.0
        college_match = HybridRetriever._college_match(question, document, searchable_text)
        section_match = HybridRetriever._section_intent_match(question, searchable_text)
        rank_prior = 1.0 - (max(1, fused_rank) - 1) / max(1, candidate_count)
        return round(
            max(0.0, vector_score) * 0.25
            + lexical_normalized * 0.17
            + coverage * 0.13
            + school_match * 0.06
            + code_match * 0.22
            + year_match * 0.03
            + college_match * 0.05
            + section_match * 0.07
            + rank_prior * 0.02,
            6,
        )

    @staticmethod
    def _college_match(question: str, document: KnowledgeDocument, searchable_text: str) -> float:
        scoped_question = question.replace(document.school_name or "", " ")
        college_phrases = re.findall(
            r"[\u4e00-\u9fff]{2,16}(?:学院|学部|研究院)", scoped_question
        )
        return 1.0 if college_phrases and any(phrase in searchable_text for phrase in college_phrases) else 0.0

    @staticmethod
    def _section_intent_match(question: str, searchable_text: str) -> float:
        normalized = re.sub(r"\s+", "", searchable_text)
        if any(intent in question for intent in ("招生计划", "招生人数")):
            if re.search(r"[一二三四五六七八九十]+[、.．]招生计划", normalized):
                return 1.0
            if "专业代码专业名称招生人数" in normalized:
                return 1.0
        if any(intent in question for intent in ("复试线", "分数线")):
            if "专业代码专业名称政治外语业务课1业务课2总分" in normalized:
                return 1.0
        return 0.0

    def _parent_context(self, document: KnowledgeDocument) -> str:
        siblings = sorted(self._by_document_id[document.document_id], key=lambda item: item.chunk_index)
        current = document.content[:2400]
        remaining = max(0, 2400 - len(current) - 2)
        previous = next(
            (item.content for item in reversed(siblings) if item.chunk_index < document.chunk_index),
            "",
        )
        following = next(
            (item.content for item in siblings if item.chunk_index > document.chunk_index),
            "",
        )
        previous_budget = remaining // 2 if following else remaining
        following_budget = remaining - min(len(previous), previous_budget)
        parts = []
        if previous:
            parts.append(previous[-previous_budget:] if previous_budget else "")
        parts.append(current)
        if following and following_budget:
            parts.append(following[:following_budget])
        return "\n".join(item for item in parts if item)[:2400]

    def _load_corpus(self) -> None:
        path = self.settings.corpus_path
        if not path.exists():
            return
        raw = json.loads(path.read_text(encoding="utf-8"))
        self._set_corpus([KnowledgeDocument.model_validate(item) for item in raw])

    def _write_corpus(self, corpus: Iterable[KnowledgeDocument]) -> None:
        path = self.settings.corpus_path
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = Path(f"{path}.tmp")
        temporary.write_text(
            json.dumps([item.model_dump() for item in corpus], ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        temporary.replace(path)

    def _set_corpus(self, corpus: list[KnowledgeDocument]) -> None:
        self._corpus = corpus
        self._by_chunk_id = {item.chunk_id: item for item in corpus}
        self._by_document_id = defaultdict(list)
        for item in corpus:
            self._by_document_id[item.document_id].append(item)
        tokenized = [tokenize(item.content) or [str(item.chunk_id)] for item in corpus]
        self._bm25 = BM25Okapi(tokenized) if tokenized else None
