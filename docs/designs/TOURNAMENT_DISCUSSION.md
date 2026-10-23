# トーナメント機能 実装ディスカッション

このファイルは `docs/features/tournament.md` の設計を Java 実装に落とし込む際の
議論・疑問点・決定事項を記録するためのファイルです。

---

> **バージョン位置づけ**: トーナメントモードは **v2** の機能です。
> 現在のアプリは v1.2.0（キャッシュゲーム・オフラインモード完成）。
> 以降の "初期実装" や "後回し" という表現は v2 内の優先度を指します。

---

## 現状把握（2026-05-23 時点）

### 実装済み（流用できるもの）

| 機能 | 場所 | 備考 |
|------|------|------|
| ゲームコアロジック | `GameState.java` | ブラインド・アクション・サイドポット・showdown 全部ある |
| 全員均等アンティ | `GameState.postAnte()` | 全 active プレイヤーから均等徴収。レベルごとに `setAnte()` で更新可能 |
| テーブル管理 | `TableLifecycleService` / `TableRecord` | `game_type` フィールドあり（現在 "ring" / "offline"） |
| ウォレット | `WalletService` + `wallet_ledger` | エントリーフィー徴収・賞金還元に使える |
| ハンド履歴フック | `GameActionService` | showdown/fold-win の両経路で終了検知済み |
| サイドポット | `GameState.resolveShowdown()` | トーナメントでも共通利用可能 |
| sitting_out フラグ | `Player.sittingOut` | アクションスキップに使用中 |

### 未実装（新規に作るもの）

- トーナメント状態管理（登録・進行・終了）
- ブラインドスケジュール・時間ベース自動レベルアップ
- 敗退判定・順位確定・同時敗退処理
- 再エントリーフロー
- 賞金配分（wallet 連携）
- トーナメント用フロントエンド UI

---

## 決定済み事項

### [決定] アンティ方式 → 全員均等アンティ（BBアンティ不採用）

- 現在の `GameState.postAnte()` をそのまま使う
- ブラインドスケジュールの `ante` フィールド = レベルごとの「1人あたりの額」
- `GameState.ante` はゲーム作成時固定だが、ハンド開始前に `setAnte(levelAnte)` で更新する
- `GameLifecycleService.startHand(id, bigBlind, ante)` のオーバーロードを追加する

---

### [決定] level_type → minutes ベースのみ実装

- `tournaments.next_level_at TIMESTAMP` で永続化
- ハンド開始時に `now >= next_level_at` をチェックし、該当すればレベルを進める
  （`while` ループで複数スキップにも対応）
- これにより「進行中のハンドは現レベルで完結」が自然に保証される
- API レスポンスに `seconds_until_next_level` を含め、フロントでカウントダウン表示
- `next_level_at` は `tournaments` テーブルに直接持つ（別ログテーブルは不要）
- `hands_in_level` カラムは不採用

---

### [決定] ブラインドスケジュール JSON フォーマット

`hands` フィールドは除去。レベル時間は `level_duration_seconds` で統一。

```json
{ "level": 1, "sb": 50, "bb": 100, "ante": 0 }
```

---

### [決定] Q3: ヘッズアップ特殊ルール → 対応済み（変更不要）

`GameState.startHand()` はすでに `activePlayers == 2` を検知して正しく処理している。

- ボタン = SB (`smallBlindIdx = buttonIndex`) ✓
- プリフロップ: SB が先行動（`nextActive(bigBlindIdx)` = ボタン/SB）✓
- フロップ以降: BB が先行動（`nextActive(buttonIndex)` = 非ボタン/BB）✓

v1（リングゲーム）から正しく動いており、バグではない。domain 変更不要。

---

### [決定] Q4: 同時敗退の順位決定 → `stack_at_hand_start` カラム追加

`tournament_players.stack_at_hand_start INT` をスキーマに追加。  
ハンド開始時に全プレイヤーのスタックを記録し、同時バスト時はこの値で昇順ソートして下位から順位を割り当てる。

---

### [決定] Q5: tournament と table の関係 → MTT 前提の複数テーブル管理

将来的に 9人卓 × 10卓以上、100人規模の MTT を扱うため、`tournaments.table_id` に
単一テーブルを直接持たせる設計は採用しない。

