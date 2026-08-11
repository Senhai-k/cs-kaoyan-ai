import { useEffect, useState } from 'react';
import { requestJson } from '../api';
import { isProvince } from '../provinces';
import type { RecommendationItem, RecommendationProfile } from '../types';

const PROFILE_STORAGE_KEY = 'kaoyanRecommendationProfile';

export const DEFAULT_RECOMMENDATION_PROFILE: RecommendationProfile = {
  targetScore: '360',
  preferredProvinces: ['江苏'],
  prefer408: 'ONLY_408',
  degreeType: '专硕',
  riskPreference: 'BALANCED'
};

export function parseStoredRecommendationProfile(value: string | null): RecommendationProfile {
  if (!value) return { ...DEFAULT_RECOMMENDATION_PROFILE, preferredProvinces: [...DEFAULT_RECOMMENDATION_PROFILE.preferredProvinces] };
  try {
    const parsed = JSON.parse(value) as unknown;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return { ...DEFAULT_RECOMMENDATION_PROFILE, preferredProvinces: [...DEFAULT_RECOMMENDATION_PROFILE.preferredProvinces] };
    }
    const stored = parsed as Record<string, unknown>;
    const prefer408 = ['ANY', 'ONLY_408', 'SELF_DESIGNED'].includes(String(stored.prefer408))
      ? stored.prefer408 as RecommendationProfile['prefer408']
      : DEFAULT_RECOMMENDATION_PROFILE.prefer408;
    const riskPreference = ['CONSERVATIVE', 'BALANCED', 'AGGRESSIVE'].includes(String(stored.riskPreference))
      ? stored.riskPreference as RecommendationProfile['riskPreference']
      : DEFAULT_RECOMMENDATION_PROFILE.riskPreference;
    const storedProvinceValues = Array.isArray(stored.preferredProvinces)
      ? stored.preferredProvinces
      : Array.isArray(stored.preferredRegions) ? stored.preferredRegions : [];
    const preferredProvinces = storedProvinceValues.filter(isProvince);
    return {
      targetScore: typeof stored.targetScore === 'string' ? stored.targetScore : DEFAULT_RECOMMENDATION_PROFILE.targetScore,
      preferredProvinces: preferredProvinces.length > 0
        ? preferredProvinces
        : [...DEFAULT_RECOMMENDATION_PROFILE.preferredProvinces],
      prefer408,
      degreeType: typeof stored.degreeType === 'string' ? stored.degreeType : DEFAULT_RECOMMENDATION_PROFILE.degreeType,
      riskPreference
    };
  } catch {
    return { ...DEFAULT_RECOMMENDATION_PROFILE, preferredProvinces: [...DEFAULT_RECOMMENDATION_PROFILE.preferredProvinces] };
  }
}

export function buildRecommendationRequest(profile: RecommendationProfile) {
  const parsedTargetScore = Number(profile.targetScore);
  return {
    targetScore: profile.targetScore.trim() && Number.isFinite(parsedTargetScore) ? parsedTargetScore : null,
    preferredProvinces: profile.preferredProvinces,
    prefer408: profile.prefer408 === 'ANY' ? null : profile.prefer408 === 'ONLY_408',
    degreeType: profile.degreeType || null,
    riskPreference: profile.riskPreference,
    limit: 8
  };
}

export function toggleRecommendationProvince(profile: RecommendationProfile, province: string): RecommendationProfile {
  const exists = profile.preferredProvinces.includes(province);
  return {
    ...profile,
    preferredProvinces: exists
      ? profile.preferredProvinces.filter((item) => item !== province)
      : [...profile.preferredProvinces, province]
  };
}

export function useRecommendations() {
  const [recommendationProfile, setRecommendationProfile] = useState<RecommendationProfile>(() =>
    parseStoredRecommendationProfile(localStorage.getItem(PROFILE_STORAGE_KEY))
  );
  const [recommendations, setRecommendations] = useState<RecommendationItem[]>([]);
  const [recommendationLoading, setRecommendationLoading] = useState(false);
  const [recommendationError, setRecommendationError] = useState('');

  useEffect(() => {
    localStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(recommendationProfile));
  }, [recommendationProfile]);

  const loadRecommendations = () => {
    setRecommendationLoading(true);
    setRecommendationError('');
    requestJson<RecommendationItem[]>('/api/recommendations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(buildRecommendationRequest(recommendationProfile))
    })
      .then((payload) => setRecommendations(payload.data ?? []))
      .catch((requestError: Error) => {
        setRecommendationError(requestError.message);
        setRecommendations([]);
      })
      .finally(() => setRecommendationLoading(false));
  };

  const toggleProfileProvince = (province: string) => {
    setRecommendationProfile((current) => toggleRecommendationProvince(current, province));
  };

  return {
    recommendationProfile,
    setRecommendationProfile,
    recommendations,
    recommendationLoading,
    recommendationError,
    loadRecommendations,
    toggleProfileProvince
  };
}
