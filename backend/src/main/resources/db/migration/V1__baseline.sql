CREATE TABLE IF NOT EXISTS school (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  province VARCHAR(50),
  city VARCHAR(50),
  region VARCHAR(50),
  school_level VARCHAR(50),
  is_985 TINYINT DEFAULT 0,
  is_211 TINYINT DEFAULT 0,
  is_double_first_class TINYINT DEFAULT 0,
  official_site VARCHAR(255),
  graduate_site VARCHAR(255),
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_school_name (name)
);

CREATE TABLE IF NOT EXISTS college (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  official_site VARCHAR(255),
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_college_school_id (school_id),
  CONSTRAINT fk_college_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE IF NOT EXISTS major (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id BIGINT NOT NULL,
  college_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  major_code VARCHAR(50),
  degree_type VARCHAR(20),
  research_direction VARCHAR(255),
  study_mode VARCHAR(20),
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_major_school_id (school_id),
  KEY idx_major_college_id (college_id),
  KEY idx_major_name (name),
  CONSTRAINT fk_major_school FOREIGN KEY (school_id) REFERENCES school(id),
  CONSTRAINT fk_major_college FOREIGN KEY (college_id) REFERENCES college(id)
);

CREATE TABLE IF NOT EXISTS document_source (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  source_type VARCHAR(50),
  source_url VARCHAR(500),
  publish_date DATE,
  school_id BIGINT,
  college_id BIGINT,
  year INT,
  is_official TINYINT DEFAULT 1,
  audit_status VARCHAR(20) DEFAULT 'PUBLISHED',
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_source_school_id (school_id),
  KEY idx_source_college_id (college_id),
  KEY idx_source_year (year),
  CONSTRAINT fk_source_school FOREIGN KEY (school_id) REFERENCES school(id),
  CONSTRAINT fk_source_college FOREIGN KEY (college_id) REFERENCES college(id)
);

CREATE TABLE IF NOT EXISTS source_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  document_type VARCHAR(50),
  source_url VARCHAR(500),
  school_id BIGINT,
  college_id BIGINT,
  major_id BIGINT,
  year INT,
  audit_status VARCHAR(20) DEFAULT 'DRAFT',
  source_reliability VARCHAR(20) DEFAULT 'UNKNOWN',
  raw_text LONGTEXT,
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_source_document_school_year (school_id, year),
  KEY idx_source_document_type (document_type),
  KEY idx_source_document_audit_status (audit_status),
  KEY idx_source_document_reliability (source_reliability),
  CONSTRAINT fk_source_document_school FOREIGN KEY (school_id) REFERENCES school(id),
  CONSTRAINT fk_source_document_college FOREIGN KEY (college_id) REFERENCES college(id),
  CONSTRAINT fk_source_document_major FOREIGN KEY (major_id) REFERENCES major(id)
);

CREATE TABLE IF NOT EXISTS document_chunk (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  school_id BIGINT,
  college_id BIGINT,
  major_id BIGINT,
  year INT,
  document_type VARCHAR(50),
  chunk_index INT NOT NULL,
  content TEXT NOT NULL,
  page_number INT,
  audit_status VARCHAR(20) DEFAULT 'DRAFT',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_chunk_document_id (document_id),
  KEY idx_chunk_school_year (school_id, year),
  KEY idx_chunk_type (document_type),
  KEY idx_chunk_audit_status (audit_status),
  FULLTEXT KEY ft_chunk_content (content),
  CONSTRAINT fk_chunk_document FOREIGN KEY (document_id) REFERENCES source_document(id),
  CONSTRAINT fk_chunk_school FOREIGN KEY (school_id) REFERENCES school(id),
  CONSTRAINT fk_chunk_college FOREIGN KEY (college_id) REFERENCES college(id),
  CONSTRAINT fk_chunk_major FOREIGN KEY (major_id) REFERENCES major(id)
);

CREATE TABLE IF NOT EXISTS admission_plan (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id BIGINT NOT NULL,
  college_id BIGINT NOT NULL,
  major_id BIGINT NOT NULL,
  year INT NOT NULL,
  total_quota INT,
  recommended_quota INT,
  unified_quota INT,
  has_adjustment TINYINT DEFAULT 0,
  source_id BIGINT,
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_plan_school_year (school_id, year),
  KEY idx_plan_major_year (major_id, year),
  CONSTRAINT fk_plan_school FOREIGN KEY (school_id) REFERENCES school(id),
  CONSTRAINT fk_plan_college FOREIGN KEY (college_id) REFERENCES college(id),
  CONSTRAINT fk_plan_major FOREIGN KEY (major_id) REFERENCES major(id),
  CONSTRAINT fk_plan_source FOREIGN KEY (source_id) REFERENCES document_source(id)
);

