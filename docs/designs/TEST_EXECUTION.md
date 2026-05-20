# テスト・起動ガイド

> **重要**: 以下のコマンドはすべて devcontainer 内で実行する必要がある。

## フォーマット

```bash
./mvnw spotless:apply
./mvnw spotless:check
```

## 単体テスト

```bash
./mvnw test
./mvnw jacoco:report
```

## 結合テスト

PostgreSQL を使う結合テストは Testcontainers で自動起動する。単体テストと分離して実行可能。

```bash
./mvnw verify
```

## API をローカル起動

`local` プロファイルでは認証が無効化される。

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

## PostgreSQL 付きで API を起動

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mapoker \
SPRING_DATASOURCE_USERNAME=mapoker \
SPRING_DATASOURCE_PASSWORD=mapoker \
SPRING_PROFILES_ACTIVE=local,postgresql \
./mvnw spring-boot:run
```

Docker Compose で DB だけ起動する場合:

```bash
docker compose up -d db
./mvnw spring-boot:run
```

## DB マイグレーション

アプリ起動時に Flyway が自動実行される。手動実行する場合:

```bash
./mvnw flyway:migrate
```

## curl による疎通確認

フロントエンドなしでも `curl` だけで完全なゲームを実行できる。

### ユーザー登録（セッション Cookie 取得）

```bash
curl -s -c /tmp/cookies.txt \
  -X POST http://localhost:8080/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"secret"}'
```

### ゲーム作成（匿名）

```bash
curl -s -X POST http://localhost:8080/v1/games \
  -H 'Content-Type: application/json' \
  -d '{"players":[{"id":"p1","stack":100},{"id":"p2","stack":100}],"button_index":0,"big_blind":10}'
```

### ハンド開始

```bash
curl -s -X POST http://localhost:8080/v1/games/{game_id}/start \
  -H 'Content-Type: application/json' \
  -d '{"big_blind":10}'
```

### ゲーム状態取得

```bash
curl -s http://localhost:8080/v1/games/{game_id}?viewer_index=0
```

### アクション実行

```bash
curl -s -X POST http://localhost:8080/v1/games/{game_id}/actions \
  -H 'Content-Type: application/json' \
  -d '{"player_index":1,"action":{"type":"fold","amount":0}}'
```

## カバレッジ目標

全体 80% 以上。特に優先するテスト対象:

- hand evaluator
- action validation
- street progression
- showdown / side-pot distribution
- repository のトランザクション挙動
