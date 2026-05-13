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
- Spring の `SimpMessagingTemplate` で既存のサービス層と統合しやすい
- SockJS により WebSocket 非対応環境でも long-polling にフォールバックできる

### トピック設計

```
/topic/tables/{tableId}/game     ゲーム状態の変化（全員に broadcast、ホールカードは全マスク）
/topic/tables/{tableId}/members  ロスター変化（全員に broadcast）
/user/queue/hole-cards           ホールカード（本人のみ、個別 push）
```

アクション送信は引き続き REST POST を使う。WebSocket はサーバ→クライアントの push 専用。

### サーバ側の主な変更

1. **`WebSocketConfig.java`** 追加
   - `@EnableWebSocketMessageBroker` で STOMP エンドポイント `/ws` を SockJS ありで公開
   - ブローカープレフィックス: `/topic` と **`/queue`** の両方が必要（`/user/...` は内部で `/queue/...` に解決される）
   - アプリプレフィックス `/app`、ユーザープレフィックス `/user`

2. **`WebSocketSecurityConfig.java`** 追加
   - `permitAll("/ws/**")` は HTTP ハンドシェイクを通すだけで STOMP フレームは保護しない
   - このクラスで STOMP `CONNECT`/`SUBSCRIBE`/`MESSAGE` フレームの認可を明示的に設定する
   - `@Profile("local")` で全許可、`@Profile("!local")` で認証必須

3. **`GameEventPublisher.java`** 追加（新規コンポーネント）
   - `SimpMessagingTemplate` を注入
   - `publishGameState(tableId, state)` → ホールカード全マスクで `/topic/.../game` に broadcast
   - `publishHoleCards(tableId, state)` → 各着席ユーザーに `/user/queue/hole-cards` で個別 push
   - `publishMembers(tableId, members)` → `/topic/.../members` に broadcast

4. **`GameService` / `TableService`** 変更
   - 状態変化を起こすすべてのメソッドで `GameEventPublisher` を呼び出す
   - `startHand` は `publishGameState` と `publishHoleCards` の両方を呼ぶ
   - `processPendingLeaves`・リバイ・スタックリセット等も publish 対象

5. **認証**
   - 現在の認証方式はユーザー名/パスワード + HttpSession（Google 認証ではない）
   - Spring Security セッション認証では、認証済み HTTP セッションの `Principal` はハンドシェイク時に WebSocket セッションへ自動引き継ぎされる

### フロントエンド側の主な変更

1. **`src/ws.ts`** 追加（WebSocket クライアント）
   - STOMP クライアントの生成・接続・再接続ロジック
   - `subscribeGame` / `subscribeMembers` / `subscribeHoleCards` を公開

2. **`App.tsx`** 変更
   - `setInterval` によるポーリングを削除
   - `subscribeGame` で全員マスク済みゲーム状態を受信
   - `subscribeHoleCards` で受け取った自分のカードをゲーム状態にマージ
   - `subscribeMembers` でロスターを更新
   - 初回ロード・再接続時は REST GET で状態復元（維持）
   - アクション送信は REST のまま

3. **プロキシ設定**
   - `vite.config.ts`: `/ws` プロキシ追加（`ws: true` で WebSocket 対応）
   - `nginx.conf`: `/ws` location に `Upgrade`/`Connection` ヘッダー設定を追加

4. **アニメーション同期**
   - サーバが push するペイロードに `streetRevealedAt`（`Instant`）タイムスタンプを含める
   - ストリート変化の検知は `GameService` 側（サービスロック内）で行い publisher に渡す
   - クライアントはそのタイムスタンプを基準にアニメーション開始時刻を計算
   - 全クライアントが同じタイミングでフロップ/ターン/リバーをめくれる

---

## 移行フェーズ

### Phase 1: 基盤構築
- Spring WebSocket 設定（`WebSocketConfig`・`WebSocketSecurityConfig`）追加
- `SecurityConfig` に `/ws/**` の HTTP 許可追加
- `vite.config.ts` / `nginx.conf` の `/ws` プロキシ設定追加

### Phase 2: broadcast + ホールカード個別配信（同時実装）

> **重要**: ポーリングを廃止するこのフェーズで、ホールカード個別配信も必ず同時に実装する。
> 公開 broadcast にホールカードを含めると全員に漏洩し、含めなければ自分のカードが消える。

