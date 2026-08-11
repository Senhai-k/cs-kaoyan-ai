export type ApiResponse<T> = { code: number; message: string; data: T };

export async function requestJson<T>(input: RequestInfo | URL, init?: RequestInit): Promise<ApiResponse<T>> {
  const response = await fetch(input, init);
  const text = await response.text();
  let payload: ApiResponse<T> | null = null;
  if (text) {
    try {
      payload = JSON.parse(text) as ApiResponse<T>;
    } catch {
      throw new Error(response.ok ? `响应格式错误: ${response.status}` : `请求失败: ${response.status}`);
    }
  }
  if (!response.ok) {
    const message = payload && typeof payload === 'object' && 'message' in payload
      ? String(payload.message)
      : `请求失败: ${response.status}`;
    throw new Error(message);
  }
  if (!payload) throw new Error(`空响应: ${response.status}`);
  return payload;
}
