import { describe, expect, it } from 'vitest';
import { adminErrorMessage, adminSuccessMessage } from './adminFeedback';

describe('admin feedback', () => {
  it('describes common mutations', () => {
    expect(adminSuccessMessage('POST', '/api/schools')).toBe('数据已新增');
    expect(adminSuccessMessage('PUT', '/api/schools/1')).toBe('修改已保存');
    expect(adminSuccessMessage('DELETE', '/api/schools/1')).toContain('已删除');
  });

  it('uses specific document workflow messages', () => {
    expect(adminSuccessMessage('POST', '/api/source-documents/quality-check')).toContain('预检');
    expect(adminSuccessMessage('POST', '/api/source-documents/1/chunks')).toContain('切片');
    expect(adminSuccessMessage('POST', '/api/source-documents/publication-batches')).toContain('批次已发布');
    expect(adminSuccessMessage('POST', '/api/source-documents/publication-batches/1/rollback')).toContain('原子回滚');
    expect(adminSuccessMessage('POST', '/api/source-documents/web-capture-changes/1/review')).toContain('变化已复核');
  });

  it('prefers backend errors and falls back by status', () => {
    expect(adminErrorMessage(400, { message: '标题不能为空' })).toBe('标题不能为空');
    expect(adminErrorMessage(404, null)).toContain('不存在');
    expect(adminErrorMessage(500, null)).toContain('HTTP 500');
  });
});
