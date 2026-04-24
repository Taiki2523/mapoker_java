# UI Redesign 要件定義書

## フェーズ間の依存関係

フェーズは必ず以下の順番で進める。後のフェーズは前のフェーズが完了していることを前提とする。

```
Phase 1（静的UI・CSS トークン整備）
  └→ Phase 2（ゲームテーブルUI変更）— Phase 1 のトークンと class 設計を前提にする
       └→ Phase 3（動的アクション・アニメーション）— Phase 2 のDOM構造とコンポーネント拡張を前提にする
```

## 1. 現状整理

### 1.1 現在の実装状態

| 対象 | 現状 |
| --- | --- |
| `mapoker-frontend/src/App.tsx` | 画面遷移、API呼び出し、ポーリング、アクション送信、ショーダウン実行、buy-in モーダル制御を集中管理している。`GameScreen` / `LobbyScreen` / `RoomScreen` は基本的に表示責務を持つ。 |
| `mapoker-frontend/src/types.ts` | `GameState` / `Player` / `Showdown` / `Table` / `RoomMember` など、UIが参照するAPIレスポンス型を定義している。 |
| `components/GameScreen/index.tsx` | `TopBar` / `TableArea` / `ActionPanel` の3分割でゲーム画面を構成している。 |
| `components/GameScreen/TopBar.tsx` | `street` / `pot_total` / `current_bet` / `status` を上部バーに表示。招待コピー、マイページ、セッション設定、退席、ログアウトの導線も保持している。 |
| `components/GameScreen/TableArea.tsx` | 中央フェルト、コミュニティカード、ショーダウン表示、待機状態、プレイヤーシート、ベット額表示を描画している。座席位置は `seatPosition()` に依存した絶対配置。 |
| `components/GameScreen/ActionPanel.tsx` | 自分の手札、役名、ターン表示、ベットスライダー、プリセット、アクションボタンを表示。パネル自体は常時描画され、`canAct` に応じて操作可否を切り替えている。 |
| `components/LobbyScreen.tsx` | テーブル一覧取得、公開/非公開フィルタ、フラグ絞り込み、参加導線を提供している。 |
| `components/RoomScreen.tsx` | テーブル作成フォーム。テーブル名、人数、ブラインド、公開/非公開、フラグを設定できる。`visibility` と `flags` のUIブロックが重複表示されている。 |
| `components/Card.tsx` | カード表面のテキスト表現のみ実装。裏面、アニメーション用状態、空スロット専用表現は未実装。 |
| `mapoker-frontend/src/App.css` | ロビー系は明るいアイボリー基調、ゲーム画面はダークブラウン+フェルト基調。1ファイルにロビー、ゲーム、プロフィール、モーダルのスタイルが混在している。 |
| `mapoker-frontend/src/index.css` | 現行デザイントークンは Pinterest 系コメントの付いた `--red`, `--plum`, `--sand` 等で、Spotify系トークンではない。 |

### 1.2 設計書との乖離点

- `DESIGN.md` は Spotify ライクな near-black + green + pill geometry を前提としているが、現行実装はアイボリー/プラム/赤を主軸とする別テーマである。
- `DESIGN.md` のフォント方針（SpotifyMixUI 系、compact typography）は未適用で、現行はシステムフォントスタックである。
- `GAME_UI_DESIGN.md` は2人対戦前提のシンプルな画面説明だが、現行コードは `playerCount 2-9`、ロビー一覧、buy-in、マイページ、セッション設定まで含む。以後の要件はコードを正とし、既存機能を削らない。
- `TopBar.tsx` は phase/pot/bet を表示しているが、フェーズ別色分け、明示的な「あなたのターン / 相手のターン」表示、右端の設定アイコン統一デザインは未整備である。
- `TableArea.tsx` のフェルトは円形に近く、`GAME_UI_DESIGN.md` の「横長楕円テーブル」ではない。フェルトのテクスチャ、アバター、ポジションバッジの視認性、ベットチップ色分けも未実装。
- `TableArea.tsx` のプレイヤーシートにはアバターがなく、カードは常に `CardFace` の簡易表現である。相手カードの専用裏面デザインもない。
- `ActionPanel.tsx` は常時表示構造で、デザイン書の「自分のターン時のみスライドイン」と一致しない。クイックベットも `30/50/100%` または `2x/3x/4x` で、設計書の `最小 / 1/2P / P / ALL` と異なる。
- `RoomScreen.tsx` には `visibility` と `flags` の重複UIが存在する。これは見た目の問題として解消対象だが、送信 payload は維持する。
- `GAME_UI_DESIGN.md` のカードディール、フロップ開示、チップ移動、ショーダウン演出は現行未実装。現状のアニメーションは `your-turn` テキストの単純な pulse のみ。

