# Implementation Plan: Auth & Security Improvements

Source: codex-review findings (2026-04-20)
Orchestrator: claude-orchestrator

## Overview

| Phase | Title | Priority | Skill |
|-------|-------|----------|-------|
| Phase 1 | Backend Security: Cookie属性 + レート制限 | 高 | codex-implement |
| Phase 2 | Frontend: ログアウトUI拡充 + マイページ | 高/中 | codex-implement |
| Phase 3 | Cleanup: Google Auth削除 + CORS修正 | 中 | codex-implement |

## Execution order

Phase 1 → Phase 2 → Phase 3

各Phaseの詳細実装仕様は個別ファイルを参照。

- [Phase 1](phase1-backend-security.md)
- [Phase 2](phase2-frontend-logout-mypage.md)
- [Phase 3](phase3-cleanup.md)

## Acceptance criteria (全Phase共通)

- devcontainer内で `./mvnw test` が通ること
- devcontainer内で `./mvnw spring-boot:run` + フロントエンドで動作確認できること
- 本番環境 https://mapoker.marciadesign.org でログイン・ログアウトが正常に動作すること
- curl のみでゲームを完全実行できる状態を維持すること（CLAUDE.md制約）

## Architecture decisions (orchestrator保持)

- Bucket4j をレート制限ライブラリとして採用（Spring Boot 3.x対応、in-memory対応）
- マイページはモーダルではなく、RoomScreen上部に常設ヘッダーバーを設ける
- Google OAuth は削除（実装しない判断確定）
- セッション分散は現時点でスコープ外
