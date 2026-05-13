# Docker 構成ガイド

mapoker の Docker 本番環境構成ルールをまとめたものです。

---

## 構成図

```
┌────────────────────────────────────────────────────┐
│          https://mapoker.marciadesign.org          │
│        (External Reverse Proxy / Load Balancer)    │
├────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────────────┐      ┌──────────────────┐   │
│  │ Spring Boot      │      │ nginx + React    │   │
│  │ backend:8080     │      │ frontend:3000    │   │
│  │ /v1/games        │      │ /api/v1/...      │   │
│  └────────┬─────────┘      └──────────────────┘   │
│           │                                        │
│           ▼                                        │
│      ┌──────────────┐                             │
│      │  PostgreSQL  │                             │
│      │  (host mount)│                             │
│      └──────────────┘                             │
│                                                     │
└────────────────────────────────────────────────────┘
```

---

## イメージ戦略

### バックエンド（Spring Boot）

**マルチステージビルド:**

```dockerfile
# Build stage: Maven でコンパイル
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
COPY pom.xml src/ ./
RUN ./mvnw clean package -DskipTests

# Runtime stage: JRE のみ（軽量）
FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /build/target/*.jar app.jar
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**メリット:**
- ビルド出力を最小化（Maven キャッシュを除外）
- セキュア（非ルートユーザーで実行）
- ヘルスチェック搭載

### フロントエンド（React + nginx）

**マルチステージビルド:**

```dockerfile
# Build stage: Vite でビルド
FROM node:20-alpine AS builder
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Runtime stage: nginx で配信
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
```

**メリット:**
- 本番向けの静的ファイル配信
- SPA リライト対応
- キャッシュ最適化（JS/CSS は 1 年、HTML は 1 日）

---

## ボリューム戦略（ホストマウント）

### 設計原則

- **本番環境**: `/srv/mapoker/data/db` にホストマウント
- **ローカル開発**: `./data/db` に相対パスマウント
- **環境変数で管理**: `DB_DATA_LOCATION` で柔軟に変更可能

### docker-compose.yaml

```yaml
services:
  db:
    volumes:
      - ${DB_DATA_LOCATION:-/srv/mapoker/data/db}:/var/lib/postgresql/data
      - ./mapoker-backend/src/main/resources/db/migration:/docker-entrypoint-initdb.d:ro
```

**構文:**
```
${VARIABLE_NAME:-default_value}
```
- 環境変数 `VARIABLE_NAME` が存在 → その値を使用
- 存在しない場合 → `default_value` を使用

### 環境ファイル

#### .env.local（ローカル開発）

```bash
# ローカルマシンの data/ ディレクトリにマウント
DB_DATA_LOCATION=./data/db
```

#### .env.prd（本番環境）

```bash
# 本番サーバーの /srv/mapoker/data/db にマウント
DB_DATA_LOCATION=/srv/mapoker/data/db
```

---

## 起動方法

### ローカル開発

```bash
# .env.local が自動読み込みされる（デフォルト）
docker compose up -d

# または明示的に指定
docker compose --env-file .env.local up -d

# アクセス
# Frontend: http://localhost:3000
# Backend: http://localhost:8080
# Application API: http://localhost:3000/api/v1/games
```

### 本番環境

```bash
# 1. .env.prd を準備
vim .env.prd

# 2. ホストマウントディレクトリを作成
sudo mkdir -p /srv/mapoker/data/db
sudo chown -R 999:999 /srv/mapoker/data/db
sudo chmod 700 /srv/mapoker/data/db

# 3. 起動
docker compose --env-file .env.prd up -d

# 4. ログ確認
docker compose logs -f
```

---

## ネットワーク構成

### Docker 内部ネットワーク

```yaml
networks:
  mapoker-network:
    driver: bridge
```

**ホスト側:**
- Backend: `localhost:8080` (or `:8080`)
- Frontend: `localhost:3000` (or `:3000`)
- Database: `localhost:5432` (or `:5432`)

**コンテナ間通信:**
- `backend` ↔ `db` (`postgres://db:5432`)
- `frontend` ↔ `backend` (`/api` を nginx が `http://backend:8080` へプロキシ)

