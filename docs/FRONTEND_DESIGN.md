# フロントエンドデザインガイド（Pinterest インスパイア）

## 概要

mapoker フロントエンドは Pinterest のデザインシステムを参考にしたスタイルガイドを採用している。
旧来のクールブルー系（`#496aff`）から全面的に刷新し、暖かみのあるトーンに統一した。

---

## 1. カラーパレット

### 変更前後

| 役割 | 旧 | 新 |
|------|----|----|
| プライマリ | `#496aff`（クールブルー） | `#e60023`（Pinterest Red） |
| テキスト | `#111827` | `#211922`（Plum Black） |
| サブテキスト | `#475569` | `#62625b`（Olive Gray） |
| ボーダー | cool gray | `#91918c`（Warm Silver） |
| セカンダリ背景 | cool gray | `#e5e5e0`（Sand Gray） |
| ゲーム背景 | `#0f1923`（クールダーク） | `#1a1611`（暖かいダーク） |

### プライマリブランド

- **Pinterest Red** (`#e60023`): Primary CTA、ブランドアクセント — 主張のある赤
- **Green 700** (`#103c25`): 成功 / ナチュラル系アクセント

### テキスト

- **Plum Black** (`#211922`): 主要テキスト — ほんのりプラム寄りの暖かい黒
- **Black** (`#000000`): セカンダリテキスト、ボタンテキスト
- **Olive Gray** (`#62625b`): 説明文、補助テキスト
- **Warm Silver** (`#91918c`): 無効状態テキスト、インプットボーダー
- **White** (`#ffffff`): 暗い背景・色付き背景上のテキスト

### インタラクティブ

- **Focus Blue** (`#435ee5`): フォーカスリング
- **Link Blue** (`#2b48d4`): リンクテキスト
- **Facebook Blue** (`#0866ff`): ソーシャルログイン

### サーフェス & ボーダー

- **Sand Gray** (`#e5e5e0`): セカンダリボタン背景
- **Warm Light** (`#e0e0d9`): 円形ボタン背景、バッジ
- **Warm Wash** (`hsla(60, 20%, 98%, 0.5)`): 微細な暖かいバッジ背景（ロビーのインバイトボックスなど）
- **Fog** (`#f6f6f3`): ライトサーフェス
- **Dark Surface** (`#33332e`): ダークセクション背景

### セマンティック

- **Error Red** (`#9e0a0a`): フォームエラー状態

---

## 2. タイポグラフィ

### フォントファミリー

Google Fonts（Fraunces / Space Grotesk）は廃止。Pin Sans のフォールバックスタックに統一。

```
Pin Sans, -apple-system, system-ui, Segoe UI, Roboto, Oxygen-Sans,
Apple Color Emoji, Segoe UI Emoji, Segoe UI Symbol, Ubuntu, Cantarell,
Fira Sans, Droid Sans, Helvetica Neue, Helvetica,
ヒラギノ角ゴ Pro W3, メイリオ, Meiryo, ＭＳ Ｐゴシック, Arial
```

### スケール

| 役割 | サイズ | ウェイト | 行高 | 字間 |
|------|--------|----------|------|------|
| Display Hero | 70px | 600 | normal | normal |
| Section Heading | 28px | 700 | normal | -1.2px |
| Body | 16px | 400 | 1.40 | normal |
| Caption Bold | 14px | 700 | normal | normal |
| Caption / Button | 12px | 400–500 | 1.50 | normal |

---

## 3. ボタン・角丸

### 変更点

- ボタン border-radius: `999px`（ピル型）→ **`16px`**（角丸）
- カード・パネル: `12px`〜`40px`
- ロビーパネル: `40px`（Hero スケール）

### ボタン種別

**Primary Red**
- Background: `#e60023`, Text: `#000000`
- Padding: `6px 14px`, Radius: `16px`

**Secondary Sand**
- Background: `#e5e5e0`, Text: `#000000`
- Padding: `6px 14px`, Radius: `16px`

**Circular Action**
- Background: `#e0e0d9`, Text: `#211922`, Radius: `50%`

**Ghost / Transparent**
- Background: transparent, Text: `#000000`

### ボーダー半径スケール

| 値 | 用途 |
|----|------|
| 12px | 小カード、リンク |
| 16px | ボタン、インプット |
| 20px | フィーチャーカード |
| 28px | 大コンテナ |
| 32px | タブ要素、大パネル |
| 40px | ヒーローコンテナ |
| 50% | 円形ボタン |

---

## 4. 画面別スタイル

### ロビー画面

- 背景: 暖かいクリームホワイト（`#fafaf8`）+ サンドグラデーション
- インバイトボックス: ブルー系 → Warm Wash（`hsla(60,20%,98%,0.5)`）+ Sand Border

