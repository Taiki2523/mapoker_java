package com.mapoker.domain.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionValidatorTest {

    private static final TableState TABLE_PREFLOP_NOCALL =
            new TableState(Street.PREFLOP, 10, 0, 10, true);
    private static final TableState TABLE_PREFLOP_BET20 =
            new TableState(Street.PREFLOP, 10, 20, 10, true);
    private static final PlayerState PLAYER_FULL =
            new PlayerState(100, 0, false, false);
    private static final PlayerState PLAYER_CONTRIBUTED20 =
            new PlayerState(80, 20, false, false);

    @Test
    void checkOk() {
        assertThatNoException().isThrownBy(() ->
                ActionValidator.validate(TABLE_PREFLOP_NOCALL, PLAYER_FULL, Action.of(ActionType.CHECK, 0)));
    }

    @Test
    void checkFailsWhenMustCall() {
        assertThatThrownBy(() ->
                ActionValidator.validate(TABLE_PREFLOP_BET20, PLAYER_FULL, Action.of(ActionType.CHECK, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot check");
    }

    @Test
    void betOk() {
        assertThatNoException().isThrownBy(() ->
                ActionValidator.validate(TABLE_PREFLOP_NOCALL, PLAYER_FULL, Action.of(ActionType.BET, 20)));
    }

    @Test
    void betFailsBelowBigBlind() {
        assertThatThrownBy(() ->
                ActionValidator.validate(TABLE_PREFLOP_NOCALL, PLAYER_FULL, Action.of(ActionType.BET, 5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void raiseOk() {
        assertThatNoException().isThrownBy(() ->
                ActionValidator.validate(TABLE_PREFLOP_BET20, PLAYER_FULL, Action.of(ActionType.RAISE, 40)));
    }

    @Test
    void raiseFailsBelowMinRaise() {
        assertThatThrownBy(() ->
                ActionValidator.validate(TABLE_PREFLOP_BET20, PLAYER_FULL, Action.of(ActionType.RAISE, 25)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void callAutoOk() {
        assertThatNoException().isThrownBy(() ->
                ActionValidator.validate(TABLE_PREFLOP_BET20, PLAYER_FULL, Action.of(ActionType.CALL, 0)));
    }

    @Test
    void foldAlwaysOk() {
        assertThatNoException().isThrownBy(() ->
                ActionValidator.validate(TABLE_PREFLOP_BET20, PLAYER_FULL, Action.of(ActionType.FOLD, 0)));
    }

    @Test
    void allInOk() {
        assertThatNoException().isThrownBy(() ->
                ActionValidator.validate(TABLE_PREFLOP_BET20, PLAYER_FULL, Action.of(ActionType.ALL_IN, 0)));
    }

    @Test
    void foldedPlayerCannotAct() {
        PlayerState folded = new PlayerState(100, 0, true, false);
        assertThatThrownBy(() ->
                ActionValidator.validate(TABLE_PREFLOP_NOCALL, folded, Action.of(ActionType.CHECK, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
