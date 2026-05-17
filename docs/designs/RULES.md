# テキサスホールデム ルール仕様（Java サービス向け）

この文書は、Go 実装から Java へ移植する際にも維持するテキサスホールデムのルールをまとめたものです。

対象範囲:
- ノーリミットのリングゲーム
- 2-9 人対戦
- 固定ブラインド
- 任意のアンティ（0 でアンティなし）
- サイドポットあり

## 1. 目的
- 次の組み合わせから最強の 5 枚役を作る
  - hole cards 0-2 枚
  - community cards 0-5 枚
- または、全員を fold させて pot を獲得する

## 2. プレイヤーとポジション
- 参加人数は 2-9 人
- ディーラーボタン（BTN）は各 hand ごとに時計回りに 1 つ進む
- Small Blind（SB）は BTN の左
- Big Blind（BB）は SB の左
- heads-up（2 人）の場合は BTN が SB を兼ね、もう 1 人が BB になる

## 3. ブラインド
- SB と BB は配札前に強制的に支払うベット
- ブラインド値は hand 開始時に呼び出し側が指定する

## 3.5 アンティ

- テーブル作成時に `ante` 額を設定する（0 でアンティなし）
- デフォルト値は `max(1, big_blind / 10)`（UI 側）
- 各 hand 開始時、SB/BB ポスト前に全プレイヤーが個別にアンティを支払う
- BB ante（BB が全員分をまとめて払う方式）は採用しない
- アンティはポットに追加されるが、現ストリートのベット額（`contributed`）には加算しない
  - コール額 = `currentBet - contributed`（アンティの影響なし）
  - サイドポット計算は `totalContrib`（アンティ込み）を使用
- スタックがアンティ額に満たないプレイヤーは持ち金全額をアンティとして支払いオールインとなる

## 4. ベッティングラウンド
各 hand は最大 4 回の betting round を持つ。
1. Preflop
2. Flop
3. Turn
4. River

## 5. 配札とボード
- 各アクティブプレイヤーに face-down の hole cards を 2 枚配る
- community cards は face-up で公開する
  - Flop: 3 枚
  - Turn: 1 枚
  - River: 1 枚
- 各 community 公開の前に burn card を 1 枚捨てる

## 6. アクション順
### プリフロップ
- 最初のアクションは UTG（BB の左）から始まり、button → SB → BB の順で、BB が最後に行動する
- BB のオプション: プリフロップはブラインドですでにベットが入っているため、BB は `bet` ではなく `check`（誰もレイズしていない場合）または `raise`（レイズする場合）を選択する。`bet` アクションはプリフロップでは BB に許可されない
- heads-up では button=SB が先手、BB が後手

### ポストフロップ
- 最初のアクションは BTN の左側にいる、最初のアクティブプレイヤーから始まる

## 7. プレイヤーのアクション
手番のプレイヤーが hand に残っていて、かつ all-in でない場合、次の行動ができる。
- Fold: hand を諦める
- Check: call が不要な場合にパスする
- Call: 現在のベット額に合わせる
- Bet: その round の最初の賭けを行う
- Raise: 現在のベット額を引き上げる
- All-in: 残りチップをすべて賭ける

## 8. amount の意味
- `bet` の `amount` は、その action 後にそのプレイヤーがその street でいくら出しているかの合計額
- `raise` の `amount` は raise-to の合計額
- `call` の `amount = 0` は、自動的に妥当な call 額へ合わせる指定として扱ってよい

## 9. No-Limit のベッティングルール
- 最小レイズ額は次の大きい方
  - big blind
  - 現在の current_bet（`max(bigBlind, currentBet)`）
- 最小レイズ未満の all-in は許可する
- 最小レイズ未満の all-in は、すでに行動済みプレイヤーに対して betting を reopen しない

## 10. ベッティングラウンド終了条件
次の条件を満たしたら、その betting round は終了する。
- アクティブな全プレイヤーが current bet に追いついている、または all-in である
- 最後の full raise の後、行動可能な全プレイヤーに応答機会がある

他の全員が fold して 1 人だけ残った場合、その時点で hand は終了し、そのプレイヤーが pot を獲得する。

## 11. ショーダウン
river の betting round 後に 2 人以上残っている場合:
- 残っているプレイヤーは hole cards を公開する
- 各プレイヤーは 7 枚（hole 2 + community 5）で評価する
- 最強の hand が該当 pot を獲得する
- 同着ならその pot を分配する

## 12. Pot の解決
- main pot と side pot は、各プレイヤーの hand 全体の contribution から導出する
- fold したプレイヤーは pot を獲得できないが、出したチップは pot に残る
- 端数チップは設定された odd-chip rule に従って配分する

## 13. リバイ（チップ補充）

- スタックが 0 になったプレイヤーは自動退席せず、テーブルに残留する
- ハンド終了後にリバイ画面が表示され、プレイヤーは再度バイインできる
- リバイ後はスタックが補充され、次のハンドから通常参加する
- リバイせずにロビーに戻ることも可能（退席＝キャッシュアウト）
- スタック 0 のまま次のハンドが開始された場合、そのプレイヤーは `folded=true` として扱われ参加できない
- リバイ金額の範囲はテーブルの `min_buy_in` / `max_buy_in` に従う

## 14. 役の強さ（強い順）
1. Straight Flush
2. Four of a Kind
3. Full House
4. Flush
5. Straight
6. Three of a Kind
7. Two Pair
8. One Pair
9. High Card