## 2. フェーズ1: 静的UI変更

### 2.1 目的

`DESIGN.md` の Spotify design system を、ゲームロジックに触れずに静的スタイルへ適用する。対象は色、余白、角丸、タイポグラフィ、静的レイアウト、コンポーネント外観に限定する。

### 2.2 カラーパレット適用（現在CSSとの差分）

- 現行の基調色:
  - ロビー: `#fafaf8`, `--sand`, `--plum`, `--red`
  - ゲーム: ブラウン + フェルト + ゴールド
- 変更後の基調色:
  - ベース背景: `#121212`
  - サーフェス: `#181818`, `#1f1f1f`, `#252525`
  - 主要文字: `#ffffff`
  - 補助文字: `#b3b3b3`
  - アクセント: `#1ed760`
  - 危険/警告/情報: `#f3727f`, `#ffa42b`, `#539df5`
- 適用方針:
  - green は CTA、active、play、現在ターンなど機能的用途に限定する。
  - ロビー系も明色背景をやめ、ゲーム画面と同系統の dark surface に統一する。
  - 現行の gold はポットや勝利表示などポーカー文脈に限定し、基本UIアクセントから外す。

### 2.3 タイポグラフィ適用

- グローバル font-family を SpotifyMixUI 系のフォールバックスタックへ変更する。
- ボタンは uppercase + 1.4px 以上の letter-spacing を標準化する。
- 見出しは 24px / 18px、本文は 16px、caption は 12px-14px の compact scale に再編する。
- `font-weight` は 700 / 600 / 400 に寄せ、現行の曖昧な weight 差を整理する。

### 2.4 ボタン・カードコンポーネントのスタイル

- ボタンの角丸は 16px ではなく、pill (`500px` から `9999px`) を基本形にする。
- `primary`, `secondary`, `ghost`, `danger`, `icon-btn`, `preset-btn` の色・影・hover を Spotify ルールへ統一する。
- `card-pill`, `card-mini`, `.player-box`, `.lobby-browser-row`, `.lobby-filter-card` は dark surface + 6px-8px radius に寄せる。
- 入力、select、slider は dark surface と inset border-shadow ベースへ変更する。

### 2.5 ロビー画面 / RoomScreen の静的レイアウト改善

- ロビー一覧は「ヘッダー」「フィルタ」「テーブル一覧」を明確な dark card セクションに再構成する。
- `RoomScreen` は 2カラム form を維持しつつ、重複している `visibility` / `flags` のUIを1セットへ整理する。
- テーブル一覧行は情報密度を保ちつつ、ステーク、人数、状態、フラグを視覚階層化する。

### 2.6 対象ファイルと具体的な変更内容

| 対象ファイル | 具体的な変更内容 |
| --- | --- |
| `mapoker-frontend/src/index.css` | **CSS トークンの唯一の定義場所とする。** `:root` に Spotify 系デザイントークン（`--color-bg`, `--color-surface`, `--color-accent` 等）を定義し、グローバル font-family と base text color を設定する。`App.css` はこのトークンを参照するだけとし、直接の色値は書かない。 |
| `mapoker-frontend/src/App.css` | ロビー/ゲーム/フォーム/ボタン/入力/カードの共通見た目を全面的に再定義する。light theme の `lobby-screen` / `lobby-panel` を dark theme 化し、pill button、compact typography、shadow ルールを統一する。色値は必ず `index.css` のトークンを参照する（直値禁止）。 |
| `mapoker-frontend/src/components/LobbyScreen.tsx` | DOM構造は基本維持しつつ、見出し、フィルタ、テーブル行の class 再編を行う。フィルタUIは chip/pill ベースにする。データ取得・絞り込みロジックは変更しない。 |
| `mapoker-frontend/src/components/RoomScreen.tsx` | 作成フォームの重複している `visibility` / `flags` UIを1セットへ整理し、説明文、セクション見出し、アクション行を Spotify 系カードフォームへ再配置する。送信する `CreateGameConfig` は変更しない。 |
| `mapoker-frontend/src/components/Card.tsx` | 既存のカード表面マークアップを、Phase 2 で front/back/slot に拡張しやすい class 構成へ整理する。カードデータ解釈ロジックは変更しない。props 拡張の方針は §3.4 に従う。 |

