export function adminSuccessMessage(method: string, url: string) {
  const normalizedMethod = method.toUpperCase();
  if (normalizedMethod === 'DELETE') return '数据已删除，列表已刷新';
  if (url.includes('/publication-batches') && url.includes('/rollback')) return '发布批次已原子回滚';
  if (url.includes('/publication-batches')) return '资料批次已发布';
  if (url.includes('/web-capture-changes')) return '官网内容变化已复核';
  if (url.includes('/quality-check')) return '资料质量预检已完成';
  if (url.includes('/parse')) return '文件解析已完成';
  if (url.includes('/batch')) return '批量资料已导入';
  if (url.includes('/chunks')) return '资料切片已更新';
  if (normalizedMethod === 'PUT') return '修改已保存';
  return '数据已新增';
}

export function adminErrorMessage(status: number, payload: unknown) {
  if (payload && typeof payload === 'object' && 'message' in payload && typeof payload.message === 'string' && payload.message.trim()) {
    return payload.message.trim();
  }
  if (status === 403) return '当前账号没有执行此操作的权限';
  if (status === 404) return '目标数据不存在或已被删除';
  if (status === 409) return '数据存在关联或重复，无法完成操作';
  return `管理操作失败（HTTP ${status}）`;
}
