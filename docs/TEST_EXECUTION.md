# テスト・起動計画（Java）

現時点のリポジトリには設計書しかありません。以下のコマンドは、今後 Java 実装を作る際に標準フローとして用意したい内容です。

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
PostgreSQL を使う結合テストは、高速な単体テストとは分けて実行する。

```bash
./mvnw verify
```

推奨構成:
- 通常のローカル実行 / CI では Testcontainers を使う
- 手動確認向けには `docker compose` ベースの PostgreSQL 起動手段も用意する

## API をローカル起動
移行初期は匿名ローカルプロファイルで起動できるようにする。

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

## PostgreSQL 付きで API を起動
環境変数の例:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mapoker \
SPRING_DATASOURCE_USERNAME=mapoker \
SPRING_DATASOURCE_PASSWORD=mapoker \
SPRING_PROFILES_ACTIVE=local,postgresql \
./mvnw spring-boot:run
```

後で Docker Compose を用意する場合の想定フロー:

```bash
docker compose up -d db
./mvnw spring-boot:run
```

## DB マイグレーション
ローカル開発ではアプリ起動時に Flyway が実行される想定にする。手動実行もできるようにする。

```bash
./mvnw flyway:migrate
```

## HTTP シナリオ
フロントエンドが無くても、`curl` で最低限の疎通確認ができるようにする。

### 作成
```bash
curl -s -X POST http://localhost:8080/v1/games \
  -H 'Content-Type: application/json' \
  -d '{"players":[{"id":"p1","stack":100},{"id":"p2","stack":100}],"button_index":0,"big_blind":10,"odd_chip_rule":"low_index"}'
```

### 開始
```bash
curl -s -X POST http://localhost:8080/v1/games/{game_id}/start \
  -H 'Content-Type: application/json' \
  -d '{"big_blind":10}'
```

### 取得
```bash
curl -s http://localhost:8080/v1/games/{game_id}
```

### アクション実行
```bash
curl -s -X POST http://localhost:8080/v1/games/{game_id}/actions \
  -H 'Content-Type: application/json' \
  -d '{"player_index":1,"action":{"type":"fold","amount":0}}'
```

## カバレッジ目標
- 全体目標: 80% 以上
- 特に優先して厚くテストする箇所:
  - hand evaluator
  - action validation
  - street progression
  - showdown / side-pot distribution
  - repository のトランザクション挙動