CREATE TABLE IF NOT EXISTS exam_subject (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id BIGINT NOT NULL,
  college_id BIGINT NOT NULL,
  major_id BIGINT NOT NULL,
  year INT NOT NULL,
  politics VARCHAR(100),
  foreign_language VARCHAR(100),
  math_subject VARCHAR(100),
  professional_subject VARCHAR(100),
  is_408 TINYINT DEFAULT 0,
  reference_books TEXT,
  source_id BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_subject_school_year (school_id, year),
  KEY idx_subject_major_year (major_id, year),
  KEY idx_subject_is_408 (is_408),
  CONSTRAINT fk_subject_school FOREIGN KEY (school_id) REFERENCES school(id),
  CONSTRAINT fk_subject_college FOREIGN KEY (college_id) REFERENCES college(id),
  CONSTRAINT fk_subject_major FOREIGN KEY (major_id) REFERENCES major(id),
  CONSTRAINT fk_subject_source FOREIGN KEY (source_id) REFERENCES document_source(id)
);

CREATE TABLE IF NOT EXISTS score_line (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id BIGINT NOT NULL,
  college_id BIGINT NOT NULL,
  major_id BIGINT NOT NULL,
  year INT NOT NULL,
  total_score INT,
  politics_score INT,
  foreign_language_score INT,
  math_score INT,
  professional_score INT,
  source_id BIGINT,
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_score_school_year (school_id, year),
  KEY idx_score_major_year (major_id, year),
  KEY idx_score_total (total_score),
  CONSTRAINT fk_score_school FOREIGN KEY (school_id) REFERENCES school(id),
  CONSTRAINT fk_score_college FOREIGN KEY (college_id) REFERENCES college(id),
  CONSTRAINT fk_score_major FOREIGN KEY (major_id) REFERENCES major(id),
  CONSTRAINT fk_score_source FOREIGN KEY (source_id) REFERENCES document_source(id)
);

CREATE TABLE IF NOT EXISTS admission_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id BIGINT NOT NULL,
  college_id BIGINT NOT NULL,
  major_id BIGINT NOT NULL,
  year INT NOT NULL,
  admitted_count INT,
  lowest_score INT,
  average_score DECIMAL(6,2),
  highest_score INT,
  retest_ratio DECIMAL(5,2),
  source_id BIGINT,
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_result_school_year (school_id, year),
  KEY idx_result_major_year (major_id, year),
  CONSTRAINT fk_result_school FOREIGN KEY (school_id) REFERENCES school(id),
  CONSTRAINT fk_result_college FOREIGN KEY (college_id) REFERENCES college(id),
  CONSTRAINT fk_result_major FOREIGN KEY (major_id) REFERENCES major(id),
  CONSTRAINT fk_result_source FOREIGN KEY (source_id) REFERENCES document_source(id)
);

CREATE TABLE IF NOT EXISTS retest_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id BIGINT NOT NULL,
  college_id BIGINT NOT NULL,
  major_id BIGINT NOT NULL,
  year INT NOT NULL,
  retest_time VARCHAR(255),
  retest_method VARCHAR(255),
  retest_ratio DECIMAL(5,2),
  initial_score_weight INT,
  retest_score_weight INT,
  qualification_line TEXT,
  materials TEXT,
  source_id BIGINT,
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_retest_school_year (school_id, year),
  KEY idx_retest_major_year (major_id, year),
  CONSTRAINT fk_retest_school FOREIGN KEY (school_id) REFERENCES school(id),
  CONSTRAINT fk_retest_college FOREIGN KEY (college_id) REFERENCES college(id),
  CONSTRAINT fk_retest_major FOREIGN KEY (major_id) REFERENCES major(id),
  CONSTRAINT fk_retest_source FOREIGN KEY (source_id) REFERENCES document_source(id)
);

