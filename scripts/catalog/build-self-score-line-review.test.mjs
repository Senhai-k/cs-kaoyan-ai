import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const reviewed = JSON.parse(await readFile(resolve(root, 'database', 'self-score-lines-2026-reviewed.json'), 'utf8'));
const sources = JSON.parse(await readFile(resolve(root, 'database', 'self-score-lines-2026-sources.json'), 'utf8'));

test('reviewed self-score-line batch covers all 34 schools with valid source hashes', () => {
  assert.deepEqual(reviewed.stats, { schools: 34, available: 33, unavailable: 1 });
  assert.equal(new Set(reviewed.records.map((record) => record.schoolName)).size, 34);
  const sourceBySchool = new Map(sources.records.map((record) => [record.schoolName, record]));
  for (const record of reviewed.records) {
    const source = sourceBySchool.get(record.schoolName);
    assert.ok(source, `missing source for ${record.schoolName}`);
    assert.equal(record.articleUrl, source.articleUrl);
    assert.equal(record.articleSha256, source.articleSha256);
    assert.ok(source.images.some((image) => image.imageUrl === record.imageUrl && image.sha256 === record.imageSha256));
  }
  assert.equal(reviewed.batchSha256,
    createHash('sha256').update(JSON.stringify(reviewed.records)).digest('hex'));
});

test('available records have one score representation and unavailable records remain empty', () => {
  for (const record of reviewed.records) {
    const specific = [record.politicsScore, record.foreignLanguageScore, record.subjectOneScore, record.subjectTwoScore];
    const generic = [record.score100, record.scoreOver100];
    if (record.availabilityStatus === 'AVAILABLE') {
      assert.ok(record.totalScore >= 200 && record.totalScore <= 500);
      assert.notEqual(specific.every((value) => value !== null), generic.every((value) => value !== null));
    } else {
      assert.equal(record.schoolName, '武汉大学');
      assert.equal(record.totalScore, null);
      assert.ok([...specific, ...generic].every((value) => value === null));
    }
  }
});
