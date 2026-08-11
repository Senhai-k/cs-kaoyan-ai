import type { DataCollectionTask } from './types';

export type CollectionTaskFilter = 'ACTIVE' | DataCollectionTask['status'] | 'ALL';

export const TASK_STATUS_LABELS: Record<DataCollectionTask['status'], string> = {
  OPEN: '待处理',
  IN_PROGRESS: '进行中',
  BLOCKED: '受阻',
  COMPLETED: '已完成'
};

export const TARGET_STATUS_LABELS = {
  PENDING: '待采集',
  COLLECTED: '已采集',
  VERIFIED: '已核验'
} as const;

export const TASK_HISTORY_LABELS: Record<string, string> = {
  TASK_CREATED: '任务创建',
  MANUAL_UPDATE: '任务更新',
  AUTO_COMPLETED: '自动完成',
  AUTO_REOPENED: '自动重开',
  TARGETS_SYNCED: '待办同步',
  TARGET_CREATED: '新增 URL',
  TARGET_UPDATED: '更新 URL',
  TARGET_DELETED: '删除 URL',
  TARGET_LINK_ACCEPTED: '候选链接已确认'
};

export function filterCollectionTasks(tasks: DataCollectionTask[], filter: CollectionTaskFilter) {
  if (filter === 'ALL') return tasks;
  if (filter === 'ACTIVE') return tasks.filter((task) => task.status !== 'COMPLETED');
  return tasks.filter((task) => task.status === filter);
}
