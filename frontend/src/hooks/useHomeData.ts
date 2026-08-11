import { useMemo } from 'react';
import type { DataCoverageReport, DocumentSource, HomeUpdate, School, SourceDocument } from '../types';

export function useHomeData({
  schools,
  sources,
  sourceDocuments,
  coverageReport
}: {
  schools: School[];
  sources: DocumentSource[];
  sourceDocuments: SourceDocument[];
  coverageReport: DataCoverageReport | null;
}) {
  const schoolById = useMemo(() => new Map(schools.map((school) => [school.id, school])), [schools]);
  const recommendedSchools = useMemo(() => {
    const sourceDocumentCount = new Map<number, number>();
    const latestDocumentUpdate = new Map<number, string>();
    sourceDocuments.forEach((document) => {
      if (!document.schoolId) return;
      sourceDocumentCount.set(document.schoolId, (sourceDocumentCount.get(document.schoolId) ?? 0) + 1);
      if (document.updatedAt) {
        const current = latestDocumentUpdate.get(document.schoolId);
        if (!current || document.updatedAt > current) latestDocumentUpdate.set(document.schoolId, document.updatedAt);
      }
    });
    const sourceCount = new Map<number, number>();
    sources.forEach((source) => {
      sourceCount.set(source.schoolId, (sourceCount.get(source.schoolId) ?? 0) + 1);
      if (source.updatedAt) {
        const current = latestDocumentUpdate.get(source.schoolId);
        if (!current || source.updatedAt > current) latestDocumentUpdate.set(source.schoolId, source.updatedAt);
      }
    });
    return [...schools]
      .map((school) => ({
        school,
        score: (sourceDocumentCount.get(school.id) ?? 0) * 3 + (sourceCount.get(school.id) ?? 0) * 2 + (school.is408 ? 1 : 0),
        latestUpdatedAt: latestDocumentUpdate.get(school.id) ?? ''
      }))
      .sort((a, b) => b.score - a.score || b.latestUpdatedAt.localeCompare(a.latestUpdatedAt) || (b.school.latestQuota ?? 0) - (a.school.latestQuota ?? 0))
      .slice(0, 4)
      .map((item) => item.school);
  }, [schools, sourceDocuments, sources]);

  const latestUpdates = useMemo<HomeUpdate[]>(() => {
    const documentUpdates = sourceDocuments.map((document) => ({
      key: `doc-${document.id}`,
      title: document.title,
      subtitle: `${schoolById.get(document.schoolId ?? -1)?.name ?? '未关联院校'} / ${document.documentType}`,
      updatedAt: document.updatedAt ?? '',
      sourceUrl: document.sourceUrl
    }));
    const sourceUpdates = sources.map((source) => ({
      key: `source-${source.id}`,
      title: source.title,
      subtitle: `${schoolById.get(source.schoolId)?.name ?? '未关联院校'} / ${source.sourceType}`,
      updatedAt: source.updatedAt ?? '',
      sourceUrl: source.sourceUrl
    }));
    return [...documentUpdates, ...sourceUpdates]
      .filter((item) => item.updatedAt && item.sourceUrl)
      .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
      .slice(0, 6);
  }, [schoolById, sourceDocuments, sources]);

  return {
    schoolById,
    recommendedSchools,
    latestUpdates,
    dataCoverage: {
      officialSources: coverageReport?.officialSourceCount ?? sources.filter((source) => source.official && source.auditStatus === 'PUBLISHED').length,
      verifiedDocuments: coverageReport?.officialDocumentCount ?? sourceDocuments.filter((document) => document.auditStatus === 'PUBLISHED' && document.sourceReliability === 'OFFICIAL').length,
      schoolsWithMetrics: coverageReport?.readySchoolCount ?? schools.filter((school) => school.latestQuota !== null || school.latestScoreLine !== null || school.primarySubject).length
    }
  };
}
