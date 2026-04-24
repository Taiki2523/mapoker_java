# Phase 3 設計書 — 動的アクション・アニメーション

## 概要

Phase 2 の DOM 構造を前提に、CSS animation と局所 React state でアニメーションを実装する。
ゲーム進行条件・API 呼び出し順序・state 計算は変更しない。
トリガーは既存の `game`, `showdown`, `canAct`, `player.contributed`, `community`, `street`, `status` の変化のみ使用する。

**Framer Motion の採用は CSS animation で実現できない場合に限り、事前にオーナー承認を得てから導入する。**

---

## 1. アニメーション基本方針

### 使用プロパティ（GPU 負荷が低いもの優先）

- `transform` (translate, scale, rotate)
- `opacity`
- `filter` (brightness, blur)
- `box-shadow`

### `prefers-reduced-motion` 対応（必須）

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
```

### keyframes 命名規則

```
@keyframes mp-{name}    /* mapoker prefix — 他ライブラリと衝突回避 */
```

---

## 2. アクティブターン パルスエフェクト

### 現行

`your-turn-text` テキストのみが `opacity` pulse。

### 新設計

seat border glow を pulse させる。

```css
@keyframes mp-seat-pulse {
  0%, 100% {
    box-shadow:
      0 0 0 3px rgba(245,200,66,0.3),
      0 0 20px rgba(245,200,66,0.15);
  }
  50% {
    box-shadow:
      0 0 0 5px rgba(245,200,66,0.5),
      0 0 32px rgba(245,200,66,0.25);
  }
}