- `GameEventPublisher` 実装（`publishGameState`・`publishHoleCards`・`publishMembers`）
- ゲーム状態変化・ロスター変化・ホールカードを WebSocket push に切り替え
- ポーリング廃止（REST GET は維持）

### Phase 3: アニメーション同期
- サーバ push ペイロードに `streetRevealedAt` を追加
- ストリート変化検知を `GameService` 内で行い、タイムスタンプをペイロードに付加
- フロントエンドのアニメーション開始タイミングをタイムスタンプ基準に変更

---

## 互換性・リスク

| リスク | 対策 |
|---|---|
| WebSocket 非対応環境 | SockJS の long-polling フォールバックで対応 |
| 認証セッション | Spring Security のセッション認証で Principal が自動引き継ぎされる |
| STOMP フレーム認可 | `WebSocketSecurityConfig` で CONNECT/SUBSCRIBE/MESSAGE を明示的に保護 |
| スケールアウト | 複数インスタンス時は Redis Pub/Sub ブローカー（将来対応） |
| 移行期間 | REST エンドポイントは残したまま段階移行できる |
| 複数タブ/デバイス | `/user/queue/hole-cards` は同一ユーザー名の全セッションに届く（現状は許容） |

---

## 実装計画

### 対象ファイル

**新規作成（バックエンド）**

| ファイル | パッケージ |
|---|---|
| `WebSocketConfig.java` | `com.mapoker.infrastructure.config` |
| `WebSocketSecurityConfig.java` | `com.mapoker.infrastructure.config` |
| `GameEventPublisher.java` | `com.mapoker.infrastructure.messaging` |
| `GameBroadcastPayload.java` | `com.mapoker.interfaces.ws.dto` |
| `MembersBroadcastPayload.java` | `com.mapoker.interfaces.ws.dto` |
| `HoleCardsPayload.java` | `com.mapoker.interfaces.ws.dto` |

**変更（バックエンド）**

| ファイル | 変更内容 |
|---|---|
| `pom.xml` | `spring-boot-starter-websocket`, `spring-security-messaging` を追加 |
| `SecurityConfig.java` | 本番 chain に `/ws/**` の `permitAll()` を追加 |
| `GameService.java` | `ObjectProvider<GameEventPublisher>` 注入。`applyAction`・`startHand`・`resolveShowdown` に publish 追加 |
| `TableService.java` | `ObjectProvider<GameEventPublisher>` 注入。`join`・`leave`・`processPendingLeaves`・リバイ・スタックリセット等に publish 追加 |

**新規作成・変更（フロントエンド）**

| ファイル | 変更内容 |
|---|---|
| `mapoker-frontend/src/ws.ts` | STOMP クライアントラッパー（新規） |
| `mapoker-frontend/package.json` | `@stomp/stompjs`, `sockjs-client` を追加 |
| `mapoker-frontend/src/App.tsx:220-228` | `setInterval` ポーリングを WebSocket サブスクリプションに置き換え |
| `mapoker-frontend/vite.config.ts` | `/ws` プロキシ設定を追加（`ws: true`） |
| `mapoker-frontend/nginx.conf` | `/ws` location に WebSocket ヘッダー設定を追加 |

### 設計の要点

- **ブローカー設定**: `enableSimpleBroker("/topic", "/queue")` — `/queue` は `/user/...` の解決に必須
- **認証の引き継ぎ**: Spring Security セッション認証では `Principal` はハンドシェイク時に自動伝播。`HttpSessionHandshakeInterceptor` の明示的な登録は不要
- **STOMP 認可**: `WebSocketSecurityConfig` が担う。HTTP パス許可だけでは STOMP フレームは保護されない
- **循環依存回避**: `GameService → GameEventPublisher` の注入は既存の `ObjectProvider<TableService>` パターンと同様に `ObjectProvider` を使う
- **ホールカード**: broadcast は常に全員マスク済み。自分のカードは `startHand` 時に `/user/queue/hole-cards` で個別 push
- **ストリートタイムスタンプ**: ストリート変化の検知はサービスロック内の `GameService` で行い、`publishGameState` に `streetRevealedAt` を渡す。Publisher 側でのキャッシュは使わない
