ALTER TABLE players ADD COLUMN IF NOT EXISTS offline_buy_in  INT;
ALTER TABLE players ADD COLUMN IF NOT EXISTS offline_cash_out INT;
ALTER TABLE players ADD COLUMN IF NOT EXISTS cashout_at       TIMESTAMP;

ALTER TABLE tables DROP CONSTRAINT IF EXISTS chk_game_type;
ALTER TABLE tables ADD CONSTRAINT chk_game_type
  CHECK (game_type IN ('ring', 'offline', 'tournament'));
