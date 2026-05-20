package com.mapoker.domain.game;

import com.mapoker.domain.rules.Action;
import com.mapoker.domain.rules.ActionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ベッティングラウンドの終了条件・raiseOpen・ストリート進行のテスト。
 *
 * <p>straddle / ante 追加時にベッティングフローが壊れていないことを確認する基準テスト。
 */
class GameStateBettingTest {

    private static GameState game2P(int s0, int s1) {
        return GameState.newGame(
                List.of(new Player("p0", s0), new Player("p1", s1)),
                0, 10, new Random(1), OddChipRule.LOW_INDEX);
    }

    private static GameState game3P() {
        return GameState.newGame(
                List.of(new Player("p0", 100), new Player("p1", 100), new Player("p2", 100)),
                0, 10, new Random(1), OddChipRule.LOW_INDEX);
    }

    // -----------------------------------------------------------------------
    // ストリート進行
    // -----------------------------------------------------------------------

    @Test
    void preflopCallAndCheckAdvancesToFlop() {
        GameState g = game2P(100, 100);
        g.startHand(10);

        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CALL, 0));
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CHECK, 0));

        assertThat(g.getStreet()).isEqualTo(com.mapoker.domain.rules.Street.FLOP);
        assertThat(g.getCommunity()).hasSize(3);
    }

    @Test
    void flopTurnRiverSequenceReachesShowdown() {
        GameState g = game2P(100, 100);
        g.startHand(10);

        // preflop
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CALL, 0));
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CHECK, 0));
        // flop
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CHECK, 0));
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CHECK, 0));
        // turn
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CHECK, 0));
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CHECK, 0));
        // river
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CHECK, 0));
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CHECK, 0));

        assertThat(g.getStatus()).isEqualTo(GameStatus.SHOWDOWN);
        assertThat(g.getCommunity()).hasSize(5);
    }

    @Test
    void communityHasFiveCardsAtShowdown() {
        GameState g = game2P(100, 100);
        g.startHand(10);

        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CALL, 0));
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CHECK, 0));

        while (g.getStatus() == GameStatus.IN_PROGRESS) {
            g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CHECK, 0));
        }

        assertThat(g.getStatus()).isEqualTo(GameStatus.SHOWDOWN);
        assertThat(g.getCommunity()).hasSize(5);
    }

    // -----------------------------------------------------------------------
    // raiseOpen：サブミン all-in はベッティングを再オープンしない
    // -----------------------------------------------------------------------

    @Test
    void subMinAllInDoesNotReopenBetting() {
        // button=2 → startHand 後: button=p0, SB=p1, BB=p2(25chips → 残り15), UTG=p0
        // p0 が raise20、p1 が fold、p2 が all-in 25（currentBet=20 に対し raise+5 < minRaise10）
        // → raiseOpen=false → p0 は再レイズ不可
        GameState g = GameState.newGame(
                List.of(new Player("p0", 100), new Player("p1", 100), new Player("p2", 25)),
                2, 10, new Random(1), OddChipRule.LOW_INDEX);
        g.startHand(10);

        // プリフロップ: UTG(p0) raise to 20
        int cur = g.getCurrentPlayer(); // p0
        g.applyAction(cur, Action.of(ActionType.RAISE, 20));

        // p1(SB) fold
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.FOLD, 0));

        // p2(BB, 残り15) all-in → total 25、currentBet=20 に対しサブミン raise (+5 < minRaise 10)
        int p2 = g.getCurrentPlayer();
        g.applyAction(p2, Action.of(ActionType.ALL_IN, 0));

        // p0 はレイズ不可（raiseOpen=false）→ raise が弾かれることを確認
        int p0Again = g.getCurrentPlayer();
        assertThatThrownBy(() ->
                g.applyAction(p0Again, Action.of(ActionType.RAISE, 60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fullRaiseReopensAndLetsPreviousActorRaise() {
        GameState g = game3P();
        g.startHand(10);

        // p0(UTG) calls
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CALL, 0));
        // p1(SB) raises to 30
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.RAISE, 30));
        // p2(BB) can re-raise because full raise occurred
        int p2 = g.getCurrentPlayer();
        // BB のレイズ（raise to 60）が通ることを確認
        g.applyAction(p2, Action.of(ActionType.RAISE, 60));

        assertThat(g.getCurrentBet()).isEqualTo(60);
    }

    // -----------------------------------------------------------------------
    // ベット額の境界
    // -----------------------------------------------------------------------

    @Test
    void betAmountAddsToPot() {
        GameState g = game2P(100, 100);
        g.startHand(10); // pot=15 (SB5 + BB10)

        // preflop: SBがcall → pot=20
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CALL, 0));
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CHECK, 0));

        // flop: bet 20
        int potBeforeBet = g.getPot();
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.BET, 20));

        assertThat(g.getPot()).isEqualTo(potBeforeBet + 20);
        assertThat(g.getCurrentBet()).isEqualTo(20);
    }

    @Test
    void raiseAmountIsRaiseTo() {
        GameState g = game2P(100, 100);
        g.startHand(10);

        // SB calls
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CALL, 0));
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CHECK, 0));

        // flop: p0 bet 20, p1 raise to 50
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.BET, 20));
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.RAISE, 50));

        assertThat(g.getCurrentBet()).isEqualTo(50);
    }

    // -----------------------------------------------------------------------
    // applyAction エラー系
    // -----------------------------------------------------------------------

    @Test
    void actionAfterShowdownThrows() {
        GameState g = game2P(100, 100);
        g.startHand(10);

        // チェックダウンしてショーダウンへ
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CALL, 0));
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CHECK, 0));
        while (g.getStatus() == GameStatus.IN_PROGRESS) {
            g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CHECK, 0));
        }

        assertThatThrownBy(() ->
                g.applyAction(0, Action.of(ActionType.CHECK, 0)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startHandDuringShowdownThrows() {
        GameState g = game2P(100, 100);
        g.startHand(10);

        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CALL, 0));
        g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CHECK, 0));
        while (g.getStatus() == GameStatus.IN_PROGRESS) {
            g.applyAction(g.getCurrentPlayer(), Action.of(ActionType.CHECK, 0));
        }

        assertThatThrownBy(() -> g.startHand(10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("showdown not resolved");
    }

    @Test
    void canStartHandReturnsFalseWithOneChipHolder() {
        // p1 のスタックを 0 にして canStartHand=false にする
        GameState g = GameState.newGame(
                List.of(new Player("p0", 100), new Player("p1", 0)),
                0, 10, new Random(1), OddChipRule.LOW_INDEX);

        assertThat(g.canStartHand()).isFalse();
    }

    @Test
    void canStartHandReturnsTrueWithTwoChipHolders() {
        GameState g = game2P(100, 100);
        assertThat(g.canStartHand()).isTrue();
    }
}