`POST /v1/tournaments/{id}/start` 時に `TournamentService` が内部的に必要卓数を計算し、
`game_type='tournament'` の `tables` を複数作成する。トーナメントと各 table の関係は
`tournament_tables` で管理する。

トーナメントの席・順位・スタックの正本は `tournament_players` とする。
既存の `tables` / `games` / `players` は「卓単位のポーカー実行エンジン」として再利用し、
ハンド開始・終了時に `tournament_players` と同期する。

初期実装では 1卓運用でもよいが、DB/API/Service の形は最初から MTT 前提にする。

---

### [決定] Q5-2: 初期配置・テーブルバランス・卓割れ

#### 初期配置

- 最大卓人数は v2 初期では 9人
- 必要卓数 = `ceil(参加者数 / max_players_per_table)`
- 全卓の人数差が最大1人になるように配分する
- 参加者をシャッフルし、`table_number` と `seat_index` をランダムに割り当てる
- 各卓の初期ボタンは `buttonSeat = random(activeSeats)` で決定する
- 卓ごとのブレイク優先順位 `break_order` を開始時にランダム生成または事前設定する

例: 100人 / 9人卓

```
ceil(100 / 9) = 12卓
4卓 × 9人
8卓 × 8人
```

#### テーブルバランス

テーブルバランスは「卓数を変えず、人数差を小さくする」処理。

- `maxPlayers - minPlayers >= 2` になった時点で実施する
- 移動元は最多人数卓
- 移動先は最少人数卓
- 移動対象は移動元テーブルの「次のハンドで BB になるプレイヤー」
- 移動先は「BB に最も近い不利な空席」
- 原則として SB には直接配置しない

WSOP/TDA に近い停止基準として、最多卓より3人以上少ない卓は次ハンドを開始せず、
バランス完了を待つ。

#### テーブルブレイク / 卓割れ

卓割れは「1卓を閉鎖し、その卓の全員をほかの卓へ移す」処理。

- 必要卓数 = `ceil(activePlayers / max_players_per_table)`
- `activeTableCount > requiredTables` なら卓割れを実施する
- 閉鎖対象は最少人数卓ではなく、開始時に決めた `break_order` に従う
- 壊した卓の全 active プレイヤーは、移動先 table/seat をランダム再配置する
- バランス移動とは異なり、次回BB予定者を選ぶ処理はしない

終盤は通常のオンライン MTT として、残り人数 `<= max_players_per_table` で1卓に統合する。
将来的には WSOP 風に「9-handed なら残り10人で非公式ファイナルテーブル化」などを
設定で選べるようにする。

#### 移動履歴

初期配置・バランス・卓割れ・ファイナル再抽選は `tournament_table_movements` に記録する。
不公平感の説明、バグ調査、リプレイ検証に使う。

---

### [決定] Q6: ハンド終了フック → 両経路に差し込む

ハンド終了は2経路あり、両方から `TournamentService.onHandFinished(tableId)` を呼ぶ:
1. `GameActionService.resolveShowdown()` 末尾
2. `GameActionService.applyAction()` で `isFoldWin() == true` を検知した時

フックの中で「スタック書き戻し → 敗退判定 → standings 更新」を行う。
進行中ハンドへの移動割り込みを避けるため、卓割れ・テーブルバランスの実移動は
次ハンド開始前に集約して実行する。

#### ハンド終了後 / ハンド開始前の責務分担

ハンド終了時:

```
1. GameState の seat stack を tournament_players.stack へ書き戻す
2. bust 判定
3. busted_sequence 採番
4. status / rank / standings 更新
5. 必要なら対象 tournament に rebalance/break 検討対象フラグを立てる
```

ハンド開始前:

```
1. レベルアップ判定
2. reentry_level_limit を超えた busted_reentry_available を busted に確定
3. 卓割れ
4. テーブルバランス
5. ショートテーブル停止対象でなければ startHand
```

#### ハンド自動進行

トーナメント卓は自動進行を基本とする。

- `TournamentService.onHandFinished(tableId)` の後、短いインターバルを挟んで次ハンド開始を予約する
- 次ハンド開始前には必ず tournament-aware な pre-start 処理を通す
- 手動 `/start-hand` は v2 では管理用 / フォールバック扱い
- 通常リングゲームの手動 startHand とは分離する
- `tournament_tables.status != active` の卓は自動開始しない
- 次ハンド開始予約は `tournament_tables.next_hand_at` に永続化する

