# トーナメントモード

**ステータス**: `planned`

## 概要

キャッシュゲームとは独立した `tournament` game type として、MTT（Multi Table
Tournament）対応のトーナメントモードを追加する。

v2 初期から 1卓専用にはせず、9人卓を複数作成できる前提で DB/API/Service を設計する。
既存の `GameState` / `GameLifecycleService` / `GameActionService` は卓単位のポーカー
実行エンジンとして再利用し、トーナメント全体の席・順位・スタックは
`tournament_players` を正本にする。

詳細な設計判断は `docs/designs/TOURNAMENT_DISCUSSION.md` を参照。

---

## v2 スコープ

### 含める

- トーナメント作成・登録・開始・終了
- MTT 前提の複数テーブル管理
- 初期ランダム座席配置
- テーブルバランス
- テーブルブレイク / 卓割れ
- 時間ベースのブラインドレベルアップ
- 全員均等アンティ
- ハンド終了時の bust 判定・順位確定
- 再エントリー
- 賞金配分と wallet 連携
- ハンド自動進行
- アクションタイムアウト
- auto-fold
- トーナメント用フロントエンド

### 含めない

- レイトレジスト
- アドオン
- 複数 entry ticket の厳密な履歴管理
- ファイナルテーブル再抽選の詳細設定
- WSOP 風の「9-handed で残り10人統合」設定
- リングゲームへのアクションタイムアウト適用

---

## 主要ルール

### エントリーと再エントリー

- `registering` 中のみ新規 register 可能
- `running` 開始後の新規 register は不可
- リバイは不採用
- 再エントリーは busted 後、受付期間内のみ可能
- 再エントリー費は `entry_fee` と同額
- 再エントリー後は新規参加者として空席へ再配置
- v2 初期の再エントリー上限は 1回

再エントリー状態:

```text
active
busted_reentry_available
busted
```

`busted_reentry_available` は順位未確定で、残り人数・卓数計算には含めない。

### ブラインド

- level は minutes ベースのみ
- `level_duration_seconds` でレベル時間を管理
- `next_level_at` を DB に永続化
- ハンド開始前にレベルアップ判定
- 進行中ハンドは開始時点の level で完結
- アンティは BB アンティではなく全員均等アンティ

blind schedule:

```json
[
  { "level": 1, "sb": 50, "bb": 100, "ante": 0 },
  { "level": 2, "sb": 75, "bb": 150, "ante": 0 },
  { "level": 3, "sb": 100, "bb": 200, "ante": 25 }
]
```

### MTT seating

- 最大卓人数は v2 初期では 9人
- 初期卓数 = `ceil(players / max_players_per_table)`
- 全卓の人数差が最大1人になるように初期配置
- 初期 table / seat / button はランダム
- break order は開始時に決定して保存

テーブルバランス:

- `maxPlayers - minPlayers >= 2` で実施
- 最多卓の次回 BB 予定者を最少卓へ移動
- 移動先は BB に近い不利な空席
- SB には原則直接配置しない

卓割れ:

- `activeTables > ceil(activePlayers / max_players_per_table)` で実施
- break order に従って閉鎖対象を選ぶ
- 壊した卓の active player は移動先 table / seat へランダム再配置

### 自動進行

- トーナメント卓は自動進行
- ハンド終了後 `next_hand_delay_seconds = 5` で次ハンド開始予約
- 予約は `tournament_tables.next_hand_at` に永続化
- worker は `FOR UPDATE SKIP LOCKED` で二重開始を防ぐ
- `tournament_tables.status != active` の卓は自動開始しない

アクションタイムアウト:

- `action_timeout_seconds = 30`
- timeout 時は check 可能なら check、不可なら fold
- 連続 timeout 2回で `auto_fold = true`
- `auto_fold` 中も席・stack は残り、ブラインド/アンティを支払う

### 終了と賞金

- 再エントリー受付中は、`busted_reentry_available` が残っていれば終了保留
- 再エントリー受付終了後、active が1人になったら終了
- 最後の active player に `rank = 1`
- prize pool は `TOURNAMENT_ENTRY + TOURNAMENT_REENTRY`
- 賞金は `floor(prize_pool * pct / 100)` で計算
- 余りは上位 rank から1ずつ配る
- 支払いは `TOURNAMENT_PRIZE` ledger で idempotent に記録

---

## API

