import assert from 'node:assert/strict';
import test from 'node:test';
import { prepareAdmissionResultBatch, validateAnonymousBatch } from './prepare-admission-result-batch.mjs';

const baseInput = {
  schemaVersion: 1,
  schoolId: 1,
  year: 2026,
  documentType: '拟录取名单',
  sourceId: 20,
  remark: '官方名单匿名结构化',
  records: [{
    candidateRef: 'private-exam-id-001',
    collegeName: '计算机学院',
    majorCode: '081200',
    majorName: '计算机科学与技术',
    degreeType: '学硕',
    studyMode: '全日制',
    candidateType: '普通计划',
    initialScore: 380,
    retestScore: 86.5,
    finalScore: 82.1,
    specialProgram: null
  }]
};

test('generates deterministic anonymous output without private reference', () => {
  const batch = prepareAdmissionResultBatch(baseInput, Buffer.from('official-list'), 'private-salt-2026');
  const repeated = prepareAdmissionResultBatch(baseInput, Buffer.from('official-list'), 'private-salt-2026');

  assert.equal(batch.records[0].candidateKey.length, 64);
  assert.equal(batch.records[0].candidateKey, repeated.records[0].candidateKey);
  assert.equal(batch.batchSha256, repeated.batchSha256);
  assert.equal('candidateRef' in batch.records[0], false);
  assert.equal(JSON.stringify(batch).includes('private-exam-id-001'), false);
  assert.equal(validateAnonymousBatch(batch), true);
});

test('different salts cannot be linked by candidate key', () => {
  const first = prepareAdmissionResultBatch(baseInput, Buffer.from('official-list'), 'private-salt-2026');
  const second = prepareAdmissionResultBatch(baseInput, Buffer.from('official-list'), 'another-salt-2026');
  assert.notEqual(first.records[0].candidateKey, second.records[0].candidateKey);
});

test('rejects duplicate candidates before writing output', () => {
  const input = { ...baseInput, records: [baseInput.records[0], { ...baseInput.records[0] }] };
  assert.throws(
    () => prepareAdmissionResultBatch(input, Buffer.from('official-list'), 'private-salt-2026'),
    /匿名候选人重复/
  );
});

test('rejects explicit personal fields and weak salt', () => {
  const withName = { ...baseInput, records: [{ ...baseInput.records[0], candidateName: '不应保留' }] };
  assert.throws(
    () => prepareAdmissionResultBatch(withName, Buffer.from('official-list'), 'private-salt-2026'),
    /禁止写入/
  );
  assert.throws(
    () => prepareAdmissionResultBatch(baseInput, Buffer.from('official-list'), 'short'),
    /至少16位/
  );
});
