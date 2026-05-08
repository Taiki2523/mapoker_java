package com.mapoker.domain.hand;

import com.mapoker.domain.PokerConstants;
import com.mapoker.domain.card.Card;
import com.mapoker.domain.card.Rank;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ポーカーハンドの役判定を行うユーティリティです。
 */
public final class HandEvaluator {

    private HandEvaluator() {}

    /**
     * 5 枚のカードから役を評価します。
     *
     * @param cards 評価対象の 5 枚カード
     * @return 評価結果
     */
    public static HandValue eval5(Card[] cards) {
        int[] rankCounts = new int[PokerConstants.RANK_ARRAY_SIZE];
        int[] suitCounts = new int[PokerConstants.NUM_SUITS];
        for (Card c : cards) {
            rankCounts[c.rank().getValue()]++;
            suitCounts[c.suit().ordinal()]++;
        }

        boolean flush = false;
        for (int c : suitCounts) {
            if (c == PokerConstants.FLUSH_SIZE) { flush = true; break; }
        }

        StraightResult sr = isStraight(rankCounts);
        if (sr.isStraight() && flush) {
            return new HandValue(HandRank.STRAIGHT_FLUSH, List.of(sr.high()));
        }

        ClassifyResult cr = classifyRanks(rankCounts);
        if (cr.fourRank() != null) {
            Rank kicker = highestExcluding(rankCounts, cr.fourRank());
            return new HandValue(HandRank.FOUR_OF_A_KIND, List.of(cr.fourRank(), kicker));
        }
        if (cr.threeRank() != null && !cr.pairs().isEmpty()) {
            return new HandValue(HandRank.FULL_HOUSE, List.of(cr.threeRank(), cr.pairs().get(0)));
        }
        if (flush) {
            return new HandValue(HandRank.FLUSH, ranksDesc(rankCounts));
        }
        if (sr.isStraight()) {
            return new HandValue(HandRank.STRAIGHT, List.of(sr.high()));
        }
        if (cr.threeRank() != null) {
            List<Rank> kickers = ranksDescExcluding(rankCounts, cr.threeRank());
            List<Rank> result = new ArrayList<>();
            result.add(cr.threeRank());
            result.addAll(kickers);
            return new HandValue(HandRank.THREE_OF_A_KIND, result);
        }
        if (cr.pairs().size() >= 2) {
            Rank kicker = highestExcluding(rankCounts, cr.pairs().get(0), cr.pairs().get(1));
            return new HandValue(HandRank.TWO_PAIR, List.of(cr.pairs().get(0), cr.pairs().get(1), kicker));
        }
        if (cr.pairs().size() == 1) {
            List<Rank> kickers = ranksDescExcluding(rankCounts, cr.pairs().get(0));
            List<Rank> result = new ArrayList<>();
            result.add(cr.pairs().get(0));
            result.addAll(kickers);
            return new HandValue(HandRank.ONE_PAIR, result);
        }
        return new HandValue(HandRank.HIGH_CARD, ranksDesc(rankCounts));
    }

    /**
     * 7 枚のカードから最良の 5 枚役を評価します。
     *
     * @param cards 評価対象の 7 枚カード
     * @return 最良役の評価結果
     */
    public static HandValue eval7(Card[] cards) {
        HandValue best = new HandValue(HandRank.HIGH_CARD, List.of(Rank.TWO));
        Card[] combo = new Card[5];
        for (int a = 0; a < 3; a++)
        for (int b = a + 1; b < 4; b++)
        for (int c = b + 1; c < 5; c++)
        for (int d = c + 1; d < 6; d++)
        for (int e = d + 1; e < 7; e++) {
            combo[0] = cards[a]; combo[1] = cards[b]; combo[2] = cards[c];
            combo[3] = cards[d]; combo[4] = cards[e];
            HandValue val = eval5(combo);
            if (val.compareTo(best) > 0) best = val;
        }
        return best;
    }

    private record StraightResult(boolean isStraight, Rank high) {}

    private static StraightResult isStraight(int[] rankCounts) {
        int unique = 0;
        for (Rank r : Rank.values()) {
            if (rankCounts[r.getValue()] > 0) unique++;
        }
        if (unique != 5) return new StraightResult(false, null);

        Rank[] ranks = Rank.values();
        // check from ACE down to FIVE
        for (int i = ranks.length - 1; i >= 0; i--) {
            Rank r = ranks[i];
            if (r.getValue() < 5) break;
            if (rankCounts[r.getValue()] == 0) continue;
            boolean ok = true;
            for (int j = 1; j < 5; j++) {
                if (rankCounts[r.getValue() - j] == 0) { ok = false; break; }
            }
            if (ok) return new StraightResult(true, r);
        }
        // wheel: A-2-3-4-5
        if (rankCounts[Rank.ACE.getValue()] > 0
                && rankCounts[Rank.TWO.getValue()] > 0
                && rankCounts[Rank.THREE.getValue()] > 0
                && rankCounts[Rank.FOUR.getValue()] > 0
                && rankCounts[Rank.FIVE.getValue()] > 0) {
            return new StraightResult(true, Rank.FIVE);
        }
        return new StraightResult(false, null);
    }

    private record ClassifyResult(Rank fourRank, Rank threeRank, List<Rank> pairs) {}

    private static ClassifyResult classifyRanks(int[] rankCounts) {
        Rank fourRank = null, threeRank = null;
        List<Rank> pairs = new ArrayList<>(2);
        Rank[] ranks = Rank.values();
        for (int i = ranks.length - 1; i >= 0; i--) {
            Rank r = ranks[i];
            int count = rankCounts[r.getValue()];
            if (count == 4) fourRank = r;
            else if (count == 3) threeRank = r;
            else if (count == 2) pairs.add(r);
        }
        return new ClassifyResult(fourRank, threeRank, pairs);
    }

    private static List<Rank> ranksDesc(int[] rankCounts) {
        List<Rank> out = new ArrayList<>(5);
        Rank[] ranks = Rank.values();
        for (int i = ranks.length - 1; i >= 0; i--) {
            Rank r = ranks[i];
            for (int j = 0; j < rankCounts[r.getValue()]; j++) out.add(r);
        }
        return out;
    }

    private static List<Rank> ranksDescExcluding(int[] rankCounts, Rank... exclude) {
        Set<Rank> ex = new HashSet<>();
        for (Rank r : exclude) ex.add(r);
        List<Rank> out = new ArrayList<>(5);
        Rank[] ranks = Rank.values();
        for (int i = ranks.length - 1; i >= 0; i--) {
            Rank r = ranks[i];
            if (ex.contains(r)) continue;
            for (int j = 0; j < rankCounts[r.getValue()]; j++) out.add(r);
        }
        return out;
    }

    private static Rank highestExcluding(int[] rankCounts, Rank... exclude) {
        Set<Rank> ex = new HashSet<>();
        for (Rank r : exclude) ex.add(r);
        Rank[] ranks = Rank.values();
        for (int i = ranks.length - 1; i >= 0; i--) {
            Rank r = ranks[i];
            if (ex.contains(r)) continue;
            if (rankCounts[r.getValue()] > 0) return r;
        }
        return Rank.TWO;
    }
}
