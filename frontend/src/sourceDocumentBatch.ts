import type { SourceDocumentRequestLike, WebCaptureDraft } from './types';

export type SourceDocumentBatchParseResult =
  | { ok: true; documents: SourceDocumentRequestLike[] }
  | { ok: false; message: string };

export function parseSourceDocumentBatch(input: string): SourceDocumentBatchParseResult {
  if (!input.trim()) return { ok: false, message: '请先粘贴批量导入 JSON' };
  let value: unknown;
  try {
    value = JSON.parse(input);
  } catch {
    return { ok: false, message: 'JSON 格式不正确' };
  }
  if (!Array.isArray(value)) return { ok: false, message: '批量导入内容必须是数组' };
  if (value.length === 0) return { ok: false, message: '批量导入数组不能为空' };
  if (value.some((item) => item === null || typeof item !== 'object' || Array.isArray(item))) {
    return { ok: false, message: '数组中的每一项必须是资料对象' };
  }
  return { ok: true, documents: value as SourceDocumentRequestLike[] };
}

export function formatSourceDocumentQualitySummary(report: { errorCount: number; warningCount: number; issues: Array<{ index: number; level: string; field: string; message: string }> }) {
  const preview = report.issues.slice(0, 3).map((issue) => `#${issue.index + 1} ${issue.level} ${issue.field}: ${issue.message}`).join('；');
  return `预检完成：${report.errorCount} 个错误，${report.warningCount} 个警告。${preview}`;
}

export function canSaveSourceDocumentDraft(document: { title: string; rawText: string }) {
  return Boolean(document.title.trim() && document.rawText.trim());
}

export function webCaptureDraftPatch(draft: WebCaptureDraft) {
  return {
    schoolId: String(draft.schoolId),
    collegeId: '',
    majorId: '',
    title: draft.title,
    documentType: draft.documentType,
    sourceUrl: draft.sourceUrl,
    year: String(draft.year),
    auditStatus: 'DRAFT',
    sourceReliability: 'OFFICIAL',
    rawText: draft.rawText,
    remark: draft.remark
  };
}
