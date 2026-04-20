# Phase 1: Backend Security

codex-implement へ渡す実装仕様。

---

```
objective:
  1. Session Cookie に Secure / HttpOnly / SameSite=Lax 属性を付与する
  2. リバースプロキシ越しのX-Forwarded-Protoを信頼する設定を追加する
  3. /v1/auth/login と /v1/auth/register にIPベースのレート制限を実装する
     - 上限: 10回/分（IP単位）
     - 超過時: HTTP 429 を返す
     - ライブラリ: Bucket4j（in-memory、Spring Boot 3.x対応）

scope:
  mapoker-backend モジュール全体

allowed_files:
  - mapoker-backend/pom.xml
  - mapoker-backend/src/main/resources/application.properties
  - mapoker-backend/src/main/java/com/mapoker/infrastructure/config/SecurityConfig.java
  - mapoker-backend/src/main/java/com/mapoker/infrastructure/config/RateLimitConfig.java  （新規作成）
  - mapoker-backend/src/main/java/com/mapoker/interfaces/http/filter/LoginRateLimitFilter.java  （新規作成）
  - mapoker-backend/src/test/java/com/mapoker/interfaces/http/AuthControllerRateLimitTest.java  （新規作成）

forbidden_changes:
  - AuthController.java のビジネスロジックを変更しない
  - UserService.java を変更しない
  - DB マイグレーションファイルを作成しない
  - public API の URL / レスポンス形式を変更しない
  - ドメイン層 (com.mapoker.domain) を変更しない
  - FQCN をコード中に直書きしない（import 文に書くこと）
  - com.mapoker.domain パッケージに Spring 依存を追加しない

acceptance_criteria:
  - application.properties に以下が追加されていること:
      server.servlet.session.cookie.http-only=true
      server.servlet.session.cookie.secure=true
      server.servlet.session.cookie.same-site=Lax
      server.forward-headers-strategy=framework
  - pom.xml に bucket4j-core の依存が追加されていること（Spring Boot BOM管理外のためバージョン明記）
  - /v1/auth/login に対して同一IP から11回連続リクエストすると429が返ること
  - /v1/auth/register に対して同一IP から11回連続リクエストすると429が返ること
  - 429 レスポンスに Retry-After ヘッダーが含まれること
  - localプロファイルではレート制限が無効になること（開発の妨げにならないよう @Profile("!local") で制御）
  - ./mvnw test が通ること

output_format:
  ## Changed Files
  ## What Was Implemented
  ## Remaining Work
  ## Risks
  ## Suggested Verification Steps
```

---

## 実装ガイダンス（orchestratorが提供する判断）

### Cookie設定

`application.properties` に追記するだけで完結する：

```properties
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.same-site=Lax
server.forward-headers-strategy=framework
```

`secure=true` はローカルでは HTTPS なしのため Set-Cookie が効かないが、localプロファイルでの開発に支障はない（SessionFixation保護は引き続き動作する）。

### Bucket4j レート制限

依存を追加する（バージョンは Spring Boot 3.5.x 対応の 8.x 系）：

```xml
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.10.1</version>
</dependency>
```

実装方針：
- `LoginRateLimitFilter` を `OncePerRequestFilter` として実装
- `/v1/auth/login` と `/v1/auth/register` にのみ適用
- `RemoteAddr` をキーにして `ConcurrentHashMap<String, Bucket>` で管理
- `@Profile("!local")` で本番のみ有効化
- 429 レスポンスに `Retry-After: 60` ヘッダーを付与
- `RateLimitConfig` Bean でフィルターを `FilterRegistrationBean` 経由で登録

### localプロファイルでの無効化

`SecurityConfig` の `localSecurityFilterChain` はフィルターチェーンを分けており、`@Profile("local")` と `@Profile("!local")` で分離されている。フィルターも同様に `@Profile("!local")` で制御する。
