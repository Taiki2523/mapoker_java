DROP TABLE IF EXISTS hand_history CASCADE;

CREATE TABLE hand_history (
  id BIGSERIAL PRIMARY KEY,
  table_id VARCHAR(64) NOT NULL,
  hand_id VARCHAR(64) NOT NULL UNIQUE,
  players JSONB NOT NULL,
  winners JSONB NOT NULL,
  pot INT NOT NULL,
  street game_street NOT NULL,
  finished_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_hand_history_table_finished_at
  ON hand_history (table_id, finished_at DESC);
