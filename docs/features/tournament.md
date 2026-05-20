# トーナメントモード

**ステータス**: `in-design`

## 概要

エントリー費を払って参加し、チップがなくなれば脱落、最後の一人（または合意した人数）まで競うトーナメント形式のゲームモードを追加する。キャッシュゲーム（リングゲーム）とは独立したゲームタイプとして実装する。

## 動機

- キャッシュゲームは自由な参加・離脱が可能だが競技性が低い
- トーナメントは明確な勝者と順位があり、ゲームとしての盛り上がりが生まれる
- 友人間での定期的な大会を手軽に開催できる

---

## ゲームルールの差分（リングゲームとの違い）

| 項目 | リング | トーナメント |
|---|---|---|
| スタック | ウォレットから随時バイイン | エントリー時に固定スターティングスタック |
| リバイ | 常時可（残高の範囲内） | リバイオプション有無を事前設定 |
| ブラインド | 固定 | レベルごとに自動アップ |
| 離脱 | 随時キャッシュアウト可 | チップ消失時のみ脱落（強制） |
| 勝利条件 | なし（スポット獲得を目的） | 全チップ獲得 or 合意人数まで |
| 賞金 | ウォレット増減 | プライズプール分配（入賞者のみ） |

---

## 主要機能

### 1. トーナメント管理

- トーナメント作成（エントリー費・スターティングスタック・最大参加人数・プライズ構成を設定）
- 参加登録（エントリー費をウォレットから徴収）
- 開始条件（最低参加人数到達 or 主催者が手動スタート）
- リバイオプション（設定時：指定ハンド数 or レベル内のみ許可）
- アドオンオプション（指定ブレイクタイムに一定額を追加購入可）

### 2. ブラインドスケジュール

- レベルごとの SB/BB/アンティ 額を JSON で事前定義
- レベルアップは「ハンド数」または「経過時間」どちらかを選択
- 現在のレベル・次レベルまでの残り（ハンド数 or 時間）を全員に通知
- ブラインドアップ時、進行中のハンドは現レベルで完結させてから切り替える

```json
// blindSchedule のサンプル（JSON カラム）
[
  { "level": 1, "sb": 25,  "bb": 50,  "ante": 0,   "hands": 10 },
  { "level": 2, "sb": 50,  "bb": 100, "ante": 0,   "hands": 10 },
  { "level": 3, "sb": 75,  "bb": 150, "ante": 25,  "hands": 10 },
  { "level": 4, "sb": 100, "bb": 200, "ante": 25,  "hands": 10 }
]
```

### 3. プレイヤー管理

- スタック 0 で脱落（自動 bust 処理）
- サバイバー数と順位を随時更新（脱落順に `rank` を確定）
- バブル（賞金圏外最後の一人）を検出してフロントに通知
- 着席は 1 テーブルのみ（MVP スコープ：マルチテーブルは対象外）

### 4. 賞金分配

- トーナメント終了時にプライズプール（全エントリー費合計）を入賞者に分配
- 分配率はテーブルで設定（例：1位 50%・2位 30%・3位 20%）
- 分配チップを `wallet_ledger` に `TOURNAMENT_PRIZE` 理由で記録

---

## API 設計（追加エンドポイント）

```
POST   /v1/tournaments                       # トーナメント作成
GET    /v1/tournaments                       # 一覧
GET    /v1/tournaments/{id}                  # 詳細（ステータス・順位・プライズ）
POST   /v1/tournaments/{id}/register         # エントリー登録
POST   /v1/tournaments/{id}/start            # 開始（主催者）
POST   /v1/tournaments/{id}/rebuy            # リバイ（許可期間内のみ）
GET    /v1/tournaments/{id}/standings        # 現在の順位・スタック一覧
```

ハンドの進行は既存の `/v1/games/{id}/*` をそのまま流用する。トーナメント層はゲーム完了後にブラインドアップ・脱落判定・終了判定を行う。

---

## DB スキーマ変更

### 新テーブル：`tournaments`

```sql
CREATE TABLE tournaments (
  id                VARCHAR(128) PRIMARY KEY,
  name              VARCHAR(255) NOT NULL,
  host_user_id      BIGINT       NOT NULL,
  status            VARCHAR(32)  NOT NULL DEFAULT 'registering',  -- registering / running / finished
  entry_fee         INT          NOT NULL,
  starting_stack    INT          NOT NULL,
  max_players       INT          NOT NULL,
  prize_structure   JSONB        NOT NULL,  -- [{ "rank": 1, "pct": 50 }, ...]
  blind_schedule    JSONB        NOT NULL,
  level_type        VARCHAR(16)  NOT NULL DEFAULT 'hands',  -- hands / minutes
  allow_rebuy       BOOLEAN      NOT NULL DEFAULT FALSE,
  rebuy_level_limit INT,                    -- このレベルまでリバイ可（NULL = 制限なし）
  current_level     INT          NOT NULL DEFAULT 1,
  hands_in_level    INT          NOT NULL DEFAULT 0,
  table_id          VARCHAR(128),
  created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_tournaments_host FOREIGN KEY (host_user_id) REFERENCES users(id)
);
```

### 新テーブル：`tournament_players`

```sql
CREATE TABLE tournament_players (
  id              BIGSERIAL    PRIMARY KEY,
  tournament_id   VARCHAR(128) NOT NULL,
  user_id         BIGINT       NOT NULL,
  seat_index      INT          NOT NULL,
  stack           INT          NOT NULL,
  status          VARCHAR(16)  NOT NULL DEFAULT 'active',  -- active / busted
  rank            INT,                                     -- 脱落時に確定
  rebuy_count     INT          NOT NULL DEFAULT 0,
  prize_chips     INT          NOT NULL DEFAULT 0,
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (tournament_id, user_id),
  UNIQUE (tournament_id, seat_index),
  CONSTRAINT fk_tournament_players_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE,
  CONSTRAINT fk_tournament_players_user       FOREIGN KEY (user_id) REFERENCES users(id)
);
```

### `wallet_ledger_reason` ENUM 拡張

```sql
ALTER TYPE wallet_ledger_reason ADD VALUE 'TOURNAMENT_ENTRY';
ALTER TYPE wallet_ledger_reason ADD VALUE 'TOURNAMENT_PRIZE';
ALTER TYPE wallet_ledger_reason ADD VALUE 'TOURNAMENT_REBUY';
```

---

## ステートマシン

```
registering
  └─[start]──▶ running
                 ├─[hand loop] running (ブラインドアップ・脱落処理)
                 └─[最後の1人 or 合意人数]──▶ finished (賞金分配)
```

---

## 実装方針

- `TournamentService` がトーナメント固有のライフサイクルを管理する
- ハンド終了フック（`GameService` が `TournamentService.onHandFinished()` を呼ぶ）でブラインドアップ・脱落判定を実行
- ドメイン層（`com.mapoker.domain`）には手を加えない。トーナメント固有のルールは `application` 層で完結させる
- MVP スコープ：1 テーブル（最大 9 人）のみ。マルチテーブルバランシングは次フェーズ

---

## 依存する先行作業

- ウォレット機能（`user_wallets` / `wallet_ledger`）— V6 で実装済み
- テーブル機能（`tables`）— V7 で実装済み
- リングゲームの安定動作

## 未解決事項

- タイムバンク（追加持ち時間）を実装するか
- マルチテーブル時のテーブルバランシング戦略（2 テーブル以上への分散）
- モバイル UI でのブラインドスケジュール表示レイアウト
