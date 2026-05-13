# API リファレンス

Texas Hold'em ポーカー HTTP API の仕様。

## 基本方針

- ベース URL: `http://localhost:8080`（本番: `https://mapoker.marciadesign.org/api`）
- リクエスト / レスポンスは JSON
- HTTP はステートレス。ゲーム状態は `game_id` / `table_id` で読み込む
- 認証は Spring Security セッション Cookie（`JSESSIONID`）

## エラー形式

```json
{
  "error": {
    "code": "invalid_action",
    "message": "cannot raise: betting not reopened"
  }
}
```

---

## 認証 API — `/v1/auth`

セッション Cookie ベース。ローカルプロファイルでは認証が無効化される（`SPRING_PROFILES_ACTIVE=local`）。

### ユーザー登録

`POST /v1/auth/register`

登録と同時にセッションを確立して返す（別途ログイン不要）。

リクエスト:
```json
{ "username": "alice", "password": "secret" }
```

レスポンス `201 Created` + `Set-Cookie: JSESSIONID=...`:
```json
{ "id": 1, "username": "alice" }
```

### ログイン

`POST /v1/auth/login`

リクエスト:
```json
{ "username": "alice", "password": "secret" }
```

レスポンス `200 OK` + `Set-Cookie: JSESSIONID=...`:
```json
{ "id": 1, "username": "alice" }
```

### 現在ユーザー取得

`GET /v1/auth/me`

レスポンス `200 OK` / `401 Unauthorized`:
```json
{ "id": 1, "username": "alice" }
```

### プロフィール更新

`PUT /v1/auth/me`

リクエスト（すべてのフィールドは省略可能）:
```json
{
  "new_username": "alice2",
  "current_password": "secret",
  "new_password": "newsecret"
}
```

ユーザー名を変更した場合はセッションを再発行する。レスポンス `200 OK` / `400 Bad Request` / `401 Unauthorized`。

### テーブル参加履歴

`GET /v1/auth/history`

直近のテーブル参加記録を返す。レスポンス `200 OK` / `401 Unauthorized`:
```json
[
  {
    "table_id": "550e8400-...",
    "table_name": "Cash Orbit Tokyo",
    "seat_index": 1,
    "visibility": "public",
    "status": "active",
    "flags": ["casual"],
    "joined_at": "2026-04-21T03:00:00Z",
    "left_at": null,
    "active": true
  }
]
```

### ハンド履歴

`GET /v1/auth/hand-history?limit=20`

直近のハンド履歴を返す。レスポンス `200 OK` / `401 Unauthorized`:
```json
[
  {
    "hand_id": "uuid",
    "table_id": "uuid",
    "pot": 200,
    "street": "river",
    "winners": [0],
    "players": [...],
    "finished_at": "2026-04-21T04:00:00Z"
  }
]
```

### ログアウト

`POST /v1/auth/logout`

セッションを破棄する。レスポンス `204 No Content`。

---

## テーブル API — `/v1/tables`

テーブル作成・一覧・参加・退室を管理する。

### テーブル作成

`POST /v1/tables`

リクエスト:
```json
{
  "table_name": "Cash Orbit Tokyo",
  "player_count": 6,
  "small_blind": 5,
  "big_blind": 10,
  "visibility": "public",
  "flags": ["casual", "newbie"]
}
```

- `small_blind` 省略時は `big_blind / 2`
- `visibility`: `public` または `private`
- `flags`: 小文字スネークケース、最大 8 件

レスポンス `201 Created`:
```json
{
  "id": "uuid",
  "room_id": "uuid",
  "name": "Cash Orbit Tokyo",
  "game_type": "ring",
  "small_blind": 5,
  "big_blind": 10,
  "min_buy_in": 200,
  "max_buy_in": 1000,
  "max_players": 6,
  "visibility": "public",
  "status": "waiting",
  "flags": ["casual", "newbie"],
  "members": [],
  "game": { ...GameResponse... }
}
```

### テーブル一覧

`GET /v1/tables?visibility=public&flags=casual`

### テーブル取得

`GET /v1/tables/{id}`

### テーブル参加者一覧

`GET /v1/tables/{id}/members`

### テーブル参加

`POST /v1/tables/{id}/join`

リクエスト（認証済みの場合 `name` はサーバー側で解決）:
```json
{ "name": "alice", "buy_in": 200 }
```

レスポンス:
```json
{
  "assigned_seat_index": 1,
  "members": [
    { "name": "alice", "seat_index": 1, "joined_at": "...", "pending_leave": false }
  ]
}
```

### テーブル退室

`POST /v1/tables/{id}/leave`

リクエスト（認証済みの場合 `name` 省略可）:
```json
{ "name": "alice" }
```

---

## ルーム API — `/v1/rooms`

テーブルと同じ `id` を使う参加者管理 API。認証フローなしに名前ベースで参加できる簡易エイリアス。

