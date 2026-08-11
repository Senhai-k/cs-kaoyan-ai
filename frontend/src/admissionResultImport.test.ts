import { describe, expect, it } from 'vitest';
import { parseAdmissionResultImport } from './admissionResultImport';

const valid = {
  schemaVersion: 1,
  schoolId: 1,
  year: 2026,
  documentType: '拟录取名单',
  sourceId: 2,
  sourceSha256: 'a'.repeat(64),
  batchSha256: 'b'.repeat(64),
  remark: null,
  records: [{ candidateKey: 'c'.repeat(64), collegeName: '计算机学院', majorCode: '081200', majorName: '计算机科学与技术', degreeType: '学硕', studyMode: '全日制', candidateType: '普通计划', initialScore: 380, retestScore: null, finalScore: null, specialProgram: null }]
};

describe('parseAdmissionResultImport', () => {
  it('accepts an anonymous batch', () => {
    expect(parseAdmissionResultImport(JSON.stringify(valid)).records).toHaveLength(1);
  });

  it('rejects temporary candidate references and personal fields', () => {
    expect(() => parseAdmissionResultImport(JSON.stringify({ ...valid, records: [{ ...valid.records[0], candidateRef: 'raw-id' }] }))).toThrow('未匿名化字段');
    expect(() => parseAdmissionResultImport(JSON.stringify({ ...valid, records: [{ ...valid.records[0], candidateName: '张三' }] }))).toThrow('未匿名化字段');
  });

  it('rejects malformed or duplicate candidate hashes', () => {
    expect(() => parseAdmissionResultImport(JSON.stringify({ ...valid, records: [{ ...valid.records[0], candidateKey: 'bad' }] }))).toThrow('候选人键');
    expect(() => parseAdmissionResultImport(JSON.stringify({ ...valid, records: [valid.records[0], valid.records[0]] }))).toThrow('候选人重复');
  });

  it('rejects invalid json and empty records', () => {
    expect(() => parseAdmissionResultImport('{')).toThrow('有效 JSON');
    expect(() => parseAdmissionResultImport(JSON.stringify({ ...valid, records: [] }))).toThrow('没有候选人记录');
  });
});
