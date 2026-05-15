# Google Auth + username/discriminator ユーザー識別

**ステータス**: `planned`

---

## 背景

現在のWebアプリにはユーザー名 + パスワード認証が実装済み。
これに加えて Google OAuth 2.0 によるログインを追加する。

DBにはメールアドレスを保存しない。
Google アカウントの識別には Google ID Token の `sub` claim のみを使用する。

また、アプリ上のユーザー名は Discord の旧形式のような `username + discriminator` 方式を採用する。

```
taiki#1234
guest#4821
pokerking#9012
```

`username` 単体は重複可能。
`username + discriminator` の組み合わせが一意とする。

---

## 技術スタック（実装時の注意）

このプロジェクトのスタックは以下の通り。要件定義書に記載された内容が実態と異なる場合は、**以下の実態に従うこと**。

| 項目 | 実態 |
|---|---|
| 永続化 | `JdbcTemplate` による明示的 SQL（ORM なし、`@Entity` 禁止） |
| ドメインモデル | Java `record`（Lombok なし） |
| 認証方式 | Spring Security + `HttpSession`（JWT なし） |
| API プレフィックス | `/v1/` |
| パッケージ構成 | `com.mapoker.application` / `com.mapoker.infrastructure.persistence` / `com.mapoker.interfaces.http` |
| 次の Flyway バージョン | `V9` |

---

## 目的

Google Auth によるログイン機能を追加する。

DBにはメールアドレスを保存せず、以下の情報でユーザーを管理する。

| フィールド | 格納先 |
|---|---|
| DB 内部 ID | `users.id` |
| 外部公開 ID | `users.public_id` |
| 表示名 | `users.username` |
| 識別子 | `users.discriminator` |
| Google 識別子 | `user_auth_identities.provider_user_id` |

---

## 設計方針

### ID の役割

| フィールド | 型 | 役割 |
|---|---|---|
| `users.id` | BIGSERIAL | DB 内部主キー。外部 API には返さない |
| `users.public_id` | UUID | 外部公開用。API レスポンスや URL で使用 |
| `users.username` | VARCHAR(50) | 表示名。重複可能 |
| `users.discriminator` | VARCHAR(4) | 同名ユーザーを区別する4桁コード |
| `user_auth_identities.provider_user_id` | VARCHAR(255) | Google sub。メールアドレスは保存しない |

---

## DB 設計

### 既存 `users` テーブルへの変更

`users` テーブルは V2 で作成済みで以下のスキーマ。

```sql
-- 現在の users テーブル（V2）
CREATE TABLE users (
  id            BIGSERIAL    PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

V9 で以下の変更を加える。

- `username` の UNIQUE 制約を削除 → `(username, discriminator)` の複合ユニーク制約に変更
- `discriminator` カラムを追加（`VARCHAR(4) NOT NULL DEFAULT '0000'`）
- `password_hash` を NULL 許可に変更（Google 専用ユーザーはパスワード不要）
- `public_id` を追加
- `avatar_url` を追加

### 新規 `user_auth_identities` テーブル

```sql
CREATE TABLE user_auth_identities (
    id               BIGSERIAL    PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider         VARCHAR(50)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_auth_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_auth_user_provider UNIQUE (user_id, provider)
);
```

---

## Flyway Migration

ファイル名: `V9__add_google_auth.sql`

> **注意**: 既存の V1〜V8 は編集禁止。スキーマ変更は必ず新しい versioned migration で行うこと。

```sql
-- pgcrypto が未導入の場合のみ必要
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- username の UNIQUE 制約を削除
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_username_key;

-- discriminator カラムを追加（既存ユーザーは '0000' を初期値とする）
ALTER TABLE users ADD COLUMN IF NOT EXISTS discriminator VARCHAR(4) NOT NULL DEFAULT '0000';

-- username + discriminator の複合ユニーク制約を追加
-- ただし既存ユーザーで username が重複している場合は事前にデータ補正が必要
ALTER TABLE users ADD CONSTRAINT uq_users_username_discriminator
    UNIQUE (username, discriminator);

-- discriminator フォーマット制約
ALTER TABLE users ADD CONSTRAINT chk_users_discriminator_format
    CHECK (discriminator ~ '^[0-9]{4}$');

-- public_id カラムを追加（既存ユーザーは UUID を自動付与）
ALTER TABLE users ADD COLUMN IF NOT EXISTS public_id UUID UNIQUE DEFAULT gen_random_uuid();
ALTER TABLE users ALTER COLUMN public_id SET NOT NULL;

