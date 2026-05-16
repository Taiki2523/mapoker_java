# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Status

Java 実装は完成・稼働中（v1.0.2）。Go プロジェクト（`/home/taiki/projects/mapoker`）は参照実装として残すが、主体は本リポジトリの Java 実装に移っている。

## Commands

> **重要**: 以下のコマンドはすべてdevcontainer内で実行する必要があります。Claude Codeはdevcontainer内でコマンドを直接実行できないため、実行が必要な場合はユーザーに依頼してください。

```bash
# Format
./mvnw spotless:apply
./mvnw spotless:check

# Test
./mvnw test
./mvnw verify                              # unit + integration tests
./mvnw test -Dtest=SomeTest                # single test class

# Run
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run

# Run with PostgreSQL
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/mapoker \
SPRING_DATASOURCE_USERNAME=mapoker \
SPRING_DATASOURCE_PASSWORD=mapoker \
SPRING_PROFILES_ACTIVE=local,postgresql \
./mvnw spring-boot:run

# DB migration (runs automatically on startup; manual override)
./mvnw flyway:migrate
```

## フロントエンド設計方針

**ゲームロジックはバックエンド（API）にのみ置くこと。**

- フロントエンドは表示とユーザー操作の中継のみを担当する
- ゲームの進行判定・状態遷移・勝敗判定・カード評価はすべて Java 側で完結させる
- API のレスポンスだけで次にすべき操作がわかるように設計する（`can_start_hand`, `current_player`, `status` 等）
- フロントなしでも `curl` だけでゲームを完全に実行できる状態を維持すること
- フロントが「ショーダウンかどうか」「誰のターンか」などを独自に計算する実装は禁止

**ホールカードの可視性もバックエンドで制御する：**
- ハンド進行中：`viewer_index` クエリパラメータで指定されたプレイヤーのカードのみ返す
- ショーダウン・終了時：折りたたんでいない全プレイヤーのカードを返す（`status: showdown / finished`）
- フロントが独自にカードを隠す実装は禁止

## Architecture

**Texas Hold'em poker engine** — Spring Boot 3.x HTTP API managing game state by `game_id` (stateless HTTP; no in-memory sessions).

### Package structure

```
com.mapoker.domain          # Pure poker logic: Card, Deck, Hand, evaluator, action validation
com.mapoker.application     # Use-case orchestration, service layer
com.mapoker.infrastructure  # Persistence (PostgreSQL + in-memory), auth (Spring Security), Flyway
com.mapoker.interfaces.http # Controllers, DTOs, Jackson serialization, exception mapping
com.mapoker.interfaces.ws   # WebSocket DTOs (GameBroadcastPayload, HoleCardsPayload, MembersBroadcastPayload)
```

Source roots:
- `src/main/java`, `src/main/resources`
- `src/test/java` (unit tests)
- `src/integrationTest/java` (Testcontainers-based PostgreSQL tests)
- DB migrations: `src/main/resources/db/migration` (Flyway)

### Flyway migration rule

- `src/main/resources/db/migration/V*.sql` は、一度でも適用されたら編集しないこと。既存 migration の変更は禁止。
- スキーマ変更やデータ補正が必要な場合は、必ず新しい versioned migration を追加すること。
- `V` の番号が増えること自体は問題として扱わない。履歴を短くしたい場合は既存 `V` を潰さず、`B*.sql` の baseline migration を追加すること。
- checksum mismatch が出た場合、まず既存 migration の編集有無を疑う。安易に `repair` しない。
- `flyway repair` はローカル開発や合意済み環境の例外対応に限定し、理由を `docs/designs/FLYWAY.md` か PR に残すこと。
- 詳細ルールは `docs/designs/FLYWAY.md` を参照。

### Key design decisions

- **Domain is framework-free.** `com.mapoker.domain` has zero Spring dependencies; all poker rules are plain Java 21.
- **Repository port.** Application layer depends on a `GameRepository` interface. Two implementations: in-memory (tests/local) and PostgreSQL (prod). Swap via Spring profile.
- **Transactional writes.** Updating `games`/`players` and inserting into `actions` must happen in a single transaction. See `docs/designs/db_schema.md`.
- **Explicit SQL.** Use `JdbcTemplate` / Spring JDBC — no ORM. JSON columns serialized/deserialized with Jackson.
- **`bet`/`raise` amount is raise-to total** (not the increment). `call` with `amount=0` is auto-call.
- **Minimum raise** = `max(big blind, last raise size)`. Sub-min all-in is allowed but does not reopen betting (`raiseOpen=false`).
- **Odd chip rule** is configurable per game (`low_index` default, `button_left` optional).
- **Hole card visibility**: show only to the owning player during a hand; reveal all at showdown. Visibility enforcement belongs in the HTTP layer, not the domain.
- **Auth**: Google ID token verification in infrastructure layer. `SPRING_PROFILES_ACTIVE=local` disables auth for development.

