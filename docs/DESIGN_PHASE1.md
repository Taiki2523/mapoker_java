# Phase 1 設計書 — 静的 UI 刷新

## 概要

Spotify design system (DESIGN.md) を適用し、ロビー〜ゲーム画面全域を near-black ダークテーマへ統一する。
ゲームロジック・API・状態管理は一切変更しない。

---

## 1. CSS トークン設計（`index.css`）

`index.css` を唯一のトークン定義ファイルとし、`App.css` は必ずここを参照する（直値禁止）。

### 現行トークン → 新トークンの対応表

| 現行変数 | 用途 | 新変数 | 新値 |
|---|---|---|---|
| なし | 最深背景 | `--bg` | `#121212` |
| なし | サーフェス | `--surface` | `#181818` |
| なし | サーフェス2 | `--surface-2` | `#1f1f1f` |
| なし | サーフェス3 | `--surface-3` | `#252525` |
| `--plum` | 見出し色 | 廃止 | `--text-base: #ffffff` で代替 |
| `--olive` | 補助テキスト | 廃止 | `--text-muted: #b3b3b3` で代替 |
| `--silver` | ラベル色 | `--text-muted` | `#b3b3b3` |
| なし | アクセント | `--accent` | `#1ed760` |
| `--red` | エラー・危険 | `--danger` | `#f3727f` |
| なし | 警告 | `--warning` | `#ffa42b` |
| なし | 情報 | `--info` | `#539df5` |
| `--gold` | ポーカー文脈の強調 | `--poker-gold` | `#f5c842` |
| `--felt` | フェルト | `--felt` | `#1a6b35` |
| `--felt-dark` | フェルト暗部 | `--felt-dark` | `#0f4a22` |
| `--rail` | テーブル縁 | `--rail` | `#3b2a1a` |
| `--rail-dark` | テーブル縁暗部 | `--rail-dark` | `#1a1005` |
| `--sand`, `--fog`, `--warm-wash` | ロビー背景系 | 廃止 | `--surface` / `--surface-2` で代替 |
| `--border` | ボーダー | `--border` | `rgba(255,255,255,0.1)` |
| `--focus` | フォーカスリング | `--focus` | `#1ed760` |
| なし | インセットボーダー | `--inset-border` | `rgb(124,124,124) 0px 0px 0px 1px inset` |

### シャドウ

```css
--shadow-sm:   rgba(0,0,0,0.3) 0px 4px 8px;
--shadow-md:   rgba(0,0,0,0.3) 0px 8px 8px;
--shadow-lg:   rgba(0,0,0,0.5) 0px 8px 24px;
--shadow-inset: rgb(18,18,18) 0px 1px 0px, rgb(124,124,124) 0px 0px 0px 1px inset;
```

### タイポグラフィ

```css
--font-ui:    'SpotifyMixUI', 'Helvetica Neue', helvetica, arial, 'Hiragino Sans', 'Hiragino Kaku Gothic ProN', Meiryo, 'MS Gothic', sans-serif;
--font-title: 'SpotifyMixUITitle', var(--font-ui);

--text-xs:   0.63rem;   /* 10px  micro */
--text-sm:   0.75rem;   /* 12px  small */
--text-base: 1rem;      /* 16px  body  */
--text-lg:   1.13rem;   /* 18px  feature heading */
--text-xl:   1.5rem;    /* 24px  section title */
```

---

## 2. グローバルスタイル変更（`index.css`）

```css
:root {
  /* 上記トークン全定義 */
}

body {
  font-family: var(--font-ui);
  background: var(--bg);
  color: var(--text-base);
  font-size: 16px;
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;
}
```

---

## 3. ボタンスタイル再設計（`App.css`）

### 基本形：pill geometry

```
border-radius: 9999px
font-weight: 700
text-transform: uppercase
letter-spacing: 1.4px
padding: 8px 24px
font-size: 0.88rem
```

### バリアント一覧

| クラス | background | color | border | 用途 |
|---|---|---|---|---|
| `primary` | `var(--accent)` | `#000` | なし | 主要CTA |
| `secondary` | `var(--surface-2)` | `var(--text-base)` | `1px solid var(--border)` | 副次操作 |
| `ghost` | `transparent` | `var(--text-muted)` | `1px solid var(--border)` | アイコン脇のテキストボタン |
| `danger` | `rgba(243,114,127,0.15)` | `#f3727f` | `1px solid rgba(243,114,127,0.4)` | 破壊操作 |
| `gold` | `var(--poker-gold)` | `#1a1200` | なし | ポーカー文脈の強調 |
| `icon-btn` | `var(--surface-2)` | `var(--text-muted)` | `1px solid var(--border)` | トップバーアイコン |
| `preset-btn` | `var(--surface-3)` | `var(--text-base)` | `1px solid var(--border)` | ベットプリセット |

### hover / disabled

```css
button:hover:not(:disabled) {
  filter: brightness(1.1);
  transform: translateY(-1px);
}
button:disabled {
  opacity: 0.38;
  cursor: not-allowed;
}
```

---

## 4. 入力・セレクト・スライダー（`App.css`）

