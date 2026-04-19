# テキサスホールデム ルール仕様（Java サービス向け）

この文書は、Go 実装から Java へ移植する際にも維持するテキサスホールデムのルールをまとめたものです。

対象範囲:
- ノーリミットのリングゲーム
- 2-9 人対戦
- 固定ブラインド
- アンティなし
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
- 最初のアクションは BB の左隣から始まる
- BB はすでにベット済みなので、続行にはその額への call が必要

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
  - 直前の raise サイズ
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

## 13. 役の強さ（強い順）
1. Straight Flush
2. Four of a Kind
3. Full House
4. Flush
5. Straight
6. Three of a Kind
7. Two Pair
8. One Pair
9. High Card
