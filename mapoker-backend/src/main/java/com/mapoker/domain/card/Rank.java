package com.mapoker.domain.card;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Rank {
    TWO(2, "2"), THREE(3, "3"), FOUR(4, "4"), FIVE(5, "5"),
    SIX(6, "6"), SEVEN(7, "7"), EIGHT(8, "8"), NINE(9, "9"),
    TEN(10, "T"), JACK(11, "J"), QUEEN(12, "Q"), KING(13, "K"), ACE(14, "A");

    private final int value;
    private final String code;

    Rank(int value, String code) {
        this.value = value;
        this.code = code;
    }

    public int getValue() {
        return value;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static Rank fromCode(String code) {
        if ("10".equals(code) || "t".equalsIgnoreCase(code)) return TEN;
        for (Rank r : values()) {
            if (r.code.equalsIgnoreCase(code)) return r;
        }
        throw new IllegalArgumentException("Unknown rank: " + code);
    }

    public static Rank fromValue(int value) {
        for (Rank r : values()) {
            if (r.value == value) return r;
        }
        throw new IllegalArgumentException("Unknown rank value: " + value);
    }
}
