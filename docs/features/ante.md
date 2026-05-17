# アンティ設定

**ステータス**: `planned`

## 概要

各ハンド開始時に全プレイヤーが強制的に一定額を支払うアンティを設定できるようにする。
BB ante（BBが全員分を代払いする方式）は採用しない。毎ハンド、テーブルの全プレイヤーが個別に支払う。

---

## anteとは

**ante（アンティ）**とは、カードが配られる前に全員が強制的にポットへ入れる参加料。
ブラインド（SB/BB）とは異なり、コール額の基準にはならない。

```
SB = 50, BB = 100, Ante = 10（6人テーブル）

初期ポット = 10×6 + 50 + 100 = 210

UTGのコール額 = 100  ← anteを払っていても BBの100にコールする
```

anteはポットには入るが、現在のベット額（`contributed`）とは別物。

---

## テーブル作成時の設定

### UI

テーブル作成画面でBBと同じ画面上に「アンティ額」の入力欄を追加する。

- デフォルト値：`bigBlind / 10`（端数切り捨て、最低0）
- 変更可能範囲：`0`〜任意（0はアンティなし）
- `ante = 0` の場合はアンティ徴収をスキップする

### API

`POST /v1/tables` のリクエストボディに `ante` フィールドを追加する。

```json
{
  "table_name": "My Table",
  "player_count": 6,
  "big_blind": 100,
  "small_blind": 50,
  "ante": 10,
  "visibility": "public"
}
```

`ante` が省略された場合は `bigBlind / 10` をデフォルトとして使用する。

---

## ハンド開始時の処理順序

`GameState.startHand()` 内での実行順序：

```
1. プレイヤー状態リセット（contributed=0, totalContrib=0, ...）
2. 参加可能プレイヤー数チェック（stack > 0 が2人以上）
3. ボタンを次のアクティブプレイヤーへ移動
4. 全プレイヤーからanteを徴収（ante > 0 の場合）
5. ante徴収後のオールイン状態を確定
6. SBブラインド投下
7. BBブラインド投下
8. ブラインド投下後のオールイン状態を確定
9. ホールカード配布
```

anteはブラインドより先に徴収する（ポーカーの標準的なルール）。

---

## contributed と totalContrib の扱い

**anteは `contributed` に加算しない。`totalContrib` にのみ加算する。**

これが実装上の最重要ポイント。現在の `Player` フィールドの意味：

| フィールド | 意味 | ストリートをまたぐか |
| --- | --- | --- |
| `contributed` | 現ストリートでのベット額（コール額計算に使用） | リセットされる |
| `totalContrib` | ハンド全体の累計拠出額（サイドポット計算に使用） | 引き継がれる |

anteを払った後の状態：

```
（例）ante=10, SB=50, BB=100

ante徴収後：
  全プレイヤー: contributed=0, totalContrib=10

SBポスト後：
  SB: contributed=50, totalContrib=60

BBポスト後：
  BB: contributed=100, totalContrib=110
```

これにより、コール額計算は正しく動く：

```java
// ActionValidator でのコール額計算
callAmount = currentBet - player.contributed;
// UTGの場合: 100 - 0 = 100  ← ante 10を払っていても100
```

サイドポット計算も正しく動く：

```java
// buildSidePots() は totalContrib を使う
int tc = players.get(i).getTotalContrib();  // ante込みの合計
```

---

## オールインアンティの扱い

anteを払うとき、スタックがante額に満たないプレイヤーは、持ち金全額をanteとして出してオールインになる。

```java
private void postAnte(int playerIndex, int amount) {
    if (amount <= 0) return;
    Player p = players.get(playerIndex);
    if (p.isFolded()) return;          // stack=0でfold済みはスキップ
    if (p.getStack() < amount) amount = p.getStack();  // オールインante
    if (amount == 0) return;
    p.setStack(p.getStack() - amount);
    // contributed は触らない ← 重要
    p.setTotalContrib(p.getTotalContrib() + amount);
    if (p.getStack() == 0) p.setAllIn(true);
    pot += amount;
}
```

