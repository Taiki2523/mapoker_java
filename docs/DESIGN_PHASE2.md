# Phase 2 設計書 — ゲームテーブル UI 変更

## 概要

`GAME_UI_DESIGN.md` に基づき、ゲーム画面の見た目を刷新する。
Phase 1 のトークン・pill geometry を前提とする。
API 呼び出し・`seatPosition()`・アクション送信ロジックは変更しない。

---

## 1. `Card.tsx` — 3状態コンポーネントへの拡張

### 現行

`CardFace` のみ。`--` を渡すと `??` プレースホルダーを表示。

### 新設計

```ts
type CardVariant = 'front' | 'back' | 'slot'

interface CardProps {
  card?: string        // variant='front' の場合のみ使用
  variant: CardVariant
  size?: 'sm' | 'md'  // デフォルト 'sm'
}
```

- `front`: 既存の `CardFace` ロジックをそのまま流用（ランク・スート表示）
- `back`: ポーカー裏面デザイン（CSS のみ、データ不要）
- `slot`: 未配置スロット（薄い破線アウトラインのみ）
- `size='sm'`: プレイヤー手札用（現行 `.card-mini` 相当）
- `size='md'`: コミュニティカード用（現行 `.card-pill` より一回り大きい）

### CSS クラス構成

```
.card                      /* 共通ベース */
.card.front                /* 表面 — 白背景、ランク・スート */
.card.back                 /* 裏面 — 斜線パターン or グラデーション */
.card.slot                 /* 空スロット — 破線アウトラインのみ */
.card.sm                   /* プレイヤー手札サイズ */
.card.md                   /* コミュニティカードサイズ */
```

### 裏面（`.card.back`）のビジュアル仕様

```css
.card.back {
  background: linear-gradient(135deg, #1a2a6c 25%, #b21f1f 50%, #1a2a6c 75%);
  /* 紺×赤の斜めストライプ — ポーカーらしい裏面 */
  border: 1px solid rgba(255,255,255,0.15);
}
```

### 空スロット（`.card.slot`）のビジュアル仕様

```css
.card.slot {
  background: transparent;
  border: 2px dashed rgba(255,255,255,0.15);
}
```

### 後方互換

既存の `<CardFace card={c} />` 呼び出しは `<Card card={c} variant="front" />` に段階移行する。
`CardFace` を即時削除せず、`Card` のラッパーとして残してもよい。

---

## 2. `TableArea.tsx` — テーブル・シート・コミュニティカード刷新

### 2.1 フェルトを楕円に変更

現行 `border-radius: 50%`（円形）→ 横長楕円へ変更。

```css
.poker-felt {
  width: 62%;
  height: 52%;
  border-radius: 50% / 38%;   /* 横長楕円 */
}
```

フェルトテクスチャの追加（擬似要素）：

```css
.poker-felt::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background-image: url("data:image/svg+xml,..."); /* noise SVG */
  opacity: 0.04;
  pointer-events: none;
}
```

### 2.2 プレイヤーシートの構造変更

現行の DOM：
```
.player-seat
  .player-box
    .player-name-row
      .player-name
      .badges
    .player-stack
    .player-hole-cards
```

新しい DOM：
```
.player-seat
  .player-box
    .seat-header                ← 新規
      .seat-avatar              ← 新規
      .seat-info                ← 新規
        .player-name
        .seat-badges            ← バッジをまとめる
    .player-stack
    .player-hole-cards
```

#### アバター（`.seat-avatar`）

```css
.seat-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--surface-3);
  border: 1.5px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--text-muted);
  flex-shrink: 0;
}
```

表示文字：`displayName(idx)` の先頭1文字を `toUpperCase()` する。新 API は不要。

#### アクティブ状態のグロー（ゴールド統一）

現行は `var(--red)`。設計書に合わせて `var(--poker-gold)` に変更する。

```css
.player-seat.active .player-box {
  border-color: var(--poker-gold);
  box-shadow:
    0 0 0 3px rgba(245,200,66,0.3),
    0 0 20px rgba(245,200,66,0.15),
    0 4px 20px rgba(0,0,0,0.4);
}
```

勝者 glow は `var(--accent)`（green）を維持。
フォールド時の opacity は `0.35` に微調整。

### 2.3 コミュニティカードの空スロット

