package com.mapoker.domain.hand;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum HandRank {
    HIGH_CARD("HighCard"),
    ONE_PAIR("OnePair"),
    TWO_PAIR("TwoPair"),
    THREE_OF_A_KIND("ThreeOfAKind"),
    STRAIGHT("Straight"),
    FLUSH("Flush"),
    FULL_HOUSE("FullHouse"),
    FOUR_OF_A_KIND("FourOfAKind"),
    STRAIGHT_FLUSH("StraightFlush");

    private final String label;

    HandRank(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static HandRank fromLabel(String label) {
        for (HandRank h : values()) {
            if (h.label.equalsIgnoreCase(label)) return h;
        }
        throw new IllegalArgumentException("Unknown hand rank: " + label);
    }
}
