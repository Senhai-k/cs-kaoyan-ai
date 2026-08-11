import { describe, expect, it } from 'vitest';
import { isProvince, PROVINCE_OPTIONS, provinceToRegion } from './provinces';

describe('province options', () => {
  it('contains the 31 mainland province-level regions without duplicates', () => {
    expect(PROVINCE_OPTIONS).toHaveLength(31);
    expect(new Set(PROVINCE_OPTIONS)).toHaveLength(31);
  });

  it('rejects legacy macro-region labels', () => {
    expect(isProvince('江苏')).toBe(true);
    expect(isProvince('华东')).toBe(false);
  });

  it('derives the internal import region from a province', () => {
    expect(provinceToRegion('江苏')).toBe('华东');
    expect(provinceToRegion('四川')).toBe('西南');
    expect(provinceToRegion('')).toBe('');
  });
});
