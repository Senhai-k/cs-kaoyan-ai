import { useEffect, useState } from 'react';
import { requestJson } from '../api';
import { activeSchoolFilterLabels, DEFAULT_SCHOOL_FILTERS, type SchoolFilterPreset } from '../schoolFilters';
import type { School } from '../types';

export function useSchools() {
  const [schools, setSchools] = useState<School[]>([]);
  const [keyword, setKeyword] = useState('');
  const [is408, setIs408] = useState<boolean | undefined>();
  const [province, setProvince] = useState('');
  const [schoolLevel, setSchoolLevel] = useState('');
  const [degreeType, setDegreeType] = useState('');
  const [minQuota, setMinQuota] = useState('');
  const [maxQuota, setMaxQuota] = useState('');
  const [minScore, setMinScore] = useState('');
  const [maxScore, setMaxScore] = useState('');
  const [professionalKeyword, setProfessionalKeyword] = useState('');
  const [filtersExpanded, setFiltersExpanded] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const filterState = {
    keyword, is408, province, schoolLevel, degreeType, professionalKeyword,
    minQuota, maxQuota, minScore, maxScore
  };

  const updateSchoolFilters = (patch: SchoolFilterPreset) => {
    if ('keyword' in patch) setKeyword(patch.keyword ?? '');
    if ('is408' in patch) setIs408(patch.is408);
    if ('province' in patch) setProvince(patch.province ?? '');
    if ('schoolLevel' in patch) setSchoolLevel(patch.schoolLevel ?? '');
    if ('degreeType' in patch) setDegreeType(patch.degreeType ?? '');
    if ('professionalKeyword' in patch) setProfessionalKeyword(patch.professionalKeyword ?? '');
    if ('minQuota' in patch) setMinQuota(patch.minQuota ?? '');
    if ('maxQuota' in patch) setMaxQuota(patch.maxQuota ?? '');
    if ('minScore' in patch) setMinScore(patch.minScore ?? '');
    if ('maxScore' in patch) setMaxScore(patch.maxScore ?? '');
  };

  const loadSchools = () => {
    const params = new URLSearchParams();
    if (keyword.trim()) params.set('keyword', keyword.trim());
    if (is408 !== undefined) params.set('is408', String(is408));
    if (province) params.set('province', province);
    if (schoolLevel) params.set('schoolLevel', schoolLevel);
    if (degreeType) params.set('degreeType', degreeType);
    if (minQuota) params.set('minQuota', minQuota);
    if (maxQuota) params.set('maxQuota', maxQuota);
    if (minScore) params.set('minScore', minScore);
    if (maxScore) params.set('maxScore', maxScore);
    if (professionalKeyword.trim()) params.set('professionalKeyword', professionalKeyword.trim());
    setLoading(true);
    setError('');
    requestJson<School[]>(`/api/schools?${params.toString()}`)
      .then((payload) => {
        if (payload.code !== 200) throw new Error(payload.message);
        setSchools(payload.data);
      })
      .catch((requestError: Error) => {
        setError(requestError.message);
        setSchools([]);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => loadSchools(), [keyword, is408, province, schoolLevel, degreeType, minQuota, maxQuota, minScore, maxScore, professionalKeyword]);

  const activeFilters = activeSchoolFilterLabels(filterState).map((item) => ({
    ...item,
    clear: () => updateSchoolFilters({ [item.key.replace(/-true|-false/, '')]: '' } as SchoolFilterPreset)
  }));

  return {
    schools,
    keyword,
    setKeyword,
    filtersExpanded,
    setFiltersExpanded,
    loading,
    error,
    filterState,
    activeFilters,
    updateSchoolFilters,
    resetFilters: () => updateSchoolFilters(DEFAULT_SCHOOL_FILTERS),
    loadSchools
  };
}
