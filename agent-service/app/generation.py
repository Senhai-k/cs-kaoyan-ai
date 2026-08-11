import re
import threading
from typing import Any

from langchain_core.messages import HumanMessage, SystemMessage

from .config import Settings
from .models import RetrievedEvidence


class GroundedGenerator:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._lock = threading.RLock()
        self._openai: Any = None
        self._tokenizer: Any = None
        self._local_model: Any = None

    @property
    def mode(self) -> str:
        if self.settings.openai_api_key:
            return "openai-compatible"
        if self.settings.local_llm_model:
            return "local-transformers"
        return "grounded-extractive"

    def generate(self, question: str, evidence: list[RetrievedEvidence], feedback: str = "") -> str:
        if not evidence:
            return "当前私域知识库没有检索到足以回答该问题的已发布资料，建议补充对应学校、专业和年份的官方文档。"
        prompt = self._prompt(question, evidence, feedback)
        if self.settings.openai_api_key:
            return self._generate_openai(prompt)
        if self.settings.local_llm_model:
            return self._generate_local(prompt)
        return self._extractive_answer(question, evidence)

    def _generate_openai(self, prompt: str) -> str:
        with self._lock:
            if self._openai is None:
                from langchain_openai import ChatOpenAI

                self._openai = ChatOpenAI(
                    model=self.settings.openai_model,
                    api_key=self.settings.openai_api_key,
                    base_url=self.settings.openai_base_url or None,
                    temperature=0.1,
                    timeout=self.settings.request_timeout_seconds,
                    max_retries=2,
                )
            result = self._openai.invoke([
                SystemMessage(content="你是计算机考研招生资料研究助手，只能依据给定证据回答。"),
                HumanMessage(content=prompt),
            ])
            return str(result.content).strip()

    def _generate_local(self, prompt: str) -> str:
        with self._lock:
            if self._local_model is None:
                from transformers import AutoModelForCausalLM, AutoTokenizer

                self._tokenizer = AutoTokenizer.from_pretrained(self.settings.local_llm_model)
                self._local_model = AutoModelForCausalLM.from_pretrained(
                    self.settings.local_llm_model,
                    torch_dtype="auto",
                    device_map="auto",
                )
            messages = [
                {"role": "system", "content": "你是计算机考研招生资料研究助手，只能依据给定证据回答。"},
                {"role": "user", "content": prompt},
            ]
            text = self._tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
            inputs = self._tokenizer([text], return_tensors="pt").to(self._local_model.device)
            output = self._local_model.generate(**inputs, max_new_tokens=384, do_sample=False)
            generated = output[0][inputs.input_ids.shape[1]:]
            return self._tokenizer.decode(generated, skip_special_tokens=True).strip()

    def _extractive_answer(self, question: str, evidence: list[RetrievedEvidence]) -> str:
        school_names = list(dict.fromkeys(item.school_name or "学校未标注" for item in evidence))
        if len(school_names) > 1:
            lines = ["根据已发布的私域资料，按院校整理如下："]
            indexed_evidence = list(enumerate(evidence, 1))
            for school_name in school_names:
                lines.append(f"{school_name}：")
                school_items = [item for item in indexed_evidence if (item[1].school_name or "学校未标注") == school_name]
                for citation_index, item in school_items[:2]:
                    content = focused_excerpt(question, item.content, 220)
                    lines.append(f"- {content} [{citation_index}]")
            lines.append("以上仅对比当前各校已入库资料；缺失字段不能视为学校没有对应政策或数据。")
            return "\n".join(lines)
        lines = ["根据已发布的私域资料，可确认以下信息："]
        for index, item in enumerate(evidence[:4], start=1):
            content = focused_excerpt(question, item.content, 280)
            lines.append(f"{index}. {content} [{index}]")
        lines.append("以上结论仅覆盖当前已入库资料；缺失年份或专业不能据此推断。")
        return "\n".join(lines)

    def _prompt(self, question: str, evidence: list[RetrievedEvidence], feedback: str) -> str:
        context = []
        for index, item in enumerate(evidence, start=1):
            context.append(
                f"[{index}] 学校={item.school_name or '未标注'}；年份={item.year or '未标注'}；"
                f"标题={item.title}；内容={item.parent_context or item.content}；来源={item.source_url or '未标注'}"
            )
        feedback_text = f"\n人工审核意见：{feedback}" if feedback else ""
        return (
            f"问题：{question}{feedback_text}\n\n证据：\n" + "\n".join(context)
            + "\n\n要求：只依据证据回答；每个事实使用 [n] 引用；学校、专业和年份不得混用；"
              "资料不足时明确说明，不得补造人数、分数或政策。"
        )


_FOCUS_TERMS = (
    "招生计划", "招生人数", "目录人数", "复试线", "差额比例", "复试比例",
    "成绩权重", "初试科目", "专业课", "非全日制", "全日制", "研究方向",
    "录取分数", "拟录取", "调剂", "参考书",
)


def focused_excerpt(question: str, text: str, limit: int = 280) -> str:
    normalized = re.sub(r"\s+", " ", text).strip()
    if len(normalized) <= limit:
        return normalized
    numeric_anchors = re.findall(r"(?<!\d)(?:20\d{2}|\d{4,6})(?!\d)", question)
    phrase_anchors = [term for term in _FOCUS_TERMS if term in question]
    anchors = list(dict.fromkeys(numeric_anchors + phrase_anchors))
    positions = [position for anchor in anchors for position in _find_positions(normalized, anchor)]
    anchor_index = 0
    best_score = -1
    for position in positions:
        candidate_start = max(0, position - min(80, limit // 3))
        candidate = normalized[candidate_start:candidate_start + limit]
        score = sum(2 for anchor in numeric_anchors if anchor in candidate)
        score += sum(3 for anchor in phrase_anchors if anchor in candidate)
        if score > best_score:
            best_score = score
            anchor_index = position
    start = max(0, anchor_index - min(80, limit // 3))
    end = min(len(normalized), start + limit)
    if end == len(normalized):
        start = max(0, end - limit)
    excerpt = normalized[start:end]
    return ("..." if start else "") + excerpt + ("..." if end < len(normalized) else "")


def _find_positions(text: str, value: str) -> list[int]:
    positions = []
    start = 0
    while value and (position := text.find(value, start)) >= 0:
        positions.append(position)
        start = position + len(value)
    return positions
