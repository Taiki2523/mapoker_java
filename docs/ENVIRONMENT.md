# 環境変数・機密情報の管理

このドキュメントは、秘密情報（API キー、DB パスワード、Google クライアント ID など）を安全に管理するための手順です。

## 原則

- **秘密情報をコードに直書きしない**
- **`.env`, `application-secret.properties` などは `.gitignore` に記載し、リポジトリへコミットしない**
- **ローカル開発・本番環境ごとに環境変数を切り替える**

---

## バックエンド（Java / Spring Boot）

### ローカル開発

1. **`.env.local` ファイルを作成**（`mapoker-backend/` ディレクトリ）

   ```bash
   cp mapoker-backend/.env.example mapoker-backend/.env.local
   ```

2. **実際の値を記入**

   ```
   SPRING_PROFILES_ACTIVE=local
   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mapoker
   SPRING_DATASOURCE_USERNAME=mapoker
   SPRING_DATASOURCE_PASSWORD=your_password
   GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
   GOOGLE_CLIENT_SECRET=your-client-secret
   ```

3. **Maven 実行時に読み込み**

   ```bash
   cd mapoker-backend
   # .env.local から環境変数を読み込んで実行
   export $(cat .env.local | xargs)
   SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
   ```

### 本番環境（PostgreSQL + 認証）

Spring Boot の `@ConfigurationProperties` を使い、環境変数から設定を読み込みます。

**設定ファイル構成：**
- `application.properties` — 共通設定（コミット済み）
- `application-local.properties` — ローカル開発用（コミット済み）
- `application-postgresql.properties` — PostgreSQL 設定（コミット済み）
- `application-secret.properties` — 秘密情報（`.gitignore`、作成しない）

**例：`application-secret.properties`（作成しないこと）**
```properties
spring.datasource.url=jdbc:postgresql://prod-db:5432/mapoker
spring.datasource.username=mapoker_prod
spring.datasource.password=ultra_secure_password
google.client.id=prod-client-id
google.client.secret=prod-client-secret
```

**環境変数での指定**
```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db:5432/mapoker
export SPRING_DATASOURCE_USERNAME=mapoker_prod
export SPRING_DATASOURCE_PASSWORD=ultra_secure_password
export GOOGLE_CLIENT_ID=prod-client-id
export GOOGLE_CLIENT_SECRET=prod-client-secret
export SPRING_PROFILES_ACTIVE=postgresql,production

./mvnw spring-boot:run
```

### Google OAuth 設定

1. [Google Cloud Console](https://console.cloud.google.com/) で OAuth 2.0 認証情報を作成
2. **クライアント ID** と **クライアント シークレット** を取得
3. 環境変数に設定

   ```
   GOOGLE_CLIENT_ID=YOUR_CLIENT_ID.apps.googleusercontent.com
   GOOGLE_CLIENT_SECRET=YOUR_CLIENT_SECRET
   ```

4. Spring Security で検証

---

## フロントエンド（Node.js / Vite）

### ローカル開発

1. **`.env.local` を作成**（`mapoker-frontend/` ディレクトリ）

   ```bash
   cp mapoker-frontend/.env.example mapoker-frontend/.env.local
   ```

2. **実際の値を記入**

   ```
   APPLICATION_URL=http://localhost:5173
   BACKEND_URL=http://localhost:8080
   VITE_GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
   VITE_APP_ENV=development
   ```

3. **実行**

   ```bash
   cd mapoker-frontend
   npm install
   npm run dev
   ```

### Vite の環境変数ルール

- **ファイル名:** `.env` / `.env.local` / `.env.production`
- **変数名プレフィックス:** 通常は `VITE_` が必要。`APPLICATION_URL` は `vite.config.ts` で明示的に公開している
- **読み込み順序:**
  1. `.env` （デフォルト、コミット可）
  2. `.env.local` （ローカル上書き、`.gitignore`）
  3. `.env.production` または環境別ファイル

`APPLICATION_URL` は API ベース URL の起点として使われ、フロントエンドは常に `${APPLICATION_URL}/api/v1/...` を呼びます。
ローカルの `npm run dev` では Vite が `/api` を `BACKEND_URL`（未指定時は `http://localhost:8080`）へプロキシします。

**ローカル開発:**
```bash
npm run dev  # .env + .env.local を使用
```

**本番ビルド:**
```bash
npm run build  # .env + .env.production を使用
```

### Google Sign-In の設定

1. [Google Cloud Console](https://console.cloud.google.com/) で OAuth 2.0 認証情報を作成
2. **クライアント ID** を取得
3. `.env.local` に設定

   ```
   VITE_GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
   ```

4. フロントエンド コンポーネントで使用

   ```typescript
   const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
   ```

---

## Docker/devcontainer での管理

### devcontainer の場合

`.devcontainer/devcontainer.json` で環境変数を注入（ただし秘密は環境変数から）：

```json
{
  "remoteEnv": {
    "SPRING_PROFILES_ACTIVE": "local"
  },
  "remoteUser": "root"
}
```

**秘密値は外部から注入：**
```bash
docker compose up -d
# または
devcontainer open --workspace-folder . --log-level trace
```

`.env.local` は devcontainer マウント内に配置してから実行。

### Docker Compose の場合

```yaml
version: '3.8'
services:
  backend:
    build: ./mapoker-backend
    env_file:
      - .env.local
    environment:
      SPRING_PROFILES_ACTIVE: postgresql
    ports:
      - "8080:8080"
  
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: mapoker
      POSTGRES_USER: mapoker
      POSTGRES_PASSWORD_FILE: /run/secrets/db_password
    secrets:
      - db_password

secrets:
  db_password:
    file: ./secrets/db_password.txt  # .gitignore に記載
```

---

## チェックリスト

- [ ] `.gitignore` に `.env`, `.env.local`, `application-secret.properties` を記載
- [ ] `.env.example` を作成（テンプレート用）
- [ ] ローカル開発用に `.env.local` を作成・機密値を設定
- [ ] `SPRING_PROFILES_ACTIVE=local` で起動確認
- [ ] Google OAuth の設定が完了
- [ ] 本番環境でも同じ仕組みで環境変数を注入できるか検証
- [ ] CI/CD パイプライン（GitHub Actions など）で秘密値を `Secrets` として設定

---

## 参考リンク

- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [Vite Environment Variables](https://vitejs.dev/guide/env-and-modes)
- [Google Cloud OAuth 2.0](https://developers.google.com/identity/protocols/oauth2)
