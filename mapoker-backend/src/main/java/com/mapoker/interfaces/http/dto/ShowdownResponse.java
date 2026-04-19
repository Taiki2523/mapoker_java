package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.domain.game.ShowdownResult;
import com.mapoker.domain.hand.HandRank;
import com.mapoker.domain.card.Rank;

import java.util.List;

public record ShowdownResponse(
        List<Integer> winners,
        @JsonProperty("best_hand") BestHandDto bestHand,
        List<Integer> payouts
) {
    public record BestHandDto(HandRank rank, List<Rank> kickers) {}

    public static ShowdownResponse from(ShowdownResult r) {
        return new ShowdownResponse(
                r.winnerIndexes(),
                new BestHandDto(r.bestHand().rank(), r.bestHand().kickers()),
                r.payouts());
    }
}