-- avatar_url カラムを追加
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url TEXT;

-- password_hash を NULL 許可に変更（Google 専用ユーザー対応）
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- user_auth_identities テーブルを作成
CREATE TABLE IF NOT EXISTS user_auth_identities (
    id               BIGSERIAL    PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider         VARCHAR(50)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_auth_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_auth_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX IF NOT EXISTS idx_user_auth_identities_user_id
    ON user_auth_identities (user_id);
```

> **既存ユーザーの discriminator 衝突について**: 既存ユーザーが全員 `discriminator = '0000'` になるため、同名ユーザーが複数いると複合ユニーク制約違反が発生する。この migration 適用前にデータ補正が必要かどうかを確認すること。

---

## ドメインモデル変更

### User レコードの拡張

`com.mapoker.application.User` に以下を追加する。

```java
public record User(
    long id,
    String publicId,   // UUID 文字列
    String username,
    String discriminator,
    String avatarUrl,
    LocalDateTime createdAt
) {}
```

既存の `long id, String username, LocalDateTime createdAt` から拡張。

### UserAuthIdentity レコードを新規追加

`com.mapoker.application.UserAuthIdentity`

```java
public record UserAuthIdentity(
    long id,
    long userId,
    String provider,
    String providerUserId,
    LocalDateTime createdAt
) {}
```

### AuthProvider 定数

```java
public final class AuthProvider {
    public static final String GOOGLE = "google";

    private AuthProvider() {}
}
```

---

## Repository インターフェース設計

プロジェクトの規約に従い、`com.mapoker.application` にインターフェースを定義し、
`com.mapoker.infrastructure.persistence` に `JdbcTemplate` 実装を置く。

### UserRepository の拡張

既存の `UserRepository` インターフェース（`com.mapoker.application.UserRepository`）に以下を追加。

```java
// 既存メソッドに加えて追加するメソッド
Optional<User> findByPublicId(String publicId);
boolean existsByUsernameAndDiscriminator(String username, String discriminator);
User createWithGoogle(String username, String discriminator, String avatarUrl);
```

### UserAuthIdentityRepository（新規）

`com.mapoker.application.UserAuthIdentityRepository`

```java
public interface UserAuthIdentityRepository {
    Optional<UserAuthIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);
    void create(long userId, String provider, String providerUserId);
}
```

---

## Service 設計

### GoogleTokenVerifier（新規）

`com.mapoker.application.GoogleTokenVerifier`

責務: Google ID Token の検証と claims 抽出。

```java
public record GoogleUserClaims(
    String subject,
    String name,
    String picture
) {}
```

検証内容:
- トークン署名
- `iss` (`accounts.google.com` または `https://accounts.google.com`)
- `aud` (設定された `GOOGLE_CLIENT_ID` と一致)
- `exp` (有効期限)

保存する claims: `sub`, `name`, `picture`
**保存しない claims**: `email`, `email_verified`

Google の公式ライブラリ (`google-api-client`) を使用するか、
`nimbus-jose-jwt` など既存の JWT ライブラリで手動検証するか、
どちらでも可。pom.xml への依存追加が必要。

**設定**:

```yaml
# application.properties に追加
app.auth.google.client-id=${GOOGLE_CLIENT_ID:}
```

### UsernameNormalizer（新規）

`com.mapoker.application.UsernameNormalizer`

正規化ルール:
- null / blank → `"user"`
- 前後空白を trim
- 連続空白を1つにする
- 制御文字を除去
- 最大 50 文字に切り詰め

### DiscriminatorGenerator（新規）

`com.mapoker.application.DiscriminatorGenerator`

- `0000` 〜 `9999` の4桁数字文字列をランダム生成（`String.format("%04d", random.nextInt(10000))`）
- 衝突チェックは Service 層で行う（Generator 自体は純粋な生成のみ）

### GoogleAuthService（新規）

`com.mapoker.application.GoogleAuthService`