自動開始フロー:

```
1. onHandFinished(tableId)
2. stack 書き戻し / bust 判定 / standings 更新
3. `tournament_tables.next_hand_at = now + next_hand_delay_seconds`
4. startTournamentHand(tableId)
5. レベルアップ / 再エントリー受付終了 / 卓割れ / バランス
6. 当該 table が active かつ停止対象でなければ GameLifecycleService.startHand(...)
```

自動開始ディレイは v2 初期では固定値でよい。

```
next_hand_delay_seconds = 5
```

将来的にはトーナメント設定として変更可能にする。

自動開始 worker:

```
@Scheduled(fixedDelay = 1000)
1. next_hand_at <= now AND status = 'active' の tournament_tables を取得
2. 対象行を transaction 内でロック
3. startTournamentHand(tableId)
4. 開始成功時は next_hand_at = NULL
5. 開始不可の場合は status/next_hand_at を理由に応じて更新
```

これによりアプリ再起動後も、未開始の次ハンド予約を DB から復旧できる。

複数プロセスで worker が動いても二重開始しないよう、対象取得は PostgreSQL の
行ロックを使う。

```sql
SELECT *
FROM tournament_tables
WHERE next_hand_at <= now()
  AND status = 'active'
FOR UPDATE SKIP LOCKED
LIMIT 20;
```

`startTournamentHand(tableId)` は開始直前に紐づく `GameState.status` も確認する。
すでに `IN_PROGRESS` の場合は二重開始せず、`next_hand_at = NULL` にして終了する。

#### アクションタイムアウト

自動進行トーナメントでは、プレイヤーの放置で卓全体が止まらないよう
アクションタイムアウトを v2 範囲に含める。

```
action_timeout_seconds = 30
```

タイムアウト時の自動アクション:

```
check 可能なら check
check 不可なら fold
```

`games.action_deadline_at` を追加し、現在アクション待ちのプレイヤーに期限を持たせる。

- `startHand` 後、最初の current_player に `now + action_timeout_seconds`
- 通常 action 後、次の current_player に `now + action_timeout_seconds`
- `SHOWDOWN` / `FINISHED` / all-in で追加アクション不要な状態では `NULL`

timeout worker:

```
@Scheduled(fixedDelay = 1000)
1. action_deadline_at <= now AND status = 'in_progress' の tournament game を取得
2. game / tournament_table を transaction 内でロック
3. 現在 player の legal actions を確認
4. check 可能なら check、不可なら fold を GameActionService 経由で適用
5. action 後に次 player の action_deadline_at を更新
```

通常リングゲームには初期適用しない。v2 では `game_type='tournament'` の卓だけを対象にする。

#### 連続タイムアウトと auto-fold

放置プレイヤーが毎ハンド 30秒を消費して進行を遅らせることを防ぐため、
トーナメント用に `auto_fold` を導入する。

既存の `Player.sittingOut` はハンド参加除外の意味が強いため、トーナメントの
「席は残るが自動 check/fold」状態とは分けて扱う。

```
consecutive_timeouts >= 2
  → tournament_players.auto_fold = true
```

`auto_fold = true` の扱い:

- 席と stack は残る
- ハンドには参加する
- ブラインド / アンティは通常通り支払う
- 自分の action では即時に check 可能なら check、不可なら fold
- プレイヤー本人の操作で `auto_fold = false` に戻せる
- 復帰時に `consecutive_timeouts = 0`

通常 action を時間内に行った場合は `consecutive_timeouts = 0` に戻す。

`auto_fold` の即時 check/fold は、ハンド開始後と各 action 後に実行する。

```
startHand 後:
  current_player が auto_fold なら processAutoFoldChain()

通常 action 後:
  次の current_player が auto_fold なら processAutoFoldChain()

timeout 後:
  自動 check/fold
  consecutive_timeouts++
  consecutive_timeouts >= 2 なら auto_fold = true
```

`processAutoFoldChain()` は auto_fold プレイヤーが連続していても進行できるよう、
合法 action を確認しながら check/fold を繰り返す。無限ループ防止のため、
1回の呼び出しで処理する最大回数は seat 数を上限にする。

