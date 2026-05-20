package com.mapoker.domain.card;

import com.mapoker.domain.PokerConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 標準52枚デッキの生成とシャッフルを担うユーティリティクラス。
 *
 * <p>インスタンス化不可。ハンド開始前に {@link #newDeck()} で新しいデッキを生成し、
 * {@link #shuffle(List, Random)} でシャッフルしてから使う。
 * デッキはリストとして扱い、先頭から順に1枚ずつドローする（{@link GameState} 内の {@code deckPos} で管理）。
 */
public final class Deck {

    private Deck() {}

    /**
     * 全4スーツ × 全13ランクの52枚を含む新しいデッキを生成する。
     *
     * <p>返されるリストは可変であり、{@link #shuffle(List, Random)} に直接渡せる。
     * 順序はスーツ優先でランクが昇順（{@link Suit#values()} × {@link Rank#values()} の順）。
     *
     * @return 52枚のカードリスト（シャッフル前）
     */
    public static List<Card> newDeck() {
        List<Card> deck = new ArrayList<>(PokerConstants.DECK_SIZE);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(rank, suit));
            }
        }
        return deck;
    }

    /**
     * 指定した {@link Random} を使ってデッキをシャッフルする。
     *
     * <p>{@code rng} が {@code null} の場合は {@code new Random()} をフォールバックとして使う。
     * 再現性が必要なテストでは非 {@code null} の {@link Random} を渡すこと。
     *
     * @param deck シャッフル対象のカードリスト（インプレースで変更される）
     * @param rng  乱数生成器。{@code null} を渡すとデフォルトの {@link Random} が使われる
     */
    public static void shuffle(List<Card> deck, Random rng) {
        if (rng == null) {
            rng = new Random();
        }
        Collections.shuffle(deck, rng);
    }
}