現行：`CardFace` に `'--'` を渡すと `??` 表示。

新設計：未配置スロットは `<Card variant="slot" size="md" />` で描画する。

判定ロジック（`TableArea.tsx` 内）：
```ts
// game.community は実際のカード文字列のみ含む（'--' を含まない可能性あり）
// 5スロット固定で、データがない位置は slot で描画
const communitySlots = Array.from({ length: 5 }, (_, i) =>
  game.community?.[i] ?? null
)
```

```tsx
{communitySlots.map((card, i) =>
  card
    ? <Card key={i} card={card} variant="front" size="md" />
    : <Card key={i} variant="slot" size="md" />
)}
```

### 2.4 ベットチップ色分け（`.bet-chip`）

金額帯に応じた色を CSS クラスで分ける。JavaScript での金額計算は追加しない。

```ts
function betChipClass(amount: number): string {
  if (amount <= 50)   return 'bet-chip bet-chip--white'
  if (amount <= 200)  return 'bet-chip bet-chip--green'
  if (amount <= 1000) return 'bet-chip bet-chip--black'
  return 'bet-chip bet-chip--purple'
}
```

```css
.bet-chip--white  { border-color: #e5e5e0; color: #e5e5e0; }
.bet-chip--green  { border-color: var(--accent); color: var(--accent); }
.bet-chip--black  { border-color: #6b7280; color: #9ca3af; }
.bet-chip--purple { border-color: #a78bfa; color: #a78bfa; }
```

---

## 3. `TopBar.tsx` — 3カラム再設計

### 3.1 レイアウト構造

```
[左: brand + phase badge + status] [中央: pot + bet] [右: controls]
```

現行は `topbar-info` が flex で全部並んでいる。新設計：

```tsx
<header className="game-topbar">
  <div className="topbar-left">
    <span className="topbar-brand">mapoker</span>
    {game?.street && (
      <span className={`street-badge street-badge--${game.street}`}>
        {game.street.toUpperCase()}
      </span>
    )}
    {/* status text */}
    <span className="topbar-status">{statusText}</span>
  </div>

  <div className="topbar-center">
    <span className="topbar-stat">
      POT <strong>{game?.pot_total ?? 0}</strong>
    </span>
    {(game?.current_bet ?? 0) > 0 && (
      <span className="topbar-stat">
        BET <strong>{game.current_bet}</strong>
      </span>
    )}
  </div>

  <div className="topbar-right">
    {/* 既存: leavePending, error, autoRefresh, icon buttons */}
  </div>
</header>
```

### 3.2 フェーズバッジ色分け

```css
.street-badge--preflop { background: rgba(255,255,255,0.1);  color: #e5e5e0; }
.street-badge--flop    { background: rgba(83,157,245,0.2);   color: var(--info); }
.street-badge--turn    { background: rgba(255,164,43,0.2);   color: var(--warning); }
.street-badge--river   { background: rgba(243,114,127,0.2);  color: var(--danger); }
```

### 3.3 status テキスト

`statusText` は TopBar の props or 内部 memo で導出する（計算ロジックは追加せず、既存 `game.status` と `game.current_player` を参照する）。

```ts
// 表示例（呼び出し元 App.tsx の mySeat, canAct を props 経由で渡す）
const statusText =
  canAct ? t('yourTurn') :
  game?.status === 'in_progress' ? `${displayName(game.current_player)}...` :
  game?.status === 'showdown' ? t('showdown') :
  game?.status === 'finished' ? t('finished') :
  ''
```

### 3.4 アイコンボタンの統一

絵文字からシンプルなテキスト記号 or SVG アイコンへ変更（外部アイコンライブラリ不使用、インライン SVG）。

| 現行 | 新 |
|---|---|
| `🔗` | `<svg>` チェーンアイコン or `⎘` |
| `☺` | `<svg>` ユーザーアイコン |
| `⚙` | `<svg>` 歯車アイコン |

インライン SVG として `TopBar.tsx` 内に直接記述する（外部 import 不要）。

---

## 4. `ActionPanel.tsx` — ターン表示強化 + プリセットラベル更新

### 4.1 非ターン時の折りたたみ

現行：`bet-control-row` は `canAct` のときのみ表示。`action-buttons` は常時表示でボタンが disabled。

