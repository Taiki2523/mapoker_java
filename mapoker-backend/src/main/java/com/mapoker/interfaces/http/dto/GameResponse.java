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

/**
 * ゲーム状態レスポンスです。
 *
 * @param id ゲーム ID
 * @param status ゲーム状態
 * @param street 現在ストリート
 * @param buttonIndex ボタン位置
 * @param smallBlindIdx スモールブラインド位置
 * @param bigBlindIdx ビッグブラインド位置
 * @param currentPlayer 現在アクション中のプレイヤー位置
 * @param currentBet 現在ベット額
 * @param lastRaiseSize 直近レイズ幅
 * @param bigBlind ビッグブラインド額
 * @param potTotal ポット総額
 * @param players プレイヤー一覧
 * @param community コミュニティカード
 * @param oddChipRule 端数チップルール
 * @param canStartHand ハンド開始可否
 * @param viewerMembershipActive 閲覧者の参加状態
 * @param canRebuy リバイ可否
 * @param lastShowdown 直近ショーダウン結果
 */
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
    /**
     * プレイヤー表示 DTO です。
     *
     * @param id プレイヤー ID
     * @param stack 現在スタック
     * @param contributed 現ストリート拠出額
     * @param folded フォールド済みかどうか
     * @param allIn オールイン状態かどうか
     * @param hole ホールカード
     */
    public record PlayerResponse(
            String id,
            int stack,
            int contributed,
            boolean folded,
            @JsonProperty("all_in") boolean allIn,
            List<Card> hole
    ) {}

    /**
     * ショーダウン表示 DTO です。
     *
     * @param winners 勝者一覧
     * @param bestHand 最良ハンド
     * @param payouts 配当一覧
     */
    public record ShowdownDto(
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
    }

    /**
     * ドメイン状態からゲームレスポンスを生成します。
     *
     * @param g           ゲーム状態
     * @param viewerIndex 閲覧者の席番号
     * @param spectator   観戦者かどうか
     * @param seatedCount テーブルに着席中の実プレイヤー数（-1 の場合はロスター未考慮）
     * @return 生成したゲームレスポンス
     */
    /** ロスター未考慮の旧シグネチャ（後方互換）。 */
    public static GameResponse from(GameState g, Integer viewerIndex, boolean spectator) {
        return from(g, viewerIndex, spectator, -1);
    }

    public static GameResponse from(GameState g, Integer viewerIndex, boolean spectator, int seatedCount) {
        List<PlayerResponse> playerResponses = new ArrayList<>();
        boolean showAll = g.getStatus() == GameStatus.SHOWDOWN
                || (g.getStatus() == GameStatus.FINISHED && !g.isFoldWin());

        for (int i = 0; i < g.getPlayers().size(); i++) {
            Player p = g.getPlayers().get(i);
            List<Card> hole = null;
            boolean showThisPlayer = showAll
                    || (p.isAllIn() && !p.isFolded());
            if (showThisPlayer && p.getHole() != null && p.getHole()[0] != null) {
                hole = Arrays.asList(p.getHole());
            } else if (!spectator && viewerIndex != null && viewerIndex == i) {
                hole = p.getHole() != null ? Arrays.asList(p.getHole()) : List.of();
            }
            playerResponses.add(new PlayerResponse(
                    p.getId(), p.getStack(), p.getContributed(),
                    p.isFolded(), p.isAllIn(), hole));
        }

        boolean canStartHand = g.canStartHand()
                && (seatedCount < 0 || seatedCount >= com.mapoker.domain.PokerConstants.MIN_PLAYERS);
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
