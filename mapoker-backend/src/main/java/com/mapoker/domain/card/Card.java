package com.mapoker.domain.card;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record Card(Rank rank, Suit suit) {

    @JsonCreator
    public static Card parse(String code) {
        if (code == null || code.length() < 2 || code.length() > 3) {
            throw new IllegalArgumentException("Invalid card: " + code);
        }
        String rankPart = code.substring(0, code.length() - 1);
        String suitPart = code.substring(code.length() - 1);
        return new Card(Rank.fromCode(rankPart), Suit.fromCode(suitPart));
    }

    @JsonValue
    public String toCode() {
        return rank.getCode() + suit.getCode();
    }
}
