# Phase 2: Frontend ログアウトUI拡充 + マイページ

codex-implement へ渡す実装仕様。

---

```
objective:
  1. RoomScreen にログアウトボタンを追加する
  2. WaitingScreen にログアウトボタンを追加する
  3. マイページ機能を実装する（ユーザー名表示 + ログアウト）
     - RoomScreen・WaitingScreen の上部に常設のヘッダーバーを設ける
     - ヘッダーバーにログイン中ユーザー名と「ログアウト」ボタンを表示する
     - 将来のプレイ履歴表示に備えた拡張ポイント（コメントのみ、実装なし）を残す

scope:
  mapoker-frontend モジュール

allowed_files:
  - mapoker-frontend/src/components/RoomScreen.tsx
  - mapoker-frontend/src/components/WaitingScreen.tsx
  - mapoker-frontend/src/components/UserHeader.tsx  （新規作成）
  - mapoker-frontend/src/App.tsx
  - mapoker-frontend/src/i18n.ts

forbidden_changes:
  - GameScreen/ 配下のファイルを変更しない（TopBarのログアウトは既存のまま維持）
  - api.ts を変更しない
  - types.ts を変更しない
  - バックエンドファイルを変更しない
  - ゲームロジックをフロントエンドに追加しない（CLAUDE.md制約）
  - ホールカードの可視性をフロントエンドで計算する実装を追加しない

acceptance_criteria:
  - RoomScreen の上部に UserHeader が表示され、ログイン中のユーザー名が確認できること
  - UserHeader の「ログアウト」ボタンを押すと POST /v1/auth/logout が呼ばれ、AuthScreen に戻ること
  - WaitingScreen の上部にも同じ UserHeader が表示されること
  - WaitingScreen のログアウトボタンが動作すること
  - App.tsx で handleLogout が RoomScreen・WaitingScreen の両方に渡されていること
  - i18n.ts に必要なキーが追加されていること（例: myPage, loggedInAs 等）
  - 既存の TopBar（GameScreen）のログアウトが引き続き動作すること（変更なし）
  - ブラウザで localhost:3000 を開いてログイン後、RoomScreen と WaitingScreen でログアウトできること

output_format:
  ## Changed Files
  ## What Was Implemented
  ## Remaining Work
  ## Risks
  ## Suggested Verification Steps
```

---

## 実装ガイダンス（orchestratorが提供する判断）

### UserHeader コンポーネント

新規作成 `UserHeader.tsx`：

```tsx
type Props = {
  username: string
  onLogout: () => void
}
```

表示内容：
- 左: "mapoker" ブランド（または省略して右寄せのみでも可）
- 右: `{username} さん` + `ログアウト` ボタン (`.ghost.danger` クラス)

将来の拡張ポイントとして、`マイページ` ボタンのプレースホルダーコメントを残す（実装しない）。

### RoomScreen の変更

現在の Props:
```tsx
type Props = {
  loading: boolean
  error: string
  onCreateGame: (config: CreateGameConfig) => Promise<void>
  onJoinRoom: (idOrUrl: string) => Promise<void>
}
```

追加する Props:
```tsx
  currentUser: AuthUser | null
  onLogout: () => void
```

変更箇所: return の先頭に `<UserHeader username={currentUser?.username ?? ''} onLogout={onLogout} />` を追加。

### WaitingScreen の変更

現在の Props に追加:
```tsx
  currentUser: AuthUser | null
  onLogout: () => void
```

変更箇所: return の先頭に `<UserHeader username={currentUser?.username ?? ''} onLogout={onLogout} />` を追加。

### App.tsx の変更

`currentUser` と `handleLogout` を以下の2コンポーネント呼び出し箇所に追加する：
- `<RoomScreen ... currentUser={currentUser} onLogout={handleLogout} />`
- `<WaitingScreen ... currentUser={currentUser} onLogout={handleLogout} />`

`handleLogout` はすでに App.tsx に実装済み（TopBar で使用中）のため、同じ関数を渡すだけでよい。

### i18n.ts の追加キー

```ts
myPage: 'マイページ',
loggedInAs: 'ログイン中: {name}',
```