## 3. フェーズ2: ゲームテーブルUI変更

### 3.1 目的

`GAME_UI_DESIGN.md` のテーブル表現をベースに、現行の `GameScreen` 構造を保ったままゲーム画面を刷新する。対象はゲーム画面のレイアウト・見た目・表示階層であり、アクション送信・ターン判定・ポーリングは変更しない。

### 3.2 テーブル（楕円形フェルト）の実装要件

- 現行の円形寄りフェルトを、横長楕円に変更する。
- 外周は dark wood rail、内側は green felt gradient、さらに擬似要素で軽い texture/noise を追加する。
- ポット、コミュニティカード、ショーダウン結果、待機表示の中央配置ルールを再定義する。
- 既存の absolute layout を踏襲し、座席の基準点は `seatPosition()` を使い続ける。

### 3.3 プレイヤーシートのレイアウト要件

- 各 seat は「アバター」「プレイヤー名」「ポジション/状態バッジ」「Stack」「手札」で構成する。
- アバター未設定時は頭文字フォールバックを使う。新規APIは追加しないため、表示文字列は既存の `displayName()` から生成する。
- `button_index` / `small_blind_idx` / `big_blind_idx` / `folded` / `all_in` を使い、小型バッジで視覚化する。
- `current_player` は gold/green 系の強いグローで強調する。`folded` はグレーアウト、勝者は別の glow にする。
- `player.contributed` は seat と pot の間に chip 表現で表示する。色階層は金額帯ごとの視覚差として実装してよいが、金額算出ロジックは追加しない。
- 現行コードが 2-9席に対応しているため、2人専用レイアウトへ固定しない。

### 3.4 コミュニティカードエリア

- コミュニティカードは中央横一列を維持する。
- 未公開カードは `??` 表示ではなく、薄いアウトラインの空スロットに変更する。
- カードサイズは seat card より一段大きくし、中央の視認性を優先する。
- `Card.tsx` は以下の props 設計で front / back / slot を切り替えられる presentational コンポーネントに拡張する。

```ts
type CardVariant = 'front' | 'back' | 'slot'

interface CardProps {
  card?: string        // カード文字列（例: "Ah", "Td"）。variant が 'front' の場合のみ使用
  variant: CardVariant // 'front'=表面, 'back'=裏面, 'slot'=未配置プレースホルダー
  size?: 'sm' | 'md'  // 'sm'=プレイヤー手札, 'md'=コミュニティカード（デフォルト sm）
}
```

カード文字列のパースロジック（スート・ランク抽出）は現状維持。variant と size の切り替えは CSS class のみで制御する。

### 3.5 アクションパネルのUI改善

- パネルは「情報行」「ベット操作行」「アクションボタン行」の3層構造を明確化する。
- 非ターン時は collapsed 表示または非表示にし、ターン時のみ強調表示する。
- クイックベットは `最小 / 1/2P / P / ALL` 表記に刷新する。ただし、内部的には既存の `betPresets`, `minRaise`, `maxBet`, `actionAmount` を使う。
- ボタン配色は fold=dark red、check/call=dark green、bet/raise=dark blue を基本にし、pill geometry を適用する。
- `all_in` 専用ボタンを残すか、`ALL` preset + primary action に統合するかはUI判断でよいが、送信アクション種別は現行の `fold/check/call/bet/raise/all_in` を維持する。

### 3.6 ステータスバー改善

