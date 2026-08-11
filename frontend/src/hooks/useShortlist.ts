import { useEffect, useMemo, useState } from 'react';
import { addComparedSchool, removeComparedSchool, sanitizeComparedSchools, toggleComparedSchool, toggleFavorite, updateFavorite } from '../decisionState';
import type { FavoriteSchool, School } from '../types';

const FAVORITE_STORAGE_KEY = 'kaoyanFavoriteSchools';
const COMPARE_STORAGE_KEY = 'kaoyanComparedSchoolIds';

export function parseStoredFavorites(value: string | null): FavoriteSchool[] {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed.flatMap((candidate): FavoriteSchool[] => {
      if (!candidate || typeof candidate !== 'object') return [];
      const item = candidate as Partial<FavoriteSchool>;
      if (!Number.isInteger(item.schoolId) || (item.schoolId ?? 0) <= 0) return [];
      const savedAt = typeof item.savedAt === 'string' ? item.savedAt : '';
      return [{
        schoolId: item.schoolId!,
        name: typeof item.name === 'string' && item.name.trim() ? item.name : `院校 ${item.schoolId}`,
        regionLabel: typeof item.regionLabel === 'string' ? item.regionLabel : '地区待核验',
        schoolLevel: typeof item.schoolLevel === 'string' ? item.schoolLevel : '层次待核验',
        primarySubject: typeof item.primarySubject === 'string' ? item.primarySubject : null,
        is408: typeof item.is408 === 'boolean' ? item.is408 : null,
        latestQuota: typeof item.latestQuota === 'number' ? item.latestQuota : null,
        latestScoreLine: typeof item.latestScoreLine === 'number' ? item.latestScoreLine : null,
        groupTag: item.groupTag === '冲刺' || item.groupTag === '保底' ? item.groupTag : '稳妥',
        note: typeof item.note === 'string' ? item.note.slice(0, 300) : '',
        savedAt,
        noteUpdatedAt: typeof item.noteUpdatedAt === 'string' ? item.noteUpdatedAt : savedAt
      }];
    });
  } catch {
    return [];
  }
}

export function parseStoredComparedIds(value: string | null): number[] {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value) as unknown;
    if (!Array.isArray(parsed)) return [];
    return [...new Set(parsed.filter((id): id is number => Number.isInteger(id) && id > 0))].slice(0, 4);
  } catch {
    return [];
  }
}

export function useShortlist(schools: School[]) {
  const [selectedIds, setSelectedIds] = useState<number[]>(() =>
    parseStoredComparedIds(localStorage.getItem(COMPARE_STORAGE_KEY))
  );
  const [favoriteSchools, setFavoriteSchools] = useState<FavoriteSchool[]>(() =>
    parseStoredFavorites(localStorage.getItem(FAVORITE_STORAGE_KEY))
  );
  const favoriteIds = useMemo(() => new Set(favoriteSchools.map((item) => item.schoolId)), [favoriteSchools]);
  const validSchoolIds = useMemo(() => new Set(schools.map((school) => school.id)), [schools]);

  useEffect(() => {
    localStorage.setItem(FAVORITE_STORAGE_KEY, JSON.stringify(favoriteSchools));
  }, [favoriteSchools]);

  useEffect(() => {
    localStorage.setItem(COMPARE_STORAGE_KEY, JSON.stringify(selectedIds));
  }, [selectedIds]);

  useEffect(() => {
    if (schools.length === 0) return;
    setSelectedIds((current) => sanitizeComparedSchools(current, validSchoolIds));
  }, [schools.length, validSchoolIds]);

  return {
    selectedIds,
    setSelectedIds,
    favoriteSchools,
    setFavoriteSchools,
    favoriteIds,
    toggleComparison: (schoolId: number) => setSelectedIds((current) => toggleComparedSchool(current, schoolId)),
    addToComparison: (schoolId: number) => setSelectedIds((current) => addComparedSchool(current, schoolId)),
    removeFromComparison: (schoolId: number) => setSelectedIds((current) => removeComparedSchool(current, schoolId)),
    clearComparison: () => setSelectedIds([]),
    toggleFavoriteSchool: (school: School) => setFavoriteSchools((current) => toggleFavorite(current, school)),
    updateFavoriteSchool: (schoolId: number, patch: Partial<FavoriteSchool>) =>
      setFavoriteSchools((current) => updateFavorite(current, schoolId, patch)),
    removeFavoriteSchool: (schoolId: number) =>
      setFavoriteSchools((current) => current.filter((item) => item.schoolId !== schoolId))
  };
}
