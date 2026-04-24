# Seat Layout Test

9 人卓までのシート配置とチップ重なりを手元で確認するための手順。

## 目的

- `5` 人卓と `9` 人卓で `player-seat` が互いに重ならないこと
- `player-seat` が viewport 外にはみ出さないこと
- ベットチップがシート情報と視覚的に干渉していないこと
- 並列 join 時に seat が重複しないこと

## 前提

- `docker compose --env-file .env.local up -d` 済み
- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- `mapoker-frontend/node_modules` が存在すること
- Playwright のブラウザがインストール済みであること

## 1. テーブルと検証ユーザーを作る

認証は `register` のレスポンスでセッション Cookie が発行される。手動検証ではこの Cookie を保存して使う。

例:

```bash
curl -s -c /tmp/seat5_p1.cookies \
  -X POST http://localhost:8080/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"seat5p1_check","password":"pass1234"}'
```

ホスト用 Cookie でテーブルを作る。

```bash
curl -s -b /tmp/seat5_host.cookies \
  -X POST http://localhost:8080/v1/tables \
  -H 'Content-Type: application/json' \
  -d '{"table_name":"Seat Check 5","player_count":5,"small_blind":5,"big_blind":10,"visibility":"public","flags":[]}'
```

各ユーザーを順番に join させる。並列 join は別途バックエンドの race 検証として扱う。

```bash
curl -s -b /tmp/seat5_p1.cookies \
  -X POST http://localhost:8080/v1/tables/{table_id}/join \
  -H 'Content-Type: application/json' \
  -d '{"name":"seat5p1_check","buy_in":200}'
```

全員 join 後にハンドを開始する。

```bash
curl -s -b /tmp/seat5_host.cookies \
  -X POST http://localhost:8080/v1/games/{table_id}/start \
  -H 'Content-Type: application/json' \
  -d '{"big_blind":10}'
```

同じ流れで `player_count: 9` の卓も作る。

## 2. Frontend で viewer セッションを差し込む

Frontend は `localStorage` の `mapoker.session.{table_id}` を見て viewer seat を決める。ブラウザ起動前に以下を入れる。

```json
{
  "name": "seat9p1_check",
  "seatIndex": 3,
  "updatedAt": "2026-04-24T00:00:00.000Z"
}
```

Cookie と `localStorage` が入った状態で `http://localhost:3000/?tableId={table_id}` を開く。

## 3. Playwright で目視確認と実測を取る

以下を確認する。

- `.player-seat` の矩形同士に overlap がない
- `.player-seat` が viewport 外に出ていない
- `.bet-chip` がシート情報に食い込んで見えない
- `5` 人卓 / `9` 人卓のスクリーンショットを保存する

実行時の採取物の例:

- `/tmp/seatcheck5.png`
- `/tmp/seatcheck9.png`
- `/tmp/seatcheck_results.json`

`seatcheck_results.json` には以下を最低限入れる。

- viewport サイズ
- 各 `.player-seat` の矩形
- overlap 判定結果
- viewport 外 seat 一覧

## 4. 並列 join の確認

API レベルで同じテーブルに対して複数の join を同時に投げる。期待値は以下。

- `assigned_seat_index` が全件で一意
- 最終的な member 数が期待人数と一致
- `seat_index` の重複がない

自動テストは `mapoker-backend/src/test/java/com/mapoker/TableServiceConcurrencyTest.java` を使う。

```bash
cd mapoker-backend
./mvnw -Dtest=TableServiceConcurrencyTest test
```

## 5. チェック後に見る場所

- シート座標ロジック: `mapoker-frontend/src/utils.ts`
- シート見た目: `mapoker-frontend/src/App.css`
- 卓参加の排他: `mapoker-backend/src/main/java/com/mapoker/application/TableService.java`
