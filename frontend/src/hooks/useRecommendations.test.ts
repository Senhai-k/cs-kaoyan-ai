import { describe, expect, it } from 'vitest';
import {
  DEFAULT_RECOMMENDATION_PROFILE,
  buildRecommendationRequest,
  parseStoredRecommendationProfile,
  toggleRecommendationProvince
} from './useRecommendations';

describe('recommendation profile helpers', () => {
  it('falls back safely when persisted state is invalid', () => {
    expect(parseStoredRecommendationProfile('{broken')).toEqual(DEFAULT_RECOMMENDATION_PROFILE);
    expect(parseStoredRecommendationProfile('[]')).toEqual(DEFAULT_RECOMMENDATION_PROFILE);
    expect(parseStoredRecommendationProfile('{"targetScore":380,"prefer408":"INVALID"}')).toEqual(DEFAULT_RECOMMENDATION_PROFILE);
  });

  it('merges valid persisted fields with defaults', () => {
    expect(parseStoredRecommendationProfile('{"targetScore":"385","preferredProvinces":["浙江"],"riskPreference":"AGGRESSIVE"}')).toEqual({
      ...DEFAULT_RECOMMENDATION_PROFILE,
      targetScore: '385',
      preferredProvinces: ['浙江'],
      riskPreference: 'AGGRESSIVE'
    });
  });

  it('migrates valid legacy provinces and discards obsolete macro regions', () => {
    expect(parseStoredRecommendationProfile('{"preferredRegions":["浙江","华东"]}').preferredProvinces).toEqual(['浙江']);
    expect(parseStoredRecommendationProfile('{"preferredRegions":["华北"]}').preferredProvinces).toEqual(['江苏']);
  });

  it('builds the backend request without leaking UI-only values', () => {
    expect(buildRecommendationRequest({
      ...DEFAULT_RECOMMENDATION_PROFILE,
      targetScore: '375',
      prefer408: 'ANY',
      degreeType: ''
    })).toEqual({
      targetScore: 375,
      preferredProvinces: ['江苏'],
      prefer408: null,
      degreeType: null,
      riskPreference: 'BALANCED',
      limit: 8
    });
  });

  it('adds and removes a preferred province without mutating the profile', () => {
    const added = toggleRecommendationProvince(DEFAULT_RECOMMENDATION_PROFILE, '浙江');
    expect(added.preferredProvinces).toEqual(['江苏', '浙江']);
    expect(DEFAULT_RECOMMENDATION_PROFILE.preferredProvinces).toEqual(['江苏']);
    expect(toggleRecommendationProvince(added, '江苏').preferredProvinces).toEqual(['浙江']);
  });
});