### ゲーム画面

- トップバーブランド色: 金色 → Pinterest Red
- アクティブプレイヤーのグロー: ブルー → Red
- プレイヤーボックス: 暖かいダーク系 + 16px 角丸

---

## 5. プレイヤー情報表示

| 項目 | 旧 | 新 |
|------|----|----|
| ボックス最小幅 | 90px | 120px |
| パディング | `0.45rem 0.65rem` | `0.6rem 0.8rem` |
| プレイヤー名フォント | 0.78rem | 0.88rem |
| スタックフォント | 0.72rem | 0.80rem |
| 最大名前幅 | 70px | 88px |

---

## 6. ベット額操作 UI

数値入力（`<input type="number">`）を廃止し、スライダー＋プリセットボタンに置き換えた。

### スライダー（`input[type=range]`）

- min: 最小レイズ額（`current_bet + max(big_blind, last_raise_size)`）
- max: 自分のスタック（all-in 上限）
- 自分のターン開始時に最小レイズ額で自動初期化（`useEffect` + `prevIsMyTurn` ref）

### プリセットボタン

| 状況 | ボタン |
|------|--------|
| チェック状況（ベット） | 30% / 50% / 100%（ポット比） |
| コール状況（レイズ） | 2x / 3x / 4x（カレントベット倍率） |

いずれも `Math.min(maxBet, Math.max(minRaise, v))` でクランプ。

### ベット/レイズボタン

- ラベルに現在の金額を表示（例: "Raise 80"）

---

## 7. アクションパネルのレイアウト

### 旧構造（横並び）

```
[My cards]  [Fold] [Check/Call] [Amount input][Bet/Raise] [All-in]  [Status]
```

### 新構造（縦 3 行）

```
Row 1: [My cards] [手役バッジ]                          [Status]
Row 2: [30%][50%][100%] or [2x][3x][4x]  [←slider→ 80]
Row 3: [Fold] [Check/Call] [Raise 80] [All-in]
```

Row 2 は `canAct` が true のときのみ表示。

---

## 8. 手役リアルタイム表示

クライアントサイドで自分のホールカード＋コミュニティカードから手役を計算する（API コール不要）。実装: `src/handEval.ts`。

### アルゴリズム

- カード文字列（例: `"AS"`, `"TH"`）をパース → `{rank: number, suit: string}`
- n 枚から 5 枚の組み合わせ（C(7,5)=21 最大）を全列挙
- 各 5 枚に対してスコア配列を計算し、最高手役を返す
- スコア形式: `[handRank, ...kickers]`（辞書順比較）

### Preflop（コミュニティなし）

- ポケットペア: 「ポケットAA」「ポケットKK」など
- スーテッド: 「AKスーテッド」
- オフスート: 「AKオフスート」

### Flop / Turn / River / Showdown

ワンペア / ツーペア / スリーカード / ストレート / フラッシュ / フルハウス / フォーカード / ストレートフラッシュ / ロイヤルフラッシュ

### 可視性

- 自分のシート（`mySeat`）のホールカードのみ使用
- アクションパネルに赤バッジで表示

---

## 9. レスポンシブ対応

| ブレイクポイント | 幅 |
|---------|-----|
| Mobile | <576px |
| Mobile Large | 576–768px |
| Tablet | 768–890px |
| Desktop Small | 890–1312px |
| Desktop | 1312–1440px |
| Large Desktop | 1440–1680px |
| Ultra-wide | >1680px |

ピングリッド列数: 5+ → 3 → 2 → 1

---

## 10. Do's and Don'ts

### Do

- 暖かいニュートラル（`#e5e5e0`, `#e0e0d9`, `#91918c`）を使う
- Pinterest Red（`#e60023`）は Primary CTA のみに使う
- ボーダー半径は 16px（ボタン/インプット）、20px+（カード）を守る
- Plum Black（`#211922`）を主要テキストに使う

### Don't

- クールグレー系のニュートラルは使わない（常に暖かいオリーブ/サンド系）
- ピル型ボタン（`999px`）は使わない
- 重いシャドウは使わない（フラットデザイン）
- 追加のブランドカラーを導入しない（レッド＋暖かいニュートラルで完結）
- 細いフォントウェイト（400 未満）は使わない

---

## 11. 技術的な決定事項

| 項目 | 決定 |
|------|------|
| 手役計算 | クライアントサイドのみ（API 不要） |
| フォント | Google Fonts 廃止、システムフォント統一 |
| ボタン形状 | ピル型廃止、16px 角丸に統一 |
| スライダー初期値 | ターン開始時に自動セット（最小レイズ額） |
| カラーアクセント | Pinterest Red 一本化（ブルー系を排除） |