```css
input, select {
  background: var(--surface-2);
  color: var(--text-base);
  border: none;
  box-shadow: var(--shadow-inset);
  border-radius: 9999px;   /* pill */
  padding: 10px 20px;
  font-size: 0.95rem;
  font-family: var(--font-ui);
}
input:focus, select:focus {
  outline: 2px solid var(--focus);
  outline-offset: 2px;
}
```

bet-slider のサムは `var(--accent)` に変更する（現行 `var(--red)`）。

---

## 5. ロビー画面刷新（`App.css` + `LobbyScreen.tsx`）

### 5.1 `.lobby-screen`

```css
.lobby-screen {
  background: var(--bg);    /* #121212 — light背景を廃止 */
}
```

### 5.2 `.lobby-panel`

```css
.lobby-panel {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow-lg);
}
.lobby-panel h2 {
  color: var(--text-base);
  font-family: var(--font-title);
  font-size: var(--text-xl);
}
.lobby-panel p {
  color: var(--text-muted);
}
```

### 5.3 テーブル一覧行（`.lobby-browser-row`）

```css
.lobby-browser-row {
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 1rem 1.1rem;
}
.lobby-browser-row:hover {
  background: var(--surface-3);
}
.lobby-browser-title-row strong {
  color: var(--text-base);
}
.lobby-browser-meta {
  color: var(--text-muted);
  font-size: 0.86rem;
}
```

### 5.4 フィルタ（`.lobby-filter-card`）

```css
.lobby-filter-card {
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: 8px;
}
.lobby-flag-option {
  background: var(--surface-3);
  border: 1px solid var(--border);
  border-radius: 9999px;
  color: var(--text-muted);
}
.lobby-flag-option:has(input:checked) {
  border-color: var(--accent);
  color: var(--accent);
}
```

### 5.5 `LobbyScreen.tsx` の DOM 変更

変更量は最小限。クラス名の整理のみ。

- `lobby-flag-option` の重複している visibility/flag 選択を1箇所にまとめる
- `lobby-browser-row` の visibility バッジを pill スタイル（`table-visibility.public` / `.private`）に統一
  - public: `var(--accent)` 系
  - private: `var(--info)` 系

---

## 6. RoomScreen の重複UI修正（`RoomScreen.tsx`）

現状、`visibility` と `flags` の `<label>` ブロックが2箇所に存在する。

**修正方針：**
- 送信する `CreateGameConfig` の payload は変更しない
- JSX 上で重複している方の `<label>` ブロックを削除する
- フォームを `field-grid`（2カラム）から `field-grid single`（1カラム）に整理し、dark surface フォームカード化する

---

## 7. `.badge` スタイル統一（`App.css`）

```css
.badge {
  background: var(--surface-3);
  color: var(--text-muted);
  border-radius: 9999px;
  font-size: 0.65rem;
  font-weight: 700;
  padding: 0.1rem 0.4rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.badge.btn  { background: rgba(245,200,66,0.2);  color: var(--poker-gold); }
.badge.sb   { background: rgba(83,157,245,0.2);  color: var(--info); }
.badge.bb   { background: rgba(243,114,127,0.2); color: var(--danger); }
.badge.warn { background: var(--surface-3); color: var(--text-muted); }
.badge.accent { background: rgba(30,215,96,0.15); color: var(--accent); }
```

---

## 8. プロフィールパネル dark 化（`App.css`）

```css
.profile-panel {
  background: var(--surface);
}
.profile-card, .profile-table-card {
  background: var(--surface-2);
  border: 1px solid var(--border);
}
.profile-header h2, .profile-section h3,
.profile-table-main strong, .profile-browser-title-row strong {
  color: var(--text-base);
}
.profile-header p, .profile-panel .muted {
  color: var(--text-muted);
}
```

---

## 9. buy-in モーダル dark 化（`App.css`）

```css
.modal-panel {
  background: var(--surface);
  border: 1px solid var(--border);
  color: var(--text-base);
}
.modal-panel .muted { color: var(--text-muted); }
.buyin-amount-display { color: var(--text-base); }
.buyin-range-labels   { color: var(--text-muted); }
```

slider の `accent-color` を `var(--accent)` に変更。

---

## 10. 対象ファイルと変更量まとめ

| ファイル | 変更内容 | 変更量 |
|---|---|---|
| `src/index.css` | トークン全面定義、グローバル font / color | 中（既存削除 + 新規定義） |
| `src/App.css` | ロビー・ゲーム・ボタン・バッジ・プロフィール・モーダル の dark 化 | 大（全面改修） |
| `src/components/LobbyScreen.tsx` | flag/visibility バッジの class 整理 | 小（クラス名調整のみ） |
| `src/components/RoomScreen.tsx` | 重複 label ブロックの削除 | 小（数行削除） |

**JSX ロジックは変更しない。** 変更するのは className とインライン style の値のみ。

---

## 11. 完了条件

- ロビー・ゲーム・プロフィール・モーダルすべてが `#121212` ベースのダークテーマである
- `index.css` に色の直値がなく、すべて `--*` トークン参照になっている
- `App.css` に `#fafaf8`, `#ffffff`, `--plum`, `--sand` 等の旧ライトテーマ値が残っていない
- ボタンが pill 形状（border-radius: 9999px）になっている
- `RoomScreen` の visibility / flags UI が1セットに統一されている
- 既存の API 呼び出し・ゲームロジックが無変更である