- 左: ブランド、phase badge、テーブル状態
- 中央: pot / current bet / 任意の補助情報
- 右: invite copy、my page、session settings
- `street` は phase badge として色分けする。`status` は補助テキストとして再設計する。
- 既存の auto refresh toggle と退席予約表示は残す。

### 3.7 対象ファイルと具体的な変更内容

| 対象ファイル | 具体的な変更内容 |
| --- | --- |
| `mapoker-frontend/src/components/GameScreen/index.tsx` | `TopBar` / `TableArea` / `ActionPanel` の責務分割は維持しつつ、必要な見た目用 props の受け渡しだけを調整する。API呼び出しや state 管理は `App.tsx` に残す。 |
| `mapoker-frontend/src/components/GameScreen/TopBar.tsx` | 情報を左・中央・右の3ブロックへ再配置し、phase badge・status text・icon buttons の見た目を刷新する。既存の操作導線とイベントハンドラは維持する。 |
| `mapoker-frontend/src/components/GameScreen/TableArea.tsx` | 楕円テーブル、中央ポット、コミュニティカード、ショーダウンオーバーレイ、待機表示、seat card のDOMを再設計する。アバター、seat header、chip amount、状態別 class を追加する。 |
| `mapoker-frontend/src/components/GameScreen/ActionPanel.tsx` | 情報行、slider、preset、action button の構造を整理し、turn時/非turn時の表示差を強化する。既存の `onSendAction` と `setActionAmount` の使い方は変えない。 |
| `mapoker-frontend/src/components/Card.tsx` | 表面/裏面/空スロットの3状態を扱える見た目コンポーネントへ拡張する。カード文字列のパース仕様は現状維持。 |
| `mapoker-frontend/src/App.css` | `game-layout`, `game-topbar`, `poker-felt`, `player-seat`, `player-box`, `badge`, `bet-chip`, `community-cards-row`, `action-panel` を中心に全面改修する。レスポンシブ時も 2-9席の可読性を維持する。 |

## 4. フェーズ3: 動的アクション・アニメーション

### 4.1 方針

アニメーションは presentation-only に限定し、ゲーム進行条件やAPI呼び出し順序を変えない。トリガーは既存の `game`, `showdown`, `canAct`, `player.contributed`, `community`, `street`, `status` の変化のみを使う。

### 4.2 カードディールアニメーション

- ハンド開始時に、中央デッキ位置から各 seat の hole card へ移動する演出を追加する。
- 実データは既存の `game.players[idx].hole` を使い、演出中の見た目だけ局所 state で管理する。
- 相手カードは通常時 back face、ショーダウン時のみ front face に切り替える。

### 4.3 アクションパネルのスライドイン

- `canAct === true` のタイミングで下からスライドイン、`false` で縮小またはフェードアウトさせる。
- 入力要素自体の有効/無効ロジックは現行の `disabled` 判定をそのまま使う。

### 4.4 ショーダウン演出

- `showdown` 到達時に、勝者名、役名、払戻しを段階表示する。
- 勝者 seat の glow、敗者 seat の dim、コミュニティカードの強調、結果オーバーレイの pop-in を追加する。
- 「次のゲームへ」ボタンは現行コードに存在しないため、本フェーズではUI案として留める。自動次ハンド遷移を壊さないことを優先する。

### 4.5 ベット時チップアニメーション

- `player.contributed` の増分を検知し、seat から pot へ chip token が飛ぶ演出を追加する。
- 金額計算は既存値の差分のみを用い、新たな賭け計算ロジックは実装しない。

### 4.6 アクティブターンのパルスエフェクト

- 現行の `your-turn` テキスト pulse を、seat card の border glow、phase/status accent、action panel highlight に拡張する。
- pulse は過度に強くせず、視認性確保を主目的とする。

### 4.7 実装アプローチ推奨

- 優先:
  - CSS animation / transition
  - `transform`, `opacity`, `filter`, `box-shadow` を中心にGPU負荷の軽い演出を採用する。
- 条件付き推奨:
  - Framer Motion
  - カード配布、チップ移動、ショーダウン段階表示のような enter/exit 管理が複雑な場合のみ導入を検討する。
  - **ただし外部依存の追加になるため、導入前にプロジェクトオーナーの承認を得ること。** `package.json` への追加が必要であり、CSS animation のみで実現できない場合にのみ採用する。
