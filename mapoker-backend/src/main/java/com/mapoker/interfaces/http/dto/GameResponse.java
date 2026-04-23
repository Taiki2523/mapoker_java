package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.domain.card.Card;
import com.mapoker.domain.card.Rank;
import com.mapoker.domain.game.GameState;
import com.mapoker.domain.game.GameStatus;
import com.mapoker.domain.game.OddChipRule;
import com.mapoker.domain.game.Player;
import com.mapoker.domain.game.ShowdownResult;
import com.mapoker.domain.hand.HandRank;
import com.mapoker.domain.rules.Street;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record GameResponse(
        String id,
        GameStatus status,
        Street street,
        @JsonProperty("button_index") int buttonIndex,
        @JsonProperty("small_blind_idx") int smallBlindIdx,
        @JsonProperty("big_blind_idx") int bigBlindIdx,
        @JsonProperty("current_player") int currentPlayer,
        @JsonProperty("current_bet") int currentBet,
        @JsonProperty("last_raise_size") int lastRaiseSize,
        @JsonProperty("big_blind") int bigBlind,
        @JsonProperty("pot_total") int potTotal,
        List<PlayerResponse> players,
        List<Card> community,
        @JsonProperty("odd_chip_rule") OddChipRule oddChipRule,
        @JsonProperty("can_start_hand") boolean canStartHand,
        @JsonProperty("viewer_membership_active") boolean viewerMembershipActive,
        @JsonProperty("can_rebuy") boolean canRebuy,
        @JsonProperty("last_showdown") ShowdownDto lastShowdown
) {
    public record PlayerResponse(
            String id,
            int stack,
            int contributed,
            boolean folded,
            @JsonProperty("all_in") boolean allIn,
            List<Card> hole
    ) {}

    public record ShowdownDto(
            List<Integer> winners,
            @JsonProperty("best_hand") BestHandDto bestHand,
            List<Integer> payouts
    ) {
        public record BestHandDto(HandRank rank, List<Rank> kickers) {}
    }

    public static GameResponse from(GameState g, Integer viewerIndex, boolean spectator) {
        List<PlayerResponse> playerResponses = new ArrayList<>();
        boolean showAll = g.getStatus() == GameStatus.SHOWDOWN
                || (g.getStatus() == GameStatus.FINISHED && !g.isFoldWin());

        for (int i = 0; i < g.getPlayers().size(); i++) {
            Player p = g.getPlayers().get(i);
            List<Card> hole = null;
            if (showAll && p.getHole() != null && p.getHole()[0] != null) {
                hole = Arrays.asList(p.getHole());
            } else if (!spectator && viewerIndex != null && viewerIndex == i) {
                hole = p.getHole() != null ? Arrays.asList(p.getHole()) : List.of();
            }
            playerResponses.add(new PlayerResponse(
                    p.getId(), p.getStack(), p.getContributed(),
                    p.isFolded(), p.isAllIn(), hole));
        }

        boolean canStartHand = g.canStartHand();
        boolean viewerMembershipActive = false;
        boolean canRebuy = false;
        if (viewerIndex != null && viewerIndex >= 0 && viewerIndex < g.getPlayers().size()) {
            Player vp = g.getPlayers().get(viewerIndex);
            viewerMembershipActive = true;
            canRebuy = (vp.getStack() == 0);
        }

        ShowdownDto showdownDto = null;
        if (g.getLastShowdown() != null) {
            ShowdownResult sr = g.getLastShowdown();
            ShowdownDto.BestHandDto bestHandDto = g.isFoldWin()
                    ? null
                    : new ShowdownDto.BestHandDto(sr.bestHand().rank(), sr.bestHand().kickers());
            showdownDto = new ShowdownDto(
                    sr.winnerIndexes(),
                    bestHandDto,
                    sr.payouts());
        }

        return new GameResponse(
                g.getId(), g.getStatus(), g.getStreet(),
                g.getButtonIndex(), g.getSmallBlindIdx(), g.getBigBlindIdx(), g.getCurrentPlayer(),
                g.getCurrentBet(), g.getLastRaiseSize(), g.getBigBlindSize(),
                g.getPot(), playerResponses, g.getCommunity(), g.getOddChipRule(),
                canStartHand, viewerMembershipActive, canRebuy, showdownDto);
    }
}