### 本番 HTTPS ルーティング

外部リバースプロキシが以下のようにルーティング：

```
https://mapoker.marciadesign.org
└── https://mapoker.marciadesign.org:80 → frontend:3000 (SPA, /api は backend へ)

https://api.mapoker.marciadesign.org
└── https://api.mapoker.marciadesign.org:80 → backend:8080 (/v1/...)
```

---

## ヘルスチェック

すべてのサービスにヘルスチェックを搭載：

```yaml
healthcheck:
  test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 40s
```

**確認方法:**

```bash
# コンテナのヘルスチェック状態確認
docker compose ps

# 手動ヘルスチェック
curl http://localhost:8080/actuator/health
curl http://localhost:3000/health
docker compose exec db pg_isready -U mapoker
```

---

## セキュリティ設定

### ユーザー権限

**Spring Boot:**
```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser  # 非ルートユーザーで実行
```

**PostgreSQL:**
```yaml
user: "999:999"  # postgres ユーザー UID (docker イメージ内)
```

**ホストマウントパーミッション:**
```bash
sudo chown -R 999:999 /srv/mapoker/data/db
sudo chmod 700 /srv/mapoker/data/db  # owner のみ読み書き可能
```

### 環境変数の秘密情報

`.env.local` / `.env.prd` に記載される秘密情報：

```bash
POSTGRES_PASSWORD=secure_password
GOOGLE_CLIENT_ID=xxx
GOOGLE_CLIENT_SECRET=xxx
```

**ルール:**
- `.gitignore` に記載 → リポジトリにコミットされない
- 本番環境では強力なランダム値を生成
- 環境変数で注入（コードに直書きしない）

---

## リソース管理

### メモリ・CPU

Docker Compose で制限を設定（オプション）：

```yaml
services:
  backend:
    deploy:
      resources:
        limits:
          cpus: '1'
          memory: 1G
        reservations:
          cpus: '0.5'
          memory: 512M
```

### ログ管理

`/etc/docker/daemon.json`:

```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m",
    "max-file": "10"
  }
}
```

---

## ベストプラクティス

### イメージ

- ✅ マルチステージビルドで最小化
- ✅ 特定バージョン指定（`postgres:16-alpine`）
- ✅ Alpine Linux で軽量化
- ✅ 非ルートユーザーで実行
- ❌ `latest` タグ使用
- ❌ 大きなベースイメージ

### ボリューム

- ✅ ホストマウント（本番向け）
- ✅ 環境変数で管理
- ✅ パーミッション設定
- ❌ 名前付きボリューム（本番）
- ❌ 固定パス（hardcode）

### ネットワーク

- ✅ 内部ネットワーク分離
- ✅ ヘルスチェック搭載
- ✅ 依存関係明示（`depends_on`）
- ❌ ホストネットワークモード（本番）

### セキュリティ

- ✅ 環境変数で秘密情報管理
- ✅ `.gitignore` で制御
- ✅ 非ルートユーザー
- ✅ ヘルスチェックとリスタート設定
- ❌ コード中に秘密情報
- ❌ リポジトリに秘密ファイル

---

## トラブルシューティング

### イメージビルド失敗

```bash
# キャッシュクリア
docker compose build --no-cache

# ビルドログ確認
docker compose build --verbose
```

### ボリュームマウント失敗

```bash
# パーミッション確認
ls -la /srv/mapoker/data/db

# 修正
sudo chown -R 999:999 /srv/mapoker/data/db
sudo chmod 700 /srv/mapoker/data/db

# コンテナ再起動
docker compose restart db
```

### ヘルスチェック失敗

```bash
# コンテナログ確認
docker compose logs db

# 手動ヘルスチェック
docker compose exec db pg_isready -U mapoker
```

---

## 参考

- [Docker Compose 仕様](https://docs.docker.com/compose/compose-file/)
- [Docker セキュリティベストプラクティス](https://docs.docker.com/engine/security/)
- [mamich docker-compose.yml](参照: mamich プロジェクト)
