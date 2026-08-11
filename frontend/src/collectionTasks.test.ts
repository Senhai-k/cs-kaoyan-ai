import { describe, expect, it } from 'vitest';
import { filterCollectionTasks, TASK_STATUS_LABELS } from './collectionTasks';
import type { DataCollectionTask } from './types';

const task = (status: DataCollectionTask['status']) => ({ status }) as DataCollectionTask;

describe('collection task helpers', () => {
  it('groups active and completed tasks without mutating the queue', () => {
    const tasks = [task('OPEN'), task('IN_PROGRESS'), task('BLOCKED'), task('COMPLETED')];
    expect(filterCollectionTasks(tasks, 'ACTIVE')).toHaveLength(3);
    expect(filterCollectionTasks(tasks, 'COMPLETED')).toHaveLength(1);
    expect(tasks).toHaveLength(4);
  });

  it('provides a user-facing label for every persisted status', () => {
    expect(Object.keys(TASK_STATUS_LABELS)).toEqual(['OPEN', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED']);
  });
});
