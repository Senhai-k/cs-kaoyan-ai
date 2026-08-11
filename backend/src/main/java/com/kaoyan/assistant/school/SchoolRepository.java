package com.kaoyan.assistant.school;

import com.kaoyan.assistant.common.JdbcValues;
import com.kaoyan.assistant.schoolscore.SchoolScoreLineInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
public class SchoolRepository {

    private static final Set<String> CATEGORY_B_PROVINCES = Set.of(
            "内蒙古", "广西", "海南", "贵州", "云南", "西藏", "甘肃", "青海", "宁夏", "新疆"
    );

    private final JdbcTemplate jdbcTemplate;

    public SchoolRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SchoolSummary> findSummaries(String keyword, Boolean is408, String province,
                                             String schoolLevel, String degreeType, Integer minQuota,
                                             Integer maxQuota, Integer minScore, Integer maxScore,
                                             String professionalKeyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                  s.id,
                  s.name,
                  s.province,
                  s.city,
                  s.school_level,
                  s.is_985,
                  s.is_211,
                  s.is_double_first_class,
                  es.professional_subject,
                  es.is_408,
                  ap.total_quota,
                  sl.total_score
                FROM school s
                LEFT JOIN major m ON m.id = (
                  SELECT m2.id FROM major m2
                  WHERE m2.school_id = s.id
                  ORDER BY CASE WHEN EXISTS (
                    SELECT 1 FROM exam_subject preferred_es
                    WHERE preferred_es.major_id = m2.id AND preferred_es.is_408 = 1
                  ) THEN 0 ELSE 1 END, m2.id ASC
                  LIMIT 1
                )
                LEFT JOIN exam_subject es ON es.id = (
                  SELECT es2.id FROM exam_subject es2
                  WHERE es2.major_id = m.id
                  ORDER BY es2.year DESC, es2.is_408 DESC, es2.id DESC
                  LIMIT 1
                )
                LEFT JOIN admission_plan ap ON ap.major_id = m.id
                  AND ap.year = (SELECT MAX(ap2.year) FROM admission_plan ap2 WHERE ap2.major_id = m.id)
                LEFT JOIN score_line sl ON sl.major_id = m.id
                  AND sl.year = (SELECT MAX(sl2.year) FROM score_line sl2 WHERE sl2.major_id = m.id)
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (s.name LIKE ? OR s.province LIKE ? OR s.city LIKE ?)");
            String likeKeyword = "%" + keyword.trim() + "%";
            params.add(likeKeyword);
            params.add(likeKeyword);
            params.add(likeKeyword);
        }
        if (is408 != null) {
            sql.append(" AND es.is_408 = ?");
            params.add(is408 ? 1 : 0);
        }
        if (province != null && !province.isBlank()) {
            sql.append(" AND s.province = ?");
            params.add(province.trim());
        }
        if (schoolLevel != null && !schoolLevel.isBlank()) {
            sql.append(" AND s.school_level LIKE ?");
            params.add("%" + schoolLevel.trim() + "%");
        }
        if (degreeType != null && !degreeType.isBlank()) {
            sql.append(" AND m.degree_type = ?");
            params.add(degreeType.trim());
        }
        if (minQuota != null) {
            sql.append(" AND ap.total_quota >= ?");
            params.add(minQuota);
        }
        if (maxQuota != null) {
            sql.append(" AND ap.total_quota <= ?");
            params.add(maxQuota);
        }
        if (minScore != null) {
            sql.append(" AND sl.total_score >= ?");
            params.add(minScore);
        }
        if (maxScore != null) {
            sql.append(" AND sl.total_score <= ?");
            params.add(maxScore);
        }
        if (professionalKeyword != null && !professionalKeyword.isBlank()) {
            sql.append(" AND es.professional_subject LIKE ?");
            params.add("%" + professionalKeyword.trim() + "%");
        }

