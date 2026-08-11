import type { AdmissionResultImportRequest } from './types';

const FORBIDDEN_FIELDS = new Set([
  'name', 'candidateName', 'examineeName', 'examId', 'admissionTicket',
  'admissionTicketNumber', 'idCard', 'phone', 'email', 'candidateRef'
]);

function inspectFields(value: unknown, path = '$') {
  if (Array.isArray(value)) {
    value.forEach((item, index) => inspectFields(item, `${path}[${index}]`));
    return;
  }
  if (!value || typeof value !== 'object') return;
  Object.entries(value).forEach(([key, child]) => {
    if (FORBIDDEN_FIELDS.has(key)) throw new Error(`${path}.${key} 包含未匿名化字段`);
    inspectFields(child, `${path}.${key}`);
  });
}

export function parseAdmissionResultImport(text: string): AdmissionResultImportRequest {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Error('文件不是有效 JSON');
  }
  inspectFields(parsed);
  if (!parsed || typeof parsed !== 'object') throw new Error('批次内容必须是对象');
  const batch = parsed as Partial<AdmissionResultImportRequest>;
  if (batch.schemaVersion !== 1 || batch.documentType !== '拟录取名单') throw new Error('批次版本或资料类型不正确');
  if (!Number.isInteger(batch.schoolId) || !Number.isInteger(batch.sourceId) || !Number.isInteger(batch.year)) {
    throw new Error('学校、来源和年份必须是整数');
  }
  if (!/^[0-9a-f]{64}$/.test(batch.sourceSha256 ?? '') || !/^[0-9a-f]{64}$/.test(batch.batchSha256 ?? '')) {
    throw new Error('来源或批次 SHA-256 不正确');
  }
  if (!Array.isArray(batch.records) || batch.records.length === 0) throw new Error('批次没有候选人记录');
  const keys = new Set<string>();
  batch.records.forEach((record, index) => {
    if (!/^[0-9a-f]{64}$/.test(record.candidateKey ?? '')) throw new Error(`第 ${index + 1} 条候选人键不正确`);
    if (keys.has(record.candidateKey)) throw new Error(`第 ${index + 1} 条候选人重复`);
    keys.add(record.candidateKey);
  });
  return batch as AdmissionResultImportRequest;
}
