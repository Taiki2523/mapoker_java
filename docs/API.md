# API 設計（Java 下書き）

この文書は `mapoker_java` の初期 HTTP API を定義するものです。API の表面仕様は Go 版に近い形を保ちつつ、実装先は Spring Boot ベースの Java サービスとします。

## 基本方針
- 将来の Web フロントエンドを前提にした API-first 設計にする
- リクエスト / レスポンスは JSON を使う
- HTTP はステートレスに保ち、現在のゲーム状態は `game_id` で読み込む
- `seed` が指定された場合は決定的に動作する
- エラー形式は統一する
- ドメインルールは HTTP フレームワークの詳細に依存させない

## 実装前提
- ベース URL: `http://localhost:8080`
- Controller は `com.mapoker.interfaces.http` 配下に置く
- DTO 変換はドメインオブジェクトから分離する
- トランザクション制御と repository 呼び出しの調停は application service が担当する

## エラー形式
```json
{
  "error": {
    "code": "invalid_action",
    "message": "cannot raise: betting not reopened",
    "details": {}
  }
}
```

## リソース

### Game
進行中の 1 ハンド / 1 セッションを表す。

レスポンス例:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "in_progress",
  "street": "preflop",
  "button_index": 0,
  "current_player": 2,
  "current_bet": 40,
  "last_raise_size": 20,
  "big_blind": 10,
  "pot_total": 120,
  "players": [
    {
      "id": "p1",
      "stack": 80,
      "contributed": 40,
      "folded": false,
      "all_in": false,
      "hole": ["AS", "KH"]
    }
  ],
  "community": ["2S", "3S", "4S", "5S", "7D"],
  "odd_chip_rule": "low_index"
}
```

`hole` は、その閲覧者に見せてよい場合だけ返す。

### ショーダウン
```json
{
  "winners": [0, 2],
  "best_hand": {
    "rank": "StraightFlush",
    "kickers": ["A"]
  },
  "payouts": [0, 0, 70]
}
```

## エンドポイント（v1）

### ゲーム作成
`POST /v1/games`

```json
{
  "players": [
    {"id": "p1", "stack": 100},
    {"id": "p2", "stack": 100}
  ],
  "button_index": 0,
  "big_blind": 10,
  "seed": 42,
  "odd_chip_rule": "low_index"
}
```

### ハンド開始
`POST /v1/games/{game_id}/start`

```json
{
  "big_blind": 10
}
```

### ゲーム状態取得
`GET /v1/games/{game_id}`

### アクション適用
`POST /v1/games/{game_id}/actions`

```json
{
  "player_index": 2,
  "action": {
    "type": "raise",
    "amount": 40
  }
}
```

### アクション一覧取得
`GET /v1/games/{game_id}/actions`

```json
{
  "actions": [
    {"seq": 1, "player_index": 0, "type": "call", "amount": 0}
  ]
}
```

### ショーダウン実行
`POST /v1/games/{game_id}/showdown`

ショーダウン結果と payout を返す。

### ゲーム一覧取得（任意）
`GET /v1/games`

## 認証 API

ユーザー名 / パスワード認証。Spring Security のセッション Cookie ベース。

### ユーザー登録
`POST /v1/auth/register`

```json
{ "username": "alice", "password": "secret" }
```

レスポンス (`201 Created`):
```json
{ "id": "uuid", "username": "alice" }
```

### ログイン
`POST /v1/auth/login`

```json
{ "username": "alice", "password": "secret" }
```

セッション Cookie (`JSESSIONID`) を HttpOnly / Secure / SameSite=Lax で発行する。

レスポンス (`200 OK`):
```json
{ "id": "uuid", "username": "alice" }
```

### 現在ユーザー取得
`GET /v1/auth/me`

セッション Cookie から現在ユーザーを返す。未認証時は `401`。

### ログアウト
`POST /v1/auth/logout`

セッションを破棄する。レスポンス: `204 No Content`。

## アクション種別
`fold | check | call | bet | raise | all_in`

`amount` の意味:
- `bet` と `raise` では raise-to の合計額を表す
- `call` では省略または `0` を指定すると auto-call として扱ってよい

## Street の値
`preflop | flop | turn | river`

## 可視性に関する注意
- hole cards の表示可否はリクエストした利用者に依存する
- ハンド進行中は、自分の hole cards だけが見える
- showdown では公開された hole cards を全員が見られる
- 管理者 / デバッグ用の完全可視性はコアドメインの外で扱う

## Java 実装メモ
- ドメインオブジェクトをそのまま返さず、`@RestController` と request / response DTO を使う
- ドメインエラーは集中管理された例外ハンドラで安定した API エラーコードへ変換する
- Controller の責務は薄く保ち、リクエスト形式を超える業務バリデーションは application / domain 層へ置く
- repository 実装の SQL や永続化 DTO を Controller 側へ漏らさない
