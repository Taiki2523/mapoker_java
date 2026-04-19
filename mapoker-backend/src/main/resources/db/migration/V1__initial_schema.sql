CREATE TYPE game_status AS ENUM ('in_progress', 'showdown', 'finished');
CREATE TYPE game_street AS ENUM ('preflop', 'flop', 'turn', 'river');
CREATE TYPE odd_chip_rule AS ENUM ('low_index', 'button_left');
CREATE TYPE action_type AS ENUM ('fold', 'check', 'call', 'bet', 'raise', 'all_in');

CREATE TABLE games (
  id VARCHAR(64) PRIMARY KEY,
  status game_status NOT NULL,
  street game_street NOT NULL,
  button_index INT NOT NULL,
  small_blind_idx INT NOT NULL,
  big_blind_idx INT NOT NULL,
  current_player INT NOT NULL,
  current_bet INT NOT NULL,
  last_raise_size INT NOT NULL,
  big_blind INT NOT NULL,
  pot_total INT NOT NULL,
  odd_chip_rule odd_chip_rule NOT NULL,
  deck JSONB NOT NULL,
  deck_pos INT NOT NULL,
  community JSONB NOT NULL,
  acted JSONB NOT NULL,
  raise_open BOOLEAN NOT NULL,
  last_showdown JSONB,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE players (
  id BIGSERIAL PRIMARY KEY,
  game_id VARCHAR(64) NOT NULL,
  player_index INT NOT NULL,
  player_id VARCHAR(64) NOT NULL,
  stack INT NOT NULL,
  contributed INT NOT NULL,
  total_contrib INT NOT NULL,
  folded BOOLEAN NOT NULL,
  all_in BOOLEAN NOT NULL,
  hole JSONB NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (game_id, player_index),
  CONSTRAINT fk_players_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
);
CREATE INDEX idx_players_game ON players (game_id);

CREATE TABLE actions (
  id BIGSERIAL PRIMARY KEY,
  game_id VARCHAR(64) NOT NULL,
  seq INT NOT NULL,
  player_index INT NOT NULL,
  action_type action_type NOT NULL,
  amount INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (game_id, seq),
  CONSTRAINT fk_actions_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
);
CREATE INDEX idx_actions_game ON actions (game_id);

CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  google_sub VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  email_verified BOOLEAN NOT NULL,
  name VARCHAR(255) NOT NULL,
  picture VARCHAR(512) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (google_sub)
);