CREATE TABLE IF NOT EXISTS reference_book (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id BIGINT NOT NULL,
  college_id BIGINT NOT NULL,
  major_id BIGINT NOT NULL,
  year INT NOT NULL,
  subject_name VARCHAR(255),
  book_title VARCHAR(255),
  author VARCHAR(255),
  edition VARCHAR(100),
  publisher VARCHAR(255),
  source_id BIGINT,
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_book_school_year (school_id, year),
  KEY idx_book_major_year (major_id, year),
  CONSTRAINT fk_book_school FOREIGN KEY (school_id) REFERENCES school(id),
  CONSTRAINT fk_book_college FOREIGN KEY (college_id) REFERENCES college(id),
  CONSTRAINT fk_book_major FOREIGN KEY (major_id) REFERENCES major(id),
  CONSTRAINT fk_book_source FOREIGN KEY (source_id) REFERENCES document_source(id)
);

CREATE TABLE IF NOT EXISTS adjustment_info (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id BIGINT NOT NULL,
  college_id BIGINT NOT NULL,
  major_id BIGINT NOT NULL,
  year INT NOT NULL,
  title VARCHAR(255),
  is_open TINYINT DEFAULT 0,
  vacancy_count INT,
  application_window VARCHAR(255),
  requirements TEXT,
  notice_url VARCHAR(500),
  source_id BIGINT,
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_adjustment_school_year (school_id, year),
  KEY idx_adjustment_major_year (major_id, year),
  CONSTRAINT fk_adjustment_school FOREIGN KEY (school_id) REFERENCES school(id),
  CONSTRAINT fk_adjustment_college FOREIGN KEY (college_id) REFERENCES college(id),
  CONSTRAINT fk_adjustment_major FOREIGN KEY (major_id) REFERENCES major(id),
  CONSTRAINT fk_adjustment_source FOREIGN KEY (source_id) REFERENCES document_source(id)
);

CREATE TABLE IF NOT EXISTS admin_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(50),
  status TINYINT NOT NULL DEFAULT 1,
  last_login_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_admin_username (username)
);

CREATE TABLE IF NOT EXISTS ai_conversation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  question TEXT NOT NULL,
  answer TEXT,
  related_school_id BIGINT,
  related_major_id BIGINT,
  source_summary TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ai_school_id (related_school_id),
  KEY idx_ai_major_id (related_major_id),
  CONSTRAINT fk_ai_school FOREIGN KEY (related_school_id) REFERENCES school(id),
  CONSTRAINT fk_ai_major FOREIGN KEY (related_major_id) REFERENCES major(id)
);

CREATE TABLE IF NOT EXISTS catalog_import_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  catalog_type VARCHAR(50) NOT NULL,
  year INT NOT NULL,
  retrieved_at VARCHAR(50),
  is_complete TINYINT DEFAULT 0,
  input_records INT NOT NULL,
  school_count INT NOT NULL,
  batch_sha256 VARCHAR(64) NOT NULL,
  imported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_catalog_batch (catalog_type, year, batch_sha256),
  KEY idx_catalog_batch_latest (catalog_type, year, imported_at)
);

CREATE TABLE IF NOT EXISTS data_change_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator VARCHAR(50),
  action VARCHAR(20) NOT NULL,
  resource_path VARCHAR(255) NOT NULL,
  status_code INT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_change_log_created_at (created_at),
  KEY idx_change_log_resource_path (resource_path)
);

CREATE TABLE IF NOT EXISTS data_collection_task (
  school_id BIGINT PRIMARY KEY,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  assignee VARCHAR(100),
  due_date DATE,
  completion_criteria VARCHAR(1000) NOT NULL,
  criteria_custom TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  completed_at DATETIME,
  KEY idx_collection_task_status_due (status, due_date),
  CONSTRAINT fk_collection_task_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE IF NOT EXISTS data_collection_target (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  document_type VARCHAR(50) NOT NULL,
  target_year INT NOT NULL,
  source_url VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  note VARCHAR(500),
  system_generated TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_collection_target (school_id, document_type, target_year, source_url),
  KEY idx_collection_target_status (status, target_year),
  CONSTRAINT fk_collection_target_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE IF NOT EXISTS data_collection_task_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id BIGINT NOT NULL,
  action VARCHAR(30) NOT NULL,
  from_status VARCHAR(20),
  to_status VARCHAR(20),
  operator VARCHAR(100),
  detail VARCHAR(1000),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_collection_history_school_time (school_id, created_at),
  CONSTRAINT fk_collection_history_school FOREIGN KEY (school_id) REFERENCES school(id)
);
