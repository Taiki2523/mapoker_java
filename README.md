# mapoker Java

テキサスホールデムポーカーエンジン — Spring Boot 3.x による HTTP API。

本番 URL: https://mapoker.marciadesign.org/

## クイックスタート

### Docker を使った起動（推奨）

**ローカル開発:**

```bash
# .env.local ですでに設定済み
docker compose --env-file .env.local up -d

# アクセス
# Frontend: http://localhost:3000
# Application API: http://localhost:3000/api/v1/games
# Backend direct: http://localhost:8080/v1/games
```

**本番環境:**

```bash
# .env.prd を編集してから起動
vim .env.prd
docker compose --env-file .env.prd up -d

# 外部リバースプロキシ経由でアクセス
# https://mapoker.marciadesign.org
```

### ローカル開発（Maven + npm - devcontainer 必須）

```bash
# 1. バックエンド起動
cd mapoker-backend
cp .env.example .env.local
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run

# 2. フロントエンド起動（別ターミナル）
cd mapoker-frontend
cp .env.example .env.local
npm install
npm run dev
```

詳細は [docs/ENVIRONMENT.md](docs/ENVIRONMENT.md) および [docs/DEPLOY.md](docs/DEPLOY.md) を参照。

## ドキュメント

| ファイル | 内容 |
|----------|------|
| [docs/ENVIRONMENT.md](docs/ENVIRONMENT.md) | 📋 **環境変数・機密情報の管理（まず読むこと）** |
| [docs/DOCKER.md](docs/DOCKER.md) | 🐳 **Docker 構成ガイド（イメージ・ボリューム・セキュリティ）** |
| [docs/DEPLOY.md](docs/DEPLOY.md) | 🚀 **デプロイ手順（本番環境セットアップ）** |
| [docs/API.md](docs/API.md) | HTTP API 仕様（エンドポイント・リクエスト/レスポンス） |
| [docs/GAME_FLOW.md](docs/GAME_FLOW.md) | ゲーム進行フロー（Mermaid 図） |
| [docs/RULES.md](docs/RULES.md) | テキサスホールデム ルール仕様 |
| [docs/HAND_EVALUATION.md](docs/HAND_EVALUATION.md) | ハンド評価ロジック（7枚→最強5枚） |
| [docs/db_schema.md](docs/db_schema.md) | PostgreSQL スキーマ定義 |
| [docs/DECISIONS.md](docs/DECISIONS.md) | 設計判断の記録 |
| [docs/TEST_EXECUTION.md](docs/TEST_EXECUTION.md) | テスト・ビルド・curl 手順 |
| [docs/IMPL_PROGRESS.md](docs/IMPL_PROGRESS.md) | 実装進捗トラッカー |
| [docs/FRONTEND_DESIGN.md](docs/FRONTEND_DESIGN.md) | フロントエンドデザインガイド |
| [CLAUDE.md](CLAUDE.md) | Claude Code 向けコーディング規約・アーキテクチャ概要 |

## セキュリティ

- `.env.local` と `.env.example` は `.gitignore` に記載済み
- 秘密情報は環境変数で注入（コードに直書きしない）
- 詳細は [docs/ENVIRONMENT.md](docs/ENVIRONMENT.md) を参照
