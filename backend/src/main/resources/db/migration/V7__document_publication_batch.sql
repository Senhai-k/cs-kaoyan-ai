CREATE TABLE document_publication_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  status VARCHAR(20) NOT NULL,
  document_count INT NOT NULL,
  chunk_count INT NOT NULL DEFAULT 0,
  rollback_chunk_count INT,
  reason VARCHAR(500),
  operator VARCHAR(100) NOT NULL,
  rollback_reason VARCHAR(500),
  rollback_operator VARCHAR(100),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME,
  rolled_back_at DATETIME,
  KEY idx_document_publication_batch_status_time (status, created_at)
);

CREATE TABLE document_publication_batch_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  document_id BIGINT NOT NULL,
  previous_version_no INT NOT NULL,
  published_version_no INT NOT NULL,
  rollback_version_no INT,
  previous_audit_status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_document_publication_batch_item (batch_id, document_id),
  KEY idx_document_publication_item_document (document_id, batch_id),
  CONSTRAINT fk_document_publication_item_batch
    FOREIGN KEY (batch_id) REFERENCES document_publication_batch(id)
);
