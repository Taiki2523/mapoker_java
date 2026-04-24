# Docker デプロイガイド

本番環境における mapoker の Docker デプロイ手順です。

**関連ドキュメント:**
- [docs/DOCKER.md](DOCKER.md) — Docker 構成ルール（イメージ・ボリューム・セキュリティ）
- [docs/ENVIRONMENT.md](ENVIRONMENT.md) — 環境変数管理
- [docs/FLYWAY.md](FLYWAY.md) — Flyway migration 運用ルール

## 構成図

本番環境では外部リバースプロキシ（CloudFlare、nginx など）で HTTPS/ドメインを管理します。

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
│      │  port 5432   │                             │
│      └──────────────┘                             │
│                                                     │
└────────────────────────────────────────────────────┘
```

---

## クイックスタート（ローカル開発）

```bash
# 1. .env.local で設定済み（localhost:8080 で起動）
docker compose --env-file .env.local up -d

# 2. ログ確認
docker compose logs -f

# 3. アクセス確認
# Frontend: http://localhost:3000
# Backend Health: http://localhost:8080/actuator/health
# Application API: http://localhost:3000/api/v1/games
```

---

## 本番デプロイ（VPS/クラウド）

### 1. 環境ファイルを準備

`.env.prd` ファイルが既にリポジトリに含まれています。本番の実際の値に編集してください：

```bash
# .env.prd を編集（秘密情報を入力）
vim .env.prd

# または新規作成
cat > .env.prd << EOF
POSTGRES_DB=mapoker
POSTGRES_USER=mapoker
POSTGRES_PASSWORD=$(openssl rand -base64 32)

SPRING_PROFILES_ACTIVE=postgresql,production

APPLICATION_URL=https://mapoker.marciadesign.org
VITE_GOOGLE_CLIENT_ID=your-google-client-id
VITE_APP_ENV=production

GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret

CORS_ALLOWED_ORIGINS=https://mapoker.marciadesign.org
EOF
```

**重要**: `.env.prd` は `.gitignore` に記載されているため、リポジトリにコミットされません。

### 1.5. ホストマウント用ディレクトリを作成

```bash
# /srv/mapoker/data/db ディレクトリを作成
sudo mkdir -p /srv/mapoker/data/db
sudo chown -R 999:999 /srv/mapoker/data/db  # PostgreSQL user
sudo chmod 700 /srv/mapoker/data/db
```

### 2. Docker のインストール

```bash
# Ubuntu/Debian
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

### 3. コンテナの起動

```bash
# 本番環境で起動
docker compose --env-file .env.prd up -d

# ログ確認
docker compose logs -f backend
docker compose logs -f frontend

# ステータス確認
docker compose ps
```

### 4. リバースプロキシ設定

本番環境は以下の構成を想定します：

**オプション A: CloudFlare（推奨 - DNS レベル）**

```
Domain: mapoker.marciadesign.org
└── https://mapoker.marciadesign.org → frontend:3000 (/api は backend:8080 へ)
```

CloudFlare DNS 設定:
- A record: `mapoker.marciadesign.org` → `your-server-ip`

サーバー側 nginx（ポート 80/443 でリッスン）:

```nginx
# mapoker.marciadesign.org → frontend:3000
server {
    listen 443 ssl http2;
    server_name mapoker.marciadesign.org;
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /api/ {
        proxy_pass http://localhost:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

# HTTP → HTTPS redirect
server {
    listen 80;
    server_name _;
    return 301 https://$host$request_uri;
}
```

**オプション B: Docker + Traefik（自動HTTPS）**

```bash
# docker-compose に追加して、Traefik で自動 Let's Encrypt
docker compose -f docker-compose.yaml -f docker-compose.traefik.yaml up -d
```

**オプション C: Nginx Proxy Manager（GUI 管理）**

```bash
docker run -d \
  --name npm \
  -p 80:80 -p 443:443 -p 81:81 \
  -v npm_data:/data \
  -v npm_letsencrypt:/etc/letsencrypt \
  jc21/nginx-proxy-manager:latest
```

管理画面: `http://server-ip:81`

---

## データベース管理

ホストマウント用のデータ保存先は `.env.prd` で `DB_DATA_LOCATION` 環境変数で管理されます。

### セットアップ（初回）

```bash
# .env.prd でパスを指定
DB_DATA_LOCATION=/srv/mapoker/data/db

# ディレクトリを作成
sudo mkdir -p /srv/mapoker/data/db
sudo chown -R 999:999 /srv/mapoker/data/db  # PostgreSQL user (postgres in container)
sudo chmod 700 /srv/mapoker/data/db
```

### バックアップ

```bash
# PostgreSQL バックアップ
docker compose exec db pg_dump -U mapoker mapoker > backup.sql

# ホストマウントディレクトリのバックアップ
sudo tar czf /backup/db_backup_$(date +%Y%m%d).tar.gz /srv/mapoker/data/db/
```

### リストア

```bash
# PostgreSQL リストア
docker compose exec -T db psql -U mapoker mapoker < backup.sql

# ホストマウントディレクトリのリストア
sudo tar xzf /backup/db_backup_*.tar.gz -C /
```

---

## トラブルシューティング

### Backend が起動しない

```bash
# ログ確認
docker compose logs backend

# データベース接続確認
docker compose exec backend wget -O- http://localhost:8080/actuator/health
```

Flyway の checksum mismatch でも backend は起動失敗する。`Validate failed: Migrations have failed validation` が見えたら、既存 `V*.sql` の書き換えを疑い、`docs/FLYWAY.md` に従って対処する。

### Frontend がビルドエラーで失敗

```bash
# キャッシュをクリアして再ビルド
docker compose build --no-cache frontend
docker compose up -d frontend
```

### CORS エラー

```bash
# CORS_ALLOWED_ORIGINS を正しく設定
CORS_ALLOWED_ORIGINS=https://mapoker.marciadesign.org
```

### ポート競合

```bash
# ポートの確認
sudo lsof -i :8080
sudo lsof -i :3000

# 別のポートで起動
docker compose down
# docker-compose.yaml の ports を編集
docker compose up -d
```

---

## 監視 & ヘルスチェック

```bash
# ヘルスチェックエンドポイント
curl http://localhost:8080/actuator/health
curl http://localhost:3000/health

# コンテナのリソース使用量
docker stats

# ログ確認
docker compose logs --tail=50 -f
```

---

## 定期メンテナンス

### ログローテーション

`/etc/docker/daemon.json` に追加:

```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m",
    "max-file": "10"
  }
}
```

### 自動バックアップ（cron）

```bash
# crontab -e
0 2 * * * cd /path/to/mapoker_java && docker compose exec -T db pg_dump -U mapoker mapoker | gzip > /backup/db_$(date +\%Y\%m\%d).sql.gz && sudo tar czf /backup/data_$(date +\%Y\%m\%d).tar.gz /srv/mapoker/data/db/
```

### 自動更新＆再起動

```bash
# watchtower を使用して、イメージを自動更新
docker run -d \
  --name watchtower \
  -v /var/run/docker.sock:/var/run/docker.sock \
  containrrr/watchtower \
  --interval 3600
```

---

## スケーリング

複数インスタンスで運用する場合は Kubernetes や Docker Swarm 検討。

---

## 参考リンク

- [Docker Documentation](https://docs.docker.com/)
- [Docker Compose Reference](https://docs.docker.com/compose/compose-file/)
- [Nginx Proxy Manager](https://nginxproxymanager.com/)
- [Traefik](https://traefik.io/)
- [Watchtower](https://containrrr.dev/watchtower/)