### Game state machine

```
CreateGame → StartHand → [preflop → flop → turn → river] → showdown → finished
```

Status values: `in_progress`, `showdown`, `finished`. See `docs/designs/GAME_FLOW.md` for full Mermaid diagrams of each transition.

### HTTP API (13 endpoints)

```
POST   /v1/games
GET    /v1/games
GET    /v1/games/{id}
POST   /v1/games/{id}/start
POST   /v1/games/{id}/actions
GET    /v1/games/{id}/actions
POST   /v1/games/{id}/showdown
GET    /v1/rooms/{id}/members
POST   /v1/rooms/{id}/join
POST   /v1/rooms/{id}/leave
POST   /v1/auth/google
GET    /v1/auth/me
POST   /v1/auth/logout
```

### Database schema (4 tables)

`games`, `players`, `actions`, `users` — see `docs/designs/db_schema.md` for DDL. Key: `deck`, `community`, `hole`, `acted` are JSON columns.

### Testing priorities (85% coverage target)

カバレッジは JaCoCo で計測（`./mvnw verify` で `target/site/jacoco/index.html` が生成される）。
設定クラス・DTO・PostgresRepository は除外済み。`./mvnw verify` が 85% 未達の場合はビルドが失敗する。

**優先順位の方針**: 変更頻度 × 複雑度で優先する。バグが出たら即テスト追加（リグレッション防止）。

**テストファイルの分割規則**（`GameState` を例に）:
- `GameStateTest.java` — 基本フロー（ハンド開始・ブラインド・ボタン移動）
- `GameStateSidePotTest.java` — サイドポット計算・showdown 解決・odd chip
- `GameStateBettingTest.java` — raiseOpen・ストリート進行・ベット額境界

**モックを使う場合**: `UserService` 等アプリ層は `InMemoryUserRepository` をそのまま使うと Spring 不要で速い。
`ObjectProvider` 依存がある場合は Mockito で stub する。

**ドメイン層のテストは Spring 不要**: `GameState`, `ActionValidator`, `HandEvaluator` は `@SpringBootTest` なしで書く（起動が速い）。

Emphasize: action validation, street progression, showdown/side-pot distribution, repository transaction behavior.

### Reference implementation

When porting logic, read the Go source at `/home/taiki/projects/mapoker/internal/core/`. The Java domain layer should be a direct port of that logic.

## コーディング規約

### 定数・設定値の管理

- **ポーカールール固有の定数**（デッキ枚数・ハンド枚数など）は `com.mapoker.domain.PokerConstants` に Java 定数として定義する。設定ファイルに出してはいけない（ルールは変わらないため）。
- **環境依存の設定**（CORS、ゲームデフォルト値など）は `application.properties` に `@ConfigurationProperties` レコードで取得する。マジックナンバーをコード中に直書きしない。
- `CorsProperties` (`cors.*`) と `GameProperties` (`game.*`) が定義済み。新しい設定カテゴリが増えたら同様のレコードを追加すること。
- `@ConfigurationPropertiesScan` は `MapokerApplication` に付与済み。新しい `@ConfigurationProperties` クラスは自動検出される。

### インポート

- FQCN（`java.util.HashSet` などをコード中に直書き）は禁止。すべて `import` 文に書くこと。
- `com.mapoker.domain` パッケージは Spring 非依存を維持すること（import は Java 標準ライブラリのみ）。

### 個人情報・機密情報の扱い

**詳細は `docs/designs/ENVIRONMENT.md` を参照。**

- CLAUDE.md（およびリポジトリにコミットするすべてのファイル）に個人名・メールアドレス・APIキーなどを直書きしない。
- 環境依存の機密値は `.env` / `.env.local` で注入し、`.gitignore` に追加すること。
- 秘密情報用テンプレート：
  - バックエンド: `mapoker-backend/.env.example` をコピーして `.env.local` を作成
  - フロントエンド: `mapoker-frontend/.env.example` をコピーして `.env.local` を作成
- 設定ファイル構成：
  - `application.properties` — 共通設定（コミット可）
  - `application-secret.properties` — 秘密情報（`.gitignore`、作成しない）
  - `.env.local` — ローカル環境変数（`.gitignore`、作成しない）

### その他

- エラーメッセージ文字列はコード中に直書きで構わない（設定化しない）。
- `@Profile("local")` でローカル開発用の実装を切り替える。本番コードに開発用の分岐を混在させない。
