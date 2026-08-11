export type SchoolFilterState = {
  keyword: string;
  is408: boolean | undefined;
  province: string;
  schoolLevel: string;
  degreeType: string;
  professionalKeyword: string;
  minQuota: string;
  maxQuota: string;
  minScore: string;
  maxScore: string;
};

export type SchoolFilterPreset = Partial<SchoolFilterState>;

export const DEFAULT_SCHOOL_FILTERS: SchoolFilterState = {
  keyword: '',
  is408: undefined,
  province: '',
  schoolLevel: '',
  degreeType: '',
  professionalKeyword: '',
  minQuota: '',
  maxQuota: '',
  minScore: '',
  maxScore: ''
};

export function resetSchoolFilters(): SchoolFilterState {
  return { ...DEFAULT_SCHOOL_FILTERS };
}

export function applySchoolFilterPreset(preset: SchoolFilterPreset): SchoolFilterState {
  return { ...DEFAULT_SCHOOL_FILTERS, ...preset };
}

export function activeSchoolFilterLabels(filters: SchoolFilterState) {
  return [
    filters.keyword.trim() ? { key: 'keyword', label: `关键词：${filters.keyword.trim()}` } : null,
    filters.is408 === true ? { key: 'is408-true', label: '408' } : null,
    filters.is408 === false ? { key: 'is408-false', label: '自命题' } : null,
    filters.province ? { key: 'province', label: `省份：${filters.province}` } : null,
    filters.schoolLevel ? { key: 'schoolLevel', label: `层次：${filters.schoolLevel}` } : null,
    filters.degreeType ? { key: 'degreeType', label: `类型：${filters.degreeType}` } : null,
    filters.professionalKeyword.trim() ? { key: 'professionalKeyword', label: `专业课：${filters.professionalKeyword.trim()}` } : null,
    filters.minQuota ? { key: 'minQuota', label: `招生 >= ${filters.minQuota}` } : null,
    filters.maxQuota ? { key: 'maxQuota', label: `招生 <= ${filters.maxQuota}` } : null,
    filters.minScore ? { key: 'minScore', label: `复试线 >= ${filters.minScore}` } : null,
    filters.maxScore ? { key: 'maxScore', label: `复试线 <= ${filters.maxScore}` } : null
  ].filter((item): item is { key: string; label: string } => item !== null);
}
