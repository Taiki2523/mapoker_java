package com.mapoker.domain.game;

import com.mapoker.domain.rules.Action;
import com.mapoker.domain.rules.ActionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * オフラインモード向け GameState 機能のテスト。
 * startHand(skipCardDeal=true) と resolveShowdownManual() を検証する。
 */
class GameStateOfflineTest {

    private static GameState game3P() {
        return GameState.newGame(
                List.of(new Player("p0", 500), new Player("p1", 500), new Player("p2", 500)),
                2, 10, new Random(1), OddChipRule.LOW_INDEX);
    }

    // ---- startHand(skipCardDeal=true) ----

    @Test
    void startHandSkipCardDeal_doesNotDealHoleCards() {
        GameState g = game3P();
        g.startHand(10, false, true);

        assertThat(g.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
        for (Player p : g.getPlayers()) {
            if (!p.isFolded()) {
                assertThat(p.getHole()).allSatisfy(card -> assertThat(card).isNull());
            }
        }
    }

    @Test
    void startHandSkipCardDeal_communityIsEmpty() {
        GameState g = game3P();
        g.startHand(10, false, true);
        assertThat(g.getCommunity()).isEmpty();
    }

    @Test
    void startHandSkipCardDeal_blindsAndPotAreSet() {
        GameState g = game3P();
        g.startHand(10, false, true);

        assertThat(g.getPot()).isGreaterThan(0);
        assertThat(g.getCurrentBet()).isEqualTo(10);
    }

    @Test
    void startHandNormal_isUnchanged() {
        GameState g = game3P();
        g.startHand(10, false, false);

        assertThat(g.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
        long dealtPlayers = g.getPlayers().stream()
                .filter(p -> !p.isFolded())
                .filter(p -> p.getHole()[0] != null && p.getHole()[1] != null)
                .count();
        assertThat(dealtPlayers).isEqualTo(3);
    }

    // ---- resolveShowdownManual() ----

    @Test
    void resolveShowdownManual_singleWinner_receivesEntirePot() {
        GameState g = game3P();
        g.startHand(10, false, true);
        advanceToShowdown(g);

        int potBefore = g.getPot();
        ShowdownResult result = g.resolveShowdownManual(List.of(0));

        assertThat(result.winnerIndexes()).containsExactly(0);
        assertThat(result.bestHand()).isNull();
        assertThat(result.payouts().get(0)).isEqualTo(potBefore);
    }

    @Test
    void resolveShowdownManual_twoWinners_splitsPotEvenly() {
        GameState g = game3P();
        g.startHand(10, false, true);
        advanceToShowdown(g);

        int potBefore = g.getPot();
        ShowdownResult result = g.resolveShowdownManual(List.of(0, 1));

        assertThat(result.winnerIndexes()).containsExactlyInAnyOrder(0, 1);
        assertThat(result.payouts().get(0) + result.payouts().get(1)).isEqualTo(potBefore);
    }

    @Test
    void resolveShowdownManual_statusMustBeShowdown() {
        GameState g = game3P();
        g.startHand(10, false, true);

        assertThatThrownBy(() -> g.resolveShowdownManual(List.of(0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("showdown not available");
    }

    @Test
    void resolveShowdownManual_emptyWinnersThrows() {
        GameState g = game3P();
        g.startHand(10, false, true);
        advanceToShowdown(g);

        assertThatThrownBy(() -> g.resolveShowdownManual(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolveShowdownManual_setsLastShowdown() {
        GameState g = game3P();
        g.startHand(10, false, true);
        advanceToShowdown(g);

        g.resolveShowdownManual(List.of(0));
        assertThat(g.getLastShowdown()).isNotNull();
        assertThat(g.getLastShowdown().winnerIndexes()).containsExactly(0);
    }

    private static void advanceToShowdown(GameState g) {
        int safety = 100;
        while (g.getStatus() == GameStatus.IN_PROGRESS && safety-- > 0) {
            int pi = g.getCurrentPlayer();
            Player p = g.getPlayers().get(pi);
            Action action = p.getContributed() < g.getCurrentBet()
                    ? Action.of(ActionType.CALL, 0)
                    : Action.of(ActionType.CHECK, 0);
            g.applyAction(pi, action);
        }
    }
}
