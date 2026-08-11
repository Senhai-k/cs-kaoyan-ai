CREATE TABLE web_capture_schedule (
  target_id BIGINT PRIMARY KEY,
  enabled TINYINT NOT NULL DEFAULT 0,
  interval_hours INT NOT NULL DEFAULT 24,
  next_run_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  lease_owner VARCHAR(100),
  lease_until DATETIME,
  last_started_at DATETIME,
  last_finished_at DATETIME,
  last_status VARCHAR(20),
  last_error VARCHAR(500),
  consecutive_failures INT NOT NULL DEFAULT 0,
  updated_by VARCHAR(100),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_web_capture_schedule_target FOREIGN KEY (target_id)
    REFERENCES data_collection_target(id) ON DELETE CASCADE,
  KEY idx_web_capture_schedule_due (enabled, next_run_at, lease_until)
);
