package com.mapoker.domain.rules;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Street {
    PREFLOP("preflop"),
    FLOP("flop"),
    TURN("turn"),
    RIVER("river");

    private final String label;

    Street(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    public Street next() {
        return values()[ordinal() + 1];
    }
}
