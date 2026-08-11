CREATE TABLE admission_result_import_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id BIGINT NOT NULL,
  year INT NOT NULL,
  source_id BIGINT NOT NULL,
  source_sha256 VARCHAR(64) NOT NULL,
  batch_sha256 VARCHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  input_records INT NOT NULL,
  group_count INT NOT NULL,
  mapped_group_count INT NOT NULL,
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  published_at DATETIME,
  UNIQUE KEY uk_admission_result_import_hash (batch_sha256),
  KEY idx_admission_result_import_school_year (school_id, year),
  KEY idx_admission_result_import_status (status),
  CONSTRAINT fk_admission_result_import_school FOREIGN KEY (school_id) REFERENCES school(id),
  CONSTRAINT fk_admission_result_import_source FOREIGN KEY (source_id) REFERENCES document_source(id)
);

CREATE TABLE admission_result_candidate (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  candidate_key VARCHAR(64) NOT NULL,
  college_name VARCHAR(100) NOT NULL,
  major_code VARCHAR(50) NOT NULL,
  major_name VARCHAR(100),
  degree_type VARCHAR(20) NOT NULL,
  study_mode VARCHAR(20) NOT NULL,
  candidate_type VARCHAR(50) NOT NULL,
  initial_score INT,
  retest_score DOUBLE,
  final_score DOUBLE,
  special_program VARCHAR(100),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_admission_result_candidate (batch_id, candidate_key),
  KEY idx_admission_result_candidate_group (batch_id, college_name, major_code, degree_type, study_mode, candidate_type),
  CONSTRAINT fk_admission_result_candidate_batch FOREIGN KEY (batch_id)
    REFERENCES admission_result_import_batch(id) ON DELETE CASCADE
);
