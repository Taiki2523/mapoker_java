package com.mapoker.domain.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ActionType {
    FOLD("fold"),
    CHECK("check"),
    CALL("call"),
    BET("bet"),
    RAISE("raise"),
    ALL_IN("all_in");

    private final String label;

    ActionType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static ActionType fromLabel(String label) {
        for (ActionType t : values()) {
            if (t.label.equalsIgnoreCase(label)) return t;
        }
        throw new IllegalArgumentException("Unknown action type: " + label);
    }
}
