# WebSocket 移行計画

ステータス: `planned`

## 背景と動機

現在のアーキテクチャは REST API + クライアントポーリング（2秒間隔）で動作している。
この設計はシンプルだが、リアルタイムゲームとして以下の本質的な限界がある。

### REST ポーリングの限界

- **演出の同期不可**: オールインでボードがランアウトするとき、各クライアントがバラバラのタイミングでポーリングするため、フロップ/ターン/リバーのめくりアニメーション開始時刻がズレる
- **遅延**: 最大 2 秒のポーリング遅延がある（相手のアクションが見えるまでのラグ）
- **サーバ負荷**: 全クライアントが 2 秒ごとにリクエストを送り続ける（ゲームが動いていない時間も含む）

### WebSocket で解決できること

- サーバが全クライアントに**同時**に状態変化を push → 全員が同じ `t=0` でアニメーションを開始できる
- アクション結果が即座に全員に届く（体感的なレスポンスが大幅改善）
- ポーリングがなくなり、サーバ・クライアント双方の負荷が下がる

---

## 移行アーキテクチャ

### 採用技術

- **Spring WebSocket + STOMP over SockJS**（Spring Boot ネイティブサポート）
- フロントエンド: `@stomp/stompjs` + `sockjs-client`

STOMP を選ぶ理由:
- pub/sub モデルでブロードキャストが簡単
- Spring の `@MessageMapping` / `SimpMessagingTemplate` で既存のサービス層と統合しやすい
- SockJS により WebSocket 非対応環境でも long-polling にフォールバックできる

### トピック設計

```
/topic/tables/{tableId}/game     ゲーム状態の変化（全員に broadcast）
/topic/tables/{tableId}/members  ロスター変化（全員に broadcast）
/user/queue/hole-cards           ホールカード（本人のみ、個別 push）
```

アクション送信は引き続き REST POST を使う（または `/app/actions` STOMP destination）。

### サーバ側の主な変更

1. **`WebSocketConfig.java`** 追加
   - `@EnableWebSocketMessageBroker` で STOMP エンドポイント `/ws` を公開
   - トピックプレフィックス `/topic`, アプリプレフィックス `/app` を設定

2. **`GameEventPublisher.java`** 追加（新規サービス）
   - `SimpMessagingTemplate` を注入
   - `publishGameState(tableId, gameState)` → `/topic/tables/{id}/game` に送信
   - `publishMembers(tableId, members)` → `/topic/tables/{id}/members` に送信

3. **`GameService` / `TableService`** 変更
   - アクション適用・スタートハンド・ジョイン後に `GameEventPublisher` を呼び出す

4. **認証**
   - Spring Security の WebSocket サポートで既存の Google 認証と統合

### フロントエンド側の主な変更

1. **`src/ws.ts`** 追加（WebSocket クライアント）
   - STOMP クライアントの生成・接続・再接続ロジック
   - `subscribeGame(tableId, onGameState)` / `subscribeMembers(tableId, onMembers)` を公開

2. **`App.tsx`** 変更
   - `setInterval` によるポーリングを削除
   - WebSocket サブスクリプションで `setGame` / `setRoster` を呼び出す
   - アクション送信は REST のまま（または STOMP SEND に移行）

3. **アニメーション同期**
   - サーバが push するペイロードに `revealedAt` タイムスタンプを含める
   - クライアントはそのタイムスタンプを基準にアニメーション開始時刻を計算
   - 全クライアントが同じタイミングでフロップ/ターン/リバーをめくれる

---

## 移行フェーズ

### Phase 1: 基盤構築
- Spring WebSocket 設定追加
- `GameEventPublisher` 実装
- フロントエンドの STOMP クライアントラッパー作成

### Phase 2: broadcast 置き換え
- ゲーム状態変化を WebSocket push に切り替え
- ロスター変化を WebSocket push に切り替え
- ポーリング廃止（REST GET は維持、初期ロード用）

### Phase 3: アニメーション同期
- サーバ push ペイロードに `streetRevealedAt` を追加
- フロントエンドのアニメーション開始タイミングをタイムスタンプ基準に変更

### Phase 4: ホールカード個別配信
- `/user/queue/hole-cards` で本人のカードだけを push
- `viewer_index` クエリパラメータ依存を廃止

---

## 互換性・リスク

| リスク | 対策 |
|---|---|
| WebSocket 非対応環境 | SockJS の long-polling フォールバックで対応 |
| 認証セッション | Spring Security の WebSocket ハンドシェイク認証で既存と統合 |
| スケールアウト | 複数インスタンス時は Redis Pub/Sub ブローカー（将来対応） |
| 移行期間 | REST エンドポイントは残したまま段階移行できる |
