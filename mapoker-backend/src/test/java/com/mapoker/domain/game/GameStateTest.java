package com.mapoker.domain.game;

import com.mapoker.domain.rules.Action;
import com.mapoker.domain.rules.ActionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameStateTest {

    private static GameState newGame2P() {
        List<Player> players = List.of(new Player("p1", 100), new Player("p2", 100));
        return GameState.newGame(players, 0, 10, new Random(42), OddChipRule.LOW_INDEX);
    }

    @Test
    void newGameCreated() {
        GameState g = newGame2P();
        assertThat(g.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(g.getPlayers()).hasSize(2);
    }

    @Test
    void startHandPostsBlinds() {
        GameState g = newGame2P();
        g.startHand(10);
        // SB posts 5, BB posts 10 → pot = 15
        assertThat(g.getPot()).isEqualTo(15);
        assertThat(g.getCurrentBet()).isEqualTo(10);
    }

    @Test
    void foldEndsHand() {
        GameState g = newGame2P();
        g.startHand(10);
        int firstActor = g.getCurrentPlayer();
        g.applyAction(firstActor, Action.of(ActionType.FOLD, 0));
        assertThat(g.getStatus()).isEqualTo(GameStatus.FINISHED);
    }

    @Test
    void checkProgressesStreet() {
        GameState g = newGame2P();
        g.startHand(10);
        // 2P preflop: SB acts first
        // SB calls (or check if already equal), BB checks → move to flop
        int first = g.getCurrentPlayer();
        // SB needs to call 5 more to match BB's 10
        g.applyAction(first, Action.of(ActionType.CALL, 0));
        int second = g.getCurrentPlayer();
        g.applyAction(second, Action.of(ActionType.CHECK, 0));
        assertThat(g.getStreet().ordinal()).isGreaterThan(0); // moved past preflop
    }

    @Test
    void wrongPlayerThrows() {
        GameState g = newGame2P();
        g.startHand(10);
        int first = g.getCurrentPlayer();
        int other = first == 0 ? 1 : 0;
        assertThatThrownBy(() -> g.applyAction(other, Action.of(ActionType.FOLD, 0)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fullHandReachesShowdown() {
        GameState g = newGame2P();
        g.startHand(10);
        // Play until game ends: call if there's a bet, otherwise check
        int safety = 50;
        while (g.getStatus() == GameStatus.IN_PROGRESS && safety-- > 0) {
            int pi = g.getCurrentPlayer();
            Player p = g.getPlayers().get(pi);
            Action action = p.getContributed() < g.getCurrentBet()
                    ? Action.of(ActionType.CALL, 0)
                    : Action.of(ActionType.CHECK, 0);
            g.applyAction(pi, action);
        }
        assertThat(g.getStatus()).isIn(GameStatus.SHOWDOWN, GameStatus.FINISHED);
    }

    @Test
    void tooFewPlayersThrows() {
        assertThatThrownBy(() ->
                GameState.newGame(List.of(new Player("p1", 100)), 0, 10, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