```text
POST   /v1/tournaments
GET    /v1/tournaments
GET    /v1/tournaments/{id}
POST   /v1/tournaments/{id}/register
POST   /v1/tournaments/{id}/start
POST   /v1/tournaments/{id}/reentry
GET    /v1/tournaments/{id}/standings
GET    /v1/tournaments/{id}/tables
GET    /v1/tournaments/{id}/me
POST   /v1/tournaments/{id}/auto-fold
```

既存の `/v1/games/{id}/*` は卓内 action には再利用する。
ただしトーナメント卓の hand start は tournament-aware な自動進行経由に限定する。

---

## DB 変更

### migration 方針

- `V18`: `wallet_ledger_reason` に tournament 理由を追加
- `V19`: tournament 系テーブル追加
- `V20`: `games.action_deadline_at` 追加

### wallet_ledger_reason

```sql
ALTER TYPE wallet_ledger_reason ADD VALUE IF NOT EXISTS 'TOURNAMENT_ENTRY';
ALTER TYPE wallet_ledger_reason ADD VALUE IF NOT EXISTS 'TOURNAMENT_PRIZE';
ALTER TYPE wallet_ledger_reason ADD VALUE IF NOT EXISTS 'TOURNAMENT_REENTRY';
```

### games

```sql
ALTER TABLE games
ADD COLUMN IF NOT EXISTS action_deadline_at TIMESTAMP;
```

### tournaments

```sql
CREATE TABLE tournaments (
  id                     VARCHAR(128) PRIMARY KEY,
  name                   VARCHAR(255) NOT NULL,
  host_user_id           BIGINT       NOT NULL,
  status                 VARCHAR(32)  NOT NULL DEFAULT 'registering',
  entry_fee              INT          NOT NULL,
  starting_stack         INT          NOT NULL,
  max_players            INT          NOT NULL,
  max_players_per_table  INT          NOT NULL DEFAULT 9,
  prize_structure        JSONB        NOT NULL,
  blind_schedule         JSONB        NOT NULL,
  level_duration_seconds INT          NOT NULL DEFAULT 480,
  allow_reentry          BOOLEAN      NOT NULL DEFAULT FALSE,
  reentry_level_limit    INT,
  max_reentries          INT          NOT NULL DEFAULT 0,
  current_level          INT          NOT NULL DEFAULT 1,
  next_level_at          TIMESTAMP,
  next_busted_sequence   BIGINT       NOT NULL DEFAULT 1,
  created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_tournaments_host FOREIGN KEY (host_user_id) REFERENCES users(id)
);
```

### tournament_tables

```sql
CREATE TABLE tournament_tables (
  id             BIGSERIAL    PRIMARY KEY,
  tournament_id  VARCHAR(128) NOT NULL,
  table_id       VARCHAR(128) NOT NULL,
  table_number   INT          NOT NULL,
  status         VARCHAR(32)  NOT NULL DEFAULT 'active',
  break_order    INT          NOT NULL,
  next_hand_at   TIMESTAMP,
  created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  closed_at      TIMESTAMP,
  UNIQUE (tournament_id, table_number),
  UNIQUE (tournament_id, table_id),
  UNIQUE (tournament_id, break_order),
  CONSTRAINT fk_tt_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE,
  CONSTRAINT fk_tt_table      FOREIGN KEY (table_id) REFERENCES tables(id)
);
```

`status`:

```text
active
paused
breaking
closed
finished
```

### tournament_players

```sql
CREATE TABLE tournament_players (
  id                   BIGSERIAL    PRIMARY KEY,
  tournament_id        VARCHAR(128) NOT NULL,
  user_id              BIGINT       NOT NULL,
  current_table_id     VARCHAR(128),
  current_seat_index   INT,
  stack                INT          NOT NULL,
  stack_at_hand_start  INT,
  status               VARCHAR(32)  NOT NULL DEFAULT 'active',
  rank                 INT,
  busted_at            TIMESTAMP,
  busted_sequence      BIGINT,
  reentry_count        INT          NOT NULL DEFAULT 0,
  auto_fold            BOOLEAN      NOT NULL DEFAULT FALSE,
  consecutive_timeouts INT          NOT NULL DEFAULT 0,
  prize_chips          INT          NOT NULL DEFAULT 0,
  created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (tournament_id, user_id),
  UNIQUE (tournament_id, current_table_id, current_seat_index),
  CONSTRAINT fk_tp_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE,
  CONSTRAINT fk_tp_user       FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_tp_table      FOREIGN KEY (current_table_id) REFERENCES tables(id)
);
```