```java
@Transactional
public User loginWithGoogle(String idToken) {
    GoogleUserClaims claims = googleTokenVerifier.verify(idToken);
    // 既存ユーザーを検索
    return userAuthIdentityRepository
        .findByProviderAndProviderUserId(AuthProvider.GOOGLE, claims.subject())
        .map(identity -> userRepository.findById(identity.userId()).orElseThrow())
        .orElseGet(() -> createNewUser(claims));
}

private User createNewUser(GoogleUserClaims claims) {
    String username = usernameNormalizer.normalize(claims.name());
    for (int attempt = 0; attempt < 20; attempt++) {
        String discriminator = discriminatorGenerator.generate();
        if (userRepository.existsByUsernameAndDiscriminator(username, discriminator)) {
            continue;
        }
        try {
            User user = userRepository.createWithGoogle(username, discriminator, claims.picture());
            userAuthIdentityRepository.create(user.id(), AuthProvider.GOOGLE, claims.subject());
            return user;
        } catch (DataIntegrityViolationException e) {
            // 競合時は再試行
        }
    }
    // 20回試行後は username に suffix を付けて再試行
    // （個人開発規模では通常ここに到達しない）
    throw new IllegalStateException("Failed to generate unique discriminator for username: " + username);
}
```

---

## HTTP API

### Google ログインエンドポイント

```http
POST /v1/auth/google
```

**Request**:

```json
{
  "idToken": "xxxxx.yyyyy.zzzzz"
}
```

**Response (200 OK)**:

```json
{
  "publicId": "8f6f0e3a-2e6f-4c2d-8a6b-123456789abc",
  "username": "taiki",
  "discriminator": "1234",
  "displayName": "taiki#1234",
  "avatarUrl": "https://example.com/avatar.png"
}
```

**セッション**: 既存の `/v1/auth/login` と同様に `HttpSession` でセッションを確立する。
JWT は使用しない（プロジェクト既存の認証方式に合わせる）。

Google ログイン後のセッション確立は `SecurityContextHolder` に
`UsernamePasswordAuthenticationToken` の代わりに `PreAuthenticatedAuthenticationToken` または
`TestingAuthenticationToken`（もしくはカスタム `Authentication`）を設定し、
`HttpSessionSecurityContextRepository` 経由でセッションに保存する。

**SecurityConfig への追加**:

```java
// defaultSecurityFilterChain の permitAll に追加
.requestMatchers("/v1/auth/google").permitAll()
```

### 現在のユーザー情報取得（既存 API の拡張）

```http
GET /v1/auth/me
```

`UserResponse` を拡張して `publicId`, `discriminator`, `displayName`, `avatarUrl` を含める。

### ユーザー名変更エンドポイント（新規）

```http
PATCH /v1/auth/me/username
```

**Request**:

```json
{
  "username": "newname"
}
```

**Response (200 OK)**:

```json
{
  "publicId": "8f6f0e3a-2e6f-4c2d-8a6b-123456789abc",
  "username": "newname",
  "discriminator": "1234",
  "displayName": "newname#1234",
  "avatarUrl": "https://example.com/avatar.png"
}
```

仕様:
- 認証済みユーザーのみ
- `username` を変更しても `discriminator` は維持
- `username + discriminator` が既に存在する場合は 409 Conflict
- username を正規化する（UsernameNormalizer と同じルール）

---

## DTO 設計

### GoogleLoginRequest

`com.mapoker.interfaces.http.dto.GoogleLoginRequest`

```java
public record GoogleLoginRequest(
    @NotBlank String idToken
) {}
```

### UserResponse の拡張

`com.mapoker.interfaces.http.dto.UserResponse` を拡張。

```java
public record UserResponse(
    long id,           // 既存フィールド（内部用・session 管理に残す）
    String publicId,   // 追加
    String username,
    String discriminator,  // 追加
    String displayName,    // username + "#" + discriminator（DBには保存しない）
    String avatarUrl       // 追加（null 可）
) {}
```

> `users.id` は API 外部に返さない設計だが、既存コードで `new UserResponse(user.id(), user.username())` を使っているため変更コストが高い。内部 ID の扱いは実装時に判断すること。

---

## セキュリティ要件

**DB に保存しないもの**:
- email
- email_verified
- Google access token
- Google refresh token

**認証後**:
- Google ID Token をそのままアプリ内認証として使わない
- バックエンドで検証後、`HttpSession` でアプリ独自のセッションを発行する

---

## エラーハンドリング

| ケース | HTTP Status | レスポンス例 |
|---|---|---|
| idToken が不正 / 期限切れ | 401 | `{"message": "Invalid Google ID token"}` |
| idToken が未指定 | 400 | `{"message": "idToken is required"}` |
| username + discriminator 衝突（変更時） | 409 | `{"message": "Username and discriminator already exists"}` |

---

## pom.xml への依存追加

Google ID Token の検証には以下のいずれかを使用する。

