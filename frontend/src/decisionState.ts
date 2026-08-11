import type { FavoriteSchool, School } from './types';
import { formatRegionLabel } from './formatters';

export const MAX_COMPARE_SCHOOLS = 4;

export function toggleComparedSchool(selectedIds: number[], schoolId: number) {
  if (selectedIds.includes(schoolId)) {
    return selectedIds.filter((id) => id !== schoolId);
  }
  return addComparedSchool(selectedIds, schoolId);
}

export function addComparedSchool(selectedIds: number[], schoolId: number) {
  if (selectedIds.includes(schoolId) || selectedIds.length >= MAX_COMPARE_SCHOOLS) {
    return selectedIds;
  }
  return [...selectedIds, schoolId];
}

export function removeComparedSchool(selectedIds: number[], schoolId: number) {
  return selectedIds.filter((id) => id !== schoolId);
}

export function sanitizeComparedSchools(selectedIds: number[], validSchoolIds: ReadonlySet<number>) {
  return selectedIds.filter((id) => validSchoolIds.has(id)).slice(0, MAX_COMPARE_SCHOOLS);
}

export function createFavoriteSchool(school: School, savedAt = new Date().toISOString()): FavoriteSchool {
  return {
    schoolId: school.id,
    name: school.name,
    regionLabel: formatRegionLabel(school.province, school.city),
    schoolLevel: school.schoolLevel,
    primarySubject: school.primarySubject,
    is408: school.is408,
    latestQuota: school.latestQuota,
    latestScoreLine: school.latestScoreLine,
    groupTag: '稳妥',
    note: '',
    savedAt,
    noteUpdatedAt: savedAt
  };
}

export function toggleFavorite(favorites: FavoriteSchool[], school: School, savedAt?: string) {
  if (favorites.some((item) => item.schoolId === school.id)) {
    return favorites.filter((item) => item.schoolId !== school.id);
  }
  return [createFavoriteSchool(school, savedAt), ...favorites];
}

export function updateFavorite(favorites: FavoriteSchool[], schoolId: number, patch: Partial<FavoriteSchool>) {
  const normalizedPatch = patch.note === undefined ? patch : { ...patch, note: patch.note.slice(0, 300) };
  return favorites.map((item) => item.schoolId === schoolId ? { ...item, ...normalizedPatch } : item);
}
