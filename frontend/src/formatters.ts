import type { AdmissionPlanInfo, RecommendationItem } from './types';

export function auditLabel(status?: string) {
  if (status === 'PENDING') return '待审核';
  if (status === 'DRAFT') return '草稿';
  return '已发布';
}

export function riskLabel(level: string) {
  if (level === 'LOW') return '低风险';
  if (level === 'MEDIUM') return '较稳妥';
  if (level === 'STRETCH') return '可冲刺';
  if (level === 'HIGH') return '高风险';
  return '待核验';
}

export function recommendationConfidence(item: RecommendationItem) {
  const score = recommendationConfidencePercent(item);
  if (score >= 80) return '高';
  if (score >= 50) return '中';
  return '低';
}

export function recommendationConfidencePercent(item: RecommendationItem) {
  let score = 0;
  if (item.officialSourceCount > 0) score += 25;
  if (item.school.primarySubject && item.school.is408 !== null) score += 25;
  if (item.school.latestQuota !== null) score += 20;
  if (item.benchmarkScore !== null) score += 30;
  return score;
}

export function formatExamType(is408: boolean | null) {
  if (is408 === true) return '408';
  if (is408 === false) return '自命题';
  return '待核验';
}

export function formatDateTime(value?: string) {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 19);
}

export function formatRegionLabel(province?: string | null, city?: string | null) {
  return [province, city].filter((value): value is string => Boolean(value?.trim())).join(' ') || '地区待核验';
}

export function formatAdmissionPlan(plan: AdmissionPlanInfo) {
  const parts: string[] = [];
  if (plan.totalQuota !== null) parts.push(`总计划 ${plan.totalQuota}`);
  if (plan.recommendedQuota !== null) parts.push(`推免 ${plan.recommendedQuota}`);
  if (plan.unifiedQuota !== null) parts.push(`统考/复试阶段 ${plan.unifiedQuota}`);
  return parts.length ? parts.join(' / ') : '名额待核验';
}