.player-seat.active .player-box {
  animation: mp-seat-pulse 1.8s ease-in-out infinite;
}
```

`your-turn-text` の pulse は廃止し、TopBar の `statusText` で「あなたのターン」を表示する（Phase 2 で実装済み）。

---

## 3. ActionPanel スライドイン

### 現行

`canAct` の変化で `active` / `inactive` クラスを切り替える（Phase 2 で実装）。

### 新設計（アニメーション追加）

```css
@keyframes mp-panel-slidein {
  from {
    transform: translateY(100%);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.action-panel.active {
  animation: mp-panel-slidein 0.22s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
}

.action-panel.inactive {
  transform: translateY(0);          /* collapsed height は CSS max-height で制御 */
  transition: max-height 0.2s ease, opacity 0.15s ease;
  opacity: 0.7;
}
```

スライドインはターンが来たタイミングの一度のみ。`inactive` 状態のアニメーション再生防止のため、初回レンダリングには `no-animate` クラスを付与し JS で除去する。

---

## 4. カードディールアニメーション

### トリガー

`game.players[*].hole` が空 → 2枚に変化したとき（ハンド開始）。

### 実装方針

**CSS animation + React の局所 state** で実装する。Framer Motion 不使用。

#### `TableArea.tsx` の変更

```ts
// 直前の hole 状態を useRef で保持
const prevHoleRef = useRef<Record<number, string[]>>({})

// ハンド開始を検知
useEffect(() => {
  const isNewDeal = game.players.some((p, i) => {
    const prev = prevHoleRef.current[i] ?? []
    return prev.length === 0 && (p.hole?.length ?? 0) > 0
  })

  if (isNewDeal) {
    setDealing(true)
    setTimeout(() => setDealing(false), 600)   // アニメーション完了後に解除
  }

  prevHoleRef.current = Object.fromEntries(
    game.players.map((p, i) => [i, p.hole ?? []])
  )
}, [game.players])
```

#### CSS

```css
@keyframes mp-card-deal {
  from {
    transform: translate(-50%, -50%) scale(0.4);
    opacity: 0;
  }
  to {
    transform: translate(0, 0) scale(1);
    opacity: 1;
  }
}

.player-seat.dealing .player-hole-cards .card {
  animation: mp-card-deal 0.25s cubic-bezier(0.34, 1.3, 0.64, 1) both;
}

/* 2枚目は少し遅延 */
.player-seat.dealing .player-hole-cards .card:nth-child(2) {
  animation-delay: 0.1s;
}
```

`dealing` 中は `<Card variant="back" />` を表示し、アニメーション完了後に通常の表示へ戻る。
自分の手札のみ `back → front` のフリップを行う（相手は常に `back`）。

---

## 5. コミュニティカード フリップアニメーション

### トリガー

`game.community.length` が増加したとき（フロップ: 0→3、ターン: 3→4、リバー: 4→5）。

### 実装方針

新しく追加されたカードのみにフリップアニメーションを付与する。

```ts
const prevCommLen = useRef(0)
const [newlyRevealedIndices, setNewlyRevealedIndices] = useState<Set<number>>(new Set())

useEffect(() => {
  const current = game.community?.filter(c => c && c !== '--').length ?? 0
  if (current > prevCommLen.current) {
    const newIndices = new Set(
      Array.from({ length: current - prevCommLen.current }, (_, i) => prevCommLen.current + i)
    )
    setNewlyRevealedIndices(newIndices)
    setTimeout(() => setNewlyRevealedIndices(new Set()), 500)
  }
  prevCommLen.current = current
}, [game.community])
```

#### CSS（カードフリップ）

```css
@keyframes mp-card-flip {
  0%   { transform: rotateY(90deg); opacity: 0; }
  100% { transform: rotateY(0deg);  opacity: 1; }
}

.card.flipping {
  animation: mp-card-flip 0.3s ease-out forwards;
}
```

フロップの3枚は 0 / 60ms / 120ms の遅延で順次めくる。

---

## 6. ショーダウン演出

### トリガー

`game.status` が `showdown` になったとき。

### 演出ステップ（逐次進行）

| ステップ | タイミング | 内容 |
|---|---|---|
| 0 | 0ms | showdown 状態に入り、両者の手札を `back` から `front` にフリップ |
| 1 | 400ms | 勝者 seat に gold glow、敗者 seat を dim |
| 2 | 700ms | コミュニティカードと手役名を強調表示 |
| 3 | 1000ms | 結果オーバーレイ（勝者名・役名・払戻し）をフェードイン |

#### 局所 state

```ts
const [sdStep, setSdStep] = useState(0)

useEffect(() => {
  if (!isShowdown) { setSdStep(0); return }
  const t1 = setTimeout(() => setSdStep(1), 400)
  const t2 = setTimeout(() => setSdStep(2), 700)
  const t3 = setTimeout(() => setSdStep(3), 1000)
  return () => { clearTimeout(t1); clearTimeout(t2); clearTimeout(t3) }
}, [isShowdown])
```

#### CSS

```css
@keyframes mp-showdown-overlay {
  from { opacity: 0; transform: scale(0.92); }
  to   { opacity: 1; transform: scale(1); }
}

.showdown-result[data-step="3"] {
  animation: mp-showdown-overlay 0.3s ease-out forwards;
}

/* 勝者 glow（ステップ1以降） */
.player-seat.winner.sd-active .player-box {
  box-shadow: 0 0 0 4px rgba(30,215,96,0.5), 0 0 40px rgba(30,215,96,0.2);
  transition: box-shadow 0.4s ease;
}

/* 敗者 dim（ステップ1以降） */
.player-seat.loser.sd-active .player-box {
  opacity: 0.45;
  filter: saturate(0.3);
  transition: opacity 0.4s ease, filter 0.4s ease;
}
```

`data-step` 属性は `showdown-result` の div に付与する。

---

## 7. ベット時チップアニメーション

### トリガー

`player.contributed` が 0 → 正の値になったとき。

### 実装方針

CSS `transform: translate` で seat からポット方向へ移動するように見せる。
実際のチップ移動は行わず、`.bet-chip` の出現 animation で表現する（簡易実装）。

```css
@keyframes mp-chip-appear {
  from {
    transform: translate(-50%, -50%) scale(0.3) translateY(-20px);
    opacity: 0;
  }
  to {
    transform: translate(-50%, -50%) scale(1) translateY(0);
    opacity: 1;
  }
}

.bet-chip.new {
  animation: mp-chip-appear 0.25s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
}
```

`new` クラスの付与・除去は `useEffect` で `contributed` の変化を監視して行う。

---

## 8. 実装上の注意点

### タイマー管理

`useEffect` のクリーンアップで必ず `clearTimeout` する。コンポーネントアンマウント時の state 更新を防ぐ。

```ts
useEffect(() => {
  const id = setTimeout(() => { ... }, delay)
  return () => clearTimeout(id)
}, [dependency])
```

### state の最小化

- アニメーション用 state は `boolean` または `Set<number>` のみ
- `dealing: boolean` — カードディール中
- `newlyRevealedIndices: Set<number>` — 新規コミュニティカードの index
- `sdStep: number` — ショーダウン演出ステップ (0-3)
- `newChips: Set<number>` — bet-chip の新規出現 seat index

これらはすべて `TableArea.tsx` または `ActionPanel.tsx` の局所 state とし、`App.tsx` に引き上げない。

### 既存ロジックとの分離

- `dealing` などのアニメーション state が `true` の間も、API ポーリングは通常通り継続する
- アニメーション完了前に次の状態が来た場合は、タイマーをリセットして新しい state に対応する
- アニメーションが描画に失敗しても、ゲームの進行は妨げない

---

## 9. 対象ファイルと変更量まとめ

| ファイル | 変更内容 | ロジック変更 |
|---|---|---|
| `src/components/GameScreen/TableArea.tsx` | dealing / sdStep / newChips / newlyRevealedIndices 状態追加、class 付与 | なし（アニメーション state のみ） |
| `src/components/GameScreen/ActionPanel.tsx` | mp-panel-slidein animation class 追加 | なし |
| `src/components/Card.tsx` | `flipping` class 受け取り prop 追加（任意） | なし |
| `src/App.css` | keyframes 定義、animation class 追加、reduced-motion 対応 | なし |

---

## 10. 完了条件

- ハンド開始時にカードがディール animation で表示される（裏面→表面）
- ターンが来たとき ActionPanel が下からスライドインする
- コミュニティカードが公開時にフリップ animation で表示される
- ショーダウン時に勝者 glow・敗者 dim・結果オーバーレイが順次表示される
- ベット時にチップが出現 animation で表示される
- アクティブ seat の border が pulse animation する
- `prefers-reduced-motion: reduce` 環境でアニメーションがスキップされる
- API ポーリング・ゲーム進行に一切影響がない
- Framer Motion を使用していない（または導入前に承認済みである）