オールインアンティのプレイヤーはハンドに参加できる（ただしポットの受け取りはオールインした額まで）。サイドポット計算は `totalContrib` を使うため、自動的に正しく処理される。

---

## 変更ファイル一覧

### ドメイン層

**`GameState.java`**
- `ante` フィールドを追加（`private int ante`）
- `newGame(...)` に `ante` パラメータを追加、フィールドにセット
- `startHand(int bigBlind)` 内でブラインド投下前に `postAnte` を呼び出す
- `postAnte(int playerIndex, int amount)` プライベートメソッドを追加
- getter/setter を追加（`getAnte()`, `setAnte(int)`）

**`Player.java`**
- 変更なし（`totalContrib` が ante を吸収できる）

### アプリケーション層

**`GameService.java`**
- `createGame(...)` に `ante` パラメータを追加し、`GameState.newGame` へ渡す

### HTTP層

**`CreateTableRequest.java`**
- `ante` フィールドを追加（`@Nullable Integer ante`）
- コンパクトコンストラクタで `null` の場合は `bigBlind / 10` をデフォルト設定

**`GameResponse.java`**
- `ante` フィールドを追加（`int ante`）
- `GameResponse.from(...)` で `g.getAnte()` をマッピング

### インフラ層

**`TableRecord.java`**
- `ante` フィールドを追加（`int ante`）

**DBマイグレーション（新規Vファイル）**
- `tables` テーブルに `ante INTEGER NOT NULL DEFAULT 0` カラムを追加
- `GameState` のJSONシリアライズに `ante` フィールドが含まれるため、`games` テーブルの変更は不要

---

## GameResponse への追加

フロントエンドがanteを表示・ゲーム情報として使うために `ante` をレスポンスに含める。

```json
{
  "id": "...",
  "status": "in_progress",
  "big_blind": 100,
  "ante": 10,
  "pot_total": 210,
  ...
}
```

---

## 実装で間違えやすいポイント

### 1. anteをcontributedに足してしまう

```java
// 誤り
p.setContributed(p.getContributed() + ante);

// 正しい（totalContribだけ増やす）
p.setTotalContrib(p.getTotalContrib() + ante);
```

`contributed` に ante を加算すると、UTGのコール額が `100 - 10 = 90` になってしまう。

### 2. anteをブラインドより後に徴収する

```java
// 誤り（ブラインド後に徴収するとcontributedとの整合が崩れる）
postBlind(smallBlindIdx, bigBlind / 2);
postBlind(bigBlindIdx, bigBlind);
for (Player p : players) postAnte(idx, ante);  // ← ここはダメ

// 正しい（ブラインド前に徴収する）
for (int i = 0; i < players.size(); i++) postAnte(i, ante);
postBlind(smallBlindIdx, bigBlind / 2);
postBlind(bigBlindIdx, bigBlind);
```

### 3. サイドポット計算からanteを除外する

anteは `totalContrib` に含まれるため、`buildSidePots()` は変更不要。除外しようとする必要はない。

---

## テスト方針

`GameStateTest.java` に以下を追加：

- ante > 0 のとき `startHand` でポットが `ante × 人数 + SB + BB` になること
- anteを払ったプレイヤーがプリフロップでコールする額が `bigBlind` であること（anteが差し引かれないこと）
- anteでオールインになったプレイヤーがサイドポット計算で正しく扱われること
- `ante = 0` のとき動作が現状と変わらないこと

`GameStateSidePotTest.java` に以下を追加：

- anteによるオールインを含むサイドポット計算

---

## まとめ

| 項目 | 仕様 |
| --- | --- |
| 設定タイミング | テーブル作成時 |
| デフォルト値 | `bigBlind / 10`（端数切り捨て） |
| 支払い対象 | 毎ハンド、全プレイヤー個別 |
| BB ante方式 | 採用しない |
| 徴収タイミング | ブラインド投下より前 |
| `contributed` への影響 | なし（コール額に影響しない） |
| `totalContrib` への影響 | あり（サイドポット計算に含まれる） |
| スタック不足時 | 持ち金全額をオールインante |
