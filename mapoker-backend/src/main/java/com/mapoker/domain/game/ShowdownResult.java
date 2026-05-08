package com.mapoker.domain.game;

import com.mapoker.domain.hand.HandValue;
import java.util.List;

/**
 * ショーダウン結果を表すレコードです。
 *
 * @param winnerIndexes 勝者の席番号一覧
 * @param bestHand 勝利ハンドの内容
 * @param payouts 各勝者への配当一覧
 */
public record ShowdownResult(List<Integer> winnerIndexes, HandValue bestHand, List<Integer> payouts) {}
