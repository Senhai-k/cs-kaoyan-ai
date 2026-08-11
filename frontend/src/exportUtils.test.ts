import { describe, expect, it } from 'vitest';
import { toCsv } from './exportUtils';

describe('toCsv', () => {
  it('escapes quotes and preserves commas inside cells', () => {
    expect(toCsv([{ name: '南京大学', note: '稳妥,需核验"年份"' }])).toBe(
      'name,note\n"南京大学","稳妥,需核验""年份"""'
    );
  });

  it('returns an empty document for an empty list', () => {
    expect(toCsv([])).toBe('');
  });
});
