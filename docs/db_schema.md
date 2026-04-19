# Java サービス向け PostgreSQL スキーマ案

このスキーマは Java 実装の永続化先です。API 契約を崩さずに移植できるよう、意図的に Go 版に近い構造を保ちます。

## 目的
- API が `game_id` から現在のゲーム状態を再読み込みできるようにする
- action log により、再現性のあるリプレイ / デバッグ情報を残す
- 実用性を保ちながら、DB モデルをドメインモデルに近づける
- ゲーム状態保存と action 追加をトランザクションでまとめて扱えるようにする

## Java 側の永続化メモ
- repository 実装は `com.mapoker.infrastructure.persistence` 配下に置く
- SQL は明示的に書き、JSON カラムの直列化 / 復元には Jackson を使う
- 保存処理は Spring のトランザクション管理を使う
- スキーママイグレーションは Flyway で管理し、`src/main/resources/db/migration` に配置する

## テーブル

### games
ゲーム全体の状態を保持する。

```sql
CREATE TYPE game_status AS ENUM ('in_progress', 'showdown', 'finished');
CREATE TYPE game_street AS ENUM ('preflop', 'flop', 'turn', 'river');
CREATE TYPE odd_chip_rule AS ENUM ('low_index', 'button_left');

CREATE TABLE games (
  id VARCHAR(64) PRIMARY KEY,
  status game_status NOT NULL,
  street game_street NOT NULL,
  button_index INT NOT NULL,
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
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

補足:
- `deck` には `["AS","KH"]` のようなカードコードで、シャッフル後の山札を保存する
- `community` には現在公開されているボードカードを保存する
- `acted` には各プレイヤーがその betting round でアクション済みかどうかを保存する

### players
ゲームに属するプレイヤーを保持する。

```sql
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
```

補足:
- `player_id` は `p1` のような外部向け識別子
- `total_contrib` は hand 全体の contribution で、side-pot 計算に使う
- `hole` は通常 API では非表示でも DB には保存してよい。可視性制御は API 層の責務とする

### actions
リプレイやデバッグのための action log を保持する。

```sql
CREATE TYPE action_type AS ENUM ('fold', 'check', 'call', 'bet', 'raise', 'all_in');

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
```

repository に求めること:
- `games` / `players` の更新と `actions` への insert は必ず同一トランザクションで行う

### users
Google identity から解決したローカルユーザー情報を保持する。

```sql
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
```

## 補足
- 初期の Java 版では、サーバー側 `sessions` テーブルは必須ではない。署名付き cookie / session token ベースの認証で十分とする
- session の永続化や失効管理が必要になったら、後から `sessions` テーブルを追加する
- このスキーマは正規化の美しさよりも、正確さとリプレイ容易性を優先している
- PostgreSQL の JSON 取り回しが厳しければ、ドメイン契約を変えずに TEXT へ落とすこともできる
