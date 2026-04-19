package com.mapoker.domain.game;

import com.fasterxml.jackson.annotation.JsonValue;

public enum GameStatus {
    IN_PROGRESS("in_progress"),
    SHOWDOWN("showdown"),
    FINISHED("finished");

    private final String label;

    GameStatus(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}
