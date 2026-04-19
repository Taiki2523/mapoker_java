package com.mapoker.domain.rules;

public record Action(ActionType type, int amount) {
    public static Action of(ActionType type, int amount) {
        return new Action(type, amount);
    }
}