自動進行に使う `tournament_tables.status`:

```
active    // 通常進行。自動開始対象
paused    // バランス/卓割れ待ち。自動開始しない
breaking  // 卓割れ処理中。自動開始しない
closed    // 閉鎖済み
finished  // トーナメント終了で停止
```

ショートテーブル停止や卓割れ対象になった卓は `paused` または `breaking` にする。
バランス完了後に `paused -> active` へ戻し、必要なら次ハンド開始 job を再予約する。

---

### [決定] Q7: wallet_ledger_reason ENUM 拡張 → 追加する

`TOURNAMENT_ENTRY` / `TOURNAMENT_PRIZE` / `TOURNAMENT_REENTRY` の3値を追加。  
Flyway migration として実装（PostgreSQL ENUM の `ADD VALUE` は既存行に影響なし）。

---

### [決定] Q10: フロントエンド v2 初期リリースの範囲 → フル実装

MTT 前提で、ゲーム画面だけではなくロビー・順位・移動通知まで含める。

Tournament List:

- 登録中 / 進行中 / 終了済みトーナメント一覧
- 自分が参加中の tournament への復帰導線

Tournament Lobby:

- トーナメント作成フォーム
- エントリー登録
- 参加者一覧
- テーブル一覧
- ブラインドスケジュール
- prize structure
- host の start ボタン

My Tournament Status:

- 自分の table / seat
- 自分の rank / status
- 再エントリー可否
- テーブル移動通知

Game Screen Additions:

- 現在 level
- next level countdown
- action timer
- average stack
- active players count
- table number
- auto-fold toggle

Standings:

- 全体順位
- active / busted_reentry_available / busted の表示
- prize chips

テーブル移動はモーダルまたはトーストで強制通知し、新しい卓へ遷移するボタンを出す。
`busted_reentry_available` のプレイヤーには再エントリー導線を表示する。
終了時は敗退・順位確定モーダルと賞金配分結果を表示する。

---

### [決定] v2 スコープ → リバイではなく再エントリー

| 項目 | 決定内容 |
|------|----------|
| リバイ | 不採用 |
| 再エントリー条件 | バスト後、再エントリー受付期間内のみ |
| 再エントリースタック | 固定 `starting_stack` |
| 再エントリー上限回数 | 1回まで |
| 再エントリー費 | `entry_fee` と同額 |
| 座席 | 新規参加者として再配置 |
| タイミング | 受付後、次ハンド開始前に空席へ配置 |

**状態遷移**:

```
active → (スタック0 & ハンド終了)
       → busted_reentry_available (rank未確定)
           └─ [POST /reentry 受信 & 受付期間内 & reentry_count < 1]
                → active (stack = starting_stack, reentry_count++, 新しい table/seat へ配置)

active → (スタック0 & ハンド終了 & 再エントリー不可)
       → busted (rank確定)

busted_reentry_available → (受付期間終了 or 再エントリーしない)
                         → busted (rank確定)
```

`tournament_players.status` の値:

```
active                    // プレイ中
busted_reentry_available  // バスト済み・再エントリー可能・順位未確定
busted                    // 脱落確定・順位確定済み
```

再エントリーはバスト済みプレイヤーが新規エントリーし直す扱い。
そのため 60秒席占有や `rebuy_eligible` 状態は持たない。
再エントリー受付期間は `reentry_level_limit` で制御する。

#### 再エントリー設定のバリデーション

再エントリー受付期間はレベルで指定する。v2 初期では事故を避けるため固定上限を持つ。

- `allow_reentry = false`
  - `reentry_level_limit = NULL`
  - `max_reentries = 0`
- `allow_reentry = true`
  - `reentry_level_limit >= 1`
  - `reentry_level_limit <= 4`
  - `reentry_level_limit < blind_schedule` の最終 level
  - `max_reentries >= 1`

例: `reentry_level_limit = 4` の場合、Level 4 までは再エントリー可能。
Level 5 に上がるハンド開始前に受付終了処理を行う。

#### 再エントリー受付終了と順位確定

`busted_reentry_available` は再エントリー受付期間が終わるまで順位未確定のまま保持する。

