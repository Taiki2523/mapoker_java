CREATE TYPE wallet_ledger_reason AS ENUM (
  'REGISTER_BONUS',
  'DAILY_BONUS',
  'RECOVERY_BONUS',
  'TABLE_BUY_IN',
  'TABLE_CASH_OUT',
  'ADMIN_GRANT'
);

CREATE TABLE user_wallets (
  user_id BIGINT PRIMARY KEY,
  chip_balance BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_wallets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE wallet_ledger (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  delta BIGINT NOT NULL,
  balance_after BIGINT NOT NULL,
  reason wallet_ledger_reason NOT NULL,
  reference_type VARCHAR(64),
  reference_id VARCHAR(128),
  idempotency_key VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_wallet_ledger_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  UNIQUE (idempotency_key)
);

CREATE INDEX idx_wallet_ledger_user_created
  ON wallet_ledger (user_id, created_at DESC);
