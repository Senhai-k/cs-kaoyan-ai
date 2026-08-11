import { describe, expect, it } from 'vitest';
import { homePrimaryAction } from './HomePage';

describe('homePrimaryAction', () => {
  it('starts new users from recommendation', () => {
    expect(homePrimaryAction(0).destination).toBe('recommend');
  });

  it('opens an established shortlist', () => {
    expect(homePrimaryAction(3).destination).toBe('favorites');
  });
});