- 再エントリー受付中: `busted_reentry_available` のまま、本人は `/reentry` 可能
- 残り人数・卓数計算には含めない
- 席は占有しない
- standings には「再エントリー可」として表示する
- `current_level > reentry_level_limit`: すべての `busted_reentry_available` を `busted` にして `rank` 確定
- `reentry_count >= max_reentries`: bust 時点で即 `busted` にして `rank` 確定

一時 bust の順位確定順を安定させるため、bust 発生時に `busted_at` と
`busted_sequence` を記録する。再エントリーせず受付終了したプレイヤーは、
受付終了時に以下の順で順位を確定する。

```
1. busted_sequence 昇順
2. 同一ハンド内の同時 bust は stack_at_hand_start 昇順
3. それでも同値なら current_table_id / current_seat_index の安定順
```

複数卓でほぼ同時に bust が発生した場合、全体順位は
`TournamentService.onHandFinished()` が DB transaction 内で `busted_sequence` を
採番した順に確定する。クライアント時刻や物理時刻には依存しない。

- 異なる卓の bust: 先に transaction で `busted_sequence` を取得した方を先に扱う
- 同じハンド内の複数 bust: `stack_at_hand_start` 昇順で同一ハンド内順位を決める
- 同値の場合: `current_table_id / current_seat_index` の安定順

`busted_sequence` は `tournaments.next_busted_sequence` から tournament 単位で採番する。
`onHandFinished()` では transaction 内で tournament 行を `FOR UPDATE` し、bust 人数分の
sequence を確保してから `tournament_players` を更新する。

```
1. SELECT tournaments ... FOR UPDATE
2. current = next_busted_sequence
3. bust 対象に current, current + 1, ... を割り当て
4. tournaments.next_busted_sequence = current + busted_count
```

`busted_reentry_available` 中に元の卓が卓割れで閉鎖されても問題なし。
再エントリー時は、その時点の active table の空席プールへ新規配置する。

#### 再エントリー時の座席配置

再エントリーはレイトレジストと同じ扱いで、現在の active table へ新規配置する。

- 候補テーブル: active table のうち最少人数の卓
- 候補席: その卓の空席。ただし SB と BTN の間は避ける
- 複数候補がある場合はランダム選択
- 配置後に `tournament_table_movements(reason='reentry')` を記録する
- 配置先の卓でハンド進行中の場合、既存の `sitting_out=true` を使い次ハンドから参加させる

再エントリー配置後に人数差が広がった場合は、次ハンド開始前の
テーブルバランス / 卓割れ処理で調整する。

受付終了判定はハンド開始前に実行する。

ハンド開始前の処理順:

```
1. レベルアップ判定
2. reentry_level_limit を超えた busted_reentry_available を busted に確定
3. テーブルバランス / 卓割れ
4. startHand
```

#### 再エントリー時のプレイヤー行

再エントリーは `tournament_players` を 1ユーザー1行のまま扱う。

- `UNIQUE (tournament_id, user_id)` は維持する
- 再エントリー時は同じ行を `active` に戻す
- `stack = starting_stack`
- `current_table_id/current_seat_index` は新しい配置先で更新する
- `rank` は最終 bust 時だけ確定する
- `busted_reentry_available` の間は `rank = NULL`
- 再エントリー前の一時的な bust は最終順位としては残さない
- 再エントリー時は `busted_at` / `busted_sequence` を NULL に戻す
- エントリー費・再エントリー費の履歴は `wallet_ledger`
- 席移動履歴は `tournament_table_movements(reason='reentry')`

再エントリー回数が v2 初期では 1回までのため、1ユーザー1行で十分。
将来、複数エントリーの厳密なハンド履歴や entry ticket 単位の分析が必要になったら、
`tournament_entries` の追加を検討する。

---

### [決定] Q11: トーナメント終了条件

再エントリー受付中は、active が1人になっても即終了しない。
`busted_reentry_available` が残っている場合は、再エントリー権を優先して終了を保留する。

終了条件:

```
再エントリー受付中:
  active == 1 かつ busted_reentry_available == 0
    → finished

再エントリー受付終了後:
  active == 1
    → finished
```

終了時処理:

```
1. 最後の active player に rank = 1 を設定
2. tournament.status = finished
3. tournament_tables.status = finished
4. 未確定の busted_reentry_available が残っていれば busted に確定
5. 賞金配分を実行
6. standings / result を公開
```