        sql.append(" ORDER BY s.id ASC");
        return jdbcTemplate.query(sql.toString(), this::mapSummary, params.toArray());
    }

    public SchoolSummary findSummaryById(Long id) {
        List<SchoolSummary> result = jdbcTemplate.query("""
                SELECT
                  s.id,
                  s.name,
                  s.province,
                  s.city,
                  s.school_level,
                  s.is_985,
                  s.is_211,
                  s.is_double_first_class,
                  es.professional_subject,
                  es.is_408,
                  ap.total_quota,
                  sl.total_score
                FROM school s
                LEFT JOIN major m ON m.id = (
                  SELECT m2.id FROM major m2
                  WHERE m2.school_id = s.id
                  ORDER BY CASE WHEN EXISTS (
                    SELECT 1 FROM exam_subject preferred_es
                    WHERE preferred_es.major_id = m2.id AND preferred_es.is_408 = 1
                  ) THEN 0 ELSE 1 END, m2.id ASC
                  LIMIT 1
                )
                LEFT JOIN exam_subject es ON es.id = (
                  SELECT es2.id FROM exam_subject es2
                  WHERE es2.major_id = m.id
                  ORDER BY es2.year DESC, es2.is_408 DESC, es2.id DESC
                  LIMIT 1
                )
                LEFT JOIN admission_plan ap ON ap.major_id = m.id
                  AND ap.year = (SELECT MAX(ap2.year) FROM admission_plan ap2 WHERE ap2.major_id = m.id)
                LEFT JOIN score_line sl ON sl.major_id = m.id
                  AND sl.year = (SELECT MAX(sl2.year) FROM score_line sl2 WHERE sl2.major_id = m.id)
                WHERE s.id = ?
                """, this::mapSummary, id);
        return result.isEmpty() ? null : result.get(0);
    }

    public SchoolDetail findDetailById(Long id) {
        SchoolSummary summary = findSummaryById(id);
        if (summary == null) {
            return null;
        }
        SchoolMajorInfo majorInfo = findPrimaryMajorInfo(id);
        Long majorId = majorInfo == null ? null : majorInfo.majorId();
        Long collegeId = majorInfo == null ? null : majorInfo.collegeId();
        List<SchoolProgramInfo> programs = findPrograms(id);
        Long examSourceId = majorId == null ? null : findLatestExamSourceId(majorId);
        List<YearValue> quotas = majorId == null ? List.of() : findYearValues("""
                SELECT year, total_quota AS metric_value, source_id
                FROM admission_plan
                WHERE major_id = ? AND total_quota IS NOT NULL
                ORDER BY year DESC
                """, majorId);
        List<AdmissionPlanInfo> admissionPlans = majorId == null ? List.of() : jdbcTemplate.query("""
                SELECT year, total_quota, recommended_quota, unified_quota,
                  has_adjustment, source_id, remark
                FROM admission_plan
                WHERE major_id = ?
                ORDER BY year DESC, id DESC
                """, (rs, rowNum) -> new AdmissionPlanInfo(
                (Integer) rs.getObject("year"),
                (Integer) rs.getObject("total_quota"),
                (Integer) rs.getObject("recommended_quota"),
                (Integer) rs.getObject("unified_quota"),
                rs.getObject("has_adjustment") == null ? null : rs.getInt("has_adjustment") == 1,
                (Long) rs.getObject("source_id"),
                rs.getString("remark")
        ), majorId);
        List<YearValue> scoreLines = majorId == null ? List.of() : findYearValues("""
                SELECT year, total_score AS metric_value, source_id
                FROM score_line
                WHERE major_id = ?
                ORDER BY year DESC
                """, majorId);
        List<NationalScoreLineInfo> nationalScoreLines = majorInfo == null
                ? List.of()
                : findNationalScoreLines(id, majorInfo.majorCode());
        List<SchoolScoreLineInfo> schoolScoreLines = findSchoolScoreLines(id,
                majorInfo == null ? null : majorInfo.majorCode());
        List<AdmissionResultInfo> admissionResults = majorId == null ? List.of() : jdbcTemplate.query("""
                SELECT year, admitted_count, lowest_score, average_score, highest_score, retest_ratio, source_id
                FROM admission_result
                WHERE major_id = ?
                ORDER BY year DESC
                """, (rs, rowNum) -> new AdmissionResultInfo(
                (Integer) rs.getObject("year"),
                (Integer) rs.getObject("admitted_count"),
                (Integer) rs.getObject("lowest_score"),
                JdbcValues.nullableDouble(rs, "average_score"),
                (Integer) rs.getObject("highest_score"),
                JdbcValues.nullableDouble(rs, "retest_ratio"),
                (Long) rs.getObject("source_id")
        ), majorId);
        List<RetestRuleInfo> retestRules = jdbcTemplate.query("""
                SELECT college_id, major_id, year, retest_time, retest_method, retest_ratio, initial_score_weight,
                  retest_score_weight, qualification_line, materials, source_id, remark
                FROM retest_rule
                WHERE school_id = ? AND (major_id = ?
                  OR (major_id IS NULL AND (college_id = ? OR college_id IS NULL)))
                ORDER BY year DESC,
                  CASE WHEN major_id IS NOT NULL THEN 0 WHEN college_id IS NOT NULL THEN 1 ELSE 2 END,
                  id DESC
                """, (rs, rowNum) -> new RetestRuleInfo(
                rs.getObject("major_id") != null ? "MAJOR"
                        : rs.getObject("college_id") != null ? "COLLEGE" : "SCHOOL",
                (Integer) rs.getObject("year"),
                rs.getString("retest_time"),
                rs.getString("retest_method"),
                JdbcValues.nullableDouble(rs, "retest_ratio"),
                (Integer) rs.getObject("initial_score_weight"),
                (Integer) rs.getObject("retest_score_weight"),
                rs.getString("qualification_line"),
                rs.getString("materials"),
                (Long) rs.getObject("source_id"),
                rs.getString("remark")
        ), id, majorId, collegeId);
        List<ReferenceBookInfo> referenceBooks = majorId == null ? List.of() : jdbcTemplate.query("""
                SELECT year, subject_name, book_title, author, edition, publisher, source_id, remark
                FROM reference_book
                WHERE major_id = ?
                ORDER BY year DESC, id DESC
                """, (rs, rowNum) -> new ReferenceBookInfo(
                (Integer) rs.getObject("year"),
                rs.getString("subject_name"),
                rs.getString("book_title"),
                rs.getString("author"),
                rs.getString("edition"),
                rs.getString("publisher"),
                (Long) rs.getObject("source_id"),
                rs.getString("remark")
        ), majorId);
        List<AdjustmentInfoView> adjustmentInfos = majorId == null ? List.of() : jdbcTemplate.query("""
                SELECT year, title, is_open, vacancy_count, application_window, requirements, notice_url, source_id, remark
                FROM adjustment_info
                WHERE major_id = ?
                ORDER BY year DESC, id DESC
                """, (rs, rowNum) -> new AdjustmentInfoView(
                (Integer) rs.getObject("year"),
                rs.getString("title"),
                rs.getInt("is_open") == 1,
                (Integer) rs.getObject("vacancy_count"),
                rs.getString("application_window"),
                rs.getString("requirements"),
                rs.getString("notice_url"),
                (Long) rs.getObject("source_id"),
                rs.getString("remark")
        ), majorId);
        List<SourceInfo> sources = jdbcTemplate.query("""
                SELECT id, title, source_type, source_url, year, is_official, audit_status, updated_at
                FROM document_source
                WHERE school_id = ?
                  AND COALESCE(audit_status, 'PUBLISHED') = 'PUBLISHED'
                ORDER BY year DESC, id DESC
                """, (rs, rowNum) -> new SourceInfo(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("source_type"),
                rs.getString("source_url"),
                (Integer) rs.getObject("year"),
                rs.getInt("is_official") == 1,
                rs.getString("audit_status"),
                rs.getString("updated_at")
        ), id);

        return new SchoolDetail(
                summary,
                majorInfo == null ? null : majorInfo.collegeName(),
                majorInfo == null ? null : majorInfo.majorName(),
                majorInfo == null ? null : majorInfo.majorCode(),
                majorInfo == null ? null : majorInfo.degreeType(),
                majorInfo == null ? null : majorInfo.researchDirection(),
                majorInfo == null ? null : majorInfo.studyMode(),
                programs,
                examSourceId,
                quotas,
                admissionPlans,
                scoreLines,
                nationalScoreLines,
                schoolScoreLines,
                admissionResults,
                retestRules,
                referenceBooks,
                adjustmentInfos,
                sources
        );
    }

    public Long create(CreateSchoolRequest request) {
        jdbcTemplate.update("""
                INSERT INTO school (
                  name, province, city, region, school_level, is_985, is_211,
                  is_double_first_class, official_site, graduate_site
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                request.name(),
                request.province(),
                request.city(),
                request.region(),
                request.schoolLevel(),
                request.is985() ? 1 : 0,
                request.is211() ? 1 : 0,
                request.isDoubleFirstClass() ? 1 : 0,
                request.officialSite(),
                request.graduateSite()
        );
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM school WHERE name = ?", Long.class, request.name());
    }

    public void update(Long id, CreateSchoolRequest request) {
        jdbcTemplate.update("""
                UPDATE school
                SET name = ?, province = ?, city = ?, region = ?, school_level = ?,
                    is_985 = ?, is_211 = ?, is_double_first_class = ?,
                    official_site = ?, graduate_site = ?
                WHERE id = ?
                """,
                request.name(),
                request.province(),
                request.city(),
                request.region(),
                request.schoolLevel(),
                request.is985() ? 1 : 0,
                request.is211() ? 1 : 0,
                request.isDoubleFirstClass() ? 1 : 0,
                request.officialSite(),
                request.graduateSite(),
                id);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM school WHERE id = ?", id);
    }

    private SchoolMajorInfo findPrimaryMajorInfo(Long schoolId) {
        List<SchoolMajorInfo> result = jdbcTemplate.query("""
                SELECT
                  c.id AS college_id,
                  c.name AS college_name,
                  m.id AS major_id,
                  m.name AS major_name,
                  m.major_code,
                  m.degree_type,
                  m.research_direction,
                  m.study_mode
                FROM major m
                LEFT JOIN college c ON c.id = m.college_id
                WHERE m.school_id = ?
                ORDER BY CASE WHEN EXISTS (
                  SELECT 1 FROM exam_subject preferred_es
                  WHERE preferred_es.major_id = m.id AND preferred_es.is_408 = 1
                ) THEN 0 ELSE 1 END, m.id ASC
                LIMIT 1
                """, (rs, rowNum) -> new SchoolMajorInfo(
                (Long) rs.getObject("college_id"),
                rs.getString("college_name"),
                rs.getLong("major_id"),
                rs.getString("major_name"),
                rs.getString("major_code"),
                rs.getString("degree_type"),
                rs.getString("research_direction"),
                rs.getString("study_mode")
        ), schoolId);
        return result.isEmpty() ? null : result.get(0);
    }

    private List<SchoolProgramInfo> findPrograms(Long schoolId) {
        return jdbcTemplate.query("""
                SELECT
                  m.id AS major_id,
                  c.name AS college_name,
                  m.name AS major_name,
                  m.major_code,
                  m.degree_type,
                  m.research_direction,
                  m.study_mode,
                  es.year,
                  es.politics,
                  es.foreign_language,
                  es.math_subject,
                  es.professional_subject,
                  es.is_408,
                  es.source_id
                FROM major m
                LEFT JOIN college c ON c.id = m.college_id
                JOIN exam_subject es ON es.major_id = m.id
                WHERE m.school_id = ? AND es.is_408 = 1
                ORDER BY es.year DESC, m.major_code, m.degree_type, m.study_mode,
                  c.name, es.politics, es.foreign_language, es.math_subject, es.id
                """, (rs, rowNum) -> new SchoolProgramInfo(
                rs.getLong("major_id"),
                rs.getString("college_name"),
                rs.getString("major_name"),
                rs.getString("major_code"),
                rs.getString("degree_type"),
                rs.getString("research_direction"),
                rs.getString("study_mode"),
                (Integer) rs.getObject("year"),
                rs.getString("politics"),
                rs.getString("foreign_language"),
                rs.getString("math_subject"),
                rs.getString("professional_subject"),
                rs.getInt("is_408") == 1,
                (Long) rs.getObject("source_id")
        ), schoolId);
    }

    private List<YearValue> findYearValues(String sql, Long majorId) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> new YearValue(
                (Integer) rs.getObject("year"),
                (Integer) rs.getObject("metric_value"),
                (Long) rs.getObject("source_id")
        ), majorId);
    }

    private Long findLatestExamSourceId(Long majorId) {
        List<Long> result = jdbcTemplate.query("""
                SELECT source_id
                FROM exam_subject
                WHERE major_id = ? AND source_id IS NOT NULL
                ORDER BY year DESC, id DESC
                LIMIT 1
                """, (rs, rowNum) -> (Long) rs.getObject("source_id"), majorId);
        return result.isEmpty() ? null : result.get(0);
    }

    private List<NationalScoreLineInfo> findNationalScoreLines(Long schoolId, String majorCode) {
        List<SchoolBaselineContext> contexts = jdbcTemplate.query("""
                SELECT province, is_self_determined_score
                FROM school
                WHERE id = ?
                """, (rs, rowNum) -> new SchoolBaselineContext(
                rs.getString("province"), rs.getInt("is_self_determined_score") == 1
        ), schoolId);
        if (contexts.isEmpty()) {
            return List.of();
        }
        SchoolBaselineContext context = contexts.get(0);
        String categoryCode = nationalCategoryCode(majorCode);
        String candidateType = CATEGORY_B_PROVINCES.contains(context.province()) ? "B" : "A";
        return jdbcTemplate.query("""
                SELECT year, category_code, category_name, candidate_type, total_score,
                  score_100, score_over_100, source_title, source_url, published_date, remark
                FROM national_score_line
                WHERE category_code = ? AND candidate_type = ?
                ORDER BY year DESC
                """, (rs, rowNum) -> {
            boolean applicable = !context.selfDeterminedScore();
            String remark = rs.getString("remark");
            if (!applicable) {
                remark = "该校为自主划线院校，国家线仅作为调剂和政策背景参考；实际复试要求以学校及学院公告为准。";
            }
            return new NationalScoreLineInfo(
                    (Integer) rs.getObject("year"),
                    rs.getString("category_code"),
                    rs.getString("category_name"),
                    rs.getString("candidate_type"),
                    (Integer) rs.getObject("total_score"),
                    (Integer) rs.getObject("score_100"),
                    (Integer) rs.getObject("score_over_100"),
                    applicable,
                    rs.getString("source_title"),
                    rs.getString("source_url"),
                    rs.getString("published_date"),
                    remark
            );
        }, categoryCode, candidateType);
    }

    private List<SchoolScoreLineInfo> findSchoolScoreLines(Long schoolId, String majorCode) {
        String categoryCode = nationalCategoryCode(majorCode);
        return jdbcTemplate.query("""
                SELECT year, category_code, category_name, degree_type, total_score, politics_score,
                  foreign_language_score, subject_one_score, subject_two_score, score_100,
                  score_over_100, availability_status, source_id, source_title, article_url,
                  image_url, published_date, scope_note, remark
                FROM school_score_line
                WHERE school_id = ? AND category_code = ?
                ORDER BY year DESC, CASE WHEN degree_type = '学硕' THEN 0 ELSE 1 END, id DESC
                """, (rs, rowNum) -> new SchoolScoreLineInfo(
                (Integer) rs.getObject("year"),
                rs.getString("category_code"),
                rs.getString("category_name"),
                rs.getString("degree_type"),
                (Integer) rs.getObject("total_score"),
                (Integer) rs.getObject("politics_score"),
                (Integer) rs.getObject("foreign_language_score"),
                (Integer) rs.getObject("subject_one_score"),
                (Integer) rs.getObject("subject_two_score"),
                (Integer) rs.getObject("score_100"),
                (Integer) rs.getObject("score_over_100"),
                rs.getString("availability_status"),
                (Long) rs.getObject("source_id"),
                rs.getString("source_title"),
                rs.getString("article_url"),
                rs.getString("image_url"),
                rs.getString("published_date"),
                rs.getString("scope_note"),
                rs.getString("remark")
        ), schoolId, categoryCode);
    }

    private String nationalCategoryCode(String majorCode) {
        if (majorCode != null && majorCode.startsWith("07")) {
            return "07";
        }
        if (majorCode != null && majorCode.startsWith("14")) {
            return "14";
        }
        return "08";
    }

    private record SchoolMajorInfo(
            Long collegeId,
            String collegeName,
            Long majorId,
            String majorName,
            String majorCode,
            String degreeType,
            String researchDirection,
            String studyMode
    ) {
    }

    private record SchoolBaselineContext(String province, boolean selfDeterminedScore) {
    }

    private SchoolSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new SchoolSummary(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("province"),
                rs.getString("city"),
                rs.getString("school_level"),
                rs.getInt("is_985") == 1,
                rs.getInt("is_211") == 1,
                rs.getInt("is_double_first_class") == 1,
                rs.getString("professional_subject"),
                rs.getObject("is_408") == null ? null : rs.getInt("is_408") == 1,
                (Integer) rs.getObject("total_quota"),
                (Integer) rs.getObject("total_score")
        );
    }
}
