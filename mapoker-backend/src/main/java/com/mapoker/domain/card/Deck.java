package com.mapoker.domain.card;

import com.mapoker.domain.PokerConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class Deck {

    private Deck() {}

    public static List<Card> newDeck() {
        List<Card> deck = new ArrayList<>(PokerConstants.DECK_SIZE);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(rank, suit));
            }
        }
        return deck;
    }

    public static void shuffle(List<Card> deck, Random rng) {
        if (rng == null) {
            rng = new Random();
        }
        Collections.shuffle(deck, rng);
    }
}
