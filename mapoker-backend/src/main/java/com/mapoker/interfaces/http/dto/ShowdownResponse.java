package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.domain.game.ShowdownResult;
import com.mapoker.domain.hand.HandRank;
import com.mapoker.domain.card.Rank;

import java.util.List;

/**
 * ショーダウン結果レスポンスです。
 *
 * @param winners 勝者一覧
 * @param bestHand 最良ハンド
 * @param payouts 配当一覧
 */
public record ShowdownResponse(
        List<Integer> winners,
        @JsonProperty("best_hand") BestHandDto bestHand,
        List<Integer> payouts
) {
    /**
     * 最良ハンド表示 DTO です。
     *
     * @param rank 役
     * @param kickers キッカー一覧
     */
    public record BestHandDto(HandRank rank, List<Rank> kickers) {}

    /**
     * ドメイン結果からレスポンスを生成します。
     *
     * @param r ショーダウン結果
     * @return 生成したレスポンス
     */
    public static ShowdownResponse from(ShowdownResult r) {
        return new ShowdownResponse(
                r.winnerIndexes(),
                new BestHandDto(r.bestHand().rank(), r.bestHand().kickers()),
                r.payouts());
    }
}