- 非推奨:
  - Canvas/WebGL への置き換え
  - ゲーム状態を複製する大きな animation store

### 4.8 対象ファイルと具体的な変更内容

| 対象ファイル | 具体的な変更内容 |
| --- | --- |
| `mapoker-frontend/src/components/GameScreen/TableArea.tsx` | deal / chip / showdown 用の presentational state とアニメーション class の付与を担当する。APIデータの解釈は既存 `game` / `showdown` からのみ行う。 |
| `mapoker-frontend/src/components/GameScreen/ActionPanel.tsx` | turn開始/終了に応じた enter/exit animation を追加する。アクション可否ロジックはそのまま使う。 |
| `mapoker-frontend/src/components/Card.tsx` | flip animation に必要な front/back ラッパー構造を追加する。 |
| `mapoker-frontend/src/App.css` | keyframes、transition、state class、reduced motion 対応を追加する。 |

## 5. 実装上の注意点

### 5.1 変更してはいけない箇所

- `App.tsx` の API 呼び出し先、HTTPメソッド、payload 形式を変更しない。
- `refreshGame`, `refreshMembers`, `refreshTable`, `createGame`, `joinRoom`, `loginAsPlayer`, `leaveRoom`, `startHand`, `sendAction`, `runShowdown` の処理順序を変更しない。
- `toCall`, `minRaise`, `maxBet`, `betPresets`, `canAct`, `isMyTurn`, `isSpectator`, `myHandName` の計算式を変更しない。
- `seatPosition()` に依存する席配置ロジックを置き換えない。見た目調整は CSS と DOM のみで吸収する。
- ロビーの絞り込み条件（`visibility`, `selectedFlags`）やテーブル作成 payload（`tableName`, `playerCount`, `smallBlind`, `bigBlind`, `visibility`, `flags`）を変更しない。

### 5.2 APIレスポンスのフィールドで使用するもの（`types.ts` 準拠）

- `GameState`
  - `id`
  - `status`
  - `street`
  - `button_index`
  - `small_blind_idx`
  - `big_blind_idx`
  - `current_player`
  - `current_bet`
  - `last_raise_size`
  - `big_blind`
  - `pot_total`
  - `community`
  - `can_start_hand`
  - `viewer_membership_active`
  - `can_rebuy`
  - `last_showdown`
- `Player`
  - `id`
  - `stack`
  - `contributed`
  - `folded`
  - `all_in`
  - `hole`
- `Showdown`
  - `winners`
  - `best_hand.rank`
  - `payouts`
- `Table`
  - `id`
  - `name`
  - `stake.small_blind`
  - `stake.big_blind`
  - `min_buy_in`
  - `max_buy_in`
  - `max_players`
  - `flags`
  - `visibility`
  - `status`
  - `member_count`
  - `members`
  - `game`
- `RoomMember`
  - `name`
  - `seatIndex`
  - `pendingLeave`

### 5.3 既存コンポーネント構造を維持すべき理由

- `App.tsx` に API通信と状態遷移が集約されており、ここを崩すと UI-only redesign の範囲を越えて回帰リスクが高い。
- `GameScreen -> TopBar / TableArea / ActionPanel` の分割は、静的UI更新とアニメーション追加を局所化しやすい。
- `LobbyScreen` / `RoomScreen` は既にデータ取得・入力送信の境界が整理されているため、レイアウト変更だけで目的を達成できる。
- `Card.tsx` を表示専用のまま拡張すれば、カード表現の刷新を全画面へ横展開しやすい。
- `App.css` にスタイルが集中しているため、まずは token と class 設計を整理し、ロジックファイルへの影響を最小化するべきである。

### 5.4 補足

- `GAME_UI_DESIGN.md` の内容は一部古く、現行コードに存在するロビー一覧、buy-in、マイページ、セッション設定を削除してはならない。
- `ActionPanel` の役名表示は現行で `bestHandName()` に依存している。今回の redesign では「見た目のみ変更」とし、手役判定ロジックには触れない。
- アニメーション追加時は `prefers-reduced-motion` を考慮し、演出を無効化できるようにする。