再エントリー受付期間の終了処理で active が1人になった場合も、その same transaction 内で
終了判定と賞金配分まで進める。

---

### [決定] Q12: 賞金配分と端数処理

賞金原資は通常エントリー費と再エントリー費の合計とする。

```
prize_pool = TOURNAMENT_ENTRY の合計 + TOURNAMENT_REENTRY の合計
```

各順位の賞金は `floor(prize_pool * pct / 100)` で計算する。
割り切れずに余りが出た場合は、上位順位から1ずつ配る。

例:

```
prize_pool = 1001
1位 50%, 2位 30%, 3位 20%

floor:
  1位 500
  2位 300
  3位 200
余り 1

最終:
  1位 501
  2位 300
  3位 200
```

`prize_structure` バリデーション:

- `pct` 合計は 100
- `rank` は 1 からの連番
- 入賞 rank 数は `max_players` 以下
- `pct` は正の整数

賞金支払いは `wallet_ledger.reason = TOURNAMENT_PRIZE` で記録する。
同じ tournament の賞金支払いは idempotency key を固定し、二重支払いを防ぐ。

---

### [決定] Q13: レイトレジスト

v2 初期ではレイトレジストは実装しない。

```
tournament.status = registering:
  新規 register 可

tournament.status = running:
  新規 register 不可
  再エントリー条件を満たす busted_reentry_available のみ /reentry 可
```

理由:

- 再エントリーだけでも seating / prize pool / standings の分岐が多い
- start 後の新規参加を許すと、受付終了・卓割れ・人数表示の条件がさらに増える
- v2 ではトーナメント開始前に参加者を確定させる方が実装・運用とも安定する

将来レイトレジストを入れる場合は、`late_register_level_limit` を追加し、
再エントリーと同じく最少人数卓の空席へ配置する。

---

### [決定] Q14: DB正本と既存 TableStore / GameState の同期

MTT の正本は `tournament_players` とする。
既存の `TableStore` は表示・実行時キャッシュ、`GameState.players` はハンド中の卓内状態として扱う。

責務:

```
ハンド中:
  GameState.players が卓内 stack / contributed / folded / all_in の一時正本

ハンド終了時:
  GameState.players.stack -> tournament_players.stack へ書き戻す

ハンド開始前:
  tournament_players.current_table_id/current_seat_index/stack から
  GameState.players と TableStore.tableMembers を同期する

卓移動時:
  tournament_players の current_table_id/current_seat_index を先に更新
  GameState / TableStore への反映は次ハンド開始前同期に寄せる
```

`TournamentTableSyncService.syncTable(tableId)` を追加する。

```
1. tournament_tables から tournament_id / table_id を取得
2. tournament_players(status='active', current_table_id=tableId) を seat_index 順で取得
3. TableStore.tableMembers を再構築
4. GameState.players の seat stack / player id / auto_fold 状態を同期
5. closed table の TableStore 表示を停止
```

アプリ再起動後も `tournament_tables` と `tournament_players` を正本に
`TableStore` を復元できるようにする。

---

### [決定] Q15: 卓割れ・バランス・同期時のロック順

複数卓をまたぐ処理で deadlock を避けるため、DB row lock の順序を固定する。

ロック順:

```
1. tournaments row
2. tournament_tables rows: table_id 昇順
3. games rows: game_id 昇順
4. tournament_players rows: user_id 昇順
```

対象処理:

- 初期配置
- 再エントリー配置
- 卓割れ
- テーブルバランス
- ハンド開始前同期
- ハンド終了時の stack 書き戻し / bust 判定

同一 tournament 内で seating 系処理が並列に走らないよう、最初に必ず
`SELECT ... FROM tournaments WHERE id = ? FOR UPDATE` を実行する。

自動開始 worker が複数卓を拾った場合も、transaction は tournament 単位に分ける。
1 transaction で複数 tournament を同時に処理しない。

---

### [決定] Q16: フロントエンド導線

v2 フロントエンドは MTT 前提で以下を実装する。

