import { createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');

function sha256(value) {
  return createHash('sha256').update(value, 'utf8').digest('hex');
}

function stableJson(value) {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableJson(value[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}

function sanitizeText(value) {
  return value
    .replace(/[\p{Script=Han}]{1,4}老师/gu, '[联系人已移除]')
    .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/giu, '[公开邮箱已移除]')
    .replace(/(联系电话|咨询电话|联系方式|电话|传真)\s*[:：]?\s*(?:\+?86[-\s]?)?[\d()—\-\s]{7,}/gu, '$1：[公开电话已移除]')
    .replace(/(?<!\d)(?:\+?86[-\s]?)?1[3-9]\d{9}(?!\d)/gu, '[公开电话已移除]')
    .replace(/(?<!\d)0\d{2,3}[-—\s]?\d{7,8}(?!\d)/gu, '[公开电话已移除]');
}

function sanitizeValue(value) {
  if (typeof value === 'string') return sanitizeText(value);
  if (Array.isArray(value)) return value.map(sanitizeValue);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, child]) => [
      key,
      /sha256$/iu.test(key) ? child : sanitizeValue(child),
    ]));
  }
  return value;
}

async function sanitizeSelfScoreLines() {
  const path = resolve(root, 'database', 'self-score-lines-2026-sources.json');
  const payload = JSON.parse(await readFile(path, 'utf8'));
  payload.records = sanitizeValue(payload.records);
  payload.sha256 = sha256(JSON.stringify(payload.records));
  await writeFile(path, `${JSON.stringify(payload, null, 2)}\n`, 'utf8');
  return payload.sha256;
}

async function sanitizeCatalog() {
  const path = resolve(root, 'database', 'catalog-408-2026.json');
  const payload = JSON.parse(await readFile(path, 'utf8'));
  payload.records = sanitizeValue(payload.records);
  for (const record of payload.records) {
    if (record.source?.rawEvidence) {
      record.source.sha256 = sha256(stableJson(record.source.rawEvidence));
    }
  }
  delete payload.sha256;
  payload.sha256 = sha256(stableJson(payload));
  await writeFile(path, `${JSON.stringify(payload, null, 2)}\n`, 'utf8');
  return payload.sha256;
}

const sourceBatchSha256 = await sanitizeSelfScoreLines();
const catalogBatchSha256 = await sanitizeCatalog();
process.stdout.write(`${JSON.stringify({ sourceBatchSha256, catalogBatchSha256 })}\n`);