### tournament_table_movements

```sql
CREATE TABLE tournament_table_movements (
  id              BIGSERIAL    PRIMARY KEY,
  tournament_id   VARCHAR(128) NOT NULL,
  user_id         BIGINT       NOT NULL,
  from_table_id   VARCHAR(128),
  from_seat_index INT,
  to_table_id     VARCHAR(128) NOT NULL,
  to_seat_index   INT          NOT NULL,
  reason          VARCHAR(32)  NOT NULL,
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ttm_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE,
  CONSTRAINT fk_ttm_user       FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_ttm_from_table FOREIGN KEY (from_table_id) REFERENCES tables(id),
  CONSTRAINT fk_ttm_to_table   FOREIGN KEY (to_table_id) REFERENCES tables(id)
);
```

`reason`:

```text
initial
balance
break
final_redraw
reentry
```

---

## 実装フェーズ

### Phase 0: 設計同期

変更:

- `docs/features/tournament.md` を最新設計に更新
- `docs/designs/TOURNAMENT_DISCUSSION.md` との矛盾を解消

レビュー観点:

- リバイ、単一卓、hands based level など古い前提が残っていないか
- v2 スコープと後回し項目が明確か

### Phase 1: DB migration

変更:

- `TOURNAMENT_ENTRY` / `TOURNAMENT_PRIZE` / `TOURNAMENT_REENTRY` を追加
- `tournaments` を追加
- `tournament_tables` を追加
- `tournament_players` を追加
- `tournament_table_movements` を追加
- `games.action_deadline_at` を追加

レビュー観点:

- migration が既存 DB に対して後方互換か
- enum 追加が idempotent か
- unique constraint が MTT と再エントリーの要件に合っているか
- nullable な seat/table が busted 状態を表現できるか
- `allow_reentry=false` のとき `max_reentries=0` が表現できるか

### Phase 2: repository / model

変更:

- `TournamentRepository` port を追加
- Postgres 実装を追加
- tournament 用 record/value object を追加
- `BlindLevel` / `BlindSchedule` を追加
- `PrizeStructure` を追加

レビュー観点:

- JSONB を文字列操作でなく structured mapping しているか
- repository が transaction 境界を service に委ねているか
- DTO と domain/application record の責務が混ざっていないか
- `busted_sequence` 採番に必要な `FOR UPDATE` 操作が可能か

### Phase 3: 作成・登録・開始

変更:

- `TournamentService.create`
- `TournamentService.register`
- `TournamentService.start`
- entry fee の wallet ledger 記録
- 初期 seating
- tournament table 自動作成
- `tournament_table_movements(reason='initial')`

レビュー観点:

- host だけが start できるか
- start 後の新規 register が拒否されるか
- entry fee の二重徴収を idempotency で防げるか
- 初期配置で卓人数差が最大1人か
- 初期 button が active seat から選ばれるか
- `tournament_players` と作成された table/game が同期しているか

### Phase 4: ハンド境界同期

変更:

- `TournamentTableSyncService.syncTable`
- `GameLifecycleService.startHand(id, bigBlind, ante)` overload
- ハンド開始前に `stack_at_hand_start` を記録
- ハンド終了時に `GameState.players.stack` を `tournament_players.stack` へ書き戻し
- `GameActionService` の showdown / fold-win に `onHandFinished(tableId)` hook

レビュー観点:

- ハンド中は `GameState`、ハンド境界では `tournament_players` が正本になっているか
- fold-win と showdown の両方で hook が呼ばれるか
- 二重 hook で stack/rank が二重更新されないか
- 既存リングゲームの挙動に影響していないか

### Phase 5: bust / rank / 終了

変更:

- bust 判定
- `busted_sequence` 採番
- 同時 bust の順位決定
- `busted_reentry_available` / `busted` 遷移
- トーナメント終了判定
- 賞金配分

レビュー観点:

- 複数卓の同時 bust が transaction 採番順で安定するか
- 同一ハンド内 bust が `stack_at_hand_start` で正しく並ぶか
- 再エントリー受付中の一時 bust に `rank` を付けていないか
- 終了時に最後の active が rank 1 になるか
- prize payout が二重実行されないか
- 端数が上位から配られ、合計が prize pool と一致するか

