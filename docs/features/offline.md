# オフラインモード（リアルカード対応）

**ステータス**: `design-complete`

## 概要

現実のトランプを使ったポーカーのチップ計算・スタック管理・収支確認を補助するモード。カードはアプリが配るのではなく、プレイヤーが物理的に扱う。アプリはベット・ポット・スタックの追跡に特化する。

---

## リングゲームとの主な違い

| 項目 | リング | オフライン |
|---|---|---|
| カード管理 | アプリがシャッフル・配布 | 物理カード（アプリは関与しない） |
| バイイン | ウォレット残高から徴収 | 自由設定（ウォレット残高に影響しない） |
| スタック | ウォレット残高と連動 | セッション内のみで独立管理 |
| 席順 | 着席順 or ランダム | 現実に座っている順番で固定（申告制） |
| ショーダウン | サーバーがカード評価 | ホストが勝者を宣言（サーバーはポット分配のみ担当） |
| 対象デバイス | PC・スマホ両対応 | スマホ優先 |
| UI 表示向き | 通常 | 自分のベット額は逆さ表示（向かいの人が見やすい） |

---

## ゲームフロー

### 1. セッション作成

- ホストがオフラインセッションを作成する（ブラインド額を設定）
- `POST /v1/games` に `game_type: "offline"` を渡す
- バイイン額の範囲：`min_buy_in = big_blind × 20`、`max_buy_in = big_blind × 200`（リングと同一ルール）
- ただし実際のバイイン額はプレイヤーが任意に指定できる（範囲内なら自由）

### 2. 着席

- 各プレイヤーが自分のスマホからセッションに参加し、座るシート番号を申告する（1〜最大 9）
- 申告した `seat_index` がそのまま `player_index` に対応する（サーバー側で一対一マッピング）
- 同じシートへの重複着席は不可（サーバー API 層で強制）
- バイイン額を決定して着席完了（ウォレットへの加算・減算はされない）
- **途中参加**: ゲーム開始後も `POST /v1/games/{id}/seat` を呼べる（許可）
- **リバイ**: スタックが 0 になったときに追加バイインを行う場合、`offline_buy_in` を累積加算する（`rebuy_count` は持たない）

### 3. ボタン割り当て

- 最初のハンド開始時、最初に着席したプレイヤーにボタンを割り当てる（リング同様）
- 以降はハンドごとにボタンが 1 席ずつ進む（既存の `nextActive(buttonIndex)` ロジックを流用）

### 4. ゲーム進行

カードは物理で扱うため、アプリは以下の情報のみを管理する：

- 各プレイヤーのスタック（チップ枚数）
- 現在のポット額
- ベット・レイズ・コール・フォールドのアクション記録
- SB / BB / ボタンの位置

`startHand()` のオフライン版では `Deck.shuffle()` とホールカード配布ループをスキップする。`community` は空リストのまま維持する。

アクションパネルの構成はリングゲームと同一（Fold / Check / Call / Bet / Raise プリセット）。

### 5. ショーダウン（手動宣言）

カードをアプリが管理しないため、勝者判定はホストが行う。

- ホストが `POST /v1/games/{id}/showdown` に `{ "declared_winners": [0, 2] }` を渡す
- サーバーは `totalContrib` ベースのサイドポット計算（`splitPots()`）を宣言された勝者に対して実行し、分配額を返す
- `HandEvaluator.eval7()` は呼ばない（ホールカードが存在しないため）

### 6. 離席・利確

- プレイヤーがゲームを離脱するとき、現在のスタックを「利確」として確定する
- `cashout_at` タイムスタンプが記録される（`NULL` = 未確定、`NOT NULL` = 確定済み）
- 利確画面に以下を表示する：
  - バイイン額（`offline_buy_in`）
  - キャッシュアウト額（`offline_cash_out`）
  - 収支（`offline_cash_out - offline_buy_in`）※ API で計算して返す派生値
- ウォレットへの反映はしない

### 7. セッション終了

