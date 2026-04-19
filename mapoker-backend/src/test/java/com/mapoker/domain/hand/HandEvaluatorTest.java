package com.mapoker.domain.hand;

import com.mapoker.domain.card.Card;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HandEvaluatorTest {

    private static Card c(String code) {
        return Card.parse(code);
    }

    private static Card[] cards(String... codes) {
        Card[] arr = new Card[codes.length];
        for (int i = 0; i < codes.length; i++) arr[i] = c(codes[i]);
        return arr;
    }

    @Test
    void straightFlush() {
        HandValue v = HandEvaluator.eval5(cards("9S", "8S", "7S", "6S", "5S"));
        assertThat(v.rank()).isEqualTo(HandRank.STRAIGHT_FLUSH);
    }

    @Test
    void fourOfAKind() {
        HandValue v = HandEvaluator.eval5(cards("AS", "AH", "AD", "AC", "KH"));
        assertThat(v.rank()).isEqualTo(HandRank.FOUR_OF_A_KIND);
    }

    @Test
    void fullHouse() {
        HandValue v = HandEvaluator.eval5(cards("AS", "AH", "AD", "KH", "KD"));
        assertThat(v.rank()).isEqualTo(HandRank.FULL_HOUSE);
    }

    @Test
    void flush() {
        HandValue v = HandEvaluator.eval5(cards("AS", "KS", "QS", "JS", "9S"));
        assertThat(v.rank()).isEqualTo(HandRank.FLUSH);
    }

    @Test
    void straight() {
        HandValue v = HandEvaluator.eval5(cards("9S", "8H", "7D", "6C", "5S"));
        assertThat(v.rank()).isEqualTo(HandRank.STRAIGHT);
    }

    @Test
    void wheel() {
        HandValue v = HandEvaluator.eval5(cards("AS", "2H", "3D", "4C", "5S"));
        assertThat(v.rank()).isEqualTo(HandRank.STRAIGHT);
    }

    @Test
    void threeOfAKind() {
        HandValue v = HandEvaluator.eval5(cards("AS", "AH", "AD", "KH", "QD"));
        assertThat(v.rank()).isEqualTo(HandRank.THREE_OF_A_KIND);
    }

    @Test
    void twoPair() {
        HandValue v = HandEvaluator.eval5(cards("AS", "AH", "KD", "KH", "QD"));
        assertThat(v.rank()).isEqualTo(HandRank.TWO_PAIR);
    }

    @Test
    void onePair() {
        HandValue v = HandEvaluator.eval5(cards("AS", "AH", "KD", "QH", "JD"));
        assertThat(v.rank()).isEqualTo(HandRank.ONE_PAIR);
    }

    @Test
    void highCard() {
        HandValue v = HandEvaluator.eval5(cards("AS", "KH", "QD", "JC", "9S"));
        assertThat(v.rank()).isEqualTo(HandRank.HIGH_CARD);
    }

    @Test
    void eval7PicksBest() {
        // hole: AS KS, community: QS JS TS 2H 3D → royal straight flush
        HandValue v = HandEvaluator.eval7(cards("AS", "KS", "QS", "JS", "TS", "2H", "3D"));
        assertThat(v.rank()).isEqualTo(HandRank.STRAIGHT_FLUSH);
    }

    @Test
    void handCompareHigherWins() {
        HandValue flush = HandEvaluator.eval5(cards("AS", "KS", "QS", "JS", "9S"));
        HandValue straight = HandEvaluator.eval5(cards("9S", "8H", "7D", "6C", "5S"));
        assertThat(flush.compareTo(straight)).isGreaterThan(0);
    }
}