### Phase 6: ブラインドレベル

変更:

- minutes based level progression
- `next_level_at` 更新
- `seconds_until_next_level` API response
- `current_level > reentry_level_limit` 時の再エントリー受付終了

レビュー観点:

- 進行中ハンドの途中で blind/ante が変わらないか
- 長時間停止後に while loop で複数 level skip できるか
- 最終 level 超過時の扱いが決まっているか
- reentry close と tournament finish 判定が同じ transaction で処理されるか

### Phase 7: 自動進行

変更:

- `tournament_tables.next_hand_at`
- next hand worker
- `FOR UPDATE SKIP LOCKED`
- `tournament_tables.status`
- manual start-hand を tournament 卓では管理用/フォールバック扱いにする

レビュー観点:

- 複数 worker で二重 start しないか
- 再起動後に `next_hand_at` から復旧できるか
- `paused` / `breaking` / `closed` / `finished` の卓を開始しないか
- `GameState.status == IN_PROGRESS` の再確認があるか

### Phase 8: action timeout / auto-fold

変更:

- `games.action_deadline_at`
- action timeout worker
- timeout 時の check/fold
- `consecutive_timeouts`
- `auto_fold`
- `processAutoFoldChain`
- auto-fold 解除 API

レビュー観点:

- legal action を見て check/fold を選んでいるか
- timeout 自動 action も通常 action と同じ validation/hook を通るか
- auto-fold がハンド参加除外ではなく自動 check/fold として動くか
- 連続 auto-fold で無限ループしないか
- リングゲームに timeout が適用されていないか

### Phase 9: MTT 運営

変更:

- `balanceTables`
- `breakTable`
- short table pause
- table movement notification event
- break order
- lock order 統一

レビュー観点:

- 人数差が2以上のときだけ balance するか
- balance は最多卓の次回 BB 予定者を選ぶか
- break は最少卓ではなく break order に従うか
- 壊した卓の全員をランダム再配置しているか
- 移動履歴が必ず残るか
- 複数卓 lock の順序が固定されているか

### Phase 10: API

変更:

- tournament CRUD/read API
- register/start/reentry API
- standings API
- tables API
- me/status API
- auto-fold toggle API

レビュー観点:

- 認可が host / participant / public read で分かれているか
- API response に current level / countdown / table / seat / status が含まれるか
- busted / reentry available / finished の error response が明確か
- idempotency が必要な write API に入っているか

### Phase 11: frontend

変更:

- Tournament List
- Tournament Lobby
- My Tournament Status
- Standings
- Game Screen additions
- action timer
- auto-fold toggle
- table move notification
- reentry flow
- result / payout display

レビュー観点:

- 自分の現在卓へ迷わず戻れるか
- 移動通知が見落とされにくいか
- action timer と auto-fold 状態が明確か
- reentry available と busted finalized が区別できるか
- standings が大人数でも読めるか
- mobile でテーブル一覧・順位・ゲーム画面が破綻しないか

### Phase 12: tests

変更:

- unit tests
- repository tests
- service integration tests
- API tests
- frontend component tests
- E2E smoke tests

レビュー観点:

- 初期配置の人数差
- 再エントリーの状態遷移
- 同時 bust の rank
- prize payout の端数
- next hand worker の二重開始防止
- timeout check/fold
- table balance
- table break
- restart recovery
- 既存 ring/offline regression

---

## ロック順

複数卓をまたぐ処理では deadlock を避けるため、常に以下の順で row lock を取る。

```text
1. tournaments row
2. tournament_tables rows: table_id 昇順
3. games rows: game_id 昇順
4. tournament_players rows: user_id 昇順
```

自動 worker が複数 tournament を拾っても、transaction は tournament 単位に分ける。

---

## 完了条件

- 9人卓を複数作成して tournament を開始できる
- 初期配置の人数差が最大1人
- 各卓が自動でハンド進行する
- アクション放置で timeout check/fold される
- 連続 timeout で auto-fold になる
- bust / reentry / final bust の順位管理が正しい
- table balance / break が動く
- prize payout が wallet ledger に記録される
- frontend から登録、観戦、参加、再エントリー、順位確認ができる
- 既存 ring/offline の主要フローが壊れていない