- ホストが手動でセッションをクローズする（`POST /v1/games/{id}/close-session`）
- 収支サマリーは専用画面で表示する（チャット投稿は避ける。後から参照できない）

---

## UI 要件

### 共通（全員のスマホ）

- ポット額を画面上部に大きく表示（全員共通）
- SB / BB / ボタンマーカーを席に表示
- アクションパネル（リングと同一コンポーネントを流用）

### 個人表示エリア（スマホを縦持ち下半分）

- **自分のベット額を大きく表示**
- **逆さ表示（180° 回転）**：向かいに座った相手が自分のスマホを見たときに読めるよう、上下反転して表示する
- スタック残量（自分のみ）

```
┌─────────────────────────────────┐
│  POT: 1,200                     │  ← 全員から見える
│  ● SB  ◎ BB  ▶ BTN             │
│  [Player 1] [Player 2] ...      │
├─────────────────────────────────┤
│                                 │
│   ﾍﾞｯﾄ額: 300   ←逆さ表示      │  ← 向かいの人が読む
│                                 │
│  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │
│                                 │
│   ベット額: 300                 │  ← 自分が読む
│   Stack: 1,700                  │
│   [ Fold ] [ Check ] [ Raise ]  │
└─────────────────────────────────┘
```

---

## API 設計（追加エンドポイント）

既存ゲーム API を `game_type=offline` で拡張する方針。

```
POST   /v1/games                        # game_type: "offline" を追加
POST   /v1/games/{id}/seat              # シート申告（バイイン額含む）。開始後も呼び出し可能
POST   /v1/games/{id}/showdown          # offline: { "declared_winners": [0, 2] } を渡す
                                        # ring: body なし → サーバーがカード評価（既存）
POST   /v1/games/{id}/cashout           # 離席・利確
POST   /v1/games/{id}/close-session     # ホスト手動クローズ
GET    /v1/games/{id}/session-summary   # セッション全体の収支サマリー
```

`POST /v1/games/{id}/actions` はリングと共通 API を流用。ただしカード配布処理をスキップする（`game_type=offline` の場合はホールカード生成・配布を行わない）。

---

## DB スキーマ変更

### `games.game_type` に `offline` を追加

```sql
-- 既存: game_type VARCHAR(32) NOT NULL DEFAULT 'ring'
-- 新規 migration で CHECK 制約またはアプリ層バリデーションを追加
ALTER TABLE games ADD CONSTRAINT chk_game_type
  CHECK (game_type IN ('ring', 'offline', 'tournament'));
```

### `players` テーブルの拡張（V16）

```sql
-- オフラインセッション用のバイイン額を保存
ALTER TABLE players ADD COLUMN offline_buy_in INT;
ALTER TABLE players ADD COLUMN offline_cash_out INT;    -- 利確時に確定
ALTER TABLE players ADD COLUMN cashout_at TIMESTAMP;    -- NULL=未確定、NOT NULL=確定済み
```

`offline_profit`（`cash_out - buy_in`）と `settlement_status` は派生値のため DB には持たない。`cashout_at` の有無で決済状態を判定する。

---

## 実装方針

- `OfflineGameService` を `application` 層に追加して差分ロジックを集約する（`GameService` にフラグを混在させない）
- **`startHand()`**: `game_type=offline` の場合は `Deck.shuffle()` とホールカード配布ループをスキップする
- **`resolveShowdown()`**: `declared_winners` を受け取り、`splitPots()` を直接呼ぶ（`HandEvaluator` は使わない）。サイドポット計算は `totalContrib` ベースなのでカード不要で流用可能
- ベット・アクションバリデーション・ポット計算はリングと共通ロジックをそのまま使う
- `WalletService` は呼び出さない
- **UI 表示フラグ**（`mirrored_bet_display` 等）はサーバー側のデータモデルに持たせない。逆さ表示は CSS `transform: rotate(180deg)` でフロントエンドが実装する

---

## 除外した提案（Codex 提案より）