```
Tournament List:
  登録中 / 進行中 / 終了済み一覧
  自分が参加中の tournament への復帰導線

Tournament Lobby:
  登録
  参加者一覧
  テーブル一覧
  ブラインドスケジュール
  prize structure
  host start

My Tournament Status:
  自分の table / seat
  自分の rank / status
  再エントリー可否
  移動通知

Game Screen Additions:
  現在 level
  next level countdown
  action timer
  average stack
  active players count
  table number
  auto-fold toggle

Standings:
  全体順位
  active / busted_reentry_available / busted
  prize chips
```

テーブル移動はモーダルまたはトーストで強制通知し、新しい卓へ遷移するボタンを出す。
ゲーム画面だけに閉じず、ロビーと standings を tournament の正規画面として扱う。

---

## 残存する疑問点

なし。すべて解決済み。

---

## 確定スキーマ案（現時点）

`docs/features/tournament.md` から変更・追加が必要な点:

```sql
-- tournaments テーブルの変更点
-- 削除: level_type, hands_in_level
-- 追加: next_level_at, level_duration_seconds, max_players_per_table, next_busted_sequence
-- level_duration_seconds を blind_schedule JSON の外に出す
-- table_id は単一テーブル前提になるため不採用
-- games テーブルには action_deadline_at を追加する

ALTER TABLE games
ADD COLUMN action_deadline_at TIMESTAMP;

CREATE TABLE tournaments (
  id                    VARCHAR(128) PRIMARY KEY,
  name                  VARCHAR(255) NOT NULL,
  host_user_id          BIGINT       NOT NULL,
  status                VARCHAR(32)  NOT NULL DEFAULT 'registering',
  entry_fee             INT          NOT NULL,
  starting_stack        INT          NOT NULL,
  max_players           INT          NOT NULL,
  max_players_per_table INT          NOT NULL DEFAULT 9,
  prize_structure       JSONB        NOT NULL,  -- [{ "rank": 1, "pct": 70 }, ...]
  blind_schedule        JSONB        NOT NULL,  -- [{ "level": 1, "sb": 50, "bb": 100, "ante": 0 }, ...]
  level_duration_seconds INT         NOT NULL DEFAULT 480,
  allow_reentry         BOOLEAN      NOT NULL DEFAULT FALSE,
  reentry_level_limit   INT,
  max_reentries         INT          NOT NULL DEFAULT 1,
  current_level         INT          NOT NULL DEFAULT 1,
  next_level_at         TIMESTAMP,              -- トーナメント開始時にセット
  next_busted_sequence  BIGINT       NOT NULL DEFAULT 1,
  created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_tournaments_host FOREIGN KEY (host_user_id) REFERENCES users(id)
);

-- tournament_tables テーブル
-- tournament と既存 tables/game を紐づける。MTT では1 tournament に複数 table。

CREATE TABLE tournament_tables (
  id                    BIGSERIAL    PRIMARY KEY,
  tournament_id         VARCHAR(128) NOT NULL,
  table_id              VARCHAR(128) NOT NULL,
  table_number          INT          NOT NULL,
  status                VARCHAR(32)  NOT NULL DEFAULT 'active', -- active / paused / breaking / closed / finished
  break_order           INT          NOT NULL,
  next_hand_at          TIMESTAMP,
  created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  closed_at             TIMESTAMP,
  UNIQUE (tournament_id, table_number),
  UNIQUE (tournament_id, table_id),
  UNIQUE (tournament_id, break_order),
  CONSTRAINT fk_tt_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE,
  CONSTRAINT fk_tt_table      FOREIGN KEY (table_id) REFERENCES tables(id)
);

-- tournament_players テーブルの変更点
-- 追加: current_table_id, current_seat_index, stack_at_hand_start, busted_at, busted_sequence,
--      auto_fold, consecutive_timeouts

CREATE TABLE tournament_players (
  id                    BIGSERIAL    PRIMARY KEY,
  tournament_id         VARCHAR(128) NOT NULL,
  user_id               BIGINT       NOT NULL,
  current_table_id      VARCHAR(128),
  current_seat_index    INT,
  stack                 INT          NOT NULL,
  stack_at_hand_start   INT,                    -- 同時敗退の順位決定用
  status                VARCHAR(32)  NOT NULL DEFAULT 'active',
  rank                  INT,
  busted_at             TIMESTAMP,
  busted_sequence       BIGINT,
  reentry_count         INT          NOT NULL DEFAULT 0,
  auto_fold             BOOLEAN      NOT NULL DEFAULT FALSE,
  consecutive_timeouts  INT          NOT NULL DEFAULT 0,
  prize_chips           INT          NOT NULL DEFAULT 0,
  created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (tournament_id, user_id),
  UNIQUE (tournament_id, current_table_id, current_seat_index),
  CONSTRAINT fk_tp_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE,
  CONSTRAINT fk_tp_user       FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_tp_table      FOREIGN KEY (current_table_id) REFERENCES tables(id)
);

-- tournament_table_movements テーブル
-- 初期配置、通常バランス、卓割れ、ファイナル再抽選の履歴。

CREATE TABLE tournament_table_movements (
  id                    BIGSERIAL    PRIMARY KEY,
  tournament_id         VARCHAR(128) NOT NULL,
  user_id               BIGINT       NOT NULL,
  from_table_id         VARCHAR(128),
  from_seat_index       INT,
  to_table_id           VARCHAR(128) NOT NULL,
  to_seat_index         INT          NOT NULL,
  reason                VARCHAR(32)  NOT NULL, -- initial / balance / break / final_redraw / reentry
  created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ttm_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE,
  CONSTRAINT fk_ttm_user       FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_ttm_from_table FOREIGN KEY (from_table_id) REFERENCES tables(id),
  CONSTRAINT fk_ttm_to_table   FOREIGN KEY (to_table_id) REFERENCES tables(id)
);
```

