# PostgreSQL スキーマ

Flyway マイグレーション V1〜V11 が適用された後の完全なスキーマ。
マイグレーションファイルは `mapoker-backend/src/main/resources/db/migration/` にある。

## 永続化方針

- SQL は `JdbcTemplate` で明示的に記述する（ORM なし）
- JSON カラムは JSONB 型。Jackson でシリアライズ / デシリアライズする
- ゲーム状態保存と action 追加は必ず同一トランザクション内で行う
- スキーマ変更は必ず新しい versioned migration で行う（既存 `V*.sql` の編集禁止）

---

## ENUM 型

```sql
CREATE TYPE game_status       AS ENUM ('in_progress', 'showdown', 'finished');
CREATE TYPE game_street       AS ENUM ('preflop', 'flop', 'turn', 'river');
CREATE TYPE odd_chip_rule     AS ENUM ('low_index', 'button_left');
CREATE TYPE action_type       AS ENUM (
  'fold', 'check', 'call', 'bet', 'raise', 'all_in',
  'showdown', 'payout'  -- V11 で追加（アクションログ永続化用）
);
CREATE TYPE wallet_ledger_reason AS ENUM (
  'REGISTER_BONUS', 'DAILY_BONUS', 'RECOVERY_BONUS',
  'TABLE_BUY_IN', 'TABLE_CASH_OUT', 'ADMIN_GRANT', 'TABLE_REBUY'
);
-- 注: RECOVERY_BONUS は API から削除済みだが DB ENUM は互換のため維持
```

---

## テーブル

### games

ゲーム全体の状態を保持する。

