# Phase 3: Cleanup — Google Auth削除 + CORS修正

codex-implement へ渡す実装仕様。

---

```
objective:
  1. Google OAuth に関する死んだ設定・コードを削除する
  2. 本番 CORS から不要な開発オリジンを除去する

scope:
  - .env.prd
  - mapoker-backend の Google OAuth 関連ファイル（存在する場合）
  - mapoker-frontend の VITE_GOOGLE_CLIENT_ID 参照（存在する場合）

allowed_files:
  - .env.prd
  - mapoker-backend/src/main/resources/application.properties
  - mapoker-backend/src/main/java/com/mapoker/infrastructure/config/SecurityConfig.java
  （Google OAuth Filter/Config が存在した場合のみ追加で対象とする）

forbidden_changes:
  - .env.local を変更しない（開発環境は触らない）
  - ユーザー認証フロー（login / register / logout）に変更を加えない
  - CORS の allowedMethods・allowedHeaders を変更しない
  - バックエンドの API エンドポイントを変更しない

acceptance_criteria:
  - .env.prd から以下が削除されていること:
      VITE_GOOGLE_CLIENT_ID
      VITE_APP_ENV
      GOOGLE_CLIENT_ID
      GOOGLE_CLIENT_SECRET
  - .env.prd の CORS_ALLOWED_ORIGINS から以下が削除されていること:
      http://localhost:3000
      http://localhost:8080
      http://localhost
      （残すべき値: https://mapoker.marciadesign.org のみ）
  - 削除後、バックエンドが起動時にGoogle OAuth関連のBean解決エラーを出さないこと
  - SecurityConfig.java にGoogle OAuth Filterが残っている場合は除去されていること

output_format:
  ## Changed Files
  ## What Was Implemented
  ## Remaining Work
  ## Risks
  ## Suggested Verification Steps
```

---

## 実装ガイダンス（orchestratorが提供する判断）

### .env.prd の変更

削除対象行：
```
# Google OAuth
VITE_GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
VITE_APP_ENV=production

# === Backend Google OAuth ===
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret
```

CORS修正後の値：
```
CORS_ALLOWED_ORIGINS=https://mapoker.marciadesign.org
```

### .env.prd コメントも整理する

`APPLICATION_URL` の説明コメントに "external reverse proxy routes to backend" とあり正確なので残す。

### バックエンドの確認

`SecurityConfig.java` を確認済み: Google OAuth FilterChain は存在しない。`DaoAuthenticationProvider` ベースの username/password 認証のみ。削除対象コードなし。

`application.properties` に `spring.security.oauth2.*` 等の Google 設定がないことも確認済み。

よってバックエンドファイルへの変更は不要の可能性が高い。codex-implement 実行時に再確認すること。
