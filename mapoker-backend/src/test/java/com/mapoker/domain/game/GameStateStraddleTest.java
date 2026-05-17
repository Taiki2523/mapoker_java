package com.mapoker.domain.game;

import com.mapoker.domain.rules.Action;
import com.mapoker.domain.rules.ActionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateStraddleTest {

    private static final int BIG_BLIND = 200;

    /** straddle_enabled=true の6人テーブルを作成する */
    private static GameState newGame6P(boolean straddleEnabled) {
        List<Player> players = List.of(
                new Player("p0", 10000),
                new Player("p1", 10000),
                new Player("p2", 10000),
                new Player("p3", 10000),
                new Player("p4", 10000),
                new Player("p5", 10000)
        );
        // buttonIndex=5 → startHand後 button=p0, SB=p1, BB=p2, UTG=p3
        return GameState.newGame(players, 5, BIG_BLIND, new Random(42), OddChipRule.LOW_INDEX, 0, straddleEnabled);
    }

    /** straddle_enabled=true の3人テーブルを作成する */
    private static GameState newGame3P(boolean straddleEnabled) {
        List<Player> players = List.of(
                new Player("p0", 10000),
                new Player("p1", 10000),
                new Player("p2", 10000)
        );
        // buttonIndex=2 → startHand後 button=p0, SB=p1, BB=p2, UTG=p0
        return GameState.newGame(players, 2, BIG_BLIND, new Random(42), OddChipRule.LOW_INDEX, 0, straddleEnabled);
    }

    @Test
    void straddle_postsDoubleBigBlind() {
        GameState g = newGame6P(true);
        g.startHand(BIG_BLIND, true);

        // SB=100, BB=200, straddle=400 → pot=700
        assertThat(g.getPot()).isEqualTo(100 + 200 + 400);
        assertThat(g.getCurrentBet()).isEqualTo(400);
        assertThat(g.getLastRaiseSize()).isEqualTo(400);
    }

    @Test
    void straddle_straddleIdxIsUtg() {
        GameState g = newGame6P(true);
        g.startHand(BIG_BLIND, true);

        // button=p0, SB=p1, BB=p2, UTG=p3 がstraddle
        assertThat(g.getStraddleIdx()).isEqualTo(3);
    }

    @Test
    void straddle_firstToActIsUtgPlusOne() {
        GameState g = newGame6P(true);
        g.startHand(BIG_BLIND, true);

        // UTG=p3がstraddle → 最初のアクターはp4
        assertThat(g.getCurrentPlayer()).isEqualTo(4);
    }

    @Test
    void straddle_callAmountIsStraddleAmount() {
        GameState g = newGame6P(true);
        g.startHand(BIG_BLIND, true);

        // p4のcontributed=0, currentBet=400 → コール額は400
        Player p4 = g.getPlayers().get(4);
        assertThat(g.getCurrentBet() - p4.getContributed()).isEqualTo(400);
    }

    @Test
    void straddle_bbCallAmountIsDifference() {
        GameState g = newGame6P(true);
        g.startHand(BIG_BLIND, true);

        // BB=p2のcontributed=200, currentBet=400 → コール額は200
        Player bb = g.getPlayers().get(2);
        assertThat(g.getCurrentBet() - bb.getContributed()).isEqualTo(200);
    }

    @Test
    void straddle_straddlerActsLast() {
        GameState g = newGame6P(true);
        g.startHand(BIG_BLIND, true);

        // p4,p5,p0(btn),p1(sb),p2(bb) が全員コールしたあと、p3(straddle)にチェック権が回る
        // p4 call
        g.applyAction(4, Action.of(ActionType.CALL, 0));
        // p5 call
        g.applyAction(5, Action.of(ActionType.CALL, 0));
        // p0 call
        g.applyAction(0, Action.of(ActionType.CALL, 0));
        // p1(SB) call
        g.applyAction(1, Action.of(ActionType.CALL, 0));
        // p2(BB) call
        g.applyAction(2, Action.of(ActionType.CALL, 0));

        // ストラドルにアクションが回ってきている
        assertThat(g.getCurrentPlayer()).isEqualTo(3);
    }

    @Test
    void straddle_checkByStraddlerAdvancesStreet() {
        GameState g = newGame6P(true);
        g.startHand(BIG_BLIND, true);

        // 全員コール → ストラドルがチェック → FLOPへ
        g.applyAction(4, Action.of(ActionType.CALL, 0));
        g.applyAction(5, Action.of(ActionType.CALL, 0));
        g.applyAction(0, Action.of(ActionType.CALL, 0));
        g.applyAction(1, Action.of(ActionType.CALL, 0));
        g.applyAction(2, Action.of(ActionType.CALL, 0));
        g.applyAction(3, Action.of(ActionType.CHECK, 0));

        assertThat(g.getCommunity()).hasSize(3);
    }

    @Test
    void straddle_noStraddle_behavesNormally() {
        GameState g = newGame6P(true);
        g.startHand(BIG_BLIND, false);

        // ストラドルなし: pot=300, currentBet=200, straddleIdx=-1
        assertThat(g.getPot()).isEqualTo(300);
        assertThat(g.getCurrentBet()).isEqualTo(200);
        assertThat(g.getStraddleIdx()).isEqualTo(-1);
        // 最初のアクターはUTG=p3
        assertThat(g.getCurrentPlayer()).isEqualTo(3);
    }

    @Test
    void straddle_disabledTable_straddleParamIgnored() {
        // straddle_enabled=false のテーブルで straddle=true を渡しても無効
        GameState g = newGame6P(false);
        g.startHand(BIG_BLIND, true);

        assertThat(g.getStraddleIdx()).isEqualTo(-1);
        assertThat(g.getCurrentBet()).isEqualTo(200);
    }

    @Test
    void straddle_headsUp_straddleSkipped() {
        // 2人テーブルではストラドルをスキップ
        List<Player> players = List.of(new Player("p0", 10000), new Player("p1", 10000));
        GameState g = GameState.newGame(players, 0, BIG_BLIND, new Random(42), OddChipRule.LOW_INDEX, 0, true);
        g.startHand(BIG_BLIND, true);

        assertThat(g.getStraddleIdx()).isEqualTo(-1);
        assertThat(g.getCurrentBet()).isEqualTo(200);
    }

    @Test
    void straddle_insufficientStack_skipsStraddle() {
        // UTGのスタックがstraddle額(400)未満の場合スキップ
        List<Player> players = List.of(
                new Player("p0", 10000),
                new Player("p1", 10000),
                new Player("p2", 10000),
                new Player("p3", 300) // UTG: stack=300 < 400
        );
        // buttonIndex=3 → startHand後 button=p0, SB=p1, BB=p2, UTG=p3
        GameState g = GameState.newGame(players, 3, BIG_BLIND, new Random(42), OddChipRule.LOW_INDEX, 0, true);
        g.startHand(BIG_BLIND, true);

        assertThat(g.getStraddleIdx()).isEqualTo(-1);
        assertThat(g.getCurrentBet()).isEqualTo(200);
    }

    @Test
    void straddle_minRaiseIsDoubleStraddle() {
        // currentBet=400, lastRaiseSize=400 → minRaise = 400+400 = 800
        GameState g = newGame6P(true);
        g.startHand(BIG_BLIND, true);

        // p4がレイズする最小額は currentBet(400) + lastRaiseSize(400) = 800
        int minRaise = g.getCurrentBet() + Math.max(g.getBigBlindSize(), g.getLastRaiseSize());
        assertThat(minRaise).isEqualTo(800);
    }

    @Test
    void straddle_contributed_updateCorrectly() {
        GameState g = newGame6P(true);
        g.startHand(BIG_BLIND, true);

        Player stradder = g.getPlayers().get(g.getStraddleIdx());
        // straddle額はcontributedに加算される（コール基準）
        assertThat(stradder.getContributed()).isEqualTo(400);
        assertThat(stradder.getTotalContrib()).isEqualTo(400);
    }
}