以下は過剰設計またはフロントエンド責務として除外した：

| 提案 | 除外理由 |
|---|---|
| `offline_mode: "physical_cards"` | `game_type=offline` と同義。フラグ重複 |
| `auto_deal_enabled: false` | `game_type` で分岐すれば十分 |
| `seat_label` | `player_index` が既にシート番号と一対一対応 |
| `seat_locked` | `status=in_progress` になった時点でロック済み |
| `dealer_rotation_rule` | 既存の `nextActive(buttonIndex)` が担う |
| `offline_profit` | 派生値（API で計算して返す） |
| `settlement_status` | `cashout_at IS NULL/NOT NULL` で代替 |
| `declared_payouts` | サーバーが計算して返す。クライアントから受け取らない |
| `pot_reconciled` | サーバーが `splitPots()` で保証する |
| `mirrored_bet_display` | CSS 実装。DB に持たせない |
| `device_orientation_hint` | スマホ前提なら固定でよい |
| 専用 audit イベントテーブル | 既存テーブルのタイムスタンプで追跡可能 |

---

## 依存する先行作業

- テーブル機能（`tables`）— V7 で実装済み
- リングゲームの安定動作

---

## バグ修正メモ（feature/offline-mode）

### テーブル作成画面

1. **作成者が自動参加しない**
   - 旧: `createGame` が `invite_code` を返して RoomScreen で表示するだけ（着席しない）
   - 修正: オフライン作成後も `setOfflineSeatTable(data)` を呼んで OfflineSeatPopup を表示する。招待コードを `defaultInviteCode` として pre-fill する。

2. **オンライントグルのドットが縦ずれ**
   - `game-type-dot` span に `verticalAlign: 'middle'` を追加。

### オフライン着席ポップアップ（OfflineSeatPopup）

- **名前フィールドは不要**: `defaultName` prop の値を API に渡すが UI には出さない
- **バイインフィールドは不要**: 着席後に通常の BuyInPopup を使う（リングと同一フロー）
- **シート番号**: テーブル図のボタンで選択
- **招待コード**: 引き続き必須。`prefilledCode` prop で招待リンクからの場合は自動入力

### 着席後のバイインフロー

```
OfflineSeatPopup 確定
  → showBuyInPopup を表示（offlineSeatTable の min/max_buy_in を使用）
  → バイイン確定 → seatOffline(tableId, { seat_index, name, buy_in, invite_code })
  → goToTable → 通常のリングと同じゲーム画面へ
```

### テーブル画面の招待コード表示

- `App.tsx` から `GameScreen` に `table` prop を追加
- `TopBar.tsx` で `table.game_type === 'offline'` かつ `table.invite_code` がある場合に表示

### リバイ時の inviteCode バリデーションエラー

- `OfflineSeatRequest.inviteCode` に `@NotBlank` があり、リバイ時に空文字を送ると 400 エラーが発生していた
- 修正: `@NotBlank` を削除。招待コード検証はサービス層（`OfflineSessionService.seatPlayer`）がリバイをスキップする設計なので DTO での強制は不要

### 退席後にシートが空かない

- 認証済みユーザーが退席すると `TableMembershipService.leave` が `publicId` でメンバーを検索するが、オフラインメンバーは `publicId = null` で登録されるためヒットせず退席処理がスキップされた
- 修正: `publicId` 検索失敗時に `name` ベースのフォールバック検索を追加（詳細は DECISIONS.md）

### 招待リンク OTP 自動入力

- 招待 URL に `?code={inviteCode}` を付与
- `OfflineSeatPopup` の `prefilledCode` prop に渡し、OTP 入力欄に自動セット
- OTP 生成文字セットを数字のみ（6 桁）に変更（`"0123456789"`）

### 自分のハンドチップ増減表示

- `OfflineGameScreen` が `finished` 遷移ごとに直前スタックとの差分を記録
- `offline-payout-list` 領域に増（緑）/減（赤）で表示
- 初回ハンドは基準なしのため非表示