### 参加者一覧

`GET /v1/rooms/{id}/members`

### 参加

`POST /v1/rooms/{id}/join`

リクエスト:
```json
{ "name": "alice", "buy_in": 200 }
```

### 退室

`POST /v1/rooms/{id}/leave`

リクエスト:
```json
{ "name": "alice" }
```

---

## ゲーム API — `/v1/games`

ゲームの作成・操作。テーブルを介さない直接ゲーム操作も可能（デバッグ / curl 検証用）。

### ゲーム作成

`POST /v1/games`

リクエスト:
```json
{
  "players": [
    { "id": "p1", "stack": 100 },
    { "id": "p2", "stack": 100 }
  ],
  "button_index": 0,
  "big_blind": 10,
  "seed": 42,
  "odd_chip_rule": "low_index"
}
```

レスポンス `201 Created`: → GameResponse

### ゲーム一覧

`GET /v1/games`

### ゲーム取得

`GET /v1/games/{id}?viewer_index=0&spectator=0`

- `viewer_index`: 表示するプレイヤーのインデックス（認証済み時は自動解決）
- `spectator=1` / `spectator=true`: ホールカードを一切含まない観戦モード

### ハンド開始

`POST /v1/games/{id}/start`

リクエスト:
```json
{ "big_blind": 10 }
```

### アクション適用

`POST /v1/games/{id}/actions?viewer_index=0`

リクエスト:
```json
{
  "player_index": 2,
  "action": { "type": "raise", "amount": 40 }
}
```

`bet` / `raise` の `amount` は raise-to 合計額。`call` は `amount=0` で auto-call。

### アクション一覧

`GET /v1/games/{id}/actions`

```json
{
  "actions": [
    { "seq": 1, "player_index": 0, "type": "call", "amount": 10 }
  ]
}
```

### ショーダウン実行

`POST /v1/games/{id}/showdown`

---

## GameResponse 構造

```json
{
  "id": "uuid",
  "status": "in_progress",
  "street": "preflop",
  "button_index": 0,
  "small_blind_idx": 1,
  "big_blind_idx": 2,
  "current_player": 3,
  "current_bet": 20,
  "last_raise_size": 10,
  "big_blind": 10,
  "pot_total": 30,
  "odd_chip_rule": "low_index",
  "can_start_hand": false,
  "viewer_membership_active": true,
  "can_rebuy": false,
  "players": [
    {
      "id": "p1",
      "stack": 80,
      "contributed": 20,
      "folded": false,
      "all_in": false,
      "hole": ["AS", "KH"]
    }
  ],
  "community": ["2S", "3S", "4S"],
  "last_showdown": {
    "winners": [0],
    "best_hand": { "rank": "Flush", "kickers": [14, 10, 8, 5, 3] },
    "payouts": [200, 0]
  }
}
```

- `hole` はリクエストした閲覧者に見せてよい場合のみ含む
- ショーダウン / フィニッシュ（フォールドウィン以外）では全プレイヤーのホールカードを公開
- `can_start_hand`: 次のハンドを開始できる状態かどうか
- `can_rebuy`: 閲覧者のスタックが 0 でリバイ可能かどうか

---

## ウォレット API — `/v1/wallet`（postgresql プロファイルのみ）

### ウォレット残高取得

`GET /v1/wallet`

```json
{
  "chip_balance": 9500,
  "next_daily_bonus_at": "2026-04-22T03:00:00Z",
  "next_recovery_at": null
}
```

### 台帳履歴

`GET /v1/wallet/ledger?limit=20`

### デイリーボーナス

`POST /v1/wallet/daily-bonus`

クールダウン: 24 時間（`wallet.daily-bonus-cooldown-hours`）。

### 救済ボーナス

`POST /v1/wallet/recovery`

残高が閾値（デフォルト 1000 chips）以下の場合のみ請求可能。クールダウン: 12 時間。

---

## 管理者 API — `/v1/admin`（postgresql プロファイルのみ）

### チップ付与

`POST /v1/admin/wallet/grants`

`wallet.admin-usernames` に登録されたユーザーのみ実行可能。

リクエスト:
```json
{ "target_username": "alice", "amount": 5000 }
```

---

## 型定義

### アクション種別

`fold | check | call | bet | raise | all_in`

### Status

`in_progress | showdown | finished`

### Street

`preflop | flop | turn | river`

### OddChipRule

`low_index | button_left`

---

## ホールカード可視性ルール

- ハンド進行中: `viewer_index` に対応するプレイヤーのカードのみ返す
- ショーダウン / フィニッシュ（フォールドウィン以外）: 折りたたんでいない全プレイヤーのカードを返す
- 観戦モード（`spectator=1`）: 全カードを非表示
- 認証済みユーザー: テーブルシートインデックスを自動解決して適切なカードを返す