新設計：非ターン時は `action-panel` の高さを縮め、`action-info-row` のみ表示。

```css
.action-panel {
  transition: max-height 0.25s ease, padding 0.25s ease;
}
.action-panel.inactive {
  /* bet-control-row と action-buttons を collapse */
}
.action-panel.active {
  /* full height */
}
```

DOM 側：
```tsx
<div className={`action-panel ${canAct ? 'active' : 'inactive'}`}>
```

`bet-control-row` と `action-buttons` は `canAct` のときだけ描画（`&&` 条件）。
`onSendAction` の呼び出しは変更しない。

### 4.2 プリセットラベル

現行の `betPresets` は `App.tsx` で生成され、`label` フィールドを持つ。
`App.tsx` のラベル生成ロジックを `最小 / 1/2P / P / ALL` 表記へ変更する。

```ts
// App.tsx — betPresets 生成箇所
const betPresets: BetPreset[] = [
  { label: t('presetMin'),  amount: minRaise },
  { label: t('presetHalf'), amount: Math.round(game.pot_total / 2) },
  { label: t('presetPot'),  amount: game.pot_total },
  { label: t('presetAll'),  amount: maxBet },
].filter(p => p.amount > 0 && p.amount <= maxBet)
```

`i18n.ts` に追加するキー：
```ts
presetMin:  { ja: '最小', en: 'Min' },
presetHalf: { ja: '½P',  en: '½P'  },
presetPot:  { ja: 'Pot', en: 'Pot' },
presetAll:  { ja: 'ALL', en: 'ALL' },
```

### 4.3 ボタン配色

| ボタン | 現行 class | 新 class |
|---|---|---|
| フォールド | `ghost danger` | `fold-btn` |
| チェック/コール | `primary` | `call-btn` |
| ベット/レイズ | `ghost` | `raise-btn` |
| オールイン | `ghost` | `allin-btn` |

```css
.fold-btn  { background: rgba(243,114,127,0.2); color: #f3727f; border: 1px solid rgba(243,114,127,0.4); border-radius: 9999px; }
.call-btn  { background: rgba(30,215,96,0.2);   color: #1ed760; border: 1px solid rgba(30,215,96,0.4);   border-radius: 9999px; }
.raise-btn { background: rgba(83,157,245,0.2);  color: #539df5; border: 1px solid rgba(83,157,245,0.4);  border-radius: 9999px; }
.allin-btn { background: rgba(245,200,66,0.2);  color: var(--poker-gold); border: 1px solid rgba(245,200,66,0.4); border-radius: 9999px; }
```

hover は `filter: brightness(1.15)` で共通化。

---

## 5. 対象ファイルと変更量まとめ

| ファイル | 変更内容 | ロジック変更 |
|---|---|---|
| `src/components/Card.tsx` | `CardProps` 追加、variant / size 分岐、DOM 拡張 | なし |
| `src/components/GameScreen/TableArea.tsx` | DOM 再構成（アバター・楕円フェルト・slot カード・チップ色） | なし |
| `src/components/GameScreen/TopBar.tsx` | 3カラム化、フェーズ badge 色、status text、アイコン SVG 化 | なし（表示ロジックのみ） |
| `src/components/GameScreen/ActionPanel.tsx` | 折りたたみ class、ボタン class 変更 | なし |
| `src/App.tsx` | `betPresets` ラベル生成文字列のみ変更 | なし（ラベル文言のみ） |
| `src/i18n.ts` | preset キー4件追加 | なし |
| `src/App.css` | 上記すべての新 class 定義追加 | なし |

---

## 6. 完了条件

- フェルトが横長楕円（`border-radius: 50% / 38%`）になっている
- プレイヤーシートにアバター（頭文字）が表示される
- アクティブプレイヤーの glow がゴールドになっている
- コミュニティカードの空スロットが `??` ではなく破線アウトラインで表示される
- 相手の手札が通常時は裏面（`.card.back`）で表示される
- ベットチップに金額帯別の色がついている
- TopBar のフェーズバッジに色がついている（FLOP=青、TURN=橙、RIVER=赤）
- ActionPanel が非ターン時に折りたたまれる
- プリセットボタンが `最小 / ½P / Pot / ALL` 表記になっている
- API 呼び出し・`seatPosition()` に変更がない
