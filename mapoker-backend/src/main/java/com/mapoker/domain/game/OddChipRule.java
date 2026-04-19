package com.mapoker.domain.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OddChipRule {
    LOW_INDEX("low_index"),
    BUTTON_LEFT("button_left");

    private final String label;

    OddChipRule(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static OddChipRule fromLabel(String label) {
        for (OddChipRule r : values()) {
            if (r.label.equalsIgnoreCase(label)) return r;
        }
        throw new IllegalArgumentException("Unknown odd chip rule: " + label);
    }
}
