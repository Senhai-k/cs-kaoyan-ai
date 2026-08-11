import { describe, expect, it } from 'vitest';
import { canSaveSourceDocumentDraft, formatSourceDocumentQualitySummary, parseSourceDocumentBatch, webCaptureDraftPatch } from './sourceDocumentBatch';

describe('source document batch helpers', () => {
  it('rejects empty, invalid and empty-array input before network requests', () => {
    expect(parseSourceDocumentBatch('')).toEqual({ ok: false, message: '请先粘贴批量导入 JSON' });
    expect(parseSourceDocumentBatch('{"title":"x"}')).toEqual({ ok: false, message: '批量导入内容必须是数组' });
    expect(parseSourceDocumentBatch('[]')).toEqual({ ok: false, message: '批量导入数组不能为空' });
    expect(parseSourceDocumentBatch('[null]')).toEqual({ ok: false, message: '数组中的每一项必须是资料对象' });
  });

  it('returns document objects for valid JSON', () => {
    const result = parseSourceDocumentBatch('[{"title":"官方目录","rawText":"原文"}]');
    expect(result.ok).toBe(true);
    if (result.ok) expect(result.documents[0].title).toBe('官方目录');
  });

  it('formats a compact quality preview', () => {
    expect(formatSourceDocumentQualitySummary({ errorCount: 1, warningCount: 2, issues: [{ index: 0, level: 'ERROR', field: 'title', message: '不能为空' }] })).toContain('1 个错误，2 个警告');
  });

  it('requires both a title and source text before saving', () => {
    expect(canSaveSourceDocumentDraft({ title: '官方目录', rawText: '目录原文' })).toBe(true);
    expect(canSaveSourceDocumentDraft({ title: ' ', rawText: '目录原文' })).toBe(false);
    expect(canSaveSourceDocumentDraft({ title: '官方目录', rawText: ' ' })).toBe(false);
  });

  it('maps a controlled web capture to an unpublished official draft', () => {
    const patch = webCaptureDraftPatch({
      captureTaskId: 8, targetId: 3, schoolId: 5, title: '复试办法', documentType: '复试细则',
      year: 2026, sourceUrl: 'https://example.edu.cn/2026/retest.html', rawText: '官方正文',
      remark: '受控采集', contentSha256: 'abc', duplicate: false, extractorVersion: 'controlled-web-v1',
      changeDetected: false, changeId: null
    });
    expect(patch).toMatchObject({ schoolId: '5', auditStatus: 'DRAFT', sourceReliability: 'OFFICIAL' });
  });
});
