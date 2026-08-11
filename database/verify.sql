SELECT COUNT(*) AS school_count FROM school;
SELECT COUNT(*) AS college_count FROM college;
SELECT COUNT(*) AS major_count FROM major;
SELECT COUNT(*) AS source_count FROM document_source;
SELECT COUNT(*) AS admission_plan_count FROM admission_plan;
SELECT COUNT(*) AS admission_result_count FROM admission_result;
SELECT COUNT(*) AS retest_rule_count FROM retest_rule;
SELECT COUNT(*) AS reference_book_count FROM reference_book;
SELECT COUNT(*) AS adjustment_info_count FROM adjustment_info;
SELECT COUNT(*) AS exam_subject_count FROM exam_subject;
SELECT COUNT(*) AS score_line_count FROM score_line;
SELECT COUNT(*) AS published_source_count FROM document_source WHERE audit_status = 'PUBLISHED';
SELECT COUNT(*) AS change_log_count FROM data_change_log;
SELECT COUNT(*) AS source_document_count FROM source_document;
SELECT COUNT(*) AS document_chunk_count FROM document_chunk;
SELECT source_reliability, COUNT(*) AS reliability_count
FROM source_document
GROUP BY source_reliability
ORDER BY source_reliability;

SELECT
  s.name,
  s.region,
  s.school_level,
  m.degree_type,
  es.professional_subject,
  ap.total_quota,
  sl.total_score
FROM school s
LEFT JOIN major m ON m.school_id = s.id
LEFT JOIN exam_subject es ON es.major_id = m.id AND es.year = 2026
LEFT JOIN admission_plan ap ON ap.major_id = m.id AND ap.year = 2026
LEFT JOIN score_line sl ON sl.major_id = m.id AND sl.year = 2026
ORDER BY s.id;
