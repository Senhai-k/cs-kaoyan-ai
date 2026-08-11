CREATE TABLE admin_session (
  token_hash VARCHAR(64) PRIMARY KEY,
  username VARCHAR(50) NOT NULL,
  role VARCHAR(30) NOT NULL,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_used_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_admin_session_expiry (expires_at),
  KEY idx_admin_session_username (username),
  CONSTRAINT fk_admin_session_user FOREIGN KEY (username) REFERENCES admin_user(username)
);
