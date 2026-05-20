package com.mapoker.domain.game;

import com.mapoker.domain.rules.ActionType;
import com.mapoker.domain.rules.Action;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateAnteTest {

    private static GameState game2P(int s0, int s1, int ante) {
        return GameState.newGame(
                List.of(new Player("p0", s0), new Player("p1", s1)),
                0, 10, new Random(42), OddChipRule.LOW_INDEX, ante);
    }

    private static GameState game3P(int s0, int s1, int s2, int ante) {
        return GameState.newGame(
                List.of(new Player("p0", s0), new Player("p1", s1), new Player("p2", s2)),
                2, 10, new Random(42), OddChipRule.LOW_INDEX, ante);
    }

    @Test
    void anteIsCollectedBeforeBlindsAndAddedToPot() {
        // 3人テーブル: ante=5, SB=5, BB=10
        // 期待ポット = 5×3 + 5 + 10 = 30
        GameState g = game3P(1000, 1000, 1000, 5);
        g.startHand(10);
        assertThat(g.getPot()).isEqualTo(30);
    }

    @Test
    void anteDoesNotAffectContributed() {
        // ante は contributed に加算されない → コール額計算に影響しない
        GameState g = game3P(1000, 1000, 1000, 5);
        g.startHand(10);
        // button=0, SB=1, BB=2（3人なのでbuttonIndex=2→nextActive=0=button, SB=1, BB=2）
        // SBの contributed は SBブラインド分のみ（5）
        // BBの contributed は BBブラインド分のみ（10）
        Player sb = g.getPlayers().get(g.getSmallBlindIdx());
        Player bb = g.getPlayers().get(g.getBigBlindIdx());
        assertThat(sb.getContributed()).isEqualTo(5);
        assertThat(bb.getContributed()).isEqualTo(10);
    }

    @Test
    void anteIsIncludedInTotalContrib() {
        // totalContrib は ante + blinds を合計している
        GameState g = game3P(1000, 1000, 1000, 5);
        g.startHand(10);
        Player sb = g.getPlayers().get(g.getSmallBlindIdx());
        Player bb = g.getPlayers().get(g.getBigBlindIdx());
        // SB: totalContrib = ante(5) + SB(5) = 10
        assertThat(sb.getTotalContrib()).isEqualTo(10);
        // BB: totalContrib = ante(5) + BB(10) = 15
        assertThat(bb.getTotalContrib()).isEqualTo(15);
    }

    @Test
    void callAmountUnaffectedByAnte() {
        // ante があっても currentBet は BB の contributed(10) のまま
        GameState g = game2P(1000, 1000, 5);
        g.startHand(10);
        assertThat(g.getCurrentBet()).isEqualTo(10);
    }

    @Test
    void zeroAnteIsNoop() {
        // ante=0 のとき5パラメータ版と同じ動作
        GameState withAnte0 = GameState.newGame(
                List.of(new Player("p0", 1000), new Player("p1", 1000)),
                0, 10, new Random(42), OddChipRule.LOW_INDEX, 0);
        withAnte0.startHand(10);

        GameState without = GameState.newGame(
                List.of(new Player("p0", 1000), new Player("p1", 1000)),
                0, 10, new Random(42), OddChipRule.LOW_INDEX);
        without.startHand(10);

        // 両方のポットが同じ（SB=5 + BB=10 = 15）
        assertThat(withAnte0.getPot()).isEqualTo(without.getPot()).isEqualTo(15);
    }

    @Test
    void shortStackAnteIsAllIn() {
        // p0 のスタックが ante 未満 → 全額をアンティとして払いオールイン
        // ante=10, p0 stack=3
        GameState g = game2P(3, 1000, 10);
        g.startHand(10);
        Player p0 = g.getPlayers().get(0);
        // 3チップ全部をアンティに使用
        assertThat(p0.getStack()).isEqualTo(0);
        assertThat(p0.getTotalContrib()).isEqualTo(3);
        assertThat(p0.getContributed()).isEqualTo(0); // contributed には加算されない
        assertThat(p0.isAllIn()).isTrue();
    }

    @Test
    void chipConservationWithAnte() {
        // ante ありでもチップ総量が保存される（フォールドウィン経由）
        int initialTotal = 1000 + 1000 + 1000;
        GameState g = game3P(1000, 1000, 1000, 5);
        g.startHand(10);

        // 全員フォールドして1人が勝つまでアクションを進める
        int safety = 30;
        while (g.getStatus() == GameStatus.IN_PROGRESS && safety-- > 0) {
            int cp = g.getCurrentPlayer();
            g.applyAction(cp, Action.of(ActionType.FOLD, 0));
        }

        int finalTotal = g.getPlayers().stream().mapToInt(Player::getStack).sum();
        assertThat(finalTotal).isEqualTo(initialTotal);
    }

    @Test
    void anteSidePotInteraction() {
        // ante でオールインになったプレイヤーを含むサイドポット計算が正しい
        // p0=10(ante=10でオールイン), p1=1000, p2=1000, ante=10, BB=10
        int initialTotal = 10 + 1000 + 1000;
        GameState g = game3P(10, 1000, 1000, 10);
        g.startHand(10);

        // p0 はante(10)でオールイン。SBもBBも正常に投下される。
        Player p0 = g.getPlayers().get(0);
        assertThat(p0.isAllIn()).isTrue();
        assertThat(p0.getStack()).isEqualTo(0);

        // コール額があればコール、なければチェックで進める
        int safety = 30;
        while (g.getStatus() == GameStatus.IN_PROGRESS && safety-- > 0) {
            int cp = g.getCurrentPlayer();
            Player p = g.getPlayers().get(cp);
            Action action = p.getContributed() < g.getCurrentBet()
                    ? Action.of(ActionType.CALL, 0)
                    : Action.of(ActionType.CHECK, 0);
            g.applyAction(cp, action);
        }
        if (g.getStatus() == GameStatus.SHOWDOWN) {
            ShowdownResult result = g.resolveShowdown();
            g.applyPayouts(result.payouts());
        }

        int finalTotal = g.getPlayers().stream().mapToInt(Player::getStack).sum();
        assertThat(finalTotal).isEqualTo(initialTotal);
    }
}
