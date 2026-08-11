import { afterEach, describe, expect, it, vi } from 'vitest';
import { requestJson } from './api';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('requestJson', () => {
  it('returns a structured successful response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ code: 200, message: 'success', data: { count: 10 } }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )));

    await expect(requestJson<{ count: number }>('/api/test')).resolves.toEqual({
      code: 200,
      message: 'success',
      data: { count: 10 }
    });
  });

  it('reports an empty successful response without leaking a JSON syntax error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 200 })));

    await expect(requestJson('/api/test')).rejects.toThrow('空响应: 200');
  });

  it('uses the API message for structured failures', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ code: 500, message: '数据查询失败', data: null }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )));

    await expect(requestJson('/api/test')).rejects.toThrow('数据查询失败');
  });

  it('normalizes non-JSON failures', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('gateway error', { status: 502 })));

    await expect(requestJson('/api/test')).rejects.toThrow('请求失败: 502');
  });
});
