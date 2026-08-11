import { createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const FORBIDDEN_FIELDS = new Set([
  'name', 'candidateName', 'examineeName', 'examId', 'admissionTicket',
  'admissionTicketNumber', 'idCard', 'phone', 'email'
]);

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function requiredText(value, label) {
  if (typeof value !== 'string' || !value.trim()) throw new Error(`缺少${label}`);
  return value.trim();
}

function optionalText(value) {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

function validateScore(value, max, label) {
  if (value == null) return null;
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > max) {
    throw new Error(`${label}必须在 0-${max} 之间`);
  }
  return value;
}

function rejectForbiddenFields(value, path = '$') {
  if (Array.isArray(value)) {
    value.forEach((item, index) => rejectForbiddenFields(item, `${path}[${index}]`));
    return;
  }
  if (!value || typeof value !== 'object') return;
  for (const [key, child] of Object.entries(value)) {
    if (FORBIDDEN_FIELDS.has(key)) throw new Error(`${path}.${key} 禁止写入姓名、考号或联系方式`);
    rejectForbiddenFields(child, `${path}.${key}`);
  }
}

export function prepareAdmissionResultBatch(input, sourceBytes, salt) {
  rejectForbiddenFields(input);
  if (typeof salt !== 'string' || salt.length < 16) throw new Error('ADMISSION_IMPORT_SALT 至少16位');
  if (!Buffer.isBuffer(sourceBytes) || sourceBytes.length === 0) throw new Error('官方名单原文件不能为空');
  if (input?.schemaVersion !== 1) throw new Error('仅支持 schemaVersion=1');
  if (!Number.isInteger(input.schoolId) || input.schoolId <= 0) throw new Error('schoolId 必须是正整数');
  if (!Number.isInteger(input.sourceId) || input.sourceId <= 0) throw new Error('sourceId 必须是正整数');
  if (!Number.isInteger(input.year) || input.year < 2000 || input.year > 2100) throw new Error('year 必须在 2000-2100 之间');
  if (input.documentType !== '拟录取名单') throw new Error('documentType 必须为拟录取名单');
  if (!Array.isArray(input.records) || input.records.length === 0 || input.records.length > 10_000) {
    throw new Error('records 必须包含 1-10000 条记录');
  }

  const candidateKeys = new Set();
  const records = input.records.map((record, index) => {
    const candidateRef = requiredText(record.candidateRef, `records[${index}].candidateRef`);
    const candidateKey = sha256(`${salt}\0${input.schoolId}\0${input.year}\0${candidateRef}`);
    if (candidateKeys.has(candidateKey)) throw new Error(`records[${index}] 匿名候选人重复`);
    candidateKeys.add(candidateKey);
    return {
      candidateKey,
      collegeName: requiredText(record.collegeName, `records[${index}].collegeName`),
      majorCode: requiredText(record.majorCode, `records[${index}].majorCode`),
      majorName: optionalText(record.majorName),
      degreeType: requiredText(record.degreeType, `records[${index}].degreeType`),
      studyMode: requiredText(record.studyMode, `records[${index}].studyMode`),
      candidateType: requiredText(record.candidateType, `records[${index}].candidateType`),
      initialScore: validateScore(record.initialScore, 500, '初试成绩'),
      retestScore: validateScore(record.retestScore, 500, '复试成绩'),
      finalScore: validateScore(record.finalScore, 500, '总成绩'),
      specialProgram: optionalText(record.specialProgram)
    };
  });

  const payload = {
    schemaVersion: 1,
    schoolId: input.schoolId,
    year: input.year,
    documentType: '拟录取名单',
    sourceId: input.sourceId,
    sourceSha256: sha256(sourceBytes),
    remark: optionalText(input.remark),
    records
  };
  const batchSha256 = sha256(Buffer.from(JSON.stringify(payload), 'utf8'));
  const output = { ...payload, batchSha256 };
  validateAnonymousBatch(output);
  return output;
}

export function validateAnonymousBatch(batch) {
  rejectForbiddenFields(batch);
  if (!Array.isArray(batch?.records) || batch.records.length === 0) throw new Error('匿名批次没有记录');
  const keys = new Set();
  for (const [index, record] of batch.records.entries()) {
    if ('candidateRef' in record) throw new Error(`records[${index}] 仍包含 candidateRef`);
    if (typeof record.candidateKey !== 'string' || !/^[0-9a-f]{64}$/.test(record.candidateKey)) {
      throw new Error(`records[${index}].candidateKey 不是小写 SHA-256`);
    }
    if (keys.has(record.candidateKey)) throw new Error(`records[${index}] 匿名候选人重复`);
    keys.add(record.candidateKey);
  }
  return true;
}

function parseArgs(argv) {
  const values = {};
  for (let index = 0; index < argv.length; index += 2) values[argv[index]] = argv[index + 1];
  if (!values['--input'] || !values['--source'] || !values['--output']) {
    throw new Error('用法: node prepare-admission-result-batch.mjs --input <模板.json> --source <官方名单文件> --output <匿名批次.json>');
  }
  return values;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const inputPath = resolve(args['--input']);
  const sourcePath = resolve(args['--source']);
  const outputPath = resolve(args['--output']);
  const input = JSON.parse(await readFile(inputPath, 'utf8'));
  const sourceBytes = await readFile(sourcePath);
  const batch = prepareAdmissionResultBatch(input, sourceBytes, process.env.ADMISSION_IMPORT_SALT);
  await writeFile(outputPath, `${JSON.stringify(batch, null, 2)}\n`, 'utf8');
  process.stdout.write(`匿名批次已生成: ${outputPath}\n记录数: ${batch.records.length}\n批次 SHA-256: ${batch.batchSha256}\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