---

## 実装順（決定事項を反映した最新版）

```
Phase 1: バックエンド基盤
  1. Flyway V18: wallet_ledger_reason ENUM 拡張 (TOURNAMENT_ENTRY/PRIZE/REENTRY)
  2. Flyway V19: tournaments + tournament_tables + tournament_players + tournament_table_movements
  3. TournamentRepository (port + PostgresImpl)
  4. TournamentService: 作成・エントリー
  5. TournamentSeatingService: 初期配置・必要卓数計算・table 自動生成
  6. TournamentService.start(): MTT 初期配置 + 各卓の GameState 初期化
  7. TournamentService.onHandFinished(): スタック書き戻し・敗退判定・順位確定
  8. 賞金配分 (wallet 連携)
  9. HTTP endpoints (7本)

Phase 2: ブラインドスケジュール
  10. BlindLevel 値オブジェクト + BlindSchedule のデシリアライズ
  11. GameLifecycleService.startHand(id, bigBlind, ante) オーバーロード追加
  12. TournamentService: ハンド開始前レベルアップチェック + ante/bigBlind 更新

Phase 3: 自動進行
  13. tournament_tables.next_hand_at による次ハンド自動開始
  14. games.action_deadline_at によるアクションタイムアウト
  15. timeout 時の check/fold 自動適用

Phase 4: MTT 運営
  16. TournamentSeatingService.balanceTables(): 最多卓の次回BB予定者を最少卓へ移動
  17. TournamentSeatingService.breakTable(): break_order に従って卓割れ
  18. ショートテーブル停止: 最多卓より3人以上少ない卓は次ハンド開始不可

Phase 5: 再エントリー
  19. TournamentService.requestReentry(): busted_reentry_available → active
  20. TournamentSeatingService.assignReentrySeat(): 最少人数卓の空席へランダム配置
  21. POST /v1/tournaments/{id}/reentry エンドポイント

Phase 6: フロントエンド
  22. トーナメントロビー（作成・エントリー・standings・テーブル一覧）
  23. ゲーム画面へのブラインド情報追加（レベル・残り時間・平均スタック）
  24. アクションタイマー表示
  25. テーブル移動通知（balance / break）
  26. 再エントリー導線
  27. 敗退・賞金モーダル

Phase 7: 品質・後回し
  28. ヘッズアップ特殊ルール（GameState 拡張）[Q3 次第]
  29. プリセット (TURBO / STANDARD / DEEP)
  30. ファイナルテーブル再抽選設定
  31. テスト
```

---

## 参考リンク

- `docs/features/tournament.md` — 機能要件
- `docs/designs/db_schema.md` — 現在の DB スキーマ
- `docs/designs/GAME_FLOW.md` — ゲームステートマシン