**オプション A: Google API Client Library（公式推奨）**

```xml
<dependency>
    <groupId>com.google.api-client</groupId>
    <artifactId>google-api-client</artifactId>
    <version>2.7.0</version>
</dependency>
```

**オプション B: Nimbus JOSE + JWT（Spring Boot に含まれていれば追加不要の場合あり）**

```xml
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
    <version>9.40</version>
</dependency>
```

---

## テスト要件

### Unit Test

**UsernameNormalizer**:
- null → `"user"`
- blank → `"user"`
- 前後空白が trim される
- 連続空白が1つになる
- 50文字超は切り詰められる

**DiscriminatorGenerator**:
- 4桁文字列を返す
- 数字のみ
- `0000` 〜 `9999` の範囲

**GoogleAuthService**:
- 既存 Google sub の場合、既存ユーザーを返す
- 未登録 Google sub の場合、新規ユーザーを作成する
- DB に email を保存しない（`GoogleUserClaims` に email フィールドがないことで保証）
- `provider = "google"` が保存される
- `provider_user_id = sub` が保存される

### Integration Test（Testcontainers + PostgreSQL）

- `users.public_id` が一意
- `username + discriminator` が一意
- `provider + provider_user_id` が一意
- Google ログイン成功時に `users` と `user_auth_identities` が作成される
- 同じ Google sub で再ログインしても `users` が増えない

---

## ファイル構成（想定）

```
mapoker-backend/src/main/java/com/mapoker/
├── application/
│   ├── User.java                          # 拡張（publicId, discriminator, avatarUrl 追加）
│   ├── UserAuthIdentity.java              # 新規
│   ├── AuthProvider.java                  # 新規（GOOGLE 定数）
│   ├── UserRepository.java                # 拡張（findByPublicId, existsByUsernameAndDiscriminator, createWithGoogle 追加）
│   ├── UserAuthIdentityRepository.java    # 新規（インターフェース）
│   ├── GoogleTokenVerifier.java           # 新規
│   ├── GoogleUserClaims.java              # 新規（record）
│   ├── GoogleAuthService.java             # 新規
│   ├── UsernameNormalizer.java            # 新規
│   └── DiscriminatorGenerator.java        # 新規
├── infrastructure/
│   ├── config/
│   │   ├── SecurityConfig.java            # 変更（/v1/auth/google を permitAll に追加）
│   │   └── GoogleAuthConfig.java          # 新規（@ConfigurationProperties）
│   └── persistence/
│       ├── PostgresUserRepository.java    # 変更（拡張メソッドを追加）
│       └── PostgresUserAuthIdentityRepository.java  # 新規
└── interfaces/http/
    ├── AuthController.java                # 変更（/google エンドポイント追加）
    └── dto/
        ├── GoogleLoginRequest.java        # 新規
        └── UserResponse.java              # 変更（publicId, discriminator, displayName, avatarUrl 追加）

mapoker-backend/src/main/resources/db/migration/
└── V9__add_google_auth.sql               # 新規

mapoker-backend/src/test/java/com/mapoker/
├── application/
│   ├── GoogleAuthServiceTest.java         # 新規
│   ├── UsernameNormalizerTest.java        # 新規
│   └── DiscriminatorGeneratorTest.java    # 新規
```

---

## 非対象

- メールアドレス保存
- パスワードログインの廃止（既存の username/password 認証は維持）
- 複数 Google アカウントの同一ユーザー紐付け
- Google access token / refresh token 保存
- Google Calendar / Gmail API 連携
- アカウント削除・復旧
- 管理画面

---

## 受け入れ条件

- [ ] Google ID Token をバックエンドで検証できる
- [ ] Google `sub` により既存ユーザーを識別できる
- [ ] DB にメールアドレスを保存しない
- [ ] 新規 Google ログイン時に `users` レコードが作成される
- [ ] 新規 Google ログイン時に `user_auth_identities` レコードが作成される
- [ ] `username` は重複可能
- [ ] `username + discriminator` は一意
- [ ] `public_id` は UUID で一意
- [ ] API レスポンスに `users.id` を含めない（`public_id` を返す）
- [ ] 同じ Google アカウントで再ログインしても同じユーザーとして扱われる
- [ ] Google access token / refresh token を保存しない
- [ ] Flyway migration V9 が追加されている
- [ ] 既存の username/password 認証を壊さない
- [ ] `HttpSession` によるセッション管理（JWT 不使用）
- [ ] テストが追加されている
