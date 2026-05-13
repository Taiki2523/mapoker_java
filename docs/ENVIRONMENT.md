# 環境変数・機密情報の管理

秘密情報（DB パスワードなど）を安全に管理するための手順。

## 原則

- 秘密情報をコードに直書きしない
- `.env`, `application-secret.properties` などは `.gitignore` に記載してリポジトリへコミットしない
- ローカル開発・本番環境ごとに環境変数を切り替える

---

## バックエンド（Java / Spring Boot）

### 設定ファイル構成

| ファイル | 役割 | git 管理 |
|---|---|---|
| `application.properties` | 共通設定（CORS デフォルト、game / wallet プロパティなど） | ✅ コミット可 |
| `application-local.properties` | ローカル開発用（認証無効化、インメモリ DB など） | ✅ コミット可 |
| `application-postgresql.properties` | PostgreSQL 接続設定 | ✅ コミット可 |
| `application-secret.properties` | 秘密情報（作成しないこと） | ❌ `.gitignore` |
| `.env.local` | ローカル環境変数（作成しないこと） | ❌ `.gitignore` |

### ローカル開発（devcontainer 内）

`local` プロファイルでは認証が無効化され、インメモリ DB を使う。追加の環境変数は不要。

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

PostgreSQL を使うローカル環境:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/mapoker \
SPRING_DATASOURCE_USERNAME=mapoker \
SPRING_DATASOURCE_PASSWORD=mapoker \
SPRING_PROFILES_ACTIVE=local,postgresql \
./mvnw spring-boot:run
```

### 本番環境

環境変数で秘密情報を注入する（`application-secret.properties` は使わず環境変数を推奨）。

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/mapoker
export SPRING_DATASOURCE_USERNAME=mapoker
export SPRING_DATASOURCE_PASSWORD=your_secure_password
export CORS_ALLOWED_ORIGIN_PATTERNS=https://mapoker.marciadesign.org
export SPRING_PROFILES_ACTIVE=postgresql
export WALLET_ADMIN_USERNAMES=admin1,admin2
```

主要な設定項目（`application.properties` の対応キー）:

| 環境変数 | プロパティキー | デフォルト | 説明 |
|---|---|---|---|
| `CORS_ALLOWED_ORIGIN_PATTERNS` | `cors.allowed-origin-patterns` | `*` | CORS 許可オリジン |
| `GAME_DEFAULT_ODD_CHIP_RULE` | `game.default-odd-chip-rule` | `LOW_INDEX` | 端数チップルール |
| `WALLET_INITIAL_GRANT` | `wallet.initial-grant` | `10000` | 新規登録ボーナス |
| `WALLET_DAILY_BONUS_AMOUNT` | `wallet.daily-bonus-amount` | `1000` | デイリーボーナス額 |
| `WALLET_RECOVERY_THRESHOLD` | `wallet.recovery-threshold` | `1000` | 救済ボーナス発動残高 |
| `WALLET_RECOVERY_AMOUNT` | `wallet.recovery-amount` | `5000` | 救済ボーナス額 |
| `WALLET_ADMIN_USERNAMES` | `wallet.admin-usernames` | `""` | チップ付与権限ユーザー（カンマ区切り） |

---

## フロントエンド（Node.js / Vite）

### 設定ファイル構成

| ファイル | 役割 | git 管理 |
|---|---|---|
| `.env` | 共通デフォルト値 | ✅ コミット可 |
| `.env.local` | ローカル上書き（作成しないこと） | ❌ `.gitignore` |
| `.env.production` | 本番ビルド用 | ✅ コミット可 |

### Vite の環境変数ルール

- `VITE_` プレフィックスを持つ変数のみブラウザから参照可能
- `APPLICATION_URL` は `vite.config.ts` で明示的に公開している

### ローカル開発

```bash
cp mapoker-frontend/.env.example mapoker-frontend/.env.local
# 必要に応じて値を編集
cd mapoker-frontend && npm run dev
```

主な設定値:

```
APPLICATION_URL=http://localhost:3000
VITE_APP_ENV=development
```

`APPLICATION_URL` はブラウザが `/api/v1/...` を呼ぶ起点となる URL。ローカル Docker 環境では `http://localhost:3000`（nginx がプロキシ）を指定する。`http://localhost:8080` を直接指定すると `/api/...` が 403 になる。

### 本番ビルド

```bash
npm run build  # .env + .env.production を使用
```

---

## Docker Compose

`.env.local`（開発）/ `.env.prd`（本番）をプロジェクトルートに配置して利用する。

```bash
# 開発
docker compose --env-file .env.local up -d

# 本番
docker compose --env-file .env.prd up -d
```

主要な Docker 環境変数:

```bash
POSTGRES_DB=mapoker
POSTGRES_USER=mapoker
POSTGRES_PASSWORD=your_secure_password
DB_DATA_LOCATION=/srv/mapoker/data/db  # DB データディレクトリのホストパス
APPLICATION_URL=https://mapoker.marciadesign.org
VITE_APP_ENV=production
CORS_ALLOWED_ORIGIN_PATTERNS=https://mapoker.marciadesign.org
SPRING_PROFILES_ACTIVE=postgresql
```

---

## チェックリスト

- [ ] `.gitignore` に `.env.local`, `application-secret.properties` を記載
- [ ] `.env.example` テンプレートが最新の状態か確認
- [ ] `SPRING_PROFILES_ACTIVE=local` でローカル起動確認
- [ ] 本番環境で必要な環境変数が CI/CD Secrets に設定済み
