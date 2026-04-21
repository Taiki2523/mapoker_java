CREATE TABLE user_table_sessions (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(50) NOT NULL,
  table_id VARCHAR(64) NOT NULL,
  table_name VARCHAR(100) NOT NULL,
  seat_index INT NOT NULL,
  visibility VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  flags JSONB NOT NULL,
  joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  left_at TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_table_sessions_username_joined_at
  ON user_table_sessions (username, joined_at DESC);

CREATE INDEX idx_user_table_sessions_active
  ON user_table_sessions (username, table_id, seat_index)
  WHERE left_at IS NULL;