```sql
CREATE TABLE games (
  id              VARCHAR(64)  PRIMARY KEY,
  status          game_status  NOT NULL,
  street          game_street  NOT NULL,
  button_index    INT          NOT NULL,
  small_blind_idx INT          NOT NULL,
  big_blind_idx   INT          NOT NULL,
  current_player  INT          NOT NULL,
  current_bet     INT          NOT NULL,
  last_raise_size INT          NOT NULL,
  big_blind       INT          NOT NULL,
  pot_total       INT          NOT NULL,
  odd_chip_rule   odd_chip_rule NOT NULL,
  deck            JSONB        NOT NULL,  -- ["AS","KH",...] シャッフル済み山札
  deck_pos        INT          NOT NULL,
  community       JSONB        NOT NULL,  -- 公開済みボードカード
  acted           JSONB        NOT NULL,  -- プレイヤーごとのアクション済みフラグ
  raise_open      BOOLEAN      NOT NULL,
  fold_win        BOOLEAN      NOT NULL DEFAULT FALSE,  -- フォールドによる勝利か
  last_showdown   JSONB,
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### players

ゲームに属するプレイヤーを保持する。

```sql
CREATE TABLE players (
  id           BIGSERIAL    PRIMARY KEY,
  game_id      VARCHAR(64)  NOT NULL,
  player_index INT          NOT NULL,
  player_id    VARCHAR(64)  NOT NULL,
  stack        INT          NOT NULL,
  contributed  INT          NOT NULL,
  total_contrib INT         NOT NULL,  -- hand 全体の拠出額（side-pot 計算用）
  folded       BOOLEAN      NOT NULL,
  all_in       BOOLEAN      NOT NULL,
  hole         JSONB        NOT NULL,  -- ["AS","KH"] — DB には常に保存、可視性は API 層で制御
  sitting_out  BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (game_id, player_index),
  CONSTRAINT fk_players_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
);
CREATE INDEX idx_players_game ON players (game_id);
```

### actions

リプレイ / デバッグ用の action ログ。V11 で `label` カラムと `showdown` / `payout` 種別を追加。

```sql
CREATE TABLE actions (
  id           BIGSERIAL    PRIMARY KEY,
  game_id      VARCHAR(64)  NOT NULL,
  seq          INT          NOT NULL,
  player_index INT          NOT NULL,
  action_type  action_type  NOT NULL,
  amount       INT          NOT NULL,
  label        VARCHAR(100),            -- V11 追加: ショーダウン役名など
  created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (game_id, seq),
  CONSTRAINT fk_actions_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
);
CREATE INDEX idx_actions_game ON actions (game_id);
```

`games` / `players` の更新と `actions` への insert は必ず同一トランザクションで行う。

### users

ユーザー情報。V10 で Google Auth 対応のため `discriminator` / `public_id` / `avatar_url` を追加。

```sql
CREATE TABLE users (
  id            BIGSERIAL    PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL,
  discriminator VARCHAR(4)   NOT NULL DEFAULT '0000',  -- V10 追加
  password_hash VARCHAR(255),                          -- V10 より NULL 許可（Google専用ユーザー対応）
  public_id     UUID         NOT NULL UNIQUE,          -- V10 追加
  avatar_url    TEXT,                                  -- V10 追加
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (username, discriminator)                     -- V10 で username 単独 UNIQUE から変更
);
-- discriminator は 0000〜9999 の4桁数字
```

### user_auth_identities

OAuth プロバイダーとの紐付け（V10 追加）。

```sql
CREATE TABLE user_auth_identities (
  id               BIGSERIAL    PRIMARY KEY,
  user_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider         VARCHAR(50)  NOT NULL,  -- 例: 'google'
  provider_user_id VARCHAR(255) NOT NULL,  -- Google sub
  created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_auth_provider_user UNIQUE (provider, provider_user_id),
  CONSTRAINT uq_auth_user_provider UNIQUE (user_id, provider)
);
CREATE INDEX idx_user_auth_identities_user_id ON user_auth_identities (user_id);
```

### user_table_sessions

ユーザーのテーブル参加履歴（`GET /v1/auth/history` の元データ）。

```sql
CREATE TABLE user_table_sessions (
  id         BIGSERIAL    PRIMARY KEY,
  username   VARCHAR(50)  NOT NULL,
  table_id   VARCHAR(64)  NOT NULL,
  table_name VARCHAR(100) NOT NULL,
  seat_index INT          NOT NULL,
  visibility VARCHAR(16)  NOT NULL,
  status     VARCHAR(16)  NOT NULL,
  flags      JSONB        NOT NULL,
  joined_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  left_at    TIMESTAMP,
  updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_table_sessions_username_joined_at
  ON user_table_sessions (username, joined_at DESC);

CREATE INDEX idx_user_table_sessions_active
  ON user_table_sessions (username, table_id, seat_index)
  WHERE left_at IS NULL;
```

### hand_history

ハンド終了時に保存する履歴（`GET /v1/auth/hand-history` の元データ）。

```sql
CREATE TABLE hand_history (
  id          BIGSERIAL    PRIMARY KEY,
  table_id    VARCHAR(64)  NOT NULL,
  hand_id     VARCHAR(64)  NOT NULL UNIQUE,
  players     JSONB        NOT NULL,
  winners     JSONB        NOT NULL,
  pot         INT          NOT NULL,
  street      game_street  NOT NULL,
  finished_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_hand_history_table_finished_at
  ON hand_history (table_id, finished_at DESC);
```

### user_wallets

ユーザーのチップ残高。

```sql
CREATE TABLE user_wallets (
  user_id       BIGINT    PRIMARY KEY,
  chip_balance  BIGINT    NOT NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_wallets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### wallet_ledger

ウォレットの入出金ログ。べき等キーで重複付与を防ぐ。

```sql
CREATE TABLE wallet_ledger (
  id               BIGSERIAL              PRIMARY KEY,
  user_id          BIGINT                 NOT NULL,
  delta            BIGINT                 NOT NULL,
  balance_after    BIGINT                 NOT NULL,
  reason           wallet_ledger_reason   NOT NULL,
  reference_type   VARCHAR(64),
  reference_id     VARCHAR(128),
  idempotency_key  VARCHAR(255)           NOT NULL,
  created_at       TIMESTAMP              NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_wallet_ledger_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  UNIQUE (idempotency_key)
);

CREATE INDEX idx_wallet_ledger_user_created
  ON wallet_ledger (user_id, created_at DESC);
```

### tables

テーブルのメタデータ。参加者状態はアプリケーション層（`TableService`）がインメモリで管理し、PostgreSQL 実装では `tables` テーブルと `user_table_sessions` を使う。

```sql
CREATE TABLE tables (
  id           VARCHAR(128) PRIMARY KEY,
  room_id      VARCHAR(128) NOT NULL,
  name         VARCHAR(255) NOT NULL,
  game_type    VARCHAR(32)  NOT NULL DEFAULT 'ring',
  small_blind  INT          NOT NULL,
  big_blind    INT          NOT NULL,
  min_buy_in   INT          NOT NULL,
  max_buy_in   INT          NOT NULL,
  max_players  INT          NOT NULL,
  flags        TEXT[]       NOT NULL DEFAULT '{}',
  visibility   VARCHAR(32)  NOT NULL DEFAULT 'public',
  status       VARCHAR(32)  NOT NULL DEFAULT 'inactive',
  ever_seated  BOOLEAN      NOT NULL DEFAULT FALSE,
  game_id      VARCHAR(128),
  created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

## Flyway マイグレーション履歴

| バージョン | ファイル | 内容 |
|---|---|---|
| V1 | `V1__initial_schema.sql` | games / players / actions / users（初期スキーマ） |
| V2 | `V2__users_username_password.sql` | users テーブルをユーザー名 / パスワード方式に再作成 |
| V3 | `V3__add_fold_win.sql` | games に `fold_win` カラム追加 |
| V4 | `V4__user_table_sessions.sql` | user_table_sessions テーブル作成 |
| V5 | `V5__hand_history.sql` | hand_history テーブル作成 |
| V6 | `V6__wallet_and_ledger.sql` | user_wallets / wallet_ledger テーブルと ENUM 作成 |
| V7 | `V7__tables_and_sitting_out.sql` | tables テーブル作成、players に `sitting_out` カラム追加 |
| V8 | `V8__add_rebuy_reason.sql` | `wallet_ledger_reason` ENUM に `TABLE_REBUY` を追加 |
| V9 | `V9__add_google_id_to_users.sql` | users に `google_id` カラム追加（V10 で削除・置換） |
| V10 | `V10__add_discriminator_and_auth_identities.sql` | Google Auth 本対応: `google_id` を削除し `discriminator` / `public_id` / `avatar_url` を追加、`user_auth_identities` テーブル作成 |
| V11 | `V11__add_label_to_actions.sql` | actions に `label` カラム追加、`action_type` ENUM に `showdown` / `payout` を追加 |
